#!/usr/bin/env python3
"""Print failed/errored JUnit testcases from the given TEST-*.xml files.

Invoked by support/scripts/unit-test after a failing run (#334): the
condensed build-brief/Gradle console output names the failing test but not
the assertion's expected/actual or the failing line, which only exist in
the XML report.
"""
import sys
import xml.etree.ElementTree as ET

MAX_FAILURES = 15
PACKAGE_PREFIX = "com.simplecityapps"


def first_project_frame(stack_text):
    for line in stack_text.splitlines():
        line = line.strip()
        if not line.startswith("at ") or PACKAGE_PREFIX not in line:
            continue
        rest = line[3:]
        if rest.endswith(")") and "(" in rest:
            location = rest[rest.rindex("(") + 1:-1]
            if ":" in location:
                return location
    return None


def collect_failures(paths):
    failures = []
    for path in paths:
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError:
            continue
        for testcase in root.iter("testcase"):
            fail = testcase.find("failure")
            if fail is None:
                fail = testcase.find("error")
            if fail is None:
                continue
            classname = testcase.get("classname", "?")
            name = testcase.get("name", "?")
            message = (fail.get("message") or "").strip()
            body = fail.text or ""
            detail_source = message if message else body
            detail_lines = [l.strip() for l in detail_source.splitlines() if l.strip()][:5]
            frame = first_project_frame(body)
            failures.append((classname, name, detail_lines, frame))
    return failures


def main():
    failures = collect_failures(sys.argv[1:])
    if not failures:
        return

    shown = failures[:MAX_FAILURES]
    for classname, name, detail_lines, frame in shown:
        print(f"{classname}.{name}")
        for line in detail_lines:
            print(f"    {line}")
        if frame:
            print(f"    at {frame}")
        print()

    remaining = len(failures) - len(shown)
    if remaining > 0:
        print(f"... {remaining} more")


if __name__ == "__main__":
    main()
