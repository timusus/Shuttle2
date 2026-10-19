# shellcheck shell=bash
# uiautomator dump, text/desc lookup and tap for support/scripts/remote-emu.sh. Sourced, not run.

xml_unescape() {
    local s="$1"
    s="${s//&lt;/<}"; s="${s//&gt;/>}"; s="${s//&quot;/\"}"; s="${s//&apos;/\'}"
    # uiautomator's XmlSerializer writes BMP characters (accents, CJK, ...) as raw UTF-8 but numeric-
    # escapes anything outside the BMP -- emoji, some musical symbols -- as `&#NNNN;`/`&#xHHHH;`
    # decimal/hex character references (#416: a title with such a character never matched raw text).
    # Only worth a python3 call when one is actually present.
    if [[ "$s" == *'&#'* ]]; then
        s="$(python3 -c '
import re, sys
def repl(m):
    cp = int(m.group(1), 16) if m.group(1) else int(m.group(2))
    # A lone surrogate (D800-DFFF, from a lossy source) or an out-of-range code point cannot be
    # a real character -- chr() itself is fine with a surrogate, but encoding it back out for
    # stdout is not, so swap it for U+FFFD instead of raising and aborting the decode (#416).
    if cp > 0x10FFFF or 0xD800 <= cp <= 0xDFFF:
        return "�"
    return chr(cp)
sys.stdout.write(re.sub(r"&#x([0-9A-Fa-f]+);|&#([0-9]+);", repl, sys.stdin.read()))
' <<<"$s")"
    fi
    s="${s//&amp;/&}"
    echo "$s"
}

# dump_xml_to <local-file>: uiautomator dump (compressed, retried) saved to <local-file>. Retries
# because "could not get idle state" is common while music plays -- the playback screen's progress
# bar keeps ticking even with `ui-prep`'s animation scales at 0. Assumes $LANE is set.
dump_xml_to() {
    local out_file="$1" remote="/sdcard/s2-dump.xml" tries=8 n out
    for n in $(seq 1 "$tries"); do
        out="$(radb shell uiautomator dump --compressed "$remote" 2>&1 | tr -d '\r')"
        if ! echo "$out" | grep -qi error && radb pull "$remote" "$out_file" >/dev/null 2>&1 && [ -s "$out_file" ]; then
            return 0
        fi
        # Each idle-state failure costs ~10 s, and a screen that keeps updating (the mini player's
        # progress while music plays) never goes idle: give up after two rather than eight.
        if echo "$out" | grep -q "could not get idle state" && [ "$n" -ge 2 ]; then
            echo "remote-emu: the UI never went idle (music playing?): pause with support/scripts/s2-debug.sh PAUSE, or use a Maestro flow" >&2
            return 1
        fi
        sleep 1
    done
    echo "remote-emu: uiautomator dump failed after ${tries} attempts (last: ${out})" >&2
    return 1
}

# Field separator for list_nodes' output. Not a tab/space: bash `read` and `cut` treat IFS
# whitespace specially (leading/trailing separators are stripped, runs of them collapse), which
# silently shifts columns whenever the first field (text) is empty -- as it usually is for a
# content-desc-only node. \x1f (unit separator) never appears in UI text.
NODE_SEP=$'\x1f'

# list_nodes <xml-file>: one line per node with a text or content-desc: "text<NODE_SEP>desc<NODE_SEP>bounds".
list_nodes() {
    local node text desc bounds
    while IFS= read -r node; do
        text="$(grep -oE ' text="[^"]*"' <<<"$node" | head -1 | sed -E 's/ text="(.*)"/\1/')"
        desc="$(grep -oE ' content-desc="[^"]*"' <<<"$node" | head -1 | sed -E 's/ content-desc="(.*)"/\1/')"
        bounds="$(grep -oE ' bounds="[^"]*"' <<<"$node" | head -1 | sed -E 's/ bounds="(.*)"/\1/')"
        [ -z "$text" ] && [ -z "$desc" ] && continue
        printf '%s%s%s%s%s\n' "$(xml_unescape "$text")" "$NODE_SEP" "$(xml_unescape "$desc")" "$NODE_SEP" "$bounds"
    done < <(grep -oE '<node[^>]*>' "$1")
}

cmd_dump_texts() {
    LANE="$(resolve_lane)"
    tunnel_pid "$LANE" >/dev/null || { echo "remote-emu: no tunnel for lane $LANE; run start first" >&2; exit 1; }
    local tmp; tmp="$(mktemp)"
    if ! dump_xml_to "$tmp"; then rm -f "$tmp"; exit 1; fi
    while IFS="$NODE_SEP" read -r text desc bounds; do
        [ -n "$text" ] && echo "text=\"${text}\" bounds=${bounds}"
        [ -n "$desc" ] && echo "desc=\"${desc}\" bounds=${bounds}"
    done < <(list_nodes "$tmp")
    rm -f "$tmp"
}

cmd_tap_text() {
    LANE="$(resolve_lane)"
    tunnel_pid "$LANE" >/dev/null || { echo "remote-emu: no tunnel for lane $LANE; run start first" >&2; exit 1; }
    local field=text index=0 target=""
    while [ $# -gt 0 ]; do
        case "$1" in
            --desc) field=desc; shift ;;
            --index) index="${2:?remote-emu: --index needs a value}"; shift 2 ;;
            *) target="$1"; shift ;;
        esac
    done
    [ -n "$target" ] || { echo "remote-emu: usage: tap-text <text> [--desc] [--index N]" >&2; exit 2; }

    local tmp; tmp="$(mktemp)"
    if ! dump_xml_to "$tmp"; then rm -f "$tmp"; exit 1; fi

    local col=1; [ "$field" = "desc" ] && col=2
    local matches; matches="$(list_nodes "$tmp" | awk -F"$NODE_SEP" -v c="$col" -v t="$target" '$c == t')"
    if [ -z "$matches" ]; then
        echo "remote-emu: no node with ${field}=\"${target}\" -- visible text/desc:" >&2
        list_nodes "$tmp" | cut -d "$NODE_SEP" -f1,2 | tr "$NODE_SEP" '\n' | grep -v '^$' | sort -u >&2
        rm -f "$tmp"
        exit 1
    fi
    local chosen; chosen="$(echo "$matches" | sed -n "$((index + 1))p")"
    rm -f "$tmp"
    if [ -z "$chosen" ]; then
        echo "remote-emu: only $(echo "$matches" | wc -l | tr -d ' ') match(es) for ${field}=\"${target}\"; index ${index} out of range" >&2
        exit 1
    fi
    local bounds x1 y1 x2 y2 cx cy
    bounds="$(echo "$chosen" | cut -d "$NODE_SEP" -f3)"
    read -r x1 y1 x2 y2 <<<"$(echo "$bounds" | sed -E 's/\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]/\1 \2 \3 \4/')"
    cx=$(( (x1 + x2) / 2 )); cy=$(( (y1 + y2) / 2 ))
    echo "remote-emu: tapping ${field}=\"${target}\" at (${cx},${cy}) bounds=${bounds}"
    radb shell input tap "$cx" "$cy"
}
