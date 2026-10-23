# Changelog

All notable changes to this project are documented here. This is the
project's first changelog entry — see git history for everything that
came before it.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Date Versioning](https://datevers.ing) since v26.10.23 (`YY.MM.DD`, the UTC release date, with `-1`, `-2`… for later releases the same day); earlier releases used [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## v26.10.23

### Added

- `tests/version.sh`, which checks that `VERSION.md` is a valid Date Version, a real calendar date not in the future, and the same as the newest entry in this changelog

### Changed

- Switched from Semantic Versioning to [Date Versioning](https://datevers.ing): releases are numbered by their UTC release date as `YY.MM.DD`, with `-1`, `-2`… for later releases the same day. This release follows v1.0.0
- `commit.sh` and `commit.bat` refuse a `VERSION.md` that isn't a valid Date Version or is dated in the future (UTC), and warn when it's dated before today
- `README.md` and `CONTRIBUTING.md` describe Date Versioning and the release steps
- `pom.xml` carries the SemVer form of the version (`26.10.2300`) because Maven expects dotted numeric versions; keep it in step with `VERSION.md` (`26.10.23-1` becomes `26.10.2301`)

## v1.0.0

### Fixed
- `README.md`'s footer "Built & Maintained by StuxieDev" icon now uses the hosted `https://global.media.stuxie.dev/icon.png` icon instead of the live GitHub avatar shorthand
