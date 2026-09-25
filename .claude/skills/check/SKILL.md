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

   Prefer `support/scripts/unit-test --changed` for a faster loop: it maps the current diff
   (against `origin/main` by default) to affected modules and test classes and runs only those,
   compiling first so a compile error surfaces before any test runs. See `.claude/rules/testing.md`
   for the mapping rules. Otherwise use the module-scoped form, e.g.
   `support/scripts/unit-test playback` or `support/scripts/unit-test playback app --tests
   '*QueueManager*'`. It accepts a short module name or a full Gradle path.

   If tests fail, read the failure output. The output shows the test class and method that
   failed, plus the assertion message. Open the failing test to understand what it expects,
   then fix the implementation (not the test) unless the test is wrong.

3. **Report results:** Summarize what passed and what was fixed.

## When to use

- After implementing a feature or fix
- After refactoring code
- Before committing
