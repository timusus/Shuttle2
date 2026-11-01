#!/usr/bin/env python3
"""Play listing text as code (GPP listings dir): `pull` live text into the repo, `push` repo text back. Never touches graphics."""
import argparse
import difflib
import os
import sys
from pathlib import Path

from google.oauth2 import service_account
from googleapiclient.discovery import build
from googleapiclient.errors import HttpError

PKG = "com.simplecityapps.shuttle"
# The default key is read-only for listings; --apply needs the publishing key, as support/scripts/play passes it.
CREDS = os.environ.get("PLAY_CREDENTIALS_FILE") or os.path.expanduser("~/.config/simplecity/play-credentials.json")
ROOT = Path(__file__).resolve().parents[2] / "android/app/src/main/play/listings"
FIELDS = {"title": ("title.txt", 30), "shortDescription": ("short-description.txt", 80), "fullDescription": ("full-description.txt", 4000)}


def service():
    creds = service_account.Credentials.from_service_account_file(
        CREDS, scopes=["https://www.googleapis.com/auth/androidpublisher"])
    return build("androidpublisher", "v3", credentials=creds, cache_discovery=False)


def live_listings(svc):
    edit = svc.edits().insert(packageName=PKG, body={}).execute()["id"]
    try:
        items = svc.edits().listings().list(packageName=PKG, editId=edit).execute().get("listings", [])
    finally:
        svc.edits().delete(packageName=PKG, editId=edit).execute()
    return {l["language"]: l for l in items}


def norm(text):
    return text.replace("\r\n", "\n")


def read_local(locale):
    d = ROOT / locale
    return {f: norm((d / name).read_text(encoding="utf-8")).rstrip("\n") if (d / name).exists() else None
            for f, (name, _) in FIELDS.items()}


def pull(args):
    live = live_listings(service())
    for locale, l in sorted(live.items()):
        d = ROOT / locale
        d.mkdir(parents=True, exist_ok=True)
        for f, (name, _) in FIELDS.items():
            (d / name).write_text(norm(l.get(f, "")) + "\n", encoding="utf-8")
    print(f"pulled {len(live)} locales: {' '.join(sorted(live))}")


def push(args):
    svc = service()
    live = live_listings(svc)
    locales = args.locale or sorted(p.name for p in ROOT.iterdir() if p.is_dir())
    changes, bad = {}, False
    for locale in locales:
        if not (ROOT / locale / FIELDS["title"][0]).exists():
            print(f"ERROR {locale}: no {FIELDS['title'][0]}")
            bad = True
            continue
        local = read_local(locale)
        old = live.get(locale, {})
        for f, (name, limit) in FIELDS.items():
            if local[f] is None:
                continue
            if len(local[f]) > limit:
                print(f"ERROR {locale}/{name}: {len(local[f])} chars exceeds {limit}")
                bad = True
            before = norm(old.get(f, "")).rstrip("\n")
            if local[f] != before:
                changes.setdefault(locale, {})[f] = local[f]
                print(f"--- live {locale}/{name}\n+++ repo {locale}/{name}")
                sys.stdout.writelines(difflib.unified_diff(
                    (before + "\n").splitlines(True), (local[f] + "\n").splitlines(True), n=1))
    if bad:
        sys.exit("refusing to push: length overflow")
    if not changes:
        print("no diff")
        return
    if not args.apply:
        print(f"dry run: {len(changes)} locale(s) differ; rerun with --apply")
        return
    apply_changes(svc, live, changes)


def apply_changes(svc, live, changes):
    edit = svc.edits().insert(packageName=PKG, body={}).execute()["id"]
    try:
        for locale, fields in changes.items():
            body = {"language": locale, **{f: live.get(locale, {}).get(f, "") for f in FIELDS}, **fields}
            svc.edits().listings().update(packageName=PKG, editId=edit, language=locale, body=body).execute()
        try:
            svc.edits().commit(packageName=PKG, editId=edit).execute()
        except HttpError as e:
            if "changesNotSentForReview" not in str(e) and "sent for review" not in str(e):
                raise
            # Play names the exact flag to retry with when the app needs review submission.
            svc.edits().commit(packageName=PKG, editId=edit, changesNotSentForReview=True).execute()
            print("committed with changesNotSentForReview=true; send the changes for review in Play Console")
            return
        print("committed")
    except Exception:
        try:
            svc.edits().delete(packageName=PKG, editId=edit).execute()
        except HttpError:
            # codehealth-ignore empty-catch: best-effort cleanup; the original error is re-raised below and an orphaned edit expires on its own
            pass
        raise


def main():
    p = argparse.ArgumentParser(description=__doc__)
    sub = p.add_subparsers(dest="cmd", required=True)
    sub.add_parser("pull").set_defaults(fn=pull)
    q = sub.add_parser("push")
    q.add_argument("--locale", action="append")
    q.add_argument("--apply", action="store_true")
    q.set_defaults(fn=push)
    args = p.parse_args()
    args.fn(args)


if __name__ == "__main__":
    main()
