#!/usr/bin/env bash
# ============================================================
# 「宇宙是旷野」生产服务器一键初始化（Ubuntu/Debian）
#
# 在全新云服务器上运行（普通用户即可，脚本内部自行 sudo 提权）：
#     REGISTRY_MIRROR=https://xxxxxxxx.mirror.aliyuncs.com bash scripts/init-server.sh
# 不要在外层套 sudo —— 那会让 $HOME 变成 /root，代码克隆到 root 家目录。
#
# 幂等：可重复执行。做四件事：
#   1. 安装 docker + compose 插件（缺才装），并配置容器镜像加速器
#   2. 持久化 vm.max_map_count=262144（Elasticsearch 硬性要求，重启不丢）
#   3. 配置 swap（已有则跳过），避免内存峰值直接触发 OOM Killer
#   4. 克隆仓库，并从模板生成 .env（不会覆盖已有 .env）
#
# 跑完只需：编辑 .env 填真实值 -> 启动 compose（见文末提示）。
# ============================================================
set -euo pipefail

# ---------- 0. 可配置项（均可用环境变量覆盖） ----------
# 目标目录（默认 ~/wilderness，可用第一个参数覆盖）
APP_DIR="${1:-$HOME/wilderness}"

# Docker apt 源。默认阿里云镜像：download.docker.com 在国内实测不可达
# （curl 返回 000，2.3 秒快速失败）。海外部署可覆盖回官方源。
DOCKER_MIRROR="${DOCKER_MIRROR:-https://mirrors.aliyun.com/docker-ce}"

# 容器镜像加速器。账号专属地址，故不硬编码进仓库，必须由调用方传入。
# 获取：阿里云控制台 -> 容器镜像服务 ACR -> 镜像工具 -> 镜像加速器
# 不配置的话，后续 compose 拉取 mysql/redis/prometheus/grafana 会全部超时。
REGISTRY_MIRROR="${REGISTRY_MIRROR:-}"

# 源码仓库。GitHub 在国内是「间歇性可达」而非完全不通，故保留并加重试。
REPO_URL="${REPO_URL:-https://github.com/qilixiang007/wilderness.git}"

# swap 大小（机器上已有任何 swap 则跳过）
SWAP_SIZE="${SWAP_SIZE:-2G}"

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

echo "==> 1/4 安装基础工具 + Docker（缺才装）"
export DEBIAN_FRONTEND=noninteractive
$SUDO apt-get update -y -qq
$SUDO apt-get install -y -qq curl ca-certificates git gnupg >/dev/null

if ! command -v docker >/dev/null 2>&1; then
    echo "    安装 Docker Engine + Compose 插件（源：$DOCKER_MIRROR）"
    $SUDO install -m 0755 -d /etc/apt/keyrings
    curl -fsSL "${DOCKER_MIRROR}/linux/ubuntu/gpg" \
        | $SUDO gpg --dearmor --yes -o /etc/apt/keyrings/docker.gpg
    $SUDO chmod a+r /etc/apt/keyrings/docker.gpg
    # 同时兼容 Debian/Ubuntu 常见代号
    . /etc/os-release
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
${DOCKER_MIRROR}/linux/${ID} ${VERSION_CODENAME} stable" \
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

# ---------- 容器镜像加速器 ----------
# registry-1.docker.io 在国内不可达；不配这一步，Docker 装得再顺利，
# 后面 compose 拉镜像也会全线 i/o timeout。
if [ -f /etc/docker/daemon.json ]; then
    echo "    /etc/docker/daemon.json 已存在，保留不动"
elif [ -n "$REGISTRY_MIRROR" ]; then
    $SUDO mkdir -p /etc/docker
    $SUDO tee /etc/docker/daemon.json >/dev/null <<EOF
{
  "registry-mirrors": ["$REGISTRY_MIRROR"]
}
EOF
    $SUDO systemctl restart docker
    echo "    已配置镜像加速器：$REGISTRY_MIRROR"
else
    echo "    !! 未设置 REGISTRY_MIRROR" >&2
    echo "    !! 国内环境下拉取 mysql/redis/prometheus/grafana 镜像将会超时" >&2
    echo "    !! 获取地址：阿里云控制台 -> 容器镜像服务 ACR -> 镜像工具 -> 镜像加速器" >&2
    echo "    !! 然后重跑：REGISTRY_MIRROR=https://xxxx.mirror.aliyuncs.com bash $0" >&2
fi

echo "==> 2/4 设置 vm.max_map_count=262144（ES 要求）"
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

echo "==> 3/4 配置 swap（已有则跳过）"
# 作用是兜底而非扩容：内存打满时先换页，而不是让 OOM Killer 直接 SIGKILL
# 掉占用最大的进程（通常正是 backend 或 mysql，且不留任何日志）。
# swappiness 压到 10：swap 对 JVM 很不友好，GC 标记阶段会把换出的冷页
# 逐个 major page fault 拉回来，几十毫秒的 GC 能拖成几十秒。
if [ "$(swapon --show --noheadings 2>/dev/null | wc -l)" -gt 0 ]; then
    echo "    已存在 swap，跳过"
elif [ -e /swapfile ]; then
    echo "    /swapfile 已存在但未启用，跳过（请人工确认后 swapon）"
else
    $SUDO fallocate -l "$SWAP_SIZE" /swapfile
    # 600 不可省：swap 里是进程换出的内存页，可能含密码、API key 明文
    $SUDO chmod 600 /swapfile
    $SUDO mkswap /swapfile >/dev/null
    $SUDO swapon /swapfile
    grep -q '^/swapfile' /etc/fstab \
        || echo '/swapfile none swap sw 0 0' | $SUDO tee -a /etc/fstab >/dev/null
    grep -q 'vm.swappiness' /etc/sysctl.conf \
        || echo 'vm.swappiness=10' | $SUDO tee -a /etc/sysctl.conf >/dev/null
    $SUDO sysctl -w vm.swappiness=10 >/dev/null
    echo "    已配置 $SWAP_SIZE swap（swappiness=10）"
fi

echo "==> 4/4 拉取代码 + 生成 .env"
if [ ! -d "$APP_DIR/.git" ]; then
    # GitHub 在国内是间歇性可达：同一台机器可能这次超时、下次 7 MB/s。
    # 故重试 3 次而非直接换 Gitee；--depth 1 省掉约 24MB 历史。
    cloned=0
    for attempt in 1 2 3; do
        rm -rf "$APP_DIR"
        if timeout 180 git clone --depth 1 "$REPO_URL" "$APP_DIR"; then
            cloned=1
            break
        fi
        echo "    第 ${attempt}/3 次 clone 失败，10 秒后重试" >&2
        sleep 10
    done
    if [ "$cloned" != 1 ]; then
        echo "" >&2
        echo "    clone 三次均失败（GitHub 在国内间歇性不可达）。" >&2
        echo "    可手动把代码上传到 $APP_DIR 后重跑本脚本 —— 脚本幂等，" >&2
        echo "    已完成的 Docker 安装等步骤会自动跳过。" >&2
        exit 1
    fi
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
echo "   3) 启动。推荐先在本地/CI 构建好镜像推仓库，服务器只负责跑："
echo "        docker compose -f docker-compose.prod.yml pull"
echo "        docker compose -f docker-compose.prod.yml up -d"
echo ""
echo "      若确需在服务器上构建（建议 4GiB 以上且已配 swap），"
echo "      分开构建，避免 Maven 与 Vite 的内存峰值叠加："
echo "        docker compose -f docker-compose.prod.yml build backend"
echo "        docker compose -f docker-compose.prod.yml build frontend"
echo "        docker compose -f docker-compose.prod.yml up -d"
echo "      （首次构建需拉 Maven/npm 依赖，国内可能较慢）"
echo "   4) 验证：curl http://127.0.0.1/api/health"
echo "            应返回 {\"success\":true,...\"status\":\"UP\"}"
echo "   安全组只放行 80/443（以及你的 IP 的 22）即可对外"
echo "============================================================"
