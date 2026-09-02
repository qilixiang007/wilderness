#!/usr/bin/env bash
# wilderness 端到端冒烟测试：对「正在运行的」后端做真实 HTTP 断言。
#
# 验证的三条主链路（对应最近三次功能完善）：
#   F1 个人知识库：注册/登录 → 上传 txt → 文件列表可见 → 删除
#   F2 对话历史：   AI 问答一次 → MySQL 落库 → /api/history 能看到 → 删除该记录
#   F3 自定义智能体：创建 → 列表可见 → 用其生成 → 删除（生成需 LLM，可用 -k 跳过）
#
# 前提：
#   1) docker compose up -d mysql redis elasticsearch   （脚本要求 ES 在线以完成上传切块）
#   2) 后端已启动：cd backend && set -a && . ../.env && set +a && ...spring-boot:run
#      （带 spring-boot-devtools 时改代码会自动重启，无需手动重启）
#   3) 跨平台：只需 curl + python。JSON 载荷一律用 ASCII（避免 Windows 下中文经管道转码损坏），
#      中文只出现在「从磁盘读取」的 txt 文件里；断言只 grep ASCII 模式串。
#
# 用法：
#   bash scripts/smoke.sh     # 全跑（AI 模块在线时自动执行问答/生成）
#   bash scripts/smoke.sh -k  # 跳过需要真实 LLM 的步骤
#
# 退出码：0 = 全绿；1 = 有断言失败。SKIP 不计失败。

set -u
BASE=${BASE:-http://localhost:8080}
SKIP_LLM=0
[ "${1:-}" = "-k" ] && SKIP_LLM=1

# 用 python 取「curl 与 Windows python 都能打开」的临时目录：
# Git Bash 的 /tmp 只是映射，原生 python 读不到；这里统一取系统临时目录并转正斜杠。
WTMP=$(python - <<'PY' 2>/dev/null
import tempfile
print(tempfile.gettempdir().replace('\\', '/'))
PY
)
TMP="${WTMP:-/tmp}/wilderness-smoke-$$"
mkdir -p "$TMP"
JAR="$TMP/cookies.txt"
BODY="$TMP/body.json"
EMAIL="smoke-$(date +%s)@wilderness.local"
PASS="smoke-pass-123"
UPLOAD_FILE="$TMP/smoke-upload.txt"

PASS_N=0; FAIL_N=0; SKIP_N=0

say()  { printf '%s\n' "$*"; }
pass() { PASS_N=$((PASS_N+1)); say "  PASS  $*"; }
fail() { FAIL_N=$((FAIL_N+1)); say "  FAIL  $*"; }
skip() { SKIP_N=$((SKIP_N+1)); say "  SKIP  $*"; }

# http <方法> <URL> [curl 附加参数...]：存 body 到 $BODY，echo HTTP 状态码。
# Content-Type 由调用方按需传入（JSON 用 -H 'Content-Type: application/json'，
# 上传用 -F，curl 会自动带 multipart boundary，切勿再压 JSON header）。
http() {
	local method=$1 url=$2; shift 2
	curl -s -o "$BODY" -w '%{http_code}' -X "$method" -b "$JAR" -c "$JAR" "$url" "$@" 2>/dev/null
}

# 失败时打印后端 message（仅 ASCII/数字安全，中文由 python 处理不做 stdout 中文）
msg() { python -c "import json;d=json.load(open(r'$BODY',encoding='utf-8'));print(d.get('message') or d.get('error') or '')" 2>/dev/null; }

echo "== smoke start: $BASE  (LLM steps: $([ $SKIP_LLM = 1 ] && echo skip || echo run)) =="
echo "test account: $EMAIL"

# ---------- 0) health ----------
code=$(http GET "$BASE/api/health")
if [ "$code" = "200" ]; then pass "health /api/health -> 200"
else fail "health /api/health -> $code (backend down? start it first)"; fi

# ---------- 1) register (session cookie set on success) ----------
code=$(http POST "$BASE/api/auth/register" -H 'Content-Type: application/json' \
	-d "{\"email\":\"$EMAIL\",\"password\":\"$PASS\",\"code\":null}")
if [ "$code" = "200" ] && grep -q '"success":true' "$BODY"; then pass "register -> 200 (session set)"
else fail "register -> $code ($(msg))"; fi

# ---------- 2) F1 knowledge: upload -> list -> delete ----------
printf 'Jupiter is the largest planet in the solar system, radius about 69911 km.\n\nGas giants are mostly hydrogen and helium.\n' > "$UPLOAD_FILE"
code=$(http POST "$BASE/api/knowledge/upload" -F "file=@$UPLOAD_FILE")
doc_id=""
if [ "$code" = "200" ] && grep -q '"success":true' "$BODY"; then
	doc_id=$(python -c "import json;d=json.load(open(r'$BODY',encoding='utf-8'));dd=d.get('data') or {};print(dd.get('id') or dd.get('fileId') or '')" 2>/dev/null)
	pass "upload smoke-upload.txt -> 200${doc_id:+ (id=$doc_id)}"
else
	fail "upload knowledge -> $code ($(msg)) — is ES up? file <= 10MB?"
fi

code=$(http GET "$BASE/api/knowledge/files")
if [ "$code" = "200" ] && grep -q 'smoke-upload.txt' "$BODY"; then pass "file list contains smoke-upload.txt"
else fail "file list -> $code (contains uploaded file?) ($(msg))"; fi

if [ -n "$doc_id" ]; then
	code=$(http DELETE "$BASE/api/knowledge/files/$doc_id")
	if [ "$code" = "200" ] && grep -q '"success":true' "$BODY"; then pass "delete uploaded file -> 200"
	else fail "delete knowledge file /files/$doc_id -> $code ($(msg))"; fi
fi

# ---------- 3) F2 conversation history: chat -> persisted -> history -> delete ----------
# AI 模块无 DASHSCOPE_API_KEY 时整体 404，探测后跳过（不算失败）
code=$(http POST "$BASE/api/ai/chat" -H 'Content-Type: application/json' \
	-d '{"question":"Which planet is the largest in the solar system?","webSearchEnabled":false}')
if [ "$SKIP_LLM" = "1" ]; then
	skip "AI chat (-k)"
elif [ "$code" = "404" ]; then
	skip "AI module disabled (no DASHSCOPE_API_KEY on backend)"
elif [ "$code" = "200" ] && grep -q '"success":true' "$BODY"; then
	pass "AI chat -> 200"
	code=$(http GET "$BASE/api/history?page=0&size=5")
	if [ "$code" = "200" ] && grep -q '"success":true' "$BODY"; then
		total=$(python -c "import json;d=json.load(open(r'$BODY',encoding='utf-8'));print(d.get('data',{}).get('totalElements',0))" 2>/dev/null)
		hid=$(python -c "import json;d=json.load(open(r'$BODY',encoding='utf-8'));it=d.get('data',{}).get('items') or [];print(it[0]['id'] if it else '')" 2>/dev/null)
		if [ "$total" -ge 1 ] 2>/dev/null; then pass "history persisted, totalElements=$total"
		else fail "chat ok but /api/history empty — persistence chain broken"; fi
		if [ -n "$hid" ]; then
			code=$(http DELETE "$BASE/api/history/$hid")
			[ "$code" = "200" ] && grep -q '"success":true' "$BODY" \
				&& pass "delete history #$hid -> 200" || fail "delete history #$hid -> $code ($(msg))"
		fi
	else
		fail "GET /api/history -> $code ($(msg))"
	fi
else
	fail "AI chat -> $code ($(msg))"
fi

# ---------- 4) F3 custom agent: create -> list -> generate(-k) -> delete ----------
code=$(http POST "$BASE/api/agents" -H 'Content-Type: application/json' \
	-d '{"name":"Smoke Agent","systemPrompt":"You favor purple nebulas and poetic wording.","knowledgeSearchEnabled":false,"imageGenEnabled":false}')
aid=""
if [ "$code" = "200" ] && grep -q '"success":true' "$BODY"; then
	aid=$(python -c "import json;d=json.load(open(r'$BODY',encoding='utf-8'));print(d.get('data',{}).get('id',''))" 2>/dev/null)
	pass "create agent -> 200${aid:+ (id=$aid)}"
elif [ "$code" = "404" ]; then
	fail "create agent -> 404 (AI module disabled; rerun without agents step or use -k)"
else
	fail "create agent -> $code ($(msg))"
fi

if [ -n "$aid" ]; then
	code=$(http GET "$BASE/api/agents")
	if [ "$code" = "200" ] && grep -q "\"id\":$aid" "$BODY"; then pass "agent list contains #$aid"
	else fail "agent list -> $code ($(msg))"; fi
	if [ "$SKIP_LLM" != "1" ]; then
		code=$(http POST "$BASE/api/agents/$aid/generate" -H 'Content-Type: application/json' \
			-d '{"description":"a pale purple nebula about 80 light years across"}')
		[ "$code" = "200" ] && grep -q '"success":true' "$BODY" \
			&& pass "generate celestial via agent -> 200" \
			|| fail "agent generate -> $code ($(msg))"
	fi
	code=$(http DELETE "$BASE/api/agents/$aid")
	[ "$code" = "200" ] && grep -q '"success":true' "$BODY" && pass "delete agent -> 200" \
		|| fail "delete agent /api/agents/$aid -> $code ($(msg))"
fi

# ---------- 5) best-effort cleanup of the throwaway account ----------
if command -v docker >/dev/null 2>&1; then
	docker exec wilderness-mysql mysql -uwilderness -pwilderness wilderness \
		-e "DELETE c FROM conversation_message c JOIN app_user u ON c.user_id=u.id WHERE u.email='$EMAIL';
		    DELETE a FROM agent_config a JOIN app_user u ON a.user_id=u.id WHERE u.email='$EMAIL';
		    DELETE k FROM knowledge_document k JOIN app_user u ON k.user_id=u.id WHERE u.email='$EMAIL';
		    DELETE f FROM favorite f JOIN app_user u ON f.user_id=u.id WHERE u.email='$EMAIL';
		    DELETE FROM app_user WHERE email='$EMAIL';" >/dev/null 2>&1 \
		&& say "  cleanup smoke account (docker mysql)" \
		|| say "  note: docker mysql unreachable — smoke account $EMAIL left behind"
fi
rm -rf "$TMP"

echo "== result: PASS=$PASS_N FAIL=$FAIL_N SKIP=$SKIP_N =="
[ "$FAIL_N" -eq 0 ] || exit 1
exit 0
