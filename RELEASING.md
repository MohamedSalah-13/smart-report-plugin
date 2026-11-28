# Releasing

The library has no other home than its git tags: [JitPack](https://jitpack.io) builds a tag the first time someone
asks for it (`com.github.MohamedSalah-13.smart-report-plugin:<module>:vX.Y.Z`) and serves what it built. A release is
therefore a tag that JitPack has built, and a tag, once pushed, is never moved or deleted.

With Claude Code, the `release-notes` skill writes the changelog entry and the GitHub release, and `maven-release-tag`
does the merge, the version bump, the build, the tag and the push. Neither does the steps after the tag.

## Adding to the public API

A new option is a new method or builder step, never a new component of a public record (`PdfStyle`,
`ColumnDefinition`, `Paper`, `PreviewTexts`, ...). A new component breaks, at the source level, every record pattern
that takes the record apart, even when the old constructor is kept. japicmp checks binary compatibility only, so the
build does not catch it: 2.8.0 needed an upgrade note because `ColumnDefinition` got `total`. 2.9.0 followed the rule:
the document font and the strict glyph mode are `PdfDocument.font(PdfFont)` and
`PdfDocument.failOnMissingGlyphs(boolean)`, not components of `PdfStyle`.

The Javadoc of the public API is in English (2.11.0), so the javadoc jar and an IDE read for everyone; internal
`//` comments may stay in Arabic. A broken `{@link}` fails the build (doclint).

## Before the tag

1. **Merge** the work into `master` through pull requests, with CI green.
2. **Pick the version**, `vMAJOR.MINOR.PATCH`: a new feature is a minor version, a fix a patch. A binary-incompatible
   change to the public API needs a major version: the API check (japicmp, step 5) fails the build otherwise. It does
   not see a new component in a public record ([Adding to the public API](#adding-to-the-public-api)).
3. **Release notes**, one commit (`docs: release notes for X.Y.Z`):
   - `CHANGELOG.md`: a `## [X.Y.Z] - YYYY-MM-DD` entry with Added / Changed / Fixed.
   - The "What's New" section of `README.md` and "ما الجديد" of `README.ar.md`: a `### X.Y.Z` section on top.
   - The version lines: `<version>vX.Y.Z</version>` in the Installation section of both READMEs and in the dependency
     snippets of `claude-skill/pdf-print/SKILL.md`, and the "from `vX.Y.Z`" notes there for what is new.
4. **Bump the poms**, one commit (`chore: bump version to X.Y.Z`): the `<version>` of the parent `pom.xml` and the
   `<parent>` version of the five module poms. `git grep -n "<version>X.Y.Z</version>" -- '*pom.xml'` lists six lines.
5. **Build**: `./mvnw verify`. Every test, and the API check of each module against `api.baseline.version`, the last
   release. `./mvnw` is Maven 3.9.16, the Maven JitPack uses too (it runs `./mvnw install -DskipTests` when the
   repository has the wrapper).
6. **Tag**, annotated: `git tag -a vX.Y.Z -m "Release vX.Y.Z"`.
7. **Push** both: `git push origin master vX.Y.Z`.

## After the tag

1. **JitPack.** The tag starts the [JitPack workflow](.github/workflows/jitpack.yml)
   ([`jitpack-check.sh`](.github/scripts/jitpack-check.sh)): it asks JitPack for the tag, waits up to 20 minutes for
   the build (JitPack gives one up to 15), and downloads every module's jar. The run's summary says what JitPack did;
   a red run means users cannot download the release (see below). To check a tag again:
   `gh workflow run jitpack.yml -f tag=vX.Y.Z`, or `bash .github/scripts/jitpack-check.sh vX.Y.Z` from the
   repository root. CI runs on the tag as well.
2. **GitHub release**, once JitPack has built the tag, so that no release page points at a version that cannot be
   downloaded: `gh release create vX.Y.Z --title vX.Y.Z --notes-file <notes>`, with the changelog entry as notes.
3. **Move the API baseline**: set `api.baseline.version` in the parent `pom.xml` to `vX.Y.Z` on `master`
   (`build: compare the API with vX.Y.Z`), and check it with `./mvnw verify`. Only once JitPack has built the tag:
   the API check downloads the baseline's jars from JitPack, so a baseline JitPack cannot serve fails every build.
   The JitPack run's summary names the line to change. It does not open the pull request itself: this repository
   does not let GitHub Actions open pull requests, and CI does not run on a pull request opened by a workflow, so
   the API check that the change is for would not run on it.

## When JitPack fails

The JitPack run fails with JitPack's message, its first Maven errors, and the build log
(`https://jitpack.io/com/github/MohamedSalah-13/smart-report-plugin/vX.Y.Z/build.log`).

- **Never move or delete the tag.** Someone may already depend on it, and JitPack keeps the result of the build it
  made. One version must always mean one content.
- **Fix the build on `master`** through a pull request: CI runs JitPack's own build command, so it shows whether
  JitPack will build the next tag.
- **Release the next patch version** with the same content plus the fix, and say in its changelog entry and "What's
  New" that the failed version cannot be downloaded and this one replaces it. Make no GitHub release for the failed
  tag, and do not move the API baseline to it.

That is what happened to 2.7.0: without a Maven wrapper JitPack built it with Maven 3.6.3, which the API check's
plugin refuses, so `v2.7.0` has no jars. 2.7.1 is the same release with a Maven 3.9.16 wrapper.

If the run says that JitPack **did not finish** (exit code 2) rather than that the build failed, JitPack was slow or
unreachable: run the check again later. A build that failed for a reason outside the repository can be deleted by a
maintainer signed in on jitpack.io and requested again ([Rebuilding](https://docs.jitpack.io/building/#rebuilding));
the tag stays as it is.
