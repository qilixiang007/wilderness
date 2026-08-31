# ============================================================
# 「宇宙是旷野」前端镜像：多阶段构建
#   1. node 构建 dist
#   2. nginx 托管静态资源 + 反代后端 API
# ============================================================

# ---------- 构建阶段 ----------
FROM node:20-alpine AS build
WORKDIR /app

# package-lock.json 已提交,用 npm ci 保证可复现
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# ---------- 运行阶段 ----------
FROM nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
