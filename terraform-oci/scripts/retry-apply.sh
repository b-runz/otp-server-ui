#!/bin/bash
# Retries `terraform apply -auto-approve -target=oci_core_instance.otp` in a
# loop until it succeeds -- specifically for OCI's "Out of host capacity"
# error on Always Free A1 Flex instances, which is transient and frees up
# intermittently. Any OTHER kind of failure stops the loop immediately
# rather than retrying blindly, so a real bug doesn't get masked as a
# capacity wait.
#
# Scoped to just the instance (-target) rather than a plain `apply`: every
# other resource (VCN, subnet, IGW, security list, route table) already
# exists and is tracked in state, so a full apply would re-evaluate and
# re-refresh all of them on every single retry for no reason -- -target
# skips straight to the one resource actually still missing, making each
# retry cheaper and faster.
#
# Once the instance itself launches, this runs one final plain `apply`
# (no -target) to let null_resource.deploy_otp's file/remote-exec
# provisioners run -- -target only ever creates the targeted resource
# and its dependencies, never anything that depends ON it.
#
# Run from terraform-oci/ with TF_VAR_otp_auth_token and
# TF_VAR_google_places_api_key already exported in your shell:
#   ./scripts/retry-apply.sh
#
# Safe to leave running in the background (nohup ./scripts/retry-apply.sh &).

set -uo pipefail
cd "$(dirname "$0")/.."

RETRY_INTERVAL_SECONDS=60
ATTEMPT=0

while true; do
  ATTEMPT=$((ATTEMPT + 1))
  echo "=== Attempt $ATTEMPT: $(date) ==="

  OUTPUT=$(terraform apply -auto-approve -target=oci_core_instance.otp 2>&1)
  STATUS=$?
  echo "$OUTPUT"

  if [ "$STATUS" -eq 0 ]; then
    echo "=== Instance launched on attempt $ATTEMPT: $(date) ==="
    break
  fi

  if echo "$OUTPUT" | grep -q "Out of host capacity"; then
    echo "=== Out of host capacity -- retrying in ${RETRY_INTERVAL_SECONDS}s ==="
    sleep "$RETRY_INTERVAL_SECONDS"
  else
    echo "=== Non-capacity error on attempt $ATTEMPT -- stopping. Investigate manually. ==="
    exit 1
  fi
done

echo "=== Running final full apply to deploy the server onto the new instance ==="
terraform apply -auto-approve
exit $?
