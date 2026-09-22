---
name: commit
description: Analyse working tree changes, group them into logical atomic commits, and create well-structured conventional commits with module scopes.
user_invocable: true
---

# Commit Changes

Analyse the current working tree, group changes into logical atomic commits, and create them using conventional commit format.

## Steps

### 1. Gather context

Run these commands to understand the full picture:

```bash
# Staged changes
git diff --cached --stat
git diff --cached

# Unstaged changes
git diff --stat
git diff

# Untracked files
git status -u

# Recent commits for style reference
git log --oneline -10
```

If there are no changes at all (nothing staged, unstaged, or untracked), tell the user and stop.

### 2. Review changes and decide inclusion

Review **all** changes (staged, unstaged, and untracked). **Be autonomous — commit everything that looks intentional without asking.** The bias is toward getting things committed, not toward caution-prompting.

**Include without asking:**
- All staged changes — the user explicitly staged these
- All unstaged changes to tracked files — these are work in progress that should be captured
- Untracked files that clearly relate to the other changes (new source files, tests, resources, configs for the feature being worked on)
- Deleted files — if a file is deleted in the working tree, the deletion is intentional
- Files outside the apparent scope — include them in their own commit rather than leaving them uncommitted
- CLAUDE.md changes — always include these

**Only stop and ask if you encounter a genuine red flag:**
- **Sensitive files** (credentials, keys, tokens, `.env` with secrets) — warn and **never** commit these
- **Files that are almost certainly not meant for version control** (e.g., `.DS_Store`, `*.log`, editor swap files, `node_modules/`)

If everything looks clean (which is the common case), proceed directly to grouping and committing — no confirmation prompt needed.

### 3. Classify changes by component

Determine which component each changed file belongs to:

- **app** — files under `android/app/`
- **playback** — files under `android/playback/`
- **mediaprovider** — files under `android/mediaprovider/` (any provider)
- **data** — files under `android/data/`
- **core**, **networking**, **imageloader**, **trial** — the corresponding `android/<module>/`
- **root** — top-level files (CI, docs, scripts, Gradle config)

For root files, choose the most relevant scope:
- CI/CD workflows for a specific module → use that module's scope
- Cross-cutting or build-wide changes → omit the scope

### 4. Group into logical commits

Split changes into atomic commits. Each commit should represent **one logical change** that leaves the codebase in a working state.

**Group by:**
- Same feature or purpose across related files
- Same type of change (e.g., all test updates for a feature go together)
- Dependencies — if change B only makes sense with change A, they belong together

**Split when:**
- Changes serve different purposes (feature vs refactor vs test vs docs)
- Unrelated files happen to be modified together
- A rename/refactor is mixed with behaviour changes
- Test additions are standalone (not tied to a specific feature change)

**Ordering:** Commit foundational changes first (e.g., new module before feature using it, refactor before feature built on it).

When in doubt, **fewer well-grouped commits are better than many tiny ones.** Don't split for the sake of splitting — only split when commits genuinely represent distinct logical changes.

### 5. Determine commit message for each group

Follow the conventional commit format from this project:

```
type(scope): description

[optional body]

[optional footer]
```

#### Type

Choose the most accurate type:
- `feat` — new feature or capability
- `fix` — bug fix
- `refactor` — code restructuring without behaviour change
- `test` — adding or updating tests
- `docs` — documentation changes
- `style` — formatting, linting (no logic change)
- `chore` — maintenance, dependencies, config
- `build` — build system or dependency changes
- `ci` — CI/CD pipeline changes
- `perf` — performance improvement

#### Scope

The module the change lives in (see the list above). Optional for cross-cutting or build-wide changes.

If a commit touches several modules, use the primary one (where the meaningful change is) or split into separate commits.

#### Subject line

- **Imperative mood**: "add", "fix", "update" — not "added", "adds", "adding"
- **Lowercase** after the colon
- **No period** at the end
- **Max 50 characters** for the description (after `type(scope): `). Stretch to 72 max if absolutely necessary
- **Explain the "why" or "what changed"**, not the mechanical "how"

Good: `feat(playback): add queue reordering via drag and drop`
Bad: `feat(playback): updated QueueScreen.kt and QueueViewModel.kt`

#### Body

Include a body when:
- The "why" isn't obvious from the subject line alone
- There are side effects, trade-offs, or context worth capturing
- Multiple files changed and the connection isn't obvious

Body rules:
- Blank line between subject and body
- Wrap at 72 characters
- Explain **why**, not what (the diff shows what)
- Use bullet points for multiple points

Skip the body for self-explanatory changes (typo fixes, simple renames, obvious additions).

#### Changelog upkeep (mobile commits)

For `feat`/`fix`/`perf` commits touching files under `mobile/`, the `.githooks/commit-msg` hook requires one of:

- **Stage `mobile/changelog-unreleased.json` together with the code** — when the commit changes what a user sees or experiences
- **Add a `Changelog: none` trailer** to the message — when the change is internal (tests, refactors, telemetry, CI, monetisation mechanics, flag plumbing)

Which rule decides is in `.claude/rules/changelog.md`: a change iterating on an unreleased fragment item edits that item rather than adding a new line, and internal changes take the trailer.

### 6. Stage and commit each group

For each logical commit group, stage the relevant files and commit:

```bash
# Stage specific files for this commit
git add <file1> <file2> ...

# Use git add -p to stage partial file changes when needed
# (when a single file contains changes belonging to different commits)

# Commit with heredoc for proper formatting
git commit -m "$(cat <<'EOF'
type(scope): subject line here

Optional body here explaining why this change was made.
Wrap at 72 characters.
EOF
)"
```

**Important:**
- Stage specific files by name — never use `git add -A` or `git add .`
- Use `git add -p <file>` when a file has changes belonging to different logical commits
- Verify each commit with `git status` before moving to the next group
- If a pre-commit hook fails, fix the issue and create a **new** commit (never `--amend` unless the user explicitly asks)

### 7. Summary

After all commits are created, show a brief summary:

```
Created N commit(s):

  abc1234 type(scope): first commit subject
  def5678 type(scope): second commit subject
```

## Examples

### Single-component change
```
feat(android): add offline download support for episodes

Introduce DownloadManager and background worker to cache episodes
locally. Downloads persist across app restarts and respect user's
network preferences.
```

### Multi-commit from mixed changes
Given changes to Android UI, Android tests, and a backend endpoint:
1. `feat(android): add episode search filter by duration`
2. `test(android): add search filter unit tests`
3. `feat(backend): add duration parameter to episode search endpoint`
