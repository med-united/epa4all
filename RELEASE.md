# Releasing epa4all — user manual

epa4all uses **date-based release versions** (ISO-8601). No SemVer: this is a
deployable service, not a published API. The same date string is the Maven
version, the Docker image tag, and the GitHub Release name.

| Case | Tag / version |
|------|---------------|
| First release of a day | `2026-05-18` |
| 2nd, 3rd, … same day   | `2026-05-18-2`, `2026-05-18-3`, … |

## Cutting a release

1. Land the change on `main` via the normal PR flow. Day-to-day builds
   (local, CI, PRs) carry the **last released** date (the committed
   `<revision>` default) — nothing is published until a tag is pushed.
2. Tag the commit with **today's date** (derived automatically, never typed):

   ```bash
   TAG="$(date +%F)"                                   # e.g. 2026-05-18
   git tag "$TAG"     && git push origin "$TAG"         # first release today
   git tag "${TAG}-2" && git push origin "${TAG}-2"     # 2nd, 3rd, … same day
   ```

3. The tag push runs `.github/workflows/docker-build.yml`, which:
   - builds the Maven artifacts at the tag version,
   - pushes `servicehealtherxgmbh/epa4all:<date>` **and** `:latest` to Docker
     Hub (multi-arch amd64/arm64),
   - creates a GitHub Release with auto-generated notes,
   - if `SUBMODULE_PAT` is configured, bumps & tags any changed submodule in
     its own repo.

## Manual builds (Actions → Run workflow)

- **Tag field filled** with an existing date tag → re-runs that release
  (identical behaviour to the tag push).
- **Tag field blank** → ad-hoc build of the selected ref at version
  `manual-<short-sha>`. Pushes **only** `epa4all:manual-<sha>`; does **not**
  move `:latest`, create a Release, or touch submodules.

Every build resolves `${revision}` to a concrete value — local/CI/PR → last
released date, date tag → that date, manual → `manual-<sha>`. No image ever
ships the literal `${revision}`.

## How it works under the hood

### Maven (`${revision}`)

Every POM uses the CI-friendly `${revision}` property. Default = the last
released date, set in the root `pom.xml` and — for the parent-less modules
`lib-jcr`, `lib-cetp`, `lib-vau`, `api-telematik` — also locally. The workflow
overrides it with `-Drevision=<tag>`; each release bumps the committed default.

`flatten-maven-plugin` (`resolveCiFriendliesOnly`) rewrites each installed /
deployed POM so it carries the **resolved** version instead of the literal
`${revision}`, keeping artifacts (notably the shared `lib-cetp` / `lib-vau`)
consumable by other projects.

A single Maven reactor cannot give modules different versions, so all 14
epa4all-owned modules share one release version. Owned→owned dependencies use
`${project.version}`; owned→submodule dependencies use the
`${lib-cetp.version}` / `${lib-vau.version}` / `${api-telematik.version}`
properties.

### Submodules — change-gated independent versions

`lib-cetp`, `lib-vau`, `api-telematik` are git submodules (separate
`med-united` repos). The **source of truth for a submodule's version is its own
committed `<revision>` plus a matching tag in its own repo**. Per release the
workflow, for each submodule:

1. reads its `<revision>` (e.g. `2026-05-18`);
2. if the submodule's checked-out tree still matches that `<revision>` tag's
   tree → **unchanged**, keeps that version;
3. otherwise → **changed**, adopts the current release version, and (when
   `SUBMODULE_PAT` is set) commits the `<revision>` bump + creates the matching
   tag in the submodule's own repo;
4. builds each submodule in isolation at its effective version, then builds the
   rest of the reactor at the release version with the submodule version
   properties pointing at those artifacts.

So an unchanged submodule **retains its previous date**; a changed one moves to
the release date and is stamped back for external consumers.

**Baseline:** all three submodules were seeded at `<revision>2026-05-18</revision>`
with a matching `2026-05-18` tag in each repo (`lib-cetp`/`lib-vau` on `main`,
`api-telematik` on `OPB5`).

## Secrets

| Secret | Required | Purpose |
|--------|----------|---------|
| `DOCKERHUB` | yes | Docker Hub password for `servicehealtherxgmbh` (image push) |
| `SUBMODULE_PAT` | optional | PAT with write access to `med-united/{lib-cetp,lib-vau,api-telematik}`. Enables automatic submodule `<revision>` bump + tag on releases that changed a submodule. Unset → that step is skipped; do it manually. |

`GITHUB_TOKEN` (auto, `permissions: contents: write`) creates the GitHub
Release. It **cannot** write to the submodule repos — hence the separate
`SUBMODULE_PAT`.
