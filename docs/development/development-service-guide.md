# 独立服务开发指导

本文档说明如何在 ops-factory 仓库中新增一个独立领域服务。涵盖目录结构、Maven 配置、Spring Boot 应用、日志规范、编排脚本、前端集成、测试和文档要求。

## 1. 概述

### 1.1 适用场景

当一个功能领域具备以下特征时，应考虑创建独立服务：

- 与 gateway 核心职责（鉴权、路由、会话、文件、进程管理）无直接耦合
- 拥有独立的数据存储和业务逻辑
- 需要独立部署、扩缩容或按需启停
- 可作为可选增强能力，核心链路不依赖它

### 1.2 服务分类

| 分类 | 说明 | 启动策略 | 示例 |
| --- | --- | --- | --- |
| 必选服务 | 核心链路依赖 | 编排器默认启动 | knowledge-service、skill-market |
| 可选服务 | 增强能力，不影响核心链路 | 通过环境变量 toggle 控制 | operation-intelligence、prometheus-exporter |

新建服务应优先设计为可选服务，除非有明确的强依赖理由。

## 2. 服务目录结构

标准目录模板：

```text
<service-name>/
├── pom.xml                         # Maven 构建配置
├── config.yaml.example             # 配置文件模板（入库）
├── config.yaml                     # 运行时配置（gitignored）
├── README.md                       # 服务说明
├── scripts/
│   └── ctl.sh                      # 服务控制脚本
├── logs/                           # 运行日志（gitignored）
├── data/                           # 运行时数据（gitignored，按需）
└── src/
    ├── main/
    │   ├── java/com/huawei/opsfactory/<servicename>/
    │   │   ├── <ServiceName>Application.java    # Spring Boot 主类
    │   │   ├── config/                          # 配置属性类
    │   │   ├── controller/                      # REST 控制器
    │   │   ├── service/                         # 业务逻辑
    │   │   ├── common/                          # 通用工具、异常处理、日志
    │   │   └── ...                              # 领域子包
    │   └── resources/
    │       ├── application.yml                  # Spring Boot 配置
    │       ├── application-test.yaml            # 测试 profile
    │       └── logback-spring.xml                # 日志配置
    └── test/
        └── java/com/huawei/opsfactory/<servicename>/
            └── ...                              # 测试类
```

**包命名规范**：`com.huawei.opsfactory.<servicename>`，使用全小写无分隔符形式（如 `operationintelligence`、`businessintelligence`）。

**参照示例**：`business-intelligence/`、`operation-intelligence/`

## 3. Maven 配置

### 3.1 pom.xml 模板

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
    http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.huawei.opsfactory</groupId>
    <artifactId><service-name></artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <packaging>jar</packaging>

    <properties>
        <java.version>21</java.version>
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <spring-boot.version>3.3.12</spring-boot.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-dependencies</artifactId>
                <version>${spring-boot.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <!-- Web 层：WebMVC 或 WebFlux（二选一） -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webflux</artifactId>
            <exclusions>
                <exclusion>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-logging</artifactId>
                </exclusion>
            </exclusions>
        </dependency>
        <!-- 健康检查 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
            <exclusions>
                <exclusion>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-logging</artifactId>
                </exclusion>
            </exclusions>
        </dependency>
        <!-- 日志：统一使用 Logback -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-logging</artifactId>
        </dependency>
        <!-- 测试 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <finalName><service-name></finalName>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <version>${spring-boot.version}</version>
                <configuration>
                    <mainClass>com.huawei.opsfactory.<servicename>.<ServiceName>Application</mainClass>
                </configuration>
                <executions>
                    <execution>
                        <goals><goal>repackage</goal></goals>
                    </execution>
                </executions>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.11.0</version>
                <configuration>
                    <source>${java.version}</source>
                    <target>${java.version}</target>
                    <encoding>${project.build.sourceEncoding}</encoding>
                    <parameters>true</parameters>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

### 3.2 WebMVC vs WebFlux 选型

| 场景 | 推荐 | 原因 |
| --- | --- | --- |
| 常规 CRUD / 表单处理 | WebMVC（`spring-boot-starter-web`） | 简单直接，同步编程模型 |
| 需要 WebClient / Netty SSL / 流式响应 | WebFlux（`spring-boot-starter-webflux`） | 原生支持响应式，避免同时引入 servlet 和 reactive 栈 |
| 与外部系统高频 HTTP 交互 | WebFlux | 非阻塞 IO，适合并发调用 |

## 4. Spring Boot 应用

### 4.1 Application 主类

```java
package com.huawei.opsfactory.<servicename>;

import com.huawei.opsfactory.<servicename>.config.<ServiceName>Properties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(<ServiceName>Properties.class)
public class <ServiceName>Application {
    public static void main(String[] args) {
        SpringApplication.run(<ServiceName>Application.class, args);
    }
}
```

### 4.2 配置属性类

使用 `@ConfigurationProperties` 绑定 `config.yaml` 中的服务专属配置段：

```java
@ConfigurationProperties(prefix = "<service-name>")
public class <ServiceName>Properties {
    private String corsOrigin = "*";
    private Logging logging = new Logging();
    // ... 服务专属配置字段

    public static class Logging {
        private boolean accessLogEnabled = true;
        // getters/setters
    }
}
```

**要点**：
- prefix 与服务目录名一致（如 `operation-intelligence`）
- 包含 `corsOrigin` 和 `logging` 是标准做法
- 路径解析方法用于定位 data 目录，默认取 config 文件所在目录下的 `data` 子目录

### 4.3 application.yml

```yaml
server:
  port: <port>

spring:
  application:
    name: <service-name>
  config:
    import: optional:file:${<SERVICE_NAME>_CONFIG_PATH:./config.yaml}

logging:
  config: classpath:logback-spring.xml

management:
  endpoints:
    web:
      exposure:
        include: health,info
```

**端口分配**：按现有顺序递增（gateway=3000, knowledge=8092, bi=8093, control-center=8094, skill-market=8095, operation-intelligence=8096）。

### 4.4 application-test.yaml

测试 profile 使用随机端口和最小配置：

```yaml
server:
  port: 0
spring:
  application:
    name: <service-name>
<service-name>:
  cors-origin: "*"
  logging:
    access-log-enabled: false
```

## 5. 日志规范

> 关键规则来自 [logging-guidelines.md](./logging-guidelines.md)，此处内联核心要求。

### 5.1 统一日志 API

- 所有日志使用 **SLF4J API**（`org.slf4j.Logger` + `org.slf4j.LoggerFactory`）
- 禁止直接依赖 Logback 专有 API
- 运行时使用 Logback 作为 backend（通过 `spring-boot-starter-logging`）

### 5.2 MDC 上下文

以下标识必须通过 MDC 传递，而非手动拼接到日志消息中：

- `requestId`：每个 HTTP 请求的唯一标识

### 5.3 RequestLoggingFilter

每个服务必须实现请求日志过滤器，负责设置 MDC、记录 access log。

**WebMVC 版本**（使用 `spring-boot-starter-web` 的服务）：

```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private final <ServiceName>Properties properties;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = resolveRequestId(request);
        response.setHeader("X-Request-Id", requestId);
        long startedAt = System.currentTimeMillis();
        MDC.put("requestId", requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            try {
                if (properties.getLogging().isAccessLogEnabled()) {
                    log.info("HTTP {} {} completed status={} durationMs={}",
                            request.getMethod(), request.getRequestURI(),
                            response.getStatus(), System.currentTimeMillis() - startedAt);
                }
            } finally {
                MDC.remove("requestId");
            }
        }
    }
}
```

**WebFlux 版本**（使用 `spring-boot-starter-webflux` 的服务）：

```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private final <ServiceName>Properties properties;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String requestId = resolveRequestId(exchange);
        exchange.getResponse().getHeaders().set("X-Request-Id", requestId);
        long startedAt = System.currentTimeMillis();
        MDC.put("requestId", requestId);
        return chain.filter(exchange)
                .doFinally(signalType -> {
                    try {
                        if (properties.getLogging().isAccessLogEnabled()) {
                            log.info("HTTP {} {} completed status={} durationMs={}",
                                    exchange.getRequest().getMethod(),
                                    exchange.getRequest().getURI().getPath(),
                                    exchange.getResponse().getStatusCode(),
                                    System.currentTimeMillis() - startedAt);
                        }
                    } finally {
                        MDC.remove("requestId");
                    }
                });
    }
}
```

**要点**：
- 从请求头 `X-Request-Id` 读取，若无则生成 UUID
- 响应头中回传 `X-Request-Id`，便于调用方链路追踪
- access log 通过 `logging.accessLogEnabled` 开关控制
- access log 格式统一为 `HTTP {method} {path} completed status={status} durationMs={duration}`

### 5.4 logback-spring.xml 模板

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Configuration status="WARN" monitorInterval="30">
    <Properties>
        <Property name="APP_NAME"><service-name></Property>
        <Property name="LOG_DIR">${sys:user.dir}/logs</Property>
        <Property name="LOG_PATTERN">%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [service=${APP_NAME} requestId=%X{requestId}] [%t] %logger{36} - %msg%n%throwable</Property>
    </Properties>

    <Appenders>
        <Console name="Console" target="SYSTEM_OUT">
            <PatternLayout pattern="${LOG_PATTERN}" />
        </Console>

        <RollingFile name="File"
                     fileName="${LOG_DIR}/<service-name>.log"
                     filePattern="${LOG_DIR}/<service-name>-%d{yyyy-MM-dd}-%i.log.gz">
            <PatternLayout pattern="${LOG_PATTERN}" />
            <Policies>
                <TimeBasedTriggeringPolicy interval="1" />
                <SizeBasedTriggeringPolicy size="100MB" />
            </Policies>
            <DefaultRolloverStrategy max="30" />
        </RollingFile>
    </Appenders>

    <Loggers>
        <Logger name="com.huawei.opsfactory.<servicename>" level="${spring:logging.level.com.huawei.opsfactory.<servicename>:-INFO}" />
        <Logger name="org.springframework" level="${spring:logging.level.org.springframework:-WARN}" />

        <Root level="${spring:logging.level.root:-INFO}">
            <AppenderRef ref="Console" />
            <AppenderRef ref="File" />
        </Root>
    </Loggers>
</Configuration>
```

**要点**：
- 日志文件轮转：按天 + 100MB 大小触发，最多保留 30 份
- Pattern 中包含 `requestId=%X{requestId}` 用于链路追踪
- Spring 框架日志默认 WARN 级别，服务代码默认 INFO
- 日志级别可通过 `application.yml` 或环境变量覆盖

### 5.5 日志级别规则

| 级别 | 使用场景 | 示例 |
| --- | --- | --- |
| INFO | 业务摘要 | 启动完成、定时任务开始/完成、外部连接建立 |
| WARN | 可恢复问题 | 请求超时重试、降级处理、配置缺失使用默认值 |
| ERROR | 不可恢复故障 | 认证失败、存储写入异常，附带堆栈 |

**禁止事项**：
- 禁止在日志中输出密码、Token、证书内容等敏感数据
- 禁止使用 `System.out.println` 或 `e.printStackTrace()`
- 禁止在循环体中记录 INFO 级别日志

## 6. 变更范围规范

> 关键规则来自 [change-scope-rules.md](./change-scope-rules.md)，此处内联核心要求。

- **保持 diff 有意图性**：不在功能 PR 中混入无关重构或批量重命名；一个 PR 解决一个明确问题
- **跨服务变更需跨团队协调**：变更同时涉及 `gateway`、`web-app`、domain service 时，PR 描述中需说明依赖链和影响范围
- **配置字段同步更新**：新增配置字段时，必须在同一个 PR 中更新代码、`config.yaml.example` 和相关文档
- **显式标记破坏性变更**：不将兼容性破坏隐藏在实现细节中；若有 breaking change，PR 标题和描述必须明确标注
- **AGENTS.md 视为强制规则**：服务级 `AGENTS.md` 中的规则等价于代码审查红线

## 7. 协作规范

> 关键规则来自 [collaboration-announcement.md](./collaboration-announcement.md)，此处内联核心要求。

- **API / SSE / Auth / Config / UI 变更必须在同一个 PR 中更新**：涉及接口契约、认证方式、配置结构或用户界面的变更，相关文档和代码必须同步更新
- **新配置字段三同步**：代码 + `config.yaml.example` + 文档（`docs/architecture/` 或 `docs/development/`）
- **文档结构遵循仓库约定**：
  - `README.md` — 服务介绍与快速开始
  - `AGENTS.md` — 贡献者必读的强制规则（短小精悍）
  - `docs/architecture/*` — 系统边界、API 契约、进程管理
  - `docs/development/*` — 开发规范、UI 约束、测试要求
  - `docs/operations/*` — 运维排障、故障恢复
- **测试范围与变更匹配**：Vitest 覆盖前端，JUnit 覆盖 Java，跨服务场景用集成测试

## 8. UI 开发规范

> 关键规则来自 [ui-guidelines.md](./ui-guidelines.md)，此处内联核心要求。

- **保留现有交互模型**：当前路由驱动的 shell、侧边栏导航、右侧面板（right-panel）模式是新页面的默认交互基线
- **页面模式复用**：
  - 列表/详情页：主导航 + 批量操作在主栏，详情检查在右侧面板
  - 配置页：分段卡片（section cards）+ 短标题 + 聚焦操作 + 紧凑布局
  - 工作台（workbench）：控件在顶部，结果以结构化卡片展示，详情用右侧面板或模态框
  - 比较页：共享面板结构，对齐元数据展示
- **UI 组织结构**：
  - `web-app/src/app/platform/*`：共享 shell、导航、providers、聊天、预览、渲染器、面板、运行时辅助、可复用 UI 基础组件
  - `web-app/src/app/modules/<module>/*`：业务模块的 pages、components、hooks、styles
  - 根 `src/` 仅保留入口文件和跨切面资源（`App.tsx`、`main.tsx`、assets、config、i18n、types、utils）
- **国际化（i18n）**：所有用户可见文本必须通过 i18n 机制管理，`en.json` 和 `zh.json` 同步更新，使用稳定的 namespace 风格 key
- **响应式设计**：新增顶层页面或主要工作流必须支持响应式布局
- **视觉一致性**：复用既有间距、圆角、边框、空状态、标签、按钮样式，不因功能而重新发明视觉语言
- **分析类 UI 复用**：KPI 概览卡片、图表头部图例、饼图分布卡片、状态单元格等应标准化在 `app/platform/*` 中跨模块复用

## 9. 前端协作规范

> 关键规则来自 [webapp-collaboration.md](./webapp-collaboration.md)，此处内联核心要求。

- **两层组织**：`app/platform/*`（共享运行时能力）+ `app/modules/*`（业务模块），模块间禁止直接引用
- **模块自动发现**：通过 `import.meta.glob('../modules/**/module.ts')` 在 ModuleLoader 中自动加载
- **变更风险分级**：
  - 低风险：仅修改模块自身目录（`module.ts`、`pages/*`、`components/*`、`hooks/*`、`styles/*`）
  - 中风险：修改 `app/platform/ui/*`、`app/platform/chat/*` 等 — 需在 PR 中说明复用影响
  - 高风险：修改 `app/platform/providers/*`、`app/platform/navigation/*`、`App.tsx` — 需谨慎审查
- **结构变更验证**：前端结构性变更后必须运行 `cd web-app && npm run check:boundaries` + `npm run test:basic` + `npm run build`

## 10. Review 规范

> 关键规则来自 [review-checklist.md](./review-checklist.md)，此处内联核心要求。

- **单一清晰问题**：每个 PR / Review 聚焦一个明确问题，不捆绑无关重构
- **前端经由 Gateway 调用**：前端不绕过 Gateway 直接访问后端服务（domain service 除外，它们由前端通过各自 URL 直接调用）
- **路由前缀 / Auth 头 / 流式契约保持兼容**：变更不能破坏已有的路由前缀约定、认证头格式、SSE/流式响应契约
- **新 config key 更新正确的 config example 文件**：每个服务有自己的 `config.yaml.example`，配置变更需同步到对应服务的 example 文件
- **AGENTS.md 视为强制规则**：新增不可协商的规则时更新服务级 `AGENTS.md`

## 11. CORS 与 Web 配置

### 11.1 WebMVC 版本（使用 `CorsFilter`）

```java
@Configuration
public class WebConfig {

    private final <ServiceName>Properties properties;

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        String corsOrigin = properties.getCorsOrigin();
        if (StringUtils.hasText(corsOrigin)) {
            String[] origins = StringUtils.commaDelimitedListToStringArray(corsOrigin);
            config.setAllowedOriginPatterns(Arrays.asList(origins));
        } else {
            config.setAllowedOriginPatterns(List.of("*"));
        }
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
```

### 11.2 WebFlux 版本（使用 `CorsWebFilter`）

```java
@Configuration
public class WebConfig {

    private final <ServiceName>Properties properties;

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        // 与 WebMVC 版本相同的 CorsConfiguration 设置
        // ...

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }
}
```

**要点**：
- 允许的来源从 `properties.getCorsOrigin()` 读取，支持逗号分隔的多 origin
- 默认允许所有 origin（`*`）、标准 HTTP 方法、所有 header
- 允许 credentials，预检缓存 3600 秒

## 12. 配置文件

### 12.1 config.yaml.example 格式

配置文件模板必须入库（`config.yaml.example`），运行时配置（`config.yaml`）加入 `.gitignore`。

```yaml
# <service-name> 配置
<service-name>:
  # CORS 允许的来源，逗号分隔，默认 "*"
  cors-origin: "*"

  # 日志配置
  logging:
    access-log-enabled: true

  # 服务专属配置段
  # ...
```

### 12.2 配置项命名约定

- 顶层 key 与服务目录名一致（如 `operation-intelligence`）
- 使用 kebab-case（`cors-origin`、`access-log-enabled`）
- 每个配置项在 `config.yaml.example` 中附带注释说明

### 12.3 环境变量覆盖

- 服务端口通过环境变量 `<SERVICE_NAME>_PORT` 或 `config.yaml` 中的 `server.port` 指定
- 配置文件路径通过 `<SERVICE_NAME>_CONFIG_PATH` 环境变量覆盖，默认为 `./config.yaml`
- 编排脚本中的 `yaml_val()` 函数从 `config.yaml` 读取值，环境变量优先级更高

**参照示例**：`operation-intelligence/config.yaml.example`、`business-intelligence/config.yaml.example`

## 13. 编排脚本

### 13.1 scripts/ctl.sh 模板

每个服务必须提供 `scripts/ctl.sh`，支持 `startup`、`shutdown`、`status`、`restart` 四个动作，支持 `--foreground` 和 `--background` 两种启动模式。

**关键函数**：

| 函数 | 职责 |
| --- | --- |
| `build_service()` | 检查 JAR 是否需要重建（源码比 JAR 新时触发），执行 `mvn package -DskipTests -q` |
| `do_startup()` | 检查 PID 文件和端口占用 → 构建服务 → 后台启动 JAR 或前台 exec → 健康检查等待 |
| `do_shutdown()` | 通过 PID 文件停止进程 → 等待端口释放 → 清理 PID 文件 |
| `do_status()` | 检查 PID 文件是否存在 → 健康检查 → 报告运行状态 |

**关键特性**：

- **PID 文件管理**：`${SERVICE_DIR}/logs/<service-name>.pid`
- **健康检查**：后台模式启动后轮询 `/actuator/health`，默认 40 次重试、每次间隔 1 秒
- **构建缓存**：对比源码和 JAR 的修改时间，跳过不必要的构建
- **端口冲突处理**：检测无 PID 文件的端口占用，使用 `service-daemon.sh` 提供的端口级清理
- **公共脚本依赖**：`source "${ROOT_DIR}/scripts/lib/service-daemon.sh"` 提供通用守护进程管理函数

**参照示例**：`business-intelligence/scripts/ctl.sh` — 直接复制并替换服务名、端口和 JAR 名

## 14. 根编排器集成

新建服务后，需修改根编排器 `scripts/ctl.sh` 完成注册。

### 14.1 修改清单

1. **添加 sub-script 路径**：
   ```bash
   CTL_<SERVICE_NAME>="${ROOT_DIR}/<service-name>/scripts/ctl.sh"
   ```

2. **添加可选服务开关**（仅限可选服务）：
   ```bash
   ENABLE_<SERVICE_NAME>="${ENABLE_<SERVICE_NAME>:-true}"
   ```

3. **`VALID_COMPONENTS` 数组**：添加 `<service-name>`

4. **`component_name()` 函数**：添加 case 映射 `<service-name>) echo "服务显示名" ;;`

5. **`is_optional_component()` 函数**：添加 case（可选服务返回 `true`，必选服务无需添加）

6. **`startup_one()` / `shutdown_one()` / `status_one()`**：添加 case 调用 sub-script

7. **`do_startup` 全量启动序列**：在合适位置插入新服务（参考现有启动顺序）

8. **`do_shutdown` 逆序关闭序列**：逆序插入

9. **`do_status`**：插入检查

10. **更新 usage 文本**

### 14.2 依赖启动顺序

当前启动顺序：gateway → knowledge-service → business-intelligence → skill-market → operation-intelligence → prometheus-exporter → control-center → webapp

新服务应按依赖关系插入合适位置。如无特殊依赖，通常放在 skill-market 之后、prometheus-exporter 之前。

**参照文件**：`scripts/ctl.sh`

## 15. 前端集成

### 15.1 RuntimeConfig 注册

修改 `web-app/src/config/runtime.ts`：

- `RuntimeConfig` 接口添加 `<serviceName>ServiceUrl?: string`
- 添加 `resolve<ServiceName>ServiceUrl()` 函数（模式同 `resolveBusinessIntelligenceServiceUrl`）
- 导出 `<SERVICE_NAME>_SERVICE_URL` 常量
- 在 `setRuntimeConfig()` 中初始化

### 15.2 API 服务层

- 导入新的服务 URL 常量替代 `GATEWAY_URL`（如适用）
- 添加对应服务的 headers 函数（参照 `gatewayHeaders`）
- 端点路径不变，base URL 切换到新服务

### 15.3 配置文件

在 `web-app/config.json` 和 `config.json.example` 中添加：

```json
"<serviceName>ServiceUrl": "http://127.0.0.1:<port>",
"<serviceName>SecretKey": "<shared-secret-if-required>"
```

如果新服务使用 `x-secret-key` 认证，前端运行时配置里的 `<serviceName>SecretKey` 必须和该服务的 `config.yaml` / `config.yaml.example` 中对应 secret key 保持一致，避免示例配置启动后静默回退到默认密钥。

### 15.4 i18n 检查

- 确认页面使用的 i18n key 在 `en.json` 和 `zh.json` 中均存在且对齐
- 新增用户可见文本时，同步更新两个语言文件，使用 namespace 风格 key

**参照文件**：`web-app/src/config/runtime.ts`

## 16. 测试规范

> 关键规则来自 [testing-guidelines.md](./testing-guidelines.md)，此处内联核心要求。

### 16.1 后端测试

测试目录：`<service-name>/src/test/java/com/huawei/opsfactory/<servicename>/`

| 测试类型 | 说明 | 示例 |
| --- | --- | --- |
| Spring 上下文冒烟测试 | 验证应用能正常加载 | `<ServiceName>ApplicationTest.java` |
| Service 单元测试 | Mock 外部依赖，测试业务逻辑 | `<ServiceName>ServiceTest.java` |
| Store/Repository 测试 | 使用临时目录测试文件读写 | `JsonFileStoreTest.java` |

测试使用 `@ActiveProfiles("test")` 加载 `application-test.yaml`，随机端口（`server.port: 0`）。

### 16.2 前端验证

前端变更后运行以下命令确认无回归：

```bash
cd web-app && npm run check:boundaries   # 模块边界检查
cd web-app && npm run test:basic          # 基础测试
cd web-app && npm run build               # 构建验证
```

### 16.3 集成测试

跨服务场景放在 `test/` 目录下，使用 Playwright 或 Node 测试运行器。

## 17. 文档要求

### 17.1 必须创建

| 文档 | 位置 | 说明 |
| --- | --- | --- |
| 服务 README | `<service-name>/README.md` | 服务介绍、快速开始、配置说明 |
| 架构文档 | `docs/architecture/<service-name>-architecture.md` | 技术架构、数据流、接口列表、存储策略 |

### 17.2 必须更新

| 文档 | 更新内容 |
| --- | --- |
| [docs/architecture/overview.md](../architecture/overview.md) | 系统图中添加服务、Core Services 段添加描述 |
| [docs/deployment/from-scratch-to-running.md](../deployment/from-scratch-to-running.md) | 服务组件表格、架构图、配置步骤、部署验证命令 |
| 根 README.md | 服务列表中添加新服务 |

### 17.3 可选创建

| 文档 | 说明 |
| --- | --- |
| 集成指南 `docs/architecture/<service-name>-integration.md` | API 规范、配置项详解、cURL 示例 |
| 排障指南 `docs/operations/<service-name>-troubleshooting-guide.md` | 常见问题排查步骤、日志查看命令 |

## 18. 完整检查清单

新建独立服务时，逐条确认以下各项：

### 目录与构建

- [ ] 服务目录结构符合第 2 节模板
- [ ] `pom.xml` 符合第 3 节模板（groupId、Spring Boot 版本、Java 版本、打包方式）
- [ ] `mvn compile` 通过
- [ ] `mvn test` 通过

### Spring Boot 应用

- [ ] Application 主类带 `@SpringBootApplication` + `@EnableConfigurationProperties`
- [ ] Properties 类使用 `@ConfigurationProperties(prefix = "<service-name>")`
- [ ] `application.yml` 端口、config import、logback、actuator 配置完整
- [ ] `application-test.yaml` 随机端口 + 最小配置

### 日志与规范

- [ ] `logback-spring.xml` 符合第 5.4 节模板
- [ ] `RequestLoggingFilter` 实现完整（MDC、access log、requestId 回传）
- [ ] 日志级别使用符合第 5.5 节规则，无敏感数据泄漏

### CORS 与配置

- [ ] `WebConfig` CORS 配置正确（WebMVC 用 `CorsFilter`，WebFlux 用 `CorsWebFilter`）
- [ ] `config.yaml.example` 入库，配置项带注释
- [ ] 环境变量覆盖路径正常工作

### 编排与集成

- [ ] `scripts/ctl.sh` 符合第 13 节模板（build/startup/shutdown/status 四动作）
- [ ] 根 `scripts/ctl.sh` 已注册新服务（sub-script 路径、VALID_COMPONENTS、启动/关闭/状态函数）
- [ ] 前端 `runtime.ts` 已注册新服务 URL
- [ ] 前端 `config.json` 已添加服务 URL
- [ ] 前端 `check:boundaries` + `test:basic` + `build` 通过

### 测试与文档

- [ ] 后端测试类已创建（冒烟测试 + 单元测试）
- [ ] 服务 `README.md` 已创建
- [ ] `docs/architecture/` 下的架构文档已创建
- [ ] `docs/architecture/overview.md` 已更新
- [ ] `docs/deployment/from-scratch-to-running.md` 已更新
- [ ] 根 `README.md` 已更新

### 回归验证

- [ ] Gateway 构建无回归（`cd gateway && mvn compile`）
- [ ] 全量编排启动正常（`./scripts/ctl.sh startup all`）
- [ ] 全量编排状态正常（`./scripts/ctl.sh status`）
