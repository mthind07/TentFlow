#!/bin/sh
set -eu

#run one real administrator booking lifecycle against a deployed TentFlow
#required secrets are read from the environment and are never printed
base_url=${TENTFLOW_BASE_URL:-http://localhost:8080}
admin_email=${TENTFLOW_ADMIN_EMAIL:?Set TENTFLOW_ADMIN_EMAIL.}
admin_password=${TENTFLOW_ADMIN_PASSWORD:?Set TENTFLOW_ADMIN_PASSWORD.}

command -v curl >/dev/null 2>&1 || {
    echo "curl is required." >&2
    exit 1
}

command -v python3 >/dev/null 2>&1 || {
    echo "python3 is required." >&2
    exit 1
}

json_value() {
    python3 -c \
        'import json,sys; print(json.load(sys.stdin)[sys.argv[1]])' "$1"
}

echo "1/8 Checking readiness at ${base_url}/readyz"

curl --fail --silent --show-error \
    "${base_url}/readyz" >/dev/null

echo "2/8 Signing in as the configured administrator"

login_response=$(curl --fail-with-body --silent --show-error \
    --request POST "${base_url}/api/auth/login" \
    --header 'Content-Type: application/json' \
    --data "$(python3 -c \
        'import json,sys; print(json.dumps({"email":sys.argv[1],"password":sys.argv[2]}))' \
        "$admin_email" "$admin_password")")

access_token=$(printf '%s' "$login_response" | json_value accessToken)
run_id="$(date -u +%Y%m%d%H%M%S)-$$"

echo "3/8 Creating isolated tent inventory for this smoke run"

tents_response=$(curl --fail-with-body --silent --show-error \
    "${base_url}/api/tents" \
    --header "Authorization: Bearer ${access_token}")

tent_dimensions=$(printf '%s' "$tents_response" | python3 -c '
import json,sys
used={(tent["widthFeet"],tent["lengthFeet"]) for tent in json.load(sys.stdin)}
available=next(
    (
        (w,l)
        for w in range(10,41)
        for l in range(w,41)
        if (w,l) not in used
    ),
    None
)
if available is None:
    raise SystemExit("No unused valid tent dimensions remain for smoke data.")
print(*available)
')

set -- $tent_dimensions

tent_response=$(curl --fail-with-body --silent --show-error \
    --request POST "${base_url}/api/tents" \
    --header "Authorization: Bearer ${access_token}" \
    --header 'Content-Type: application/json' \
    --data "{\"widthFeet\":$1,\"lengthFeet\":$2,\"totalQuantity\":4}")

tent_id=$(printf '%s' "$tent_response" | json_value id)

echo "4/8 Creating a unique demo customer"

customer_response=$(curl --fail-with-body --silent --show-error \
    --request POST "${base_url}/api/customers" \
    --header "Authorization: Bearer ${access_token}" \
    --header 'Content-Type: application/json' \
    --data "$(python3 -c \
        'import json,sys; print(json.dumps({"fullName":"Release Smoke Test","email":"release-smoke-"+sys.argv[1]+"@example.com","phone":"416-555-0199"}))' \
        "$run_id")")

customer_id=$(printf '%s' "$customer_response" | json_value id)

echo "5/8 Requesting inventory"

reservation_payload=$(python3 -c '
import datetime,json,sys
day=(datetime.datetime.now(datetime.timezone.utc)+datetime.timedelta(days=30)).date()
at=lambda hour: datetime.datetime.combine(day,datetime.time(hour)).isoformat()
print(json.dumps({
    "customerId":int(sys.argv[1]),
    "tentId":int(sys.argv[2]),
    "quantity":1,
    "eventStart":at(10),
    "eventEnd":at(16),
    "reservedFrom":at(8),
    "reservedUntil":at(18),
    "location":"Toronto release smoke test"
}))
' "$customer_id" "$tent_id")

reservation_response=$(curl --fail-with-body --silent --show-error \
    --request POST "${base_url}/api/reservations" \
    --header "Authorization: Bearer ${access_token}" \
    --header 'Content-Type: application/json' \
    --data "$reservation_payload")

reservation_id=$(printf '%s' "$reservation_response" | json_value id)
requested_status=$(printf '%s' "$reservation_response" | json_value status)

[ "$requested_status" = "PENDING" ] || {
    echo "Expected PENDING, received ${requested_status}." >&2
    exit 1
}

echo "6/8 Confirming and completing reservation ${reservation_id}"

confirmed_response=$(curl --fail-with-body --silent --show-error \
    --request POST "${base_url}/api/reservations/${reservation_id}/confirm" \
    --header "Authorization: Bearer ${access_token}")

[ "$(printf '%s' "$confirmed_response" | json_value status)" = \
    "CONFIRMED" ]

completed_response=$(curl --fail-with-body --silent --show-error \
    --request POST "${base_url}/api/reservations/${reservation_id}/complete" \
    --header "Authorization: Bearer ${access_token}")

[ "$(printf '%s' "$completed_response" | json_value status)" = \
    "COMPLETED" ]

echo "7/8 Verifying the completed lifecycle in the administrator audit"

audit_response=$(curl --fail-with-body --silent --show-error \
    "${base_url}/api/admin/audit-events?size=100" \
    --header "Authorization: Bearer ${access_token}")

printf '%s' "$audit_response" | python3 -c '
import json,sys
reservation_id=sys.argv[1]
events=json.load(sys.stdin)["content"]
found=any(
    event["action"] == "COMPLETE_RESERVATION"
    and event["resourceType"] == "RESERVATION"
    and event["resourceId"] == reservation_id
    and event["oldStatus"] == "CONFIRMED"
    and event["newStatus"] == "COMPLETED"
    and event["outcome"] == "SUCCEEDED"
    for event in events
)
if not found:
    raise SystemExit("Completed reservation audit event was not found.")
' "$reservation_id"

echo "8/8 TentFlow smoke test passed."