# Token 运营 FinOps 架构说明

## 1. 文档目标

本文反映当前 `ops-factory` 中 `finops` 模块的实际实现状态，并把已落地能力、数据边界和当前不做的能力明确下来。本文不再描述远期路线图，也不把当前 UI 已移除或当前数据无法支撑的能力写成一阶段交付目标。

当前模块中文名称为 **Token 运营**，英文产品语义使用 **FinOps**。这里的 FinOps 是 Agentic AI 用量运营，不是传统云资源计费、预算或财务结算系统。

## 2. 模块定位

Token 运营是 OpsFactory 面向智能体使用事实的运营分析模块。它通过 gateway 快照接口读取 gateway 管理的 goosed session 数据，围绕 `user`、`agent`、`session`、`model`、`provider` 和 Token 维度回答：

- 当前周期内 Token 总量、输入 Token、输出 Token 和 Session 数是多少。
- 哪些智能体、用户、会话、模型和 Provider 贡献了主要 Token 消耗。
- 用户会话和定时任务会话的 Token 分布如何。
- 当前数据快照来自多少 session DB，是否有跳过或读取失败。

Token 运营不直接执行 Agent，不修改 Agent、Skill、Prompt、Scheduler 或模型路由配置。它也不做价格、预算、扣费、账单、发票、多方对账和权限隔离。

## 3. 当前实现总览

当前实现包含一个独立后端服务和一个前端业务模块：

```text
finops/
web-app/src/app/modules/finops/
web-app/src/services/finopsAPI.ts
```

整体链路：

```text
gateway/users/*/agents/*/data/sessions/sessions.db
        |
        | gateway read-only SQLite scan
        v
GET /gateway/usage/session-snapshot
        |
        v
finops GatewayUsageSnapshotClient
        |
        v
FinOpsSnapshotStore in-memory snapshot
        |
        v
UsageAggregationService
        |
        v
GET /finops/overview  +  paged dimension APIs  +  session message API  +  POST /finops/refresh
        |
        v
web-app /finops Token 运营页面
```

关键实现事实：

- `finops` 是独立 Spring Boot 服务，默认端口 `8097`。
- 服务启动后会刷新一次快照，并按 `finops.scan.refresh-interval-ms` 定时刷新。
- 手动刷新接口为 `POST /finops/refresh`。
- 当前快照存储在进程内存中，由 `FinOpsSnapshotStore` 的 `AtomicReference` 持有。
- FinOps 不直接访问 goosed SQLite，也不需要本地挂载 `gateway/users`；gateway 连接地址与密钥通过 `finops.gateway.*` 配置。
- 前端总览调用 `GET /overview?compare=true`，维度 tab 调用 `/agents`、`/users`、`/sessions`、`/models` 分页接口，会话明细抽屉调用 `/sessions/{sessionId}/messages`，手动刷新调用 `POST /refresh`。
- 后端不暴露 `/recommendations` 和 `/reports/summary`。

## 4. 行业 FinOps 逻辑映射

Token 运营沿用行业 FinOps 的 `Inform -> Optimize -> Operate` 思路，但当前实现重点落在 `Inform`。由于当前数据不足以判断高 Token 是否代表浪费或问题，后端和前端都不提供优化建议接口或报告接口。

### 4.1 Inform：用量透明

已落地：

- 总览 KPI：总 Token、输入 Token、输出 Token、Session 数、活跃用户、活跃智能体。
- Token 趋势：按 `updatedAt` 聚合到日。
- 任务执行负载：按 Session 近似一次任务，展示平均 Token、平均消息数和平均工具返回。
- Session 类型：用户会话 / 定时任务分布。
- 模型消耗分布。
- 主要消耗驱动：按智能体展示 Top 消耗来源。
- 智能体、用户、会话、模型四个维度的 TopN 分析区与分页明细表格。
- 当前周期与上一等长周期对比：Token 与 Session 增长率。

### 4.2 Optimize：暂不落地

当前不提供优化建议能力。原因是：高 Token 不等于问题，尤其在 Agent 复杂任务场景中，Token 多可能是合理结果。只基于 session DB 中的 Token、消息数、输入/输出比例，无法可靠判断任务难度、模型胜任力、业务产出或是否浪费。

因此当前实现移除了：

- `/finops/recommendations`
- `/finops/reports/summary`
- `RecommendationService`
- `FinOpsReportViewService`

后续如果重新引入优化建议，需要同时引入可解释证据、人工确认闭环或业务结果数据，不能仅用高 Token 排名推断问题。

### 4.3 Operate：运营闭环

当前不做：

- 建议状态管理，例如 `accepted`、`dismissed`、`resolved`。
- 派单、审批、自动改配置、自动换模型。
- 预算、价格、账单、成本中心和 chargeback。
- 基于业务结果的价值归因。

## 5. 数据来源与可获得性

### 5.1 扫描路径

主路径：

```text
gateway/users/<userId>/agents/<agentId>/data/sessions/sessions.db
```

兼容历史路径：

```text
gateway/users/<userId>/agents/<agentId>/data/sessions.db
```

`userId` 和 `agentId` 由路径推导：

```text
userId  = gateway/users/<userId>
agentId = gateway/users/<userId>/agents/<agentId>
```

### 5.2 gateway 快照接口与 SQLite 读取

SQLite 读取由 gateway 服务负责，FinOps 通过 HTTP 拉取归一化快照：

```text
GET /gateway/usage/session-snapshot
header: x-secret-key: <gateway secret>
header: x-user-id: <finops gateway user id>
```

gateway 以只读方式打开 SQLite：

```text
jdbc:sqlite:file:<db>?mode=ro
PRAGMA query_only = true
```

读取失败的 DB 会被跳过，并记录到快照响应的 `skippedDbCount` 和 `lastError`。gateway 不修改 goosed session DB，不迁移 schema，不写入 session DB。FinOps 只消费 gateway 响应，不引入 SQLite 驱动，也不读取 gateway 本地文件。

### 5.3 sessions 表

当前读取字段：

```text
id
name
session_type
working_dir
created_at
updated_at
total_tokens
input_tokens
output_tokens
accumulated_total_tokens
accumulated_input_tokens
accumulated_output_tokens
schedule_id
recipe_json
provider_name
model_config_json
goose_mode
thread_id
```

Token 汇总以 `sessions.total_tokens`、`input_tokens`、`output_tokens` 为准。空值按 0 处理。

### 5.4 messages 表

当前把 `messages` 作为辅助信息来源和会话解释数据来源：

```text
id
message_id
session_id
role
content_json
created_timestamp
timestamp
tokens
metadata_json
```

用途：

- 统计 `messageCount`、`userMessageCount`、`assistantMessageCount`。
- 通过 `content_json` 中是否包含工具请求或工具返回统计工具信号。
- 当 `sessions.name` 和 `recipe_json` 都不足以识别会话时，提取第一条用户文本作为短标签。
- 为会话明细抽屉提供消息时间线、角色、内容预览、内容长度、工具名、错误标记、用户/智能体可见性等解释信息。

当前 API 仅按用户点击的单个会话返回消息明细，不在列表接口批量返回完整对话正文，不把自然语言内容发给外部模型做分类、摘要或价值判断。

当前 `messages.tokens` 在 goosed 本地样本和上游实现中通常为空。Token 运营不会伪造逐条消息 Token，也不会把 session 级 Token 强行拆分到 message；前端会明确提示逐条 Token 不可用，并使用会话级 Token、消息结构、工具信号和内容长度辅助排查。

### 5.5 Session 标签生成规则

`SessionUsageRecord.label` 用于在高消耗会话表里帮助识别任务，不是 AI 摘要，也不是审计结论。

生成优先级：

1. 使用 `sessions.name`，但忽略空值和默认 `New Chat`。
2. 使用 `recipe_json.title`，必要时拼接 `recipe_json.description`。
3. 使用最早用户消息中的文本片段。
4. 截断到 120 字符。
5. 仍不可得时使用 `Session`。

## 6. 数据模型与指标口径

### 6.1 核心记录

后端内部统一使用 `SessionUsageRecord` 表示从 session DB 读取出的标准化会话记录，关键字段包括：

```text
id
userId
agentId
name
sessionType
workingDir
createdAt
updatedAt
totalTokens
inputTokens
outputTokens
accumulatedTotalTokens
accumulatedInputTokens
accumulatedOutputTokens
scheduleId
providerName
modelName
gooseMode
threadId
messageCount
userMessageCount
assistantMessageCount
toolResponseCount
label
modelConfig
recipe
```

`SessionUsageRecord` 是内部读取/聚合模型，不直接作为 API DTO 返回。HTTP API 返回脱敏后的 `SessionUsage`，只包含页面需要展示的字段：

```text
id
userId
agentId
name
sessionType
createdAt
updatedAt
totalTokens
inputTokens
outputTokens
scheduleId
providerName
modelName
messageCount
userMessageCount
assistantMessageCount
toolResponseCount
label
```

以下内部字段不通过 API 暴露：

```text
workingDir
gooseMode
threadId
modelConfig
recipe
accumulatedTotalTokens
accumulatedInputTokens
accumulatedOutputTokens
```

### 6.2 聚合指标

当前口径：

```text
totalTokens = sum(session.totalTokens)
inputTokens = sum(session.inputTokens)
outputTokens = sum(session.outputTokens)
sessionCount = count(session)
activeUsers = distinct userId
activeAgents = distinct agentId
activeModels = distinct providerName + "/" + modelName
scheduledSessionCount = count(sessionType = scheduled or scheduleId not blank)
manualSessionCount = sessionCount - scheduledSessionCount
avgTokensPerSession = totalTokens / sessionCount
```

`sessionType` 归一化：

- `scheduleId` 非空时归为 `scheduled`。
- `session_type` 为空时归为 `manual`。
- 其他值转为小写。

前端展示时把 `manual` 与 `user` 归并为“用户会话”，并总是同时展示“用户会话”和“定时任务”，即使其中一项为 0。

### 6.3 周期对比

默认筛选窗口为最近 30 天：

```text
endTime = now
startTime = now - 30 days
```

当 `compare=true` 时，上一周期为等长窗口：

```text
current:  [startTime, endTime)
previous: [startTime - duration, startTime)
```

当前计算：

```text
tokenDelta = current.totalTokens - previous.totalTokens
tokenGrowthRate = tokenDelta / previous.totalTokens
sessionDelta = current.sessionCount - previous.sessionCount
sessionGrowthRate = sessionDelta / previous.sessionCount
```

上一周期分母为 0 时，增长率返回 `null`。

### 6.4 维度聚合

当前已实现维度：

| 维度 | 输出 |
| --- | --- |
| Agent | activeUsers、sessionCount、total/input/outputTokens、avgTokensPerSession、scheduledSessionCount、highTokenSessionCount |
| User | activeAgents、sessionCount、total/input/outputTokens、avgTokensPerSession、lastActiveAt、topAgent |
| Session | 高 Token Session，可分页 |
| Model / Provider | providerName、modelName、activeUsers、activeAgents、sessionCount、total/input/outputTokens、avgTokensPerSession |
| Distribution | 按 sessionType、providerName 等字段分布，输出 totalTokens 与 percentage |

`highTokenSessionCount` 当前按该 Agent 内 session totalTokens 的 P90 阈值计算。

## 7. 后端架构

### 7.1 技术栈

当前 `finops/pom.xml`：

- Java 21
- Spring Boot 3.3.12
- `spring-boot-starter-web`
- `spring-boot-starter-validation`
- `spring-boot-starter-actuator`
- Maven 打包独立 jar，最终包名 `finops.jar`

SQLite 驱动位于 gateway 侧，用于读取 gateway 管理的 goosed session DB；FinOps 服务通过 gateway HTTP 接口获取快照。

### 7.2 服务分层

```text
finops/src/main/java/com/huawei/opsfactory/finops/
├── FinOpsApplication.java
├── api
│   └── FinOpsController.java
├── common
│   └── ApiExceptionHandler.java
├── config
│   ├── FinOpsProperties.java
│   ├── FinOpsSecretFilter.java
│   └── WebConfig.java
├── model
│   └── FinOpsModels.java
├── service
│   ├── GatewayUsageSnapshotClient.java
│   ├── UsageAggregationService.java
│   └── UsageIngestionService.java
└── store
    └── FinOpsSnapshotStore.java
```

职责：

- `GatewayUsageSnapshotClient`：调用 gateway 快照 API。
- `FinOpsSnapshotStore`：保存当前进程内快照和快照状态。
- `UsageIngestionService`：启动刷新、定时刷新、手动刷新入口。
- `UsageAggregationService`：筛选、汇总、趋势、分布和维度聚合。
- `FinOpsController`：暴露 HTTP API。

### 7.3 配置

`finops/src/main/resources/application.yaml`：

```yaml
spring:
  application:
    name: finops
  config:
    import: "optional:file:${CONFIG_PATH:./config.yaml}"

management:
  endpoints:
    web:
      exposure:
        include: health,info

server:
  port: 8097
```

`finops/config.yaml.example`：

```yaml
server:
  port: 8097

finops:
  secret-key: "${FINOPS_SECRET_KEY:}"
  cors-origin: "http://127.0.0.1:5173"
  gateway:
    base-url: "http://127.0.0.1:3000"
    secret-key: "${GATEWAY_SECRET_KEY:}"
    user-id: "${FINOPS_GATEWAY_USER_ID:}"
    timeout-ms: 30000
  scan:
    refresh-interval-ms: 300000
    refresh-on-startup: true
```

当前注意事项：

- `gateway.base-url` 是 FinOps 访问 gateway 的服务地址，允许两者部署在不同服务器。
- `gateway.secret-key` 应与 gateway 的 `x-secret-key` 配置一致。
- `gateway.user-id` 是 FinOps 调用 gateway 时使用的 `x-user-id`，与 web-app 调用 gateway 的用户上下文机制保持一致；建议部署时显式配置为壳应用传入的当前用户或约定的运行用户。
- `refresh-interval-ms` 和 `refresh-on-startup` 控制 FinOps 拉取快照的频率和启动行为。

### 7.4 鉴权、CORS 和健康检查

当前保护模型：

- `/finops/**` 业务 API 需要 `x-secret-key`。
- `OPTIONS` 放行。
- `/actuator/**` 放行，用于健康检查。
- CORS 来源来自 `finops.cors-origin`，并自动兼容 `localhost` 与 `127.0.0.1`。
- 不复用 gateway 用户鉴权，不做用户级权限隔离。
- 前端通过运行时配置读取 `FINOPS_SECRET_KEY`，并在浏览器请求中以 `x-secret-key` 发送。该密钥只能作为内部工具的共享访问控制使用，不能视为对终端用户保密的服务端凭据；生产部署应由反向代理、内网边界或更完整的身份认证承担外部访问控制。

### 7.5 API

当前后端暴露：

```text
GET  /finops/overview
GET  /finops/agents
GET  /finops/agents/{agentId}
GET  /finops/users
GET  /finops/users/{userId}
GET  /finops/sessions
GET  /finops/sessions/{sessionId}?userId=<userId>&agentId=<agentId>
GET  /finops/sessions/{sessionId}/messages
GET  /finops/models
POST /finops/refresh
```

筛选参数：

```text
startTime
endTime
agentId
userId
sessionType
providerName
modelName
compare
page
size
```

`page` 从 `1` 开始，`size` 默认 `25`，最大 `100`。分页列表返回：

```text
snapshotStatus
items
page
size
totalItems
totalPages
```

`GET /finops/sessions/{sessionId}` 和 `GET /finops/sessions/{sessionId}/messages` 额外要求：

```text
userId
agentId
```

`GET /finops/overview` 额外返回 `taskExecutionLoad`：

```text
avgTokensPerTask
avgMessagesPerTask
avgToolResponsesPerTask
```

这里的“任务”按 Session 近似。`taskExecutionLoad` 只返回总览页实际展示的平均 Token、平均消息数和平均工具返回；任务数复用顶部 Session KPI，不单独返回。

原因是不同用户或智能体下可能存在相同的 `sessionId`。该接口返回：

```text
snapshotStatus
session
stats
capabilities
messages
```

其中 `capabilities.messageTokenAvailable=false` 表示当前明细没有可靠的逐条消息 Token。

当前前端使用：

```text
GET  /finops/overview?compare=true
GET  /finops/agents?page=1&size=25
GET  /finops/users?page=1&size=25
GET  /finops/sessions?page=1&size=25
GET  /finops/sessions/{sessionId}/messages?userId=<userId>&agentId=<agentId>
GET  /finops/models?page=1&size=25
POST /finops/refresh
```

### 7.6 编排接入

已接入根编排：

- 根 `scripts/ctl.sh` 增加 `finops` 组件。
- `ENABLE_FINOPS` 默认 `true`，可通过 `ENABLE_FINOPS=false` 跳过。
- `finops` 是 optional service，启动失败不阻断强依赖服务。
- `startup all` 中在 `operation-intelligence` 之后、`exporter` 之前启动 `finops`。
- `shutdown all` 会停止 `finops`。
- `status all` 会检查 `finops`。

`finops/scripts/ctl.sh` 支持：

```text
startup
shutdown
status
restart
--foreground
--background
```

行为：

- 构建 `target/finops.jar`。
- 后台启动写入 `finops/logs/finops.pid`。
- 日志写入 `finops/logs/finops.log`。
- 健康检查 `http://127.0.0.1:8097/actuator/health`。

`control-center/config.yaml.example` 已加入 FinOps 受管服务配置，用于在控制中心展示服务健康和日志位置。

## 8. 前端架构

### 8.1 模块结构

当前结构：

```text
web-app/src/app/modules/finops/
├── module.ts
├── hooks
│   └── useFinOps.ts
├── pages
│   └── FinOpsPage.tsx
└── styles
    └── finops.css

web-app/src/services/finopsAPI.ts
```

路由和导航：

```text
route: /finops
module id: finops
nav group: business
titleKey: sidebar.finops
icon: finops
```

运行时配置：

```text
finopsServiceUrl -> runtime.FINOPS_URL
finopsSecretKey -> runtime.FINOPS_SECRET_KEY
```

`web-app/config.standalone.json.example` 和 `web-app/config.embed.json.example` 已包含：

```json
"finopsServiceUrl": "http://127.0.0.1:8097",
"finopsSecretKey": ""
```

### 8.2 页面结构

当前页面为单路由标签工作台：

```text
Token 运营
├── 总览
├── 智能体
├── 用户
├── 会话
└── 模型
```

已移除：

- 优化建议 tab
- 报告 tab
- 搜索框

移除原因：

- 当前建议主要基于高 Token 和简单比例规则，高 Token 不足以证明问题。
- 报告页没有形成前端主工作流，且后端报告接口已移除。
- 搜索框当前没有形成可靠筛选闭环。

当前只保留会话页的右侧消息抽屉。原因是会话列表本身只能说明某个 session Token 较高，不能解释高 Token 来自多轮对话、工具调用、长上下文还是大段工具返回。抽屉定位为“会话消耗解释”，不做合理/不合理判断。

### 8.3 总览页

总览页展示：

- 6 个 `StatCard`：总 Token、输入 Token、输出 Token、Session 数、活跃 User、活跃 Agent。
- Token 趋势：最多展示最近 14 个日 bucket。
- 任务执行负载：按 Session 近似一次任务，展示平均 Token、平均消息数和平均工具返回。
- Session 类型：用户会话 / 定时任务 donut。
- 模型消耗分布：主维度展示模型名，Provider 作为来源说明。
- 用户消耗分布：Top user 列表。

页面顶部展示数据范围：

```text
数据范围 近 30 天快照 刷新于 <time>，源 DB <n> 个，跳过 <n> 个
```

### 8.4 维度页

智能体、用户、会话、模型四个页统一采用：

```text
轻量分析头
  ├── 标题与说明
  ├── 4 个紧凑指标
  └── 细比例条
主表格
结果数
```

不再在每个 tab 重复总览 6 个 KPI，也不再使用大号排名卡或大面积图表卡。

#### 智能体页

分析头：

- 智能体数
- Top 1 占比
- 平均每智能体
- 用户覆盖
- Top 3 / 其他 Token 细比例条

表格列：

```text
智能体
用户数
会话数
Token
输入
输出
平均 Token
定时任务
```

#### 用户页

分析头：

- 活跃 User
- Top 1 占比
- 平均每用户
- 人均智能体
- 多智能体用户 / 单智能体用户细比例条

表格列：

```text
用户
智能体数
会话数
Token
输入
输出
平均 Token
最近活跃
主要智能体
```

#### 会话页

分析头：

- Session 数
- 平均每会话
- 最高单会话
- 输入占比
- 用户会话 / 定时任务细比例条

表格列：

```text
会话
用户
智能体
类型
Provider
Model
Token
输入
输出
消息数
更新时间
```

点击会话行会打开右侧抽屉，展示：

- 会话基础信息：用户、智能体、模型、更新时间。
- 会话级指标：总 Token、输入 Token、输出 Token、消息数。
- 数据能力提示：当 `messages.tokens` 不可用时，提示逐条 Token 不可用。
- 解释线索：输入/输出、用户/助手/工具消息构成、最长内容长度和预览。
- 消息时间线：角色、时间、内容预览、内容长度、工具请求/工具返回、工具名、错误标记、用户/智能体可见性。
- 角色筛选：全部、用户、助手、工具。

抽屉不展示推断型建议，不判断 Token 是否浪费，不做模型替换结论。

#### 模型页

分析头：

- Provider 数
- Model 数
- Top Provider 占比
- 平均每模型
- Top Provider Token 细比例条

表格列：

```text
Provider
Model
用户数
智能体数
会话数
Token
输入
输出
平均 Token
```

### 8.5 UI 复用与边界

当前复用平台组件：

```text
PageHeader
Button
ListWorkbench
ListToolbar
ListResultsMeta
StatCard
SectionCard
AnalyticsTableCard
```

可见文案在 `web-app/src/i18n/zh.json` 和 `web-app/src/i18n/en.json` 中维护。

FinOps 模块代码位于 `web-app/src/app/modules/finops`，不直接导入其他业务模块。

## 9. 当前不纳入范围

以下能力当前不做：

- 价格、预算、金额估算、计费、扣费、发票、多方对账。
- 用户级权限隔离。
- 语义缓存、缓存命中率、重复会话识别。
- 自动模型路由、模型替换、模型降级建议。
- 基于任务难度或模型胜任力的判断。
- 业务结果价值归因。
- 实时控额或请求级 metering gateway。
- 自动修改 Agent、Prompt、Skill、Scheduler 或模型配置。
- 优化建议工作流，包括前端 tab 和后端 `/recommendations` API。
- 报告页、报告 API 或文件生成。

## 10. 当前实现限制

- 快照为内存态，服务重启后需要重新从 gateway 拉取。
- gateway 当前每次快照请求会全量扫描匹配的 goosed session DB；FinOps 当前每次刷新会把完整 session 和 message 快照放入单个内存引用中。一阶段适合内部运营分析和中等规模数据集，建议把单次快照控制在数万级 session、数十万级 message 以内；超过该量级后应引入变更检测、增量同步、分页持久化或外部分析存储。
- 维度页具备 TopN 分析区和分页明细，但暂不提供搜索、筛选或排序切换；仅会话页提供消息解释抽屉。
- 后端不再暴露 recommendation/report API。
- 会话标签是短标识，不是摘要，不保证能准确描述任务全貌。
- 高 Token 只能表示用量归属和排查入口，不能直接判断浪费、质量问题或模型不适配。
- 当前逐条消息 Token 通常不可用，消息抽屉以内容结构和工具信号辅助解释，而不是计算消息级 Token 成本。

## 11. 验收标准

当前实现应满足：

- `finops` 可作为独立服务启动，并通过 `/actuator/health` 健康检查。
- 能只读扫描 `gateway/users/*/agents/*/data/sessions/sessions.db` 和兼容历史 `data/sessions.db`。
- 能生成包含 `snapshotStatus` 的当前进程内快照。
- 能按 user、agent、session、model、provider 聚合 Token。
- 能区分用户会话和定时任务。
- 能针对单个会话展示消息明细抽屉，且不伪造逐条消息 Token。
- `/finops` 前端页面能展示总览、智能体、用户、会话、模型五个标签页。
- 页面不展示价格、预算、计费、权限隔离或模型替换判断。
- 所有前端可见文案走 i18n。
