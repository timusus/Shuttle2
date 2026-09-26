#!/usr/bin/env bash
# Print failed/errored JUnit testcases from TEST-*.xml files written after
# $1, via unit-test-report.py. Shared by support/scripts/unit-test and
# support/scripts/remote-build.sh (#468) so a build/test failure always names
# the failing test the same way, without each script duplicating the XML
# search.
#
#   support/scripts/report-test-failures.sh <marker-file> <repo-root>
#
# Silently does nothing without python3, or when no test-results XML changed
# since the marker (a non-test build failure, or stale reports left over from
# an earlier run).
set -euo pipefail

marker="$1"
repo_root="$2"

command -v python3 >/dev/null 2>&1 || exit 0

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

xml_files=()
while IFS= read -r -d '' f; do
  xml_files+=("${f}")
done < <(find "${repo_root}" -path '*/build/test-results/*/TEST-*.xml' -newer "${marker}" -print0 2>/dev/null)

[ "${#xml_files[@]}" -eq 0 ] && exit 0

echo
echo "Failed tests:"
python3 "${script_dir}/unit-test-report.py" "${xml_files[@]}"
