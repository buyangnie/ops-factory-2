# operation-intelligence 排障指南

## 1. 文档目标

本文档用于帮助开发、测试和运维人员快速定位 `operation-intelligence` 的常见问题。

重点覆盖：

- 服务启动失败
- DV 连接失败（SSL、认证、超时）
- 数据采集异常
- 健康曲线页面无数据
- 常用日志查看命令

如果需要了解接口、评分算法和配置细节，请结合以下文档阅读：

- [docs/architecture/operation-intelligence-architecture.md](../architecture/operation-intelligence-architecture.md)
- [docs/architecture/operation-intelligence-integration.md](../architecture/operation-intelligence-integration.md)

## 2. 运行入口与关键配置

### 2.1 启停入口

默认通过脚本运行：

```bash
cd operation-intelligence
./scripts/ctl.sh startup --background
./scripts/ctl.sh status
./scripts/ctl.sh restart --background
./scripts/ctl.sh shutdown
```

### 2.2 配置入口

`operation-intelligence` 的实际运行时配置入口是：

```bash
operation-intelligence/config.yaml
```

当前 Spring Boot 会直接加载该文件。脚本只负责：

- 发现配置文件位置
- 进程启停
- health check
- 少量显式环境变量覆盖

重点配置包括：

- `operation-intelligence.server.*`
- `operation-intelligence.qos.*`
- `operation-intelligence.dv.*`
- `logging.level.*`

环境变量 `OI_CONFIG_PATH` 可用于显式指定配置文件路径。

### 2.3 关键日志文件

应用主日志：

```bash
operation-intelligence/logs/operation-intelligence.log
```

当前设计下，`operation-intelligence.log` 是唯一主业务日志文件，由应用内 Logback 负责写入与滚动。

后台启动时还可能看到辅助输出捕获文件：

```bash
operation-intelligence/logs/operation-intelligence-stdout-stderr.log
```

该文件只用于保留后台进程的 `stdout/stderr` 输出，不是常规业务排障入口。默认先看 `operation-intelligence.log`；只有启动早期异常、日志框架未接管前输出、或第三方库直接写标准错误时再看它。

代码层说明：

- 当前代码层统一使用 `SLF4J API`
- 运行时后端仍为 `Logback`
- 后续新增或修改日志代码时，应继续遵守该约束

## 3. 基础检查

### 3.1 先看服务是否存活

```bash
cd operation-intelligence
./scripts/ctl.sh status
curl -fsS http://127.0.0.1:8096/actuator/health
```

### 3.2 看端口占用

```bash
lsof -i :8096
```

如果端口被占用，先不要继续查业务接口。

### 3.3 看构建是否正常

```bash
cd operation-intelligence
mvn test
```

如果本地修改后连构建都不过，先处理编译或测试问题。

## 4. 日志怎么看

### 4.1 关键字段

当前 `operation-intelligence` 日志默认会带：

- `service`
- `environment`
- `thread`
- `logger`

其中 `environment` 是排查数据采集问题的第一关键字段。

### 4.2 按模块搜索

```bash
cd operation-intelligence
rg "QosDataScheduler" logs/operation-intelligence.log
rg "DvClient" logs/operation-intelligence.log
rg "DvAuthService" logs/operation-intelligence.log
rg "QosCalculationService" logs/operation-intelligence.log
rg "JsonFileStore" logs/operation-intelligence.log
```

### 4.3 access log

每个 HTTP 请求默认会输出一条 access log，包含：

- method
- path
- status
- durationMs
- environment（可识别时）

所以如果一个请求完全没有业务日志，至少也应该能先在 access log 中确认它是否真正到达了 `operation-intelligence`。

## 5. 如何临时提级日志

### 5.1 直接改配置文件

在 `operation-intelligence/config.yaml` 中修改：

```yaml
logging:
  level:
    root: INFO
    com.huawei.opsfactory.operationintelligence: DEBUG
```

然后重启：

```bash
cd operation-intelligence
./scripts/ctl.sh restart --background
```

### 5.2 通过环境变量显式覆盖

如果只是想临时调日志级别，建议优先直接改 `config.yaml`，不要继续扩展脚本层的配置翻译逻辑。

## 6. 常见问题排查

### 6.1 服务启动失败

优先检查：

1. `./scripts/ctl.sh status`
2. `logs/operation-intelligence.log`
3. `logs/operation-intelligence-stdout-stderr.log`
4. `operation-intelligence/config.yaml`
5. 端口是否被占用

重点关键词：

- `Failed to start`
- `BindException`
- `port ... is occupied`
- `Scheduling`

常见原因：

- 端口 8096 被其他进程占用
- `config.yaml` 配置格式错误
- `operation-intelligence.qos.enabled` 配置项缺失或类型错误
- `operation-intelligence.dv.environments` 配置格式错误

### 6.2 DV 连接失败排查

DV 连接问题通常分为三类：SSL、认证、超时。

#### SSL 问题

现象：

- 日志中出现 `SSLHandshakeException`
- 日志中出现 `PKIX path building failed`
- 日志中出现 `certificate verify failed`

排查：

```bash
rg "SSL|certificate|handshake|PKIX" logs/operation-intelligence.log
```

处理：

- 确认 DV 系统 `baseUrl` 使用正确的协议（`https://`）
- 检查 DV 系统证书是否为可信 CA 签发
- 如需使用自签名证书，将证书导入 JVM truststore，或将 `sslVerify` 临时设为 `false`（仅测试环境）

#### 认证问题

现象：

- 日志中出现 `401 Unauthorized`
- 日志中出现 `403 Forbidden`
- 日志中出现 `authentication failed`

排查：

```bash
rg "401|403|auth|token|Unauthorized|Forbidden" logs/operation-intelligence.log
```

处理：

- 检查 `operation-intelligence.dv.environments[].token` 是否正确
- 如果使用环境变量引用 `${DV_PRODUCTION_TOKEN}`，确认环境变量已设置
- 检查 token 是否已过期

#### 超时问题

现象：

- 日志中出现 `SocketTimeoutException`
- 日志中出现 `connect timed out`
- 日志中出现 `read timed out`

排查：

```bash
rg "timeout|timed out|SocketTimeout" logs/operation-intelligence.log
```

处理：

- 检查 DV 系统网络连通性：`curl -v <dv-base-url>`
- 检查 `connectTimeout` 和 `readTimeout` 配置是否合理
- 检查 DV 系统负载是否过高导致响应缓慢
- 检查是否有防火墙或代理阻断连接

### 6.3 数据采集异常排查

现象：

- 日志中无采集任务执行记录
- `data/raw/` 目录下无新文件生成
- 采集任务执行但报错

排查：

```bash
# 检查 QoS 是否启用
rg "qos.enabled" config.yaml

# 检查调度任务是否执行
rg "QosDataScheduler" logs/operation-intelligence.log

# 检查采集是否成功
rg "collect|fetch|DvClient" logs/operation-intelligence.log

# 检查是否有写入错误
rg "JsonFileStore|write|failed" logs/operation-intelligence.log

# 检查数据目录是否有文件
ls -la data/raw/
ls -la data/normalize/
ls -la data/detail/
```

常见原因：

- `operation-intelligence.qos.enabled` 设为 `false`
- `cron` 表达式配置错误
- DV 连接失败（参见 6.2）
- `data/` 目录无写入权限
- 磁盘空间不足

### 6.4 健康曲线页面无数据排查

现象：

- 前端健康曲线页面显示空白或无数据
- 前端请求返回空结果或错误

排查步骤：

1. 先确认服务是否正常运行：

```bash
curl -fsS http://127.0.0.1:8096/actuator/health
```

2. 确认环境列表是否能返回：

```bash
curl http://127.0.0.1:8096/operation-intelligence/qos/getEnvironments
```

3. 确认指定环境是否有数据：

```bash
curl -X POST http://127.0.0.1:8096/operation-intelligence/qos/getHealthIndicator \
  -H 'Content-Type: application/json' \
  -d '{
    "environment": "production",
    "startTime": "2026-05-01T00:00:00Z",
    "endTime": "2026-05-08T00:00:00Z"
  }'
```

4. 检查前端配置：

```bash
cat web-app/config.json | rg operationIntelligenceServiceUrl
```

确认 `operationIntelligenceServiceUrl` 指向正确的地址。

5. 检查数据目录是否有文件：

```bash
ls -la operation-intelligence/data/normalize/
```

常见原因：

- `operation-intelligence` 服务未启动或已崩溃
- `web-app/config.json` 中 `operationIntelligenceServiceUrl` 配置错误
- 请求的 `environment` 名称与配置中的环境名称不匹配
- 时间范围内确实无数据（采集任务尚未运行过或数据已过期清理）
- 数据采集任务失败（参见 6.3）

## 7. 常用日志查看命令

### 7.1 实时跟踪日志

```bash
tail -f operation-intelligence/logs/operation-intelligence.log
```

### 7.2 查看最近错误

```bash
grep -RniE 'error|exception|failed|timeout|refused' \
  operation-intelligence/logs
```

### 7.3 按模块查看

```bash
# 查看调度任务日志
rg "QosDataScheduler" operation-intelligence/logs/operation-intelligence.log

# 查看 DV 交互日志
rg "DvClient|DvAuthService" operation-intelligence/logs/operation-intelligence.log

# 查看评分计算日志
rg "QosCalculationService" operation-intelligence/logs/operation-intelligence.log

# 查看存储操作日志
rg "JsonFileStore" operation-intelligence/logs/operation-intelligence.log
```

### 7.4 查看采集周期日志

```bash
rg "collect|scheduler|rotation|retention" operation-intelligence/logs/operation-intelligence.log
```

## 8. 推荐排障顺序

建议按这个顺序走：

1. 先确认 `operation-intelligence` 是否正常启动
2. 再确认当前 `config.yaml` 是否是实际生效配置
3. 确认 DV 环境配置是否正确
4. 在 `operation-intelligence.log` 中按模块关键词搜索
5. 如果是 DV 连接问题，按 SSL/认证/超时三类分别排查
6. 如果是数据问题，检查 `data/` 目录下是否有文件
7. 如果是前端无数据，先确认服务可用，再确认前端配置与请求参数
8. 必要时临时调高 `logging.level.*`
9. 必要时短时间打开 DEBUG 级别查看完整请求/响应细节

## 9. 后续变更时需要同步更新本文档

以下变更需要同步维护本文档：

- `operation-intelligence/config.yaml` 结构变化
- 日志文件路径变化
- `operation-intelligence.qos.*` 开关变化
- DV 集成相关配置变化
- 采集调度或存储策略变化
