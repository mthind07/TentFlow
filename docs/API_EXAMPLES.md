# TentFlow API examples

Set the service address once:

```shell
export TENTFLOW_BASE_URL='http://localhost:8080'
```

## Log in

```shell
curl --request POST "$TENTFLOW_BASE_URL/api/auth/login" \
  --header 'Content-Type: application/json' \
  --data '{
    "email": "admin@example.com",
    "password": "YOUR_PASSWORD"
  }'
```

Copy only `accessToken` from the response into a local shell variable. Do not
paste a real token into source control, screenshots, or issue reports.

```shell
export TENTFLOW_TOKEN='PASTE_ACCESS_TOKEN'
```

## Create inventory (staff/admin)

```shell
curl --request POST "$TENTFLOW_BASE_URL/api/tents" \
  --header "Authorization: Bearer $TENTFLOW_TOKEN" \
  --header 'Content-Type: application/json' \
  --data '{
    "widthFeet": 20,
    "lengthFeet": 30,
    "totalQuantity": 4
  }'
```

Copy the returned tent `id`. The remaining examples use `1`; replace every tent
ID with the actual returned number.

## Create a customer record (staff/admin)

```shell
curl --request POST "$TENTFLOW_BASE_URL/api/customers" \
  --header "Authorization: Bearer $TENTFLOW_TOKEN" \
  --header 'Content-Type: application/json' \
  --data '{
    "fullName": "Jordan Lee",
    "email": "jordan.lee@example.com",
    "phone": "416-555-0100"
  }'
```

Copy the returned customer `id`. Replace `customerId` below with that number.

## Request a reservation

The example dates are in the future at the time of the 1.0 release. If you use
this example after 2030, choose a later future date. Local date-times represent
Toronto business time and have no UTC suffix.

```shell
curl --request POST "$TENTFLOW_BASE_URL/api/reservations" \
  --header "Authorization: Bearer $TENTFLOW_TOKEN" \
  --header 'Content-Type: application/json' \
  --data '{
    "customerId": 1,
    "tentId": 1,
    "quantity": 1,
    "eventStart": "2030-06-12T10:00:00",
    "eventEnd": "2030-06-12T16:00:00",
    "reservedFrom": "2030-06-12T08:00:00",
    "reservedUntil": "2030-06-12T18:00:00",
    "location": "Toronto, Ontario"
  }'
```

Copy the returned reservation `id`. Replace the reservation ID `1` in the next
commands with that value.

## Confirm and complete (staff/admin)

```shell
curl --request POST "$TENTFLOW_BASE_URL/api/reservations/1/confirm" \
  --header "Authorization: Bearer $TENTFLOW_TOKEN"

curl --request POST "$TENTFLOW_BASE_URL/api/reservations/1/complete" \
  --header "Authorization: Bearer $TENTFLOW_TOKEN"
```

## Verify availability

Replace the tent ID `1` with the actual tent ID.

```shell
curl --get "$TENTFLOW_BASE_URL/api/tents/1/availability" \
  --header "Authorization: Bearer $TENTFLOW_TOKEN" \
  --data-urlencode 'from=2030-06-12T08:00:00' \
  --data-urlencode 'until=2030-06-12T18:00:00'
```

The checked-in `scripts/smoke-test.sh` automates readiness, administrator login,
isolated test inventory/customer creation, reservation request, confirmation,
and completion against a local or deployed instance.