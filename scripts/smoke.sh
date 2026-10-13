#!/usr/bin/env bash
set -euo pipefail
base_url=${BASE_URL:-http://127.0.0.1:8080}
work_dir=$(mktemp -d)
trap 'rm -rf "$work_dir"' EXIT
response_file="$work_dir/response.json"
request() {
  local method=$1 path=$2 expected=$3 body=${4:-} key=${5:-} status
  local args=(-sS --connect-timeout 3 --max-time 15 -X "$method" -o "$response_file" -w '%{http_code}')
  if [[ -n "$body" ]]; then args+=(-H 'Content-Type: application/json' --data "$body"); fi
  if [[ -n "$key" ]]; then args+=(-H "Idempotency-Key: $key"); fi
  status=$(curl "${args[@]}" "$base_url$path")
  if [[ "$status" != "$expected" ]]; then
    printf 'Expected %s for %s %s, got %s\n' "$expected" "$method" "$path" "$status" >&2
    cat "$response_file" >&2
    exit 1
  fi
  cat "$response_file"
}
field() { python3 -c 'import json,sys; print(json.load(sys.stdin)[sys.argv[1]])' "$1"; }
new_uuid() { python3 -c 'import uuid; print(uuid.uuid4())'; }
request GET /health 200 >/dev/null
roaster=$(request POST /api/roasters 201 '{"name":"Smoke Roaster"}')
roaster_id=$(printf '%s' "$roaster" | field id)
lot=$(request POST /api/coffee-lots 201 "{\"roasterId\":\"$roaster_id\",\"name\":\"Smoke Blend\",\"roastProfile\":\"OMNI\",\"originComponents\":[{\"country\":\"Colombia\"},{\"region\":\"Sidama\"}]}")
lot_id=$(printf '%s' "$lot" | field id)
path="/api/coffee-lots/$lot_id/purchases"
key=$(new_uuid)
first=$(request POST "$path" 201 '{"purchasedOn":"2026-10-01","roastedOn":"2026-10-03","weightGrams":250,"price":{"amount":"12.5","currency":"EUR"}}' "$key")
second=$(request POST "$path" 201 '{"purchasedOn":"2026-10-02","weightGrams":200}' "$(new_uuid)")
replay=$(request POST "$path" 200 '{"purchasedOn":"2026-10-01","roastedOn":"2026-10-03","weightGrams":250,"price":{"amount":"12.50","currency":"EUR"}}' "$key")
printf '%s' "$first" > "$work_dir/first.json"
printf '%s' "$second" > "$work_dir/second.json"
printf '%s' "$replay" > "$work_dir/replay.json"
request POST "$path" 409 '{"purchasedOn":"2026-10-04"}' "$key" >/dev/null
request GET "$path" 200 > "$work_dir/history.json"
request GET "/api/coffee-lots/$lot_id" 200 > "$work_dir/lot.json"
python3 - "$work_dir" "$lot_id" <<'PY'
import json, pathlib, sys
root = pathlib.Path(sys.argv[1])
def read(name): return json.loads((root / (name + '.json')).read_text())
first, second, replay, history, lot = map(read, ['first', 'second', 'replay', 'history', 'lot'])
assert first == replay, 'Replay changed the original purchase'
assert len(history) == 2, 'Expected exactly two purchases'
assert [p['id'] for p in history] == [second['id'], first['id']], 'History ordering is wrong'
assert first['state'] == 'PURCHASED' and first['price']['amount'] == '12.50'
assert lot['id'] == sys.argv[2] and len(lot['originComponents']) == 2
print('Smoke passed: one lot, two purchases, exact replay, conflicting retry rejected.')
print('COFFEE_LOT_ID=' + lot['id'])
PY
