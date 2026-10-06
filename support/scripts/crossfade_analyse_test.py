#!/usr/bin/env python3
"""Tests for crossfade-analyse.py against synthetic tap WAVs. Run: python3 -I support/scripts/crossfade_analyse_test.py"""
import importlib.util
import math
import os
import struct
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("crossfade_analyse", os.path.join(os.path.dirname(os.path.abspath(__file__)), "crossfade-analyse.py"))
analyse_mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(analyse_mod)

RATE = 8000
FREQS = [330.0, 550.0, 770.0]
SONG_S = 10
FADE_S = 3
AMP = 0.25


def curves(kind):
    """(fade-out gain, fade-in gain) at progress 0..1."""
    if kind == "equal-power":
        return lambda p: math.cos(p * math.pi / 2), lambda p: math.sin(p * math.pi / 2)
    if kind == "linear":
        return lambda p: 1 - p, lambda p: p
    if kind == "none":
        return lambda p: 1.0, lambda p: 1.0
    raise ValueError(kind)


def render(fade_s=FADE_S, kind="equal-power", gap_at=None, gap_ms=0, gain=1.0):
    """Mono samples for three tone songs joined with a crossfade of fade_s seconds."""
    fade_out, fade_in = curves(kind)
    step = SONG_S - fade_s
    total = int((len(FREQS) * SONG_S - (len(FREQS) - 1) * fade_s) * RATE)
    samples = []
    for n in range(total):
        t = n / RATE
        value = 0.0
        for i, freq in enumerate(FREQS):
            local = t - i * step
            if not 0 <= local < SONG_S:
                continue
            g = 1.0
            if i > 0 and local < fade_s:
                g = fade_in(local / fade_s)
            if i < len(FREQS) - 1 and local >= SONG_S - fade_s:
                g = fade_out((local - (SONG_S - fade_s)) / fade_s)
            value += g * AMP * math.sin(2 * math.pi * freq * local)
        samples.append(value * gain)
    if gap_at is not None:
        for n in range(int(gap_at * RATE), int((gap_at + gap_ms / 1000) * RATE)):
            samples[n] = 0.0
    return samples


def write_wav(path, samples, bits=16, channels=2):
    scale = 2 ** (bits - 1)
    body = bytearray()
    for v in samples:
        q = max(-scale, min(scale - 1, int(round(v * scale))))
        frame = q.to_bytes(bits // 8, "little", signed=True)
        body += frame * channels
    width = bits // 8 * channels
    header = b"RIFF" + struct.pack("<I", 36 + len(body)) + b"WAVEfmt " + struct.pack("<IHHIIHH", 16, 1, channels, RATE, RATE * width, width, bits)
    with open(path, "wb") as f:
        f.write(header + b"data" + struct.pack("<I", len(body)) + bytes(body))


def run(samples, crossfade_ms=FADE_S * 1000, bits=16):
    with tempfile.TemporaryDirectory() as d:
        path = os.path.join(d, "tap.wav")
        write_wav(path, samples, bits)
        rate, channels, data = analyse_mod.read_wav(path)
        return analyse_mod.analyse(rate, channels, data, FREQS, crossfade_ms)


class CrossfadeAnalyseTest(unittest.TestCase):
    def test_clean_equal_power_crossfade_passes_at_16_and_24_bit(self):
        samples = render()
        for bits in (16, 24):
            report = run(samples, bits=bits)
            self.assertEqual([], report["failures"], f"{bits}-bit")
            self.assertEqual(2, len(report["transitions"]))
            for t in report["transitions"]:
                self.assertAlmostEqual(FADE_S * 1000, t["overlap_ms"], delta=300)

    def test_overlap_shorter_than_the_setting_fails(self):
        report = run(render(fade_s=1), crossfade_ms=3000)
        self.assertTrue(any("overlap" in f for f in report["failures"]), report["failures"])

    def test_linear_fade_is_flagged_as_not_equal_power(self):
        report = run(render(kind="linear"))
        self.assertTrue(any("equal-power" in f for f in report["failures"]), report["failures"])

    def test_no_fade_curve_is_flagged(self):
        report = run(render(kind="none"))
        self.assertTrue(any("monotonic" in f or "louder" in f or "quieter" in f for f in report["failures"]), report["failures"])

    def test_hard_cut_with_no_overlap_fails(self):
        report = run(render(fade_s=0, kind="none"))
        self.assertTrue(any("no overlap" in f for f in report["failures"]), report["failures"])

    def test_silence_inside_the_audio_is_a_gap(self):
        report = run(render(gap_at=4.0, gap_ms=40))
        self.assertEqual(1, len(report["gaps"]))
        self.assertAlmostEqual(40, report["gaps"][0]["ms"], delta=5)
        self.assertTrue(any("gap" in f for f in report["failures"]))

    def test_leading_and_trailing_silence_is_not_a_gap(self):
        samples = [0.0] * RATE + render() + [0.0] * RATE
        self.assertEqual([], run(samples)["gaps"])

    def test_clipping_is_counted(self):
        report = run(render(gain=5.0))
        self.assertGreater(report["clipped_samples"], 0)
        self.assertTrue(any("clipped" in f for f in report["failures"]))

    def test_a_song_missing_from_the_tap_fails(self):
        report = analyse_mod.analyse(RATE, 1, [[0.0] * RATE * 2], FREQS, 3000)
        self.assertTrue(any("silent" in f for f in report["failures"]))
        self.assertTrue(any("never heard" in f for f in report["failures"]))

    def test_a_missing_song_with_neighbour_leakage_fails(self):
        # Songs 1 and 3 play, song 2 never does; its bin still sees a little of both neighbours.
        tone = lambda f, n: [AMP * math.sin(2 * math.pi * f * k / RATE) + 0.002 * math.sin(2 * math.pi * 550 * k / RATE) for k in range(n)]
        report = run(tone(FREQS[0], SONG_S * RATE) + tone(FREQS[2], SONG_S * RATE), crossfade_ms=0)
        self.assertTrue(any("song 2" in f and "never heard" in f for f in report["failures"]))

    def test_unaligned_hard_cut_passes_with_no_crossfade(self):
        # Cuts half a window off the 20 ms grid: one window holds both tones, which is not an overlap.
        lengths = [SONG_S * RATE + 80, SONG_S * RATE + 80, SONG_S * RATE]
        samples = []
        for freq, n in zip(FREQS, lengths):
            samples += [AMP * math.sin(2 * math.pi * freq * k / RATE) for k in range(n)]
        report = run(samples, crossfade_ms=0)
        self.assertEqual([], report["failures"])


if __name__ == "__main__":
    unittest.main()
