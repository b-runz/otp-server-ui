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

This project replaces that deployment wholesale — **and nothing about the
old server is preserved**: the old `bikebus/terraform-oci/` Terraform
state is destroyed outright once the new deployment is live and verified,
not reused or migrated in place. The bare OTP container is swapped for
this project's own backend (API + frontend, serving the real otp-server-ui
site), the deployment config moves into this repo as its own source of
truth, images are built and published by GitHub Actions rather than by
hand, and the access-control pattern is carried forward with real
additions (TLS, Caddy-level rate limiting, a 403 instead of 401, a freshly
generated token) to suit a real app-facing deployment rather than a
`curl`-only testing endpoint.

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
- **Distributed** rate limiting. `caddy-ratelimit`'s in-memory, single-node
  sliding-window limiter is enough for one Caddy instance on one VM; the
  plugin's distributed mode (shared state across multiple Caddy nodes)
  solves a problem this deployment doesn't have.
- The Android app itself. That's this project's own separate spec (see
  `2026-10-07-android-app-design.md`), built against the real, deployed,
  token-gated endpoint this design produces.
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
|  Caddy (the only process with a public listener), custom-built     |
|  via xcaddy with the caddy-ratelimit plugin                       |
|    - terminates TLS for otp.brj.one                               |
|    - rate_limit zone keyed on {client_ip} (in-memory sliding      |
|      window) -> over the limit: respond 403 (same as a bad token  |
|      -- see "Uniform rejection response," below)                  |
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
failure. Also built for `linux/arm64`, pushed to GHCR under its own tag
(see "GitHub Actions + GHCR," below).

### 1c. The VM-side weekly job (cron, provisioned via cloud-init)

A plain shell script, installed as `/etc/cron.d/otp-graph-refresh`,
**03:00 Europe/Copenhagen weekly** (chosen off-peak — see "Known risk:
memory contention," below):

1. `docker pull` the latest `otp-graph-builder` image from GHCR (no
   login needed — see "GitHub Actions + GHCR," below, on making the
   packages public).
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

### 2. Caddy, custom-built with rate limiting

Stock Caddy (the Cloudsmith-distributed `apt install caddy` package used
today) has no rate limiting at all — it's a third-party plugin
(`github.com/mholt/caddy-ratelimit`), compiled in via `xcaddy build --with
github.com/mholt/caddy-ratelimit` (confirmed: not built into core Caddy,
and this is the standard, widely-used module for it). cloud-init's
Caddy-install step changes from "apt install the stock binary" to
"install Go + `xcaddy`, build a custom binary with this one plugin added,
once" — still a native systemd service, same as today, just built
differently. (Running Caddy itself as a container, built via the same CI
pipeline as the app images, was considered and rejected for this pass:
it would need its own persistent volume for ACME certificate storage to
avoid re-requesting a Let's Encrypt cert on every restart, which is more
moving parts than a one-time build step on an otherwise-idle VM
justifies.)

Caddyfile addition, keyed by client IP, checked *before* the
`X-Auth-Token` match (so it caps a flood of any traffic, valid or
invalid, not just unauthorized traffic):

```
otp.brj.one {
  rate_limit {
    zone main {
      key    {client_ip}
      events 120
      window 1m
    }
  }

  handle_errors 429 {
    respond 403
  }

  @authorized header X-Auth-Token "<token>"
  handle @authorized {
    reverse_proxy 127.0.0.1:8081
  }
  respond 403
}
```

(`events 120`/`window 1m` — 2 requests/second sustained per IP — is a
starting point generous enough for one app user's normal burst of
autocomplete/search calls, not a carefully load-tested number; see "Open
Questions.")

**Uniform rejection response (the user's own reasoning).** The
`caddy-ratelimit` module always returns its own `429` internally —
confirmed directly, it has no built-in option to change that status code
— but Caddy's own `handle_errors` directive intercepts it before it
reaches the client and rewrites it to a bare `403`, identical to the
invalid-token response: same status code, same empty body, no
`Retry-After` header (which the plugin's own default response would
otherwise include, and which would itself be a distinguishing signal). A
`429` tells a prober "there's a rate limiter here, and backing off or
spreading requests across IPs will get through" — confirmation that
probing is worth continuing. An indistinguishable `403` gives an attacker
no way to tell "wrong token" apart from "too many requests," so there's
nothing to learn from varying their approach and no signal that a guard
exists at all, beyond the fact that every request keeps failing.

### 3. GitHub Actions + GHCR

- **Prerequisite, not yet true today:** this repo has no GitHub remote
  configured and no `gh` auth in this environment (confirmed directly
  before writing this spec). A real GitHub repository has to exist before
  any of this can run — creating it and pushing this repo to it is a
  concrete first step of the implementation plan, not assumed here.
- One workflow (`.github/workflows/publish-images.yml`), triggered on
  push to `main` (or a tag — confirm during planning), builds **both**
  images for `linux/arm64` and pushes to GHCR:
  - `ghcr.io/<owner>/otp-server-ui:<tag>` (the serving backend,
    Component 1)
  - `ghcr.io/<owner>/otp-graph-builder:<tag>` (Component 1b)
  - Auth to GHCR uses the workflow's own automatic `GITHUB_TOKEN`
    (`permissions: packages: write`) — no separate registry credential to
    create or store.
- **Native ARM64 runners, not QEMU emulation** — GitHub's hosted
  `ubuntu-24.04-arm` runners build real `aarch64` natively; cross-building
  via QEMU emulation would make the backend's own Gradle compile step (and
  especially the graph-builder's heavier dependencies) considerably
  slower for no benefit here. Confirm current availability/cost for this
  repository's plan during planning — this is a real product detail
  worth a direct check, not assumed from general knowledge.
- **GHCR packages set to public.** Nothing about either image is
  sensitive on its own — the actual secrets (`GOOGLE_PLACES_API_KEY`,
  the `X-Auth-Token` value) are injected at container-run time, never
  baked into either image. Making the packages public means the VM's own
  `docker pull` needs no registry credential at all, which is one fewer
  secret that has to exist on the VM.
- This replaces the OCI Container Registry (OCIR) component from an
  earlier draft of this spec entirely — no OCIR repository is created.

### 4. Secrets — `GOOGLE_PLACES_API_KEY` (and the OCI/SSH credentials Terraform itself needs)

The requirement: this key must never be committed to git, never be baked
into the Docker image, and still needs to reach the running container at
deploy time.

- **Supplied to Terraform via the `TF_VAR_` environment-variable
  convention, not a `terraform.tfvars` file.** `variable
  "google_places_api_key" { type = string, sensitive = true }` has no
  default; the operator sets `TF_VAR_google_places_api_key` in their own
  shell before running `terraform apply`. Terraform picks this up
  automatically — nothing about the key ever needs to live in a file at
  all, matching the fetch-and-pipe-directly pattern this project already
  used for the same key earlier (via `gcloud services api-keys
  get-key-string`, piped straight into an env var, never displayed or
  logged).
- **On the VM itself, written to an uploaded env-file, not inlined into
  a visible shell command.** A `remote-exec` provisioner with `docker run
  -e GOOGLE_PLACES_API_KEY=<value>` directly in its `inline` command list
  would put the real value in that VM's own process list (`ps aux`,
  visible to anything else running on the VM for the command's duration)
  and shell history. Instead: Terraform's `file` provisioner writes a
  `/home/ubuntu/otp-server-ui.env` file (`chmod 600`, owner `ubuntu`)
  containing `GOOGLE_PLACES_API_KEY=<value>`, and the `docker run` command
  references it via `--env-file /home/ubuntu/otp-server-ui.env` instead of
  a literal `-e` flag — the value never appears as a bare command-line
  argument anywhere on the VM.
- **Still present in Terraform's own state file**, exactly like
  `otp_auth_token` already is today — an accepted, already-documented
  tradeoff (see Open Question 3, below), not a new one this introduces.
- **The OCI API signing key and SSH key** Terraform itself needs
  (`~/.oci/config`, `ssh_private_key_path`) are unchanged from today's
  setup — already outside git, already on the operator's own machine
  only.

### 5. `terraform-oci/` — moved into `otp-server-ui`, rewritten for a fresh VM

Copied from `bikebus/terraform-oci/` as a **starting point** only — per
the explicit direction to preserve nothing of the old server, this is a
**fresh VM, fresh VCN/subnet/security list, fresh public IP**, not an
in-place update of the existing instance. That resolves what an earlier
draft of this spec left as an open question (whether `terraform apply`
can update a running instance's cloud-init at all) — it no longer needs
to, since cloud-init only ever needs to run once, on a new instance's
first boot.

- **`compute.tf`:** `docker pull ghcr.io/<owner>/otp-server-ui:<tag>` (no
  registry login — see Component 3) + a synchronous one-time run of
  `otp-graph-builder` producing the *initial*
  `/home/ubuntu/otp-graph/graph.obj` (the ongoing weekly refresh is
  cloud-init's own cron job, not Terraform's concern after this first
  run) + the env-file upload (Component 4) + `docker run -d --name
  otp-server-ui --restart unless-stopped -p 127.0.0.1:8081:8080 -v
  /home/ubuntu/otp-graph:/graph:ro -e GRAPH_FILE_PATH=/graph/graph.obj
  --env-file /home/ubuntu/otp-server-ui.env <image>`.
- **`network.tf`:** security list drops the old `8080` ingress rule, adds
  `80` and `443`. SSH (`22`) stays.
- **`cloud-init.yaml.tftpl`:** the Caddy install step changes to the
  xcaddy build (Component 2); the Caddyfile is the rate-limited,
  `otp.brj.one`-addressed, `respond 403` version from Component 2 (not
  the old `:8080`/`respond 401` one); host-level `iptables` rules open
  80/443 instead of 8080; also installs the weekly refresh script + its
  `/etc/cron.d/otp-graph-refresh` entry (Component 1c).
- **`variables.tf`:** drops everything specific to uploading files from
  the developer's machine (`local_graph_path`,
  `local_router_config_path`, `otp_image`, `otp_java_opts`) and OCIR
  (no longer used at all), adds `otp_image_tag` / `graph_builder_image_tag`
  (which GHCR tags to deploy) and `google_places_api_key` (sensitive, see
  Component 4).
- **`outputs.tf`:** `endpoint` changes to `https://otp.brj.one` (no more
  `/otp/routers/default/index/graphql` path — this project's own API
  shape, not stock OTP's GraphQL endpoint).
- A **fresh `otp_auth_token`** is generated for this deployment (same
  `openssl rand -hex 32` convention as today) — this token's whole purpose
  is changing (becoming the Android app's embedded secret, not just a
  personal `curl` credential) and deserves its own identity, not a reused
  one.

### 6. Migration & cutover sequence

Explicit ordering, so there is no window where DNS points at a dead IP
and no step destroys the only working thing before its replacement is
proven:

1. `terraform apply` the new `otp-server-ui/terraform-oci/` config —
   creates a brand-new VM with its own new public IP (the old VM, still
   managed by `bikebus/terraform-oci/`, is completely untouched so far).
2. Verify the new deployment directly against its new IP, **before** any
   DNS change: `curl --resolve otp.brj.one:443:<new-ip> -k
   https://otp.brj.one/...` (the `-k`/`--resolve` combination forces the
   right SNI/Host routing through Caddy while skipping the TLS cert check,
   since the cert won't validate for a bare-IP connection — expected and
   fine for this pre-cutover check only). Confirm the 403-without-token,
   200-with-token, and static-frontend checks from "Testing," below.
3. Update the `otp.brj.one` DNS A record to the new IP (via the Spaceship
   DNS tools already used to confirm the existing record earlier in this
   project's history).
4. Wait for propagation, then re-verify over the real domain name (no
   `--resolve` trick needed) — including that Caddy's own Let's Encrypt
   cert issuance succeeded for the new IP.
5. **Only then**, destroy the old deployment: `terraform destroy` in
   `bikebus/terraform-oci/` — tearing down the old VM, VCN, subnet,
   security list, and internet gateway entirely. Nothing about the old
   server is kept.

## Error Handling

- **Invalid/missing `X-Auth-Token`, or rate limit exceeded:** both produce
  the exact same response — `403`, empty body, no `WWW-Authenticate`
  header, no `Retry-After` header, nothing distinguishing "wrong token"
  from "no token" from "too many requests." The rate-limit check runs
  first in the Caddyfile (Component 2), before the token check or the
  backend, so a flood of *any* traffic is capped, not just unauthorized
  traffic — but by the time a response leaves the VM, which of the two
  guards actually fired is deliberately unknowable from the outside (see
  "Uniform rejection response" in Component 2).
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
- **Rate limiting:** send more than the configured `events`/`window`
  budget from one IP in a short burst and confirm a `403` (**not** a
  `429` — confirm the `handle_errors` rewrite actually fires, not just
  that the plugin's own default response happens to look similar), with
  no `Retry-After` header, byte-for-byte matching a bad-token response's
  shape. Also confirm a normal, slower request pattern is never affected.
- **GHCR pull with no credentials configured on the VM at all** —
  confirms the "packages set to public" decision (Component 3) actually
  works as intended, not just assumed.
- **The full migration/cutover sequence** (Component 6) is itself the
  end-to-end test: a new VM passing every check above *before* DNS
  changes, DNS verified *after* cutover, old infrastructure destroyed
  only once both are confirmed.

## Open Questions for the Implementation Plan

(Deliberately left for the plan/implementation phase rather than guessed
here — each needs a real answer, not an assumption, before or during
implementation:)

1. **Exact Gradle distribution task name** for `backend` (e.g.
   `installDist` vs a custom shadow-jar task) — confirm against
   `backend/build.gradle.kts` directly rather than assuming.
2. **Creating the real GitHub repository** (owner/name, public or
   private) is a genuine prerequisite this spec can't resolve on its own
   — confirmed no remote/auth exists yet in this environment. Every
   `ghcr.io/<owner>/...` path above is a placeholder until this exists.
3. **Secret handling in Terraform state.** `terraform.tfstate` (confirmed
   present in the bikebus directory already) holds sensitive values in
   plain text by Terraform's own design — the new repo's own copy carries
   forward the same `.gitignore` treatment (confirmed: never commit
   `terraform.tfvars` or `terraform.tfstate`). Also worth confirming
   during planning: whether Terraform actually redacts the
   `google_places_api_key` variable's value from CLI plan/apply output
   when it flows through a `file`/`remote-exec` provisioner's own
   arguments (sensitivity propagation through provisioners has
   historically been inconsistent across Terraform versions) — verify
   directly against the real Terraform version in use rather than assume.
4. **Exact Gradle module layout for the promoted `graph-builder/`.**
   `.tools/graph-builder/build.gradle.kts` depends on this repo's other
   modules as flat `files(...)` jar dependencies (built separately,
   referenced by path) rather than real Gradle project dependencies
   (`project(":otp-routing")` etc.) — confirm during planning whether to
   convert these to proper project dependencies now that it's a real
   module in the same Gradle reactor, or keep the existing jar-file
   pattern unchanged (lower-risk, since it's already proven to work).
5. **Whether to keep the download step inside the Java tool itself** (a
   small addition to the promoted module) **or as a shell step in the
   `graph-builder/Dockerfile`/entrypoint script** wrapping the existing
   `./gradlew run --args=...` invocation unchanged. The latter touches
   less of the existing, already-proven driver code.
6. **The exact validation thresholds** for "did the build actually
   succeed" (a minimum file size, confirmed against the known real
   `denmark-graph.obj`'s own size as a reference point) — a concrete
   number belongs in the plan, not guessed here.
7. **GitHub's hosted ARM64 runner availability/cost** for this specific
   repository's actual plan (free/public-repo vs. private-repo pricing) —
   confirm directly, don't assume, before committing the workflow to
   that runner class.
8. **The rate limiter's exact `events`/`window` numbers** (120/1m
   proposed above) are a starting guess, not a measured one — real usage
   data doesn't exist yet for this site. Fine to ship as a starting point
   and adjust once real traffic is observed.
