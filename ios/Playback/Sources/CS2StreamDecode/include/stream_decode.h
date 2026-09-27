/* Adapted from Shuttle Podcasts (podcasts@9ee6e0954) mobile/ios/Spine/Sources/CStreamDecode/include/stream_decode.h — see ios/Playback/README.md. S2 additions are marked "S2:". */
/*
 * stream_decode.h — the PLAYBACK decoder, for iOS.
 *
 * A streaming sibling of `CSpineDecode/spine_decode.c`. Same libraries, same AVIO trick, but the
 * two have opposite jobs and must not be merged:
 *
 *   - `spine_decode.c` takes a whole span in memory and hands back 8 kHz mono float32, because the
 *     ad-skip matcher's PCM is defined by `segment-detection/spine/audio.py` and any other rate
 *     would not be that PCM.
 *   - this takes a *byte reader* — a file, or an HTTP range transaction that may block for a
 *     second — and hands back the source's OWN rate and channel count, interleaved float32,
 *     a chunk at a time, seekably, cancellably. That is what a player schedules; resampling it
 *     would be a second, lossy, pointless conversion.
 *
 * Plan of record: `mobile/ios/docs/plans/2026-09-09-streaming-audio-pipeline.md` §0 (Phase 1 is
 * the whole player), §1 (the picture), §3 (the `moov`-after-`mdat` bandwidth trap), §5 (position
 * is media time; after a seek it comes from the first decoded packet's pts), §6 Phase 1.
 *
 * THE SEEK CALLBACK IS NOT OPTIONAL. With a read callback alone `pb->seekable` is 0, and
 * libavformat's `mov` demuxer then read-discards the entire `mdat` to reach a trailing `moov`
 * (`aviobuf.c` forward-seek path, `mov.c` retry gated on AVIO_SEEKABLE_NORMAL). On a 60 MB episode
 * that is 60 MB of cellular data to learn where the audio starts. `StreamDecodeTests` measures it.
 *
 * Threading: one decoder is driven by exactly one thread. `stream_decoder_cancel` is the single
 * exception — it may be called from any thread and only sets a flag the callbacks read.
 */
#ifndef STREAM_DECODE_H
#define STREAM_DECODE_H

#include <stddef.h>
#include <stdint.h>

typedef enum {
    STREAM_DECODE_OK = 0,
    STREAM_DECODE_EOF = 1,          /* the stream ended; not an error */
    STREAM_DECODE_ERR_ALLOC = 2,
    STREAM_DECODE_ERR_OPEN = 3,     /* not a container this build can demux */
    STREAM_DECODE_ERR_NO_AUDIO = 4, /* container opened, no audio stream */
    STREAM_DECODE_ERR_DECODER = 5,  /* no decoder for the codec, or it would not open */
    STREAM_DECODE_ERR_RESAMPLE = 6,
    STREAM_DECODE_ERR_IO = 7,       /* the reader could not deliver bytes */
    STREAM_DECODE_ERR_SEEK = 8,     /* avformat_seek_file refused */
    STREAM_DECODE_ERR_CANCELLED = 9,
    STREAM_DECODE_ERR_ARGS = 10,
    /* The caller asked for the current read to come back so it could seek. Unlike a cancel this is
     * not terminal: the decoder stays open and the next `stream_decoder_seek` clears it. */
    STREAM_DECODE_ERR_INTERRUPTED = 11
} StreamDecodeStatus;

/**
 * The byte source, as C. `opaque` is passed back untouched; the Swift wrapper puts an unmanaged
 * pointer to the box holding its `StreamByteReader` there.
 *
 * `read` MUST NOT return 0: libavformat treats a 0-length read as "try again" and spins. Report
 * end of stream as `STREAM_READ_EOF`, a cancel as `STREAM_READ_CANCELLED`, an interruption as
 * `STREAM_READ_INTERRUPTED`, and any transport failure as `STREAM_READ_ERROR`; anything > 0 is a
 * byte count.
 */
#define STREAM_READ_EOF         (-1)
#define STREAM_READ_CANCELLED   (-2)
#define STREAM_READ_ERROR       (-3)
/* The reader was interrupted so the caller could seek. Recoverable; see
 * `stream_decoder_interrupt`. */
#define STREAM_READ_INTERRUPTED (-4)

typedef struct {
    /** Copy up to `n` bytes at the current position into `buf`; advance by the count. */
    int (*read)(void *opaque, uint8_t *buf, int n);
    /** Move the current position to `offset`. 0 on success, `STREAM_READ_*` otherwise. */
    int (*seek)(void *opaque, int64_t offset);
    /** Total length in bytes, or < 0 when unknown (a live stream, or chunked with no length). */
    int64_t (*size)(void *opaque);
} StreamDecodeCallbacks;

/** What the container says about the audio, filled in by `stream_decoder_open`. */
typedef struct {
    int    sample_rate;      /* the SOURCE's rate; the player runs at it */
    int    channel_count;
    double duration_sec;     /* 0 when the container does not know (AV_NOPTS_VALUE) */
    char   codec_name[32];   /* "mp3", "aac", ... */
    char   container_name[64];
} StreamAudioInfo;

typedef struct StreamDecoder StreamDecoder;

/**
 * Open `callbacks` as an audio stream. Returns a handle on success and writes `info`; returns NULL
 * on failure and writes the reason to `status`.
 *
 * A failed open is ALWAYS a failure, never silence: the player above turns it into the `AVPlayer`
 * fallback with a counter (plan §1), and a decoder that opened nothing but reported success would
 * present as an episode that plays no audio and never ends.
 */
StreamDecoder *stream_decoder_open(const StreamDecodeCallbacks *callbacks,
                                   void *opaque,
                                   StreamAudioInfo *info,
                                   int *status);

/**
 * Seek to `seconds` and report where the stream actually landed in `landed_seconds`.
 *
 * The landed value is the `best_effort_timestamp` of the first frame decoded after the seek, in
 * seconds — NOT the number that was asked for (plan §5.1). MP3 without a TOC lands on a frame
 * boundary near a bitrate estimate; MP4 lands on the sample table's keyframe. The caller's
 * position must follow the audio, not the request, or the scrubber lies and every ad-skip seek is
 * computed against a time nobody played.
 *
 * The frame decoded to find that timestamp is held and returned by the next `read`, so no audio is
 * lost to the probe. Returns a `StreamDecodeStatus`.
 *
 * Clears any interruption first: an interrupt exists precisely so that the seek that follows it can
 * run, so leaving the flag latched would make every interrupted read permanent.
 */
int stream_decoder_seek(StreamDecoder *decoder, double seconds, double *landed_seconds);

/**
 * Fill `out` with up to `max_frames` frames of interleaved float32 in [-1, 1] at the source's rate
 * and channel count. `out` must hold `max_frames * info.channel_count` floats.
 *
 * Writes the frame count to `frames`. Zero frames is not by itself an error: the status says
 * whether it was `STREAM_DECODE_EOF`, a cancel, or an IO failure. Returns a `StreamDecodeStatus`.
 */
int stream_decoder_read(StreamDecoder *decoder, float *out, int max_frames, int *frames);

/**
 * S2: convert everything this decoder hands out to `sample_rate` Hz and `channels` channels
 * (swresample; mono is spread to both sides at full level, more than two channels are downmixed).
 * Call after `stream_decoder_open` and before the first read; refused with
 * `STREAM_DECODE_ERR_ARGS` while decoded audio is pending. `info` keeps describing the SOURCE.
 * A seek's landed time stays in media seconds, whatever the output rate.
 */
int stream_decoder_set_output(StreamDecoder *decoder, int sample_rate, int channels);

/** Bytes the reader has been asked for since the decoder opened. Diagnostics and tests only. */
int64_t stream_decoder_position_bytes(const StreamDecoder *decoder);

/**
 * Override the byte budget one seek may spend before it is abandoned for the byte estimate.
 * **Tests only.** Everything the fixtures contain seeks by an index or a table of contents and
 * lands well inside the real budget, so the fallback — and the AVIO state the abandoned seek
 * leaves behind — is otherwise only reachable on a container no test holds. A value <= 0 restores
 * the default.
 */
void stream_decoder_set_seek_budget_bytes(StreamDecoder *decoder, int64_t bytes);

/**
 * Abort any blocked or future callback. Safe from any thread, idempotent. The reader's own
 * `cancel` still has to unblock a call that is already waiting; this only stops the decoder
 * starting another one.
 */
void stream_decoder_cancel(StreamDecoder *decoder);

/**
 * Make the callback that is running right now return promptly, WITHOUT ending the decode.
 *
 * The seek race this exists for: the player's pull loop can be blocked inside a read on a stalled
 * connection, and a seek queued behind it would not be applied until the network answered — which
 * on a dead link is never. Cancelling would answer the seek but destroy the decoder; this leaves it
 * open and reusable, and `stream_decoder_seek` clears the flag. `stream_decoder_read` then reports
 * `STREAM_DECODE_ERR_INTERRUPTED`, which the caller must not read as end of stream.
 *
 * Safe from any thread, idempotent. The reader's own `interrupt` still has to unblock a call that
 * is already waiting.
 */
void stream_decoder_interrupt(StreamDecoder *decoder);

/** Clear an interruption without seeking. Safe on NULL. */
void stream_decoder_clear_interrupt(StreamDecoder *decoder);

/** Release everything. Safe on NULL. */
void stream_decoder_close(StreamDecoder *decoder);

#endif
