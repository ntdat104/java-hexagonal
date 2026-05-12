#!/usr/bin/env bash
# ==============================================================================
# test-full.sh — BFSI Digital Onboarding Platform · Full Integration Test Suite
#
# Kịch bản:
#   SETUP   Create MBBank Full KYC workflow (new edge condition format)
#   TC-001  Full sequential KYC + input chaining          → APPROVED (5 steps)
#   TC-002  Condition branch: NOT_LIVE → DECLINED          → DECLINED (2 steps)
#   TC-003  ASYNC node auto-execute & advance              → APPROVED (5 steps)
#   TC-004  AML HIT branch                                → DECLINED (4 steps)
#   TC-005  Dedup DUPLICATE branch                        → DECLINED (3 steps)
#   TC-006  Validation errors
#   TC-007  Fork-Join (PARALLEL_FORK/PARALLEL_JOIN)
#
# Yêu cầu: curl, jq
# Chạy: ./test-full.sh [BASE_URL]   (default: http://localhost:8080)
# ==============================================================================

set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"

# ── Colours ────────────────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
CYAN='\033[0;36m'; BOLD='\033[1m'; RESET='\033[0m'
DIM='\033[2m'; BLUE='\033[0;34m'; MAGENTA='\033[0;35m'

# ── Counters ──────────────────────────────────────────────────────────────────
PASS=0; FAIL=0; TOTAL=0
_HTTP_STATUS_FILE=/tmp/.http_status_$$

# ── Helpers ───────────────────────────────────────────────────────────────────
pass() { echo -e "  ${GREEN}✓${RESET} $1"; PASS=$((PASS+1)); TOTAL=$((TOTAL+1)); }
fail() { echo -e "  ${RED}✗${RESET} $1"; FAIL=$((FAIL+1)); TOTAL=$((TOTAL+1)); }

assert_eq() {
  local label="$1" actual="$2" expected="$3"
  if [[ "$actual" == "$expected" ]]; then pass "$label (got: $actual)"
  else fail "$label — expected: '$expected', got: '$actual'"; fi
}

assert_ne() {
  local label="$1" actual="$2"
  if [[ -n "$actual" && "$actual" != "null" ]]; then pass "$label (got: $actual)"
  else fail "$label — expected non-null/non-empty"; fi
}

assert_len() {
  local label="$1" actual="$2" expected="$3"
  if [[ "$actual" -eq "$expected" ]]; then pass "$label (count: $actual)"
  else fail "$label — expected $expected, got $actual"; fi
}

assert_contains() {
  local label="$1" haystack="$2" needle="$3"
  if echo "$haystack" | grep -q "$needle"; then pass "$label"
  else fail "$label — expected to contain '$needle'"; fi
}

assert_http() {
  local label="$1" expected="$2"
  local actual
  actual=$(cat "$_HTTP_STATUS_FILE" 2>/dev/null || echo "")
  if [[ "$actual" == "$expected" ]]; then pass "$label HTTP $actual"
  else fail "$label — expected HTTP $expected, got $actual"; fi
}

section()    { echo -e "\n${CYAN}${BOLD}▶ $1${RESET}"; }
subsection() { echo -e "  ${YELLOW}$1${RESET}"; }

_compact_json() {
  local raw="$1" max="${2:-300}"
  local compact
  compact=$(echo "$raw" | jq -c . 2>/dev/null || echo "$raw")
  if [[ ${#compact} -gt $max ]]; then echo "${compact:0:$max}…"
  else echo "$compact"; fi
}

post() {
  local url="$1" body="$2"
  local _s _path _status_color
  _path="${url#"$BASE_URL"}"
  echo -e "${DIM}  ┌─ ${BLUE}POST${RESET}${DIM} ${_path}${RESET}" >&2
  echo -e "${DIM}  │  req: $(_compact_json "$body" 200)${RESET}" >&2
  _s=$(curl -s -o /tmp/resp.json -w "%{http_code}" -X POST "$url" -H "Content-Type: application/json" -d "$body")
  printf '%s' "$_s" > "$_HTTP_STATUS_FILE"
  if [[ "$_s" == 2* ]]; then _status_color="$GREEN"; else _status_color="$RED"; fi
  echo -e "${DIM}  └─ ${_status_color}${BOLD}$_s${RESET}${DIM} $(_compact_json "$(cat /tmp/resp.json)" 300)${RESET}" >&2
  cat /tmp/resp.json
}

get() {
  local url="$1"
  local _s _path _status_color
  _path="${url#"$BASE_URL"}"
  echo -e "${DIM}  ┌─ ${MAGENTA}GET${RESET}${DIM} ${_path}${RESET}" >&2
  _s=$(curl -s -o /tmp/resp.json -w "%{http_code}" "$url")
  printf '%s' "$_s" > "$_HTTP_STATUS_FILE"
  if [[ "$_s" == 2* ]]; then _status_color="$GREEN"; else _status_color="$RED"; fi
  echo -e "${DIM}  └─ ${_status_color}${BOLD}$_s${RESET}${DIM} $(_compact_json "$(cat /tmp/resp.json)" 300)${RESET}" >&2
  cat /tmp/resp.json
}

# ── Connectivity check ─────────────────────────────────────────────────────────
section "Pre-flight: checking server at $BASE_URL"
if ! curl -sf "$BASE_URL/actuator/health" -o /dev/null 2>/dev/null; then
  if ! curl -sf --max-time 3 "$BASE_URL" -o /dev/null 2>/dev/null; then
    echo -e "${RED}ERROR: Cannot reach $BASE_URL — start the server first.${RESET}"
    exit 1
  fi
fi
echo -e "  ${GREEN}Server reachable${RESET}"

# ==============================================================================
# WORKFLOW JSON  — new flat edge condition format: {"field","op","value"}
# ==============================================================================
WORKFLOW_JSON='{
  "tenant_id": 1,
  "name": "MBBank: Full KYC Sequential",
  "version": 1,
  "status": "DRAFT",
  "timeout": 3600,
  "definition": {
    "start_node": "ocr_front",
    "nodes": [
      {
        "id": "ocr_front", "type": "OCR_EXTRACT",
        "inputMap": {"image_base64": {"type": "SESSION_INPUT", "key": "image_base64"}}
      },
      {
        "id": "liveness_check", "type": "LIVENESS_CHECK",
        "inputMap": {"selfie_base64": {"type": "SESSION_INPUT", "key": "selfie_base64"}}
      },
      {
        "id": "dedup_check", "type": "DOCUMENT_DEDUP",
        "inputMap": {
          "id_number": {"type": "NODE_OUTPUT", "nodeId": "ocr_front", "field": "id_number"},
          "full_name":  {"type": "NODE_OUTPUT", "nodeId": "ocr_front", "field": "full_name"}
        }
      },
      {
        "id": "aml_screening", "type": "AML_SCREENING", "executionMode": "ASYNC",
        "inputMap": {
          "id_number": {"type": "NODE_OUTPUT", "nodeId": "ocr_front", "field": "id_number"},
          "full_name":  {"type": "NODE_OUTPUT", "nodeId": "ocr_front", "field": "full_name"}
        }
      },
      {
        "id": "risk_decision", "type": "DECISION",
        "config": {
          "branches": [
            {"name": "APPROVE", "conditions": [{"field": "score", "op": "GTE", "value": 0.8}]},
            {"name": "REVIEW",  "conditions": [{"field": "score", "op": "LT",  "value": 0.8}]},
            {"name": "DECLINE", "default": true}
          ]
        },
        "inputMap": {
          "liveness_result": {"type": "NODE_OUTPUT", "nodeId": "liveness_check", "field": "liveness_result"},
          "dedup_verdict":   {"type": "NODE_OUTPUT", "nodeId": "dedup_check",    "field": "dedup_verdict"},
          "score":           {"type": "NODE_OUTPUT", "nodeId": "liveness_check", "field": "score"}
        }
      },
      {"id": "end_approved", "type": "END", "config": {"outcome": "APPROVED"}},
      {"id": "end_review",   "type": "END", "config": {"outcome": "REVIEW"}},
      {"id": "end_declined", "type": "END", "config": {"outcome": "DECLINED"}}
    ],
    "edges": [
      {"from": "ocr_front",     "to": "liveness_check"},
      {"from": "liveness_check","to": "dedup_check",   "condition": {"field": "liveness_result", "op": "EQ", "value": "LIVE"}},
      {"from": "liveness_check","to": "end_declined",  "condition": {"field": "liveness_result", "op": "EQ", "value": "NOT_LIVE"}},
      {"from": "dedup_check",   "to": "aml_screening", "condition": {"field": "dedup_verdict",   "op": "EQ", "value": "CLEAR"}},
      {"from": "dedup_check",   "to": "end_declined",  "condition": {"field": "dedup_verdict",   "op": "EQ", "value": "DUPLICATE"}},
      {"from": "aml_screening", "to": "end_declined",  "condition": {"field": "aml_verdict",     "op": "EQ", "value": "HIT"}},
      {"from": "aml_screening", "to": "risk_decision", "condition": {"field": "aml_verdict",     "op": "EQ", "value": "CLEAR"}},
      {"from": "risk_decision", "to": "end_approved",  "condition": {"field": "branch",          "op": "EQ", "value": "APPROVE"}},
      {"from": "risk_decision", "to": "end_review",    "condition": {"field": "branch",          "op": "EQ", "value": "REVIEW"}},
      {"from": "risk_decision", "to": "end_declined"}
    ]
  }
}'

# ==============================================================================
# SETUP — Create workflow
# ==============================================================================
section "SETUP — Register MBBank Full KYC Workflow"

RESP=$(post "$BASE_URL/api/v1/workflows" "$WORKFLOW_JSON")
assert_http "SETUP: create workflow" "201"

WORKFLOW_UID=$(echo "$RESP" | jq -r '.data.uid // empty')
assert_ne "SETUP: uid captured" "$WORKFLOW_UID"
echo -e "  ${BOLD}workflowUid = $WORKFLOW_UID${RESET}"

if [[ -z "$WORKFLOW_UID" ]]; then
  echo -e "${RED}SETUP failed — cannot continue.${RESET}"
  exit 1
fi

# ==============================================================================
# TC-001: Full Sequential KYC + Input Chaining → APPROVED
# ==============================================================================
section "TC-001 · Full Sequential KYC + Input Chaining → APPROVED"

subsection "Step 1/3 · Create session + image_base64 → ocr_front auto-executes"
RESP=$(post "$BASE_URL/api/v1/sessions" "{
  \"tenant_id\": 1,
  \"workflow_uid\": \"$WORKFLOW_UID\",
  \"user_ref\": \"customer_tc001\",
  \"channel\": \"WEB\",
  \"input_data\": {\"image_base64\": \"data:image/jpeg;base64,/9j/mock_front_id_tc001\"}
}")
assert_http "TC-001 create session" "201"
assert_eq   "TC-001 status IN_PROGRESS"    "$(echo "$RESP" | jq -r '.data.status')"          "IN_PROGRESS"
assert_eq   "TC-001 paused at liveness"    "$(echo "$RESP" | jq -r '.data.current_node_id')" "liveness_check"

SESSION_ID=$(echo "$RESP" | jq -r '.data.session_id')

subsection "Step 2/3 · Submit selfie → full auto-advance chain to end_approved"
RESP=$(post "$BASE_URL/api/v1/sessions/$SESSION_ID/steps" '{
  "input_data": {"selfie_base64": "data:image/jpeg;base64,/9j/mock_selfie_tc001"}
}')
assert_http "TC-001 submit step"   "200"
assert_eq   "TC-001 COMPLETED"             "$(echo "$RESP" | jq -r '.data.status')"          "COMPLETED"
assert_eq   "TC-001 ended at end_approved" "$(echo "$RESP" | jq -r '.data.current_node_id')" "end_approved"
assert_ne   "TC-001 completed_at set"      "$(echo "$RESP" | jq -r '.data.completed_at')"

subsection "Step 3/3 · GET session → verify 5 steps + NODE_OUTPUT input chaining"
RESP=$(get "$BASE_URL/api/v1/sessions/$SESSION_ID")
STEPS=$(echo "$RESP" | jq '.data.steps')
assert_len "TC-001 5 steps total"         "$(echo "$STEPS" | jq 'length')"            5
assert_eq  "TC-001 step[0]=ocr_front"     "$(echo "$STEPS" | jq -r '.[0].node_id')"   "ocr_front"
assert_eq  "TC-001 step[1]=liveness"      "$(echo "$STEPS" | jq -r '.[1].node_id')"   "liveness_check"
assert_eq  "TC-001 step[2]=dedup_check"   "$(echo "$STEPS" | jq -r '.[2].node_id')"   "dedup_check"
assert_eq  "TC-001 step[3]=aml_screening" "$(echo "$STEPS" | jq -r '.[3].node_id')"   "aml_screening"
assert_eq  "TC-001 step[4]=risk_decision" "$(echo "$STEPS" | jq -r '.[4].node_id')"   "risk_decision"

OCR_ID_NUMBER=$(echo  "$STEPS" | jq -r '.[0].output' | jq -r '.id_number')
DEDUP_ID_NUMBER=$(echo "$STEPS" | jq -r '.[2].input_snapshot' | jq -r '.id_number')
assert_eq  "TC-001 dedup.input.id_number == ocr.output.id_number (NODE_OUTPUT chain)" \
           "$DEDUP_ID_NUMBER" "$OCR_ID_NUMBER"

LIVENESS_SCORE=$(echo "$STEPS" | jq -r '.[1].output' | jq -r '.score')
DECISION_SCORE=$(echo "$STEPS" | jq -r '.[4].input_snapshot' | jq -r '.score')
assert_eq  "TC-001 decision.input.score == liveness.output.score (NODE_OUTPUT chain)" \
           "$DECISION_SCORE" "$LIVENESS_SCORE"

# ==============================================================================
# TC-002: Condition Branch — NOT_LIVE → DECLINED
# ==============================================================================
section "TC-002 · Condition Branch: NOT_LIVE → DECLINED"

subsection "Step 1/3 · Create session"
RESP=$(post "$BASE_URL/api/v1/sessions" "{
  \"tenant_id\": 1,
  \"workflow_uid\": \"$WORKFLOW_UID\",
  \"user_ref\": \"customer_tc002\",
  \"channel\": \"MOBILE\",
  \"input_data\": {\"image_base64\": \"data:image/jpeg;base64,/9j/mock_front_id_spoof\"}
}")
assert_http "TC-002 create session" "201"
assert_eq   "TC-002 paused at liveness" "$(echo "$RESP" | jq -r '.data.current_node_id')" "liveness_check"
SESSION_ID=$(echo "$RESP" | jq -r '.data.session_id')

subsection "Step 2/3 · Submit selfie + _mock_liveness_result=NOT_LIVE"
RESP=$(post "$BASE_URL/api/v1/sessions/$SESSION_ID/steps" '{
  "input_data": {
    "selfie_base64": "data:image/jpeg;base64,/9j/mock_selfie_replay_attack",
    "_mock_liveness_result": "NOT_LIVE"
  }
}')
assert_http "TC-002 submit step"   "200"
assert_eq   "TC-002 COMPLETED"          "$(echo "$RESP" | jq -r '.data.status')"          "COMPLETED"
assert_eq   "TC-002 ended at declined"  "$(echo "$RESP" | jq -r '.data.current_node_id')" "end_declined"

subsection "Step 3/3 · Verify 2 steps (dedup/aml/decision skipped)"
RESP=$(get "$BASE_URL/api/v1/sessions/$SESSION_ID")
STEPS=$(echo "$RESP" | jq '.data.steps')
assert_len "TC-002 2 steps"              "$(echo "$STEPS" | jq 'length')"           2
assert_eq  "TC-002 step[0]=ocr_front"   "$(echo "$STEPS" | jq -r '.[0].node_id')"  "ocr_front"
assert_eq  "TC-002 step[1]=liveness"    "$(echo "$STEPS" | jq -r '.[1].node_id')"  "liveness_check"
LIVENESS_RESULT=$(echo "$STEPS" | jq -r '.[1].output' | jq -r '.liveness_result')
assert_eq  "TC-002 liveness=NOT_LIVE"   "$LIVENESS_RESULT"  "NOT_LIVE"
SPOOF_TYPE=$(echo "$STEPS" | jq -r '.[1].output' | jq -r '.spoof_type')
assert_eq  "TC-002 spoof_type=REPLAY_ATTACK" "$SPOOF_TYPE"  "REPLAY_ATTACK"

# ==============================================================================
# TC-003: ASYNC Node — aml_screening fires and auto-advances
# ==============================================================================
section "TC-003 · ASYNC Node: aml_screening fires & auto-advances"

subsection "Step 1/3 · Create session"
RESP=$(post "$BASE_URL/api/v1/sessions" "{
  \"tenant_id\": 1,
  \"workflow_uid\": \"$WORKFLOW_UID\",
  \"user_ref\": \"customer_tc003\",
  \"channel\": \"API\",
  \"input_data\": {\"image_base64\": \"data:image/jpeg;base64,/9j/mock_front_id_async\"}
}")
assert_http "TC-003 create session" "201"
SESSION_ID=$(echo "$RESP" | jq -r '.data.session_id')

subsection "Step 2/3 · Single /steps call → COMPLETED (ASYNC ran in same request)"
RESP=$(post "$BASE_URL/api/v1/sessions/$SESSION_ID/steps" '{
  "input_data": {"selfie_base64": "data:image/jpeg;base64,/9j/mock_selfie_async"}
}')
assert_http "TC-003 submit step" "200"
assert_eq   "TC-003 COMPLETED"   "$(echo "$RESP" | jq -r '.data.status')" "COMPLETED"

subsection "Step 3/3 · Inspect steps — aml_screening(ASYNC) present"
RESP=$(get "$BASE_URL/api/v1/sessions/$SESSION_ID")
STEPS=$(echo "$RESP" | jq '.data.steps')
assert_len "TC-003 5 steps total"            "$(echo "$STEPS" | jq 'length')"                     5
assert_eq  "TC-003 step[3]=aml_screening"    "$(echo "$STEPS" | jq -r '.[3].node_id')"            "aml_screening"
assert_eq  "TC-003 aml status=COMPLETED"     "$(echo "$STEPS" | jq -r '.[3].status')"             "COMPLETED"
assert_eq  "TC-003 step[4]=risk_decision"    "$(echo "$STEPS" | jq -r '.[4].node_id')"            "risk_decision"
AML_OUTPUT=$(echo "$STEPS" | jq -r '.[3].output' | jq -r '.aml_verdict')
assert_eq  "TC-003 aml_verdict=CLEAR"        "$AML_OUTPUT"  "CLEAR"

# ==============================================================================
# TC-004: AML HIT branch → DECLINED
# ==============================================================================
section "TC-004 · AML HIT Branch → DECLINED"

subsection "Step 1/2 · Create session"
RESP=$(post "$BASE_URL/api/v1/sessions" "{
  \"tenant_id\": 1,
  \"workflow_uid\": \"$WORKFLOW_UID\",
  \"user_ref\": \"customer_tc004\",
  \"channel\": \"WEB\",
  \"input_data\": {\"image_base64\": \"data:image/jpeg;base64,/9j/mock_front_id_aml\"}
}")
assert_http "TC-004 create session" "201"
SESSION_ID=$(echo "$RESP" | jq -r '.data.session_id')

subsection "Step 2/2 · Submit selfie + _mock_aml_verdict=HIT"
RESP=$(post "$BASE_URL/api/v1/sessions/$SESSION_ID/steps" '{
  "input_data": {
    "selfie_base64": "data:image/jpeg;base64,/9j/mock_selfie_aml",
    "_mock_aml_verdict": "HIT"
  }
}')
assert_http "TC-004 submit step" "200"
assert_eq   "TC-004 COMPLETED"          "$(echo "$RESP" | jq -r '.data.status')"          "COMPLETED"
assert_eq   "TC-004 ended at declined"  "$(echo "$RESP" | jq -r '.data.current_node_id')" "end_declined"

RESP=$(get "$BASE_URL/api/v1/sessions/$SESSION_ID")
STEPS=$(echo "$RESP" | jq '.data.steps')
assert_len "TC-004 4 steps (risk_decision skipped)" "$(echo "$STEPS" | jq 'length')" 4
assert_eq  "TC-004 last=aml_screening"              "$(echo "$STEPS" | jq -r '.[-1].node_id')" "aml_screening"
AML_OUT=$(echo "$STEPS" | jq -r '.[-1].output' | jq -r '.aml_verdict')
assert_eq  "TC-004 aml_verdict=HIT"                 "$AML_OUT" "HIT"

# ==============================================================================
# TC-005: Dedup DUPLICATE branch → DECLINED
# ==============================================================================
section "TC-005 · Dedup DUPLICATE Branch → DECLINED"

subsection "Step 1/2 · Create session"
RESP=$(post "$BASE_URL/api/v1/sessions" "{
  \"tenant_id\": 1,
  \"workflow_uid\": \"$WORKFLOW_UID\",
  \"user_ref\": \"customer_tc005\",
  \"channel\": \"WEB\",
  \"input_data\": {\"image_base64\": \"data:image/jpeg;base64,/9j/mock_front_id_dedup\"}
}")
assert_http "TC-005 create session" "201"
SESSION_ID=$(echo "$RESP" | jq -r '.data.session_id')

subsection "Step 2/2 · Submit selfie + _mock_dedup_verdict=DUPLICATE"
RESP=$(post "$BASE_URL/api/v1/sessions/$SESSION_ID/steps" '{
  "input_data": {
    "selfie_base64": "data:image/jpeg;base64,/9j/mock_selfie_dedup",
    "_mock_dedup_verdict": "DUPLICATE"
  }
}')
assert_http "TC-005 submit step" "200"
assert_eq   "TC-005 COMPLETED"         "$(echo "$RESP" | jq -r '.data.status')"          "COMPLETED"
assert_eq   "TC-005 ended at declined" "$(echo "$RESP" | jq -r '.data.current_node_id')" "end_declined"

RESP=$(get "$BASE_URL/api/v1/sessions/$SESSION_ID")
STEPS=$(echo "$RESP" | jq '.data.steps')
assert_len "TC-005 3 steps (aml/decision skipped)" "$(echo "$STEPS" | jq 'length')" 3
assert_eq  "TC-005 last=dedup_check"               "$(echo "$STEPS" | jq -r '.[-1].node_id')" "dedup_check"
DEDUP_OUT=$(echo "$STEPS" | jq -r '.[-1].output' | jq -r '.dedup_verdict')
assert_eq  "TC-005 dedup_verdict=DUPLICATE"        "$DEDUP_OUT" "DUPLICATE"

# ==============================================================================
# TC-006: Validation Errors
# ==============================================================================
section "TC-006 · Validation Errors"

subsection "TC-006a · Missing tenant_id → 400"
RESP=$(post "$BASE_URL/api/v1/sessions" "{
  \"workflow_uid\": \"$WORKFLOW_UID\",
  \"user_ref\": \"customer_err\",
  \"channel\": \"WEB\"
}")
assert_http "TC-006a HTTP 400" "400"
ERR_CODE=$(echo "$RESP" | jq '[.meta.errors[]? | select(.code == 400011)] | length')
assert_eq   "TC-006a error code 400011 present" "$ERR_CODE" "1"

subsection "TC-006b · Invalid channel enum → 400"
RESP=$(post "$BASE_URL/api/v1/sessions" "{
  \"tenant_id\": 1,
  \"workflow_uid\": \"$WORKFLOW_UID\",
  \"user_ref\": \"customer_err\",
  \"channel\": \"INVALID_CHANNEL\"
}")
assert_http "TC-006b HTTP 400" "400"

subsection "TC-006c · Workflow UID not found → 400"
RESP=$(post "$BASE_URL/api/v1/sessions" '{
  "tenant_id": 1,
  "workflow_uid": "00000000-0000-0000-0000-000000000000",
  "user_ref": "customer_err",
  "channel": "WEB"
}')
assert_http "TC-006c HTTP 400" "400"
assert_contains "TC-006c body contains 'not found'" "$RESP" "not found"

subsection "TC-006d · Missing input_data on /steps → 400"
RESP=$(post "$BASE_URL/api/v1/sessions/$SESSION_ID/steps" '{}')
assert_http "TC-006d HTTP 400" "400"

subsection "TC-006e · Cycle in DAG → 400"
RESP=$(post "$BASE_URL/api/v1/workflows" '{
  "tenant_id": 1,
  "name": "Cyclic Invalid",
  "version": 99,
  "definition": {
    "start_node": "nodeA",
    "nodes": [
      {"id": "nodeA", "type": "OCR_EXTRACT",    "inputMap": {"image_base64": {"type": "SESSION_INPUT", "key": "image_base64"}}},
      {"id": "nodeB", "type": "LIVENESS_CHECK", "inputMap": {"selfie_base64": {"type": "SESSION_INPUT", "key": "selfie_base64"}}},
      {"id": "end1",  "type": "END", "config": {"outcome": "APPROVED"}}
    ],
    "edges": [
      {"from": "nodeA", "to": "nodeB"},
      {"from": "nodeB", "to": "nodeA"},
      {"from": "nodeB", "to": "end1"}
    ]
  }
}')
assert_http     "TC-006e HTTP 400" "400"
assert_contains "TC-006e body contains 'Cycle'" "$RESP" "Cycle"

subsection "TC-006f · END node with outgoing edge → 400"
RESP=$(post "$BASE_URL/api/v1/workflows" '{
  "tenant_id": 1,
  "name": "Invalid END Edge",
  "version": 98,
  "definition": {
    "start_node": "nodeA",
    "nodes": [
      {"id": "nodeA", "type": "OCR_EXTRACT", "inputMap": {"image_base64": {"type": "SESSION_INPUT", "key": "image_base64"}}},
      {"id": "end1",  "type": "END", "config": {"outcome": "APPROVED"}},
      {"id": "end2",  "type": "END", "config": {"outcome": "DECLINED"}}
    ],
    "edges": [
      {"from": "nodeA", "to": "end1"},
      {"from": "end1",  "to": "end2"}
    ]
  }
}')
assert_http     "TC-006f HTTP 400" "400"
assert_contains "TC-006f END node outgoing edge" "$RESP" "END node"

subsection "TC-006g · Submit step on non-existent session → 400"
RESP=$(post "$BASE_URL/api/v1/sessions/00000000-0000-0000-0000-000000000000/steps" '{
  "input_data": {"selfie_base64": "mock"}
}')
assert_http     "TC-006g HTTP 400" "400"
assert_contains "TC-006g body contains 'not found'" "$RESP" "not found"

subsection "TC-006h · Dead-end node (non-END no outgoing edge) → 400"
RESP=$(post "$BASE_URL/api/v1/workflows" '{
  "tenant_id": 1,
  "name": "Dead-end Test",
  "version": 95,
  "definition": {
    "start_node": "nodeA",
    "nodes": [
      {"id": "nodeA", "type": "OCR_EXTRACT",    "inputMap": {"image_base64": {"type": "SESSION_INPUT", "key": "image_base64"}}},
      {"id": "nodeB", "type": "LIVENESS_CHECK", "inputMap": {"selfie_base64": {"type": "SESSION_INPUT", "key": "selfie_base64"}}},
      {"id": "end1",  "type": "END", "config": {"outcome": "APPROVED"}}
    ],
    "edges": [
      {"from": "nodeA", "to": "nodeB"},
      {"from": "nodeA", "to": "end1"}
    ]
  }
}')
assert_http     "TC-006h HTTP 400" "400"
assert_contains "TC-006h body contains 'Dead-end'" "$RESP" "Dead-end"

subsection "TC-006i · No reachable node (orphan node) → 400"
RESP=$(post "$BASE_URL/api/v1/workflows" '{
  "tenant_id": 1,
  "name": "Unreachable Node",
  "version": 94,
  "definition": {
    "start_node": "nodeA",
    "nodes": [
      {"id": "nodeA",   "type": "OCR_EXTRACT",    "inputMap": {"image_base64": {"type": "SESSION_INPUT", "key": "image_base64"}}},
      {"id": "nodeB",   "type": "LIVENESS_CHECK", "inputMap": {"selfie_base64": {"type": "SESSION_INPUT", "key": "selfie_base64"}}},
      {"id": "orphan",  "type": "DECISION",       "inputMap": {}},
      {"id": "end1",    "type": "END", "config": {"outcome": "APPROVED"}},
      {"id": "end2",    "type": "END", "config": {"outcome": "DECLINED"}}
    ],
    "edges": [
      {"from": "nodeA", "to": "nodeB"},
      {"from": "nodeB", "to": "end1"},
      {"from": "orphan", "to": "end2"}
    ]
  }
}')
assert_http     "TC-006i HTTP 400" "400"
assert_contains "TC-006i body contains 'not reachable'" "$RESP" "not reachable"

# ==============================================================================
# TC-007: PARALLEL_FORK / PARALLEL_JOIN
# Flow: ocr_front(bg) → fork → liveness_check(bg) + face_match(bg) → join → end_approved
# ==============================================================================
section "TC-007 · PARALLEL_FORK / PARALLEL_JOIN"

FORK_JOIN_WORKFLOW='{
  "tenant_id": 1,
  "name": "Fork-Join KYC Demo",
  "version": 2,
  "status": "DRAFT",
  "definition": {
    "start_node": "ocr_front",
    "nodes": [
      {
        "id": "ocr_front", "type": "OCR_EXTRACT",
        "inputMap": {"image_base64": {"type": "SESSION_INPUT", "key": "image_base64"}}
      },
      {"id": "fork_parallel", "type": "PARALLEL_FORK"},
      {
        "id": "liveness_check", "type": "LIVENESS_CHECK",
        "inputMap": {"selfie_base64": {"type": "SESSION_INPUT", "key": "selfie_base64"}}
      },
      {
        "id": "face_match", "type": "FACE_MATCH",
        "inputMap": {"selfie_base64": {"type": "SESSION_INPUT", "key": "selfie_base64"}}
      },
      {
        "id": "join_results", "type": "PARALLEL_JOIN",
        "config": {"waitFor": ["liveness_check", "face_match"]}
      },
      {"id": "end_approved", "type": "END", "config": {"outcome": "APPROVED"}}
    ],
    "edges": [
      {"from": "ocr_front",      "to": "fork_parallel"},
      {"from": "fork_parallel",  "to": "liveness_check"},
      {"from": "fork_parallel",  "to": "face_match"},
      {"from": "liveness_check", "to": "join_results"},
      {"from": "face_match",     "to": "join_results"},
      {"from": "join_results",   "to": "end_approved"}
    ]
  }
}'

subsection "TC-007 SETUP · Register PARALLEL_FORK/JOIN workflow"
RESP=$(post "$BASE_URL/api/v1/workflows" "$FORK_JOIN_WORKFLOW")
assert_http "TC-007 create workflow" "201"
FJ_UID=$(echo "$RESP" | jq -r '.data.uid // empty')
assert_ne   "TC-007 uid captured" "$FJ_UID"

subsection "TC-007a · Create session → ocr auto-runs, paused (waiting for selfie)"
RESP=$(post "$BASE_URL/api/v1/sessions" "{
  \"tenant_id\": 1,
  \"workflow_uid\": \"$FJ_UID\",
  \"user_ref\": \"customer_tc007\",
  \"channel\": \"WEB\",
  \"input_data\": {\"image_base64\": \"data:image/jpeg;base64,/9j/mock_front_id_tc007\"}
}")
assert_http "TC-007a create session"        "201"
assert_eq   "TC-007a IN_PROGRESS"           "$(echo "$RESP" | jq -r '.data.status')"          "IN_PROGRESS"
assert_eq   "TC-007a current=fork_parallel" "$(echo "$RESP" | jq -r '.data.current_node_id')" "fork_parallel"
FJ_SESSION=$(echo "$RESP" | jq -r '.data.session_id')

subsection "TC-007b · Submit selfie → both branches run → JOIN merges → COMPLETED"
RESP=$(post "$BASE_URL/api/v1/sessions/$FJ_SESSION/steps" '{
  "input_data": {"selfie_base64": "data:image/jpeg;base64,/9j/mock_selfie_tc007"}
}')
assert_http "TC-007b submit"               "200"
assert_eq   "TC-007b COMPLETED"            "$(echo "$RESP" | jq -r '.data.status')"          "COMPLETED"
assert_eq   "TC-007b ended at end_approved" "$(echo "$RESP" | jq -r '.data.current_node_id')" "end_approved"

subsection "TC-007c · Verify 4 steps: ocr + liveness + face_match + join"
RESP=$(get "$BASE_URL/api/v1/sessions/$FJ_SESSION")
STEPS=$(echo "$RESP" | jq '.data.steps')
assert_len "TC-007c 4 steps"                  "$(echo "$STEPS" | jq 'length')"                                             4
assert_eq  "TC-007c ocr_front ran"            "$(echo "$STEPS" | jq '[.[] | select(.node_id=="ocr_front")]     | length')" "1"
assert_eq  "TC-007c liveness_check ran"       "$(echo "$STEPS" | jq '[.[] | select(.node_id=="liveness_check")]| length')" "1"
assert_eq  "TC-007c face_match ran"           "$(echo "$STEPS" | jq '[.[] | select(.node_id=="face_match")]    | length')" "1"
assert_eq  "TC-007c join_results ran"         "$(echo "$STEPS" | jq '[.[] | select(.node_id=="join_results")]  | length')" "1"

JOIN_OUT=$(echo "$STEPS" | jq -r '[.[] | select(.node_id=="join_results")] | .[0].output')
assert_ne  "TC-007c join has liveness_result"  "$(echo "$JOIN_OUT" | jq -r '.liveness_result   // empty')"
assert_ne  "TC-007c join has face_match_result" "$(echo "$JOIN_OUT" | jq -r '.face_match_result // empty')"
assert_ne  "TC-007c join has score"            "$(echo "$JOIN_OUT" | jq -r '.score              // empty')"
assert_ne  "TC-007c join has similarity_score" "$(echo "$JOIN_OUT" | jq -r '.similarity_score   // empty')"

subsection "TC-007d · Validation: PARALLEL_FORK <2 branches → 400"
RESP=$(post "$BASE_URL/api/v1/workflows" '{
  "tenant_id": 1, "name": "Bad Fork", "version": 97,
  "definition": {
    "start_node": "fork1",
    "nodes": [
      {"id": "fork1",  "type": "PARALLEL_FORK"},
      {"id": "nodeA",  "type": "OCR_EXTRACT",    "inputMap": {"image_base64": {"type": "SESSION_INPUT", "key": "image_base64"}}},
      {"id": "join1",  "type": "PARALLEL_JOIN",  "config": {"waitFor": ["nodeA", "nodeA"]}},
      {"id": "end1",   "type": "END",             "config": {"outcome": "APPROVED"}}
    ],
    "edges": [
      {"from": "fork1", "to": "nodeA"},
      {"from": "nodeA", "to": "join1"},
      {"from": "join1", "to": "end1"}
    ]
  }
}')
assert_http "TC-007d PARALLEL_FORK <2 branches → 400" "400"

subsection "TC-007e · Validation: PARALLEL_JOIN missing config.waitFor → 400"
RESP=$(post "$BASE_URL/api/v1/workflows" '{
  "tenant_id": 1, "name": "Join No WaitFor", "version": 96,
  "definition": {
    "start_node": "fork1",
    "nodes": [
      {"id": "fork1",  "type": "PARALLEL_FORK"},
      {"id": "nodeA",  "type": "OCR_EXTRACT",    "inputMap": {"image_base64": {"type": "SESSION_INPUT", "key": "image_base64"}}},
      {"id": "nodeB",  "type": "LIVENESS_CHECK", "inputMap": {"selfie_base64": {"type": "SESSION_INPUT", "key": "selfie_base64"}}},
      {"id": "join1",  "type": "PARALLEL_JOIN"},
      {"id": "end1",   "type": "END",             "config": {"outcome": "APPROVED"}}
    ],
    "edges": [
      {"from": "fork1",  "to": "nodeA"},
      {"from": "fork1",  "to": "nodeB"},
      {"from": "nodeA",  "to": "join1"},
      {"from": "nodeB",  "to": "join1"},
      {"from": "join1",  "to": "end1"}
    ]
  }
}')
assert_http "TC-007e PARALLEL_JOIN missing waitFor → 400" "400"

subsection "TC-007f · Validation: PARALLEL_FORK conditional edges → 400"
RESP=$(post "$BASE_URL/api/v1/workflows" '{
  "tenant_id": 1, "name": "Fork Conditional Edges", "version": 93,
  "definition": {
    "start_node": "fork1",
    "nodes": [
      {"id": "fork1",  "type": "PARALLEL_FORK"},
      {"id": "nodeA",  "type": "OCR_EXTRACT",    "inputMap": {"image_base64": {"type": "SESSION_INPUT", "key": "image_base64"}}},
      {"id": "nodeB",  "type": "LIVENESS_CHECK", "inputMap": {"selfie_base64": {"type": "SESSION_INPUT", "key": "selfie_base64"}}},
      {"id": "join1",  "type": "PARALLEL_JOIN",  "config": {"waitFor": ["nodeA", "nodeB"]}},
      {"id": "end1",   "type": "END",             "config": {"outcome": "APPROVED"}}
    ],
    "edges": [
      {"from": "fork1",  "to": "nodeA", "condition": {"field": "x", "op": "EQ", "value": "1"}},
      {"from": "fork1",  "to": "nodeB"},
      {"from": "nodeA",  "to": "join1"},
      {"from": "nodeB",  "to": "join1"},
      {"from": "join1",  "to": "end1"}
    ]
  }
}')
assert_http "TC-007f PARALLEL_FORK conditional edges → 400" "400"

# ==============================================================================
# SUMMARY
# ==============================================================================
echo ""
echo -e "${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${RESET}"
echo -e "${BOLD}  Test Results${RESET}"
echo -e "${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${RESET}"
echo -e "  Total  : ${BOLD}$TOTAL${RESET}"
echo -e "  ${GREEN}Passed : $PASS${RESET}"
if [[ "$FAIL" -gt 0 ]]; then
  echo -e "  ${RED}Failed : $FAIL${RESET}"
else
  echo -e "  Failed : $FAIL"
fi
echo -e "${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${RESET}"

if [[ "$FAIL" -gt 0 ]]; then
  exit 1
fi
