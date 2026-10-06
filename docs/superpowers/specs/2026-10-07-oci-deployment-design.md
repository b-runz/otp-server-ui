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
- Improving the graph-builder's own output quality (restoring the
  skipped `DirectTransferGenerator`/`IslandPruningModule`/
  `OsmBoardingLocationsModule`/elevation modules — see
  `docs/graph-build.md`'s "Known simplifications"). This design reuses
  that tool's existing behavior unchanged; revisiting those simplifications
  on their own merits is a separate, later decision.

## Architecture

```
Internet
   |
   | :80 (ACME HTTP-01 challenge + redirect to 443)
   | :443 (TLS, Let's Encrypt via Caddy, domain: otp.brj.one)
   v
+------------------------------------------------------------------+
| OCI VM "bike-bus-otp" (VM.Standard.A1.Flex, ARM)                  |
|                                                                    |
|  Caddy (the only process with a public listener)                  |
|    - terminates TLS for otp.brj.one                               |
|    - checks X-Auth-Token header (plain string compare,            |
|      no backend call, no crypto)                                  |
|    - match     -> reverse_proxy 127.0.0.1:8081                    |
|    - no match  -> respond 403 (empty body)                        |
|         |                                                         |
|         v (loopback only, not reachable externally)               |
|  otp-server-ui backend container, :8081                          |
|    - Ktor app: API + static frontend                              |
|    - GRAPH_FILE_PATH -> /home/ubuntu/otp-graph/graph.obj           |
|      (a host bind mount, read-only -- NOT baked into the image;   |
|      see "Graph refresh," below)                                  |
|    - GOOGLE_PLACES_API_KEY from env (secret)                      |
|                                                                    |
|  cron, weekly (chosen off-peak: 03:00 Europe/Copenhagen)          |
|    -> runs the graph-builder image (below), writes to a temp      |
|       path, validates, atomically replaces graph.obj, restarts    |
|       the backend container                                      |
+------------------------------------------------------------------+
```

The security boundary is unchanged from today's deployment: OCI's security
list only opens 22 (SSH), 80, and 443. Nothing else reaches the VM's
network interface at all, authorized or not. The backend container never
has a published port on the VM's real interface — only on loopback — so
even a Caddy misconfiguration can't expose it directly.

## Components

### 1. `Dockerfile` (new, in `otp-server-ui` repo root) — the serving backend

Multi-stage build:
- **Build stage:** a JDK 17 image, runs `./gradlew :backend:installDist`
  (or an equivalent distribution task — confirmed during planning) to
  produce a runnable distribution without needing Gradle at runtime.
- **Runtime stage:** a slim JRE 17 image (e.g. `eclipse-temurin:17-jre`),
  copies the built distribution in, sets `FRONTEND_DIST_PATH`,
  `PORT=8080` as defaults, and runs the distribution's own launcher
  script. **No graph file is baked into this image** — see "Graph
  refresh," below: `GRAPH_FILE_PATH` is supplied at container-run time,
  pointing at a host bind mount the weekly job keeps up to date. This is
  the "image is source code only" requirement: rebuilding/redeploying
  this image never needs a ~600 MB graph file baked in, and the image
  itself never goes stale just because the transit data did.
- Built **for `linux/arm64`** specifically — the VM is Ampere ARM, not
  x86 — via `podman build --platform linux/arm64` (confirmed working on
  this machine: podman's qemu-based emulation runs a real `aarch64`
  container already, verified directly before writing this spec).
- The frontend (`frontend/dist/`) must be built (`bun run build`) *before*
  the Docker build, since the backend serves it as static files — the
  image bakes in a built copy rather than relying on a live bind mount
  (this is a packaged deployment, not the local fast-iteration Podman
  setup from earlier in this project's history).

### 1a. Graph refresh — promoting `.tools/graph-builder` into a real module

**What exists today, and why it isn't directly reusable as-is.** The
Denmark `graph.obj` currently running locally was produced by
`.tools/graph-builder/`'s `BuildFixtureGraph` driver — but that tool is
explicitly documented (`docs/graph-build.md`) as a one-off **fixture**
builder: it is entirely gitignored (never committed, anywhere), it takes
already-downloaded local OSM/GTFS file paths as plain command-line
arguments rather than fetching them itself, and its own docs state a
"full production Denmark-wide graph-building flow" would need more than
it currently does. The actual full-Denmark source files it was run
against (`denmark-latest.osm.pbf`, `GTFS.zip`) came from the sibling
`bikebus` repo's own Python pipeline's build output — a different
project's build artifacts, not anything this project owns or can rely on
long-term.

None of that is a reason to rebuild the tool — per the stated direction,
its existing behavior (and documented simplifications: no
`DirectTransferGenerator`/`IslandPruningModule`/`OsmBoardingLocationsModule`/
elevation — see `docs/graph-build.md`'s "Known simplifications") carries
forward unchanged. What changes is packaging and sourcing:

- **Promoted out of `.tools/` into a real, tracked top-level Gradle
  module** (`graph-builder/`, joining `settings.gradle.kts` alongside
  `backend`, `otp-utils`, etc.) — `.tools/` the directory was always
  meant as "untracked scratch," which no longer fits a component a
  production cron job depends on.
- **Gains a real download step** before `BuildFixtureGraph` runs, against
  two confirmed, real, unauthenticated URLs (verified against bikebus's
  own documented pipeline sources, not guessed):
  - OSM: `https://download.geofabrik.de/europe/denmark-latest.osm.pbf`
  - GTFS: `https://www.rejseplanen.info/labs/GTFS.zip` (Rejseplanen's own
    public feed, which *they* refresh weekly — this project's own weekly
    cadence matches the upstream data's own real refresh rate, not an
    arbitrary interval)
- Run **unclipped** (no bbox clipping — that clipping step was specific
  to producing the small Aarhus-area test fixture; the production job
  wants the real, full-Denmark extract, exactly like the already-proven
  local run that produced today's `denmark-graph.obj`).

### 1b. `graph-builder/Dockerfile` (new) — the weekly refresh job

A **second** image, separate from the serving backend, single-purpose:
download both sources fresh, run the (now-real-module) graph builder with
`-Xmx8g`, write the result to a mounted output path, exit 0 on success or
non-zero (with no partial/corrupt output file left behind) on any
failure. Also built for `linux/arm64`, pushed to the same OCIR repository
under its own tag (e.g. `arn.ocir.io/axy3etqux7lj/otp-graph-builder:<tag>`).

### 1c. The VM-side weekly job (cron, provisioned via cloud-init)

A plain shell script, installed as `/etc/cron.d/otp-graph-refresh`,
**03:00 Europe/Copenhagen weekly** (chosen off-peak — see "Known risk:
memory contention," below):

1. `docker pull` the latest `otp-graph-builder` image from OCIR.
2. `docker run --rm -v /home/ubuntu/otp-graph:/output <image>`, which
   writes `/output/graph.obj.new` (never overwriting the live
   `graph.obj` directly — the container's own job ends at producing a
   candidate file, not at deciding to go live with it).
3. **Validate** the result: exit code was 0, the file exists, and its
   size is sane (a crude but effective check — a truncated/corrupt build
   would be a small fraction of the real file's size).
4. **On success:** atomically `mv graph.obj.new graph.obj` (same
   directory, so this is a rename, not a copy — no window where the file
   is partially written at the live path), then `docker restart
   otp-server-ui` so the already-running backend picks up the new graph
   (it only ever loads the graph once, at startup, into memory — per the
   project's own founding design goal — so a restart is required; the
   file changing on disk alone does nothing on its own).
5. **On failure:** leave the existing `graph.obj` and the running
   backend untouched, log the failure (cron's own mail-on-error, or a
   line to the system journal), exit non-zero. The site keeps serving
   last week's (still working) data rather than going down over a bad
   refresh.

**Known risk: memory contention.** The graph build needs `-Xmx8g`
(confirmed locally); the VM has 12 GB total, and the live serving
backend also needs real heap to hold the graph and handle requests
concurrently. Running both at once risks memory pressure or the
Linux OOM-killer picking a victim. Scheduling the job at 03:00
Europe/Copenhagen (this site's real, low-traffic hours) is the accepted
mitigation for a personal, low-traffic site — not a hard guarantee,
but a reasonable one given the cost of a fancier fix (e.g. a larger VM,
or building on a separate, temporary instance) isn't justified here.

**Initial graph.** The very first `terraform apply` has no `graph.obj`
yet — the plan needs a synchronous first run of the same
`otp-graph-builder` image (a `null_resource` provisioner, conceptually
replacing today's "upload a pre-built file over SCP" step) before the
serving container ever starts, not just a wait for the first Monday's
cron tick.

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
  + (a new, synchronous one-time step) running the `otp-graph-builder`
  image to produce the *initial* `/home/ubuntu/otp-graph/graph.obj` (see
  "Graph refresh," above — the ongoing weekly refresh is cloud-init's
  cron job, not Terraform's concern after this first run) + `docker run
  -d --name otp-server-ui --restart unless-stopped -p
  127.0.0.1:8081:8080 -v /home/ubuntu/otp-graph:/graph:ro -e
  GRAPH_FILE_PATH=/graph/graph.obj -e GOOGLE_PLACES_API_KEY=... <image>`.
  No SCP file-upload provisioner is needed anymore — both the original
  stock-OTP graph upload and this project's own graph are produced
  on-VM now, not pushed from the developer's machine.
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
  applies and gets the same treatment for the new ports). Also installs
  the weekly refresh script + its `/etc/cron.d/otp-graph-refresh` entry
  (see "Graph refresh," above).
- **`variables.tf`:** drops `local_graph_path`/`local_router_config_path`/
  `otp_image`/`otp_java_opts` (no longer relevant — nothing is uploaded
  from the developer's machine anymore), adds `otp_image_tag` /
  `graph_builder_image_tag` (which OCIR tags to deploy), `ocir_auth_token`
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
- **Weekly graph refresh failure** (upstream Geofabrik/Rejseplanen
  unreachable, a malformed download, an OOM during the `-Xmx8g` build,
  disk full, etc.): the refresh script's own validation step (exit code +
  file-exists + sane-size check) catches it before anything about the
  live site changes — no atomic rename, no container restart, the site
  keeps serving last week's graph. The failure itself is logged (cron
  mail-on-error / system journal) for the one human who'd ever read it,
  not surfaced to any site visitor in any way.

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
- **Graph-builder image, before it's ever wired into cron:** run it
  standalone (`docker run --rm -v <tmp dir>:/output
  otp-graph-builder:<tag>`) and confirm it produces a real, loadable
  `graph.obj` from freshly-downloaded sources — the same acceptance check
  `docs/graph-build.md` already established (loads via
  `SerializedGraphObject.load`, correct `otp.serialization.version.id`)
  applies here, now against freshly-downloaded data rather than
  whatever was on disk locally.
- **The refresh script's failure path**, deliberately exercised once:
  point it at a sources URL that 404s (or similar) and confirm the live
  `graph.obj` and the running backend container are both left untouched,
  and the script exits non-zero.

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
5. **Exact Gradle module layout for the promoted `graph-builder/`.**
   `.tools/graph-builder/build.gradle.kts` depends on this repo's other
   modules as flat `files(...)` jar dependencies (built separately,
   referenced by path) rather than real Gradle project dependencies
   (`project(":otp-routing")` etc.) — confirm during planning whether to
   convert these to proper project dependencies now that it's a real
   module in the same Gradle reactor, or keep the existing jar-file
   pattern unchanged (lower-risk, since it's already proven to work).
6. **Whether to keep the download step inside the Java tool itself** (a
   small addition to the promoted module) **or as a shell step in the
   `graph-builder/Dockerfile`/entrypoint script** wrapping the existing
   `./gradlew run --args=...` invocation unchanged. The latter touches
   less of the existing, already-proven driver code.
7. **The exact validation thresholds** for "did the build actually
   succeed" (a minimum file size, confirmed against the known real
   `denmark-graph.obj`'s own size as a reference point) — a concrete
   number belongs in the plan, not guessed here.
