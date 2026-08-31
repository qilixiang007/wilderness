# 「宇宙是旷野」wilderness — 7 天 RAG 改造计划

> 本文档是接手指南。记录了项目定位、7 天计划全貌、已完成/待办、技术选型定案、阻塞点与关键接口索引。
> 用于在另一个对话页面中直接继续开发。

## 一、项目定位与目标

- **项目**:「宇宙是旷野」(Wilderness of the Universe),中英双语天文科普网站。
- **仓库**:`github.com/qilixiang007/wilderness`,位于 `E:\vibe-coding\wilderness`。
- **目标**:在既有天文项目基础上,融入 **RAG / AI 问答 / 混合检索 / 联网增强检索 / 用户上传入库** 核心能力,做一个面试经得起深挖、真实可部署的 Java 全栈个人项目。
- **投入**:约一周,每天约 5 小时。
- **面试叙事**:本来是基于兴趣搭建的天文科普平台,由于工作中没有深入参与文件解析、切块、向量化过程,所以把 RAG 核心功能融入自己的项目,做一个 AI 问答与讲解的天空网站。
- **简历背景**:简历上有"多模态知识库平台"和"BankGPT 企业级大模型应用开发平台"两条,需用本项目补足真实落地。

## 二、技术栈

| 层 | 技术 |
|---|---|
| 前端 | Vue 3 + Vite + vue-router(SSE 用 EventSource,Markdown 渲染用 marked + dompurify) |
| 后端 | Spring Boot 3.4.8 + Java 17 |
| 存储 | MySQL(docker,端口 3307)、Elasticsearch 8.19.1(docker,端口 9200)、Redis |
| RAG 编排 | langchain4j 1.19.0(AiServices、切块、chat/embedding 模型) |
| 检索 | **自写混合检索(BM25 + 向量 kNN)**,使用 elasticsearch-java 8.19.20(面试亮点) |
| LLM | 阿里云百炼 DashScope(OpenAI 兼容端点),chat=`qwen-plus`,embedding=`text-embedding-v3`(1024 维) |
| 其他 | docker-compose、Prometheus/Grafana、Swagger、Apache POI + PDFBox(文件解析) |

## 三、7 天计划总览与进度

| 天 | 内容 | 任务# | 状态 |
|---|---|---|---|
| Day1 | 地基:ES 容器、依赖、AI/RAG 配置 | — | ✅ 完成 |
| Day1-2 | 28 篇中英双语语料库(约 132KB / 3.9 万字) | — | ✅ 完成 |
| Day2 | 入库管线:切块 → 向量化 → ES 索引 | #3 | ✅ 代码完成,**待 key 端到端验证** |
| Day3 | RAG 服务:混合检索 + AiServices + 问答/SSE 流式 | #4 | ✅ 完成 |
| Day4 | 前端:/ask 问答页 + 详情页 AI 讲解 | #5 | ✅ 完成 |
| Day5 | LangSmith 追踪 + 评测(自写 REST 客户端) | #6 | ⏳ **未做** |
| Day6-7 | 部署上线 + README 架构图 + 演示录屏 + 面试话术 | #7 | ⏳ **未做** |
| 附加 | 用户上传文件入库(Word/PDF/Excel) | #8 | ✅ 代码+单测完成,待 key 真实验证 |
| 附加 | 可开关的联网增强检索(Bing 搜索) | #9 | ✅ 代码+单测完成 |

**代码已全部提交至 git(提交 `0303e81`),当前工作区仅剩一处未提交改动:`frontend/src/styles.css`。**

## 四、已实现内容(代码层面)

### 后端(28 个 Java 类,`backend/src/main/java/com/wilderness/backend/`)
- **入库管线** `ai/KnowledgeIngestionService` + `ai/ElasticsearchIndexManager`:扫描 28 篇语料 → `DocumentSplitters.recursive(500,80)` 切块 → DashScope embedding → ES bulk 写入(文档 ID=`文件名#块序号`,幂等可重跑);启动时 `CommandLineRunner` 自动入库;索引含 `text`、`content_vector`(dense_vector 1024,cosine)、`slug/type/zh_name/en_name/file_name`(keyword)。
- **RAG 服务** `ai/HybridContentRetriever`(实现 langchain4j `ContentRetriever`):BM25 + kNN 双路召回 → min-max 归一化 → `keyword-weight`/`vector-weight` 加权融合 → `min-score` 过滤 → 取 top-k。`ai/AiServices` 编排问答 + AI 讲解。
- **接口**(`controller/`):
  - `POST /api/ai/chat` — 同步问答,返回 answer + sources 引文
  - `GET /api/ai/explain/{slug}` — AI 讲解
  - `GET /api/ai/chat/stream` — SSE 流式问答(打字机效果,先发 sources 再逐段 delta)
  - `POST /api/knowledge/upload` — 用户上传文件入库
  - 既有:`/api/health`、`/api/categories`、`/api/celestial-objects/...`、`/api/search`
- **文件解析** `ai/DocumentTextExtractor`:txt/md/pdf/docx/xls/xlsx 六种格式;`ai/KnowledgeUploadService` 上传入库(元数据含文件名/上传者/时间)。
- **联网检索** `ai/BingWebSearch` + `ai/WebSearchService`:可开关,开启后查官网,关闭只查本地库。

### 前端(`frontend/`)
- `/ask` 问答页(SSE 流式 + Markdown 渲染 + 引文来源展示 + 上传工具条)
- 详情页"AI 讲解"区块、导航与首页入口
- `frontend/src/api/index.js`:`chat` / `explain` / `uploadKnowledge` / `openChatStream`

### 测试
- `ai/DocumentTextExtractorTest` 6/6 通过
- `ai/BingWebSearchTest` 通过
- 无 key 冒烟测试通过:应用正常启动(health UP),AI 接口正确 404,现有接口 200

## 五、关键技术决策(面试深挖点,勿轻易推翻)

1. **不用 langchain4j-elasticsearch**(该模块只有 beta 版),检索层**自己用 ES Java 客户端写混合检索**——这是最大的面试亮点。
2. **API key 不硬编码**,用环境变量 `DASHSCOPE_API_KEY` 注入。
3. **无 key 时的 Bean 注入**:用 `@ConditionalOnExpression("!('${wilderness.ai.dashscope.api-key:}'.trim().isEmpty())")` 严格判断非空(不能只用 `@ConditionalOnProperty`,空串会被误判为已配置导致 500)。
4. **文档唯一 ID 用文件名**(不是 frontmatter slug),因为天体"月球"与分类"卫星"的 slug 都是 `moon`,已用 `category-moon.md` 区分。
5. **RAG 配置**(application.yml `wilderness.ai.rag`):chunk-size 500、chunk-overlap 80、top-k 6、keyword-weight 0.5、vector-weight 0.5、min-score 0.3。
6. **SSE**:`SseEmitter(0L)` + ExecutorService + ObjectMapper,先发 sources 事件再逐段 delta。
7. **langchain4j 1.19.0 关键 API 已用 javap 核实**:
   - `AiServices.builder(Class).chatModel(ChatModel).streamingChatModel(StreamingChatModel).build()`
   - `OpenAiChatModel` 只实现 `ChatModel`(**非流式**),流式需独立 `OpenAiStreamingChatModel`
   - `TokenStream.onPartialResponse / onCompleteResponse / onError / start()`
   - `DocumentSplitter.split(Document) → List<TextSegment>`;`DocumentSplitters.recursive(int,int)` 在 langchain4j.jar
   - `EmbeddingModel.embedAll(List<TextSegment>) → Response<List<Embedding>>`;`Embedding.vector() → float[]`
   - ES 客户端:接受 `Class`(如 `Map.class`)而非 `TypeReference`;`DenseVectorProperty.Builder.dims(int)` + `DenseVectorSimilarity.Cosine`(不是 `dimensions`)

## 六、当前阻塞点与下一步

- **唯一硬阻塞**:端到端验证(入库 → 检索 → 问答 → 上传 → 联网)需要 `DASHSCOPE_API_KEY`。
- 拿到 key 后的验证流程:
  1. 带 `DASHSCOPE_API_KEY` 启动后端 → 观察入库日志(切块数、向量维度 1024、ES 索引数据量)
  2. curl 验证 `/api/ai/chat`、`/api/ai/explain/{slug}`、SSE `/api/ai/chat/stream`
  3. 上传真实文件验证 `/api/knowledge/upload`
  4. 验证通过后标记 #3、#8 完成
- **待做任务**:
  - **#6 Day5 LangSmith**:官方无 Java SDK,需自写 LangSmith REST API 轻量追踪/评测客户端(面试亮点),补测试
  - **#7 Day6-7 部署上线**:docker-compose 全栈 + Nginx/HTTPS + README 架构图 + 演示录屏 + 面试话术
- 可选:提交未跟踪的 `frontend/src/styles.css` 改动。

## 七、启动方式

```bash
# 后端(项目根目录)
docker compose up -d elasticsearch   # 启动 ES(公共镜像,无需登录凭据)
cd backend && java -Dmaven.multiModuleProjectDirectory="$(pwd)" \
  -cp ".mvn/wrapper/maven-wrapper.jar" org.apache.maven.wrapper.MavenWrapperMain \
  -q -DskipTests compile            # 或使用 mvnw

# 前端
cd frontend && npm install && npm run dev   # localhost:5173

# 健康检查
http://localhost:8080/api/health
```

> 注:若 Docker 拉公共镜像遇到 `docker-credential-desktop` 报错,用临时 `DOCKER_CONFIG` 目录绕开,**不要动全局 docker 配置**。

## 八、编码与协作规范(必须遵守)

1. **所有输出使用中文**(思考、回答、代码注释、文档)。
2. **不猜测**:查阅资料/定方案/写代码用最稳妥、最高效、最省时的方案,省 token;核实不了就明确说"不确定"。
3. **奔简历/面试亮点**:每个组件要能讲清"为什么用、怎么实现"。
4. **写代码遵循 karpathy-guidelines**(项目内 `.claude/skills/karpathy-guidelines/SKILL.md`):
   - 想清楚再写码,不确定就问/说明
   - 简单优先,只做被要求的
   - 外科手术式修改,只动该动的
   - 目标驱动,可验证成功标准
