#!/usr/bin/env python3
"""Prints one line per failed test in an .xcresult: `FAILED <test>: <message> (<file>:<line>)`.

Reads `xcresulttool get test-results tests` (XCTest and Swift Testing; a parameterised test
reports each failing argument under an Arguments node, named `test [argument]`).
"""
import json
import os
import subprocess
import sys


def walk(node, test, out):
    kind = node.get("nodeType")
    if kind == "Test Case":
        test = node.get("name", test)
    elif kind == "Arguments":
        test = f"{test} [{node.get('name', '')}]"
    if kind == "Failure Message":
        text = " ".join(node.get("name", "").split())
        loc = node.get("sourceLocation")
        where = f" ({os.path.basename(loc['filePath'])}:{loc['lineNumber']})" if loc else ""
        out.append(f"FAILED {test}: {text}{where}")
    for child in node.get("children", []):
        walk(child, test, out)


def main(bundle):
    raw = subprocess.run(
        ["xcrun", "xcresulttool", "get", "test-results", "tests", "--path", bundle, "--format", "json"],
        capture_output=True, text=True,
    )
    if raw.returncode != 0:
        print(f"could not read {bundle}: {raw.stderr.strip()}", file=sys.stderr)
        return 1
    out = []
    for node in json.loads(raw.stdout).get("testNodes", []):
        walk(node, "", out)
    print("\n".join(dict.fromkeys(out)))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1]))
