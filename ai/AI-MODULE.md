# AI 智能客服模块

> 农批履约中台 — 基于 Spring AI 1.1.2 构建的智能客服模块，覆盖 ChatClient / ChatMemory / ToolCalling / RAG / Manus / MCP 六大核心能力。

## 一、模块定位

综合型 AI 助手（操作 + 问答）：

- **问答型**：用户问"常温区干货能放多久" → RAG 检索知识库回答
- **操作型**：用户说"帮我把 LOT001 的库存冻结 50kg" → ToolCalling 调业务 API 执行
- **多步型**：用户说"看看有没有临期批次，把对应库存冻结" → Manus ReAct 多步规划跨域执行

## 二、技术栈

| 组件 | 选型 | 说明 |
|---|---|---|
| AI 框架 | Spring AI 1.1.2 | BOM 统一管理，与 Spring Boot 3.5.15 兼容 |
| LLM 接入 | OpenAI 兼容协议 | 一套配置切换 Ollama / 通义千问 / 日日新 |
| 演示模型 | Ollama qwen2.5:3b + bge-m3 | 文本 LLM + 向量 LLM，本地运行 |
| 对话记忆 | MessageWindowChatMemory | InMemory 存储，生产可换 RedisChatMemoryRepository |
| 向量存储 | PGVector（PostgreSQL 15） | 独立数据源，1024 维余弦相似度 HNSW 索引 |
| 工具调用 | Spring AI ToolCalling | @Tool 注解 + ToolCallback |
| MCP 协议 | spring-ai-starter-mcp-client | 接 filesystem MCP server，证明外部工具消费能力 |
| 智能体 | ReAct（BaseAgent → NongpiManus） | 多步 think-act 循环，SSE 流式输出 |

## 三、目录结构

```
ai/
├── pom.xml                                  # 依赖管理（Spring AI BOM + mcp-client）
└── src/main/
    ├── java/com/nongpi/fulfillment/ai/
    │   ├── api/AiController.java            # REST 端点入口
    │   ├── app/NongpiAssistantApp.java      # 核心编排：基础对话/流式/RAG
    │   ├── config/
    │   │   ├── AiConfig.java                # ChatClient + ChatMemory + MCP 注入
    │   │   └── RagConfig.java               # PGVector 数据源 + VectorStore
    │   ├── tools/
    │   │   ├── NongpiToolSet.java            # 8 个 @Tool 业务工具
    │   │   └── TerminateTool.java            # Manus 终止信号
    │   ├── rag/
    │   │   ├── DocumentLoader.java           # 文档加载 + 元数据解析
    │   │   ├── KeywordEnricher.java         # LLM 生成关键词增强检索
    │   │   ├── QueryRewriter.java           # 查询重写提升召回率
    │   │   ├── RagCategoryInferrer.java     # 关键词推断 category
    │   │   └── RagAdvisorFactory.java       # 动态生成带过滤的 RAG Advisor
    │   └── agent/
    │       ├── BaseAgent.java               # 循环框架 + SSE 输出
    │       ├── ReActAgent.java              # think + act 拆分
    │       ├── ToolCallAgent.java           # 工具调用具体实现
    │       ├── NongpiManus.java             # 农批 Manus 配置（工具+提示词）
    │       └── model/AgentState.java        # 状态枚举
    └── resources/
        ├── mcp-servers.json                 # MCP Client 配置（filesystem server）
        └── document/                        # RAG 知识库源文档
            ├── storage-spec-冷链存储规范.md
            ├── lot-mgmt-批次管理流程.md
            └── alert-rule-预警规则说明.md
```

## 四、核心能力原理

### 4.1 ChatClient（基础对话）

**原理**：`ChatClient.builder()` 链式构建，挂载系统提示词 + Advisor 链 + 默认工具。LLM 调用通过 OpenAI 兼容协议，Ollama 在 `localhost:11434/v1` 提供 OpenAI API。

**Advisor 链**：
- `MessageChatMemoryAdvisor` — 多轮记忆，按 `conversation_id` 隔离对话
- `SimpleLoggerAdvisor` — 请求/响应日志

**配置位置**：`bootstrap/src/main/resources/application.yml`
```yaml
spring:
  ai:
    openai:
      api-key: ${LLM_API_KEY:ollama}
      base-url: ${LLM_BASE_URL:http://localhost:11434/v1}
      chat:
        options:
          model: ${LLM_MODEL:qwen2.5:3b}
```

### 4.2 ChatMemory（多轮记忆）

**原理**：`MessageWindowChatMemory` 维护滑动窗口（默认 20 条），通过 `InMemoryChatMemoryRepository` 存储。每次请求时 `MessageChatMemoryAdvisor` 自动加载历史消息拼接到 Prompt。

**会话隔离**：通过 `chatId` 参数传给 `MessageChatMemoryAdvisor`，不同用户对话互不干扰。

**生产升级**：替换 `InMemoryChatMemoryRepository` 为 `RedisChatMemoryRepository` 即可分布式持久化。

### 4.3 ToolCalling（业务工具调用）

**原理**：在 POJO 方法上加 `@Tool` 注解 + `@ToolParam` 参数描述，Spring AI 自动生成 ToolDefinition 暴露给 LLM。LLM 决定调用时，框架通过反射执行并把结果回传。

**8 个工具一览**：

| 域 | 工具 | 类型 | 权限 |
|---|---|---|---|
| lot | `queryLotStatus` | 查询 | 所有用户 |
| lot | `inboundLot` | 写入 | ADMIN |
| lot | `outboundLot` | 写入（FEFO） | ADMIN |
| inventory | `queryInventory` | 查询 | 所有用户 |
| inventory | `freezeStock` | 写入 | ADMIN |
| alert | `listAlertRecords` | 查询 | 所有用户 |
| alert | `checkExpiringLots` | 跨域触发 | ADMIN |
| system | `doTerminate` | Manus 终止 | - |

**权限校验**：写操作工具调用 `requireAdmin()`，从 `SecurityContextHolder` 读取 JWT 过滤器写入的 `ROLE_ADMIN`，非管理员抛 `BusinessException(403)`。

**参数容错**：数量/日期用 `String` 接收再 `BigDecimal` / `LocalDate.parse` 转换，避免 LLM 传错类型导致反序列化失败。

### 4.4 RAG（检索增强生成）

**完整管线**：

```
用户问题
  ↓
QueryRewriter      → LLM 重写为检索友好查询（口语 → 术语）
  ↓
RagCategoryInferrer → 关键词推断 category（storage-spec/lot-mgmt/alert-rule）
  ↓
RagAdvisorFactory   → 按 category 生成带 filterExpression 的 RetrievalAugmentationAdvisor
  ↓
VectorStoreDocumentRetriever → PGVector 向量检索 topK=3，相似度阈值 0.5
  ↓
ContextualQueryAugmenter → 将检索片段拼接到用户查询
  ↓
ChatClient.call()  → LLM 基于上下文回答
```

**入库流程**（启动时自动执行）：

```
Spring 启动 → DocumentLoader 扫描 classpath:document/*.md
  → 按文件名前缀解析 category 元数据
  → TokenTextSplitter 分片
  → KeywordEnricher 调 LLM 为每片生成 3-5 个关键词写入 metadata
  → VectorStore.add() 向量化（bge-m3 1024 维）入库 PGVector
```

**知识库扩展**：在 `ai/src/main/resources/document/` 新增 `<category>-<title>.md` 文件，重启即生效。

### 4.5 Manus（ReAct 智能体）

**继承体系**：

```
BaseAgent              ← 循环框架 + SSE 输出 + 状态管理
  └─ ReActAgent        ← 拆分 think() + act()
       └─ ToolCallAgent ← 工具调用具体实现（解析 ToolCall，执行 ToolCallback）
            └─ NongpiManus ← 农批具体配置（工具集 + 提示词 + maxSteps=20）
```

**ReAct 循环**：

1. `think()` — 把 `nextStepPrompt` 加入消息列表，调 ChatModel，看响应是否包含 `toolCalls`
2. `act()` — 遍历 `toolCalls`，按 name 匹配 `ToolCallback` 执行，结果包装成 `ToolResponseMessage` 回传 LLM
3. 检测到 `doTerminate` 调用 → `state = FINISHED` 退出循环
4. 达到 `maxSteps=20` 强制退出，防止无限循环

**SSE 输出**：每步通过 `SseEmitter.send()` 推送 `step` 事件，前端实时看到 think/act 过程。

**演示场景**：用户问"查 LOT001 状态，临期就把对应库存冻结" → Manus 5 步链路：

```
Step 1: queryLotStatus(LOT001)         → 看到 expireDate 接近
Step 2: checkExpiringLots()            → 触发预警扫描，生成 N 条记录
Step 3: listAlertRecords(handled=false) → 拿到临期批次列表
Step 4: freezeStock(skuId, zone, qty)  → 冻结对应库存
Step 5: doTerminate()                  → 任务完成
```

### 4.6 MCP Client（外部工具消费）

**原理**：Spring AI 的 `spring-ai-starter-mcp-client` 启动时按 `mcp-servers.json` 配置拉起 MCP server 子进程（Stdio 协议），自动发现其暴露的工具并封装为 `ToolCallbackProvider` Bean。

**配置**：

```json
// ai/src/main/resources/mcp-servers.json
{
  "mcpServers": {
    "filesystem": {
      "command": "npx.cmd",
      "args": [
        "-y",
        "@modelcontextprotocol/server-filesystem",
        "C:\\Users\\Lenovo\\Desktop\\nongpi-ddd-springai-platform\\ai\\src\\main\\resources\\document"
      ]
    }
  }
}
```

**AiConfig 注入**：`@Autowired(required=false) ToolCallbackProvider`，有就加到 `ChatClient.defaultToolCallbacks()`，没有不影响主流程。

**价值**：让 LLM 能 `read_file` / `list_directory` 直接读取项目文档，证明 MCP 协议接入能力（2024.11 Anthropic 推出的新协议）。

## 五、API 端点

| 方法 | 路径 | 参数 | 返回 | 说明 |
|---|---|---|---|---|
| POST | `/api/ai/chat` | `message`, `chatId` | `String` | 基础对话（同步，含记忆+工具） |
| POST | `/api/ai/chat/stream` | `message`, `chatId` | `Flux<String>` | 流式对话 SSE |
| POST | `/api/ai/chat/rag` | `message`, `chatId` | `String` | RAG 知识库问答 |
| GET | `/api/ai/manus/chat` | `message` | `SseEmitter` | Manus 多步执行 SSE |

**示例**：

```bash
# 基础对话
curl -X POST "http://localhost:8080/api/ai/chat?message=查LOT001批次状态&chatId=u1"

# RAG 问答
curl -X POST "http://localhost:8080/api/ai/chat/rag?message=常温区干货能放多久&chatId=u1"

# Manus 多步执行（SSE 长连接）
curl -N "http://localhost:8080/api/ai/manus/chat?message=检查临期批次并冻结对应库存"
```

## 六、运行前提

| 依赖 | 是否必需 | 说明 |
|---|---|---|
| Ollama + qwen2.5:3b | 必需 | 文本 LLM，`ollama pull qwen2.5:3b` |
| Ollama + bge-m3 | 必需（RAG） | 向量 LLM，`ollama pull bge-m3` |
| PGVector 容器 | 必需（RAG） | `docker run -d --name pgvector -p 5433:5432 -e POSTGRES_USER=honcho -e POSTGRES_PASSWORD=honcho_secret_2024 -e POSTGRES_DB=honcho_memory pgvector/pgvector:pg15` |
| MySQL + Redis | 必需 | 业务主库 + 分布式锁 |
| Node.js (npx) | 可选（MCP） | 启动 filesystem MCP server，不装不影响主流程 |

## 七、配置切换

**切换 LLM 提供商**（不改代码，只改环境变量）：

```bash
# 通义千问
export LLM_API_KEY=sk-xxx
export LLM_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
export LLM_MODEL=qwen-max

# 日日新
export LLM_API_KEY=xxx
export LLM_BASE_URL=https://api.x.ai/v1
export LLM_MODEL=grok-beta
```

**关闭 MCP**：删除 `spring.ai.mcp.client` 配置块或注释 `mcp-servers.json` 即可，主流程不受影响。

## 八、技术要点总结

- 基于 Spring AI 1.1.2 构建 DDD 多模块架构的 AI 智能客服，覆盖 ChatClient / ToolCalling / RAG / Manus / MCP 五大能力
- 8 个业务 @Tool 覆盖批次/库存/预警 3 大域，含写操作 JWT 权限控制和跨域多步规划
- RAG 完整管线：文档加载 → LLM 关键词增强 → 查询重写 → category 推断 → PGVector 过滤检索
- ReAct 智能体（BaseAgent → NongpiManus 继承体系），多步 think-act 循环 + SSE 流式输出
- 基于 MCP 协议（2024 Anthropic 新标准）集成 filesystem 外部工具，证明 LLM 对非结构化数据访问能力
