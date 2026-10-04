# Puts /usr/sbin (lsof, for remote-emu.sh's tunnel listener check) and the SDK platform-tools (adb)
# on PATH. Headless worker shells start without them (#718, #723). Sourced by remote-emu.sh,
# seed-test-media.sh, emu-verify.sh and checks/_lib.sh (so every checks/*.sh and s2-debug.sh gets it
# too). Sets no shell options, so it is safe to source under any `set` combination.
# shellcheck shell=bash
for _d in /usr/sbin /sbin "$HOME/Library/Android/sdk/platform-tools"; do
    case ":$PATH:" in
        *":$_d:"*) ;; # already on PATH
        *) PATH="$PATH:$_d" ;;
    esac
done
unset _d
export PATH
