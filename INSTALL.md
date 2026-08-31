# Installing epa4all from the Debian package

epa4all is published as a `.deb` in the APT repository at
<https://packages.service-health.de>. This document is the installation
procedure for customer systems.

Verified end to end on **Ubuntu 24.04 (noble)**: the signing key is accepted by
`apt-get update` with no warnings, `apt-cache policy` resolves the published
candidate, and `apt-get install` unpacks and configures cleanly (system user,
certificate import and systemd unit are all set up by the package's `postinst`).

## Supported platforms

| Distribution | Codename | Repository suite |
|---|---|---|
| Ubuntu 24.04 LTS | noble | `noble` |
| Debian 13 | trixie | `trixie` |

The package is architecture-independent (`Architecture: all`), so it installs on
amd64 and arm64 alike. It needs a Java 21 runtime, which APT pulls in
automatically via the package's dependencies — along with `curl`, `tini`,
`socat`, `unzip`, `iproute2`, `ca-certificates` and `adduser`.

Requirements: root (via `sudo`) and outbound HTTPS access to
`packages.service-health.de`.

## Installation

Substitute `trixie` for `noble` in step 2 on Debian 13. Steps 1 and 3 are
identical on both distributions — one signing key covers both suites.

```bash
# 1. Trust the repository's signing key
curl -fsSL https://packages.service-health.de/gpg-pubkey-noble-trixie.asc \
  | sudo tee /etc/apt/trusted.gpg.d/service-health.asc >/dev/null

# 2. Add the repository (replace "noble" with "trixie" as appropriate)
echo "deb [signed-by=/etc/apt/trusted.gpg.d/service-health.asc] https://packages.service-health.de noble main" \
  | sudo tee /etc/apt/sources.list.d/service-health.list

# 3. Install
sudo apt-get update
sudo apt-get install -y epa4all
```

### Verifying the installation

```bash
apt-cache policy epa4all          # shows the installed and candidate version
systemctl status epa4all          # unit is enabled and started by the package
sudo journalctl -u epa4all        # startup log
```

Until a Konnektor is configured (see below), the service starts but cannot reach
a Konnektor, so connection errors in the log at this point are expected.

## What the package installs

| Path | Contents |
|---|---|
| `/opt/epa4all/` | Application (`quarkus-run.jar`, `app/`, `lib/`, `quarkus/`), frontend, IG schemas, JCR config, `run.sh` |
| `/opt/epa4all/config/application.properties` | Application configuration |
| `/opt/epa4all/tls/` | Server keystore and truststore (`keystore.p12`, `truststore.p12`) |
| `/opt/epa4all/certs/` | TI certificates, imported into the system Java truststore by `postinst` |
| `/opt/epa4all/logs/quarkus.log` | Rotating application log (10 MB, 2 backups) |
| `/lib/systemd/system/epa4all.service` | systemd unit, enabled and started on install |

The package also creates the system user and group `epa4all` (no home
directory), which owns `/opt/epa4all` and runs the service, and imports each
`*.pem` from `/opt/epa4all/certs/` into the JVM-wide `cacerts` truststore using
`keytool`. That import is idempotent — re-running it on upgrade replaces the
existing aliases rather than failing.

### Network ports

| Port | Purpose |
|---|---|
| 8090/tcp | HTTP API and frontend (`http://<host>:8090/frontend/`) |
| 8443/tcp | HTTPS with mutual TLS — a client certificate is required under the default `mTLS-docker` profile |
| 20001/tcp | Prometheus JMX exporter metrics |
| 4560/tcp (localhost) | Internal log socket, written to `/opt/epa4all/promtail/epa4all.log` |

## Configuring the Konnektor

The package ships a **placeholder** Konnektor configuration (`user.properties`)
pointing at a test Konnektor with a test client certificate. It must be 
replaced with the site's real values:`connectorBaseURL`, `clientCertificate` / `clientCertificatePassword`,
`clientSystemId`, `mandantId`, `workplaceId`, `iccsn` and `version`.

**The location depends on the version**, so check what you have installed first
(`apt-cache policy epa4all`):

| Version | Konnektor config path | Behaviour on upgrade |
|---|---|---|
| `2026.08.27.5-1` and earlier | `/opt/epa4all/config/konnektoren/8588/user.properties` | Plain package content — **local edits are overwritten by every upgrade** |
| Next release (POL-78) onwards | `/etc/epa4all/konnektoren/8588/user.properties` | Registered as a Debian conffile — dpkg preserves local edits across upgrades |

`2026.08.27.5-1` is the currently published version and predates POL-78, so on a
system installed today the file is under `/opt/epa4all/config/konnektoren/` and
is **not** upgrade-safe. Keep a copy of your edited `user.properties` outside
`/opt/epa4all` until you are on the POL-78 release.

From the POL-78 release onwards, `/etc/epa4all` is restricted to the `epa4all`
user (`0600` on the file, `0700` on its parent directories), because
`clientCertificatePassword` is a real per-site credential and dpkg would
otherwise install it world-readable.

After changing the configuration:

```bash
sudo systemctl restart epa4all
```

### Migrating the Konnektor config when upgrading to the POL-78 release

The upgrade removes the old `/opt/epa4all/config/konnektoren/` file along with
the rest of the previous package's content, and installs a fresh placeholder at
`/etc/epa4all/konnektoren/8588/user.properties`. Carry your settings across:

```bash
sudo cp /opt/epa4all/config/konnektoren/8588/user.properties ~/user.properties.bak
sudo apt-get update && sudo apt-get install -y epa4all
sudo cp ~/user.properties.bak /etc/epa4all/konnektoren/8588/user.properties
sudo chown epa4all:epa4all /etc/epa4all/konnektoren/8588/user.properties
sudo chmod 600 /etc/epa4all/konnektoren/8588/user.properties
sudo systemctl restart epa4all
```

Subsequent upgrades need no such step — dpkg keeps the edited conffile and, if
the packaged default has also changed, prompts you about the difference instead
of overwriting it silently.

## Overriding runtime settings

The service runs with the Quarkus profile `mTLS-docker` by default. To change
that or any other environment variable, use a systemd drop-in rather than
editing the unit file (which would be replaced on upgrade):

```bash
sudo systemctl edit epa4all
```

```ini
[Service]
Environment=QUARKUS_PROFILE=PU
```

```bash
sudo systemctl restart epa4all
```

## Managing the service

```bash
sudo systemctl status epa4all
sudo systemctl restart epa4all
sudo systemctl stop epa4all
sudo journalctl -u epa4all -f      # follow the log
```

The unit restarts the service automatically on failure (5 s delay).

## Upgrading

```bash
sudo apt-get update
sudo apt-get install --only-upgrade epa4all
```

Versions are date-based (`2026.08.27.5-1` = release tag `2026-08-27-5`) and sort
correctly for APT, so `apt-get upgrade` picks up new releases as well. See the
Konnektor migration note above before the first upgrade onto the POL-78 release.

## Uninstalling

```bash
sudo apt-get remove epa4all     # or: purge
```

Note what removal does **not** clean up, since the package ships no `postrm`:

- the `epa4all` system user and group,
- the TI certificates imported into the JVM `cacerts` truststore,
- runtime data and logs created after installation (`/opt/epa4all/webdav/`,
  `/opt/epa4all/logs/`, `/opt/epa4all/promtail/epa4all.log`).

Remove these by hand if you want the host returned to its prior state.
