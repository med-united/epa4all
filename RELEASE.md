# Releasing epa4all — user manual

epa4all uses **date-based release versions** (ISO-8601). No SemVer: this is a
deployable service, not a published API. The same date string is the Maven
version, the Docker image tag, and the GitHub Release name.

| Case | Tag / version |
|------|---------------|
| First release of a day | `2026-05-18` |
| 2nd, 3rd, … same day   | `2026-05-18-2`, `2026-05-18-3`, … |

## Cutting a release

1. Land the change on `main` via the normal PR flow. **No POM edits** — the
   committed `<revision>` is only a static fallback for local/CI/PR builds;
   nothing is published until a tag is pushed.
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

Every build resolves `${revision}` to a concrete value — local/CI/PR → the
static committed fallback, date tag → that date (build-time `-Drevision`
override, POMs untouched), manual → `manual-<sha>`. No image ever ships the
literal `${revision}`. The date lives **only in built artifacts**, never
committed back to the epa4all POMs.

## How it works under the hood

### Maven (`${revision}`)

Every POM uses the CI-friendly `${revision}` property. Its committed value is a
**static fallback** for local/CI/PR builds, set in the root `pom.xml` and — for
the parent-less modules `lib-jcr`, `lib-cetp`, `lib-vau`, `api-telematik` —
also locally. A release **overrides it at build time** with `-Drevision=<tag>`;
the committed POMs are never modified by the workflow.

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
`med-united` repos). They are always built at the **pinned gitlink SHA** that
the tagged epa4all commit points to — never `main`/HEAD of the submodule. The
**source of truth for a submodule's version is its own committed `<revision>`
plus a matching tag in its own repo**. Per release the workflow, for each
submodule:

1. reads its `<revision>` (e.g. `2026-05-18`) from the pinned commit;
2. if the pinned tree still matches that `<revision>` tag's tree →
   **unchanged**, keeps that version;
3. otherwise → **changed**, adopts the current release version, and (when
   `SUBMODULE_PAT` is set) commits the `<revision>` bump + creates the matching
   tag in the submodule's own repo;
4. builds each submodule in isolation at its effective version, then builds the
   rest of the reactor at the release version with the submodule version
   properties pointing at those artifacts.

So an unchanged submodule **retains its previous date**; a changed one moves to
the release date and is stamped back for external consumers.

### When `SUBMODULE_PAT` is not set

The epa4all release still succeeds and is internally consistent. The only thing
skipped is the cross-repo write-back. Consequences:

- **Unchanged submodule** — no impact; it correctly keeps its `<revision>`
  version every release.
- **Changed submodule** — it builds at the release date *this* time, but its
  own repo's `<revision>`/tag is never advanced. So **every subsequent release
  also sees it as "changed"** and re-versions it to that release's date, until
  someone advances its baseline manually. The "unchanged ⇒ retain previous
  version" guarantee only holds once the baseline is current.

Manual baseline bump (do this in the submodule's own repo after it changed, if
`SUBMODULE_PAT` is unset), using the date of the release that shipped it:

```bash
cd lib-cetp                       # or lib-vau / api-telematik
sed -i 's:<revision>[^<]*</revision>:<revision>2026-05-25</revision>:' pom.xml
git commit -am "Release 2026-05-25"
git tag 2026-05-25
git push origin HEAD:main 2026-05-25      # api-telematik: HEAD:OPB5
cd .. && git add lib-cetp && git commit -m "Bump lib-cetp pointer"
```

**Baseline:** all three submodules were seeded at `<revision>2026-05-18</revision>`
with a matching `2026-05-18` tag in each repo (`lib-cetp`/`lib-vau` on `main`,
`api-telematik` on `OPB5`).

## Secrets

| Secret | Required | Purpose |
|--------|----------|---------|
| `DOCKERHUB` | yes | Docker Hub password for `servicehealtherxgmbh` (image push) |
| `SUBMODULE_PAT` | optional | PAT (Contents: read/write on `med-united/{lib-cetp,lib-vau,api-telematik}`). Enables automatic submodule `<revision>` bump + tag on releases that changed a submodule. Unset → step skipped; a changed submodule then re-versions every release until its baseline is bumped manually (see "When `SUBMODULE_PAT` is not set"). |

`GITHUB_TOKEN` (auto, `permissions: contents: write`) creates the GitHub
Release. It **cannot** write to the submodule repos — hence the separate
`SUBMODULE_PAT`.
