---
name: delegate-verbose
description: Run high-volume work in a subagent so its raw output never enters this conversation. Use BEFORE running a Gradle test sweep, instrumented/emulator test run, or lint sweep — anything where the output is large but only the conclusion matters. Triggers on "run the tests", "gradle test", "run instrumented tests", "run lint", "run all tests".
---

# Delegate verbose work

Raw tool output stays in context for the rest of the session and is resent on every
subsequent turn. A 40k-token test log read once costs 40k tokens on every turn that follows.
Delegating it costs 40k once, inside the subagent, and returns a summary.

## When to delegate

Delegate when **both** are true:

1. The output will exceed roughly 2k tokens, and
2. You only need the conclusion — not the raw text — to continue.

Concretely in this project: `./gradlew testDebugUnitTest` sweeps across modules,
`./gradlew :android:app:smokeGroupDebugAndroidTest` (instrumented/emulator run),
`support/scripts/lint` across the whole tree, and reading anything under `build/reports/`.

## When NOT to delegate

- You need the exact bytes (a stack trace you'll edit against, a diff you'll apply).
- The work is a single short command whose output you'd read in full anyway.
- The task requires the conversation's accumulated context to interpret — a subagent
  starts cold and cannot see this thread.

## How

Launch a subagent with the Agent tool. Give it the full command to run and, critically,
tell it **what to return** — a subagent's final message is its return value, so a vague
prompt yields a vague summary and you'll have to run it again.

Bad:  "Run the tests and tell me how it went."
Good: "Run `support/scripts/unit-test`. Return a list of every failing test class/method
       with its assertion message, which module it's in, and the overall pass/fail count.
       Do not paste raw log lines."

Ask for a bounded shape (a table, a ranked list, N bullet points), not "a summary".

If the subagent's answer turns out to be insufficient, send it a follow-up message rather
than re-running the sweep in this context — it still holds the raw output.

## Pick the model for the subagent

A subagent inherits the parent's model unless told otherwise, so a delegated grep runs on
Opus by default. Pass `model` explicitly:

- **`haiku`** — mechanical and extractive. Run a command and report pass/fail, grep for a
  pattern, count occurrences, pull the failing lines out of a log.
- **`sonnet`** — needs to read code but not to make a design judgement. Trace where a value
  comes from, summarise what a module does, draft a mechanical refactor.
- **omit it (inherit)** — anything where being wrong is expensive: reviewing a diff for
  correctness, diagnosing a race, deciding whether an approach is sound.

The failure mode is asymmetric: too cheap means a re-run, which costs more than the model
saving ever did. When genuinely unsure, omit it.

## Verifying it worked

After the subagent returns, the raw output should be absent from this conversation. If you
find yourself pasting its logs back in to reason about them, the delegation failed — the
right fix is a sharper return contract next time, not abandoning the pattern.
