# desktop

[Tauri v2](https://v2.tauri.app/) desktop shell for epa4all, targeting Windows, macOS, and Linux.

At this stage the module is a scaffold only. Integration with the Quarkus REST server — packaged via `jpackage` (`mvn -P desktop` in `rest-server/`) and spawned as a sidecar process by the Tauri Rust core — is tracked under follow-up tickets.

## Prerequisites

- [Rust toolchain](https://rustup.rs/) (stable)
- Node.js LTS
- Platform-specific build tools — see <https://v2.tauri.app/start/prerequisites/>

## Running in dev mode

```shell script
cd desktop
npm install
npm run tauri dev
```

The first run compiles the full Tauri dependency tree from source and may take a few minutes; subsequent runs are cached in `src-tauri/target/`.

## Building the Rust crate standalone

```shell script
cd desktop/src-tauri
cargo build
```

## Structure

- `src-tauri/` — Rust crate (Tauri core, sidecar lifecycle will live here)
- `src-tauri/tauri.conf.json` — app metadata, window config, bundle settings
- `index.html`, `src/`, `public/` — placeholder frontend (vanilla HTML/CSS/JS)
- `package.json` — Tauri CLI scripts (`tauri dev`, `tauri build`)
