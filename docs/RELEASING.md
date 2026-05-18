# Releasing epa4all

epa4all uses **date-based release versions** (ISO-8601). There is no SemVer:
this is a deployable service, not a published API.

## Version format

| Case | Tag / version |
|------|---------------|
| First release of a day | `2026-05-07` |
| 2nd, 3rd, … same day   | `2026-05-07-2`, `2026-05-07-3`, … |

The same string becomes the Maven version, the Docker image tag, and the
GitHub Release name.

## How to cut a release

1. Land the change on `main` through the normal PR flow. Day-to-day builds
   (local, CI, PRs) carry the **last released** date version (the committed
   `<revision>` default) — nothing is published until a tag is pushed.
2. Decide "this ships" and tag the commit with **today's date** (derived
   automatically, never hand-typed):

   ```bash
   TAG="$(date +%F)"                                  # e.g. 2026-05-18
   git tag "$TAG"        && git push origin "$TAG"     # first release today
   git tag "${TAG}-2"    && git push origin "${TAG}-2" # 2nd, 3rd, … same day
   ```

3. The tag push triggers `.github/workflows/docker-build.yml`, which:
   - builds the Maven artifacts at the tag version,
   - pushes `servicehealtherxgmbh/epa4all:<version>` **and** `:latest` to
     Docker Hub (multi-arch amd64/arm64),
   - creates a GitHub Release with auto-generated notes.

### Manual builds (`workflow_dispatch`)

The **Run workflow** button supports two modes:

- **Tag given** → re-runs the release for that existing date tag (same image
  tags, same GitHub Release behaviour).
- **Tag left blank** → ad-hoc build of the selected ref at version
  `manual-<short-sha>`. Pushes **only** `epa4all:manual-<sha>` — it does **not**
  move `:latest`, create a GitHub Release, or stamp submodules.

Every build always resolves `${revision}` to a concrete value: local/CI/PR →
the last released date (committed `<revision>` default), date tag → that date,
manual → `manual-<sha>`. No image ever ships the literal `${revision}`.

## How versioning works under the hood

### Maven (`${revision}`)

Every POM uses the CI-friendly `${revision}` property (default = the last
released date, defined in the root `pom.xml` and — for the parent-less
modules `lib-jcr`, `lib-cetp`, `lib-vau`, `api-telematik` — also locally).
The release workflow overrides it with `-Drevision=<tag>`; each release bumps
the committed default to the new date.

`flatten-maven-plugin` (`resolveCiFriendliesOnly`) rewrites each installed /
deployed POM so it carries the **resolved** version instead of the literal
`${revision}`, keeping artifacts (notably the shared `lib-cetp` / `lib-vau`)
consumable by other projects.

A single Maven reactor cannot give different modules different versions, so
all 14 epa4all-owned modules share one release version.

### Submodules — change-gated independent versions

`lib-cetp`, `lib-vau`, `api-telematik` are git submodules (separate
repositories). Their **effective version** is the date tag of the most recent
release in which their pinned gitlink SHA changed relative to the release
before it. If a submodule did not change since the previous release it keeps
that previous date version.

This is computed **entirely from epa4all's own tag history and gitlinks** —
no submodule-repo write access and no cross-repo tagging is required. The
workflow:

1. Computes each submodule's effective version.
2. Builds each submodule in isolation with `-Drevision=<effective version>`
   (an unchanged submodule is rebuilt from identical source under its prior
   date — equivalent bytes, prior coordinates retained).
3. Builds the rest of the reactor with `-Drevision=<release version>` and
   `-Dlib-cetp.version=… -Dlib-vau.version=… -Dapi-telematik.version=…` so the
   owned modules resolve the submodule artifacts at their effective versions.

Owned→owned dependencies use `${project.version}`; owned→submodule
dependencies use the `${lib-cetp.version}` / `${lib-vau.version}` /
`${api-telematik.version}` properties.

## Required secrets

| Secret | Required | Purpose |
|--------|----------|---------|
| `DOCKERHUB` | yes | Docker Hub password for `servicehealtherxgmbh` (image push) |
| `SUBMODULE_PAT` | optional | PAT with write access to `med-united/{lib-cetp,lib-vau,api-telematik}`. When set, a release that changed a submodule auto-bumps its `<revision>` and creates its matching tag in its own repo. When unset, that step is skipped (do it manually). |

`GITHUB_TOKEN` (auto-provided, `permissions: contents: write`) creates the
GitHub Release. It **cannot** write to the submodule repos — that is why
`SUBMODULE_PAT` is a separate secret.

## Submodule baseline

`lib-cetp`, `lib-vau`, `api-telematik` were seeded at `<revision>2026-05-18</revision>`
with a matching `2026-05-18` tag in each repo. A release detects a submodule as
changed when its checked-out tree differs from its `<revision>` tag's tree, and
then adopts the release version for it.
