# ClipSync Server Auto Deploy Design

## Context

ClipSync currently has a Go server in `clipSync-server` and no repository-level CI/CD assets for automated deployment. The deployment target is an existing Linux host at `8.141.100.238`, accessed as `root` over SSH. The current server deployment directory is `/opt/clipSync-server-src`, and the server has already been deployed manually at least once.

The goal of this design is to automate deployment for the server only. Windows and Android delivery remain out of scope. The authoritative runtime behavior of the server remains unchanged: it still reads `configs/config.yaml`, stores SQLite data under `data/`, exposes WebSocket on `8080`, and exposes HTTP API and health checks on `8081`.

## Goal

Create a GitHub Actions based deployment pipeline that automatically deploys `clipSync-server` when code is pushed to the `main` branch. The pipeline must build a Linux binary, upload a release bundle to the target host, replace the deployed server files under `/opt/clipSync-server-src`, overwrite `configs/config.yaml` with the repository version, preserve runtime data under `data/`, restart the existing service, and fail loudly if post-deploy health checks do not pass.

## Non-Goals

- Automating Windows or Android build and release flows
- Introducing Docker or container orchestration
- Redesigning the server runtime layout beyond what is necessary for safe deployment
- Replacing the current server host, deployment path, or service manager
- Changing protocol schemas or application behavior unrelated to deployment

## Deployment Approach

The recommended deployment path is:

1. Trigger on pushes to `main`
2. Run server tests in GitHub Actions from `clipSync-server`
3. Build a Linux `amd64` server binary in GitHub Actions
4. Package the binary, repository deployment script, and repository `configs/config.yaml`
5. Upload the bundle to the target server over SSH/SCP
6. Run a remote deployment script that stages the release, copies updated artifacts into `/opt/clipSync-server-src`, preserves `data/`, overwrites `configs/config.yaml`, restarts the existing service, and performs a health check

This keeps the build environment in GitHub Actions rather than on the production host, which reduces deployment drift and avoids depending on the server to maintain a full Go/CGO toolchain for each release.

## Architecture

### Workflow Trigger and Responsibilities

A GitHub Actions workflow at `.github/workflows/deploy-server.yml` will own the automation entrypoint. It will:

- Trigger on pushes to `main`
- Check out the repository
- Set up Go on Ubuntu
- Run `go test ./... -v -count=1` inside `clipSync-server`
- Build a Linux `amd64` binary for the server
- Assemble a deployment bundle
- Authenticate to the host using repository secrets
- Upload the bundle and execute the deployment script on the host
- Verify deployment success with an HTTP health request

### Release Bundle Contents

The release bundle should be minimal and explicit. It should include:

- The compiled Linux server binary
- The repository version of `clipSync-server/configs/config.yaml`
- The remote deployment script from `scripts/deploy/server-release.sh`
- Release metadata containing the commit SHA and build timestamp for deployment logging

The bundle should not include `data/`, since the server’s runtime data must survive deployments.

### Remote Deployment Script Responsibilities

The remote script will be executed on the target host after upload. It will:

- Extract the bundle into a temporary staging directory
- Validate that required files exist before touching the live directory
- Ensure target subdirectories such as `bin/` and `configs/` exist
- Back up the currently deployed server binary to a previous-version file
- Copy the new server binary into the live deployment directory
- Copy the new `configs/config.yaml` into the live deployment directory
- Leave `data/` untouched
- Restart the configured systemd service
- Run a post-restart health check against `http://127.0.0.1:8081/api/v1/health`

### Health Validation

The deployment is considered successful only if the server comes back healthy after restart. Health validation should happen in two layers:

- On the server, the remote script checks the local health endpoint to confirm the service is up
- In GitHub Actions, the workflow repeats the check against `http://8.141.100.238:8081/api/v1/health` for clear CI visibility after the remote script finishes

If the service fails to restart or the health endpoint does not return success, the workflow must fail.

## Secrets and Environment Contract

The workflow should read deployment-specific values from GitHub Secrets rather than hard-coding them into the repository.

Required secrets:

- `DEPLOY_HOST`: `8.141.100.238`
- `DEPLOY_USER`: `root`
- `DEPLOY_SSH_KEY`: private key contents from `C:\Users\20562\.ssh\id_ed25519`
- `DEPLOY_PATH`: `/opt/clipSync-server-src`
- `DEPLOY_SERVICE_NAME`: existing systemd service name for the server
- `DEPLOY_KNOWN_HOSTS`: server host key entry used for SSH host verification

The workflow may also use non-secret environment variables for values like the local package name or artifact paths.

## Failure Handling and Rollback

The deployment should be fail-fast and minimally recoverable.

### Failure Rules

- If tests fail, deployment does not start
- If the build fails, deployment does not start
- If upload fails, deployment does not modify the target host
- If required bundle files are missing, the remote script exits before touching the live directory
- If service restart fails, the remote script exits with a non-zero code
- If the health check fails after restart, the deployment is marked failed

### Lightweight Rollback

The first version of rollback should focus on the executable rather than full release versioning.

Before replacing the active binary, the remote script should copy the current binary to a backup filename such as `bin/clipsync-server.prev`. If the new binary fails to restart cleanly or health checks fail, the script should attempt to restore the previous binary and restart the service once more.

This keeps the implementation small while still giving the deployment path a practical recovery mechanism.

## Testing Strategy

Testing should validate both code correctness and deployment script safety.

### CI Validation

- Run the existing Go test suite with `go test ./... -v -count=1`
- Build the Linux binary in the same workflow job or in a dependent deploy job
- Validate release bundle assembly before upload

### Script Validation

The remote deployment script should be written defensively so that the most important behaviors are testable by inspection:

- It must use strict shell flags such as `set -euo pipefail`
- It must validate required environment variables and files
- It must not delete `data/`
- It must restart only the configured service name
- It must fail clearly when health checks do not succeed

If time permits during implementation, a repository-local shell check step can be added to catch basic script issues before deployment.

## Files and Responsibilities

### New Files

- `.github/workflows/deploy-server.yml`
  - Defines the CI/CD pipeline for test, build, package, upload, and deploy
- `scripts/deploy/server-release.sh`
  - Executes the remote staging, copy, restart, rollback, and health check flow
- `docs/deployment/github-actions-server.md`
  - Documents required GitHub secrets, first-time server setup expectations, and how to operate the deployment pipeline

### Existing Files Touched

- `clipSync-server/configs/config.yaml`
  - Not structurally changed by the deployment feature, but explicitly included in the release bundle and overwritten on the server during deployment

## Operational Assumptions

- The target host is Linux and reachable from GitHub Actions over SSH
- `/opt/clipSync-server-src` already exists on the server
- The existing server is managed by systemd
- The systemd service already runs from `/opt/clipSync-server-src`
- The service has permission to read `configs/config.yaml` and write under `data/`
- The health endpoint remains available at `/api/v1/health` on port `8081`

## Risks and Mitigations

### Unknown systemd service name

Risk: the repository currently does not document the actual service name.

Mitigation: treat the service name as a required GitHub Secret and document it clearly.

### Overwriting repository config into production

Risk: production settings can be unintentionally replaced by a development-oriented config file.

Mitigation: document this behavior explicitly and require the repository `config.yaml` to be production-safe before enabling automatic deployment.

### SQLite runtime continuity

Risk: accidental removal of `data/` would destroy runtime state.

Mitigation: the deployment script must never delete or replace `data/`, and the spec makes that preservation requirement explicit.

### CGO build reliability

Risk: `go-sqlite3` requires CGO support and can fail in mismatched build setups.

Mitigation: build on `ubuntu-latest` with a native Linux environment rather than relying on Windows cross-compilation shortcuts.

## Success Criteria

The deployment feature is successful when all of the following are true:

- Pushing to `main` triggers the workflow automatically
- The workflow runs server tests before deployment
- The workflow produces a Linux server binary and uploads a release bundle to the server
- The server deployment updates the binary and `configs/config.yaml` under `/opt/clipSync-server-src`
- The `data/` directory remains intact after deployment
- The existing systemd service restarts successfully
- The post-deploy health endpoint responds successfully
- Setup and maintenance steps are documented in the repository
