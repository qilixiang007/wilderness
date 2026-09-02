# 「宇宙是旷野」上线部署手册（大陆轻量服务器 · 正式运营）

> 目标读者：拥有该仓库、要把它跑在公网上的你（大陆云服务器 + 域名 + ICP 备案）。
> 配套文件：`docker-compose.prod.yml`（生产编排）、`docker-compose.https.yml`（HTTPS 叠加层）、
> `deploy/env.prod.example`、`scripts/init-server.sh` / `backup.sh` / `restore.sh`。

---

## 0. 全局成本与时间参考（2026 行情，活动价浮动大）

| 项目 | 参考价 | 说明 |
|---|---|---|
| 大陆轻量云服务器 2核4G | 新用户 ¥300–600/年 | 阿里云/腾讯云「轻量应用服务器」，系统选 **Ubuntu 22.04/24.04** |
| 4核8G（升级余量） | 活动价 ¥600–1000/年 | 留监控 + ES 提到 1g 堆 + 编译不卡 |
| 域名 .com | ¥60–80/年 | 需实名（身份证+人脸，当天过） |
| ICP 备案 | 免费 | 周期 **1–3 周**，期间网站不能绑域名对外 |
| HTTPS 证书 | 免费 | 阿里云/腾讯云单域名证书，1 年/张，到期重下 |

**最低可行配置 = 2核4G**（Elasticsearch 默认 512m 堆，去掉监控也能跑，但编译后端镜像会慢）。
**推荐 = 4核8G**：正式运营、要监控、要省心，一步到位。

**唯一建议先别省的：内存。** 整套服务 = MySQL + Redis + ES + 后端 + Nginx，ES 是内存大户。

---

## 1. 三步路线图

```
第 1 步  买服务器 + 初始化启动（半天）          → IP 可访问，功能全通
第 2 步  买域名 + 实名 + ICP 备案（1–3 周排队）→ 备案期间 IP 照常演示
第 3 步  备案过 → 绑域名 + HTTPS（1 天）        → 正式域名上线
```

备案期间网站不能绑域名对外，所以**先别等备案，第 1 步做完就能用 IP 演示/自测**。

---

## 2. 第 1 步：买服务器并启动

### 2.1 购买
1. 阿里云/腾讯云控制台 → 轻量应用服务器 → 地域选离你近的大陆节点（如上海/广州）。
2. 镜像：**Ubuntu 22.04**（或 24.04）。规格至少 **2核4G**。
3. 绑定安全组/防火墙，**只放行**：
   - `80`（HTTP）、`443`（HTTPS）→ 全网 `0.0.0.0/0`
   - `22`（SSH）→ 只放你自己的 IP（别开全网，会被爆破）
   - 其余端口（8080/3306/9200/9090/3000…）**一律不开**。容器内网互连不需要它们，开了就是裸奔风险。

### 2.2 一键初始化（含 Docker + ES 内核参数 + 拉代码）
```bash
# SSH 登录后，以 root 运行（或 sudo 执行）：
curl -fsSL https://raw.githubusercontent.com/qilixiang007/wilderness/main/scripts/init-server.sh | bash
# 等价于 git clone 后执行：bash scripts/init-server.sh
```

脚本会：装 Docker + compose 插件 → 设 `vm.max_map_count=262144`（ES 硬性要求，重启不丢）→
`git clone` 到 `~/wilderness` → 从模板生成 `.env`（**不会覆盖已有 .env**）。

### 2.3 填环境变量
```bash
cd ~/wilderness
nano .env      # 至少改这四个，其余可选：
```
必填项（.env 里都有注释）：
- `MYSQL_ROOT_PASSWORD` / `MYSQL_PASSWORD`：**两个不同的强密码**（16 位以上随机串）
- `DASHSCOPE_API_KEY`：AI 问答唯一硬阻塞（百炼控制台 → API-KEY）
- `SMTP_HOST/PORT/USERNAME/PASSWORD/FROM`：注册/登录验证码邮件（QQ/163 邮箱开 SMTP 拿授权码当密码）
- `GRAFANA_ADMIN_PASSWORD`：监控面板登录（不会暴露公网，仍建议改）

### 2.4 启动
```bash
cd ~/wilderness
docker compose -f docker-compose.prod.yml up -d --build
# 首次构建：拉 Maven 依赖 + npm ci + 打镜像，约 5–15 分钟，属正常
```

> ⚠️ 服务器上**不要**直接 `docker compose up`（那是本地开发编排，会把 MySQL/后端/ES 全暴露到公网）。
> 生产必须带 `-f docker-compose.prod.yml`。

### 2.5 验证
```bash
curl http://127.0.0.1/api/health
# → {"success":true,"data":{"status":"UP","project":"宇宙是旷野"}...}

docker compose -f docker-compose.prod.yml ps   # 全 healthy
# 浏览器访问  http://<服务器公网IP>   （走的是 nginx :80）
```
至此整站（含 AI 问答、注册登录、收藏、知识库）在 IP 上全功能可用了。

---

## 3. 第 2 步：域名 + ICP 备案（大陆服务器绑域名必需）

> 只想要 IP 访问 → 这步可整个跳过，备案可选。

1. **买域名**：阿里云/腾讯云「域名注册」买 `.com`（~¥60–80/年）。**实名认证**当天过。
2. **ICP 备案**（在服务器同一家云厂商备案，最顺）：
   - 控制台 →「ICP 备案」→ 新增备案 → 填网站信息（**主办者 = 你本人**）。
   - 需要：身份证、**这台大陆服务器的实例**（备案必须挂一台你名下的服务器）、幕布/人脸核验、网站名称（如「宇宙是旷野」）与简介。
   - 周期 **1–3 周**。**备案审核期间不要让该域名 80/443 对外提供内容**：做法是域名暂不解析、或解析到服务器但 nginx 只返回空/占位页。IP 直访不受影响，可继续自测。
   - 管局会发短信核验，注意查收别超时。
3. **域名解析**（备案通过后）：加一条 **A 记录**，主机记录 `@`（和 `www`），记录值 = 服务器公网 IP。TTL 默认即可。
4. 浏览器访问 `http://你的域名` → 应能打开（此时走 HTTP）。

---

## 4. 第 3 步：开 HTTPS（域名备案通过后）

1. **申请免费证书**：阿里云/腾讯云 → SSL 证书 → 免费证书（单域名，1 年）→ 提交域名，签发后下载 **nginx 版**（得到 `.pem` 和 `.key`）。
2. **放置证书**（certs 目录已被 .gitignore，私钥不会进仓库）：
   ```bash
   cd ~/wilderness
   mkdir -p deploy/certs
   # 上传/重命名为固定名：
   #   deploy/certs/fullchain.pem   （证书链 .pem）
   #   deploy/certs/privkey.key     （私钥 .key）  → 名字不一致就改 deploy/nginx-https.conf 两行
   ```
3. **切 HTTPS**：
   ```bash
   sed -i 's/^AUTH_COOKIE_SECURE=false/AUTH_COOKIE_SECURE=true/' .env   # 登录 Cookie 走 Secure
   docker compose -f docker-compose.prod.yml -f docker-compose.https.yml up -d
   ```
4. **验证**：
   - 浏览器 `https://你的域名` → 自动从 80 跳到 443，地址栏有锁。
   - 完整走一遍**注册/登录 → AI 问答（观察流式逐字输出）→ 收藏**，确认 Cookie Secure 生效。
5. **到期续签**：免费证书 1 年有效，到期前 30 天控制台可重新申请，重复第 1–3 步即可（也可之后改成自动续期的 ACME 方案）。

---

## 5. 日常运维

### 5.1 看日志
```bash
cd ~/wilderness
docker compose -f docker-compose.prod.yml logs -f --tail=200 backend   # 后端
docker compose -f docker-compose.prod.yml logs -f frontend             # nginx
docker compose -f docker-compose.prod.yml logs -f mysql redis elasticsearch
```

### 5.2 发新版（升级）
```bash
cd ~/wilderness
git pull origin main
docker compose -f docker-compose.prod.yml up -d --build    # 只重编有变化的镜像
```

### 5.3 备份（务必配置，两类数据都要）
```bash
# 手动跑一次验证：
bash scripts/backup.sh
# 输出在 /var/backups/wilderness/{mysql/*.sql, ES 快照}
```
MySQL 里是**账号/收藏/文件元数据**，ES 里是**对话历史 + 知识库分析索引**，两者都备份才算完整。
建议 crontab（避开整点）：
```
17 3 * * * cd /root/wilderness && bash scripts/backup.sh >> /var/log/wilderness-backup.log 2>&1
```
**再加一道云快照**：控制台给服务器磁盘做定期快照（最便宜的兜底，防整机故障）。

### 5.4 恢复演练（一个月做一次，别等出事了才学）
```bash
bash scripts/restore.sh mysql /var/backups/wilderness/mysql/wilderness-20260903-030000.sql
bash scripts/restore.sh es wild-20260903-030000
```
建议先在测试环境/副本上验证 restore.sh，再在真机上用。

### 5.5 监控（Prometheus + Grafana 已随 prod compose 启动）
- 它们只绑了 `127.0.0.1`，**不要**在安全组开 9090/3000。本机用 SSH 隧道访问：
  ```bash
  ssh -L 3000:127.0.0.1:3000 root@<服务器IP>
  # 浏览器打开 http://localhost:3000  → admin / <你在 .env 设的 GRAFANA_ADMIN_PASSWORD>
  # 数据源 Prometheus http://localhost:9090（在隧道里同样 127.0.0.1:9090 可达）
  ```
- 后端 `/actuator/prometheus` 指标已由 `monitoring/prometheus.yml` 抓取；面板可自建：JVM 内存、HTTP 请求、ES/MySQL 健康。

---

## 6. 常见问题排查表

| 症状 | 原因 & 解法 |
|---|---|
| 启动后 `elasticsearch` 反复重启 | `vm.max_map_count` 没设 → `sudo sysctl -w vm.max_map_count=262144` + 写进 `/etc/sysctl.conf` 后重启容器 |
| `/api/ai/*` 全部 404 | `.env` 没填 `DASHSCOPE_API_KEY` → 填后 `up -d` 重启后端 |
| 注册/登录收不到验证码 | SMTP 没配或授权码错 → 填 `.env` 后重启；`docker logs backend` 看 503 |
| `/ask` 提问后不逐字吐字、一次性整段出 | Nginx 缓冲了 SSE → 确认生产用的是 prod/https 配置，`proxy_buffering off` 必须在 |
| 登录后刷新就掉登录 | 开了 HTTPS 但 `AUTH_COOKIE_SECURE` 还是 false（或反之：HTTP 下误开 true）→ 对齐 `.env` 后重启 |
| 首次 build 很慢/超时 | Maven 拉依赖 + npm ci，正常 5–15 分钟；2G 内存机器可临时加 swap 再 build |
| 镜像 build 到一半 OOM | 构建机内存不足 → 本机 `docker compose build` 后 `docker save` 传到服务器 `docker load`，或加 swap |
| 磁盘满 | `docker system df` 看空间；旧镜像 `docker image prune`；备份默认 7 天轮转 |
| 域名打不开但 IP 能开 | DNS 未生效（等 10 分钟）/ 备案未通过被拦 / 安全组没放 80、443 |
| HTTPS 证书报错 | 文件名与 `deploy/nginx-https.conf` 不一致 → 改 conf 里的两行路径，`logs -f frontend` 看具体报错 |

---

## 7. 常用命令速查

```bash
# 全部服务状态 / 日志 / 重启
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs -f backend
docker compose -f docker-compose.prod.yml restart backend

# 手动跑一次健康检查
curl -s http://127.0.0.1/api/health

# 数据进容器手工查看（无需开放端口）
docker compose -f docker-compose.prod.yml exec mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" wilderness -e 'select 1'
```

## 8. 还没做、但正式运营前建议补的清单
- [ ] 服务器磁盘**云快照** + `backup.sh` cron 都配上，并**演练过一次恢复**
- [ ] 域名解析加好，HTTP/HTTPS 都能访问
- [ ] 真机完整回归一遍核心路径：注册 → 登录 → 首页/星表/详情 → AI 问答流式 → 收藏 → 知识库上传
- [ ] 把默认弱口令全换掉（MySQL root/应用账号、Grafana）
- [ ] （可选）ES 开启鉴权、后端限流、错误率告警（Grafana Alerting）
