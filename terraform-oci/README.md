# otp-server-ui on an Oracle Cloud Always Free VM

Deploys this project's own backend (API + frontend) behind a
custom-built, rate-limited, token-gated Caddy, on an Always Free
`VM.Standard.A1.Flex` instance. Replaces an earlier deployment of bare
upstream OpenTripPlanner (`bikebus/terraform-oci/`) entirely -- see
`docs/superpowers/specs/2026-10-07-oci-deployment-design.md` for the full
design and `docs/superpowers/plans/2026-10-07-oci-deployment.md` for how
this was built.

## Prerequisites (one-time, manual)

1. **An Oracle Cloud account** with an API signing key set up at
   `~/.oci/config` (see the OCI CLI's own setup docs, or the original
   `bikebus/terraform-oci/README.md`'s own walkthrough if starting from
   scratch).
2. **An SSH key pair** (`ssh-keygen -t ed25519 -f ~/.ssh/id_ed25519` if
   you don't have one).
3. **A GitHub repository** with the `publish-images.yml` workflow
   already run at least once, producing `ghcr.io/<owner>/otp-server-ui`
   and `ghcr.io/<owner>/otp-graph-builder` (both public packages).
4. **A locally-built graph** at `.tools/graph-builder/output/denmark-graph.obj`
   (see the repo root's `docs/graph-build.md`) -- used only for the
   *initial* deploy; the weekly cron job takes over from there.
5. **Two secrets, passed as environment variables, never a `.tfvars`
   file:**
   ```bash
   export TF_VAR_otp_auth_token="$(openssl rand -hex 32)"
   export TF_VAR_google_places_api_key="<your Google Places API key>"
   ```

## Deploy

```bash
cp terraform-oci/terraform.tfvars.example terraform-oci/terraform.tfvars
# edit terraform.tfvars: region, compartment_ocid, github_owner

cd terraform-oci
terraform init
terraform apply
```

This creates a **brand-new** VM (not a reuse of any previous instance) --
see the implementation plan's own "Migration & cutover sequence" for how
to safely point `otp.brj.one` at it and retire the old deployment
afterward, rather than doing a plain `terraform apply` and declaring
victory.

## Verify

```bash
curl --resolve otp.brj.one:443:"$(terraform output -raw public_ip)" -k https://otp.brj.one/search \
  -H "X-Auth-Token: $TF_VAR_otp_auth_token" \
  -X POST -H 'Content-Type: application/json' \
  -d '{"mode":"park_and_ride", ...}'
```

A bare `403` with no header, and a real itinerary response with the
correct header, both confirm Caddy's token check and the backend are
wired up correctly. `curl -I` the same host to confirm TLS once DNS
actually points at it.

## Updating

- **New backend/graph-builder image:** push to `main` (triggers the
  GitHub Actions workflow), then re-run `terraform apply` -- the
  `null_resource.deploy_otp`'s `otp_image_tag` trigger picks up the
  change and restarts the container. The weekly cron job pulls
  `otp-graph-builder` fresh every run regardless.
- **Refreshing the graph by hand** (outside its weekly schedule): SSH in
  and run `/usr/local/bin/otp-graph-refresh.sh` directly.

## Known gotchas

- **Two separate firewalls.** Oracle's Ubuntu images ship host-level
  `iptables` rules that block inbound traffic *in addition to* the OCI
  security list in `network.tf` -- cloud-init opens 80/443 on both, but
  if you change ports later, remember to update both layers.
- **A1 capacity.** Always Free A1 Flex capacity is finite per region and
  occasionally unavailable for new instances. If `apply` fails with an
  out-of-host-capacity error, retry later or try a different
  Always-Free-eligible region.
- **`go install`'s GOPATH isn't always `$HOME/go`.** Confirmed directly
  while building this: resolve it via `$(go env GOPATH)` rather than
  assuming `~/go`, or the xcaddy binary silently ends up somewhere the
  next command doesn't look.
- **Secret redaction in `terraform plan`/`apply` output:** `terraform
  plan` never exercises provisioners at all, so it can't prove this --
  confirmed instead with a real, isolated `terraform apply` (throwaway
  config, `null_resource` + `local-exec`, same Terraform version used for
  this deployment) interpolating a `sensitive = true` variable into a
  provisioner command. The console output showed `(output suppressed due
  to sensitive value in config)` and the secret value appeared zero times
  in the captured apply log, while the file the provisioner wrote still
  contained the real value, confirming redaction is console-output-only
  and doesn't affect what's written to disk. Re-verify this if the
  Terraform version in use changes.
