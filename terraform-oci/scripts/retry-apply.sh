#!/bin/bash
# Retries `terraform apply -auto-approve` in a loop until it succeeds --
# specifically for OCI's "Out of host capacity" error on Always Free A1
# Flex instances, which is transient and frees up intermittently. Any
# OTHER kind of failure stops the loop immediately rather than retrying
# blindly, so a real bug doesn't get masked as a capacity wait.
#
# Run from terraform-oci/ with TF_VAR_otp_auth_token and
# TF_VAR_google_places_api_key already exported in your shell:
#   ./scripts/retry-apply.sh
#
# Runs until it succeeds or hits a non-capacity error -- safe to leave
# running in the background (nohup ./scripts/retry-apply.sh &) since
# already-created resources (VCN, subnet, etc.) are tracked in state and
# won't be recreated on each retry, only the still-missing instance.

set -uo pipefail
cd "$(dirname "$0")/.."

RETRY_INTERVAL_SECONDS=60
ATTEMPT=0

while true; do
  ATTEMPT=$((ATTEMPT + 1))
  echo "=== Attempt $ATTEMPT: $(date) ==="

  OUTPUT=$(terraform apply -auto-approve 2>&1)
  STATUS=$?
  echo "$OUTPUT"

  if [ "$STATUS" -eq 0 ]; then
    echo "=== SUCCESS on attempt $ATTEMPT: $(date) ==="
    exit 0
  fi

  if echo "$OUTPUT" | grep -q "Out of host capacity"; then
    echo "=== Out of host capacity -- retrying in ${RETRY_INTERVAL_SECONDS}s ==="
    sleep "$RETRY_INTERVAL_SECONDS"
  else
    echo "=== Non-capacity error on attempt $ATTEMPT -- stopping. Investigate manually. ==="
    exit 1
  fi
done
