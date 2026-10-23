# Contributing

This is a small solo sample/reference implementation, not an actively
managed open-source project — but issues and pull requests are still
welcome.

- **Found a bug or have a suggestion?** Open an issue describing it.
- **Want to submit a fix?** Fork the repo, make your change on a branch,
  and open a pull request against `main`. Keep changes focused and
  include a short description of what and why.
- **Releases** follow the standard flow: update `CHANGELOG.md`, bump
  `VERSION.md`, then run `commit.sh` (or `commit.bat` on Windows).

## Versioning

This project uses [Date Versioning](https://datevers.ing). To release:

1. Add a `## vYY.MM.DD` entry at the top of `CHANGELOG.md`, with its sections in this order: Added, Changed, Fixed, Removed, Security, Deprecated.
2. Set `VERSION.md` to the same bare version (no `v`): today's **UTC** date as `YY.MM.DD`, or `YY.MM.DD-1`, `-2`… for later releases the same UTC day. Never a future date. Use UTC, not local time: just after midnight in the UK it is still the previous day in UTC.
3. Run `bash tests/version.sh`.
4. Run `./commit.sh` (or `commit.bat` on Windows). It checks the version, commits and tags `vYY.MM.DD`, and never pushes.
