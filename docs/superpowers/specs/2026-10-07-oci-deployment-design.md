# otp-server-ui: OCI Deployment & Access-Control Design

## Motivation

The project's own founding spec names a free-tier Oracle OCI VM as the
hosting target, but no deployment of *this* project has happened yet. What
already exists in OCI is a different, earlier deployment: a bare upstream
OpenTripPlanner Docker image (no custom API, no frontend), provisioned by
Terraform living in the sibling `bikebus` repo
(`bikebus/terraform-oci/`), on an Always Free `VM.Standard.A1.Flex`
instance (`bike-bus-otp`, 2 OCPU / 12 GB, `eu-stockholm-1`). The only thing
exposed to the internet on that VM is Caddy, gating every request on a
static `X-Auth-Token` header and reverse-proxying authorized requests to
the OTP container, which itself only binds to `127.0.0.1:8081`.

This project replaces that deployment wholesale: the bare OTP container is
swapped for this project's own backend (API + frontend, serving the real
otp-server-ui site), the deployment config moves into this repo as its own
source of truth, and the access-control pattern is carried forward with a
few adjustments (TLS, a 403 instead of 401, a freshly generated token) to
suit a real app-facing deployment rather than a `curl`-only testing
endpoint.

**Core design goal (the user's own framing):** an invalid or missing
`X-Auth-Token` must cost as close to nothing as possible on the VM. Caddy —
a single lightweight process doing a plain string comparison on one header
before ever touching the backend — is what makes this true today, and
nothing in this design changes that shape.

## Non-Goals

- Multi-token / per-user authentication. One shared secret, same as the
  existing deployment — this is access control against casual/automated
  traffic hitting the public IP, not a real multi-tenant auth system. A
  motivated attacker who extracts the token from the Android APK (entirely
  possible — APKs are not secret storage) defeats this; that is an accepted
  tradeoff, not a flaw to design around here.
- Rate limiting beyond the header check itself. Out of scope for this
  pass; Caddy's own rate-limit module could be added later without
  changing anything else in this design.
- The Android app itself. That's this project's own next spec, built
  against the real, deployed, token-gated endpoint this design produces.
- CI/CD automation (no GitHub Actions/build pipeline here) — deploys are a
  manual `terraform apply` + a manual image build/push, matching how the
  existing bikebus deployment already works. Automating that is a
  reasonable future improvement, not part of this pass.
- Zero-downtime/blue-green deploys. A `terraform apply` that updates the
  running container briefly interrupts service; acceptable for a
  single-user personal site.

## Architecture

```
Internet
   |
   | :80 (ACME HTTP-01 challenge + redirect to 443)
   | :443 (TLS, Let's Encrypt via Caddy, domain: otp.brj.one)
   v
+--------------------------------------------------+
| OCI VM "bike-bus-otp" (VM.Standard.A1.Flex, ARM)  |
|                                                    |
|  Caddy (the only process with a public listener)  |
|    - terminates TLS for otp.brj.one               |
|    - checks X-Auth-Token header (plain string     |
|      compare, no backend call, no crypto)          |
|    - match  -> reverse_proxy 127.0.0.1:8081        |
|    - no match -> respond 403 (empty body)          |
|         |                                          |
|         v (loopback only, not reachable externally)|
|  otp-server-ui backend container, :8081            |
|    - Ktor app: API + static frontend               |
|    - GRAPH_FILE_PATH -> baked-in Denmark graph.obj |
|    - GOOGLE_PLACES_API_KEY from env (secret)        |
+--------------------------------------------------+
```

The security boundary is unchanged from today's deployment: OCI's security
list only opens 22 (SSH), 80, and 443. Nothing else reaches the VM's
network interface at all, authorized or not. The backend container never
has a published port on the VM's real interface — only on loopback — so
even a Caddy misconfiguration can't expose it directly.

## Components

### 1. `Dockerfile` (new, in `otp-server-ui` repo root)

Multi-stage build:
- **Build stage:** a JDK 17 image, runs `./gradlew :backend:installDist`
  (or an equivalent distribution task — confirmed during planning) to
  produce a runnable distribution without needing Gradle at runtime.
- **Runtime stage:** a slim JRE 17 image (e.g. `eclipse-temurin:17-jre`),
  copies the built distribution and the pre-built Denmark `graph.obj` in,
  sets `GRAPH_FILE_PATH`, `FRONTEND_DIST_PATH`, `PORT=8080` as defaults,
  and runs the distribution's own launcher script.
- Built **for `linux/arm64`** specifically — the VM is Ampere ARM, not
  x86 — via `podman build --platform linux/arm64` (confirmed working on
  this machine: podman's qemu-based emulation runs a real `aarch64`
  container already, verified directly before writing this spec).
- The frontend (`frontend/dist/`) must be built (`bun run build`) *before*
  the Docker build, since the backend serves it as static files — the
  image bakes in a built copy rather than relying on a live bind mount
  (this is a packaged deployment, not the local fast-iteration Podman
  setup from earlier in this project's history).

### 2. OCI Container Registry (OCIR) — new

- New container repository in this tenancy, region `eu-stockholm-1`
  (registry host `arn.ocir.io`), namespace `axy3etqux7lj` (confirmed via
  `oci os ns get`). Image path:
  `arn.ocir.io/axy3etqux7lj/otp-server-ui:<tag>`.
- Push auth uses an OCI **auth token** (not the API signing key already in
  `~/.oci/config`) — generated via `oci iam auth-token create`, used once
  as the `docker login`/`podman login` password. Confirmed no auth token
  exists yet in this tenancy.
- Always Free includes OCIR storage; a single small image well within
  that allowance.

### 3. `terraform-oci/` — moved into `otp-server-ui`, adapted

Copied from `bikebus/terraform-oci/` and changed:

- **`compute.tf`:** the `null_resource.deploy_otp` provisioner's
  `remote-exec` changes from `docker run <stock OTP image>` to
  `docker login arn.ocir.io` (using the auth token, passed as a sensitive
  Terraform variable) + `docker pull arn.ocir.io/axy3etqux7lj/otp-server-ui:<tag>`
  + `docker run -d --name otp-server-ui --restart unless-stopped -p 127.0.0.1:8081:8080 -e GOOGLE_PLACES_API_KEY=... <image>`.
  No graph/router-config file upload step is needed here anymore — the
  graph is baked into the image itself (see Dockerfile, above), not
  mounted from the host.
- **`network.tf`:** security list drops the old `8080` ingress rule, adds
  `80` and `443`. SSH (`22`) stays, for the registry-login + container
  restart provisioner.
- **`cloud-init.yaml.tftpl`:** the Caddyfile's site address changes from
  `:8080` to the real domain `otp.brj.one` (Caddy then provisions TLS
  automatically via Let's Encrypt — no extra config needed beyond having
  a real domain and ports 80/443 open, both already true here), and
  `respond 401` becomes `respond 403` to match the stated design ("result
  in a 403, no other info given"). The host-level `iptables` rules in
  `runcmd` open 80/443 instead of 8080 (the existing comment about two
  separate firewalls — OCI security list *and* host iptables — still
  applies and gets the same treatment for the new ports).
- **`variables.tf`:** drops `local_graph_path`/`local_router_config_path`/
  `otp_image`/`otp_java_opts` (no longer relevant — baked into the image),
  adds `otp_image_tag` (which OCIR tag to deploy), `ocir_auth_token`
  (sensitive), and `google_places_api_key` (sensitive, new — this
  project's own backend needs it, unlike stock OTP).
- **`outputs.tf`:** `endpoint` changes to
  `https://otp.brj.one` (no more `/otp/routers/default/index/graphql` path
  — this project's own API shape, not stock OTP's GraphQL endpoint).
- A **fresh `otp_auth_token`** is generated for this deployment (same
  `openssl rand -hex 32` convention as today), not reused from the old
  bare-OTP deployment — this token's whole purpose is changing (becoming
  the Android app's embedded secret, not just a personal `curl` credential)
  and deserves its own identity.
- The existing instance is **not destroyed and recreated** from scratch
  where avoidable: `terraform apply` against the updated config reuses the
  same VM (`oci_core_instance.otp` is unchanged in shape/image/region), and
  only the container running on it, the Caddyfile, and the security list
  actually change. (Confirmed during planning: changing the Caddyfile's
  `write_files` content is not itself enough to update a *running*
  instance's cloud-init — see "Open questions for the plan," below.)

### 4. DNS

Already done — `otp.brj.one` already resolves to the VM's current public
IP (confirmed via `nslookup` before writing this spec). No DNS change
needed unless the VM's public IP changes (OCI assigns a new public IP by
default if the instance is ever recreated — flagged as a risk to watch
for during planning, not expected in the normal update path above).

## Error Handling

- **Invalid/missing `X-Auth-Token`:** Caddy responds `403` with an empty
  body. No `WWW-Authenticate` header, no JSON error body, no distinguishing
  information between "wrong token" and "no token at all" — exactly the
  "no other info given" requirement.
- **Backend container crash/restart:** Caddy's `reverse_proxy` returns a
  `502` for a correctly-authorized request if the backend is down — this
  does leak "something's wrong" to an authorized caller, which is
  acceptable (that's the app's own user, not an unauthorized prober) and
  unchanged from today's behavior.
- **ACME/TLS provisioning failure** (e.g. port 80 blocked, DNS not yet
  propagated): Caddy logs this and retries; the site serves plain HTTP (or
  fails entirely) until it succeeds. Confirmed both ports open and DNS
  already correct above, so this is not expected to trigger, but worth a
  real verification step during planning (first real `terraform apply`
  against the new Caddyfile).

## Testing

- **Local:** the existing Podman-based local test setup (from earlier in
  this project's history) can validate the Dockerfile builds and runs
  correctly before ever touching OCI — build the image locally, run it,
  `curl` it directly on a local port, confirm the API responds.
- **Against real OCI, post-deploy:**
  - `curl https://otp.brj.one/search ...` with no header -> expect a bare
    `403`.
  - Same request with the correct `X-Auth-Token` -> expect a real,
    correct API response (reusing one of this project's own already-
    confirmed-real queries, e.g. the Langelandsgade -> Dejret Park & Ride
    trip from earlier in this project's history).
  - `curl -I https://otp.brj.one` -> confirm a valid TLS certificate
    (issued for `otp.brj.one`, not a default/self-signed one).
  - Confirm the frontend's own static assets load (`GET /` returns the
    real `index.html`, not a 404) — this project's backend serves both,
    unlike the old bare-OTP deployment, so this is a genuinely new check,
    not just a port of an old one.

## Open Questions for the Implementation Plan

(Deliberately left for the plan/implementation phase rather than guessed
here — each needs a real answer, not an assumption, before or during
implementation:)

1. **Exact Gradle distribution task name** for `backend` (e.g.
   `installDist` vs a custom shadow-jar task) — confirm against
   `backend/build.gradle.kts` directly rather than assuming.
2. **Whether updating a running instance's cloud-init actually takes
   effect** via `terraform apply` alone, or whether the Caddyfile/iptables
   changes need an explicit re-run step (e.g. a `null_resource` with new
   `remote-exec` triggers, mirroring how `deploy_otp` already re-triggers
   on `graph_sha` changes) — cloud-init's `write_files`/`runcmd` only run
   once at first boot by default; this needs a real mechanism (a new
   `null_resource` provisioner that re-writes `/etc/caddy/Caddyfile` and
   restarts Caddy, rather than relying on cloud-init re-running) to apply
   on an existing instance without recreating it.
3. **Secret handling in Terraform state.** `terraform.tfstate` (confirmed
   present in the bikebus directory already) holds sensitive values in
   plain text by Terraform's own design — moving this repo's own copy
   should carry forward the same `.gitignore` treatment the bikebus repo
   already gives it (confirmed: never commit `terraform.tfvars` or
   `terraform.tfstate`).
4. **Whether the old `bikebus/terraform-oci/` config is destroyed,
   archived, or left in place** once this repo's own copy is live and
   verified — a decision for whoever reviews the implementation plan, not
   guessed here.
