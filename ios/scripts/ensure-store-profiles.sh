#!/usr/bin/env bash
set -euo pipefail

# Ensure the App Store provisioning profiles ExportOptions.plist names exist in App Store
# Connect, are tied to the Apple Distribution certificate in this Mac's keychain, and are
# installed where xcodebuild -exportArchive looks for them.
#
# Why: the team's API keys are not offered "Access to Cloud Managed Distribution Certificate", so
# automatic signing cannot mint Store profiles headlessly. Export therefore signs manually
# (ExportOptions.plist: signingStyle manual, profiles by name) and this script keeps those
# profiles in step with the local certificate. Idempotent: run it before every export; it is a
# no-op when everything is already in place, and it recreates a profile that is INVALID
# (entitlements changed), missing, or bound to a different (rotated) certificate.
#
# Needs bash, curl, python3, openssl and security (all on a stock Mac). No third-party deps.
#
# usage: ensure-store-profiles.sh [--api-key-path P] [--api-key-id K] [--api-issuer-id I]
#   Flags fall back to ASC_API_KEY_PATH / ASC_KEY_ID / ASC_ISSUER_ID in the environment.

API_KEY_PATH="${ASC_API_KEY_PATH:-}"
API_KEY_ID="${ASC_KEY_ID:-}"
API_ISSUER_ID="${ASC_ISSUER_ID:-}"

while [ $# -gt 0 ]; do
    case "$1" in
        --api-key-path)  API_KEY_PATH="$2"; shift 2 ;;
        --api-key-id)    API_KEY_ID="$2"; shift 2 ;;
        --api-issuer-id) API_ISSUER_ID="$2"; shift 2 ;;
        -h|--help)       sed -n '/^# usage:/,/^$/p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) echo "unknown argument: $1" >&2; exit 2 ;;
    esac
done

if [ -z "$API_KEY_PATH" ] || [ -z "$API_KEY_ID" ] || [ -z "$API_ISSUER_ID" ]; then
    echo "ensure-store-profiles: --api-key-path, --api-key-id and --api-issuer-id (or the ASC_* env vars) are all required" >&2
    exit 2
fi
[ -f "$API_KEY_PATH" ] || { echo "ensure-store-profiles: API key not found: $API_KEY_PATH" >&2; exit 2; }

CERT_NAME="Apple Distribution: Simplecity Apps Pty Ltd"
PROFILE_DIR="$HOME/Library/Developer/Xcode/UserData/Provisioning Profiles"

# The certificate the profiles must carry: whatever `Apple Distribution` identity the login
# keychain holds. Its SHA-1 is what we match against App Store Connect's certificate list.
if ! security find-identity -v -p codesigning | /usr/bin/grep -q "$CERT_NAME"; then
    echo "ensure-store-profiles: no signing identity '$CERT_NAME' in the keychain (see DEPLOY.md, runner prerequisites)" >&2
    exit 1
fi
# Several certificates can share the name (expired ones linger), so take the SHA-1 of the first
# *valid* identity (`find-identity -v` omits expired and revoked ones) rather than the first match.
LOCAL_SHA1="$(security find-identity -v -p codesigning | /usr/bin/grep "$CERT_NAME" | head -1 \
    | awk '{print $2}' | tr 'a-f' 'A-F')"
[[ "$LOCAL_SHA1" =~ ^[0-9A-F]{40}$ ]] || { echo "ensure-store-profiles: cannot read the identity's SHA-1" >&2; exit 1; }
echo "==> Local distribution certificate SHA-1 $LOCAL_SHA1"

mkdir -p "$PROFILE_DIR"

# The App Store Connect side lives in python (stdlib only). openssl does the ES256 signature for
# the JWT; python turns the DER signature into the raw r||s form JWS wants.
API_KEY_PATH="$API_KEY_PATH" API_KEY_ID="$API_KEY_ID" API_ISSUER_ID="$API_ISSUER_ID" \
LOCAL_SHA1="$LOCAL_SHA1" PROFILE_DIR="$PROFILE_DIR" \
python3 - <<'PY'
import base64, hashlib, json, os, plistlib, subprocess, sys, time, urllib.error, urllib.parse, urllib.request

API = "https://api.appstoreconnect.apple.com"
KEY_PATH, KEY_ID, ISSUER = os.environ["API_KEY_PATH"], os.environ["API_KEY_ID"], os.environ["API_ISSUER_ID"]
LOCAL_SHA1 = os.environ["LOCAL_SHA1"]
PROFILE_DIR = os.environ["PROFILE_DIR"]

# Keep in step with ios/ExportOptions.plist (provisioningProfiles).
PROFILES = [
    ("Shuttle Music App Store", "com.simplecityapps.shuttle"),
]
PROFILE_TYPE = "IOS_APP_STORE"


def b64url(b: bytes) -> str:
    return base64.urlsafe_b64encode(b).rstrip(b"=").decode()


def der_to_raw(der: bytes) -> bytes:
    # SEQUENCE { INTEGER r, INTEGER s } -> r||s, each left-padded to 32 bytes.
    assert der[0] == 0x30
    i = 2 if der[1] < 0x80 else 2 + (der[1] & 0x7F)
    out = b""
    for _ in range(2):
        assert der[i] == 0x02
        n = der[i + 1]
        val = der[i + 2 : i + 2 + n].lstrip(b"\x00")
        out += val.rjust(32, b"\x00")
        i += 2 + n
    return out


def make_jwt() -> str:
    now = int(time.time())
    header = b64url(json.dumps({"alg": "ES256", "kid": KEY_ID, "typ": "JWT"}).encode())
    payload = b64url(json.dumps({"iss": ISSUER, "iat": now, "exp": now + 600, "aud": "appstoreconnect-v1"}).encode())
    signing_input = f"{header}.{payload}".encode()
    der = subprocess.run(["openssl", "dgst", "-sha256", "-sign", KEY_PATH], input=signing_input,
                         capture_output=True, check=True).stdout
    return f"{header}.{payload}.{b64url(der_to_raw(der))}"


TOKEN = make_jwt()


def api(method: str, path: str, body=None):
    url = path if path.startswith("http") else API + path
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Authorization", f"Bearer {TOKEN}")
    if data is not None:
        req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            raw = resp.read()
            return json.loads(raw) if raw else {}
    except urllib.error.HTTPError as e:
        detail = e.read().decode(errors="replace")
        sys.exit(f"ensure-store-profiles: {method} {path} -> HTTP {e.code}: {detail}")


def api_all(path: str):
    items = []
    while path:
        page = api("GET", path)
        items.extend(page.get("data", []))
        path = page.get("links", {}).get("next")
    return items


# 1. Which App Store Connect certificate is the one in the keychain?
cert_id = None
for c in api_all("/v1/certificates?filter[certificateType]=DISTRIBUTION&limit=200"):
    der = base64.b64decode(c["attributes"]["certificateContent"])
    if hashlib.sha1(der).hexdigest().upper() == LOCAL_SHA1:
        cert_id = c["id"]
        print(f"==> App Store Connect certificate {cert_id} ({c['attributes'].get('displayName')}, "
              f"expires {c['attributes'].get('expirationDate')})")
        break
if cert_id is None:
    sys.exit("ensure-store-profiles: no DISTRIBUTION certificate in App Store Connect matches the "
             "keychain's Apple Distribution certificate (SHA-1 %s). Create one in Xcode > Settings > "
             "Accounts > Manage Certificates on this Mac, or revoke the stale one." % LOCAL_SHA1)

# 2. Bundle IDs by identifier. filter[identifier] is a substring match, so pin it exactly.
bundle_ids = {}
for b in api_all("/v1/bundleIds?filter[platform]=IOS&limit=200"):
    bundle_ids[b["attributes"]["identifier"]] = b["id"]


def installed_profiles():
    """Map UUID -> (Name, path) for everything in Xcode's profile directory."""
    out = {}
    for f in os.listdir(PROFILE_DIR):
        if not f.endswith(".mobileprovision"):
            continue
        path = os.path.join(PROFILE_DIR, f)
        try:
            raw = subprocess.run(["security", "cms", "-D", "-i", path], capture_output=True, check=True).stdout
            p = plistlib.loads(raw)
            out[p["UUID"]] = (p.get("Name", ""), path)
        except Exception:
            continue
    return out


def install(profile_content_b64: str, name: str):
    raw = base64.b64decode(profile_content_b64)
    decoded = subprocess.run(["security", "cms", "-D"], input=raw, capture_output=True, check=True).stdout
    uuid = plistlib.loads(decoded)["UUID"]
    # Drop any other installed copy carrying this name so manual signing by name is unambiguous.
    for other_uuid, (other_name, path) in installed_profiles().items():
        if other_name == name and other_uuid != uuid:
            os.remove(path)
            print(f"    removed stale local profile {other_uuid}")
    dest = os.path.join(PROFILE_DIR, f"{uuid}.mobileprovision")
    if not os.path.exists(dest):
        with open(dest, "wb") as fh:
            fh.write(raw)
        print(f"    installed {dest}")
    else:
        print(f"    already installed {dest}")


for name, bundle_id in PROFILES:
    print(f"==> {name} ({bundle_id})")
    if bundle_id not in bundle_ids:
        sys.exit(f"ensure-store-profiles: bundle ID {bundle_id} is not registered in App Store Connect")
    q = urllib.parse.quote(name)
    existing = api_all(f"/v1/profiles?filter[name]={q}&include=certificates&fields[certificates]=certificateType&limit=200")
    existing = [p for p in existing if p["attributes"]["name"] == name]
    keep = None
    for p in existing:
        state = p["attributes"]["profileState"]
        ptype = p["attributes"]["profileType"]
        cert_ids = {c["id"] for c in p.get("relationships", {}).get("certificates", {}).get("data", [])}
        ok = state == "ACTIVE" and ptype == PROFILE_TYPE and cert_id in cert_ids
        if ok and keep is None:
            keep = p
            print(f"    profile {p['id']} is ACTIVE with the local certificate")
        else:
            why = "INVALID" if state != "ACTIVE" else ("wrong type " + ptype if ptype != PROFILE_TYPE else "lacks the local certificate")
            print(f"    deleting profile {p['id']} ({why})")
            api("DELETE", f"/v1/profiles/{p['id']}")
    if keep is None:
        keep = api("POST", "/v1/profiles", {"data": {
            "type": "profiles",
            "attributes": {"name": name, "profileType": PROFILE_TYPE},
            "relationships": {
                "bundleId": {"data": {"type": "bundleIds", "id": bundle_ids[bundle_id]}},
                "certificates": {"data": [{"type": "certificates", "id": cert_id}]},
            },
        }})["data"]
        print(f"    created profile {keep['id']}")
    content = keep["attributes"].get("profileContent")
    if not content:
        content = api("GET", f"/v1/profiles/{keep['id']}")["data"]["attributes"]["profileContent"]
    install(content, name)

print("==> App Store profiles are in place")
PY
