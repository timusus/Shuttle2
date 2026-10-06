/* Adapted from Shuttle Podcasts (podcasts@9ee6e0954) mobile/ios/Spine/Sources/CStreamDecode/stream_decode.c — see ios/Playback/README.md. S2 additions are marked "S2:". */
/*
 * stream_decode.c — see stream_decode.h.
 *
 * The AVIO / open / cleanup shape is `CSpineDecode/spine_decode.c`'s, deliberately: that file is
 * the one that has been measured against the bench, so its ordering (open, best-effort
 * find_stream_info, find_best_stream, decoder, swresample) is copied rather than re-reasoned. What
 * is different is everything the player needs and a whole-buffer decode does not: a seekable AVIO
 * over a blocking reader, a pull-at-a-time decode loop with its own state, seek, and cancel.
 */
#include "stream_decode.h"

#include <math.h>
#include <stdlib.h>
#include <string.h>

#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libavutil/avutil.h>
#include <libavutil/opt.h>
#include <libswresample/swresample.h>

struct StreamDecoder {
    StreamDecodeCallbacks cb;
    void            *opaque;

    AVIOContext     *avio;
    AVFormatContext *fmt;
    AVCodecContext  *dec;
    SwrContext      *swr;
    AVPacket        *pkt;
    AVFrame         *frame;

    int         audio_idx;
    int         sample_rate;
    int         channels;
    /* S2: what `pending` holds — the source's rate and channel count unless
     * `stream_decoder_set_output` asked for a fixed player format. */
    int         out_rate;
    int         out_channels;
    /* The input side the resampler was configured with, so a codec that changes format mid-stream
     * gets a fresh resampler instead of reinterpreted samples. */
    int         swr_in_rate;
    int         swr_in_channels;
    int         swr_in_fmt;
    AVRational  time_base;
    int64_t     start_time;   /* stream start_time, or 0 when AV_NOPTS_VALUE */
    /* S2: the stream's last timestamp (exclusive) when the container states it exactly — an MP4's
     * edit list or sample table — else AV_NOPTS_VALUE. Frames past it are the encoder's end padding
     * (an AAC track's last packet is whole, up to 1,023 frames of it padding), which FFmpeg's mov
     * demuxer does not trim, and a gapless join would play. */
    int64_t     end_pts;
    /* S2: how far before a seek target to land, in `time_base`: a lossy codec's decoder needs the
     * packets before a frame to decode it (MDCT overlap, MP3's bit reservoir, Opus' 80 ms seek
     * preroll). The caller reads and drops from the landing to the target, so the first frame heard
     * is exact. 0 for the lossless codecs, whose every packet decodes alone. */
    int64_t     seek_preroll;

    /* Decoded-but-not-yet-returned PCM, interleaved float32. `stream_decoder_read` copies out of
     * here and only pumps the decoder again once it is empty, so a caller asking for 4096 frames
     * never loses the tail of a 1152-frame MP3 frame. */
    float      *pending;
    int         pending_cap_floats;
    int         pending_frames;
    int         pending_offset;   /* frames already handed out */

    int64_t     last_frame_pts;   /* best_effort_timestamp of the most recent decoded frame */
    int         flushing;         /* a NULL packet has been sent to the decoder */
    int         ended;            /* the decoder and the resampler are both drained */

    /* Written by `stream_decoder_cancel` from another thread and only ever read, so a plain flag
     * is enough: the worst a stale read costs is one more callback into a reader that is itself
     * already cancelled. */
    volatile int cancelled;
    /* Same shape as `cancelled`, opposite meaning: a read the caller wants back so it can seek,
     * after which the decoder carries on. Cleared by `stream_decoder_seek`. */
    volatile int interrupted;
    int64_t      bytes_read;

    /* Absolute source byte that FFmpeg's offset 0 maps to: the end of the leading ID3v2 tag(s).
     * See `probe_id3_offset`. Zero for everything without one. */
    int64_t      base_offset;

    /* Bytes libavformat may read inside one `avformat_seek_file` before the seek is abandoned.
     * Armed only around that call; see `stream_decoder_seek`. */
    int64_t      seek_budget;
    int64_t      seek_bytes;
    int          seek_budget_armed;
    /* Tests only; see `stream_decoder_set_seek_budget_bytes`. <= 0 means the default. */
    int64_t      seek_budget_override;
    int          seek_budget_blown;

    /* Whole-container facts kept for the byte-estimate seek: media bytes (size minus
     * `base_offset`) and the duration those bytes cover. */
    int64_t      media_bytes;
    double       media_duration;
};

/* ── AVIO glue ───────────────────────────────────────────────────────────── */

static int avio_read_packet(void *opaque, uint8_t *buf, int buf_size) {
    StreamDecoder *d = (StreamDecoder *)opaque;
    if (d->cancelled || d->interrupted) return AVERROR_EXIT;
    /* A seek that has already spent its budget is a demuxer walking the file packet by packet to
     * build an index it has no table for (plan §4). Refusing the read aborts the walk; the caller
     * falls back to the byte estimate, which costs one transaction instead of megabytes. */
    if (d->seek_budget_armed && d->seek_bytes >= d->seek_budget) {
        d->seek_budget_blown = 1;
        return AVERROR(EIO);
    }
    int n = d->cb.read(d->opaque, buf, buf_size);
    if (n > 0) {
        d->bytes_read += n;
        if (d->seek_budget_armed) d->seek_bytes += n;
        return n;
    }
    /* Never 0: libavformat reads a 0 as "nothing yet, ask again" and spins on it forever. */
    switch (n) {
        case STREAM_READ_EOF:       return AVERROR_EOF;
        /* Latch it. During `stream_decoder_open_ex` there is no handle for the caller's `cancel` to
         * reach, so the reader's own refusal is the only evidence that this was a cancel and not a
         * broken file — and the two must not be reported the same way. */
        case STREAM_READ_CANCELLED: d->cancelled = 1; return AVERROR_EXIT;
        /* Latched for the same reason a cancel is: the reader is the only one that knows its read
         * came back early, and the pull loop above must be able to tell an interruption from a
         * broken file. */
        case STREAM_READ_INTERRUPTED: d->interrupted = 1; return AVERROR_EXIT;
        default:                    return AVERROR(EIO);
    }
}

static int64_t avio_seek_packet(void *opaque, int64_t offset, int whence) {
    StreamDecoder *d = (StreamDecoder *)opaque;
    if (d->cancelled || d->interrupted) return AVERROR_EXIT;

    if (whence == AVSEEK_SIZE) {
        int64_t size = d->cb.size(d->opaque);
        /* ENOSYS is the documented "I do not know", and it is the ONLY honest answer for a source
         * with no length: a made-up size sends the mov demuxer seeking past the end. */
        return size >= 0 ? size - d->base_offset : AVERROR(ENOSYS);
    }

    int64_t target;
    switch (whence) {
        case SEEK_SET: target = offset; break;
        case SEEK_CUR: target = -1; break;   /* resolved below */
        case SEEK_END: {
            int64_t size = d->cb.size(d->opaque);
            if (size < 0) return AVERROR(ENOSYS);
            target = (size - d->base_offset) + offset;
            break;
        }
        default: return AVERROR(EINVAL);
    }
    if (whence == SEEK_CUR) {
        /* libavformat resolves SEEK_CUR itself for buffered IO, but a custom context can still be
         * handed one; the reader knows its own position, so ask it. */
        return AVERROR(ENOSYS);
    }
    if (target < 0) return AVERROR(EINVAL);

    int rc = d->cb.seek(d->opaque, target + d->base_offset);
    if (rc == 0) return target;
    switch (rc) {
        case STREAM_READ_CANCELLED:   d->cancelled = 1; return AVERROR_EXIT;
        case STREAM_READ_INTERRUPTED: d->interrupted = 1; return AVERROR_EXIT;
        default:                      return AVERROR(EIO);
    }
}

/* ── the ID3v2 prologue ──────────────────────────────────────────────────── */

/*
 * How many bytes of leading ID3v2 tag(s) to hide from libavformat.
 *
 * **This is the streaming player's largest single bandwidth cost, and it is not hypothetical.**
 * Measured on a Darknet Diaries enclosure (`darknet-diaries-ep179`, 108 MB): a 13 782 278-byte
 * ID3v2 tag holding a 3000x3000 PNG cover, which `mp3_read_header` READS — not seeks over, because
 * it parses every APIC frame and turns the picture into an attached-pic stream nobody asked for.
 * `stream_decoder_open_ex` cost 13.8 MB of cellular data before a note was heard. `probesize` does not
 * bound it: the tag is consumed before the demuxer ever gets to probe audio.
 *
 * So the tag is stepped over here and FFmpeg's byte 0 is the first MPEG frame. The artwork and the
 * tag's metadata are not lost to anything that wanted them — the app takes both from the feed —
 * and the byte offsets the ad-skip tee records are the SOURCE's, since the translation lives in
 * the AVIO callbacks and nowhere else.
 *
 * Returns the absolute offset to start at, and leaves the reader positioned there. On anything
 * that is not ID3v2 it returns 0 and rewinds, which is every m4a and most mp3s.
 */
static int64_t probe_id3_offset(StreamDecoder *d) {
    int64_t offset = 0;
    for (;;) {
        uint8_t header[10];
        int got = 0;
        while (got < (int)sizeof(header)) {
            int n = d->cb.read(d->opaque, header + got, (int)sizeof(header) - got);
            if (n <= 0) { got = -1; break; }
            got += n;
        }
        if (got != (int)sizeof(header)) break;
        if (header[0] != 'I' || header[1] != 'D' || header[2] != '3') break;
        if (header[3] == 0xFF || header[4] == 0xFF) break;   /* not a version we can trust */
        /* Syncsafe: seven bits per byte, high bit always clear. */
        if ((header[6] | header[7] | header[8] | header[9]) & 0x80) break;
        int64_t size = ((int64_t)header[6] << 21) | ((int64_t)header[7] << 14)
                     | ((int64_t)header[8] << 7)  |  (int64_t)header[9];
        int64_t span = 10 + size + ((header[5] & 0x10) ? 10 : 0);   /* bit 4 is "has footer" */
        if (span <= 0) break;
        offset += span;
        /* Tags can be stacked; step to the next one and look again. */
        if (d->cb.seek(d->opaque, offset) != 0) return 0;
    }
    /* Either there was no tag or the last read was past the last one: go back to where the media
     * (or the file) starts. */
    if (d->cb.seek(d->opaque, offset) != 0) return 0;
    return offset;
}

/* ── resampler ───────────────────────────────────────────────────────────── */

/* S2: float32 interleaved out, at `out_rate` / `out_channels`. In Podcasts the resampler only
 * interleaved and converted the sample format (the player ran at the source's rate); S2's player
 * runs one fixed format so tracks of different rates are scheduled back to back on one node, and
 * this is where every track is converted into it (phase-6-playback.md §3, "Graph"). */
static int init_swr_from(StreamDecoder *d, const AVChannelLayout *src_layout, int src_rate, int src_fmt) {
    swr_free(&d->swr);

    AVChannelLayout out_layout = { 0 };
    /* Zero-initialised for the reason spine_decode.c records: av_channel_layout_copy uninitialises
     * its destination first, so a free() of stack garbage is an intermittent SIGABRT. */
    AVChannelLayout in_layout = { 0 };
    if (src_layout && src_layout->nb_channels > 0) {
        av_channel_layout_copy(&in_layout, src_layout);
    } else {
        av_channel_layout_default(&in_layout, d->channels > 0 ? d->channels : 1);
    }
    if (d->out_channels == in_layout.nb_channels) {
        av_channel_layout_copy(&out_layout, &in_layout);
    } else {
        av_channel_layout_default(&out_layout, d->out_channels);
    }
    int in_channels = in_layout.nb_channels;

    int rc = swr_alloc_set_opts2(&d->swr,
                                 &out_layout, AV_SAMPLE_FMT_FLT, d->out_rate,
                                 &in_layout, (enum AVSampleFormat)src_fmt, src_rate,
                                 0, NULL);
    av_channel_layout_uninit(&in_layout);
    av_channel_layout_uninit(&out_layout);
    if (rc < 0 || !d->swr) return STREAM_DECODE_ERR_RESAMPLE;
    /* Mono is FC, which swresample spreads to FL/FR at -3 dB (hard-coded for a mono source, so
     * `center_mix_level` does not reach it). A mono record on stereo output should be as loud on
     * each side as it was on its one channel, so the matrix is given explicitly. */
    if (in_channels == 1 && d->out_channels > 1 && d->out_channels <= 8) {
        double matrix[8];
        for (int i = 0; i < d->out_channels; i++) matrix[i] = 1.0;
        if (swr_set_matrix(d->swr, matrix, 1) < 0) return STREAM_DECODE_ERR_RESAMPLE;
    }
    if (swr_init(d->swr) < 0) return STREAM_DECODE_ERR_RESAMPLE;
    d->swr_in_rate = src_rate;
    d->swr_in_channels = in_channels;
    d->swr_in_fmt = src_fmt;
    return STREAM_DECODE_OK;
}

static int init_swr(StreamDecoder *d) {
    return init_swr_from(d, &d->dec->ch_layout, d->dec->sample_rate, d->dec->sample_fmt);
}

static int pending_reserve(StreamDecoder *d, int frames) {
    int need = (d->pending_frames + frames) * d->out_channels;
    if (need <= d->pending_cap_floats) return 1;
    int cap = d->pending_cap_floats ? d->pending_cap_floats : 8192 * d->out_channels;
    while (cap < need) cap *= 2;
    float *nb = (float *)realloc(d->pending, (size_t)cap * sizeof(float));
    if (!nb) return 0;
    d->pending = nb;
    d->pending_cap_floats = cap;
    return 1;
}

/* Push `frame` (NULL flushes) through the resampler into `pending`. Returns 0 on allocation
 * failure, 1 otherwise; `pending_frames` says how much arrived. */
static int push_through_swr(StreamDecoder *d, AVFrame *frame) {
    int in_samples = frame ? frame->nb_samples : 0;
    int64_t delay = swr_get_delay(d->swr, d->swr_in_rate);
    int out_samples = (int)av_rescale_rnd(delay + in_samples, d->out_rate, d->swr_in_rate,
                                          AV_ROUND_UP);
    if (out_samples <= 0) return 1;
    if (!pending_reserve(d, out_samples)) return 0;

    uint8_t *out = (uint8_t *)(d->pending + (size_t)d->pending_frames * d->out_channels);
    int converted = swr_convert(d->swr, &out, out_samples,
                                frame ? (const uint8_t **)frame->extended_data : NULL, in_samples);
    if (converted > 0) d->pending_frames += converted;
    return 1;
}

/* ── the decode pump ─────────────────────────────────────────────────────── */

static void pending_reset(StreamDecoder *d) {
    d->pending_frames = 0;
    d->pending_offset = 0;
}

/*
 * Advance until `pending` holds audio, or the stream is over.
 *
 * Precondition: `pending` is fully consumed. Returns STREAM_DECODE_OK with pending_frames > 0,
 * STREAM_DECODE_EOF when there is nothing left, or an error status. Every exit is a status: a
 * decode that quietly produced nothing would present as an episode that stops in the middle and
 * reports it finished.
 */
static int pump(StreamDecoder *d) {
    pending_reset(d);
    if (d->ended) return STREAM_DECODE_EOF;

    for (;;) {
        if (d->cancelled) return STREAM_DECODE_ERR_CANCELLED;
        if (d->interrupted) return STREAM_DECODE_ERR_INTERRUPTED;

        int rc = avcodec_receive_frame(d->dec, d->frame);
        if (rc == 0) {
            d->last_frame_pts = d->frame->best_effort_timestamp;
            /* S2: a frame the decoder trimmed at its head (an MP3's encoder delay, Opus pre-skip)
             * keeps its packet's timestamp and duration; its first sample is later by what was
             * trimmed. Without this a seek to the start of an MP3 lands 1,105 frames early by its
             * own account, and the caller drops real audio. A tail trim reads the same, so a seek
             * that lands in a track's last frame is early by the tail; nothing else uses it. */
            if (d->last_frame_pts != AV_NOPTS_VALUE && d->frame->duration > 0) {
                int64_t kept = av_rescale_q(d->frame->nb_samples, (AVRational){ 1, d->frame->sample_rate },
                                            d->time_base);
                if (d->frame->duration > kept) d->last_frame_pts += d->frame->duration - kept;
            }
            if (d->end_pts != AV_NOPTS_VALUE && d->last_frame_pts != AV_NOPTS_VALUE) {
                /* S2: cut the end padding at the container's stated end. */
                int64_t keep = av_rescale_q(d->end_pts - d->last_frame_pts, d->time_base,
                                            (AVRational){ 1, d->frame->sample_rate });
                if (keep <= 0) { av_frame_unref(d->frame); continue; }
                if (keep < d->frame->nb_samples) d->frame->nb_samples = (int)keep;
            }
            if (d->frame->sample_rate != d->swr_in_rate
                || d->frame->ch_layout.nb_channels != d->swr_in_channels
                || d->frame->format != d->swr_in_fmt) {
                /* S2: the codec changed format mid-stream (a chained Ogg, an ADTS rate switch). The old
                 * resampler's tail is a few samples of the old format; dropping it beats feeding
                 * this frame to a resampler that would reinterpret it. */
                int swr_rc = init_swr_from(d, &d->frame->ch_layout, d->frame->sample_rate, d->frame->format);
                if (swr_rc != STREAM_DECODE_OK) { av_frame_unref(d->frame); return swr_rc; }
            }
            int ok = push_through_swr(d, d->frame);
            av_frame_unref(d->frame);
            if (!ok) return STREAM_DECODE_ERR_ALLOC;
            if (d->pending_frames > 0) return STREAM_DECODE_OK;
            continue;   /* the resampler is still filling; ask for another frame */
        }
        if (rc == AVERROR_EOF) {
            /* The decoder is drained; whatever libswresample still holds is the last of it. */
            if (!push_through_swr(d, NULL)) return STREAM_DECODE_ERR_ALLOC;
            d->ended = 1;
            return d->pending_frames > 0 ? STREAM_DECODE_OK : STREAM_DECODE_EOF;
        }
        if (rc != AVERROR(EAGAIN)) return STREAM_DECODE_ERR_DECODER;

        if (d->flushing) {
            /* EAGAIN after a NULL packet cannot happen, but treat it as the end rather than
             * looping: an unbounded loop here is a hung player. */
            if (!push_through_swr(d, NULL)) return STREAM_DECODE_ERR_ALLOC;
            d->ended = 1;
            return d->pending_frames > 0 ? STREAM_DECODE_OK : STREAM_DECODE_EOF;
        }

        int read = av_read_frame(d->fmt, d->pkt);
        if (read < 0) {
            av_packet_unref(d->pkt);
            if (d->cancelled) return STREAM_DECODE_ERR_CANCELLED;
            if (d->interrupted) return STREAM_DECODE_ERR_INTERRUPTED;
            if (read == AVERROR_EXIT) return STREAM_DECODE_ERR_CANCELLED;
            if (read == AVERROR_EOF) {
                avcodec_send_packet(d->dec, NULL);
                d->flushing = 1;
                continue;
            }
            return STREAM_DECODE_ERR_IO;
        }
        if (d->pkt->stream_index != d->audio_idx) {
            av_packet_unref(d->pkt);
            continue;
        }
        int sent = avcodec_send_packet(d->dec, d->pkt);
        av_packet_unref(d->pkt);
        /* A packet the decoder rejects is a corrupt frame, not the end of the episode: skip it and
         * keep going, which is what every player does with a bad MP3 frame. */
        (void)sent;
    }
}

/* ── public API ──────────────────────────────────────────────────────────── */

/*
 * Whether the container header alone has described the stream, so `avformat_find_stream_info` (which
 * reads ahead, often several network round trips on a stream) would only add latency. True only for
 * lossless codecs whose header carries everything: FLAC (STREAMINFO), ALAC (moov `alac` atom) and
 * PCM in WAV. Lossy codecs (MP3, AAC, Opus, Vorbis) keep the probe: their parameters and duration
 * come from the frames or a stream-level estimate.
 */
static int header_described_audio_stream(const AVFormatContext *fmt) {
    if (!fmt->iformat || !fmt->iformat->name) return -1;
    const char *container = fmt->iformat->name;
    int is_mp4 = strstr(container, "mp4") != NULL;
    int is_flac = strcmp(container, "flac") == 0;
    int is_wav = strcmp(container, "wav") == 0;
    if (!is_mp4 && !is_flac && !is_wav) return -1;

    int audio = -1;
    for (unsigned i = 0; i < fmt->nb_streams; i++) {
        if (fmt->streams[i]->codecpar->codec_type != AVMEDIA_TYPE_AUDIO) continue;
        if (audio >= 0) return -1;   /* more than one audio stream: let the probe choose */
        audio = (int)i;
    }
    if (audio < 0) return -1;
    const AVStream *stream = fmt->streams[audio];
    const AVCodecParameters *par = stream->codecpar;

    int codec_ok = 0;
    if (is_flac) codec_ok = par->codec_id == AV_CODEC_ID_FLAC;
    else if (is_mp4) codec_ok = par->codec_id == AV_CODEC_ID_ALAC;
    else {
        /* Exactly the codecs the wav demuxer emits; anything else a wav can carry (ADPCM, a-law,
         * MPEG in a RIFF hack) keeps the probe. */
        switch (par->codec_id) {
        case AV_CODEC_ID_PCM_U8:
        case AV_CODEC_ID_PCM_S16LE:
        case AV_CODEC_ID_PCM_S24LE:
        case AV_CODEC_ID_PCM_S32LE:
        case AV_CODEC_ID_PCM_F32LE:
        case AV_CODEC_ID_PCM_F64LE:
            codec_ok = 1;
            break;
        default:
            break;
        }
    }
    if (!codec_ok) return -1;

    if (is_flac) {
        /* The flac demuxer leaves the parameters to its parser (they stay 0 until the probe), but
         * hands the decoder the STREAMINFO block as extradata, which is where the decoder reads its
         * rate, channels and bit depth from. */
        if (par->extradata_size < 34) return -1;
    } else {
        if (par->sample_rate <= 0 || par->ch_layout.nb_channels <= 0) return -1;
        if (par->format == AV_SAMPLE_FMT_NONE && par->bits_per_raw_sample <= 0
            && par->bits_per_coded_sample <= 0) {
            return -1;
        }
        /* The ALAC decoder reads its setup from the magic cookie the moov `alac` atom carries as
         * extradata (36 bytes); without it the open below fails, so let the probe have the file. */
        if (is_mp4 && par->extradata_size < 36) return -1;
    }
    /* The duration must already be known without the probe, from the stream or the format. */
    if (stream->duration == AV_NOPTS_VALUE || stream->duration <= 0) {
        if (fmt->duration == AV_NOPTS_VALUE || fmt->duration <= 0) return -1;
    }
    return audio;
}

StreamDecoder *stream_decoder_open_ex(const StreamDecodeCallbacks *callbacks,
                                      void *opaque,
                                      int flags,
                                      StreamAudioInfo *info,
                                      int *status) {
    int local_status = STREAM_DECODE_ERR_ALLOC;
    if (!callbacks || !callbacks->read || !callbacks->seek || !callbacks->size || !info) {
        if (status) *status = STREAM_DECODE_ERR_ARGS;
        return NULL;
    }
    /* libav's own diagnostics go to stderr at AV_LOG_INFO and say nothing the caller acts on; the
     * status codes are the channel that is read. spine_decode.c has the incident this prevents. */
    av_log_set_level(AV_LOG_QUIET);
    memset(info, 0, sizeof(*info));

    StreamDecoder *d = (StreamDecoder *)calloc(1, sizeof(StreamDecoder));
    if (!d) { if (status) *status = STREAM_DECODE_ERR_ALLOC; return NULL; }
    d->cb = *callbacks;
    d->opaque = opaque;
    d->audio_idx = -1;
    d->last_frame_pts = AV_NOPTS_VALUE;

    const int avio_buf_size = 32 * 1024;
    uint8_t *avio_buf = (uint8_t *)av_malloc(avio_buf_size);
    if (!avio_buf) goto fail;

    /* Read AND seek: with a NULL seek callback `pb->seekable` is 0 and the mov demuxer walks the
     * whole `mdat` to find a trailing `moov` (header, and plan §3). */
    d->avio = avio_alloc_context(avio_buf, avio_buf_size, 0, d, avio_read_packet, NULL,
                                 avio_seek_packet);
    if (!d->avio) { av_free(avio_buf); goto fail; }

    /* Before anything reads: hide the ID3v2 tag, which on a real podcast enclosure is megabytes of
     * cover art that libavformat would otherwise consume in full. */
    d->base_offset = probe_id3_offset(d);
    if (d->cancelled) { local_status = STREAM_DECODE_ERR_CANCELLED; goto fail; }

    d->fmt = avformat_alloc_context();
    if (!d->fmt) goto fail;
    d->fmt->pb = d->avio;
    /* Bound what probing costs in BYTES, because for this decoder bytes are cellular data (plan
     * §4). The defaults are a 5 MB probe and 5 s of analysis, and libavformat spends them eagerly:
     * measured on `tone_moov_last.m4a`, open() alone read 42% of the file, all of it before a
     * single frame was played. Podcast audio is one stream in a container the first packets
     * already describe, so a 64 KiB probe and 1 s of analysis identify it just as well. */
    d->fmt->probesize = 64 * 1024;
    d->fmt->max_analyze_duration = AV_TIME_BASE;
    /* Seek by the table of contents the container carries rather than by binary search. Without
     * this `mp3_seek` only trusts a Xing TOC on a file it has decided is CBR, and for everything
     * else it runs `ff_seek_frame_binary`, which probes and re-syncs its way through the file: on
     * the 160 KB tone fixture one seek to 10 s read 72 KB, and on an enclosure it is the walk the
     * budget below exists to stop. The TOC is a coarser landing (a percent of the file per entry)
     * and the caller's position follows the frame that is actually decoded, so the cost of taking
     * it is nothing this player can observe. */
    d->fmt->flags |= AVFMT_FLAG_FAST_SEEK;

    if (avformat_open_input(&d->fmt, NULL, NULL, NULL) < 0) {
        d->fmt = NULL;   /* avformat_open_input freed it; the AVIO context is still ours */
        local_status = d->cancelled ? STREAM_DECODE_ERR_CANCELLED : STREAM_DECODE_ERR_OPEN;
        goto fail;
    }
    /* Best effort, as in spine_decode.c: some containers decode fine with thinner metadata.
     * Skipped when the header already describes a lossless stream (#822): the probe's read-ahead is
     * pure play-start latency there. */
    int header_audio = (flags & STREAM_DECODE_FORCE_PROBE) ? -1 : header_described_audio_stream(d->fmt);
    if (header_audio >= 0) {
        info->skipped_probe = 1;
        /* av_find_best_stream ignores a FLAC stream whose rate and channels are still 0, so take
         * the one audio stream the check above found. */
        d->audio_idx = header_audio;
    } else {
        (void)avformat_find_stream_info(d->fmt, NULL);
        d->audio_idx = av_find_best_stream(d->fmt, AVMEDIA_TYPE_AUDIO, -1, -1, NULL, 0);
    }
    if (d->audio_idx < 0) { local_status = STREAM_DECODE_ERR_NO_AUDIO; goto fail; }

    AVStream *stream = d->fmt->streams[d->audio_idx];
    AVCodecParameters *par = stream->codecpar;
    const AVCodec *codec = avcodec_find_decoder(par->codec_id);
    if (!codec) { local_status = STREAM_DECODE_ERR_DECODER; goto fail; }

    d->dec = avcodec_alloc_context3(codec);
    if (!d->dec) goto fail;
    if (avcodec_parameters_to_context(d->dec, par) < 0) { local_status = STREAM_DECODE_ERR_DECODER; goto fail; }
    /* One thread: FFmpeg's audio decoders have no frame threading to gain from (plan §4), and the
     * pull loop is single-threaded by contract. */
    d->dec->thread_count = 1;
    if (avcodec_open2(d->dec, codec, NULL) < 0) { local_status = STREAM_DECODE_ERR_DECODER; goto fail; }

    d->sample_rate = d->dec->sample_rate > 0 ? d->dec->sample_rate : par->sample_rate;
    d->channels = d->dec->ch_layout.nb_channels > 0 ? d->dec->ch_layout.nb_channels
                                                    : par->ch_layout.nb_channels;
    if (d->sample_rate <= 0 || d->channels <= 0) { local_status = STREAM_DECODE_ERR_DECODER; goto fail; }
    d->time_base = stream->time_base;
    d->start_time = stream->start_time == AV_NOPTS_VALUE ? 0 : stream->start_time;
    /* S2: only the mov demuxer's duration is the media's exact length (the edit list's, else the
     * sample table's); an MP3's is a bitrate estimate, and trimming at it would cut music. */
    d->end_pts = AV_NOPTS_VALUE;
    if (d->fmt->iformat && strstr(d->fmt->iformat->name, "mp4") && stream->duration != AV_NOPTS_VALUE
        && stream->duration > 0) {
        d->end_pts = d->start_time + stream->duration;
    }
    switch (par->codec_id) {
    case AV_CODEC_ID_AAC: case AV_CODEC_ID_MP3: case AV_CODEC_ID_OPUS: case AV_CODEC_ID_VORBIS: {
        int frame = par->frame_size > 0 ? par->frame_size : 2048;
        int64_t samples = par->seek_preroll > 2 * frame ? par->seek_preroll : 2 * frame;
        d->seek_preroll = av_rescale_q(samples, (AVRational){ 1, d->sample_rate }, d->time_base);
        break;
    }
    default:
        d->seek_preroll = 0;
    }
    d->out_rate = d->sample_rate;
    d->out_channels = d->channels;

    local_status = init_swr(d);
    if (local_status != STREAM_DECODE_OK) goto fail;

    d->pkt = av_packet_alloc();
    d->frame = av_frame_alloc();
    if (!d->pkt || !d->frame) { local_status = STREAM_DECODE_ERR_ALLOC; goto fail; }

    info->sample_rate = d->sample_rate;
    info->channel_count = d->channels;
    /* Format duration first, stream duration second (an MP3's Xing frame count reaches the stream
     * before it reaches the format).
     *
     * A source with NO TOTAL LENGTH reports 0 here, and that is FFmpeg n7.1's behaviour rather
     * than a gap in this file: mp3dec cross-checks the Xing header's own file-size field against
     * `avio_size()`, and a size of "unknown" comes back as a negative error that fails the check,
     * so the tag and its duration are discarded — measured both ways in
     * `StreamDecodeTests.testUnknownLengthStillDecodesMP3`, with and without a seek callback. The
     * caller's answer is plan §5.3's third fallback, the feed's own duration; playback itself is
     * unaffected, which is what that test asserts. */
    if (d->fmt->duration != AV_NOPTS_VALUE) {
        info->duration_sec = (double)d->fmt->duration / (double)AV_TIME_BASE;
    } else if (stream->duration != AV_NOPTS_VALUE) {
        info->duration_sec = (double)stream->duration * av_q2d(stream->time_base);
    }
    /* Kept for the byte-estimate seek: what the media occupies in bytes and how long it lasts. */
    {
        int64_t total = d->cb.size(d->opaque);
        d->media_bytes = total > d->base_offset ? total - d->base_offset : 0;
        d->media_duration = info->duration_sec;
    }
    snprintf(info->codec_name, sizeof(info->codec_name), "%s", avcodec_get_name(par->codec_id));
    if (d->fmt->iformat && d->fmt->iformat->name) {
        snprintf(info->container_name, sizeof(info->container_name), "%s", d->fmt->iformat->name);
    }

    if (status) *status = STREAM_DECODE_OK;
    return d;

fail:
    if (status) *status = local_status;
    stream_decoder_close(d);
    return NULL;
}

/* Bytes one `avformat_seek_file` may read before it is judged to be walking the file. Two AVIO
 * refills: enough for a mov index landing or an mp3 TOC landing, and small enough that the walk it
 * exists to stop is cut off after a fraction of a second of audio rather than the 12 MB a single
 * seek to 25 minutes cost on the measured fixture. */
static const int64_t kSeekBudgetBytes = 64 * 1024;

/* Finish a seek: flush the codec and the resampler and clear the pull loop's state. */
static void after_seek_reset(StreamDecoder *d) {
    avcodec_flush_buffers(d->dec);
    pending_reset(d);
    d->flushing = 0;
    d->ended = 0;
    d->last_frame_pts = AV_NOPTS_VALUE;
}

/* Whether the container gives enough to place a second in the byte stream by linear estimate. */
static int can_estimate_bytes(const StreamDecoder *d) {
    return d->media_bytes > 0 && d->media_duration > 0;
}

/*
 * Clear the error `AVIOContext` latches.
 *
 * Every refusal this file makes — a cancel, an interruption, a seek that blew its byte budget —
 * reaches libavformat as a failed read, and `AVIOContext` keeps it: `error` holds the code and
 * `eof_reached` stays 1. `avio_seek` resets `eof_reached` and nothing resets `error`, so the reads
 * that follow come straight back with the OLD failure without asking the byte source for anything:
 * an interrupted decoder reported a cancel on its next seek, and a seek that fell back to the byte
 * estimate reported `STREAM_DECODE_ERR_IO` after landing correctly. A seek is precisely the point
 * at which those refusals stop being true, so it is where they are cleared.
 */
static void avio_clear_latched_error(StreamDecoder *d) {
    if (!d->avio) return;
    d->avio->error = 0;
    d->avio->eof_reached = 0;
}

int stream_decoder_seek(StreamDecoder *decoder, double seconds, double *landed_seconds) {
    if (!decoder || !landed_seconds) return STREAM_DECODE_ERR_ARGS;
    *landed_seconds = seconds;
    if (decoder->cancelled) return STREAM_DECODE_ERR_CANCELLED;
    /* The seek IS the answer to the interruption: clearing it here, before anything reads, is what
     * makes an interrupted decoder reusable rather than dead. */
    decoder->interrupted = 0;
    avio_clear_latched_error(decoder);
    if (seconds < 0) seconds = 0;

    double tb = av_q2d(decoder->time_base);
    int64_t target = decoder->start_time + (int64_t)llround(seconds / (tb > 0 ? tb : 1.0))
        - decoder->seek_preroll;   /* S2 */
    /* S2: never before the start. A demuxer restores its start trimming (an MP3's encoder delay)
     * only for a seek to the start itself. */
    if (target < decoder->start_time) target = decoder->start_time;

    /* Backward-leaning: land at or before the request so nothing between the request and the
     * landing is skipped unheard. Where it actually lands is what the caller's position becomes.
     *
     * The budget is the whole point of this call's shape. An MP3 with no Xing TOC — which is most
     * podcast enclosures — has no way to place a timestamp, so libavformat's generic seek DECODES
     * FORWARD FROM THE START until the timestamps reach the target: measured at 12 MB for one seek
     * to 25 minutes, on a fixture whose whole open cost 32 KiB. It is refused here rather than
     * paid for, and the byte estimate below takes over. Everything with a real index — every mp4,
     * an mp3 with a TOC — lands well inside the budget and never reaches the fallback. */
    decoder->seek_bytes = 0;
    decoder->seek_budget = decoder->seek_budget_override > 0 ? decoder->seek_budget_override
                                                             : kSeekBudgetBytes;
    decoder->seek_budget_blown = 0;
    decoder->seek_budget_armed = 1;
    int rc = avformat_seek_file(decoder->fmt, decoder->audio_idx, INT64_MIN, target, target,
                                AVSEEK_FLAG_BACKWARD);
    int walked = decoder->seek_budget_blown;
    decoder->seek_budget_armed = 0;

    /* The refusal that abandoned the walk is latched in the AVIO context; the fallback below has
     * to read, so clear it here rather than after the seek that would already have failed. */
    if (walked) avio_clear_latched_error(decoder);

    if (rc < 0 || walked) {
        if (decoder->cancelled) return STREAM_DECODE_ERR_CANCELLED;
        if (!can_estimate_bytes(decoder)) {
            /* Nothing to estimate FROM: a source with no length and no container duration, which
             * over HTTP is a chunked response. The walk is then the only seek there is, so it is
             * paid for rather than refused — an expensive seek beats a seek that fails. */
            if (!walked) return STREAM_DECODE_ERR_SEEK;
            rc = avformat_seek_file(decoder->fmt, decoder->audio_idx, INT64_MIN, target, target,
                                    AVSEEK_FLAG_BACKWARD);
            if (rc < 0) {
                if (decoder->cancelled) return STREAM_DECODE_ERR_CANCELLED;
                return STREAM_DECODE_ERR_SEEK;
            }
            after_seek_reset(decoder);
            int unbudgeted_swr = init_swr(decoder);
            if (unbudgeted_swr != STREAM_DECODE_OK) return unbudgeted_swr;
            int unbudgeted = pump(decoder);
            if (unbudgeted == STREAM_DECODE_OK && decoder->last_frame_pts != AV_NOPTS_VALUE) {
                /* S2: not clamped at 0; see the timestamped return below. */
                *landed_seconds = (double)(decoder->last_frame_pts - decoder->start_time) * tb;
            }
            return unbudgeted;
        }

        /* The byte estimate. Exact for CBR, which is what a container with neither an index nor a
         * TOC almost always is; a VBR file that reaches here lands within its own bitrate swing,
         * and the caller's clock is anchored to what is REPORTED, not to what was asked for. */
        double ratio = seconds / decoder->media_duration;
        if (ratio < 0) ratio = 0;
        if (ratio > 1) ratio = 1;
        int64_t byte = (int64_t)(ratio * (double)decoder->media_bytes);
        rc = avformat_seek_file(decoder->fmt, decoder->audio_idx, INT64_MIN, byte, byte,
                                AVSEEK_FLAG_BYTE | AVSEEK_FLAG_BACKWARD);
        if (rc < 0) {
            if (decoder->cancelled) return STREAM_DECODE_ERR_CANCELLED;
            return STREAM_DECODE_ERR_SEEK;
        }
        after_seek_reset(decoder);
        int swr_rc = init_swr(decoder);
        if (swr_rc != STREAM_DECODE_OK) return swr_rc;

        /* Decode to the first frame so the caller gets audio, not an empty buffer. A byte seek
         * leaves the demuxer with no timestamp to report — there is nothing in the stream that
         * says what second this frame is — so the estimate IS the landed time. */
        int status = pump(decoder);
        *landed_seconds = ratio * decoder->media_duration;
        /* S2: some codecs DO say. A FLAC frame header carries its sample number and PCM's position
         * is its byte offset, so their timestamp after a byte seek is exact; and on a small FLAC
         * whose binary search gives up ("read_timestamp() failed in the middle") the byte seek can
         * land far from the estimate. MP3 keeps the estimate: its timestamps after a byte seek are
         * the demuxer's own bitrate guess, the thing Podcasts measured and chose not to trust. */
        if (status == STREAM_DECODE_OK && decoder->last_frame_pts != AV_NOPTS_VALUE
            && decoder->dec->codec_id != AV_CODEC_ID_MP3) {
            *landed_seconds = (double)(decoder->last_frame_pts - decoder->start_time) * tb;
            if (*landed_seconds < 0) *landed_seconds = 0;
        }
        return status;
    }

    after_seek_reset(decoder);
    int swr_rc = init_swr(decoder);   /* drop whatever the resampler still held from before */
    if (swr_rc != STREAM_DECODE_OK) return swr_rc;

    /* Decode eagerly to the first frame so the landed time is the audio's, not an estimate. The
     * PCM is kept in `pending`, so the next read starts exactly where the timestamp says. */
    int status = pump(decoder);
    if (status == STREAM_DECODE_OK && decoder->last_frame_pts != AV_NOPTS_VALUE) {
        /* S2: not clamped at 0. Frames before the stream's zero are a codec's priming (Opus
         * pre-skip, which FFmpeg drops only at the open), and a landing before it is what tells the
         * caller to read them and drop them. */
        *landed_seconds = (double)(decoder->last_frame_pts - decoder->start_time) * tb;
    }
    return status;
}

int stream_decoder_read(StreamDecoder *decoder, float *out, int max_frames, int *frames) {
    if (!decoder || !out || !frames || max_frames <= 0) return STREAM_DECODE_ERR_ARGS;
    *frames = 0;
    int channels = decoder->out_channels;
    int written = 0;
    int status = STREAM_DECODE_OK;

    while (written < max_frames) {
        int available = decoder->pending_frames - decoder->pending_offset;
        if (available <= 0) {
            status = pump(decoder);
            if (status != STREAM_DECODE_OK) break;
            available = decoder->pending_frames - decoder->pending_offset;
            if (available <= 0) { status = STREAM_DECODE_EOF; break; }
        }
        int take = available < (max_frames - written) ? available : (max_frames - written);
        memcpy(out + (size_t)written * channels,
               decoder->pending + (size_t)decoder->pending_offset * channels,
               (size_t)take * channels * sizeof(float));
        decoder->pending_offset += take;
        written += take;
    }

    *frames = written;
    /* Frames in hand beat the reason the loop stopped: the caller plays these and asks again, and
     * the next call reports the same end for the same reason. */
    return written > 0 ? STREAM_DECODE_OK : status;
}

int stream_decoder_set_output(StreamDecoder *decoder, int sample_rate, int channels) {
    if (!decoder || sample_rate <= 0 || channels <= 0) return STREAM_DECODE_ERR_ARGS;
    if (decoder->pending_frames - decoder->pending_offset > 0) return STREAM_DECODE_ERR_ARGS;
    decoder->out_rate = sample_rate;
    decoder->out_channels = channels;
    /* Anything already sized for the old channel count is empty (checked above), so only the
     * capacity bookkeeping has to follow. */
    free(decoder->pending);
    decoder->pending = NULL;
    decoder->pending_cap_floats = 0;
    pending_reset(decoder);
    return init_swr(decoder);
}

int64_t stream_decoder_position_bytes(const StreamDecoder *decoder) {
    return decoder ? decoder->bytes_read : 0;
}

void stream_decoder_set_seek_budget_bytes(StreamDecoder *decoder, int64_t bytes) {
    if (!decoder) return;
    decoder->seek_budget_override = bytes;
}

void stream_decoder_cancel(StreamDecoder *decoder) {
    if (decoder) decoder->cancelled = 1;
}

void stream_decoder_interrupt(StreamDecoder *decoder) {
    if (decoder) decoder->interrupted = 1;
}

void stream_decoder_clear_interrupt(StreamDecoder *decoder) {
    if (decoder) decoder->interrupted = 0;
}

void stream_decoder_close(StreamDecoder *decoder) {
    if (!decoder) return;
    av_frame_free(&decoder->frame);
    av_packet_free(&decoder->pkt);
    swr_free(&decoder->swr);
    avcodec_free_context(&decoder->dec);
    if (decoder->fmt) avformat_close_input(&decoder->fmt);
    /* avformat_close_input frees the format context but not the AVIO one, and libavformat may have
     * replaced the buffer we handed it, so free the CURRENT pointer. */
    if (decoder->avio) {
        av_freep(&decoder->avio->buffer);
        avio_context_free(&decoder->avio);
    }
    free(decoder->pending);
    free(decoder);
}
