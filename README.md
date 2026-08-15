# 宇宙是旷野

宇宙是旷野是一个中英双语的天文科普网页项目。首版以展示型内容为主，围绕恒星、行星、卫星、星系、星云、彗星、小行星等天体分类展开，后续会继续加入搜索、收藏、详情页、AI 讲解和公开 API 接入。

## 技术栈

- 前端：Vue 3 + Vite
- 后端：Spring Boot 3
- Java：17
- 构建：Maven Wrapper

## 目录

- `frontend/`：Vue 3 前端
- `backend/`：Spring Boot 3 后端

## 启动方式

前端：

```bash
cd frontend
npm install
npm run dev
```

后端：

```bash
cd backend
./mvnw spring-boot:run
```

Windows 下可使用 `mvnw.cmd`。

## 现阶段目标

1. 完成双语首页、分类页和详情页基础结构。
2. 设计后端接口与数据模型。
3. 为后续 AI 讲解、搜索、收藏和公开 API 接入预留扩展点。
