"""Unit tests for generate-strings.py: `python3 -m unittest discover -s ios/scripts -p 'test_*.py'`."""

import importlib.util
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("generate_strings", Path(__file__).with_name("generate-strings.py"))
gs = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gs)


class AppleLocaleTest(unittest.TestCase):
    def test_language_and_region(self):
        self.assertEqual(gs.apple_locale("de"), "de")
        self.assertEqual(gs.apple_locale("pt-rBR"), "pt-BR")
        self.assertEqual(gs.apple_locale("en-rGB"), "en-GB")

    def test_apple_names(self):
        self.assertEqual(gs.apple_locale("in"), "id")
        self.assertEqual(gs.apple_locale("iw"), "he")
        self.assertEqual(gs.apple_locale("zh-rCN"), "zh-Hans")
        self.assertEqual(gs.apple_locale("zh-rTW"), "zh-Hant")

    def test_script_qualifiers_and_renames(self):
        self.assertEqual(gs.apple_locale("b+sr+Latn"), "sr-Latn")
        self.assertEqual(gs.apple_locale("zh"), "zh-Hans")
        self.assertEqual(gs.apple_locale("tl"), "fil")

    def test_non_language_qualifiers(self):
        for qualifier in ("night", "v31", "night-v31", "sw600dp", "land"):
            self.assertIsNone(gs.apple_locale(qualifier), qualifier)


class TextTest(unittest.TestCase):
    def test_placeholders(self):
        self.assertEqual(gs.ios_text("%1$s of %2$d: %s"), "%1$@ of %2$d: %@")

    def test_escapes_and_whitespace(self):
        self.assertEqual(gs.ios_text("Couldn\\'t\n      play \\u00e9"), "Couldn't play é")
        self.assertEqual(gs.ios_text('"  kept  "'), "  kept  ")


    def test_android_backslash_escapes(self):
        self.assertEqual(gs.ios_text("\u201e%1$s\\\u201c"), "\u201e%1$@\u201c")  # German `\\u201c`: the backslash is dropped
        self.assertEqual(gs.ios_text("a\\\\b"), "a\\b")
        self.assertEqual(gs.ios_text("\\q\\t\\@\\?"), "q\t@?")


class PluralFallbackTest(unittest.TestCase):
    english = {"one": "%1$d song", "other": "%1$d songs"}

    def test_other_only_where_one_is_not_exactly_one(self):
        for locale in ("ru", "fr", "pt-BR", "hi", "pl", "ja", "zh-Hans"):
            self.assertEqual(gs.plural_fallback(self.english, locale), {"other": "%1$d songs"}, locale)

    def test_keeps_one_where_it_is_exactly_one(self):
        for locale in ("en", "de", "nl", "it", "es", "sv"):
            self.assertEqual(gs.plural_fallback(self.english, locale), self.english, locale)

    def test_ios_only_plural_in_every_locale(self):
        files = gs.render_all()
        for name, content in files.items():
            if name.endswith(".stringsdict"):
                self.assertIn("Couldn't download %lld songs", content, name)


class StringsdictTest(unittest.TestCase):
    def test_variants_in_quantity_order(self):
        plist = gs.render_stringsdict([("songs", {"other": "%1$d songs", "one": "%1$d song", "few": "%1$d s"})])
        self.assertIn("<key>songs</key>", plist)
        self.assertIn("<string>%#@count@</string>", plist)
        self.assertIn("<string>NSStringPluralRuleType</string>", plist)
        self.assertLess(plist.index("<key>one</key>"), plist.index("<key>few</key>"))
        self.assertLess(plist.index("<key>few</key>"), plist.index("<key>other</key>"))
        self.assertIn("<string>d</string>", plist)

    def test_escapes_xml_and_uses_lld_for_swift_keys(self):
        plist = gs.render_stringsdict([("a & b %lld", {"one": "<1> %lld", "other": "<n> %lld"})])
        self.assertIn("<key>a &amp; b %lld</key>", plist)
        self.assertIn("&lt;n&gt; %lld", plist)
        self.assertIn("<string>lld</string>", plist)


class GeneratedFilesTest(unittest.TestCase):
    def test_every_locale_has_every_key(self):
        files = gs.render_all()
        english = files["en.lproj/Localizable.strings"]
        key_lines = [line.split(" = ")[0] for line in english.splitlines() if line.startswith('"')]
        self.assertGreater(len(key_lines), 100)
        for name, content in files.items():
            if name.endswith(".strings"):
                self.assertEqual([l.split(" = ")[0] for l in content.splitlines() if l.startswith('"')], key_lines, name)

    def test_translated_locales_present(self):
        names = gs.render_all()
        for folder in ("de", "pt-BR", "zh-Hans", "en-GB"):
            self.assertIn(f"{folder}.lproj/Localizable.stringsdict", names)
        self.assertNotIn("night.lproj/Localizable.strings", names)


if __name__ == "__main__":
    unittest.main()
