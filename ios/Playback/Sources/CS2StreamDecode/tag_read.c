/* S2: the tag reader for local files (#590); see include/tag_read.h. */
#include "tag_read.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libavutil/dict.h>
#include <libavutil/mem.h>

typedef struct {
    AVFormatContext *fmt;
    AVIOContext *avio;
    FILE *file;
} TagFile;

static int file_read(void *opaque, uint8_t *buf, int size) {
    size_t n = fread(buf, 1, (size_t)size, (FILE *)opaque);
    return n > 0 ? (int)n : AVERROR_EOF;
}

static int64_t file_seek(void *opaque, int64_t offset, int whence) {
    FILE *file = (FILE *)opaque;
    if (whence & AVSEEK_SIZE) {
        off_t here = ftello(file);
        if (fseeko(file, 0, SEEK_END) != 0) return -1;
        off_t size = ftello(file);
        fseeko(file, here, SEEK_SET);
        return size;
    }
    if (fseeko(file, (off_t)offset, whence & ~AVSEEK_FORCE) != 0) return -1;
    return ftello(file);
}

static void tag_file_close(TagFile *t) {
    if (t->fmt) avformat_close_input(&t->fmt);
    if (t->avio) {
        av_freep(&t->avio->buffer);
        avio_context_free(&t->avio);
    }
    if (t->file) fclose(t->file);
    memset(t, 0, sizeof(*t));
}

/* Opens `path` and reads the container's header; 0 on success. */
static int tag_file_open(const char *path, TagFile *t) {
    memset(t, 0, sizeof(*t));
    av_log_set_level(AV_LOG_QUIET);
    t->file = fopen(path, "rb");
    if (!t->file) return -1;

    const int buf_size = 32 * 1024;
    uint8_t *buf = (uint8_t *)av_malloc(buf_size);
    if (!buf) { tag_file_close(t); return -1; }
    t->avio = avio_alloc_context(buf, buf_size, 0, t->file, file_read, NULL, file_seek);
    if (!t->avio) { av_free(buf); tag_file_close(t); return -1; }

    t->fmt = avformat_alloc_context();
    if (!t->fmt) { tag_file_close(t); return -1; }
    t->fmt->pb = t->avio;
    if (avformat_open_input(&t->fmt, NULL, NULL, NULL) < 0) {
        t->fmt = NULL; /* freed by avformat_open_input; the AVIO context is still ours */
        tag_file_close(t);
        return -1;
    }
    return 0;
}

static void report_tags(AVDictionary *dict, void *context, S2TagCallback on_tag) {
    const AVDictionaryEntry *entry = NULL;
    while ((entry = av_dict_iterate(dict, entry))) {
        if (entry->key && entry->value) on_tag(context, entry->key, entry->value);
    }
}

int s2_read_tags(const char *path, void *context, S2TagCallback on_tag, S2AudioProperties *properties) {
    if (!path || !on_tag || !properties) return -1;
    memset(properties, 0, sizeof(*properties));

    TagFile t;
    if (tag_file_open(path, &t) != 0) return -1;

    int audio = av_find_best_stream(t.fmt, AVMEDIA_TYPE_AUDIO, -1, -1, NULL, 0);
    if (audio < 0 || t.fmt->duration == AV_NOPTS_VALUE || t.fmt->streams[audio]->codecpar->sample_rate == 0) {
        /* The header didn't say enough (a raw AAC stream, say): read a little of the audio. */
        (void)avformat_find_stream_info(t.fmt, NULL);
        audio = av_find_best_stream(t.fmt, AVMEDIA_TYPE_AUDIO, -1, -1, NULL, 0);
    }
    if (audio < 0) { tag_file_close(&t); return -2; }

    AVStream *stream = t.fmt->streams[audio];
    AVCodecParameters *par = stream->codecpar;
    if (t.fmt->duration != AV_NOPTS_VALUE && t.fmt->duration > 0) {
        properties->duration_ms = t.fmt->duration / (AV_TIME_BASE / 1000);
    } else if (stream->duration != AV_NOPTS_VALUE && stream->duration > 0) {
        properties->duration_ms = av_rescale_q(stream->duration, stream->time_base, (AVRational){1, 1000});
    }
    properties->sample_rate = par->sample_rate;
    properties->channels = par->ch_layout.nb_channels;
    /* FLAC and ALAC say their depth as the raw sample size; PCM's is its codec's. Lossy codecs have none. */
    properties->bit_depth = par->bits_per_raw_sample > 0 ? par->bits_per_raw_sample : av_get_bits_per_sample(par->codec_id);
    properties->bit_rate = par->bit_rate > 0 ? par->bit_rate : t.fmt->bit_rate;
    const char *codec = avcodec_get_name(par->codec_id);
    if (codec) strncpy(properties->codec, codec, sizeof(properties->codec) - 1);

    /* The container's tags first; Ogg and some Matroska files keep theirs on the stream. */
    report_tags(t.fmt->metadata, context, on_tag);
    report_tags(stream->metadata, context, on_tag);

    tag_file_close(&t);
    return 0;
}

int s2_read_picture(const char *path, uint8_t **data, size_t *size) {
    if (!path || !data || !size) return -1;
    *data = NULL;
    *size = 0;

    TagFile t;
    if (tag_file_open(path, &t) != 0) return -1;

    int found = -2;
    for (unsigned i = 0; i < t.fmt->nb_streams; i++) {
        AVStream *stream = t.fmt->streams[i];
        if (!(stream->disposition & AV_DISPOSITION_ATTACHED_PIC)) continue;
        AVPacket *picture = &stream->attached_pic;
        if (picture->size <= 0 || !picture->data) continue;
        uint8_t *copy = (uint8_t *)malloc((size_t)picture->size);
        if (!copy) break;
        memcpy(copy, picture->data, (size_t)picture->size);
        *data = copy;
        *size = (size_t)picture->size;
        found = 0;
        break;
    }
    tag_file_close(&t);
    return found;
}

void s2_free_picture(uint8_t *data) {
    free(data);
}
