# ClipSync Server Auto Deploy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add GitHub Actions based automatic deployment for `clipSync-server` so pushes to `main` test, build, upload, deploy, restart, and health-check the Linux server at `8.141.100.238`.

**Architecture:** The implementation adds a GitHub Actions workflow that builds the Linux server binary on `ubuntu-latest`, packages it with the repository `config.yaml` and a remote deployment script, uploads the archive over SSH, and runs the deployment script on the production host. The deployment script stages the release, preserves `data/`, overwrites `configs/config.yaml`, restarts the existing systemd service, and rolls back to the previous binary if restart or health validation fails.

**Tech Stack:** GitHub Actions YAML, Bash, OpenSSH (`ssh` and `scp`), Go 1.22, systemd, curl

---

### Task 1: Add The Remote Deployment Script

**Files:**
- Create: `scripts/deploy/server-release.sh`

- [ ] **Step 1: Write the failing syntax check command for the missing deployment script**

```powershell
bash -n scripts/deploy/server-release.sh
```

- [ ] **Step 2: Run the syntax check to verify it fails before the file exists**

Run:

```powershell
bash -n scripts/deploy/server-release.sh
```

Expected: failure with a message equivalent to `No such file or directory`.

- [ ] **Step 3: Write the deployment script with staging, restart, rollback, and health checks**

```bash
#!/usr/bin/env bash
set -euo pipefail

require_env() {
  local name="$1"
  if [[ -z "${!name:-}" ]]; then
    echo "Missing required environment variable: $name" >&2
    exit 1
  fi
}

rollback_binary() {
  if [[ -f "$BACKUP_BINARY" ]]; then
    echo "Rolling back to previous binary: $BACKUP_BINARY"
    install -m 0755 "$BACKUP_BINARY" "$LIVE_BINARY"
    systemctl restart "$DEPLOY_SERVICE_NAME" || true
  fi
}

wait_for_health() {
  local attempt response

  for attempt in $(seq 1 10); do
    response="$(curl --fail --silent --show-error http://127.0.0.1:8081/api/v1/health || true)"
    if [[ -n "$response" ]] && grep -Eq '"status"[[:space:]]*:[[:space:]]*"ok"' <<<"$response"; then
      echo "Health check passed on attempt $attempt"
      return 0
    fi

    sleep 3
  done

  return 1
}

require_env DEPLOY_ARCHIVE
require_env DEPLOY_PATH
require_env DEPLOY_SERVICE_NAME

if [[ ! -f "$DEPLOY_ARCHIVE" ]]; then
  echo "Deploy archive not found: $DEPLOY_ARCHIVE" >&2
  exit 1
fi

STAGING_DIR="$(mktemp -d /tmp/clipsync-release.XXXXXX)"
trap 'rm -rf "$STAGING_DIR"' EXIT

tar -xzf "$DEPLOY_ARCHIVE" -C "$STAGING_DIR"

RELEASE_ROOT="$STAGING_DIR/clipSync-server"
NEW_BINARY="$RELEASE_ROOT/bin/clipsync-server-linux"
NEW_CONFIG="$RELEASE_ROOT/configs/config.yaml"
LIVE_BINARY="$DEPLOY_PATH/bin/clipsync-server-linux"
BACKUP_BINARY="$DEPLOY_PATH/bin/clipsync-server-linux.prev"
TEMP_BINARY="$DEPLOY_PATH/bin/clipsync-server-linux.new"

if [[ ! -f "$NEW_BINARY" ]]; then
  echo "Release binary missing: $NEW_BINARY" >&2
  exit 1
fi

if [[ ! -f "$NEW_CONFIG" ]]; then
  echo "Release config missing: $NEW_CONFIG" >&2
  exit 1
fi

install -d "$DEPLOY_PATH/bin" "$DEPLOY_PATH/configs" "$DEPLOY_PATH/data"

if [[ -f "$LIVE_BINARY" ]]; then
  cp "$LIVE_BINARY" "$BACKUP_BINARY"
fi

install -m 0755 "$NEW_BINARY" "$TEMP_BINARY"
mv "$TEMP_BINARY" "$LIVE_BINARY"
install -m 0644 "$NEW_CONFIG" "$DEPLOY_PATH/configs/config.yaml"

if ! systemctl restart "$DEPLOY_SERVICE_NAME"; then
  echo "Service restart failed: $DEPLOY_SERVICE_NAME" >&2
  rollback_binary
  exit 1
fi

if ! wait_for_health; then
  echo "Post-deploy health check failed" >&2
  rollback_binary
  exit 1
fi

rm -f "$DEPLOY_ARCHIVE"
echo "Deployment completed successfully"
```

- [ ] **Step 4: Run syntax validation to verify the deployment script parses cleanly**

Run:

```powershell
bash -n scripts/deploy/server-release.sh
```

Expected: no output and exit code `0`.

- [ ] **Step 5: Commit the deployment script**

```bash
git add scripts/deploy/server-release.sh
git commit -m "ci: add remote server deploy script"
```

### Task 2: Add The GitHub Actions Deployment Workflow

**Files:**
- Create: `.github/workflows/deploy-server.yml`
- Modify: `scripts/deploy/server-release.sh`

- [ ] **Step 1: Write the failing workflow validation command before the workflow file exists**

```powershell
Get-Content .github/workflows/deploy-server.yml -Raw | ConvertFrom-Yaml | Out-Null
```

- [ ] **Step 2: Run the workflow validation command to verify it fails before the file exists**

Run:

```powershell
Get-Content .github/workflows/deploy-server.yml -Raw | ConvertFrom-Yaml | Out-Null
```

Expected: failure with a message equivalent to `Cannot find path`.

- [ ] **Step 3: Write the deployment workflow with test, build, package, upload, remote execution, and health checks**

```yaml
name: Deploy ClipSync Server

on:
  push:
    branches:
      - main

jobs:
  deploy-server:
    runs-on: ubuntu-latest
    defaults:
      run:
        shell: bash
    env:
      RELEASE_ARCHIVE: clipsync-server-release.tar.gz
      RELEASE_DIR: release
      DIST_DIR: dist
      REMOTE_ARCHIVE: /tmp/clipsync-server-release-${{ github.sha }}.tar.gz

    steps:
      - name: Check out repository
        uses: actions/checkout@v4

      - name: Set up Go
        uses: actions/setup-go@v5
        with:
          go-version-file: clipSync-server/go.mod

      - name: Run server tests
        working-directory: clipSync-server
        run: go test ./... -v -count=1

      - name: Build Linux server binary
        working-directory: clipSync-server
        run: |
          mkdir -p "../${DIST_DIR}"
          CGO_ENABLED=1 GOOS=linux GOARCH=amd64 go build -o "../${DIST_DIR}/clipsync-server-linux" ./cmd/server

      - name: Assemble release bundle
        run: |
          rm -rf "${RELEASE_DIR}"
          mkdir -p "${RELEASE_DIR}/clipSync-server/bin"
          mkdir -p "${RELEASE_DIR}/clipSync-server/configs"
          mkdir -p "${RELEASE_DIR}/metadata"
          cp "${DIST_DIR}/clipsync-server-linux" "${RELEASE_DIR}/clipSync-server/bin/clipsync-server-linux"
          cp clipSync-server/configs/config.yaml "${RELEASE_DIR}/clipSync-server/configs/config.yaml"
          printf '%s\n' "${GITHUB_SHA}" > "${RELEASE_DIR}/metadata/commit_sha.txt"
          date -u '+%Y-%m-%dT%H:%M:%SZ' > "${RELEASE_DIR}/metadata/build_time_utc.txt"
          tar -czf "${RELEASE_ARCHIVE}" -C "${RELEASE_DIR}" .

      - name: Prepare SSH
        env:
          DEPLOY_SSH_KEY: ${{ secrets.DEPLOY_SSH_KEY }}
          DEPLOY_KNOWN_HOSTS: ${{ secrets.DEPLOY_KNOWN_HOSTS }}
        run: |
          mkdir -p ~/.ssh
          printf '%s\n' "${DEPLOY_SSH_KEY}" > ~/.ssh/id_ed25519
          chmod 600 ~/.ssh/id_ed25519
          printf '%s\n' "${DEPLOY_KNOWN_HOSTS}" > ~/.ssh/known_hosts
          chmod 644 ~/.ssh/known_hosts

      - name: Upload release archive
        env:
          DEPLOY_HOST: ${{ secrets.DEPLOY_HOST }}
          DEPLOY_USER: ${{ secrets.DEPLOY_USER }}
        run: |
          scp -i ~/.ssh/id_ed25519 "${RELEASE_ARCHIVE}" "${DEPLOY_USER}@${DEPLOY_HOST}:${REMOTE_ARCHIVE}"

      - name: Run remote deployment
        env:
          DEPLOY_HOST: ${{ secrets.DEPLOY_HOST }}
          DEPLOY_PATH: ${{ secrets.DEPLOY_PATH }}
          DEPLOY_SERVICE_NAME: ${{ secrets.DEPLOY_SERVICE_NAME }}
          DEPLOY_USER: ${{ secrets.DEPLOY_USER }}
        run: |
          ssh -i ~/.ssh/id_ed25519 "${DEPLOY_USER}@${DEPLOY_HOST}" \
            "DEPLOY_ARCHIVE='${REMOTE_ARCHIVE}' DEPLOY_PATH='${DEPLOY_PATH}' DEPLOY_SERVICE_NAME='${DEPLOY_SERVICE_NAME}' bash -s" \
            < scripts/deploy/server-release.sh

      - name: Verify remote health endpoint
        env:
          DEPLOY_HOST: ${{ secrets.DEPLOY_HOST }}
        run: |
          curl --fail --retry 10 --retry-delay 3 --retry-connrefused \
            "http://${DEPLOY_HOST}:8081/api/v1/health"
```

- [ ] **Step 4: Validate workflow YAML and re-check script syntax**

Run:

```powershell
Get-Content .github/workflows/deploy-server.yml -Raw | ConvertFrom-Yaml | Out-Null
bash -n scripts/deploy/server-release.sh
```

Expected: both commands succeed with exit code `0`.

- [ ] **Step 5: Commit the workflow integration**

```bash
git add .github/workflows/deploy-server.yml scripts/deploy/server-release.sh
git commit -m "ci: add automated server deployment workflow"
```

### Task 3: Add Operator Documentation For First-Time Setup And Ongoing Use

**Files:**
- Create: `docs/deployment/github-actions-server.md`

- [ ] **Step 1: Write the failing documentation existence check**

```powershell
Test-Path docs/deployment/github-actions-server.md
```

- [ ] **Step 2: Run the existence check to verify the documentation file is missing**

Run:

```powershell
Test-Path docs/deployment/github-actions-server.md
```

Expected: `False`.

- [ ] **Step 3: Write the deployment operations guide**

```markdown
# GitHub Actions Server Deployment

## What This Automates

Pushing to `main` triggers `.github/workflows/deploy-server.yml`, which runs the server test suite, builds the Linux `amd64` binary, packages the binary with `clipSync-server/configs/config.yaml`, uploads the release archive to the production host, runs the remote deployment script, restarts the configured systemd service, and verifies the health endpoint.

## Required GitHub Secrets

| Name | Example Value | Purpose |
| --- | --- | --- |
| `DEPLOY_HOST` | `8.141.100.238` | SSH host for deployment |
| `DEPLOY_USER` | `root` | SSH login user |
| `DEPLOY_SSH_KEY` | contents of `C:\Users\20562\.ssh\id_ed25519` | SSH private key used by Actions |
| `DEPLOY_PATH` | `/opt/clipSync-server-src` | Live deployment directory on the server |
| `DEPLOY_SERVICE_NAME` | `clipsync.service` | Existing systemd service restarted after deployment |
| `DEPLOY_KNOWN_HOSTS` | output of `ssh-keyscan -H 8.141.100.238` | SSH host key verification |

## Server Requirements

- Linux host reachable from GitHub Actions over SSH
- Existing deployment directory at `/opt/clipSync-server-src`
- Existing systemd unit referenced by `DEPLOY_SERVICE_NAME`
- `bash`, `tar`, `curl`, and `systemctl` available on the server
- A service working directory that points at `/opt/clipSync-server-src`
- A writable `data/` directory that must survive deployments

## First-Time Setup

1. Add the required GitHub Secrets in the repository settings.
2. Verify the production-safe values inside `clipSync-server/configs/config.yaml` before merging to `main`.
3. Confirm the service named in `DEPLOY_SERVICE_NAME` starts the binary at `/opt/clipSync-server-src/bin/clipsync-server-linux`.
4. Confirm the health endpoint returns success locally:

```bash
curl --fail http://127.0.0.1:8081/api/v1/health
```

5. Push a non-breaking change to `main` and watch the workflow run.

## Deployment Behavior

- `data/` is preserved and is never deleted by the deployment script.
- `configs/config.yaml` is overwritten on each deployment using the repository version.
- The live binary is backed up to `bin/clipsync-server-linux.prev` before replacement.
- If restart or health validation fails, the script restores the backup binary and attempts one recovery restart.

## Troubleshooting

### SSH failures

- Re-check `DEPLOY_SSH_KEY`
- Re-generate `DEPLOY_KNOWN_HOSTS` with:

```bash
ssh-keyscan -H 8.141.100.238
```

### Health check failures

- Confirm the service is running:

```bash
systemctl status clipsync.service
```

- Inspect recent logs:

```bash
journalctl -u clipsync.service -n 100 --no-pager
```

- Confirm the live config is present:

```bash
ls -l /opt/clipSync-server-src/configs/config.yaml
```
```

- [ ] **Step 4: Validate the documentation file contains the required sections**

Run:

```powershell
Select-String -Path docs/deployment/github-actions-server.md -Pattern 'Required GitHub Secrets|Server Requirements|First-Time Setup|Troubleshooting'
```

Expected: matches for all four section titles.

- [ ] **Step 5: Commit the deployment documentation**

```bash
git add docs/deployment/github-actions-server.md
git commit -m "docs: add server deployment setup guide"
```

### Task 4: Run Final Verification Across The Deployment Assets

**Files:**
- Modify: `.github/workflows/deploy-server.yml`
- Modify: `scripts/deploy/server-release.sh`
- Modify: `docs/deployment/github-actions-server.md`

- [ ] **Step 1: Re-run the server test suite before final review**

```powershell
Set-Location clipSync-server
go test ./... -v -count=1
```

- [ ] **Step 2: Re-run workflow and shell validation after all files exist**

Run:

```powershell
Set-Location ..
Get-Content .github/workflows/deploy-server.yml -Raw | ConvertFrom-Yaml | Out-Null
bash -n scripts/deploy/server-release.sh
```

Expected: both validation commands succeed with exit code `0`.

- [ ] **Step 3: Review the changed files for the required deployment behavior**

```powershell
git diff -- .github/workflows/deploy-server.yml scripts/deploy/server-release.sh docs/deployment/github-actions-server.md
```

- [ ] **Step 4: Fix any issues discovered during verification and re-run the affected checks**

```powershell
Get-Content .github/workflows/deploy-server.yml -Raw | ConvertFrom-Yaml | Out-Null
bash -n scripts/deploy/server-release.sh
Set-Location clipSync-server
go test ./... -v -count=1
```

- [ ] **Step 5: Commit the verified deployment assets**

```bash
git add .github/workflows/deploy-server.yml scripts/deploy/server-release.sh docs/deployment/github-actions-server.md
git commit -m "ci: finalize automated server deployment assets"
```
