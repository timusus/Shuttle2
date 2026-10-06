#!/usr/bin/env python3
"""Checks a crossfade tap WAV (DebugPlaybackReceiver TAP_START/TAP_STOP) against tone songs.

    support/scripts/crossfade-analyse.py tap.wav --freqs 330,550,770 --crossfade-ms 3000 [--json]

The songs are sine tones (support/scripts/seed-test-media.sh crossfade), one frequency each, in
play order. Per transition between consecutive songs it reports:
  overlap     how long both tones are audible (amplitude above OVERLAP_FLOOR of their steady level)
              against the crossfade setting
  envelope    the outgoing tone's amplitude must fall and the incoming one's rise (monotonic, within
              MONOTONIC_SLACK); equal-power curves keep out^2 + in^2 near 1 across the overlap
and over the whole file:
  gaps        runs of near-silence (both channels under GAP_LEVEL for GAP_MIN_MS) inside the audio
  clipping    samples at full scale
Exits 1 when any check fails. Stdlib only.
"""
import argparse
import array
import json
import math
import struct
import sys

WINDOW_MS = 20
OVERLAP_FLOOR = 0.03
OVERLAP_TOLERANCE = 0.10
OVERLAP_TOLERANCE_MIN_MS = 150
MONOTONIC_SLACK = 0.15
POWER_MIN, POWER_MAX = 0.7, 1.3
GAP_LEVEL = 0.001
GAP_MIN_MS = 2
CLIP_LEVEL = 0.999


def read_wav(path):
    """Returns (sample_rate, channels, samples) with samples as one list of floats per channel in -1..1."""
    with open(path, "rb") as f:
        data = f.read()
    if data[0:4] != b"RIFF" or data[8:12] != b"WAVE":
        raise ValueError("not a WAV file")
    pos, fmt, pcm = 12, None, None
    while pos + 8 <= len(data):
        chunk_id, size = data[pos:pos + 4], struct.unpack_from("<I", data, pos + 4)[0]
        body = pos + 8
        if chunk_id == b"fmt ":
            fmt = struct.unpack_from("<HHIIHH", data, body)
        elif chunk_id == b"data":
            # A tap that was never closed leaves size 0 or garbage; trust the file's length instead.
            pcm = data[body:body + size] if 0 < size <= len(data) - body else data[body:]
            break
        pos = body + size + (size & 1)
    if fmt is None or pcm is None:
        raise ValueError("WAV has no fmt or data chunk")
    tag, channels, rate, _, _, bits = fmt
    if tag != 1 or bits not in (16, 24):
        raise ValueError(f"unsupported WAV format tag={tag} bits={bits}")
    width = bits // 8
    frames = len(pcm) // (width * channels)
    pcm = pcm[:frames * width * channels]
    if bits == 16:
        raw = array.array("h")
        raw.frombytes(pcm)
        if sys.byteorder == "big":
            raw.byteswap()
        scale = 32768.0
        flat = [v / scale for v in raw]
    else:
        scale = 8388608.0
        flat = []
        for i in range(0, len(pcm), 3):
            v = pcm[i] | (pcm[i + 1] << 8) | (pcm[i + 2] << 16)
            flat.append((v - 0x1000000 if v & 0x800000 else v) / scale)
    return rate, channels, [flat[c::channels] for c in range(channels)]


def goertzel_amplitudes(mono, rate, freqs, window):
    """Amplitude (peak, as for a sine) of each frequency in each consecutive window: [freq][window]."""
    windows = len(mono) // window
    # Hann-weighted so a neighbouring song's tone doesn't leak into this one's bin.
    hann = [0.5 - 0.5 * math.cos(2 * math.pi * n / window) for n in range(window)]
    out = []
    for freq in freqs:
        coeff = 2 * math.cos(2 * math.pi * freq / rate)
        amps = []
        for w in range(windows):
            s1 = s2 = 0.0
            for x, h in zip(mono[w * window:(w + 1) * window], hann):
                s1, s2 = x * h + coeff * s1 - s2, s1
            power = s1 * s1 + s2 * s2 - coeff * s1 * s2
            amps.append(2 * math.sqrt(max(power, 0.0)) / window)
        out.append(amps)
    return out


def steady_level(amps):
    top = sorted(amps)[int(len(amps) * 0.9):]
    return sum(top) / len(top) if top else 0.0


def find_runs(flags, min_len):
    runs, start = [], None
    for i, flag in enumerate(flags + [False]):
        if flag and start is None:
            start = i
        elif not flag and start is not None:
            if i - start >= min_len:
                runs.append((start, i))
            start = None
    return runs


def analyse(rate, channels, samples, freqs, crossfade_ms):
    window = rate * WINDOW_MS // 1000
    window_s = window / rate
    mono = [sum(ch[i] for ch in samples) / channels for i in range(len(samples[0]))] if channels > 1 else samples[0]
    amps = goertzel_amplitudes(mono, rate, freqs, window)
    levels = [steady_level(a) for a in amps]
    norm = [[v / lvl if lvl > 0 else 0.0 for v in a] for a, lvl in zip(amps, levels)]
    failures = []

    for i, lvl in enumerate(levels):
        if lvl <= 0.0:
            failures.append(f"song {i + 1} ({freqs[i]} Hz) never heard in the tap")

    transitions = []
    for i in range(len(freqs) - 1):
        out_env, in_env = norm[i], norm[i + 1]
        both = [a > OVERLAP_FLOOR and b > OVERLAP_FLOOR for a, b in zip(out_env, in_env)]
        runs = find_runs(both, 1)
        t = {"from_hz": freqs[i], "to_hz": freqs[i + 1]}
        if not runs:
            t["problems"] = ["no overlap: the songs never play together"]
            failures.append(f"transition {i + 1}: no overlap ({freqs[i]} -> {freqs[i + 1]} Hz)")
            transitions.append(t)
            continue
        start, end = max(runs, key=lambda r: r[1] - r[0])
        overlap_ms = (end - start) * window_s * 1000
        # The floor trims a little off each end of a curve; the expected visible overlap is correspondingly shorter.
        trim = 2 * math.asin(OVERLAP_FLOOR) / (math.pi / 2)
        expected = crossfade_ms * (1 - trim)
        tolerance = max(OVERLAP_TOLERANCE_MIN_MS, OVERLAP_TOLERANCE * crossfade_ms)
        problems = []
        if abs(overlap_ms - expected) > tolerance:
            problems.append(f"overlap {overlap_ms:.0f} ms, expected {expected:.0f} +/- {tolerance:.0f} ms (crossfade {crossfade_ms} ms)")
        seg_out, seg_in = out_env[start:end], in_env[start:end]
        worst_out = max((b - a for a, b in zip(seg_out, seg_out[1:])), default=0.0)
        worst_in = max((a - b for a, b in zip(seg_in, seg_in[1:])), default=0.0)
        if worst_out > MONOTONIC_SLACK:
            problems.append(f"fade-out is not monotonic (rises {worst_out:.2f})")
        if worst_in > MONOTONIC_SLACK:
            problems.append(f"fade-in is not monotonic (falls {worst_in:.2f})")
        if seg_out and seg_out[0] < seg_out[-1]:
            problems.append("outgoing song gets louder across the overlap")
        if seg_in and seg_in[0] > seg_in[-1]:
            problems.append("incoming song gets quieter across the overlap")
        # Skip the outer 10% of the overlap, where the floor and window edges dominate.
        trim_w = max(1, len(seg_out) // 10)
        power = [a * a + b * b for a, b in zip(seg_out[trim_w:-trim_w], seg_in[trim_w:-trim_w])]
        if power and not all(POWER_MIN <= p <= POWER_MAX for p in power):
            problems.append(f"not equal-power: out^2+in^2 ranges {min(power):.2f}..{max(power):.2f} (want {POWER_MIN}..{POWER_MAX})")
        t.update({
            "overlap_ms": round(overlap_ms),
            "expected_overlap_ms": round(expected),
            "start_s": round(start * window_s, 3),
            "power_min": round(min(power), 2) if power else None,
            "power_max": round(max(power), 2) if power else None,
            "problems": problems,
        })
        failures += [f"transition {i + 1}: {p}" for p in problems]
        transitions.append(t)

    frames = len(samples[0])
    quiet = [all(abs(ch[n]) < GAP_LEVEL for ch in samples) for n in range(frames)]
    audible = [n for n, q in enumerate(quiet) if not q]
    gaps = []
    if audible:
        first, last = audible[0], audible[-1]
        for s, e in find_runs(quiet, rate * GAP_MIN_MS // 1000):
            if s > first and e <= last:
                gaps.append({"at_s": round(s / rate, 3), "ms": round((e - s) * 1000 / rate, 1)})
    else:
        failures.append("the tap is silent")
    for gap in gaps:
        failures.append(f"gap of {gap['ms']} ms at {gap['at_s']} s")
    clipped = sum(1 for ch in samples for v in ch if abs(v) >= CLIP_LEVEL)
    if clipped:
        failures.append(f"{clipped} clipped samples")

    return {
        "sample_rate": rate,
        "channels": channels,
        "duration_s": round(frames / rate, 2),
        "steady_amplitude": [round(v, 4) for v in levels],
        "transitions": transitions,
        "gaps": gaps,
        "clipped_samples": clipped,
        "failures": failures,
    }


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("wav")
    parser.add_argument("--freqs", required=True, help="comma-separated tone frequencies in play order")
    parser.add_argument("--crossfade-ms", type=int, required=True)
    parser.add_argument("--json", action="store_true", help="print the report as JSON")
    args = parser.parse_args(argv)
    freqs = [float(f) for f in args.freqs.split(",")]
    rate, channels, samples = read_wav(args.wav)
    report = analyse(rate, channels, samples, freqs, args.crossfade_ms)
    if args.json:
        print(json.dumps(report, indent=2))
    else:
        print(f"{args.wav}: {report['duration_s']} s, {rate} Hz, {channels} ch")
        for i, t in enumerate(report["transitions"], 1):
            detail = f"overlap {t.get('overlap_ms', '-')} ms (expected {t.get('expected_overlap_ms', '-')}), power {t.get('power_min')}..{t.get('power_max')}"
            print(f"  transition {i} {t['from_hz']:.0f}->{t['to_hz']:.0f} Hz: {detail}")
        print(f"  gaps: {len(report['gaps'])}, clipped samples: {report['clipped_samples']}")
        for failure in report["failures"]:
            print(f"  FAIL {failure}")
    return 1 if report["failures"] else 0


if __name__ == "__main__":
    sys.exit(main())
