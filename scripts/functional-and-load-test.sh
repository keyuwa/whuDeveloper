#!/usr/bin/env bash
# WHU TOP Developer — functional smoke + list load probe
set -u
API="${API:-http://localhost:8081}"
PASS=0
FAIL=0
SKIP=0
REPORT=/tmp/whu-test/report.txt
mkdir -p /tmp/whu-test
: > "$REPORT"

log() { echo "$*" | tee -a "$REPORT"; }
ok() { PASS=$((PASS+1)); log "PASS  $*"; }
bad() { FAIL=$((FAIL+1)); log "FAIL  $*"; }
skip() { SKIP=$((SKIP+1)); log "SKIP  $*"; }

assert_http() {
  local name="$1" want="$2" got="$3" body="${4:-}"
  if [[ "$got" == "$want" ]]; then ok "$name (HTTP $got)"
  else bad "$name (want $want got $got) ${body:0:160}"; fi
}

json_get() {
  python3 -c 'import json,sys; d=json.load(sys.stdin); print(d.get(sys.argv[1],"") if not isinstance(d,list) else "")' "$1" 2>/dev/null || true
}

login() {
  local email="$1" pass="$2" out="$3"
  local code
  code=$(curl -s -o "$out" -w '%{http_code}' -X POST "$API/api/v1/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"$email\",\"password\":\"$pass\"}")
  echo "$code"
}

auth_hdr() { echo "Authorization: Bearer $1"; }

measure() {
  local label="$1" url="$2" token="${3:-}"
  local hdr=(-H 'Accept: application/json')
  [[ -n "$token" ]] && hdr+=(-H "Authorization: Bearer $token")
  local out=/tmp/whu-test/measure.json
  local meta
  meta=$(curl -s -o "$out" -w '%{http_code} %{time_total} %{size_download}' "${hdr[@]}" "$url")
  local code time size
  read -r code time size <<<"$meta"
  local count
  count=$(python3 -c 'import json,sys
try:
  d=json.load(open(sys.argv[1]))
  if isinstance(d, list): print(len(d))
  elif isinstance(d, dict) and isinstance(d.get("items"), list): print(len(d["items"]))
  else: print(1)
except Exception:
  print("err")' "$out")
  log "PERF  $label http=$code time=${time}s bytes=$size items=$count"
  echo "$code $time $size $count"
}

log "=== WHU functional + load test @ $API ==="
log "time: $(date '+%Y-%m-%d %H:%M:%S')"

# ---------- Auth ----------
log ""
log "## 1. Auth / boundary"
STU=/tmp/whu-test/stu.json
PUB=/tmp/whu-test/pub.json
ADM=/tmp/whu-test/adm.json
code=$(login 'wang.student@stu.edu.cn' 'student123' "$STU")
assert_http "student login" 200 "$code" "$(cat "$STU")"
code=$(login 'li.teacher@cs.whu.edu.cn' 'publisher123' "$PUB")
assert_http "publisher login" 200 "$code" "$(cat "$PUB")"
code=$(login 'admin@whu.edu.cn' 'admin123456' "$ADM")
assert_http "admin login" 200 "$code" "$(cat "$ADM")"
code=$(login 'wang.student@stu.edu.cn' 'wrong-password' /tmp/whu-test/bad.json)
assert_http "bad password rejected" 401 "$code" "$(cat /tmp/whu-test/bad.json)"
code=$(curl -s -o /tmp/whu-test/unauth.json -w '%{http_code}' "$API/api/v1/me")
assert_http "unauth /me" 401 "$code"

STU_TOKEN=$(python3 -c 'import json; print(json.load(open("'"$STU"'")).get("token",""))')
PUB_TOKEN=$(python3 -c 'import json; print(json.load(open("'"$PUB"'")).get("token",""))')
ADM_TOKEN=$(python3 -c 'import json; print(json.load(open("'"$ADM"'")).get("token",""))')

if [[ -z "$STU_TOKEN" || -z "$PUB_TOKEN" || -z "$ADM_TOKEN" ]]; then
  bad "missing tokens — abort"
  log "summary PASS=$PASS FAIL=$FAIL SKIP=$SKIP"
  exit 1
fi

code=$(curl -s -o /tmp/whu-test/me.json -w '%{http_code}' -H "$(auth_hdr "$STU_TOKEN")" "$API/api/v1/me")
role=$(python3 -c 'import json; print(json.load(open("/tmp/whu-test/me.json")).get("role",""))')
assert_http "student /me" 200 "$code"
[[ "$role" == "STUDENT" ]] && ok "student role=STUDENT" || bad "student role=$role"

# student cannot access publisher create
code=$(curl -s -o /tmp/whu-test/forbid.json -w '%{http_code}' -X POST \
  -H "$(auth_hdr "$STU_TOKEN")" -H 'Content-Type: application/json' \
  -d '{"title":"x"}' "$API/api/v1/publisher/tasks")
if [[ "$code" == "403" || "$code" == "401" || "$code" == "400" ]]; then ok "student blocked from publisher create (HTTP $code)"
else bad "student publisher create want 4xx got $code"; fi

# ---------- Student plaza / shortlist ----------
log ""
log "## 2. Student plaza / shortlist / chat"
code=$(curl -s -o /tmp/whu-test/tasks.json -w '%{http_code}' "$API/api/v1/tasks?page=0&size=50")
assert_http "list open tasks" 200 "$code"
TASK_COUNT=$(python3 -c 'import json; d=json.load(open("/tmp/whu-test/tasks.json")); print(d.get("total", len(d.get("items",[]))))')
log "INFO  open tasks total=$TASK_COUNT"
TASK_ID=$(python3 -c 'import json
raw=json.load(open("/tmp/whu-test/tasks.json"))
d=raw.get("items", raw if isinstance(raw, list) else [])
# prefer a task student has not applied
for t in d:
  if not t.get("myApplicationId") and t.get("status")=="OPEN":
    print(t["id"]); break
else:
  print(d[0]["id"] if d else "")')
[[ -n "$TASK_ID" ]] && ok "picked task id=$TASK_ID" || bad "no task to pick"

code=$(curl -s -o /tmp/whu-test/task.json -w '%{http_code}' -H "$(auth_hdr "$STU_TOKEN")" "$API/api/v1/tasks/$TASK_ID")
assert_http "get task detail" 200 "$code"

code=$(curl -s -o /tmp/whu-test/sl.json -w '%{http_code}' -X POST -H "$(auth_hdr "$STU_TOKEN")" "$API/api/v1/tasks/$TASK_ID/shortlist")
if [[ "$code" == "200" || "$code" == "201" || "$code" == "204" ]]; then ok "add shortlist (HTTP $code)"
elif [[ "$code" == "400" || "$code" == "409" ]]; then ok "shortlist already present (HTTP $code)"
else bad "add shortlist HTTP $code $(cat /tmp/whu-test/sl.json | head -c 120)"; fi

code=$(curl -s -o /tmp/whu-test/sls.json -w '%{http_code}' -H "$(auth_hdr "$STU_TOKEN")" "$API/api/v1/me/shortlists")
assert_http "list shortlists" 200 "$code"

code=$(curl -s -o /tmp/whu-test/openmsg.json -w '%{http_code}' -X POST -H "$(auth_hdr "$STU_TOKEN")" "$API/api/v1/tasks/$TASK_ID/messages/open")
assert_http "open message thread" 200 "$code" "$(cat /tmp/whu-test/openmsg.json)"

code=$(curl -s -o /tmp/whu-test/resume.json -w '%{http_code}' -H "$(auth_hdr "$STU_TOKEN")" "$API/api/v1/me/resume")
assert_http "get resume" 200 "$code"
READY=$(python3 -c 'import json; print(json.load(open("/tmp/whu-test/resume.json")).get("resumeReady", False))')
log "INFO  resumeReady=$READY"

# apply with text only
APPLIED_TASK=""
if [[ "$READY" == "True" || "$READY" == "true" ]]; then
  # find OPEN task without myApplicationId
  APPLY_ID=$(python3 -c 'import json
d=json.load(open("/tmp/whu-test/tasks.json"))
for t in d:
  # re-fetch not available; use detail flag after authenticated list
  pass
# use authenticated detail for each candidate
print("")')
  # refresh tasks with auth to see myApplicationId
  curl -s -o /tmp/whu-test/tasks-auth.json -H "$(auth_hdr "$STU_TOKEN")" "$API/api/v1/tasks?page=0&size=50" >/dev/null
  APPLY_ID=$(python3 -c 'import json
raw=json.load(open("/tmp/whu-test/tasks-auth.json"))
d=raw.get("items", raw if isinstance(raw, list) else [])
for t in d:
  if t.get("status")=="OPEN" and not t.get("myApplicationId") and not t.get("projectId"):
    print(t["id"]); break
')
  if [[ -n "$APPLY_ID" ]]; then
    code=$(curl -s -o /tmp/whu-test/apply.json -w '%{http_code}' -X POST \
      -H "$(auth_hdr "$STU_TOKEN")" \
      -F 'proposalText=功能测试申请：自我介绍与方案说明，用于冒烟验证。' \
      "$API/api/v1/tasks/$APPLY_ID/applications")
    if [[ "$code" == "200" || "$code" == "201" ]]; then
      ok "submit application task=$APPLY_ID"
      APPLIED_TASK=$APPLY_ID
      # duplicate apply should fail
      code2=$(curl -s -o /tmp/whu-test/apply2.json -w '%{http_code}' -X POST \
        -H "$(auth_hdr "$STU_TOKEN")" \
        -F 'proposalText=重复投递' \
        "$API/api/v1/tasks/$APPLY_ID/applications")
      if [[ "$code2" == "400" || "$code2" == "409" || "$code2" == "403" ]]; then ok "duplicate apply rejected (HTTP $code2)"
      else bad "duplicate apply want 4xx got $code2"; fi
    else
      bad "submit application HTTP $code $(cat /tmp/whu-test/apply.json | head -c 160)"
    fi
  else
    skip "no OPEN unapplied task for apply test"
  fi
else
  skip "resume not ready — skip apply"
fi

# empty proposal reject
EMPTY_ID=$(python3 -c 'import json,os
path="/tmp/whu-test/tasks-auth.json" if os.path.exists("/tmp/whu-test/tasks-auth.json") else "/tmp/whu-test/tasks.json"
raw=json.load(open(path))
d=raw.get("items", raw if isinstance(raw, list) else [])
for t in d:
  if t.get("status")=="OPEN" and not t.get("myApplicationId"):
    print(t["id"]); break
' 2>/dev/null)
if [[ -n "${EMPTY_ID:-}" ]]; then
  code=$(curl -s -o /tmp/whu-test/empty-apply.json -w '%{http_code}' -X POST \
    -H "$(auth_hdr "$STU_TOKEN")" -F 'proposalText=' \
    "$API/api/v1/tasks/$EMPTY_ID/applications")
  if [[ "$code" == "400" || "$code" == "422" ]]; then ok "empty proposal rejected (HTTP $code)"
  else skip "empty proposal got HTTP $code (may already applied)"; fi
fi

code=$(curl -s -o /tmp/whu-test/apps.json -w '%{http_code}' -H "$(auth_hdr "$STU_TOKEN")" "$API/api/v1/me/applications")
assert_http "my applications" 200 "$code"
code=$(curl -s -o /tmp/whu-test/projects.json -w '%{http_code}' -H "$(auth_hdr "$STU_TOKEN")" "$API/api/v1/projects")
assert_http "my projects" 200 "$code"
code=$(curl -s -o /tmp/whu-test/msgs.json -w '%{http_code}' -H "$(auth_hdr "$STU_TOKEN")" "$API/api/v1/messages")
assert_http "message inbox" 200 "$code"

# ---------- Publisher ----------
log ""
log "## 3. Publisher tasks / review"
code=$(curl -s -o /tmp/whu-test/ptasks.json -w '%{http_code}' -H "$(auth_hdr "$PUB_TOKEN")" "$API/api/v1/publisher/tasks")
assert_http "publisher task list" 200 "$code"

# create draft intern task
CREATE_BODY='{
  "title":"功能测试草稿任务-'$(date +%s)'",
  "roleType":"INTERN",
  "techField":"全栈应用",
  "difficulty":"L2",
  "background":"功能测试背景",
  "techStack":"Vue,Spring",
  "programmingLanguages":"TypeScript,Java",
  "supportLanguages":"中文",
  "outputRequirement":"可运行 demo",
  "techRequirement":"熟悉前后端",
  "durationWeeks":8,
  "estimatedHours":10,
  "repoUrl":"",
  "learningMaterials":"",
  "stipendText":"按周公示",
  "prdMd":"# 需求",
  "prdFileUrl":"/files/fake.pdf",
  "prdFileName":"fake.pdf",
  "quota":1
}'
code=$(curl -s -o /tmp/whu-test/create.json -w '%{http_code}' -X POST \
  -H "$(auth_hdr "$PUB_TOKEN")" -H 'Content-Type: application/json' \
  -d "$CREATE_BODY" "$API/api/v1/publisher/tasks?publish=false")
assert_http "create draft task" 200 "$code" "$(cat /tmp/whu-test/create.json)"
NEW_ID=$(python3 -c 'import json; print(json.load(open("/tmp/whu-test/create.json")).get("id",""))')

if [[ -n "$NEW_ID" ]]; then
  code=$(curl -s -o /tmp/whu-test/pub.json -w '%{http_code}' -X POST \
    -H "$(auth_hdr "$PUB_TOKEN")" "$API/api/v1/publisher/tasks/$NEW_ID/publish")
  assert_http "publish task $NEW_ID" 200 "$code"
  code=$(curl -s -o /tmp/whu-test/close.json -w '%{http_code}' -X POST \
    -H "$(auth_hdr "$PUB_TOKEN")" "$API/api/v1/publisher/tasks/$NEW_ID/close")
  assert_http "close task $NEW_ID" 200 "$code"
fi

# invalid create: missing title
code=$(curl -s -o /tmp/whu-test/badcreate.json -w '%{http_code}' -X POST \
  -H "$(auth_hdr "$PUB_TOKEN")" -H 'Content-Type: application/json' \
  -d '{"title":"","roleType":"INTERN","techField":"全栈应用","difficulty":"L2","background":"b","techStack":"t","programmingLanguages":"Java","supportLanguages":"中文","outputRequirement":"o","techRequirement":"t","durationWeeks":8,"estimatedHours":10,"stipendText":"s","quota":1}' \
  "$API/api/v1/publisher/tasks")
if [[ "$code" == "400" ]]; then ok "invalid task rejected"
else bad "invalid task want 400 got $code"; fi

code=$(curl -s -o /tmp/whu-test/inbox.json -w '%{http_code}' -H "$(auth_hdr "$PUB_TOKEN")" "$API/api/v1/publisher/applications")
assert_http "publisher inbox" 200 "$code"
PENDING=$(python3 -c 'import json; print(sum(1 for a in json.load(open("/tmp/whu-test/inbox.json")) if a.get("status")=="SUBMITTED"))')
log "INFO  pending applications=$PENDING"

# review if we have a submitted app
REVIEW_ID=$(python3 -c 'import json
d=json.load(open("/tmp/whu-test/inbox.json"))
for a in d:
  if a.get("status")=="SUBMITTED":
    print(a["id"]); break
')
if [[ -n "$REVIEW_ID" ]]; then
  # reject a test application if it is our just-created one; otherwise just check endpoint with note reject on a dedicated apply
  skip "have SUBMITTED id=$REVIEW_ID — leave for manual product flow (avoid breaking student project)"
else
  skip "no SUBMITTED application to review"
fi

code=$(curl -s -o /tmp/whu-test/pcount.json -w '%{http_code}' -H "$(auth_hdr "$PUB_TOKEN")" "$API/api/v1/publisher/applications/pending-count")
assert_http "pending-count" 200 "$code"

# ---------- Admin ----------
log ""
log "## 4. Admin"
code=$(curl -s -o /tmp/whu-test/ausers.json -w '%{http_code}' -H "$(auth_hdr "$ADM_TOKEN")" "$API/api/v1/admin/users")
assert_http "admin users" 200 "$code"
code=$(curl -s -o /tmp/whu-test/atasks.json -w '%{http_code}' -H "$(auth_hdr "$ADM_TOKEN")" "$API/api/v1/admin/tasks")
assert_http "admin tasks" 200 "$code"
code=$(curl -s -o /tmp/whu-test/aset.json -w '%{http_code}' -H "$(auth_hdr "$ADM_TOKEN")" "$API/api/v1/admin/settings")
assert_http "admin settings" 200 "$code"
code=$(curl -s -o /tmp/whu-test/dicts.json -w '%{http_code}' "$API/api/v1/dicts")
assert_http "public dicts" 200 "$code"
code=$(curl -s -o /tmp/whu-test/health.json -w '%{http_code}' "$API/api/health")
assert_http "health" 200 "$code"
code=$(curl -s -o /tmp/whu-test/lb.json -w '%{http_code}' "$API/api/v1/ability/leaderboard")
assert_http "ability leaderboard" 200 "$code"

# student blocked from admin
code=$(curl -s -o /tmp/whu-test/adminforbid.json -w '%{http_code}' -H "$(auth_hdr "$STU_TOKEN")" "$API/api/v1/admin/users")
if [[ "$code" == "403" || "$code" == "401" ]]; then ok "student blocked from admin (HTTP $code)"
else bad "student admin want 403 got $code"; fi

# ---------- Load seed + measure ----------
log ""
log "## 5. Load: seed OPEN tasks then measure lists"
BASE_COUNT=$(python3 -c 'import json; print(len(json.load(open("/tmp/whu-test/tasks.json"))))')
log "INFO  open tasks before seed=$BASE_COUNT"

SEED_N="${SEED_N:-200}"
log "INFO  seeding $SEED_N OPEN tasks (publisher)"
SEED_OK=0
SEED_FAIL=0
for i in $(seq 1 "$SEED_N"); do
  body=$(python3 -c 'import json,sys,time
i=int(sys.argv[1])
print(json.dumps({
  "title": f"压测任务-{i:04d}",
  "roleType": "INTERN",
  "techField": "全栈应用",
  "difficulty": "L3",
  "background": "压测背景 " + ("完成技术。" * 5),
  "techStack": "Vue,Spring Boot,MySQL",
  "programmingLanguages": "TypeScript,Java",
  "supportLanguages": "中文",
  "outputRequirement": "可运行交付物",
  "techRequirement": "熟悉前后端基础",
  "durationWeeks": 8,
  "estimatedHours": 10,
  "repoUrl": "",
  "learningMaterials": "",
  "stipendText": "600",
  "prdMd": "# 压测需求\n内容",
  "prdFileUrl": "/files/fake.pdf",
  "prdFileName": "fake.pdf",
  "quota": 1
}))' "$i")
  code=$(curl -s -o /tmp/whu-test/seed-one.json -w '%{http_code}' -X POST \
    -H "$(auth_hdr "$PUB_TOKEN")" -H 'Content-Type: application/json' \
    -d "$body" "$API/api/v1/publisher/tasks?publish=true")
  if [[ "$code" == "200" || "$code" == "201" ]]; then SEED_OK=$((SEED_OK+1))
  else SEED_FAIL=$((SEED_FAIL+1)); fi
  if (( i % 50 == 0 )); then log "INFO  seeded $i/$SEED_N ok=$SEED_OK fail=$SEED_FAIL"; fi
done
log "INFO  seed done ok=$SEED_OK fail=$SEED_FAIL"
[[ "$SEED_OK" -gt 0 ]] && ok "seed created $SEED_OK tasks" || bad "seed created 0 tasks"

log ""
log "## 6. Load: measure list endpoints (3 samples)"
for round in 1 2 3; do
  measure "GET /tasks page0 #$round" "$API/api/v1/tasks?page=0&size=20" "$STU_TOKEN" >/dev/null
done
measure "GET /tasks page0 size=50" "$API/api/v1/tasks?page=0&size=50" "$STU_TOKEN" >/dev/null
measure "GET /publisher/tasks" "$API/api/v1/publisher/tasks" "$PUB_TOKEN" >/dev/null
measure "GET /admin/tasks" "$API/api/v1/admin/tasks" "$ADM_TOKEN" >/dev/null
measure "GET /admin/users" "$API/api/v1/admin/users" "$ADM_TOKEN" >/dev/null
measure "GET /publisher/applications" "$API/api/v1/publisher/applications" "$PUB_TOKEN" >/dev/null
measure "GET /me/shortlists" "$API/api/v1/me/shortlists" "$STU_TOKEN" >/dev/null
measure "GET /messages" "$API/api/v1/messages" "$STU_TOKEN" >/dev/null
measure "GET /ability/leaderboard" "$API/api/v1/ability/leaderboard" "" >/dev/null

# thresholds (soft) — paged plaza should stay small
meta=$(measure "threshold /tasks page" "$API/api/v1/tasks?page=0&size=20" "$STU_TOKEN")
read -r code t1 s1 c1 <<<"$meta"
python3 - << PY
t=float("$t1"); s=int(float("$s1")); c=int("$c1") if str("$c1").isdigit() else -1
print(f"INFO  /tasks?page=0&size=20 items={c} time={t:.3f}s size={s}B ({s/1024:.1f}KB)")
open("$REPORT","a").write(f"INFO  /tasks page items={c} time={t:.3f}s size={s}B\\n")
if t > 1.0:
  print("WARN  paged /tasks slower than 1s")
  open("$REPORT","a").write("WARN  paged /tasks >1s\\n")
if s > 200_000:
  print("WARN  paged /tasks payload still large")
  open("$REPORT","a").write("WARN  paged payload large\\n")
PY

log ""
log "=== SUMMARY PASS=$PASS FAIL=$FAIL SKIP=$SKIP ==="
[[ "$FAIL" -eq 0 ]] && exit 0 || exit 1
