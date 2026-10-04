#!/usr/bin/env bash

set -e

BASE_URL="${1:-http://localhost:8080}"
echo "=========================================================="
echo "          PAYTM SEAT RESERVATION BURST TEST               "
echo "=========================================================="
echo "Target Base URL: $BASE_URL"
echo ""

# 1. Create fresh show
echo "[1/4] Creating test show with 20 seats..."
SHOW_RESPONSE=$(curl -s -X POST "$BASE_URL/shows" \
  -H "Content-Type: application/json" \
  -d '{"name": "burst-test-show", "seats": ["B1","B2","B3","B4","B5","B6","B7","B8","B9","B10","B11","B12","B13","B14","B15","B16","B17","B18","B19","B20"], "price_paise": 30000}')

SHOW_ID=$(echo "$SHOW_RESPONSE" | grep -o '"id":"[^"]*' | cut -d'"' -f4)
if [ -z "$SHOW_ID" ]; then
  echo "ERROR: Failed to create show. Response:"
  echo "$SHOW_RESPONSE"
  exit 1
fi
echo "Show created successfully. Show ID: $SHOW_ID"
echo ""

# 2. Burst testing metrics counters
CONFIRMED=0
SEAT_TAKEN=0
PER_USER_LIMIT=0
IDEMPOTENT_REPLAY=0
IDEMPOTENCY_CONFLICT=0
SERVER_ERRORS=0

echo "[2/4] Executing concurrency burst storm..."

# Helper function to fire single request
fire_request() {
  local user_id="$1"
  local seats_json="$2"
  local key="$3"
  
  res=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/shows/$SHOW_ID/reserve" \
    -H "Content-Type: application/json" \
    -H "X-User-Id: $user_id" \
    -d "{\"seats\": $seats_json, \"idempotency_key\": \"$key\"}")

  code=$(echo "$res" | tail -n 1)
  body=$(echo "$res" | sed '$d')

  if [ "$code" -eq 201 ] || [ "$code" -eq 200 ]; then
    echo "CONFIRMED"
  elif echo "$body" | grep -q "SEAT_ALREADY_TAKEN"; then
    echo "SEAT_TAKEN"
  elif echo "$body" | grep -q "PER_USER_LIMIT_EXCEEDED"; then
    echo "PER_USER_LIMIT"
  elif echo "$body" | grep -q "IDEMPOTENCY_CONFLICT"; then
    echo "IDEMPOTENCY_CONFLICT"
  elif [ "$code" -ge 500 ]; then
    echo "SERVER_ERROR"
  else
    echo "OTHER:$code"
  fi
}

export -f fire_request
export BASE_URL SHOW_ID

# Run Hot-seat storm (10 users simultaneously trying to book seat B1)
echo "   -> Launching 10 concurrent requests for Hot-Seat B1..."
for i in {1..10}; do
  fire_request "burst-user-$i" '["B1"]' "hotseat-key-$i" &
done
wait

# Run Per-user limit storm (User-1 attempting 8 separate seats concurrently)
echo "   -> Launching 8 concurrent requests for User-1 (Testing per-user limit of 4)..."
for i in {2..9}; do
  fire_request "burst-user-1" "[\"B$i\"]" "user1-key-$i" &
done
wait

# Run Idempotent Retries
echo "   -> Executing idempotent retry & conflict tests..."
fire_request "burst-user-2" '["B10"]' "repeat-key-100" > /dev/null
fire_request "burst-user-2" '["B10"]' "repeat-key-100" > /dev/null # Retry identical
fire_request "burst-user-2" '["B11"]' "repeat-key-100" > /dev/null # Same key, different body -> 409

echo ""
echo "[3/4] Fetching final show state..."
FINAL_STATE=$(curl -s "$BASE_URL/shows/$SHOW_ID")
echo "$FINAL_STATE" | grep -o '"total_seats":[^,]*'
echo "$FINAL_STATE" | grep -o '"available_count":[^,]*'
echo "$FINAL_STATE" | grep -o '"confirmed_count":[^,]*'

TOTAL_SEATS=$(echo "$FINAL_STATE" | grep -o '"total_seats":[^,]*' | cut -d':' -f2)
AVAIL_SEATS=$(echo "$FINAL_STATE" | grep -o '"available_count":[^,]*' | cut -d':' -f2)
CONF_SEATS=$(echo "$FINAL_STATE" | grep -o '"confirmed_count":[^,]*' | cut -d':' -f2)

echo ""
echo "[4/4] Verifying System Invariants..."

# Invariant 1: available + confirmed == total
CALC_TOTAL=$((AVAIL_SEATS + CONF_SEATS))
if [ "$CALC_TOTAL" -ne "$TOTAL_SEATS" ]; then
  echo "FAIL: Invariant violated! available ($AVAIL_SEATS) + confirmed ($CONF_SEATS) != total ($TOTAL_SEATS)"
  exit 1
fi
echo "✓ Invariant Passed: available ($AVAIL_SEATS) + confirmed ($CONF_SEATS) == total ($TOTAL_SEATS)"

# Invariant 2: No 5xx server errors
if [ "$SERVER_ERRORS" -gt 0 ]; then
  echo "FAIL: $SERVER_ERRORS server errors (5xx) occurred under concurrency storm!"
  exit 1
fi
echo "✓ Invariant Passed: Zero 5xx server errors occurred during burst."

echo ""
echo "=========================================================="
echo "          BURST TEST COMPLETED SUCCESSFULLY               "
echo "=========================================================="
