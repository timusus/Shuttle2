#!/usr/bin/env bash
set -euo pipefail

# Store an App Store Connect API key as the GitHub secrets ios-deploy.yml needs, then
# optionally fire the no-upload smoke run.
#
# Apple has no API for minting the key itself, so generate it once in the browser:
#   App Store Connect > Users and Access > Integrations > App Store Connect API > Team Keys
#   > Generate API Key: name it, role App Manager, download the .p8 (one-shot download).
#   The key does not need (and this team is not offered) cloud-managed distribution
#   certificates: export signs manually with the Apple Distribution certificate in the runner's
#   keychain and the profiles scripts/ensure-store-profiles.sh installs. See DEPLOY.md.
# The Key ID is in the key's row; the Issuer ID is at the top of the Team Keys page.
#
# usage: ios/scripts/setup-asc-secrets.sh [--smoke] [PATH_TO_AuthKey_XXXX.p8]
#   With no path the newest ~/Downloads/AuthKey_*.p8 is used. The Key ID is read from the
#   file name; the Issuer ID is prompted for unless ASC_ISSUER_ID is set.
#   --smoke   after the secrets are stored, run ios-deploy.yml via workflow_dispatch
#             (builds and exports without uploading) and print the run URL.

SMOKE=0
P8=""
for arg in "$@"; do
    case "$arg" in
        --smoke) SMOKE=1 ;;
        -h|--help) sed -n '3,17p' "$0"; exit 0 ;;
        *) P8="$arg" ;;
    esac
done

if [[ -z "$P8" ]]; then
    P8="$(ls -t "$HOME"/Downloads/AuthKey_*.p8 2>/dev/null | head -1 || true)"
    [[ -n "$P8" ]] || { echo "No AuthKey_*.p8 in ~/Downloads; pass the path explicitly." >&2; exit 1; }
fi
[[ -f "$P8" ]] || { echo "Not a file: $P8" >&2; exit 1; }

KEY_ID="$(basename "$P8" .p8)"; KEY_ID="${KEY_ID#AuthKey_}"
[[ "$KEY_ID" =~ ^[A-Z0-9]{10}$ ]] || { echo "Cannot read a 10-char Key ID from '$P8'; rename it AuthKey_<KEYID>.p8" >&2; exit 1; }

# The .p8 must be a valid EC private key or xcodebuild will fail late, inside the archive.
openssl pkey -in "$P8" -noout >/dev/null 2>&1 || { echo "$P8 is not a valid private key" >&2; exit 1; }

ISSUER_ID="${ASC_ISSUER_ID:-}"
if [[ -z "$ISSUER_ID" ]]; then
    read -r -p "Issuer ID (UUID from the Team Keys page): " ISSUER_ID
fi
[[ "$ISSUER_ID" =~ ^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$ ]] \
    || { echo "Issuer ID does not look like a UUID: $ISSUER_ID" >&2; exit 1; }

REPO_ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
cd "$REPO_ROOT"
gh auth status >/dev/null 2>&1 || { echo "gh is not authenticated; run: gh auth login" >&2; exit 1; }

echo "==> Storing secrets for $(gh repo view --json nameWithOwner -q .nameWithOwner)"
gh secret set ASC_KEY_ID --body "$KEY_ID"
gh secret set ASC_ISSUER_ID --body "$ISSUER_ID"
base64 -i "$P8" | tr -d '\n' | gh secret set ASC_API_KEY_P8
gh secret list | grep -E '^ASC_(KEY_ID|ISSUER_ID|API_KEY_P8)\b'

echo "==> Secrets stored. Keep the .p8 somewhere safe (or delete it: the runner only needs the secret)."

if [[ "$SMOKE" -eq 1 ]]; then
    echo "==> Triggering the no-upload smoke run"
    gh workflow run ios-deploy.yml
    sleep 5
    gh run list --workflow ios-deploy.yml --limit 1 --json url,status -q '.[0] | "\(.status) \(.url)"'
fi
