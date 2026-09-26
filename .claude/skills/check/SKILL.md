---
name: check
description: Run lint + unit tests, investigate and fix failures. Use after making changes.
user_invocable: true
---

# Check

Run lint and unit tests to verify changes. Investigate and fix any failures.

## Steps

1. **Lint** (auto-fix first, then verify):

   ```bash
   support/scripts/lint -F 2>&1 | tail -20
   ```

   Then run lint without auto-fix to check for remaining issues:

   ```bash
   support/scripts/lint 2>&1 | tail -40
   ```

   If lint fails, read the error output, fix the issues, and re-run.

2. **Unit tests:**

   ```bash
   support/scripts/unit-test 2>&1 | tail -50
   ```

   Prefer `support/scripts/unit-test --changed-tests` for the fastest loop: it maps the current diff
   (against `origin/main` by default) down to the individual test classes that exercise the changed
   files, one Gradle invocation per module. It falls back to `--changed`'s module-level mapping (or
   the full suite) when a file doesn't map cleanly to a class — see `.claude/rules/testing.md` for the
   mapping rules. Run the full suite once at the end via `support/scripts/unit-test --changed` or a
   plain `support/scripts/unit-test`. Otherwise use the module-scoped form, e.g.
   `support/scripts/unit-test playback` or `support/scripts/unit-test playback app --tests
   '*QueueOperations*'`. It accepts a short module name or a full Gradle path.

   If tests fail, read the failure output. The output shows the test class and method that
   failed, plus the assertion message. Open the failing test to understand what it expects,
   then fix the implementation (not the test) unless the test is wrong.

3. **Report results:** Summarize what passed and what was fixed.

## When to use

- After implementing a feature or fix
- After refactoring code
- Before committing
