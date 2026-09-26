#!/usr/bin/env python3
"""Print failed/errored JUnit testcases from the given TEST-*.xml files.

Invoked via support/scripts/report-test-failures.sh, shared by
support/scripts/unit-test and support/scripts/remote-build.sh (#334, #468):
the condensed build-brief/Gradle console output names that a test task
failed but not which test, which only exists in the XML report.
"""
import re
import sys
import xml.etree.ElementTree as ET

MAX_FAILURES = 20

STACK_FRAME_RE = re.compile(r"^\s*at (com\.simplecityapps\S*?)\.[^.(]+\(([^():]+:\d+)\)")


def first_line(fail):
    message = (fail.get("message") or "").strip()
    if message:
        return message.splitlines()[0].strip()
    for line in (fail.text or "").splitlines():
        line = line.strip()
        if line:
            return line
    return ""


def failure_location(fail, classname):
    """The test class's own frame, else the first project frame (e.g. a shared helper)."""
    fallback = ""
    for line in (fail.text or "").splitlines():
        match = STACK_FRAME_RE.match(line)
        if not match:
            continue
        if match.group(1).split("$")[0] == classname:
            return match.group(2)
        fallback = fallback or match.group(2)
    return fallback


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
            failures.append(
                (classname, name, first_line(fail), failure_location(fail, classname))
            )
    return failures


def main():
    failures = collect_failures(sys.argv[1:])
    if not failures:
        return

    shown = failures[:MAX_FAILURES]
    for classname, name, message, location in shown:
        suffix = f" ({location})" if location else ""
        print(f"{classname}.{name}: {message}{suffix}")

    remaining = len(failures) - len(shown)
    if remaining > 0:
        print(f"+{remaining} more")


if __name__ == "__main__":
    main()
