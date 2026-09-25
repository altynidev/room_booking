#!/usr/bin/env bash
# End-to-end smoke test: calls every endpoint of a running instance with curl,
# covering success and error paths. Exits non-zero if any check fails.
#
# Usage: scripts/smoke-test.sh
#   BASE_URL        (default http://localhost:8080)
#   ADMIN_USERNAME  (default admin)
#   ADMIN_PASSWORD  (default admin123)
# Requires bash, curl, sed. Creates uniquely named users/rooms, so it can be rerun.

B=${BASE_URL:-http://localhost:8080}
ADMIN_USER=${ADMIN_USERNAME:-admin}
ADMIN_PASS=${ADMIN_PASSWORD:-admin123}
PASS=0; FAIL=0
LAST=""

# req <expected-status> <description> <curl args...>
req() {
  local expected=$1 desc=$2; shift 2
  local out code
  out=$(curl -s -w $'\n%{http_code}' "$@")
  code=${out##*$'\n'}
  LAST=${out%$'\n'*}
  if [[ "$code" == "$expected" ]]; then
    PASS=$((PASS+1)); printf '  PASS [%s] %s\n' "$code" "$desc"
  else
    FAIL=$((FAIL+1)); printf '  FAIL [%s, expected %s] %s\n' "$code" "$expected" "$desc"
  fi
  printf '       %s\n' "${LAST:0:220}"
}
# check <description> <condition...>: non-HTTP assertion
check() {
  local desc=$1; shift
  if "$@"; then PASS=$((PASS+1)); printf '  PASS %s\n' "$desc"
  else FAIL=$((FAIL+1)); printf '  FAIL %s\n' "$desc"; fi
}
json() { sed -n "s/.*\"$1\":\"\{0,1\}\([^\",}]*\).*/\1/p" <<<"$LAST"; }
# UTC date offset by N days; works with GNU date and BSD/macOS date
day() { date -u -d "$1 days" +%F 2>/dev/null || date -u -v"$1"d +%F; }

if ! curl -s -o /dev/null "$B"; then
  echo "Cannot reach $B - is the app running? (docker compose up -d)"; exit 2
fi

J='Content-Type: application/json'
D=$(day +7)       # a future day
PAST=$(day -1)
RUN=$RANDOM       # makes names unique across reruns

echo "Testing $B"
echo "== Auth"
req 201 "register alice"            -X POST $B/api/auth/register -H "$J" -d "{\"username\":\"alice$RUN\",\"password\":\"secret123\"}"
check "response has no password field" eval '! grep -q password <<<"$LAST"'
req 201 "register bob"              -X POST $B/api/auth/register -H "$J" -d "{\"username\":\"bob$RUN\",\"password\":\"secret123\"}"
req 409 "register duplicate"        -X POST $B/api/auth/register -H "$J" -d "{\"username\":\"alice$RUN\",\"password\":\"secret123\"}"
req 400 "register invalid body"    -X POST $B/api/auth/register -H "$J" -d '{"username":"a","password":"1"}'
req 400 "register malformed JSON"  -X POST $B/api/auth/register -H "$J" -d '{bad json'
req 200 "login admin"               -X POST $B/api/auth/login -H "$J" -d "{\"username\":\"$ADMIN_USER\",\"password\":\"$ADMIN_PASS\"}"
ADMIN=$(json token)
# base64url of {"alg":"HS256"} is eyJhbGciOiJIUzI1NiJ9
check "token is signed with HS256"  eval '[[ "$ADMIN" == eyJhbGciOiJIUzI1NiJ9.* ]]'
req 200 "login alice"               -X POST $B/api/auth/login -H "$J" -d "{\"username\":\"alice$RUN\",\"password\":\"secret123\"}"
ALICE=$(json token)
req 200 "login bob"                 -X POST $B/api/auth/login -H "$J" -d "{\"username\":\"bob$RUN\",\"password\":\"secret123\"}"
BOB=$(json token)
req 401 "login wrong password"      -X POST $B/api/auth/login -H "$J" -d '{"username":"admin","password":"wrong"}'
req 401 "no token"                  $B/api/rooms
req 401 "garbage token"             $B/api/rooms -H "Authorization: Bearer abc.def.ghi"

echo "== Rooms"
req 201 "admin creates room"        -X POST $B/api/rooms -H "Authorization: Bearer $ADMIN" -H "$J" -d "{\"name\":\"Orion$RUN\",\"capacity\":8,\"floor\":2}"
ROOM=$(json id)
req 201 "admin creates 2nd room"    -X POST $B/api/rooms -H "Authorization: Bearer $ADMIN" -H "$J" -d "{\"name\":\"Temp$RUN\",\"capacity\":4,\"floor\":1}"
ROOM2=$(json id)
req 409 "duplicate room name"       -X POST $B/api/rooms -H "Authorization: Bearer $ADMIN" -H "$J" -d "{\"name\":\"Orion$RUN\",\"capacity\":8,\"floor\":2}"
req 400 "invalid room"              -X POST $B/api/rooms -H "Authorization: Bearer $ADMIN" -H "$J" -d '{"name":"","capacity":0}'
req 403 "user creates room"         -X POST $B/api/rooms -H "Authorization: Bearer $ALICE" -H "$J" -d '{"name":"Nope","capacity":2,"floor":1}'
req 200 "list rooms"                $B/api/rooms -H "Authorization: Bearer $ALICE"
req 200 "get room"                  $B/api/rooms/$ROOM -H "Authorization: Bearer $ALICE"
req 404 "get missing room"          $B/api/rooms/999999 -H "Authorization: Bearer $ALICE"
req 400 "get room bad id"           $B/api/rooms/abc -H "Authorization: Bearer $ALICE"
req 200 "admin updates room"        -X PUT $B/api/rooms/$ROOM -H "Authorization: Bearer $ADMIN" -H "$J" -d "{\"name\":\"Orion$RUN\",\"capacity\":12,\"floor\":3}"
req 403 "user updates room"         -X PUT $B/api/rooms/$ROOM -H "Authorization: Bearer $ALICE" -H "$J" -d '{"name":"X","capacity":1,"floor":1}'
req 404 "update missing room"       -X PUT $B/api/rooms/999999 -H "Authorization: Bearer $ADMIN" -H "$J" -d '{"name":"X","capacity":1,"floor":1}'
req 403 "user deletes room"         -X DELETE $B/api/rooms/$ROOM2 -H "Authorization: Bearer $ALICE"
req 204 "admin deletes empty room"  -X DELETE $B/api/rooms/$ROOM2 -H "Authorization: Bearer $ADMIN"
req 404 "delete missing room"       -X DELETE $B/api/rooms/$ROOM2 -H "Authorization: Bearer $ADMIN"

echo "== Bookings"
req 201 "alice books 09-10"         -X POST $B/api/bookings -H "Authorization: Bearer $ALICE" -H "$J" -d "{\"roomId\":$ROOM,\"startTime\":\"${D}T09:00:00Z\",\"endTime\":\"${D}T10:00:00Z\"}"
B1=$(json id)
req 409 "bob overlaps 09:30-11"     -X POST $B/api/bookings -H "Authorization: Bearer $BOB" -H "$J" -d "{\"roomId\":$ROOM,\"startTime\":\"${D}T09:30:00Z\",\"endTime\":\"${D}T11:00:00Z\"}"
req 409 "bob encloses 08-12"        -X POST $B/api/bookings -H "Authorization: Bearer $BOB" -H "$J" -d "{\"roomId\":$ROOM,\"startTime\":\"${D}T08:00:00Z\",\"endTime\":\"${D}T12:00:00Z\"}"
req 201 "bob back-to-back 10-11"    -X POST $B/api/bookings -H "Authorization: Bearer $BOB" -H "$J" -d "{\"roomId\":$ROOM,\"startTime\":\"${D}T10:00:00Z\",\"endTime\":\"${D}T11:00:00Z\"}"
B2=$(json id)
req 201 "alice books 14-15"         -X POST $B/api/bookings -H "Authorization: Bearer $ALICE" -H "$J" -d "{\"roomId\":$ROOM,\"startTime\":\"${D}T14:00:00Z\",\"endTime\":\"${D}T15:00:00Z\"}"
B3=$(json id)
req 400 "end before start"          -X POST $B/api/bookings -H "Authorization: Bearer $ALICE" -H "$J" -d "{\"roomId\":$ROOM,\"startTime\":\"${D}T12:00:00Z\",\"endTime\":\"${D}T11:00:00Z\"}"
req 400 "zero-length"               -X POST $B/api/bookings -H "Authorization: Bearer $ALICE" -H "$J" -d "{\"roomId\":$ROOM,\"startTime\":\"${D}T12:00:00Z\",\"endTime\":\"${D}T12:00:00Z\"}"
req 400 "in the past"               -X POST $B/api/bookings -H "Authorization: Bearer $ALICE" -H "$J" -d "{\"roomId\":$ROOM,\"startTime\":\"${PAST}T09:00:00Z\",\"endTime\":\"${PAST}T10:00:00Z\"}"
req 400 "missing fields"            -X POST $B/api/bookings -H "Authorization: Bearer $ALICE" -H "$J" -d '{}'
req 404 "unknown room"              -X POST $B/api/bookings -H "Authorization: Bearer $ALICE" -H "$J" -d "{\"roomId\":999999,\"startTime\":\"${D}T12:00:00Z\",\"endTime\":\"${D}T13:00:00Z\"}"
req 401 "book without token"        -X POST $B/api/bookings -H "$J" -d "{\"roomId\":$ROOM,\"startTime\":\"${D}T12:00:00Z\",\"endTime\":\"${D}T13:00:00Z\"}"

echo "== Availability"
req 200 "availability $D"           "$B/api/rooms/$ROOM/availability?date=$D" -H "Authorization: Bearer $ALICE"
req 400 "availability bad date"     "$B/api/rooms/$ROOM/availability?date=2026-13-45" -H "Authorization: Bearer $ALICE"
req 400 "availability no date"      "$B/api/rooms/$ROOM/availability" -H "Authorization: Bearer $ALICE"
req 404 "availability missing room" "$B/api/rooms/999999/availability?date=$D" -H "Authorization: Bearer $ALICE"

echo "== My bookings & cancel"
req 200 "alice /my"                 $B/api/bookings/my -H "Authorization: Bearer $ALICE"
req 200 "bob /my"                   $B/api/bookings/my -H "Authorization: Bearer $BOB"
req 403 "bob cancels alice's"       -X PATCH $B/api/bookings/$B1/cancel -H "Authorization: Bearer $BOB"
req 200 "alice cancels own"         -X PATCH $B/api/bookings/$B1/cancel -H "Authorization: Bearer $ALICE"
req 409 "cancel again"              -X PATCH $B/api/bookings/$B1/cancel -H "Authorization: Bearer $ALICE"
req 200 "admin cancels bob's"       -X PATCH $B/api/bookings/$B2/cancel -H "Authorization: Bearer $ADMIN"
req 404 "cancel missing booking"    -X PATCH $B/api/bookings/999999/cancel -H "Authorization: Bearer $ALICE"
req 201 "rebook freed slot 09-10"   -X POST $B/api/bookings -H "Authorization: Bearer $BOB" -H "$J" -d "{\"roomId\":$ROOM,\"startTime\":\"${D}T09:00:00Z\",\"endTime\":\"${D}T10:00:00Z\"}"
req 409 "delete room with bookings" -X DELETE $B/api/rooms/$ROOM -H "Authorization: Bearer $ADMIN"

echo "== Misc"
req 404 "unknown path"              $B/api/nope -H "Authorization: Bearer $ALICE"
check "404 message is clean"        eval 'grep -q "\"message\":\"Endpoint GET /api/nope not found\"" <<<"$LAST"'
req 405 "wrong method"              -X DELETE $B/api/bookings/my -H "Authorization: Bearer $ALICE"

echo
echo "RESULT: $PASS passed, $FAIL failed"
[[ $FAIL -eq 0 ]]
