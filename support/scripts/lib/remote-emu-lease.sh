# shellcheck shell=bash
# Lane leases on the box for support/scripts/remote-emu.sh. Sourced, not run.

# Prints one line per lane: "N owner since alive pid host" (owner/since/pid/host are "-" without
# a lease; alive is 1 when something listens on the console port). pid/host identify the Mac
# process that claimed the lease, for the dead-owner reclaim check below. The PID lives in
# `owner-pid`: leases written before owner_pid found the real session process kept a transient
# shell's PID in `pid`, which is ignored, so such a lease shows "-" and is never reclaimed by PID.
lease_table() {
    box "for n in \$(seq 1 $LANES); do
            port=\$((5554 + 2 * (n - 1))); d=${LEASE_ROOT}/lane-\$n
            alive=0; ss -ltn 2>/dev/null | grep -q \":\$port \" && alive=1
            if [ -d \$d ]; then echo \"\$n \$(cat \$d/owner 2>/dev/null || echo '?') \$(cat \$d/since 2>/dev/null || echo 0) \$alive \$(cat \$d/owner-pid 2>/dev/null || echo -) \$(cat \$d/host 2>/dev/null || echo -)\"
            else echo \"\$n - - \$alive - -\"; fi
        done"
}

# True when the lease names an owner PID on this Mac that no longer exists. A lease without a
# recorded owner PID (legacy, or claimed elsewhere) is never dead by this test.
owner_dead() {
    local pid="$1" host="$2"
    [ "$host" = "$MY_HOST" ] && [[ "$pid" =~ ^[0-9]+$ ]] && ! ps -p "$pid" >/dev/null 2>&1
}

# "N:PID" for each lease whose owner is dead (owner_dead): a headless worker that died mid-run
# leaves such a lease behind with its qemu still running. Can only be checked locally, hence the
# separate round trip; claim_lane re-checks the PID on the box so a lease re-taken in between is kept.
dead_owner_lanes() {
    local n owner pid host out=""
    while read -r n owner _ _ pid host; do
        [ "$owner" = "-" ] && continue
        owner_dead "$pid" "$host" || continue
        out="$out $n:$pid"
    done < <(lease_table)
    echo "$out"
}

# claim_lane [N]: mkdir-claims lane N, or the lowest free lane. Prints "TAKEN N" on success;
# "STALE N owner" / "DEAD N owner" lines precede it when a lease was reclaimed (age-based, or a
# dead owner PID on this Mac -- the latter also kills the orphaned qemu first). Exits 1 with the
# holder otherwise.
claim_lane() {
    local want="${1:-}" forced my_pid; forced="$(dead_owner_lanes)"; my_pid="$(owner_pid)"
    box "set -e; mkdir -p ${LEASE_ROOT}; now=\$(date +%s)
        lanes=\"${want:-\$(seq 1 $LANES | tr '\n' ' ')}\"
        for n in \$lanes; do
            port=\$((5554 + 2 * (n - 1))); d=${LEASE_ROOT}/lane-\$n
            alive=0; ss -ltn 2>/dev/null | grep -q \":\$port \" && alive=1
            is_dead=0
            for f in $forced; do
                [ \"\${f%%:*}\" = \"\$n\" ] && [ \"\$(cat \$d/owner-pid 2>/dev/null)\" = \"\${f#*:}\" ] && is_dead=1
            done
            if [ -d \$d ]; then
                owner=\$(cat \$d/owner 2>/dev/null || echo '?'); since=\$(cat \$d/since 2>/dev/null || echo 0)
                if [ \$is_dead = 1 ]; then
                    echo \"DEAD \$n \$owner\"
                    if [ \$alive = 1 ]; then
                        pkill -f '[-]port '\$port' ' 2>/dev/null || true; sleep 2
                        pkill -9 -f '[-]port '\$port' ' 2>/dev/null || true
                    fi
                    rm -rf \$d
                elif [ \$alive = 0 ] && [ \$((now - since)) -gt $BOOT_TIMEOUT ]; then
                    echo \"STALE \$n \$owner\"; rm -rf \$d
                else
                    [ -n '$want' ] && { echo \"HELD \$n \$owner \$((now - since))\"; exit 1; }
                    continue
                fi
            elif [ \$alive = 1 ]; then
                [ -n '$want' ] && { echo \"BUSY \$n\"; exit 1; }
                continue
            fi
            if mkdir \$d 2>/dev/null; then
                echo '$OWNER' > \$d/owner; echo \$now > \$d/since
                echo '$my_pid' > \$d/owner-pid; echo '$MY_HOST' > \$d/host
                echo \"TAKEN \$n\"; exit 0
            fi
        done
        echo NONE; exit 1"
}

release_lane() { box "rm -rf $(lease_of "$1")"; }

# The AVD name the lane's qemu was launched with (empty if it is not running).
lane_avd() {
    box "pgrep -af '$(emu_pattern "$1")'" | grep -o -m1 -- '-avd [^ ]*' | cut -d' ' -f2 || true
}
