#!/usr/bin/env bash
# ============================================================
# 「宇宙是旷野」生产服务器一键初始化（Ubuntu/Debian）
#
# 在全新云服务器上以 root 运行（或 sudo bash scripts/init-server.sh）。
# 幂等：可重复执行。做三件事：
#   1. 安装 docker + compose 插件（缺才装）
#   2. 持久化 vm.max_map_count=262144（Elasticsearch 硬性要求，重启不丢）
#   3. 克隆仓库，并从模板生成 .env（不会覆盖已有 .env）
#
# 跑完只需：编辑 .env 填真实值 -> 启动 compose（见文末提示）。
# ============================================================
set -euo pipefail

# ---------- 0. 目标目录（默认 ~/wilderness，可用第一个参数覆盖） ----------
APP_DIR="${1:-$HOME/wilderness}"

need_sudo=0
if [ "$(id -u)" -ne 0 ]; then
    if command -v sudo >/dev/null 2>&1; then
        need_sudo=1
    else
        echo "请以 root 运行，或使用 sudo。" >&2
        exit 1
    fi
fi
SUDO=""
[ "$need_sudo" = 1 ] && SUDO="sudo"

echo "==> 1/3 安装基础工具 + Docker（缺才装）"
export DEBIAN_FRONTEND=noninteractive
$SUDO apt-get update -y -qq
$SUDO apt-get install -y -qq curl ca-certificates git gnupg >/dev/null

if ! command -v docker >/dev/null 2>&1; then
    echo "    安装 Docker Engine + Compose 插件（官方 apt 源）"
    $SUDO install -m 0755 -d /etc/apt/keyrings
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
        | $SUDO gpg --dearmor --yes -o /etc/apt/keyrings/docker.gpg
    $SUDO chmod a+r /etc/apt/keyrings/docker.gpg
    # 同时兼容 Debian/Ubuntu 常见代号
    . /etc/os-release
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
https://download.docker.com/linux/${ID} ${VERSION_CODENAME} stable" \
        | $SUDO tee /etc/apt/sources.list.d/docker.list >/dev/null
    $SUDO apt-get update -y -qq
    $SUDO apt-get install -y -qq docker-ce docker-ce-cli containerd.io docker-compose-plugin >/dev/null
    $SUDO systemctl enable --now docker
    echo "    Docker 已安装"
else
    echo "    Docker 已存在，跳过"
fi
if ! docker compose version >/dev/null 2>&1; then
    echo "    compose 插件缺失：请安装 docker-compose-plugin 后重试" >&2
    exit 1
fi

echo "==> 2/3 设置 vm.max_map_count=262144（ES 要求）"
CURRENT=$(sysctl -n vm.max_map_count 2>/dev/null || echo 0)
if [ "$CURRENT" -lt 262144 ]; then
    $SUDO sysctl -w vm.max_map_count=262144 >/dev/null
    # 持久化：重启不丢
    if ! grep -q "vm.max_map_count" /etc/sysctl.conf; then
        echo "vm.max_map_count=262144" | $SUDO tee -a /etc/sysctl.conf >/dev/null
    fi
    echo "    已设为 262144"
else
    echo "    已是 $CURRENT，跳过"
fi

echo "==> 3/3 拉取代码 + 生成 .env"
if [ ! -d "$APP_DIR/.git" ]; then
    mkdir -p "$APP_DIR"
    git clone https://github.com/qilixiang007/wilderness.git "$APP_DIR"
    echo "    已克隆到 $APP_DIR"
else
    echo "    仓库已存在：$APP_DIR（跳过克隆；部署后如需更新可在此 git pull）"
fi
cd "$APP_DIR"

if [ ! -f .env ]; then
    cp deploy/env.prod.example .env
    echo "    已从模板生成 .env —— 现在必须编辑它！"
else
    echo "    .env 已存在，保留不动"
fi

echo
echo "============================================================"
echo " 下一步（在服务器上）："
echo "   1) nano $APP_DIR/.env     # 填 MYSQL_ROOT_PASSWORD / MYSQL_PASSWORD /"
echo "                              # DASHSCOPE_API_KEY / SMTP_* / GRAFANA_ADMIN_PASSWORD"
echo "   2) cd $APP_DIR"
echo "   3) docker compose -f docker-compose.prod.yml up -d --build"
echo "      （首次构建后端镜像需拉 Maven 依赖，约 5~15 分钟）"
echo "   4) 验证：curl http://127.0.0.1/api/health"
echo "            应返回 {\"success\":true,...\"status\":\"UP\"}"
echo "   安全组只放行 80/443（以及你的 IP 的 22）即可对外"
echo "============================================================"
