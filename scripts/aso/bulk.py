#!/usr/bin/env python3
"""List and download Play Console bulk reports (stats/installs, stats/store_performance) from GCS."""
import argparse
import os
import sys
from pathlib import Path

from google.api_core.exceptions import Forbidden, NotFound
from google.cloud import storage
from google.oauth2 import service_account

PKG = "com.simplecityapps.shuttle"
CREDS = os.path.expanduser("~/.config/simplecity/play-credentials.json")
# Not the developer id: Play Console → Download reports → Statistics → "Copy Cloud Storage URI".
BUCKET = "pubsite_prod_rev_04915875223811481712"
PREFIXES = ["stats/installs/", "stats/store_performance/"]
OUT = Path(os.path.expanduser("~/.local/share/shuttle-music-aso/bulk"))


def open_bucket(client):
    try:
        next(iter(client.list_blobs(BUCKET, prefix="stats/", max_results=1)), None)
        return client.bucket(BUCKET)
    except (Forbidden, NotFound) as e:
        print(f"no access to gs://{BUCKET}: check the URI in Play Console → Download reports and that the "
              f"service account has 'View app information and download bulk reports' ({e.code})")
        sys.exit(3)


def blobs(bucket, prefix):
    # The trailing underscore keeps com.simplecityapps.shuttle.podcasts_ files out.
    return [b for b in bucket.client.list_blobs(bucket, prefix=prefix) if f"_{PKG}_" in b.name]


def main():
    p = argparse.ArgumentParser(description=__doc__)
    sub = p.add_subparsers(dest="cmd", required=True)
    ls = sub.add_parser("ls")
    ls.add_argument("prefix", nargs="?", help="restrict to a prefix, default both stats folders")
    get = sub.add_parser("get", help="download blobs by name, or --latest for the newest month of each")
    get.add_argument("names", nargs="*")
    get.add_argument("--latest", action="store_true")
    args = p.parse_args()

    creds = service_account.Credentials.from_service_account_file(
        CREDS, scopes=["https://www.googleapis.com/auth/devstorage.read_only"])
    bucket = open_bucket(storage.Client(credentials=creds, project=creds.project_id))

    if args.cmd == "ls":
        for prefix in ([args.prefix] if args.prefix else PREFIXES):
            for b in blobs(bucket, prefix):
                print(f"{b.size:>9}  {b.name}")
        return

    names = list(args.names)
    if args.latest:
        for prefix in PREFIXES:
            found = sorted(b.name for b in blobs(bucket, prefix))
            if found:
                month = found[-1].split("_")[-2] if found[-1].count("_") >= 2 else None
                names += [n for n in found if month and f"_{month}_" in n]
    OUT.mkdir(parents=True, exist_ok=True)
    for name in names:
        dest = OUT / Path(name).name
        bucket.blob(name).download_to_filename(str(dest))
        print(dest)


if __name__ == "__main__":
    main()
