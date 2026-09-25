# Shared check-name enumeration for run-all.sh and emu-verify.sh --suite (#454), so "every check"
# and "the device smoke set" are each listed in exactly one place instead of two copies that can
# drift apart. Defines two functions and sets no shell options itself. Sourced, not run.

# suite_all_names <checks_dir>: every check.sh in the directory, alphabetical, minus run-all.sh,
# no-crashes.sh and *_test.sh wrappers -- callers run no-crashes separately, last.
suite_all_names() {
    local dir="$1" f name
    for f in "$dir"/[a-z]*.sh; do
        name="$(basename "$f" .sh)"
        case "$name" in
            run-all | *_test | no-crashes) continue ;;
        esac
        echo "$name"
    done
}

# suite_smoke_names <checks_dir>: the device smoke set named in <checks_dir>/smoke.txt, one name
# per line (blank and '#' lines skipped). A name is echoed as-is, whether or not <name>.sh exists --
# the caller decides how to report a listed name with no script. no-crashes is excluded even if
# listed, since callers run it separately, last.
suite_smoke_names() {
    local dir="$1" name
    while read -r name _; do
        case "$name" in "" | "#"*) continue ;; esac
        [ "$name" = "no-crashes" ] && continue
        echo "$name"
    done <"$dir/smoke.txt"
}
