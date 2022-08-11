# Welcome

Welcome to the S2 Music Player Android Application project! Thank you for wanting to contribute.

## How to get started

Start by reading the [README.md](README.md).

## How to submit changes

This project uses the standard [github flow](https://guides.github.com/introduction/flow/). But for authorized project contributors it is not necessary to fork the repository before pushing a branch.

### Clone the repository

    git clone git@github.com:timusus/shuttle2.git

### Create a branch

    git checkout -b feature/short-feature-description

Branch names should include a prefix such as `tech`, `feature`, `doc`, or `fix` for technical tasks, feature development, documentation, or bugfixes respectively!

### Ensure your Git client is correctly configured

Please ensure you have set your email address and name correctly for the cloned repo. You can do this like so:

    git config --local user.name "Joe Contributor"
    git config --local user.email "joe.contributor@thepeoplespot.com"

### Commit code, Unit Tests, and UI Tests

    git add MyFeature.kt
    git commit

* Do your work in the branch you created.
* Rebase or merge your branch with master on a regular basis while you are working on your branch to keep it up to date with master.
* Commit regularly. Commit small chunks of work.
* Try and ensure each commit compiles without uncommited work.
* Provide commit messages that describe **why** you're changing files not **what** you changed.
* Tag your commits with GitHub issue or PR numbers when relevant.
* See [https://help.github.com/en/articles/closing-issues-using-keywords](https://help.github.com/en/articles/closing-issues-using-keywords) for more information about GitHub issue automation.
* Keep your total changes as small as you can. Don't do too much work in a single branch is it makes it harder to code review.
* Write unit and UI automation tests for your feature or bugfix.

#### Commit messages

First read Chris Beams' post on [How to Write a Git Commit Message](https://chris.beams.io/posts/git-commit/).

##### The seven rules of a great Git commit message

Keep in mind: [This](https://tbaggery.com/2008/04/19/a-note-about-git-commit-messages.html) [has](https://www.git-scm.com/book/en/v2/Distributed-Git-Contributing-to-a-Project#_commit_guidelines) [all](https://github.com/torvalds/subsurface-for-dirk/blob/master/README#L92-L120) [been](http://who-t.blogspot.com/2009/12/on-commit-messages.html) [said](https://github.com/erlang/otp/wiki/writing-good-commit-messages) [before](https://github.com/spring-projects/spring-framework/blob/30bce7/CONTRIBUTING.md#format-commit-messages).

1. Separate subject from body with a blank line
2. Limit the subject line to 50 characters
3. Capitalize the subject line
4. Do not end the subject line with a period
5. Use the imperative mood in the subject line
6. Wrap the body at 72 characters
7. Use the body to explain what and why vs. how

## Lint your contribution

This project uses [KTLint](https://github.com/pinterest/ktlint) for code style validation.

It is recommended to copy the [pre-commit](/support/scripts/git/pre-commit) and [pre-push](/support/scripts/git/pre-push) scripts to your local [git hooks](.git/hooks) folder to perform automatic linting before you commit/push.

Otherwise, lint the project before pushing your branch.

    ./support/scripts/lint

### Test your contribution

Run the project unit tests before pushing your branch.

    ./support/scripts/unit-test
    ./support/scripts/instrumented-test

#### Push your branch and create a Pull Request

Push your branch and open a Pull Request - following the [Pull Request Template](.github/PULL_REQUEST_TEMPLATE.md).

    git push origin feature/short-feature-description

**Note**: Pushing changes directly to the `main` branch of this repository is not allowed. All changes must be integrated via an approved pull-request. All pull request branches are also built but a continuous integration build agent and must pass all checks before they can be merged.

## How to report a bug

Use Github issues to track bugs - following the [Bug Report Issue Template](.github/ISSUE_TEMPLATE/bug_report.md).
