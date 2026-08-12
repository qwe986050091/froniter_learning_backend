# 平台分类与平台属性管理 规格说明

## 1. 问题 / 用户 / 目标

### 问题
当前后端工程 (`frontier_test_backend`) 与前端工程 (`frontier_test_admin`) 已完成品牌模块的「Thrift → 生成 → 后端实现 → 前端页面」全链路演示，但商品管理域中更核心的「**三级商品分类**」与「**平台属性 + 属性值**」（对应截图中的交互）尚未实现，导致缺少可用于后续业务模块（SKU、SPU、规格）的基础字典数据与管理页面。

### 用户
登录后台系统的 **ADMIN 运营用户**，需要维护商品分类树和平台属性字典。

### 目标
严格遵循用户指定的交付顺序：

1. 编写 Thrift IDL（`product.thrift`），定义「分类、平台属性、属性值」三类实体及其所有接口；
2. 通过后端 `pom.xml` 已集成的 `thrift` 可执行文件调用 `exec-maven-plugin` 的 `generate-sources` 阶段（即 `thrift -gen java ...`）自动产出 Java 文件；
3. 后端实现：在 `FrontierServiceImpl` 中实现新的 `ProductService.Iface`，在 `ThriftServerConfig` 注册为新的 multiplexed processor，新增 `ProductController` 通过 REST 暴露给前端，并注入足够的 mock 种子数据（至少覆盖 A/B/C 三套三级分类、3+ 条平台属性，用于验证分页 / 筛选效果）；
4. 前端实现：
   - 在 `types/index.ts` 中补齐类型；
   - 在 `api/index.ts` 的 `FrontierServiceImpl` 中补齐方法；
   - 新增 `views/pages/PlatformAttrManage.vue` 页面，接入路由、菜单，实现截图所示交互（顶部 3 级分类级联选择 + 「+ 添加平台属性」按钮 + 平台属性表格，含属性值标签、编辑、删除）。

最终交付给用户一条**可跑通**的新业务链路，并且与现有品牌模块的风格（thrift/后端/REST/前端 TS 类型/Element Plus）保持一致。

### 非目标
- 不接入数据库，继续沿用品牌模块现有的「ConcurrentHashMap + AtomicLong + 启动种子」内存模型；
- 不做图片/文件上传、不做 SPU/SKU 等后续扩展；
- 不引入鉴权分级（继续沿用全局 `@RequireAuth`）；
- 不引入分类与平台属性之外的实体（如销售属性、参数组）。

---

## 2. 功能需求（Functional Requirements）

### 2.1 Thrift 与 IDL（产物：`src/main/resources/thrift/product.thrift`）
1. 定义命名空间 `java org.example.thrift.product`，`include "common.thrift"`。
2. 定义 `Category` 实体：`id / name / parentId / level(1|2|3) / sort / status / createTime`。
3. 定义 `PlatformAttr` 实体：`id / name / categoryId(三级分类ID) / sort / status / createTime`。
4. 定义 `PlatformAttrValue` 实体：`id / attrId / value / sort`。
5. 定义 `PlatformAttrWithValues`：`PlatformAttr + list<PlatformAttrValue>`（供详情/表格行使用，避免前端二次请求）。
6. 定义查询与分页请求：
   - `CategoryQuery(level?, parentId?)` 直接返回 list（分类全量较少，不做分页）；
   - `PlatformAttrQuery(categoryId?, name?, status?, page, pageSize)`；
   - 对应 `PlatformAttrPageResult`（list + total + page + pageSize）。
7. 定义 CRUD 请求：
   - 分类：`CategoryCreateRequest / CategoryUpdateRequest / CategoryStatusRequest`；
   - 平台属性：`PlatformAttrCreateRequest(name, categoryId, sort?, status?, values:list<string>)` —— 创建时同时传入该属性的所有属性值字符串数组，后端一次落库；
   - 平台属性更新：`PlatformAttrUpdateRequest(id, name, categoryId?, sort?, status?, values:list<string>)` —— 覆盖式更新属性值（旧的删完重插，简化模型）；
   - 属性值单独更新：不做；
   - 删除：`bool deleteCategoryById(i64)`（仅允许无子分类/无属性关联时删除；否则抛出 ServiceException）、`bool deletePlatformAttrById(i64)`（级联删除其属性值）。
8. 定义 `ProductService`：
   - `listCategory` / `getCategoryById` / `createCategory` / `updateCategory` / `updateCategoryStatus` / `deleteCategoryById`；
   - `listPlatformAttr` / `getPlatformAttrById` / `createPlatformAttr` / `updatePlatformAttr` / `deletePlatformAttrById`；
   - 所有接口 `throws (1: common.ServiceException e)`。

### 2.2 后端实现
9. 新增生成命令**不用 maven 插件**，直接用 `src/main/resources/thrift/thriftGenerator.txt` 中同款的本地 thrift exe：
   - 命令模板：`D:\code\thrift-0.24.0.exe --gen java:jakarta_annotations -out D:\code\frontier_test_backend\src\main\java D:\code\frontier_test_backend\src\main\resources\thrift\product.thrift`
   - 生成产物目录：`src/main/java/org/example/thrift/product/`（与现有 `brand / auth / menu` 目录同级，已被 IDE 识别为源码）。
   - 该命令不包含 `-recursive`，所以 `include "common.thrift"` 只会引用已存在的 `common` 包，不会重复生成。
10. `FrontierServiceImpl` 必须 `implements ProductService.Iface`，内部：
   - 独立的 `categoryStore / attrStore / attrValueStore` 三个 `ConcurrentHashMap` + 独立 `AtomicLong` id 序列；
   - 启动种子：至少 3 个一级分类（手机/家电/服装），每个 3 个二级，每个二级至少 2 个三级分类（共 ≥3 套三级）；每个三级分类至少 2 条平台属性；每条平台属性至少 3 条属性值（总体数量足以使 10/页出现分页）；
   - 分类删除校验：有子分类或绑定了平台属性 → 抛 `ServiceException(code="HAS_CHILDREN", desc=...)`；
   - 属性列表默认按 `sort` 升序 + `id` 升序；
   - 属性创建/更新时 `values` 为空 → 抛 `VALIDATION_ERROR`；属性名在同分类下重复 → 抛 `DUPLICATE_NAME`。
11. `ThriftServerConfig` 必须把 `ProductService.Processor<>(frontierService)` 注册到 `TMultiplexedProcessor`。
12. `ProductController`（`@RequestMapping("/api/product")` + `@RequireAuth`）暴露 REST：
   - 分类：`GET /categories?level=&parentId=`、`POST /categories`、`PUT /categories/{id}`、`PATCH /categories/{id}/status`、`DELETE /categories/{id}`；
   - 平台属性：`GET /attrs?categoryId=&name=&status=&page=&pageSize=`、`POST /attrs`、`PUT /attrs/{id}`、`DELETE /attrs/{id}`、`GET /attrs/{id}`（返回带 values 的详情）。

### 2.3 前端实现
13. `types/index.ts`：
    - 新增 `Category / PlatformAttr / PlatformAttrValue / PlatformAttrWithValues / PlatformAttrQuery / PlatformAttrPageResult / CategoryQuery / CategoryCreateRequest / CategoryUpdateRequest / PlatformAttrCreateRequest / PlatformAttrUpdateRequest`，字段与后端对齐。
14. `api/index.ts` 的 `FrontierServiceImpl`：
    - 新增与上述 Controller 对应的 13 个方法；`buildSearchParams` 处理查询；统一 401 自动跳转登录。
15. 新增 `views/pages/PlatformAttrManage.vue`（截图对应页面）：
    - **顶部三下拉级联**：一级分类（初始拉全部 level=1）→ 选中后拉该 parentId 的 level=2 → 选中后拉该 parentId 的 level=3。截图中示例值「手机 / 手机通讯 / 手机,对讲机」符合此链路。
    - **+ 添加平台属性按钮**：需先选中三级分类才能点击（否则 disabled 或提示）；弹出表单包含「属性名称 / 排序 / 状态 / 属性值输入区（Element Plus Tag 风格，可增删多个值）」。
    - **属性表格**：列顺序为「序号 / 属性名称 / 属性值名称（每行用多个彩色 `el-tag` 展示，与截图一致）/ 操作（编辑、删除）」。
    - 编辑弹窗复用新增弹窗，回填时要把 `values` 数组放入标签输入区。
    - 删除走 `ElMessageBox.confirm`。
    - 表格不做分页控制（当属性 > 10 时用分页器，默认 10/页）。
16. 路由与菜单：
    - `router/index.ts` 的 Home children 增加 `{ path: 'platform-attr-manage', component: PlatformAttrManage }`；
    - 后端 `MenuService.getMenu()` 的返回中，在「商品管理」子菜单下新增一项「平台属性」，path 对应 `/home/platform-attr-manage`。

---

## 3. 非功能需求（Non-Functional Requirements）

1. **一致性**：后端接口风格必须同 `BrandController`（`List` / `Create` / `Update` / `PATCH status` / `DELETE` 的路径、返回体、ServiceException 处理、日志字段完全一致）。
2. **数据稳定**：启动种子必须确定（不依赖随机），重启后重新初始化（与品牌模块一致）。
3. **可验证**：后端 mock 数据数量必须使前端分页器出现至少 2 页（属性条目总数 ≥ 12，10/页）。
4. **类型安全**：前端 TS 接口必须与后端字段严格同构（`i64` → `number`、`optional` → `?`），避免 any。
5. **可运行**：完成后 `mvn spring-boot:run` 启动后端 + `pnpm dev` 启动前端，登录后页面可打开、CRUD 全部通过。

---

## 4. 约束 / 依赖 / 假设 / 开放问题

### 约束
- 必须使用现有项目内已经工作的 thrift 生成链路（`exec-maven-plugin` id=generate-thrift，executable=thrift）；
- 必须使用内存存储，不得引入数据库依赖；
- 前端必须使用现有 Element Plus 组件（品牌管理页已验证可行），不得额外引入其他 UI 库。

### 依赖
- 后端 Java 17、Spring Boot 3.2.0、libthrift 0.24.0、Lombok（已存在）；
- 前端 Vue 3 + TS + Vite + Element Plus（已存在）。

### 假设
- 用户本机固定路径已有 `D:\code\thrift-0.24.0.exe`（因为 `thriftGenerator.txt` 指向它，且 `brand.thrift` 已按同样命令生成到 `src/main/java/org/example/thrift/brand/`）。
- 截图里的「添加平台属性」按钮要求「先选中三级分类」是合理的业务约束（否则保存会失败）。

### 开放问题（实施前默认处理，用户可在 Approve 前修改）
1. Q: 三级分类是否必须全部预置，还是允许用户手动新建？
   - **默认**：预置种子足够测，同时开放「新增分类」CRUD 接口（不一定在本页有 UI，但接口要有）。
2. Q: 属性值是否需要单独更新？
   - **默认**：不做，编辑属性时整体覆盖重插。
3. Q: 分类状态字段（启/禁用）是否本页需要 UI？
   - **默认**：接口必须支持，页面不做（本页只展示分类下拉，状态过滤可在后端查询时加一层）。

---

## 5. 验收标准（Acceptance Criteria）

所有 AC 以最终「独立 Review + 浏览器证据」为准。

| 编号 | 类型 | 描述 | 可观察通过条件 |
|---|---|---|---|
| AC-01 | rule | thrift 生成产物存在 | 执行 `D:\code\thrift-0.24.0.exe --gen java:jakarta_annotations -out D:\code\frontier_test_backend\src\main\java D:\code\frontier_test_backend\src\main\resources\thrift\product.thrift` 后退出码为 0，且 `src/main/java/org/example/thrift/product/` 目录下存在 `ProductService.java`、`Category.java`、`PlatformAttr.java`、`PlatformAttrValue.java` 等生成文件 |
| AC-02 | rule | Thrift server 注册新服务成功 | 启动后端后日志打印 `services: AuthService, MenuService, BrandService, ProductService` |
| AC-03 | rule | 平台属性分页接口返回 2+ 页 | `GET /api/product/attrs?page=1&pageSize=10` 返回 `total >= 12` |
| AC-04 | rule | 分类删除校验生效 | 对有子分类的一级分类调用 `DELETE /api/product/categories/{id}`，返回 HTTP 400 且 body.code == `HAS_CHILDREN` |
| AC-05 | rule | 分类级联 REST 正确 | `GET /api/product/categories?level=1` 返回 3+ 条；取第一条 id → `?level=2&parentId=` 返回 3 条；继续取 id → `?level=3&parentId=` 返回 2+ 条 |
| AC-06 | rule | 平台属性创建 + 属性值整体落库 | `POST /api/product/attrs` body 含 `name, categoryId, values:["A","B","C"]` → 返回带 id 的 PlatformAttr；之后 `GET /attrs/{id}` 返回的 values 列表长度为 3 且值完全匹配 |
| AC-07 | rule | 平台属性同名校验 | 在同一分类下 POST 两条相同 name 的属性 → 第二条 HTTP 400 且 body.code == `DUPLICATE_NAME` |
| AC-08 | rule | 平台属性删除级联 values | 删除某 attr 后，查对应 id 返回 404（ServiceException NOT_FOUND） |
| AC-09 | rule | 前端页面路由可用 | 登录后点击左侧菜单「商品管理 / 平台属性」跳转到 `/home/platform-attr-manage` |
| AC-10 | rule | 顶部三下拉级联交互正确 | 依次选一级→二级→三级，三级选项正确出现（如截图手机→手机通讯→手机/对讲机） |
| AC-11 | rule | 未选三级分类时「+ 添加平台属性」被禁用 | 三级未选时按钮 disabled 或点击后弹 ElMessage 提示 |
| AC-12 | rule | 属性表格显示 tag 形式的属性值列表 | 表格每行的「属性值名称」列渲染多个 el-tag，数量 ≥ 3 且与后端 values 一致 |
| AC-13 | rule | 新增 / 编辑 / 删除 属性成功 | 新增后立即出现在表格中；编辑后 values 整体被新值覆盖；删除走 confirm 弹窗后表格行消失，Total -1 |
| AC-14 | rule | 分页可用 | 属性条数 ≥ 12 → 页面分页器有 2+ 页；点第二页时表格内容变化且高亮在 Page 2 |
| AC-15 | rubric | 代码与现有模式一致性（0-2） | 2 = 与品牌模块完全一致（controller 结构、异常处理、api/index.ts 模式、vue 模式）；1 = 有一处明显不一致但可运行；0 = 大量不一致难以维护 |
| AC-16 | rubric | UI 与截图匹配度（0-2） | 2 = 顶部三级分类下拉 + 表格列顺序 + tag 样式 + 操作按钮与截图高度一致；1 = 有可识别差异但不影响使用；0 = 结构明显不同 |
