# 新增 /internal 内部对接 OpenAPI(首个端点:触发元数据刷新) 任务清单

> 执行者:Codex。产出后由 Claude 检查/审查 + 真实端到端验证。
> 本文件自包含:含背景、已核实事实、逐任务改动点、验收命令与判据。

## 背景

`LoadServiceMeta`(`datasophon-api/src/main/java/com/datasophon/api/load/LoadServiceMeta.java`)是一个 `ApplicationRunner`,只在 `datasophon-api` 进程**启动时执行一次**:从 Nexus `MetaStorage` 读取全部 `meta/<frameCode>/<PHYSICAL|k8s>/<SERVICE>/service_ddl.json`,逐个解析并写入 DB + 内存 Map(`ServiceInfoMap`/`ServiceRoleMap`/`ServiceConfigMap`/`ServiceConfigFileMap`)。**改了 Nexus 上的 DDL 后必须重启整个 Master 进程才能生效**,没有任何热加载入口——这是上一轮 NACOS DDL 联调中暴露的真实运维痛点。

用户要新增一组 `/internal` 前缀的**内部系统对接 OpenAPI**(供内部脚本/CI 调用,**暂不做认证**)。首个端点就是"触发元数据全量刷新",消除上述"改完 DDL 必须重启"的问题。

**分支**:从 `main` 新建 `feat/internal-meta-refresh-api`。

## 已核实关键事实(实现前必读,避免踩坑)

1. **`/internal` 前缀已是现成约定,不要碰 `AppConfiguration`**:`controller/AgentToolController.java` 已用 `@RequestMapping("/internal/agent")` 确立范式——该控制器**不继承 `ApiController`**,因此不吃 `AppConfiguration.configurePathMatch()` 给 `ApiController` 子类加的 `/api` 前缀,实际路径 = context-path `/ddh` + `/internal/...`。三条拦截器链均已放行 `/internal/**`:`loginRegistration`/`csrfRegistration` 只 `addPathPatterns(getPathPrefix() + "/**")` 即 `/ddh/api/**`,天然不覆盖 `/internal`;`basicValidRegistration` 覆盖 `/**` 但显式 `.excludePathPatterns("/internal/**")`(`AppConfiguration.java:167-170`,注释写明是为了 SPA 转发逻辑不拦这些"非浏览器路由")。**结论:POST /internal/** 不需要登录态、不需要 CSRF token,新增端点不需要改 `AppConfiguration` 任何一行。**
2. **响应包裹只按包名生效,互不干扰**:`controller/v2/V2ResponseBodyAdvice.java` 与 `controller/v2/V2ApiExceptionHandler.java` 都用 `@RestControllerAdvice(basePackages = "com.datasophon.api.controller.v2")`,只作用于 `v2` 包。新建 `com.datasophon.api.controller.internal` 子包配自己的 Advice,与 `v2` 包、与父包下的 `AgentToolController`(裸返回)三者互不干扰,**不要把 `AgentToolController` 移动或纳入新 basePackages**。
3. **"刷新"= 复用 `LoadServiceMeta.run()` 的主体,不是重新设计加载逻辑**:`run()` 依次做两件事——① `loadGlobalVariables(clusters)`;② 用 `StorageUtils.getMetaStorage()` 分别拉 `MetaStorage.PHYSICAL` 和 `MetaStorage.K8S` 两类 `ServiceMetaItem`,按 `framework` 分组,每组内逐个调用 `ddlMetaService.loadServicePhysicalDdl(...)` / `loadServiceK8sDdl(...)`,单个服务解析失败只 `logger.error` 不中断其它服务。`ddlMetaService.loadServicePhysicalDdl` 内部走 `saveOrUpdate`(`DdlMetaServiceImpl.saveFrameService`/`saveFrameServiceRole`),**幂等**,且会同步刷新内存 Map。→ 全量刷新端点的语义就是"再跑一遍这套逻辑"。
4. **事务自调用陷阱,已有先例可循**:`run()` 当前标注 `@Transactional(rollbackFor = Exception.class)`。抽出的新方法(下称 `reloadAllMeta()`)必须**独立**标注同样的 `@Transactional`,而不是让 `run()` 内联调用一个私有方法——因为 `run()` 本身就是通过 Spring 代理调用触发事务的入口,新方法作为**另一个公开入口**(被 HTTP 层直接 `@Autowired` 调用)同样需要走代理触发事务,两者是并列的两个事务入口,不是父子调用关系。
5. **异常/失败信息目前只进日志,做成端点后要能带出来**:当前 `run()` 里 `catch (Exception e) { logger.error("invalid service ddl file: {} {}", frameCode, item.getServiceName(), e); }`,调用方(启动流程)完全看不到。刷新端点必须能把这些错误在返回体里带出来,否则调用方以为刷新成功但实际有服务 DDL 解析失败。
6. **无 MetaStorage 的兜底行为**:`run()` 里 `StorageUtils.getMetaStorage()` 可能抛 `IllegalStateException`(没有启用的 `MetaStorage` 实现),当前 `run()` 捕获后直接 `return`(不算错误,只是"跳过"——本地/测试环境常见)。刷新端点需要保留这个语义,返回一个"跳过/无数据源"的结果而不是抛 500。
7. **命名空间约定**:`FrameV2Controller`/`ClusterV2Controller` 等 v2 controller 走 `@Service`/`@Autowired` 字段注入的旧风格较多,但项目 Java Rules 明确要求**构造器注入**,新代码统一按此执行,不模仿旧代码的字段注入。
8. **springdoc 现状**:`configuration/OpenApiConfiguration.java` 目前只有一个 `@Bean @ConditionalOnProperty("springdoc.api-docs.enabled") OpenAPI openAPI()`,单一全局文档,没有分组(`GroupedOpenApi`)。新增内部端点分组是可选加分项,不是刷新功能本身的前置依赖。

## 用户已拍板决策

- 刷新粒度:**仅全量刷新**,不做按 `frameCode`/`serviceName` 的单服务粒度刷新(`DdlMetaService.updateServicePhysicalDdl(serviceId, ddl)` 已存在按 serviceId 更新的能力,但那是"用给定内容更新",不是"从 Nexus 重读",本次不涉及,不要顺带暴露它)。
- 端点组织:**先搭一套可复用的 `/internal` 基座**(统一响应信封 + 异常处理 + 可选文档分组),不是只加一个裸端点——为后续更多内部端点铺路。

## 依赖关系

**T1(基座)与 T2(刷新逻辑抽取)完全独立、可并行**,两者互不改动对方的文件。**T3(端点)依赖 T1 的信封类 + T2 的 `reloadAllMeta()` 方法**,必须等两者都完成再开始。**T4(测试/文档)依赖 T2/T3 的产出**,其中"信封本身的单测"可以在 T1 完成后立即开始,不必等 T3。

---

## Task 1 — /internal 基座脚手架

**目标**:新建 `com.datasophon.api.controller.internal` 包,提供一套内部端点专用的响应信封、统一异常处理、(可选)文档分组,供本次及后续内部端点复用。

**依赖**:无,可与 Task 2 完全并行。

**新增文件**:

- `datasophon-api/src/main/java/com/datasophon/api/controller/internal/InternalResponse.java`

  极简机器对接信封,**不复用面向前端的 `com.datasophon.api.dto.ApiResponse`**(那个信封带 `showType` 等前端弹窗语义,内部系统对接不需要)。参考字段设计:

  ```java
  public class InternalResponse<T> {
      private boolean success;
      private int code;      // 成功固定 200,失败见 InternalApiExceptionHandler 各分支
      private String message;
      private T data;

      public static <T> InternalResponse<T> ok(T data) { ... }   // success=true, code=200
      public static <T> InternalResponse<T> ok() { return ok(null); }
      public static <T> InternalResponse<T> fail(int code, String message) { ... } // success=false
  }
  ```
- `datasophon-api/src/main/java/com/datasophon/api/controller/internal/InternalResponseBodyAdvice.java`

  `@RestControllerAdvice(basePackages = "com.datasophon.api.controller.internal")` 实现 `ResponseBodyAdvice<Object>`,`supports` 恒 `true`;`beforeBodyWrite` 逻辑镜像 `controller/v2/V2ResponseBodyAdvice.java`:已是 `InternalResponse` 原样透传,否则包一层 `InternalResponse.ok(body)`。

- `datasophon-api/src/main/java/com/datasophon/api/controller/internal/InternalApiExceptionHandler.java`

  `@Order(1) @RestControllerAdvice(basePackages = "com.datasophon.api.controller.internal")`,镜像 `controller/v2/V2ApiExceptionHandler.java` 的四个 `@ExceptionHandler`(`Exception`/`BusinessException`/`BusinessHintException`/`ConstraintViolationException`),返回值类型换成 `InternalResponse<Void>`,`fail(...)` 调用点的 code/message 语义与 v2 版本保持一致(500/500/400/400)。

- (可选)`datasophon-api/src/main/java/com/datasophon/api/configuration/OpenApiConfiguration.java` 追加:

  ```java
  @Bean
  @ConditionalOnProperty(name = {"springdoc.api-docs.enabled"})
  public GroupedOpenApi internalApi() {
      return GroupedOpenApi.builder().group("internal").pathsToMatch("/internal/**").build();
  }
  ```

  仅在确认项目已引入 `springdoc-openapi-starter-webmvc-ui` 且 `GroupedOpenApi` 可用时加;如引入有困难,跳过此步,不影响 T3/T4,在“核实结论”里注明跳过原因即可。

**约束**:

- 新 Advice 的 `basePackages` 必须精确为 `"com.datasophon.api.controller.internal"`,不得使用更宽泛的包名前缀导致意外覆盖 `com.datasophon.api.controller`(会连累 `AgentToolController`)或 `com.datasophon.api.controller.v2`。
- License 头照抄同目录任意既有文件(如 `V2ResponseBodyAdvice.java` 顶部注释块)。

**验收命令**:

```bash
cd /Users/pro/IdeaProjects/datasophon
./mvnw -pl datasophon-api -am compile -DskipTests
```

**判据**:编译通过;人工检查(或补一条断言)`InternalResponseBodyAdvice`/`InternalApiExceptionHandler` 的 `@RestControllerAdvice(basePackages=...)` 精确等于 `"com.datasophon.api.controller.internal"`。

---

## Task 2 — LoadServiceMeta 抽出可复用的全量刷新方法

**目标**:把 `LoadServiceMeta.run()` 的加载主体抽成一个独立的、可被 HTTP 层直接调用、返回加载统计结果的公开方法,`run()` 本身改为委托它,启动语义保持不变。

**依赖**:无,可与 Task 1 完全并行。

**改动文件**:

- `datasophon-api/src/main/java/com/datasophon/api/load/LoadServiceMeta.java`
- 新增 `datasophon-api/src/main/java/com/datasophon/api/load/MetaReloadResult.java`

**步骤**:

1. 新增 `MetaReloadResult`(POJO,Lombok `@Data` 即可):

   ```java
   public class MetaReloadResult {
       private int physicalTotal;
       private int physicalLoaded;
       private int k8sTotal;
       private int k8sLoaded;
       private List<String> errors = new ArrayList<>();  // 格式:"<frameCode>/<serviceName>: <message>"
       private boolean metaStorageAvailable = true;       // false = 命中"无 MetaStorage"分支,视为跳过而非失败
   }
   ```
2. 在 `LoadServiceMeta` 中新增:

   ```java
   @Transactional(rollbackFor = Exception.class)
   public MetaReloadResult reloadAllMeta() {
       MetaReloadResult result = new MetaReloadResult();
       List<ClusterInfoEntity> clusters = clusterInfoService.list();
       loadGlobalVariables(clusters);

       MetaStorage metaStorage;
       try {
           metaStorage = StorageUtils.getMetaStorage();
       } catch (IllegalStateException e) {
           logger.warn("No MetaStorage available, skipping service meta load: {}", e.getMessage());
           result.setMetaStorageAvailable(false);
           return result;
       }

       Map<String, FrameInfoEntity> frameworkCache = new HashMap<>();

       List<ServiceMetaItem> physicalDdlItems = metaStorage.listService(MetaStorage.PHYSICAL);
       // ... 分组加载,累加 physicalTotal/physicalLoaded,单个失败 push 进 errors(保留原 logger.error)

       List<ServiceMetaItem> k8sItems = metaStorage.listService(MetaStorage.K8S);
       // ... 同上,累加 k8sTotal/k8sLoaded

       return result;
   }
   ```

   逻辑与现有 `run()` 的两段 `groupedPhysicalMap.forEach(...)` / `groupedK8sMap.forEach(...)` 完全一致,只是把 `try { ... } catch (Exception e) { logger.error(...); }` 的 `catch` 块里再补一行 `result.getErrors().add(frameCode + "/" + item.getServiceName() + ": " + e.getMessage());`,并在成功分支 `total`/`loaded` 计数器自增。

3. `run(ApplicationArguments args)` 保留其 `@Override @Transactional` 注解,方法体简化为:

   ```java
   @Override
   @Transactional(rollbackFor = Exception.class)
   public void run(ApplicationArguments args) {
       reloadAllMeta();
   }
   ```

   `loadGlobalVariables` 这个 `public` 方法保持不变(已被 `reloadAllMeta()` 调用,不用再单独在 `run()` 里调一次)。

**约束**:

- 纯服务层重构,**不改** `DdlMetaService` 接口方法签名与 `DdlMetaServiceImpl` 的任何实现细节;`loadServicePhysicalDdl`/`loadServiceK8sDdl` 的幂等和内存 Map 刷新行为原样复用,不要重复实现。
- 不要删除或修改 `loadGlobalVariables` 方法本身的逻辑,只调整调用位置。

**验收命令**:

```bash
cd /Users/pro/IdeaProjects/datasophon
./mvnw -pl datasophon-api -am compile -DskipTests
./mvnw -pl datasophon-api -Dtest=DataSophonApplicationServerTest test
```

**判据**:编译通过;`DataSophonApplicationServerTest`(Spring 上下文启动测试)仍然通过,证明 `run()` 委托 `reloadAllMeta()` 后启动期加载行为未被破坏。

---

## Task 3 — /internal/meta 刷新端点

> PR 审查后的安全加固已取代本 Task 最初的“无鉴权”约束：当前端点要求
> `X-Internal-Token`，服务端通过 `DDH_INTERNAL_API_TOKEN` 配置；未配置或请求 Token 不匹配时返回 HTTP 401。

**目标**:暴露 `POST /internal/meta/refresh`,调用即触发一次全量元数据刷新,返回加载统计。

**依赖**:Task 1(`InternalResponse` 信封 + Advice 生效)+ Task 2(`reloadAllMeta()` 方法)均完成后开始。

**新增文件**:`datasophon-api/src/main/java/com/datasophon/api/controller/internal/InternalMetaController.java`

```java
/**
 * 元数据管理相关的内部端点(供内部系统/脚本调用)。
 *
 * <p>此控制器不继承 {@link com.datasophon.api.controller.ApiController},路径为
 * {@code /ddh/internal/meta/**} 而非 {@code /ddh/api/internal/meta/**}。
 * 登录/CSRF 拦截器仅覆盖 {@code /ddh/api/**},{@code basicValidRequestInterceptor}
 * 显式排除 {@code /internal/**},故这些端点不在拦截范围内。
 *
 * <p><b>当前无鉴权</b>(用户明确要求暂不加认证)。后续如需加固,可参照
 * {@link com.datasophon.api.controller.AgentToolController} 的 X-Agent-Token 方案,
 * 在此加一个等价的 X-Internal-Token 校验。
 */
@RestController
@RequestMapping("/internal/meta")
public class InternalMetaController {

    private final LoadServiceMeta loadServiceMeta;

    public InternalMetaController(LoadServiceMeta loadServiceMeta) {
        this.loadServiceMeta = loadServiceMeta;
    }

    @PostMapping("/refresh")
    public MetaReloadResult refresh() {
        return loadServiceMeta.reloadAllMeta();
    }
}
```

**约束**:

- 构造器注入(项目 Java Rules 要求),不用字段 `@Autowired`。
- **不改 `AppConfiguration`**——路径已被现有拦截器规则放行,不需要新增排除项。
- 端点**不加任何认证**,这是用户本次明确的要求;只在类注释里记录后续加固的落点,不要自行加一个"简单校验"当认证。
- 返回值是裸 `MetaReloadResult`,由 Task 1 的 `InternalResponseBodyAdvice` 自动包成 `InternalResponse<MetaReloadResult>`,**不要在这里手动 `InternalResponse.ok(...)` 包一层**(会被 Advice 判定为已是信封而透传,虽结果一样但违反“控制器只管业务返回值”的约定,徒增一次显式依赖)。

**验收命令**:

```bash
cd /Users/pro/IdeaProjects/datasophon
./mvnw -pl datasophon-api -am compile -DskipTests
```

本地起服务后(任意方式,IDEA 直接跑或 `spring-boot:run`):

```bash
curl -s -XPOST http://127.0.0.1:8080/ddh/internal/meta/refresh | python3 -m json.tool
```

**判据**:编译通过;`curl` 命令**不带任何 Cookie/Token**也能拿到 `HTTP 200`,响应体形如:

```json
{
  "success": true,
  "code": 200,
  "message": null,
  "data": {
    "physicalTotal": N,
    "physicalLoaded": N,
    "k8sTotal": N,
    "k8sLoaded": N,
    "errors": [],
    "metaStorageAvailable": true
  }
}
```

---

## Task 4 — 测试 + 文档收尾

**目标**:用单测锁定刷新逻辑与端点行为的关键分支,补齐文档,方便后续新增更多 `/internal` 端点时有例可循。

**依赖**:第 1 步依赖 Task 2,第 2 步依赖 Task 1 + Task 3;第 3、4 步可随时进行。

**改动文件**:

- 新增 `datasophon-api/src/test/java/com/datasophon/api/load/LoadServiceMetaReloadTest.java`
- 新增 `datasophon-api/src/test/java/com/datasophon/api/controller/internal/InternalMetaControllerTest.java`
- 新增 `datasophon-api/src/test/resources/internal-api.http`(可选,若模块内暂无 `.http` 样例文件先例可新建)
- `datasophon-api/CLAUDE.md`(补充一句约定)
- 新增 `docs/internal-api/README.md`

**步骤**:

1. `LoadServiceMetaReloadTest`(纯 Mockito 单测,不拉 Spring 上下文):mock `ClusterInfoService`/`DdlMetaService`/静态 `StorageUtils.getMetaStorage()`(可用 Mockito `mockStatic` 或提取一个可注入的 Supplier,视现有代码风格选择改动最小的方式),覆盖三个分支:
   - 正常情况下 `physicalLoaded`/`k8sLoaded` 计数与 mock 的服务数一致、`errors` 为空;
   - 其中一个服务的 `loadServicePhysicalDdl` 抛异常时,该异常被记录进 `errors`,但循环继续处理其余服务(不中断);
   - `StorageUtils.getMetaStorage()` 抛 `IllegalStateException` 时,返回的 `metaStorageAvailable=false` 且不抛异常。
2. `InternalMetaControllerTest`(`@WebMvcTest(InternalMetaController.class)`,mock `LoadServiceMeta`):
   - 正常调用 `POST /internal/meta/refresh` 返回 200,响应体 `success=true`、`data` 字段等于 mock 返回的 `MetaReloadResult`;
   - mock `loadServiceMeta.reloadAllMeta()` 抛 `RuntimeException` 时,断言最终响应经 `InternalApiExceptionHandler` 转换为 `success=false` 而不是原始异常堆栈。
3. (可选)`internal-api.http`:写一条 `POST {{baseUrl}}/ddh/internal/meta/refresh` 样例,供人工联调时直接在 IDE 里发送。
4. 文档更新:
   - `datasophon-api/CLAUDE.md` 的"协议面"小节补一句:`/internal/**` 是内部系统对接端点约定(不继承 `ApiController`、绕开登录/CSRF、暂无认证),先例见 `AgentToolController` 与 `InternalMetaController`。
   - 新建 `docs/internal-api/README.md`,记录当前已有的内部端点清单(至少含 `POST /internal/meta/refresh`)、路径规则、无认证现状及后续加固建议(参照 `AgentToolController` 的 `X-Agent-Token` 模式)。

**验收命令**:

```bash
cd /Users/pro/IdeaProjects/datasophon
./mvnw -pl datasophon-api -am -Dtest=LoadServiceMetaReloadTest,InternalMetaControllerTest test -Dspotless.check.skip=true
./mvnw spotless:apply
./mvnw spotless:check
```

**判据**:两个新测试类全绿;`spotless:check` 通过(含新建的 `docs/internal-api/README.md` 的 Markdown 格式,若被根 pom 的 spotless docs 规则检查,注意提前跑一次 `spotless:apply` 修格式,不要留手工格式问题给下一步)。

---

## 核实结论(Codex 实现后回填)

> 按 ZooKeeper/RustFS/Doris 先例:若真实运行结果推翻本文档任何推断(如某条拦截器规则实际未放行 `/internal`、事务自调用行为与预期不符),在此处记录并说明代码已如何据此修正,不要静默覆盖原描述。

- Task 1 核实结论:两个 Advice 的 `basePackages` 均精确为
  `com.datasophon.api.controller.internal`。已确认 Knife4j 4.5.0 会引入
  `springdoc-openapi-starter-webmvc-ui` 2.3.0，故已增加 `internal` 文档分组。
- Task 2 核实结论:`run()` 保留事务注解并委托 `reloadAllMeta()`；后者也作为独立的事务入口。
  原有的全局变量加载、物理/K8s 分组加载与单服务失败继续语义均保留，并新增统计、错误列表和
  无 MetaStorage 跳过结果。
- Task 3 核实结论:端点为 `POST /internal/meta/refresh`，使用 `X-Internal-Token` 认证；成功和失败响应均为
  `InternalResponse`。Token 通过 `DDH_INTERNAL_API_TOKEN` 配置，未配置时默认拒绝访问。
- Task 4 核实结论:新增 3 个加载层分支测试和 4 个 MockMvc 响应测试均已通过；启动冒烟测试也已通过。
  本任务 Java 文件已运行模块级 `spotless:apply`，随后根级 `spotless:check` 也已通过。

---

## Claude 负责的验证(实现完成后,不在本清单范围内执行,仅记录以便交接)

1. **静态审查**:审四个 Task 的 diff——重点检查 T1 两个 Advice 的 `basePackages` 是否精确、有没有误伤 `AgentToolController`;T2 `run()` 重构后是否还是"启动时一次性加载全部服务"这个语义、`@Transactional` 是否在正确的方法上;T3 路径是否确实不带 `/api`、是否校验 `X-Internal-Token`;T4 测试是否覆盖成功、异常和鉴权失败三条路径。
2. **编译 + 单测**:`./mvnw -pl datasophon-api -am -Dspotless.check.skip=true -Dtest=LoadServiceMetaReloadTest,InternalMetaControllerTest,DataSophonApplicationServerTest test`,再单独跑 `./mvnw -pl datasophon-api spotless:check`。
3. **端到端**:设置 `DDH_INTERNAL_API_TOKEN`,本地起 `datasophon-api`(IDEA 直接跑或用打包后的 tar 用 `java -cp` 起),`curl -XPOST -H "X-Internal-Token: ${DDH_INTERNAL_API_TOKEN}" http://127.0.0.1:8080/ddh/internal/meta/refresh` 确认:① HTTP 200 + `InternalResponse` 信封;② 服务端日志能看到"重新加载 DDL"相关字样;③ 手动改一份本地/Nexus 上的 `service_ddl.json` 后再打一次该端点,响应 `data` 里的统计/内容能反映出这次改动已生效(不需要重启 `datasophon-api` 进程)。
4. 若真实行为证伪任何推断,按 ZK/RustFS/Doris 先例回改本文档并记录在"核实结论"一节。

未携带正确 `X-Internal-Token` 的请求应返回 HTTP 401。
