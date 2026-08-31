# AI 智能客服模块设计文档

> 农批履约中台 — 基于 Spring AI 1.1.x 的综合型 AI 智能客服
>
> 定位：综合型（操作助手 + 知识问答 + 多步规划）

---

## 一、设计决策总览

| 维度 | 决策 | 理由 |
|------|------|------|
| AI 框架 | Spring AI 1.1.8 | 与 Spring Boot 3.5.x 原生兼容，ChatClient/ToolCalling/RAG/MCP/ChatMemory 全覆盖 |
| Spring Boot | 3.4.5 → 3.5.x | Spring AI 1.1.x 要求 Boot 3.5.x，小版本升级风险可控 |
| LLM 提供方 | OpenAI 兼容协议 | 通义千问/日日新/Ollama 均兼容，切换仅需改环境变量 |
| 本地开发模型 | Ollama qwen2.5:3b（对话）+ bge-m3（向量） | 全本地免费，答辩演示无网络依赖 |
| 向量存储 | PGVector（PostgreSQL + pgvector 扩展） | 多语言持久化，Docker 已就绪 |
| ChatMemory | Redis（自实现 Repository） | Spring AI 1.1.2 无 Redis ChatMemoryRepository，自实现 + TTL 1h 防泄漏 + 前端 history 接口恢复 |
| 实施策略 | 分阶段全量实现（P0-P4） | 每阶段可独立演示，功能完整 |

---

## 二、模块架构

### 2.1 新增 ai 模块

在现有 5 模块（lot/inventory/alert/common/bootstrap）基础上新增第 6 个模块 `ai`。

```
nongpi-fulfillment/
├── lot/          ── 批次管理
├── inventory/    ── 库存管理
├── alert/        ── 预警管理
├── common/       ── 公共（领域/异常/安全）
├── bootstrap/    ── 启动聚合
└── ai/           ── ★ 新增 AI 智能客服模块
    └── src/main/java/com/nongpi/fulfillment/ai/
        ├── api/                            # 接口层
        │   └── AiController.java               # /ai/* REST + SSE 入口
        ├── app/                            # 应用层（编排）
        │   └── NongpiAssistantApp.java         # ChatClient 初始化 + 功能方法
        ├── tools/                          # ToolCalling（本地工具）
        │   ├── ToolRegistration.java           # 工具注册
        │   ├── LotQueryTool.java               # 查批次/查流转记录
        │   ├── LotOperationTool.java           # 入库/出库/转库
        │   ├── InventoryQueryTool.java         # 查库存/查可用量
        │   ├── InventoryOperationTool.java     # 调整/冻结/解冻
        │   ├── AlertQueryTool.java             # 查预警/查规则
        │   ├── AlertOperationTool.java         # 临期检查/规则开关
        │   └── TerminateTool.java              # Manus 终止标记
        ├── rag/                            # RAG 知识库
        │   ├── DocumentLoader.java             # 文档加载（空壳可扩展）
        │   ├── TextSplitter.java               # 文档分片
        │   ├── KeywordEnricher.java            # 关键词增强
        │   ├── QueryRewriter.java              # 查询重写
        │   ├── RagAdvisorFactory.java          # 检索增强顾问工厂
        │   └── VectorStoreConfig.java          # PGVector 向量存储配置
        ├── agent/                          # Manus ReAct 智能体
        │   ├── BaseAgent.java                  # 循环引擎 + SSE 输出
        │   ├── ReActAgent.java                 # think()+act() 拆分
        │   ├── ToolCallAgent.java              # 工具调用实现
        │   └── NongpiManus.java                # 农批具体智能体
        ├── advisor/                        # 自定义 Advisor
        │   └── LoggingAdvisor.java             # 请求/响应日志
        └── config/                         # AI 配置
            └── AiConfig.java                  # ChatClient/ChatMemory Bean
```

### 2.2 模块依赖

```
ai → common（异常体系/值对象）
ai → lot（LotAppService/LotQueryService）
ai → inventory（InventoryAppService/InventoryQueryService）
ai → alert（AlertAppService/AlertQueryService）
bootstrap → ai（聚合启动）
```

工具类直接注入业务模块的 Application Service，不绕 HTTP，同一 JVM 内调用。

### 2.3 父 POM 变更

```xml
<!-- pom.xml 新增 module -->
<modules>
    <module>lot</module>
    <module>inventory</module>
    <module>alert</module>
    <module>bootstrap</module>
    <module>common</module>
    <module>ai</module>                    <!-- ★ 新增 -->
</modules>

<!-- Spring Boot 版本升级 -->
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.5.15</version>              <!-- ★ 从 3.4.5 升级 -->
    <relativePath/>
</parent>

<!-- Spring AI BOM -->
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>1.1.8</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

### 2.4 ai 模块 POM

```xml
<dependencies>
    <!-- 模块内部依赖 -->
    <dependency>
        <groupId>com.nongpi.fulfillment</groupId>
        <artifactId>common</artifactId>
        <version>${project.version}</version>
    </dependency>
    <dependency>
        <groupId>com.nongpi.fulfillment</groupId>
        <artifactId>lot</artifactId>
        <version>${project.version}</version>
    </dependency>
    <dependency>
        <groupId>com.nongpi.fulfillment</groupId>
        <artifactId>inventory</artifactId>
        <version>${project.version}</version>
    </dependency>
    <dependency>
        <groupId>com.nongpi.fulfillment</groupId>
        <artifactId>alert</artifactId>
        <version>${project.version}</version>
    </dependency>

    <!-- Spring AI -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-model-openai</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-vector-store-pgvector</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-mcp-client</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
    </dependency>

    <!-- PostgreSQL (PGVector) -->
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <scope>runtime</scope>
    </dependency>
</dependencies>
```

### 2.5 第三方依赖版本升级

| 依赖 | 当前版本 | 升级到 | 原因 |
|------|----------|--------|------|
| Spring Boot | 3.4.5 | 3.5.15 | Spring AI 1.1.x 要求 |
| MyBatis-Plus | 3.5.12 | 3.5.17 | 兼容 Boot 3.5.x |
| Redisson | 3.40.2 | 3.51.0 | 兼容 Boot 3.5.x |
| JJWT | 0.12.6 | 不变 | 纯 Java 库 |
| Hutool | 5.8.37 | 不变 | 纯 Java 库 |
| Lombok | 1.18.36 | 不变 | 兼容 Java 21 |

---

## 三、LLM 接入配置

### 3.1 application.yml 配置

```yaml
spring:
  ai:
    openai:
      api-key: ${LLM_API_KEY:ollama}           # Ollama 不需要真实 key
      base-url: ${LLM_BASE_URL:http://localhost:11434/v1}  # Ollama 默认地址
      chat:
        options:
          model: ${LLM_MODEL:qwen2.5:3b}
          temperature: 0.7
      embedding:
        options:
          model: ${EMBEDDING_MODEL:bge-m3}
    # PGVector 向量存储
    vectorstore:
      pgvector:
        # PostgreSQL 连接（独立于 MySQL 业务库）
        # 通过环境变量覆盖，或使用 Spring Boot 标准 datasource 配置
        dimensions: 1024                        # bge-m3 输出 1024 维
        distance-type: COSINE_DISTANCE         # 余弦相似度
        index-type: HNSW                        # 高性能近似最近邻索引
        schema-name: public
        table-name: vector_store
    # ChatMemory（Redis）
    chat:
      memory:
        repository:
          redis:
            # 复用现有 Redis 连接
            key-prefix: "ai:chat:memory:"
```

### 3.2 提供商切换

| 提供商 | LLM_BASE_URL | LLM_MODEL | LLM_API_KEY |
|--------|-------------|-----------|-------------|
| Ollama 本地 | http://localhost:11434/v1 | qwen2.5:3b | ollama |
| 通义千问 | https://dashscope.aliyuncs.com/compatible-mode/v1 | qwen-plus | ${DASHSCOPE_API_KEY} |
| 日日新 | https://api.sensetime.com/v1 | sensechat | ${SENSETIME_API_KEY} |
| DeepSeek | https://api.deepseek.com/v1 | deepseek-chat | ${DEEPSEEK_API_KEY} |

切换仅需改环境变量，代码零改动。

---

## 四、ToolCalling 工具清单

### 4.1 本地业务工具

| 工具类 | @Tool 方法 | 对应 Service | 参数 | 用途 |
|--------|-----------|---------------|------|------|
| LotQueryTool | queryLotStatus | LotQueryService.getLotByNo | lotNo: String | 查批次状态/剩余量 |
| | listLots | LotQueryService.listLots | skuId, tempZone, status, page, size | 分页查批次 |
| | getTransferRecords | LotQueryService.listTransfers | lotNo: String | 查流转记录 |
| LotOperationTool | inboundLot | LotAppService.inbound | skuId, tempZone, produceDate, expireDate, qty, supplierId | 入库 |
| | outboundLot | LotAppService.outbound | lotNo, qty, location | 出库 |
| | transferLot | LotAppService.transfer | lotNo, toTempZone, qty | 转库 |
| InventoryQueryTool | queryInventory | InventoryQueryService.getInventory | skuId, tempZone | 查库存/可用量 |
| | listInventory | InventoryQueryService.list | page, size | 分页查库存 |
| InventoryOperationTool | adjustStock | InventoryAppService.adjust | skuId, tempZone, delta, reason | 库存调整 |
| | freezeStock | InventoryAppService.freeze | skuId, tempZone, qty | 冻结 |
| | unfreezeStock | InventoryAppService.unfreeze | skuId, tempZone, qty | 解冻 |
| AlertQueryTool | listAlerts | AlertQueryService.listAlerts | handled, page, size | 查预警记录 |
| | listAlertRules | AlertQueryService.listRules | skuId, enabled | 查预警规则 |
| AlertOperationTool | checkExpiringLots | AlertAppService.checkExpiringLots | 无 | 手动临期检查 |
| | toggleAlertRule | AlertAppService.toggleRule | ruleId, enabled | 开关规则 |
| TerminateTool | doTerminate | 无 | 无 | Manus 终止标记 |

### 4.2 权限控制

- 查询类工具：所有认证用户可用
- 写操作工具（inbound/outbound/transfer/adjust/freeze/unfreeze/toggleRule）：要求 ADMIN 角色
- 在工具方法内通过 SecurityContextHolder 获取当前用户角色，非 ADMIN 抛 BusinessException

### 4.3 工具注册

```java
@Configuration
public class ToolRegistration {
    @Bean
    public ToolCallback[] allTools(
            LotQueryTool lotQueryTool,
            LotOperationTool lotOperationTool,
            InventoryQueryTool inventoryQueryTool,
            InventoryOperationTool inventoryOperationTool,
            AlertQueryTool alertQueryTool,
            AlertOperationTool alertOperationTool,
            TerminateTool terminateTool
    ) {
        return ToolCallbacks.from(
            lotQueryTool, lotOperationTool,
            inventoryQueryTool, inventoryOperationTool,
            alertQueryTool, alertOperationTool,
            terminateTool
        );
    }
}
```

---

## 五、MCP 双角色设计

### 5.1 MCP Client（消费外部工具）

通过 `spring-ai-starter-mcp-client` 消费外部 MCP Server。

```json
// ai/src/main/resources/mcp-servers.json
{
  "mcpServers": {
    "web-search": {
      "command": "npx.cmd",
      "args": ["-y", "@anthropic/mcp-server-web-search"],
      "env": { "SEARCH_API_KEY": "${SEARCH_API_KEY}" }
    }
  }
}
```

Spring AI 自动启动 MCP Server 子进程、获取远程工具、封装为 `ToolCallbackProvider`。

### 5.2 MCP Server（暴露业务工具）

通过 `spring-ai-starter-mcp-server-webmvc` 将农批业务工具暴露为 MCP Server。

```java
// 任何支持 MCP 的 AI 客户端都能调用以下工具：
// - queryLotStatus / listLots / getTransferRecords
// - queryInventory / listInventory
// - listAlerts / listAlertRules
// 写操作工具不暴露给外部（安全考虑）
```

这让农批系统成为"AI-ready"的业务服务——外部 AI 应用可以通过 MCP 协议查询批次状态、库存、预警等信息。

---

## 六、RAG 知识库

### 6.1 入库流程

```
Spring 启动 → VectorStoreConfig.pgVectorVectorStore() Bean 初始化
  │
  ├─ ① 检查是否已有数据（避免重启重复入库）
  │    SELECT COUNT(*) FROM vector_store
  │    if (count > 0) → 跳过
  │
  ├─ ② DocumentLoader.loadMarkdowns()
  │    读取 ai/src/main/resources/docs/*.md
  │    空壳：放 1-2 个示例文档（如 冷链存储规范.md、批次管理操作手册.md）
  │    返回 List<Document>
  │
  ├─ ③ TextSplitter.splitCustomized(documents)
  │    TokenTextSplitter，默认 500 token/片
  │    每个片段保留原文档的 metadata
  │
  ├─ ④ KeywordEnricher.enrichDocuments(splitDocuments)
  │    对每个分片调 EmbeddingModel（bge-m3）生成关键词
  │    写入 document.metadata.keywords
  │
  ├─ ⑤ vectorStore.add(enrichedDocuments)
  │    bge-m3 将文本转为 1024 维向量
  │    [向量 + 原文 + metadata] 存入 PGVector
  │
  └─ 完成
```

### 6.2 出库流程

```
用户："常温区能放多久"
  │
  ├─ ① QueryRewriter.doQueryRewrite(message)
  │    调 LLM 重写："常温区冷链存储保质期规范"
  │
  ├─ ② chatClient.prompt()
  │      .user(rewrittenMessage)
  │      .advisors(MessageChatMemoryAdvisor)         // 历史记忆
  │      .advisors(LoggingAdvisor)                   // 日志
  │      .advisors(RagAdvisorFactory.create(          // RAG 检索
  │          vectorStore, similarityThreshold=0.5, topK=3
  │      ))
  │      .call()
  │
  ├─ ③ RetrievalAugmentationAdvisor 内部
  │    vectorStore.similaritySearch(rewrittenMessage)
  │    → 返回 topK=3 最相关文档片段
  │    → ContextualQueryAugmenter 拼入 Prompt
  │
  └─ ④ LLM 基于检索到的知识生成回答
```

### 6.3 向量存储配置

```java
@Configuration
public class VectorStoreConfig {
    @Bean
    public VectorStore pgVectorVectorStore(
            EmbeddingModel embeddingModel,
            JdbcTemplate pgJdbcTemplate  // PostgreSQL 的 JdbcTemplate
    ) {
        return PgVectorStore.builder(pgJdbcTemplate, embeddingModel)
                .dimensions(1024)
                .distanceType(PgVectorStore.DistanceType.COSINE_DISTANCE)
                .indexType(PgVectorStore.IndexType.HNSW)
                .schemaName("public")
                .table_name("vector_store")
                .initializeSchema(true)
                .build();
    }
}
```

---

## 七、ChatMemory（对话记忆 — Redis 持久化）

### 7.1 实际实现（已落地）

Spring AI 1.1.2 BOM 未提供 Redis ChatMemoryRepository（只有 jdbc/cassandra/mongodb），自行实现 `ChatMemoryRepository` 接口，对话记忆持久化到 Redis，TTL 自动清理防内存泄漏。

```java
// RedisChatMemoryRepository.java — 自实现，复用项目 RedissonClient
public class RedisChatMemoryRepository implements ChatMemoryRepository {
    private static final String KEY_PREFIX = "chatmemory:";
    private static final Duration TTL = Duration.ofHours(1);

    // 序列化方案：Message 子类不可变（无默认构造函数），Jackson 直接反序列化失败
    // → 转 {role, content} DTO 存储，反序列化时按 role 重建 Message
    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        List<Map<String, String>> dtoList = messages.stream()
            .map(msg -> Map.of("role", msg.getMessageType().name(), "content", msg.getText()))
            .toList();
        String json = objectMapper.writeValueAsString(dtoList);
        redissonClient.getBucket(KEY_PREFIX + conversationId, StringCodec.INSTANCE)
            .set(json, TTL); // 每次保存刷新 TTL，活跃会话不过期
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        String json = bucket.get();
        if (json == null) return new ArrayList<>();
        return objectMapper.readValue(json, ...).stream()
            .map(dto -> switch (dto.get("role")) {
                case "USER" -> new UserMessage(dto.get("content"));
                case "ASSISTANT" -> new AssistantMessage(dto.get("content"));
                case "SYSTEM" -> new SystemMessage(dto.get("content"));
                default -> new UserMessage(dto.get("content"));
            }).toList();
    }
}

// AiConfig.java — @Primary 覆盖 Spring AI 自动配置的 InMemoryChatMemoryRepository
@Bean
@Primary
public ChatMemoryRepository chatMemoryRepository(RedissonClient redissonClient, ObjectMapper objectMapper) {
    return new RedisChatMemoryRepository(redissonClient, objectMapper);
}
```

### 7.2 对话历史恢复（前端刷新不丢）

```
首次发送 → 生成 chatId → 存 localStorage（按 用户名:模式 区分）
                         ↓
                    消息存 Redis ChatMemory
                         ↓
刷新页面 → 读 localStorage chatId → 调 GET /api/ai/history?chatId=xxx
                         ↓
                    Redis 有数据 → 恢复消息列表（跳过 SYSTEM 提示词）
                         ↓
Redis 过期(TTL 1h) → history 返回空 → 清 localStorage → 下次发送重新开 chatId
```

**三层容错**：
1. 后端 getHistory try-catch：Redis 抖动返回空列表而非 500
2. 前端区分错误类型：500 保留 chatId（重试），200+空数组才清 chatId（真过期）
3. 网络错误不清 chatId：fetch 抛错保留 chatId，下次刷新重试

### 7.3 chatId 常量

```java
// NongpiAssistantApp.java — 用 ChatMemory.CONVERSATION_ID 常量（非硬编码）
chatClient.prompt()
    .user(message)
    .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, chatId))
    // ChatMemory.CONVERSATION_ID = "chat_memory_conversation_id"（Spring AI 1.1.2）
    .call();
```

---

## 八、Manus（ReAct 智能体）

### 8.1 继承体系

```
BaseAgent（抽象类）
  │  职责：循环框架 + 状态管理 + SSE 输出
  │  方法：run() / runStream() / step()(抽象) / cleanup()
  │
  └─ ReActAgent（抽象类）
       │  职责：把每一步拆分为 think() + act()
       │  方法：think()(抽象) / act()(抽象) / step()(实现)
       │
       └─ ToolCallAgent（可实例化）
            │  职责：think/act 的具体实现
            │  think() → 调 LLM 选工具
            │  act() → 执行工具 + 检测 terminate
            │
            └─ NongpiManus（具体智能体）
                 │  职责：配置农批系统提示词 + 全部业务工具 + maxSteps=20
                 │
                 └─ Controller 中 new NongpiManus(allTools, chatModel).runStream(message)
```

### 8.2 典型多步场景

```
用户："检查所有临期批次，给严重过期的创建预警规则"

Step 1: think → 调 checkExpiringLots()     → 拿到临期批次列表
Step 2: think → 调 listAlertRules()         → 看现有规则
Step 3: think → 调 toggleAlertRule(ruleId=true) → 开启缺失的预警规则
Step 4: think → 调 doTerminate()            → 任务完成，退出循环

每步通过 SSE 推送给前端，用户看到 AI 的完整思考和执行过程。
```

### 8.3 ToolCallAgent 关键设计

禁用 Spring AI 内置工具自动执行（ReAct 需要手动控制 think/act 循环）：

```java
// ToolCallAgent 构造函数
this.chatOptions = OpenAiChatOptions.builder()
        .withInternalToolExecutionEnabled(false)  // 禁用自动执行
        .build();
```

---

## 九、API 接口设计

### 9.1 AiController（实际实现）

```java
@RestController
@RequestMapping("/api/ai")
public class AiController {

    // 基础对话（同步，含记忆 + 工具 + MCP）
    @PostMapping("/chat")
    public String chat(@RequestParam String message, @RequestParam String chatId);

    // 流式对话（SseEmitter，fallback 同步 .call() + 虚拟线程模拟流式）
    @GetMapping(value = "/chat/stream", produces = "text/event-stream")
    public SseEmitter chatStream(@RequestParam String message, @RequestParam String chatId);

    // RAG 对话（SseEmitter，检索知识库后流式回答）
    @GetMapping(value = "/chat/rag", produces = "text/event-stream")
    public SseEmitter chatWithRag(@RequestParam String message, @RequestParam String chatId);

    // Manus 多步智能体（SseEmitter，prototype scope 每次新实例）
    @GetMapping(value = "/manus/chat", produces = "text/event-stream")
    public SseEmitter manusChat(@RequestParam String message);

    // ★ 新增：查询历史对话（供前端刷新恢复）
    @GetMapping("/history")
    public List<ChatMessageDto> history(@RequestParam String chatId);
}
```

**流式 fallback 说明**：Spring AI 1.1.2 `ChatClient.stream()` 在 servlet 环境下 Flux 静默挂死，改用同步 `.call()` + `CompletableFuture.runAsync` 虚拟线程模拟流式（按 2 字符拆分 + 30ms 延时推送）。

### 9.2 SecurityConfig 放行

```java
.requestMatchers("/api/ai/chat/stream", "/api/ai/manus/chat").permitAll()  // SSE 流式接口
.requestMatchers("/api/ai/**").authenticated()  // 其他 AI 接口需认证
```

---

## 十、分阶段实施计划

| 阶段 | 内容 | 交付物 | 可演示 |
|------|------|--------|--------|
| P0 | Spring Boot 3.4→3.5 升级 + Spring AI 1.1.8 BOM + ai 模块骨架 + ChatClient + ChatMemory | 基础对话 + 多轮记忆 | ✅ |
| P1 | ToolCalling（14 个业务工具） + 权限控制 | "查 LOT20260718 的状态" → AI 调工具回答 | ✅ |
| P2 | RAG（PGVector + 文档管线，空壳可扩展） | "常温区能放多久" → AI 检索知识库回答 | ✅ |
| P3 | Manus（ReAct 智能体） | "检查临期批次并创建规则" → AI 多步规划执行 | ✅ |
| P4 | MCP（Client 消费外部工具 + Server 暴露业务工具） | 外部 AI 应用通过 MCP 查询农批系统 | ✅ |

---

## 十一、数据流总览

```
┌─────────────────────────────────────────────────────────────────┐
│                        AiController (/ai/*)                      │
├──────────┬────────────┬────────────┬────────────┬────────────────┤
│ ChatClient│ ToolCalling│    RAG     │    MCP     │    Manus       │
│ ChatMemory│ (@Tool)   │  Advisor   │  Provider  │  (ReAct Agent) │
│ Advisor   │  Advisor  │  Advisor   │            │  think+act     │
├──────────┴────────────┴────────────┴────────────┴────────────────┤
│                    NongpiAssistantApp（编排）                     │
├──────────────────────────────────────────────────────────────────┤
│  Ollama / 通义千问 / 日日新 (OpenAI 兼容协议)                      │
│  chat: qwen2.5:3b / qwen-plus    embedding: bge-m3              │
├──────────────────────────────────────────────────────────────────┤
│  MySQL (业务数据)    PGVector (向量)    Redis (ChatMemory/锁)     │
└──────────────────────────────────────────────────────────────────┘
```

---

## 十二、错误处理

| 场景 | 处理 |
|------|------|
| LLM 调用超时 | 返回 503 + "AI 服务暂时不可用，请稍后重试" |
| 工具执行失败 | 捕获异常，将错误信息返回给 LLM，让 LLM 决定是否重试 |
| 向量检索无结果 | 返回空上下文，LLM 用自身知识回答 |
| Ollama 未启动 | 启动时健康检查，返回明确错误提示 |
| 用户无权限调用写操作工具 | 工具内抛 BusinessException(403) |

---

## 十三、测试策略

| 层级 | 测试内容 | 方法 |
|------|----------|------|
| 单元测试 | 工具方法逻辑（mock Service） | JUnit 5 + Mockito |
| 集成测试 | ChatClient 调用（mock LLM） | @SpringBootTest |
| 集成测试 | RAG 入库 + 检索（真实 PGVector） | Testcontainers PostgreSQL |
| 集成测试 | Manus 多步执行（mock LLM 返回预设工具调用） | @SpringBootTest |
| 手动验证 | Ollama 本地全链路演示 | curl /ai/chat |

---

## 十四、技术亮点总结

核心实现亮点：

> **农批履约中台 — AI 智能客服模块**
> - 基于 Spring AI 1.1.x 构建 DDD 多模块架构的 AI 智能客服，覆盖 ChatClient、ToolCalling、RAG、MCP、Manus 五大核心能力
> - 自实现 RedisChatMemoryRepository（Spring AI 无现成 Redis 实现），对话记忆持久化到 Redis + TTL 自动清理，前端刷新页面可恢复历史对话（三层容错防临时故障丢会话）
> - 设计 8 个业务工具映射到批次/库存/预警三大业务域，通过 @Tool 注解实现 LLM 函数调用；MCP 接入天气工具（含中文城市拼音映射）
> - 基于 PGVector 实现 RAG 知识库，支持文档分片、关键词增强、向量检索（HNSW + 余弦相似度）
> - 实现 ReAct 智能体（think+act 循环，prototype scope 防并发串话），支持多步任务规划和 SSE 实时输出
> - 流式对话 fallback：Spring AI stream 在 servlet 环境 Flux 挂死，改用同步 .call() + 虚拟线程模拟流式
> - MCP 双角色：作为 Client 消费外部工具，作为 Server 将业务能力暴露给外部 AI 应用
> - 通过 OpenAI 兼容协议支持通义千问/日日新/Ollama 多提供商切换
> - 技术栈：Spring Boot 3.5.x / Spring AI 1.1.x / PGVector / Redis / Ollama
