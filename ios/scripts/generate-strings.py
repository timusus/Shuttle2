#!/usr/bin/env python3
"""Writes the iOS string tables under ios/S2/<locale>.lproj from Android's strings.xml files.

StringKey and PluralKey (android/presentation, ui/text/StringKey.kt) name the strings shared code shows; each
entry's lower-case name is both the Android resource name and the iOS table key, so the text has one source.
IOS_PLURALS adds the plurals the Swift code uses itself (kept by their Android resource name). Run it after adding
a key, changing its text or translating it:

    ios/scripts/generate-strings.py            # rewrite the files
    ios/scripts/generate-strings.py --check    # exit 1 if a file is stale or orphaned (nothing written)

Output, per locale: Localizable.strings (the strings) and Localizable.stringsdict (the plurals). The English
tables are `en.lproj`; every Android `values-<qualifier>` folder (a language, optionally with a region) that
translates at least one of these keys gets its own `<apple id>.lproj` (see apple_locale), in which keys it
doesn't translate fall back to the English text, because a bundle returns the bare key when the chosen
localization lacks it. Add each locale to `localizations` in ios/project.yml.

Android's `%1$s` becomes `%1$@` (UiText passes every non-count argument as a string); `%d` stays. A plural's
count is its first argument (`%1$d`).
"""

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from xml.sax.saxutils import escape

ROOT = Path(__file__).resolve().parents[2]
STRING_KEY = ROOT / "android/presentation/src/commonMain/kotlin/com/simplecityapps/shuttle/ui/text/StringKey.kt"
IOS_DIR = ROOT / "ios/S2"
PROJECT_YML = ROOT / "ios/project.yml"
TABLE = "Localizable"

# Plurals only the Swift code uses (Android resource names, matching the Swift call sites).
IOS_PLURALS = ["songsPlural", "albumsPlural", "paywall_status_trial"]

# Plurals that exist on iOS only (no Android resource), English only: key -> {quantity: text}. The key is the
# `String(localized:)` key of the call site.
IOS_ONLY_PLURALS = {
    "Couldn't download %lld songs. Check you're signed in to the server and try again.": {
        "one": "Couldn't download %lld song. Check you're signed in to the server and try again.",
        "other": "Couldn't download %lld songs. Check you're signed in to the server and try again.",
    },
}

# Android language qualifiers that Apple names differently.
LANGUAGE_IDS = {"in": "id", "iw": "he", "ji": "yi"}
# (language, region) pairs Apple names by script instead.
SCRIPT_IDS = {("zh", "CN"): "zh-Hans", ("zh", "SG"): "zh-Hans", ("zh", "TW"): "zh-Hant", ("zh", "HK"): "zh-HK"}


def enum_keys(source, enum_name):
    body = source[source.index(f"enum class {enum_name}"):]
    body = body[body.index("{") + 1:body.index("}")]
    body = re.sub(r"/\*.*?\*/", "", body, flags=re.S)
    body = re.sub(r"//[^\n]*", "", body).split(";")[0]
    return [name.strip().lower() for name in body.split(",") if name.strip()]


def string_keys():
    return enum_keys(STRING_KEY.read_text(), "StringKey")


def plural_keys():
    keys = enum_keys(STRING_KEY.read_text(), "PluralKey")
    return keys + [key for key in IOS_PLURALS if key not in keys]


def apple_locale(qualifier):
    """The Apple locale id for an Android `values-<qualifier>` suffix, or None when it isn't a language
    (night, v31, sw600dp, ...). `pt-rBR` -> `pt-BR`, `zh-rCN` -> `zh-Hans`, `in` -> `id`."""
    match = re.fullmatch(r"([a-z]{2,3})(?:-r([A-Z]{2}))?", qualifier)
    if not match:
        return None
    language, region = match.groups()
    if (language, region) in SCRIPT_IDS:
        return SCRIPT_IDS[(language, region)]
    language = LANGUAGE_IDS.get(language, language)
    return f"{language}-{region}" if region else language


def qualifier_of(values_dir):
    return values_dir.name[len("values-"):] if values_dir.name != "values" else ""


def parse_resources():
    """{'': {'strings': {...}, 'plurals': {...}}, '<qualifier>': ...} over every module's res/values* folders."""
    resources = {}
    for path in sorted(ROOT.glob("android/**/src/*/res/values*/*.xml")):
        if "/build/" in str(path):
            continue
        qualifier = qualifier_of(path.parent)
        if qualifier and apple_locale(qualifier) is None:
            continue
        table = resources.setdefault(qualifier, {"strings": {}, "plurals": {}})
        for element in ET.parse(path).getroot():
            if qualifier and element.get("translatable") == "false":
                continue
            if element.tag == "string":
                table["strings"][element.get("name")] = "".join(element.itertext())
            elif element.tag == "plurals":
                table["plurals"][element.get("name")] = {
                    item.get("quantity"): "".join(item.itertext()) for item in element.iter("item")
                }
    return resources


def ios_text(android):
    text = android.strip()
    if len(text) >= 2 and text[0] == '"' and text[-1] == '"':
        text = text[1:-1]
    else:
        text = re.sub(r"\s+", " ", text)  # Android collapses the whitespace of an unquoted string
    text = re.sub(r"\\u([0-9a-fA-F]{4})", lambda m: chr(int(m.group(1), 16)), text)
    text = text.replace("\\'", "'").replace('\\"', '"').replace("\\n", "\n").replace("\\@", "@").replace("\\?", "?")
    return re.sub(r"%(\d+\$)?s", lambda m: f"%{m.group(1) or ''}@", text)


def strings_literal(text):
    return text.replace("\\", "\\\\").replace('"', '\\"').replace("\n", "\\n")


def render_strings(keys, texts, locale):
    lines = [
        "/* Generated by ios/scripts/generate-strings.py from Android's strings.xml"
        + (": the English text of every StringKey. */" if locale == "en" else f": the {locale} translation. */"),
        "/* Don't edit; change the Android string and run the script. */",
        "",
    ]
    for key in keys:
        lines.append(f'"{key}" = "{strings_literal(ios_text(texts[key]))}";')
    return "\n".join(lines) + "\n"


def render_stringsdict(plurals):
    """A .stringsdict: each key's `%#@v@` picks the variant for its first argument, whose text holds the count as
    `%1$d` and the other arguments as `%N$@`."""
    lines = [
        '<?xml version="1.0" encoding="UTF-8"?>',
        '<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">',
        "<!-- Generated by ios/scripts/generate-strings.py from Android's strings.xml. Don't edit. -->",
        '<plist version="1.0">',
        "<dict>",
    ]
    for key, variants in plurals:
        lines += [
            f"\t<key>{escape(key)}</key>",
            "\t<dict>",
            "\t\t<key>NSStringLocalizedFormatKey</key>",
            "\t\t<string>%#@count@</string>",
            "\t\t<key>count</key>",
            "\t\t<dict>",
            "\t\t\t<key>NSStringFormatSpecTypeKey</key>",
            "\t\t\t<string>NSStringPluralRuleType</string>",
            "\t\t\t<key>NSStringFormatValueTypeKey</key>",
            f"\t\t\t<string>{'lld' if '%lld' in variants['other'] else 'd'}</string>",
        ]
        for quantity in ("zero", "one", "two", "few", "many", "other"):
            if quantity in variants:
                lines += [f"\t\t\t<key>{quantity}</key>", f"\t\t\t<string>{escape(variants[quantity])}</string>"]
        lines += ["\t\t</dict>", "\t</dict>"]
    lines += ["</dict>", "</plist>"]
    return "\n".join(lines) + "\n"


def render_all():
    """{relative path: content} for every generated file."""
    resources = parse_resources()
    english = resources[""]
    keys = string_keys()
    plurals = plural_keys()
    missing = [k for k in keys if k not in english["strings"]] + [k for k in plurals if k not in english["plurals"]]
    if missing:
        sys.exit(f"No Android resource for StringKey/PluralKey entries: {', '.join(missing)}")

    def plural_variants(table, key):
        return {q: ios_text(t) for q, t in table["plurals"].get(key, english["plurals"][key]).items()}

    files = {}
    english_plurals = [(k, plural_variants(english, k)) for k in plurals] + list(IOS_ONLY_PLURALS.items())
    files["en.lproj"] = (render_strings(keys, english["strings"], "en"), render_stringsdict(english_plurals))
    for qualifier, table in sorted(resources.items()):
        locale = apple_locale(qualifier) if qualifier else None
        if locale is None or locale == "en":
            continue
        translated = [k for k in keys if k in table["strings"]] + [k for k in plurals if k in table["plurals"]]
        if not translated:
            continue
        texts = {k: table["strings"].get(k, english["strings"][k]) for k in keys}
        files[f"{locale}.lproj"] = (
            render_strings(keys, texts, locale),
            render_stringsdict([(k, plural_variants(table, k)) for k in plurals]),
        )
    return {
        f"{folder}/{TABLE}.{ext}": content
        for folder, (strings, plist) in files.items()
        for ext, content in (("strings", strings), ("stringsdict", plist))
    }


def generated_locales():
    return sorted(p.parent.name[:-len(".lproj")] for p in IOS_DIR.glob(f"*.lproj/{TABLE}.stringsdict"))


def declared_locales():
    """The locale ids in project.yml's `CFBundleLocalizations: [...]`."""
    match = re.search(r"CFBundleLocalizations:\s*\[([^\]]*)\]", PROJECT_YML.read_text())
    return sorted(part.strip() for part in match.group(1).split(",") if part.strip()) if match else []


def main():
    files = render_all()
    stale_orphans = [p for p in IOS_DIR.glob(f"*.lproj/{TABLE}.stringsdict") if f"{p.parent.name}/{p.name}" not in files]
    if "--check" in sys.argv[1:]:
        stale = [name for name, content in files.items()
                 if not (IOS_DIR / name).exists() or (IOS_DIR / name).read_text() != content]
        stale += [str(p.relative_to(IOS_DIR)) for p in stale_orphans]
        wanted = sorted(name.split(".lproj")[0] for name in files if name.endswith(".strings"))
        if declared_locales() != wanted:
            sys.exit(f"ios/project.yml CFBundleLocalizations is [{', '.join(declared_locales())}], should be [{', '.join(wanted)}]")
        if stale:
            sys.exit(f"Stale under ios/S2: {', '.join(stale)}: run ios/scripts/generate-strings.py")
        return
    for name, content in files.items():
        path = IOS_DIR / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content)
    for path in stale_orphans:
        path.unlink()
        (path.parent / f"{TABLE}.strings").unlink(missing_ok=True)
        print(f"Removed {path.parent.relative_to(ROOT)}")
    print(f"Wrote {len(files)} files for {len(files) // 2} locales: {', '.join(generated_locales())}")


if __name__ == "__main__":
    main()
