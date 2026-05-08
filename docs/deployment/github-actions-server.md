# GitHub Actions Server Deployment

## What This Automates

Pushing to `main` triggers [.github/workflows/deploy-server.yml](/C:/Users/20562/Desktop/桌面/clipSync/.github/workflows/deploy-server.yml). The workflow:

- Checks out the repo on `ubuntu-latest`
- Sets up Go from `clipSync-server/go.mod`
- Runs `go test ./... -v -count=1` in `clipSync-server`
- Builds a Linux `amd64` server binary with `CGO_ENABLED=1`
- Packages the binary and `clipSync-server/configs/config.yaml` into `clipsync-server-release-<git-sha>.tar.gz`
- Uploads the release archive to the deployment host over SSH/SCP
- Pipes [`scripts/deploy/server-release.sh`](/C:/Users/20562/Desktop/桌面/clipSync/scripts/deploy/server-release.sh) to the remote host and executes it with deployment environment variables
- Verifies `http://<DEPLOY_HOST>:8081/api/v1/health` from GitHub Actions after the remote deployment finishes

The current production examples are host `8.141.100.238` and live path `/opt/clipSync-server-src`, but the GitHub secrets are the source of truth.

## Required GitHub Secrets

Configure these repository secrets before enabling the workflow:

| Secret | Example | Purpose |
| --- | --- | --- |
| `DEPLOY_HOST` | `8.141.100.238` | SSH target used by `scp`, `ssh`, and the final external health check |
| `DEPLOY_USER` | `root` | SSH user on the deployment host |
| `DEPLOY_SSH_KEY` | private key contents for the deploy user | Written to `~/.ssh/id_ed25519` inside the workflow |
| `DEPLOY_PATH` | `/opt/clipSync-server-src` | Live server directory that receives `bin/`, `configs/`, and preserved `data/` |
| `DEPLOY_SERVICE_NAME` | `clipsync.service` | systemd service restarted during deploy and rollback |
| `DEPLOY_KNOWN_HOSTS` | output of `ssh-keyscan -H 8.141.100.238` | Host key entry used for strict SSH host verification |

Notes:

- `DEPLOY_SSH_KEY` must match the public key installed for `DEPLOY_USER` on the server.
- `DEPLOY_KNOWN_HOSTS` is required because the workflow uses `StrictHostKeyChecking=yes`.
- Keep `DEPLOY_PATH` and `DEPLOY_SERVICE_NAME` aligned with the actual server layout and systemd unit.
- Optional: `DEPLOY_PUBLIC_HEALTH_URL` can override the final GitHub Actions health-check URL when the public endpoint differs from `http://<DEPLOY_HOST>:8081/api/v1/health`.

## Server Requirements

The workflow assumes the target server already has:

- A Linux environment reachable from GitHub-hosted runners over SSH
- A systemd service matching `DEPLOY_SERVICE_NAME`
- `bash`, `tar`, `curl`, and `systemctl` available on the target host
- A writable deployment directory such as `/opt/clipSync-server-src`
- Permission for `DEPLOY_USER` to write under `DEPLOY_PATH`
- Permission for `DEPLOY_USER` to restart `DEPLOY_SERVICE_NAME`
- The service configured to run the deployed binary from `DEPLOY_PATH/bin/clipsync-server-linux`
- The service configured so the server can find `configs/config.yaml` after startup
- The server health endpoint available at `http://127.0.0.1:8081/api/v1/health`

Because the workflow builds on GitHub Actions, the target server does not need a Go toolchain for deployments.

The server process defaults to the relative path `configs/config.yaml`. That means the systemd unit must do one of the following:

- Set `WorkingDirectory=DEPLOY_PATH` so the default relative config path resolves correctly.
- Export `CLIPSYNC_CONFIG=DEPLOY_PATH/configs/config.yaml` so the binary can start from any working directory.

`ssh` and `scp` are only required on the GitHub Actions runner, not on the deployment host.

## First-Time Setup

1. Confirm the repository version of `clipSync-server/configs/config.yaml` is safe for the deployment environment.
2. Create the required GitHub repository secrets listed above.
3. Install the deploy public key for `DEPLOY_USER` on the server.
4. Capture the server host key for `DEPLOY_KNOWN_HOSTS`.

```bash
ssh-keyscan -H 8.141.100.238
```

5. Ensure the live directory exists and matches the expected layout.

```bash
sudo mkdir -p /opt/clipSync-server-src/bin /opt/clipSync-server-src/configs /opt/clipSync-server-src/data
```

6. Ensure the systemd service points at the deployed binary and satisfies the config lookup requirement.

Example expectations:

- Binary: `/opt/clipSync-server-src/bin/clipsync-server-linux`
- Config: `/opt/clipSync-server-src/configs/config.yaml`
- Data directory: `/opt/clipSync-server-src/data`
- Either `WorkingDirectory=/opt/clipSync-server-src` or `Environment=CLIPSYNC_CONFIG=/opt/clipSync-server-src/configs/config.yaml`

7. Verify the service can start and answer its local health endpoint before relying on automation.

```bash
curl --fail http://127.0.0.1:8081/api/v1/health
```

8. Merge or push a safe change to `main`, then watch the `Deploy Server` workflow in GitHub Actions.

9. If `8081` is not directly reachable from GitHub-hosted runners, add `DEPLOY_PUBLIC_HEALTH_URL` and point it at the real public health endpoint exposed by your proxy or load balancer.

## Deployment Behavior

The remote deployment script is intentionally narrow and opinionated:

- `data/` is preserved. The script creates `DEPLOY_PATH/data` if needed and never deletes or replaces it.
- `configs/config.yaml` is overwritten from the repository on every deploy.
- The live binary path is `DEPLOY_PATH/bin/clipsync-server-linux`.
- The binary backup path is `DEPLOY_PATH/bin/clipsync-server-linux.prev`.
- The config backup path is `DEPLOY_PATH/configs/config.yaml.prev`.
- The uploaded release archive is stored remotely at `/tmp/clipsync-server-release-<git-sha>.tar.gz`.
- The script extracts into a temporary staging directory under `/tmp/clipsync-release.XXXXXX`.
- Archive contents are validated before extraction to reject absolute paths, path traversal, and unsupported entry types.

Rollback behavior:

- Before mutating the live deployment, the script backs up the current binary and config if they exist.
- If an unexpected shell error happens after mutation starts, the script attempts to restore the backups through an `ERR` trap.
- If `systemctl restart <DEPLOY_SERVICE_NAME>` fails, the script restores backups, restarts the service again, and waits for health recovery.
- If the post-deploy local health check fails, the same rollback flow runs.
- Rollback only restores files that actually had prior live versions. On a first deploy, there may be nothing to restore.
- Rollback covers the deployed binary and `config.yaml` only. It does not roll back SQLite database state under `data/`.
- Because the server runs embedded migrations on startup, schema changes must remain backward-compatible with the previous binary if you want rollback to be safe.
- The remote archive is deleted only after a successful deployment. If deployment fails, the archive usually remains in `/tmp/` for inspection.

Health validation happens twice:

- On the server: `server-release.sh` checks `http://127.0.0.1:8081/api/v1/health` up to 10 times with 3-second sleeps.
- In GitHub Actions: the workflow checks `DEPLOY_PUBLIC_HEALTH_URL` when that optional secret is set; otherwise it falls back to `http://<DEPLOY_HOST>:8081/api/v1/health`, up to 10 times with 5-second sleeps.

## Troubleshooting

### SSH Failures

If the workflow fails during `Prepare SSH`, `Upload release archive`, or `Run remote deployment`:

- Re-check `DEPLOY_HOST`, `DEPLOY_USER`, and `DEPLOY_SSH_KEY`.
- Rebuild `DEPLOY_KNOWN_HOSTS` from the current server host key.

```bash
ssh-keyscan -H 8.141.100.238
```

- Confirm the private key in `DEPLOY_SSH_KEY` matches an authorized public key for `DEPLOY_USER`.
- Confirm the server allows SSH from GitHub-hosted runners and is reachable on port `22`.
- Test from a trusted machine with strict host checking enabled:

```bash
ssh -o BatchMode=yes -o StrictHostKeyChecking=yes root@8.141.100.238
```

- If authentication works locally but fails in Actions, re-check line breaks and full key contents in the GitHub secret.

### Health-Check Failures

There are two different health checks, so first identify which one failed:

- Remote script failure means the service did not become healthy on `127.0.0.1:8081`.
- Final workflow failure means the service may be healthy locally, but `http://<DEPLOY_HOST>:8081/api/v1/health` was not reachable or did not return `"status":"ok"` externally.
- If `DEPLOY_PUBLIC_HEALTH_URL` is configured, final workflow failure instead refers to that public URL.

Useful checks on the server:

```bash
systemctl status <DEPLOY_SERVICE_NAME> --no-pager
journalctl -u <DEPLOY_SERVICE_NAME> -n 100 --no-pager
curl --fail http://127.0.0.1:8081/api/v1/health
ls -l /opt/clipSync-server-src/bin/clipsync-server-linux*
ls -l /opt/clipSync-server-src/configs/config.yaml*
```

What to verify:

- The service name in `DEPLOY_SERVICE_NAME` is correct.
- The deployed config in `/opt/clipSync-server-src/configs/config.yaml` contains production-safe values.
- The service is actually starting the binary at `/opt/clipSync-server-src/bin/clipsync-server-linux`.
- Port `8081` is listening and reachable from outside the host if the final GitHub Actions health check is using the default URL.
- If `DEPLOY_PUBLIC_HEALTH_URL` is configured, verify that public endpoint and any proxy/load-balancer routing in front of it.
- If rollback ran, check whether `.prev` files were restored and whether the service recovered to the previous version.

## Self-Review Checklist

Before treating this flow as ready:

- Confirm the secrets in GitHub match the real server.
- Confirm `clipSync-server/configs/config.yaml` is intended to overwrite production on every push to `main`.
- Confirm `DEPLOY_PUBLIC_HEALTH_URL` is set if the API port is not publicly reachable from GitHub-hosted runners.
- Confirm the systemd service still uses the same binary path and health endpoint.
- Confirm operators understand that `data/` is preserved but config is not, and that rollback does not restore SQLite database state.
