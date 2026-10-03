/* S2: the tag reader for local files (#590). Not part of the Podcasts decoder this target was adapted from. */
/*
 * tag_read.h — a local audio file's tags, audio properties and embedded picture, read with the same
 * libavformat build the player decodes with, so every format the player plays (MP3, AAC/ALAC in MP4,
 * FLAC, Ogg Vorbis/Opus, WAV, AIFF, Matroska) is a format whose tags the library can read.
 *
 * The file is read through a plain stdio AVIO context (this FFmpeg build has no protocols), and
 * nothing is decoded: only the demuxer's header, plus `avformat_find_stream_info` when the header
 * leaves the duration or the sample rate unknown.
 *
 * Threading: every call is self-contained; any number may run at once on different threads.
 */
#ifndef S2_TAG_READ_H
#define S2_TAG_READ_H

#include <stddef.h>
#include <stdint.h>

/** The audio stream's properties; 0 for one the container doesn't say. */
typedef struct {
    int64_t duration_ms;
    int32_t sample_rate;
    int32_t channels;
    int32_t bit_depth;
    /** Bits per second. */
    int64_t bit_rate;
    /** libavcodec's name for the codec ("flac", "alac", "mp3"); empty when unknown. */
    char codec[32];
} S2AudioProperties;

/** One tag as libavformat names it ("title", "album_artist", "REPLAYGAIN_TRACK_GAIN"). */
typedef void (*S2TagCallback)(void *context, const char *key, const char *value);

/**
 * Reads `path`'s tags, calling `on_tag` once for each (the container's, then the audio stream's), and
 * fills `properties`. Returns 0, or a negative value when the file can't be opened or isn't audio.
 */
int s2_read_tags(const char *path, void *context, S2TagCallback on_tag, S2AudioProperties *properties);

/**
 * The first picture embedded in `path` (ID3 APIC, MP4 `covr`, FLAC or Vorbis picture block), as the
 * encoded image bytes, which the caller frees with `s2_free_picture`. Returns 0 and sets `*data` and
 * `*size`, or a negative value when there is none.
 */
int s2_read_picture(const char *path, uint8_t **data, size_t *size);

void s2_free_picture(uint8_t *data);

#endif
