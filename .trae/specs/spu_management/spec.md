# SPU 管理 规格说明

## 1. 问题 / 用户 / 目标

### 问题
当前 `商品管理` 分组下已有品牌、平台属性两个模块，但作为商品域核心实体的 **SPU（Standard Product Unit，标准化产品单元）** 尚未落地。用户给的截图中已经展示了 SPU 管理页面的预期布局（三级分类筛选 + 添加SPU按钮 + 含"SPU名称 / SPU描述 / 四个操作按钮"的表格），但缺：
- Thrift 契约及 Java 生成物；
- 后端 mock 存储与 REST 接口；
- 前端类型、API、路由、菜单项、页面。

### 用户
后台 ADMIN 运营用户，负责在选定三级分类下维护 SPU（名称、描述、状态、排序）。

### 目标
严格按用户指定顺序「**Thrift → thrift.exe 生成 → 后端实现 → 前端页面**」交付一条可跑通的 SPU 全链路：

1. Thrift：在现有 `product.thrift`（商品域）中增加 SPU 相关 struct / req / service 方法（不新建文件，保持域内聚合）。
2. 生成：使用 `thriftGenerator.txt` 同款命令 `D:\code\thrift-0.24.0.exe --gen java:jakarta_annotations -out ... product.thrift` 生成 Java。
3. 后端：`FrontierServiceImpl` 实现新方法（内存 store + 启动种子）、`ProductController` 暴露 REST、`ThriftServerConfig` 因复用 `ProductService` 不改动、菜单 `getMenu()` 在「商品管理」子菜单下新增「SPU管理」项。
4. 前端：
   - `types/index.ts` 补齐 SPU 类型；
   - `api/index.ts` 补齐 SPU REST 方法；
   - `router/index.ts` 加路由；
   - 新建 `SPUManage.vue`（参考截图与平台属性页面结构：顶部三级分类级联 + 添加SPU按钮 + 表格分页 + 增删改查弹窗 + 行内四个操作按钮）。

最终与品牌/平台属性保持一致的风格和可运行性，页面效果尽量对齐截图。

### 非目标
- 不引入数据库（延续 ConcurrentHashMap 内存模型）。
- 不做 SKU、规格、销售属性、图片上传等 SPU 关联扩展。
- 不做品牌/分类与 SPU 的强关联校验（仅在 SPU 实体上存 brandId/categoryId 外键字段即可，不强制校验外键存在性以避免 seed 依赖耦合）。
- 不引入权限分级（继续沿用 `@RequireAuth` 全局登录鉴权）。

---

## 2. 功能需求（Functional Requirements）

### 2.1 Thrift IDL（修改 `product.thrift`）

R-01. 在 `src/main/resources/thrift/product.thrift` 内追加：

1. 实体 `SPU`：
   ```
   struct Spu {
       1: optional i64 id
       2: required string name              // SPU 名称
       3: optional string description       // SPU 描述（长文本，对应表格"SPU描述"列）
       4: required i64 categoryId           // 所属三级分类 id
       5: optional i64 brandId              // 可选关联品牌 id（截图中OPPO/vivo/华为属于品牌 SPU）
       6: optional i32 sort                 // 排序
       7: optional i32 status               // 1 启用 0 禁用（行灰色按钮=启/禁用切换）
       8: optional string createTime
   }
   ```

2. 分页查询 `SpuQuery`：
   ```
   struct SpuQuery {
       1: optional i64 categoryId        // 三级分类（必筛，未选中=空列表，截图左上方三下拉）
       2: optional i64 brandId           // 品牌筛选（可选，先做接口留着，UI 暂不加筛选器）
       3: optional string name           // SPU 名称模糊搜索
       4: optional i32 status            // 1/0 或不传=全部
       5: optional i32 page              // 1 起
       6: optional i32 pageSize          // 默认 10
   }
   ```

3. 分页结果 `SpuPageResult`：`list<Spu> + total + page + pageSize`。

4. CRUD 请求：
   - `SpuCreateRequest(name, description?, categoryId, brandId?, sort?, status?)`
   - `SpuUpdateRequest(id, name, description?, categoryId?, brandId?, sort?, status?)`
   - `SpuStatusRequest(id, status)`（行内状态切换按钮专用，与通用 update 并存）
   - `SpuSortRequest(id, sort)`（排序快速修改）
   - 删除：`bool deleteSpuById(i64 id)`（有 SKU 才禁删；当前无 SKU 模块，所以直接删除，不做关联校验）

5. 在现有 `service ProductService { ... }` 中追加方法：
   ```
   SpuPageResult listSpu(1: SpuQuery query) throws (1: common.ServiceException e)
   Spu getSpuById(1: i64 id) throws (1: common.ServiceException e)
   Spu createSpu(1: SpuCreateRequest req) throws (1: common.ServiceException e)
   Spu updateSpu(1: SpuUpdateRequest req) throws (1: common.ServiceException e)
   bool updateSpuStatus(1: SpuStatusRequest req) throws (1: common.ServiceException e)
   bool updateSpuSort(1: SpuSortRequest req) throws (1: common.ServiceException e)
   bool deleteSpuById(1: i64 id) throws (1: common.ServiceException e)
   ```

### 2.2 后端实现

R-02. **生成 Thrift Java**：用 `thriftGenerator.txt` 同款 exe 命令重新生成 `product.thrift`（因为修改了现有域文件，不是新文件），目标目录 `src/main/java/org/example/thrift/product/`。确认产物：`Spu.java`、`SpuQuery.java`、`SpuPageResult.java`、`SpuCreateRequest.java`、`SpuUpdateRequest.java`、`SpuStatusRequest.java`、`SpuSortRequest.java`、更新的 `ProductService.java`。

R-03. **`FrontierServiceImpl`**：
- 新增 `Map<Long, Spu> spuStore` + `AtomicLong spuSeq`。
- **启动种子**：对齐截图中「手机 / 手机通讯 / 手机」三级分类的上下文，预置：
  - OPPO、vivo、华为（至少 3 条，对应截图首屏三行）；
  - 继续追加小米、三星、苹果、魅族、荣耀、一加、红米、realme、索尼、诺基亚、摩托罗拉、联想、中兴、锤子、iQOO、黑鲨、ROG、金立、TCL、酷派、海信、康佳、长虹、飞利浦、松下、夏普、LG、HTC、谷歌 Pixel、华硕（总计 ≥ 31 条 → 10/页至少 4 页，验证分页）；
  - description 用 2~5 句中文简介（与 OPPO/vivo/华为截图风格一致，避免 Lorem 占位）。
- 实现 7 个 SPU service 方法：
  - listSpu：按 `categoryId` 必筛、`name/status/brandId` 可选模糊/等值过滤；默认排序 `sort 升 → id 升`；分页正确。
  - getSpuById：not found → `ServiceException("NOT_FOUND", ...)`。
  - createSpu：`name` 空/空串 → `VALIDATION_ERROR`；`categoryId` 缺失 → `VALIDATION_ERROR`；同分类下同名重复 → `DUPLICATE_NAME`。
  - updateSpu：id 不存在 → `NOT_FOUND`；同分类重复 name（排除自身 id）→ `DUPLICATE_NAME`。
  - updateSpuStatus / updateSpuSort：id 不存在 → `NOT_FOUND`。
  - deleteSpuById：id 不存在 → `NOT_FOUND`。

R-04. **`ThriftServerConfig`**：由于 SPU 接口直接挂在 `ProductService` 下（复用现有 ProductService），无需再注册新 processor。注册状态保持当前（Auth / Menu / Brand / Product）。

R-05. **`ProductController`**：在现有 `@RequestMapping("/api/product")` 下新增 REST 端点：
```
GET    /spus?categoryId=&name=&status=&brandId=&page=&pageSize=
POST   /spus
GET    /spus/{id}
PUT    /spus/{id}
PATCH  /spus/{id}/status   body: {status}
PATCH  /spus/{id}/sort     body: {sort}
DELETE /spus/{id}
```
每个端点遵循当前 `ProductController` 的统一模式：try-catch ServiceException → 400 + errorOf；TException → 500；success → ResponseEntity.ok(结果)。

R-06. **`MenuService.getMenu()` 新增菜单项**：在「商品管理」分组的 `children` 里，在 `platform-attr` 项后面新增一项：
```java
new MenuItem("spu-manage", "SPU管理")
        .setIcon("Goods")   // Element Plus 可用图标名，若找不到可用 "Box" 或 "CollectionTag"
        .setPath("/home/spu-manage")
        .setDesc("这里是 SPU 管理页面。")
```
路径 `/home/spu-manage` 与前端路由 children 对齐，保持「子页 URL 均带 /home 前缀」的工程惯例。

### 2.3 前端实现

R-07. **TS 类型**（`src/types/index.ts`）：
- 新增 `Spu / SpuQuery / SpuPageResult / SpuCreateRequest / SpuUpdateRequest / SpuStatusRequest / SpuSortRequest`，字段与 Thrift 对齐。
- 更新 `FrontierService` 接口签名，新增：
  ```
  listSpu(query?: SpuQuery): Promise<SpuPageResult>
  getSpuById(id: number): Promise<Spu>
  createSpu(req: SpuCreateRequest): Promise<Spu>
  updateSpu(req: SpuUpdateRequest): Promise<Spu>
  updateSpuStatus(req: SpuStatusRequest): Promise<{ success: boolean }>
  updateSpuSort(req: SpuSortRequest): Promise<{ success: boolean }>
  deleteSpuById(id: number): Promise<{ success: boolean }>
  ```

R-08. **API 封装**（`src/api/index.ts` `FrontierServiceImpl` 类）：实现上面 7 个方法，调用 `/api/product/spus*`，使用统一 request wrapper（自动带 token / 401 跳转登录）。

R-09. **路由**：`router/index.ts` 的 `/home` children 加 `{ path: 'spu-manage', component: SPUManage }`。

R-10. **页面组件**（新增 `src/views/pages/SPUManage.vue`）：
整体结构参考 [PlatformAttrManage.vue](file:///d:/code/frontier_test_admin/src/views/pages/PlatformAttrManage.vue)（已抽为父子组件形态，但 SPU 首版可先写在单个组件里，保证功能跑通）。要求：

1. **顶部 Toolbar 卡片（白底+边框+圆角）**：
   - **第一行：三下拉级联分类**（一级→二级→三级），逻辑完全复用平台属性页面（`listCategory(level, parentId)` + `handleLevel1/2/3Change` + 分页重置 + `loadList`）。
   - **第二行：搜索栏 + 添加SPU按钮**（用 `justify-content: space-between` 让按钮靠右）。
     - 搜索：`SPU名称 el-input`（按 name 模糊） + `状态 el-select`（全部/启用/禁用） + 「搜索 / 重置」按钮；
     - `+ 添加SPU` 按钮：未选中三级分类时 disabled。

2. **表格卡片**：
   - 列顺序：`序号 / SPU名称 / SPU描述 / 操作`。
   - SPU描述列：文字较多，给 `min-width` 或让其自动换行（截图中为多行纯文本展示，不做 el-tag）。
   - 空状态：未选三级分类 → 提示「请先选择三级分类后查看SPU」；已选但无数据 → 「暂无数据」。
   - 操作列四个按钮（对齐截图）：
     1. **添加**（蓝色+按钮 `el-button + Plus`）—— 当前无 SKU 模块，点击弹 toast「暂未实现SKU新增」占位（或直接跳"添加"弹窗，复用 add 弹窗打开即可，更符合直觉）。
     2. **编辑**（黄色 `Edit` 按钮）。
     3. **状态切换**（灰色 `Switch` 图标按钮，确认后调 `updateSpuStatus`，当前状态反置）。
     4. **删除**（红色 `Delete` 按钮，走 `ElMessageBox.confirm`）。

3. **分页器**：`total, sizes, prev, pager, next, jumper`，支持 10/20/50/100 页大小，页码/size变化均触发 `loadList`。

4. **新增/编辑弹窗**：
   - 字段：`所属分类（只读：一/二/三级路径，复用 categoryPathLabel 计算）、SPU名称（必填 1-100）、SPU描述（textarea 2000字内可选）、关联品牌（可选 el-select，数据复用已有 `frontierService.listBrand`，拉全部或按一级分类不做过滤即可）、排序（0~9999）、状态（el-switch 启用/禁用）`。
   - 提交走 `createSpu` 或 `updateSpu`；提交成功后 `loadList()` 刷新并关闭弹窗。
   - 编辑：直接用 `row` 填表单，不要额外调 `getSpuById`（与平台属性当前版本一致，减少不必要的请求）。

5. 样式风格与平台属性页一致（toolbar / table-card / 卡片间距、分页器位置）。

---

## 3. 非功能需求

NFR-1. **一致性**：Thrift / Java 生成 / REST 端点路径 / Controller 错误处理 / 前端请求封装 / 卡片样式 / TS 类型 均与平台属性、品牌管理现有实现保持完全一致。
NFR-2. **数据稳定**：SPU 种子数据必须确定（硬编码，不随机），后端重启后重新初始化，演示总数固定、分页页码内容固定。
NFR-3. **分页可验证**：种子 SPU 总数 ≥ 31，保证 10/页至少 4 页。
NFR-4. **类型安全**：前后端字段 1:1 对齐，避免 `any`。
NFR-5. **可运行**：`mvn spring-boot:run`（后端）+ `pnpm dev`（前端）后，登录 admin/123456，「商品管理 → SPU管理」菜单可进入并完成 CRUD + 启禁用 + 排序更新 + 分页切换 + 搜索/重置。

---

## 4. 约束 / 依赖 / 假设 / 开放问题

### 约束
- Thrift 生成命令固定使用 `thriftGenerator.txt` 同款 `D:\code\thrift-0.24.0.exe --gen java:jakarta_annotations`，不得启用 pom 的 exec-maven-plugin 自动执行。
- 后端使用内存 `ConcurrentHashMap` 存储，禁止新增数据库依赖。
- 前端仅使用已有 `Vue3 + TS + Vite + Element Plus`，不引入新 UI 库。
- SPU 相关内容挂在现有 `product.thrift` / `ProductService` / `ProductController`（商品域）下，不新建 brand/menu 级别的域文件。

### 依赖
- 后端：Java 17、Spring Boot 3.2.x、libthrift 0.24.0、Lombok。
- 前端：Vue 3.5、Pinia（有但不用）、Vue Router 5、Element Plus 2.14、@element-plus/icons-vue 2.3。

### 假设
- 用户本机存在 `D:\code\thrift-0.24.0.exe`（thriftGenerator.txt 指向它，brand / product.thrift 此前都以同命令生成）。
- 截图里操作列的第一个「蓝色 + 按钮」语义是 "为该SPU新增SKU"。由于 SKU 模块不在本期范围内，默认实现为：点击时打开"新增SPU"弹窗（即与顶部"+ 添加SPU"按钮效果相同，作为占位；若用户倾向 toast 提示"SKU未实现"也可，待 approve 前确认）。

### 开放问题（默认处理，Approve 前可改）
1. **Q1**: 行首「蓝色+按钮」是"打开新增SPU弹窗复用" 还是 "弹 toast 提示 SKU 未实现"？
   - **默认**：打开新增 SPU 弹窗（复用顶部"+ 添加SPU"流程，对用户更有操作反馈，与平台属性页"Add 是加属性/SKU 的直觉一致"）。
2. **Q2**: SPU 描述最大长度？
   - **默认**：2000（textarea 带字符计数 `show-word-limit`）。
3. **Q3**: 搜索是否需要 SPU 描述模糊搜索？
   - **默认**：仅按 name 搜索，description 不加入筛选条件（截图也只有"SPU名称"筛选）。

---

## 5. 验收标准（Acceptance Criteria）

| AC 编号 | 类型 | 描述 / 通过条件 |
|---|---|---|
| AC-01 | rule | `product.thrift` 追加了 Spu/SpuQuery/SpuPageResult/SpuCreateRequest/SpuUpdateRequest/SpuStatusRequest/SpuSortRequest 七个 struct，且 ProductService 增加了 7 个 SPU 方法，thrift 生成无错 |
| AC-02 | rule | 执行 thriftGenerator.txt 同款命令后退出码=0，`src/main/java/org/example/thrift/product/` 下能找到 Spu.java、SpuQuery.java 等 7 个新增生成文件 + 更新的 ProductService.java（含对应 I/Face 方法签名） |
| AC-03 | rule | `FrontierServiceImpl.java` 编译通过，含 7 个 SPU 方法实现，启动后 `listSpu` total ≥ 31（分页可验证） |
| AC-04 | rule | `ProductController` 暴露 7 个 `/api/product/spus*` REST 端点，端点能通过 curl / 浏览器成功返回数据，ServiceException 走 400 + code/message 体 |
| AC-05 | rule | `getMenu()` 返回在「商品管理」children 下包含「SPU管理」项，path = `/home/spu-manage` |
| AC-06 | rule | 前端 `types/index.ts` 含 Spu/query/result/request 系列 TS 接口，字段数与类型与后端 Thrift 对齐 |
| AC-07 | rule | 前端 `api/index.ts` 的 `FrontierServiceImpl` 类实现了 7 个 SPU 方法，调用 `/api/product/spus*` 且带 token / 401 处理 |
| AC-08 | rule | `router/index.ts` 注册 `/home/spu-manage` 子路由，登录后可访问到页面 |
| AC-09 | rule | SPUManage 页面顶部三下拉级联：选一级→加载二级→选二级→加载三级；未选二级禁用三级、未选一级禁用二级 |
| AC-10 | rule | 未选三级分类时，"+ 添加SPU" 按钮 disabled，表格展示 "请先选择三级分类后查看SPU" |
| AC-11 | rule | 选中三级分类后，表格按分页展示 SPU；搜索框输入 SPU 名关键字后回车/点"搜索" 可过滤；状态筛选可叠加；"重置"清空所有筛选并回到第 1 页 |
| AC-12 | rule | 分页组件 total、页数与后端返回一致；切页、切页大小均触发刷新 |
| AC-13 | rule | "+ 添加SPU" 弹出表单，提交成功后端返回带 id，表格中立即出现新行（分类路径只读、品牌可选、描述可为空） |
| AC-14 | rule | 操作列「编辑」打开弹窗，回填 row 数据（不额外请求 getSpuById），修改保存后立即刷新显示；取消不影响原数据 |
| AC-15 | rule | 操作列「状态切换」按钮将该 SPU 状态在 0/1 间反置并保存成功，再次刷新后表格对应状态正确（禁用可通过搜索状态=禁用查到，启用反查不到该条禁用） |
| AC-16 | rule | 操作列「删除」经 ElMessageBox 二次确认后删除成功，列表移除该行，再查 id 可通过 NOT_FOUND 佐证 |
| AC-17 | rule | 同分类下同名 SPU 创建第二次 → 前端显示后端返回的 DUPLICATE_NAME 错误消息 |
| AC-18 | rule | 创建时 SPU 名称为空 → 前端校验失败 / 或后端 VALIDATION_ERROR → 错误可见 |
| AC-19 | rule | 菜单路径正确，页面访问 URL 为 `/home/spu-manage`，登出 / 401 重定向带 `redirect` 参数（沿用全局请求拦截器，无需单独改造） |
| AC-20 | rubric | 整体 UI 视觉与平台属性/品牌页面一致（卡片、间距、分页器、弹窗风格），页面无 TS 报错（vue-tsc 或 IDE diagnostics 0 error） |

AC-20 评分阈值：≥ 1/2（视觉一致性中等以上、零 TS 报错算通过）。
