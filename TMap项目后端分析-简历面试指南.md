# TMap 轨道交通地图平台 — 后端技术深度分析 & 面试指南

> 本文档帮助你将 TMap 项目写进简历，并为面试中可能遇到的技术问题提供详细回答思路。

---

## 一、项目概览

**项目名称：** TMap 轨道交通地图智能出行平台
**技术栈：** Spring Boot 3.2 + MyBatis-Plus 3.5 + Redis + WebSocket + LangChain4j 1.0 + MySQL 8 + 高德地图 API
**架构：** 多模块 Maven 项目，分为 common（公共层）、admin-server（管理端 8889）、user-server（用户端 8888）三个独立 Spring Boot 应用
**核心能力：** 地铁线路/站点管理、AI 智能对话路线规划、RAG 知识增强、实时 WebSocket 流式推送、票务订单系统

---

## 二、简历亮点提炼（按重要性排序）

### 亮点 1：基于 LangChain4j 的 AI Agent 对话式路线规划引擎（核心亮点）

**简历描述建议：**
> 基于 LangChain4j 构建 AI Agent 对话引擎，设计 5 节点确定性流水线（意图提取→地理编码→城市匹配→路径规划→回复生成），集成 RAG 知识检索增强，支持多 LLM 厂商热切换，通过 WebSocket 实现流式推送，解决 LLM 幻觉问题。

**技术细节：**

- **流水线架构（Pipeline 模式）：** 定义 `AgentNode` 接口，5 个节点顺序执行，任意节点可通过 `ctx.setShortCircuit(true)` 短路后续节点。代码位置：`agent/AgentNode.java`，`agent/RouteAgentEngine.java`
- **双引擎并存：**
  - `RouteAgentEngine`：自研 5 节点 Pipeline
  - `LangChain4jAgentEngine`：基于 LangChain4j 原生 @Tool 注解的确定性 7 步流水线
  - 两者共享同一个 WebSocket Handler 接口，可无缝切换
- **LLM 幻觉防护（RAG 白名单机制）：** 将数据库中真实站点名列表喂给 LLM，要求 LLM 严格从白名单中选取，而不是自由生成。代码位置：`agent/node/PathPlanNode.java:187-229`
  ```java
  // 把本城所有真实站点列表喂给 LLM，让它从中挑选最近的 1~3 个
  // 关键：LLM 的回答严格在白名单内，无须事后做 exactMatch 验证
  List<String> picked = llmLocationResolver.pickNearestFromStationList(slotName, cityName, stationNames);
  ```
- **多 LLM 厂商热切换：** 通过数据库配置支持 DeepSeek、Qwen、Kimi、Doubao、Zhipu、SiliconFlow、OpenAI 等 7+ 厂商，无需重启。代码位置：`config/LangChain4jConfig.java`
- **流式推送：** LLM 流式输出通过 `Consumer<Object> push` 回调实时推送到前端，支持 delta（文本块）、card（路线卡片）、chips（快捷回复）等多种消息类型
- **Prompt 注入防御：** 用户输入用 `[USER_INPUT]...[/USER_INPUT]` 标签包裹，`safeUserInput()` 方法转义注入标记。代码位置：`agent/LangChain4jAgentEngine.java:981-984`
- **异步执行与取消：** 使用 `CachedThreadPool` + `Future<?>` + `ConcurrentHashMap` 实现异步执行和中止，支持用户随时停止生成。MDC traceId 贯穿全链路

**面试问答：**

> **Q：为什么不用 Spring AI 而用 LangChain4j？**
> A：项目启动时 Spring AI 还不成熟，LangChain4j 1.0.0 对 Java 生态更友好，原生支持 @Tool 注解和 AiServices 抽象，可以声明式地定义 AI 助手接口。

> **Q：LLM 幻觉问题怎么解决的？**
> A：核心思路是"约束选择"而非"自由生成"。在 PathPlanNode 中，我把数据库中该城市所有真实站点名提取出来作为上下文喂给 LLM，要求它从中挑选最近的 1~3 个站。这样 LLM 的输出严格在白名单内，即使它"编"也只能从真实站点中选。之后再做防御性 exactMatch 验证兜底。

> **Q：Agent 流水线怎么设计的？**
> A：定义了 `AgentNode` 接口，每个节点接收 `AgentContext` 和 `Consumer<Object> push` 回调。5 个节点顺序执行：意图提取→地理编码→城市匹配→路径规划→回复生成。任意节点可以通过 `ctx.setShortCircuit(true)` 短路后续节点（比如用户只问天气，不需要路径规划）。这样既保证了确定性流程，又保留了灵活性。

---

### 亮点 2：N×N 多候选 BFS 最短路径规划算法

**简历描述建议：**
> 设计 N×N 多候选站点 BFS 最短路径算法，结合 Haversine 距离计算、真实邻站距离、阶梯票价模型，支持步行/骑行/出租车多模式接驳，通过 ConcurrentHashMap + 双重检查锁实现 5 分钟 TTL 站点缓存。

**技术细节：**

- **BFS 最短路径：** 从 `metro_station` 表的 `prev_station_ids` / `next_station_ids` JSON 字段构建邻接图，标准 BFS + parent 跟踪重建路径。代码位置：`service/PathPlanningService.java:218-249`
  ```java
  private List<Long> bfsShortestPath(Long startId, Long endId, Map<Long, List<Long>> graph) {
      Queue<Long> queue = new LinkedList<>();
      Map<Long, Long> parent = new HashMap<>();
      // ... 标准 BFS，parent 跟踪路径重建
  }
  ```
- **N×N 多候选优化：** 当用户说"我在天河公园附近"时，不只取最近的一个站，而是取 Top-N 候选站点。起点 N 个 × 终点 N 个 = N² 种组合，每种都跑 BFS，选总成本最低的方案。代码位置：`agent/node/PathPlanNode.java:290-309`
- **成本函数：** `总成本 = 去程时间 + 站点数 × 2.5分钟 + 返程时间`，接驳方式按距离自动选择：步行 <1.5km、骑行 <3km、出租车 >3km
- **距离计算：** 优先使用 `metro_station` 表中存储的真实邻站距离（`prev_station_distances` / `next_station_distances` JSON），缺失时回退到 kmPerStop（默认 1.8km/站）。代码位置：`PathPlanningService.java:251-267`
- **Haversine 公式：** 用于经纬度间球面距离计算，BigDecimal 和 double 两种精度版本。代码位置：`util/GeoUtil.java:27`
- **阶梯票价：** 按站点数分段计价（如 0-3 站 2 元、4-6 站 3 元...），配置存储在数据库中可动态调整。代码位置：`PathPlanningService.java:287-297`
- **站点缓存：** `ConcurrentHashMap` + `volatile` + 双重检查锁，5 分钟 TTL 自动刷新，避免重复查库。代码位置：`service/impl/TicketOrderServiceImpl.java:37-58`

**面试问答：**

> **Q：为什么用 BFS 而不是 Dijkstra？**
> A：地铁网络中每站之间的"距离"近似等权（都是相邻站），BFS 在等权图上天然求最短路径，时间复杂度 O(V+E) 比 Dijkstra 的 O((V+E)logV) 更优。如果要加权（比如考虑换乘惩罚），再升级为 Dijkstra。

> **Q：N×N 多候选的意义是什么？**
> A：用户说"我在天河公园附近"，GPS 定位可能不准，只取最近的一个站可能选错。我取 Top-3 候选站，3×3=9 种组合都跑 BFS，选总成本最优的。这样即使定位偏差 500 米也能找到正确路线。

> **Q：缓存怎么设计的？**
> A：用 `ConcurrentHashMap` 存站点列表和邻接图，`volatile` 修饰缓存引用和时间戳。读取时先检查时间戳，过期了再用 `synchronized` 双重检查锁刷新。5 分钟 TTL 平衡了数据新鲜度和查询性能。

---

### 亮点 3：WebSocket 实时通信 + 多层限流体系

**简历描述建议：**
> 基于 Spring WebSocket 实现 AI Agent 流式对话，设计 4 层限流体系（匿名日限、用户分钟限、IP 分钟限、并发连接限），支持匿名/登录双模，自定义关闭码 4029 语义化限流，AgentSessionRegistry 管理全量连接生命周期。

**技术细节：**

- **WebSocket 端点：** `/ws/agent`，Handler 继承 `AbstractWebSocketHandler`，支持 `chat`、`stop`、`ping`、`regenerate` 四种消息类型。代码位置：`websocket/AgentWebSocketHandler.java`
- **4 层限流：**
  1. **匿名用户日限制：** Redis key `agent:anon:daily:{anonToken}`，默认 10 条/天
  2. **登录用户分钟限制：** Redis key `agent:rl:user:{userId}`，默认 30 条/分钟
  3. **IP 分钟限制：** Redis key `agent:rl:ip:{ip}`，默认 60 条/分钟
  4. **并发连接限制：** `AgentSessionRegistry.countByOwner()`，默认 3 个/用户，超限返回自定义关闭码 4029
- **敏感词过滤：** 从数据库加载可配置的敏感词列表，在 LLM 调用前过滤
- **握手拦截器：** `AgentWebSocketInterceptor` 从 URL 参数提取 JWT / anonToken / sessionId，验证后存入 `ws.getAttributes()`
- **连接注册表：** `AgentSessionRegistry` 用三个 `ConcurrentHashMap` 维护 sessionId→wsSession、wsSession→owner、owner→wsSession 的双向映射，暴露 `currentOnline`、`uniqueOwners` 等指标

**面试问答：**

> **Q：WebSocket 连接数怎么限制的？**
> A：`AgentSessionRegistry` 维护 ownerKey（`u:{userId}` 或 `a:{anonToken}`）到 WebSocket 会话集合的映射。每次新连接建立时，先 `countByOwner()` 查已有连接数，超过阈值（默认 3）直接返回自定义关闭码 4029（借鉴 HTTP 429 语义）。

> **Q：匿名用户和登录用户怎么区分？**
> A：握手阶段从 URL 参数取 `token`（JWT）和 `anon`（匿名标识）。有 JWT 的走登录用户逻辑（Redis 校验 + 角色），否则走匿名逻辑（日限制 + 较短的会话 TTL）。两者共用同一个 Handler，只是限流策略不同。

---

### 亮点 4：多层安全防护体系

**简历描述建议：**
> 构建多层安全体系：JWT + Redis 双校验认证、AOP 注解式角色鉴权（4 级角色体系）、Redis 滑动窗口限流、登录失败锁定（10 次/30 分钟）、AES-256 密钥加密存储、Prompt 注入防御、敏感词过滤。

**技术细节：**

- **JWT + Redis 双校验：** Token 生成后存入 Redis，每次请求同时验证 JWT 签名和 Redis 存储值。代码位置：`interceptor/TokenInterceptor.java`、`utils/JwtUtil.java`
- **AOP 角色鉴权：** 自定义 `@RequireRole` 注解 + `RoleCheckAspect` 切面，`@Around` 通知检查 request 中的 roleCode。4 级角色：USER(1)、ADMIN(2)、SUPER_ADMIN(3)、ROOT_ADMIN(4)。代码位置：`annotation/RequireRole.java`、`aop/RoleCheckAspect.java`
- **滑动窗口限流：** `RateLimitInterceptor` 基于 Redis INCR 实现 IP 级滑动窗口，超限返回 HTTP 429 + JSON 响应。代码位置：`interceptor/RateLimitInterceptor.java:42-44`
  ```java
  String key = "rate_limit:" + ip;
  Long count = redisUtils.incrementKey(key, windowSeconds);
  if (count != null && count > maxRequests) { /* 429 */ }
  ```
- **登录失败锁定：** 10 次失败后 Redis 锁定 30 分钟，key 为 `login:fail:{username}`
- **AES-256 密钥加密：** 数据库中的 API Key（如高德、LLM）使用 AES-256 加密存储，`enc:` 前缀标识。SHA-256 密钥派生保证跨环境一致性。代码位置：`util/CryptoService.java:51-58`
- **Prompt 注入防御：** `safeUserInput()` 转义 `[USER_INPUT]` 标签，禁词列表过滤

**面试问答：**

> **Q：为什么不直接用 Spring Security？**
> A：项目权限模型相对简单（4 级角色 + 接口级控制），用自定义 `@RequireRole` + AOP 切面更轻量，避免引入 Spring Security 的整套 Filter Chain。TokenInterceptor + Redis 双校验已经覆盖了认证需求，AOP 覆盖了鉴权需求。

> **Q：Redis 滑动窗口怎么实现的？**
> A：本质上是固定窗口，用 Redis INCR 对 `rate_limit:{ip}` 计数，设置窗口过期时间。每次请求 INCR，超过阈值返回 429。窗口过期后 key 自动删除，计数归零。这是最简洁的实现方式，缺点是窗口边界有突发问题，但对于接口限流足够。

---

### 亮点 5：RAG 知识检索增强系统

**简历描述建议：**
> 实现 RAG（检索增强生成）系统，支持关键词检索和向量检索双模式，基于 LangChain4j EmbeddingModel 生成向量，余弦相似度匹配，embedding 存储在 MySQL JSON 字段中，支持 backfill 批量回填。

**技术细节：**

- **双模式检索：** 通过 `agent.rag.use_embedding` 配置切换
  - **关键词模式：** 用户输入分词 → title/keywords 包含计数 + priority 加权排序
  - **向量模式：** 用户输入 embed → 与候选 embedding 算余弦相似度 → 取 ≥ min_similarity 的 topK
- **候选范围控制：** 站点/线路精确匹配 ∪ 城市匹配 ∪ 全局通用，最多 200 条防止全表扫描
- **降级策略：** 向量检索为空时自动降级到关键词检索
- **Embedding 存储：** `station_knowledge.embedding` 字段存储 JSON 格式的 float 数组，通过 LangChain4j `EmbeddingModel` 生成
- **Backfill：** 支持批量回填缺失的 embedding，管理端 API 触发

代码位置：`service/RagService.java:50-80`

```java
public String retrieveForContext(String userMessage, Long cityId, List<Long> stationIds) {
    boolean useEmbedding = configService.getConfigInt("agent.rag.use_embedding", 0) == 1;
    List<Scored> top;
    if (useEmbedding) {
        top = retrieveByEmbedding(userMessage, candidates, topK);
        if (top.isEmpty()) {
            log.debug("Embedding 召回为空，降级到关键词");
            top = retrieveByKeyword(userMessage, candidates, topK);
        }
    } else {
        top = retrieveByKeyword(userMessage, candidates, topK);
    }
    // 注入 LLM system message
}
```

**面试问答：**

> **Q：为什么不用向量数据库（如 Milvus/Pinecone）？**
> A：项目规模是单城市级别（几千条知识），MySQL JSON 字段存储 embedding + 应用层余弦相似度计算完全够用。引入向量数据库会增加运维复杂度，对于这个量级是过度设计。如果未来扩展到百万级知识，再迁移到 Milvus 不迟。

> **Q：关键词模式和向量模式怎么选？**
> A：向量模式语义理解更好（"小蛮腰"能匹配到"广州塔"），但需要 EmbeddingModel 支持。关键词模式零依赖、速度快，适合精确查询。默认用关键词，配置开启向量后优先用向量，向量无结果自动降级到关键词。

---

### 亮点 6：多级缓存架构

**简历描述建议：**
> 设计 Redis + 内存双级缓存架构，业务数据 Redis 缓存（可配置 TTL）、系统配置 ConcurrentHashMap 内存缓存、站点数据 volatile + 双重检查锁缓存，支持缓存分类管理 API。

**技术细节：**

- **Redis 缓存层：** 国家/城市/线路/站点列表缓存，TTL 可通过数据库配置动态调整。CRUD 操作后主动失效（`deleteXxxCache()`）
- **内存缓存层：**
  - `SystemConfigServiceImpl`：`@PostConstruct` 加载全量配置到 `ConcurrentHashMap`，更新时刷新
  - `TicketOrderServiceImpl`：站点缓存 5 分钟 TTL，`volatile` + 双重检查锁
- **缓存管理 API：** `/manage/cache/status` 查看缓存状态，`/manage/cache/clear/{category}` 按分类清除
- **Redis 包装器：** `RedisTemplateWrapper` 统一日志、异常处理，封装 String/Counter/Hash/Set 操作

**面试问答：**

> **Q：为什么不直接用 @Cacheable 注解？**
> A：`@Cacheable` 适合简单的缓存场景，但项目需要：1）缓存失效时做额外操作（如清除关联缓存）；2）缓存分类管理（按 category 清除）；3）配置驱动的 TTL。手动能控制更精细。

---

### 亮点 7：高德地图集成 + 多级地理编码降级

**简历描述建议：**
> 集成高德地图地理编码/逆地理编码/POI 搜索/IP 定位 4 种 API，设计 4 级城市消歧策略（LLM 推断 > 浏览器定位 > 会话历史 > IP 定位），批量地理编码支持 200ms 限速 + 3 次重试策略。

**技术细节：**

- **AmapClient：** 封装 geocode、regeo、ipLocate、placeSearch 四个方法，使用 `URI.create()` 避免 RestTemplate 双重编码。代码位置：`service/AmapClient.java`
- **4 级城市消歧：** 代码位置：`agent/node/ResolveLocationNode.java:117-145`
  1. LLM 从对话中推断城市
  2. 浏览器 GPS 逆地理编码
  3. 会话历史 `lastCity`（持久化到 `chat_session.extras`）
  4. IP 定位
- **批量地理编码：** 200ms 间隔避免触发 API 限制，3 种重试策略（缩短地址、去掉门牌号、只用站名）
- **OSM Overpass 回退：** 本地数据库找不到站点时，调用 OpenStreetMap Overpass API 搜索附近地铁站

**面试问答：**

> **Q：城市消歧为什么需要 4 级？**
> A：用户说"我要去天河公园"，但没说在哪个城市。第 1 优先 LLM 从上下文推断（如之前聊过"广州塔"），第 2 用浏览器 GPS，第 3 用上一轮对话的城市（lastCity 持久化到 session），第 4 用 IP 定位兜底。层层降级保证在各种场景下都能正确识别城市。

---

### 亮点 8：系统配置中心化 + AES 加密

**简历描述建议：**
> 实现数据库驱动的系统配置中心，60+ 配置项覆盖全业务，支持 string/number/json/secret 四种类型，secret 类型自动 AES-256 加解密，CommandLineRunner 自动种子初始化，配置变更实时生效无需重启。

**技术细节：**

- **配置类型：** string、number、json、secret（自动加解密）
- **SystemConfigInitializer：** `CommandLineRunner` 启动时种子 60+ 配置项，upsert 模式（存在跳过、不存在插入），支持废弃配置迁移和清理
- **加密存储：** `CryptoService` 使用 AES-256-ECB + SHA-256 密钥派生，`enc:` 前缀标识加密值。读取时自动解密，写入时自动加密
- **实时生效：** `ConcurrentHashMap` 内存缓存，update 后立即 refreshCache

**面试问答：**

> **Q：为什么不直接用 Nacos/Apollo 配置中心？**
> A：项目是单体部署（两个 Spring Boot 应用），配置量级（60+项）不需要独立的配置中心。数据库 + 内存缓存的方案够用，且配置可以通过管理后台在线修改，变更实时生效。如果未来拆微服务，再引入 Nacos 不迟。

---

## 三、其他技术亮点

### 3.1 全局异常处理 + AOP 日志

- `GlobalExceptionHandler`：覆盖 8 种异常类型（BusinessException、参数校验、约束违反、邮件、DB 等），所有非平凡异常自动记录为系统消息
- `ExceptionLogAspect`：`@AfterThrowing` 切面，自动记录类名、方法名、异常信息

### 3.2 批量导入导出

- JSON 和 Excel 双格式导入，逐行错误报告
- SXSSFWorkbook 流式导出，避免 OOM
- ExcelUtils 安全单元格读取（处理所有 CellType）

### 3.3 定时任务

- `TicketExpireSchedule`：每 5 分钟自动过期未支付/过期 QR 码订单
- `ChatSessionCleanScheduler`：每天 3:30 AM 清理过期会话（级联删除消息和意图日志）

### 3.4 微信小程序集成

- `WeChatAuthService`：jscode2session 登录 + 自动注册新用户
- 匿名用户支持：X-Anon-Token 头，独立会话 TTL 和日限制

### 3.5 Long→String 序列化

- `JacksonConfig`：全局 Long/long 类型序列化为 String，避免 JavaScript 精度丢失（雪花 ID 超过 Number.MAX_SAFE_INTEGER）

---

## 四、数据库设计亮点

### 4.1 JSON 字段的巧妙使用

| 表 | JSON 字段 | 用途 |
|---|---|---|
| metro_station | prev_station_ids, next_station_ids | 邻接关系，构建 BFS 图 |
| metro_station | prev_station_distances, next_station_distances | 真实站间距 |
| metro_station | line_ids, line_names | 多线路归属（换乘站） |
| metro_line | transfer_lines, transfer_stations | 换乘信息 |
| chat_session | extras | 存储 lastCity 等上下文 |
| chat_message | extras | ROUTE_CARD/ORDER_CARD 结构化数据 |

**为什么这样设计？** 地铁网络是图结构，邻接关系用 JSON 数组存储在站点记录中，比单独的边表更紧凑、查询更快（一次查询拿到一个站的所有邻居）。

### 4.2 雪花算法 ID

所有表使用 `IdType.ASSIGN_ID`（雪花算法），保证全局唯一、趋势递增、不暴露业务含义。

### 4.3 状态码 + 状态文本双字段

如 `status_code`(int) + `status`(varchar)，状态码用于逻辑判断，文本用于前端展示，避免硬编码。

---

## 五、设计模式总结

| 模式 | 应用位置 | 解决的问题 |
|---|---|---|
| **Pipeline / 责任链** | Agent 5 节点流水线 | 复杂业务流程解耦，任意节点可短路 |
| **策略模式** | LlmClient 接口 + 多实现 | 多 LLM 厂商热切换 |
| **模板方法** | ReplyGenerateNode.buildFallbackReply() | 固定场景用模板，避免 LLM 幻觉 |
| **装饰器** | RedisTemplateWrapper | 统一日志和异常处理 |
| **Builder** | AgentContext (Lombok @Builder) | 复杂对象构建 |
| **观察者** | Consumer<Object> push 回调 | 实时消息推送 |
| **双重检查锁** | TicketOrderServiceImpl 站点缓存 | 线程安全的延迟初始化 |

---

## 六、面试高频问题汇总

### Q1：项目整体架构是怎样的？

> TMap 是一个 Spring Boot 多模块项目。common 模块包含所有共享代码（实体、Mapper、Service、Agent 系统、工具类），admin-server 和 user-server 是两个独立的 Spring Boot 应用，分别跑在 8889 和 8888 端口。两者共享 common 模块但各自有独立的 Controller 层和配置。admin 侧重管理 CRUD，user 侧重 AI 对话和票务。

### Q2：AI Agent 是怎么工作的？

> 用户通过 WebSocket 发送自然语言（如"我要从天河公园到广州塔"），Agent 引擎执行 5 步流水线：
>     1）LLM 提取意图和槽位（from/to/city）；
>     2）高德地理编码转经纬度；
>     3）数据库匹配城市；
>     4）LLM+数据库找最近站点 + BFS 最短路径；
>     5）LLM 流式生成自然语言回复 + 推送路线卡片。每一步都可能短路（比如只问天气不走路径规划）。

### Q3：怎么处理 LLM 幻觉？

> 核心是"约束选择"。在站点匹配环节，我不让 LLM 自由生成站名，而是把数据库中该城市所有真实站点名作为上下文喂给它，要求从中挑选。这样即使 LLM 不确定，也只能从真实站点中选。选完后再做 exactMatch 防御性验证。对于路线回复，固定场景（跨城、无地铁、同站）直接用模板，不走 LLM。

### Q4：缓存策略是什么？

> 三层缓存：1）Redis 缓存业务数据（国家/城市/线路/站点列表），TTL 可配置，CRUD 后主动失效；2）ConcurrentHashMap 缓存系统配置，@PostConstruct 加载，更新时刷新；3）volatile + 双重检查锁缓存站点数据，5 分钟 TTL。另外还有管理员可操作的缓存管理 API（按分类清除）。

### Q5：安全方面做了哪些？

> 7 层防护：1）JWT + Redis 双校验认证；2）@RequireRole + AOP 注解式鉴权（4 级角色）；3）Redis 滑动窗口限流（60 req/min）；4）WebSocket 4 层限流；5）登录失败锁定（10 次/30 分钟）；6）AES-256 加密存储 API Key；7）Prompt 注入防御 + 敏感词过滤。

### Q6：WebSocket 怎么保证高可用？

> 1）连接注册表 `AgentSessionRegistry` 管理全量连接，暴露在线指标；2）4 层限流防止单用户打爆；3）`Future<?>` 支持用户随时中止生成；4）自定义关闭码（4029=限流）语义化错误；5）MDC traceId 贯穿全链路便于排查；6）CachedThreadPool daemon 线程不阻塞主线程。

### Q7：如果让你重构这个项目，你会改什么？

> 1）引入 Spring Security 替代手写 TokenInterceptor，更标准化；2）用 Redisson 替代手写分布式锁；3）embedding 存储迁移到向量数据库（数据量增长后）；4）加 Docker Compose 编排；5）引入 Flyway 管理数据库迁移；6）WebSocket 层考虑升级到 STOMP 协议。

---

## 七、简历写法建议

### 精简版（适合简历项目经历栏）

> **TMap 轨道交通智能出行平台** | 全栈开发 | Spring Boot 3 + LangChain4j + WebSocket 
>
> - 基于 LangChain4j 构建 AI Agent 对话引擎，设计 5 节点确定性流水线（意图提取→地理编码→城市匹配→路径规划→回复生成），集成 RAG 知识检索，支持 7+ LLM 厂商热切换
> - 实现 N×N 多候选 BFS 最短路径算法，结合 Haversine 距离计算和阶梯票价模型，支持步行/骑行/出租车多模式接驳
> - 基于 WebSocket 实现流式对话推送，设计 4 层限流体系（匿名日限/用户分钟限/IP 分钟限/并发连接限），支持匿名/登录双模
> - 构建多层安全体系：JWT+Redis 双校验认证、AOP 注解式角色鉴权、AES-256 密钥加密、Prompt 注入防御
> - 设计 Redis+内存双级缓存架构，系统配置中心化管理（60+ 配置项），变更实时生效无需重启

### 扩展版（适合作品集/GitHub README）

在精简版基础上，补充：
- 技术架构图（画一个简单的模块关系图）
- 核心流程图（Agent 5 步流水线）
- 数据库 ER 图
- 关键代码片段截图
- 前端界面截图

---

## 八、关键代码文件索引

| 模块 | 文件 | 核心内容 |
|---|---|---|
| Agent 流水线 | `agent/AgentNode.java` | 节点接口定义 |
| Agent 流水线 | `agent/LangChain4jAgentEngine.java` | 确定性 7 步流水线 |
| Agent 流水线 | `agent/RouteAgentEngine.java` | 自研 5 节点 Pipeline |
| 路径规划 | `service/PathPlanningService.java:218-249` | BFS 最短路径 |
| 路径规划 | `agent/node/PathPlanNode.java:187-229` | RAG 白名单防幻觉 |
| 路径规划 | `agent/node/PathPlanNode.java:290-309` | N×N 多候选优化 |
| 缓存 | `service/impl/TicketOrderServiceImpl.java:37-58` | volatile + 双重检查锁缓存 |
| 缓存 | `service/impl/SystemConfigServiceImpl.java` | ConcurrentHashMap 配置缓存 |
| 安全 | `interceptor/RateLimitInterceptor.java` | Redis 滑动窗口限流 |
| 安全 | `aop/RoleCheckAspect.java` | AOP 角色鉴权 |
| 安全 | `util/CryptoService.java` | AES-256 加密 |
| WebSocket | `websocket/AgentWebSocketHandler.java` | 4 层限流 + 流式推送 |
| WebSocket | `websocket/AgentSessionRegistry.java` | 连接生命周期管理 |
| RAG | `service/RagService.java` | 关键词/向量双模式检索 |
| 地图 | `service/AmapClient.java` | 高德 API 封装 |
| 配置 | `config/SystemConfigInitializer.java` | 60+ 配置种子 |
| 配置 | `config/LangChain4jConfig.java` | 多 LLM 厂商热切换 |
| 异常 | `handler/GlobalExceptionHandler.java` | 8 类异常统一处理 |

---

*文档生成时间：2026-05-20*
