# SPU 管理 实施任务清单

> 依赖：`spec.md` 已完成；任务按「**Thrift → thrift.exe 生成 → 后端 → 前端**」的严格顺序推进。前序任务通过后才允许开始下一任务。

---

## 任务 1：修改 Thrift IDL（product.thrift）

- **Priority**: high
- **Status**: pending
- **Blocked By**: 无
- **Parents AC**: AC-01

### 描述
在 [product.thrift](file:///d:/code/frontier_test_backend/src/main/resources/thrift/product.thrift) 中追加 SPU 相关定义（不新建文件，保持商品域聚合）：

1. 新增 struct：`Spu` / `SpuQuery` / `SpuPageResult` / `SpuCreateRequest` / `SpuUpdateRequest` / `SpuStatusRequest` / `SpuSortRequest`。
2. 在现有的 `service ProductService { ... }` 末尾追加 7 个方法：`listSpu / getSpuById / createSpu / updateSpu / updateSpuStatus / updateSpuSort / deleteSpuById`，每个方法 `throws (1: common.ServiceException e)`。

参考命名/字段完全对齐 spec.md §2.1。

### 任务级 Test Requirements

| TR 编号 | 类型 | 描述 / 通过条件 | 证据来源 |
|---|---|---|---|
| T1-R1 | rule | `product.thrift` 语法正确，`thrift.exe --gen java product.thrift` 不报错 | 执行 thrift.exe 退出码=0 |
| T1-R2 | rule | 7 个 struct + 7 个方法齐全（grep 关键字核对） | Grep 结果对照 |
| T1-R3 | rule | namespace / include 行未被破坏，原有分类/平台属性定义无删除或改写 | diff / IDE 诊断 |

### 交付物
- 修改：`[frontier_test_backend]/src/main/resources/thrift/product.thrift`

---

## 任务 2：用 thrift.exe 生成 Java

- **Priority**: high
- **Status**: pending
- **Blocked By**: Task 1
- **Parents AC**: AC-01, AC-02

### 描述
使用 `thriftGenerator.txt` 同款命令重新生成 `product.thrift`：

```
D:\code\thrift-0.24.0.exe --gen java:jakarta_annotations -out D:\code\frontier_test_backend\src\main\java D:\code\frontier_test_backend\src\main\resources\thrift\product.thrift
```

核对：
1. 退出码 0，无 error 输出；
2. `src/main/java/org/example/thrift/product/` 下新增：`Spu.java`、`SpuQuery.java`、`SpuPageResult.java`、`SpuCreateRequest.java`、`SpuUpdateRequest.java`、`SpuStatusRequest.java`、`SpuSortRequest.java`；
3. `ProductService.java` 被更新（包含 7 个 SPU 方法 & `Iface` 接口签名 + `Processor` 能分发这 7 个方法）。

### 任务级 Test Requirements

| TR 编号 | 类型 | 描述 / 通过条件 | 证据来源 |
|---|---|---|---|
| T2-R1 | rule | thrift 命令退出码 = 0 | shell 输出 |
| T2-R2 | rule | 7 个新增 Spu*.java 文件存在 | `ls` / Glob |
| T2-R3 | rule | `ProductService.java` 中包含 `listSpu / createSpu` 等方法名（grep 7 个都命中） | Grep 结果 |

### 交付物
- 修改（生成物）：`[frontier_test_backend]/src/main/java/org/example/thrift/product/` 下对应文件

---

## 任务 3：FrontierServiceImpl 实现 SPU 7 方法 + 种子

- **Priority**: high
- **Status**: pending
- **Blocked By**: Task 2
- **Parents AC**: AC-03, AC-17, AC-18

### 描述
在 [FrontierServiceImpl.java](file:///d:/code/frontier_test_backend/src/main/java/org/example/service/FrontierServiceImpl.java)：

1. `implements` 列表（已经有 `ProductService.Iface`）不变，保证实现 7 个新方法（生成器会更新 Iface 签名，编译不过自动告警）。
2. 新增 `spuStore: Map<Long, Spu>`、`AtomicLong spuSeq`。
3. **种子**：在构造阶段（同品牌/属性 seed 位置）写入 ≥ 31 条 SPU，包含：
   - OPPO、vivo、华为（截图三条，按截图的简介文字抄），其余 28+ 个手机品牌 SPU；
   - 每条 `categoryId` 都指向现有的「手机 → 手机通讯 → 手机」三级分类 id（需要在 seed 内拿到该 id；或直接按 categoryStore 内的层级名查 id）；
   - 每条 `brandId` 可选地填入对应品牌 id（可空），不做存在性校验；
   - 每条 description 为 2~5 句真实中文简介，不用 Lorem。
4. 7 个方法实现：
   - listSpu：`categoryId` 必筛（空 = 空集）、name 模糊匹配 `contains`、status 等值、brandId 等值；默认 sort ASC + id ASC；分页正确；total 为过滤后总数。
   - getSpuById：不存在 → `ServiceException("NOT_FOUND", ...)`。
   - createSpu：name 空/空串 → `VALIDATION_ERROR`；categoryId 未设置（≤0）→ `VALIDATION_ERROR`；同 `categoryId` 下相同 `name.trim()`（忽略大小写？不忽略，默认精确 trim 后等值）已存在 → `DUPLICATE_NAME`。
   - updateSpu：id 不存在 → `NOT_FOUND`；同分类下重名（排除自身 id）→ `DUPLICATE_NAME`。
   - updateSpuStatus / updateSpuSort：id 不存在 → `NOT_FOUND`；返回 true。
   - deleteSpuById：id 不存在 → `NOT_FOUND`；返回 true。

### 任务级 Test Requirements

| TR 编号 | 类型 | 描述 / 通过条件 | 证据来源 |
|---|---|---|---|
| T3-R1 | rule | `mvn -q -DskipTests compile` 编译通过 | 编译日志 |
| T3-R2 | rule | 启动后调用 listSpu(page=1,pageSize=10)，返回 total ≥ 31 | 运行时断言 / 浏览器 F12 接口响应 |
| T3-R3 | rule | 同分类同名 create 第二次 → 收到 ServiceException code=DUPLICATE_NAME | 接口调用或前端 toast |
| T3-R4 | rule | create 时 name 空串 / 或 categoryId 未设置 → VALIDATION_ERROR | 同上 |
| T3-R5 | rule | updateSpuStatus / updateSpuSort / deleteSpuById 传不存在 id → code=NOT_FOUND | 同上 |

### 交付物
- 修改：`[frontier_test_backend]/src/main/java/org/example/service/FrontierServiceImpl.java`

---

## 任务 4：ProductController 新增 /api/product/spus* 7 个端点

- **Priority**: high
- **Status**: pending
- **Blocked By**: Task 3
- **Parents AC**: AC-04

### 描述
在 [ProductController.java](file:///d:/code/frontier_test_backend/src/main/java/org/example/controller/ProductController.java) 末尾（删除平台属性之后、helpers 之前）增加：

```
GET    /spus
POST   /spus
GET    /spus/{id}
PUT    /spus/{id}
PATCH  /spus/{id}/status
PATCH  /spus/{id}/sort
DELETE /spus/{id}
```

模式完全对齐现有的分类/属性端点：
- 查询用 `@RequestParam`，分页 default `page=1 / pageSize=10`；
- 创建/更新 body 用 `Map<String, Object>` 转 struct，用已有的 `str / toInt / toLong` helpers；
- `ServiceException` → 400 + `errorOf(e)`；`TException` → 500；成功 → `ResponseEntity.ok(...)`。

### 任务级 Test Requirements

| TR 编号 | 类型 | 描述 / 通过条件 | 证据来源 |
|---|---|---|---|
| T4-R1 | rule | 7 个端点都能通过浏览器/Postman/curl 返回预期 HTTP 状态与 JSON | 接口调用 |
| T4-R2 | rule | createSpu 触发 DUPLICATE_NAME 时 HTTP 400，响应体包含 `{ code, message }`（和现有 attrs 删除失败返回格式一致） | 接口调用 |
| T4-R3 | rule | 编译通过，`frontierService.listSpu` 被 controller 调用且路径、参数映射正确 | 代码审查 + IDE diagnostics |

### 交付物
- 修改：`[frontier_test_backend]/src/main/java/org/example/controller/ProductController.java`

---

## 任务 5：菜单新增「SPU管理」项

- **Priority**: high
- **Status**: pending
- **Blocked By**: Task 4（可与 T4 并行）
- **Parents AC**: AC-05, AC-19

### 描述
在 [FrontierServiceImpl.java](file:///d:/code/frontier_test_backend/src/main/java/org/example/service/FrontierServiceImpl.java) 内 `getMenu()` 方法中，在「商品管理」分组的 `children` List 里，`platform-attr` 项之后新增一项：id=`spu-manage`、name=`SPU管理`、icon=`Goods`（找不到图标的 fallback：`CollectionTag` 或 `Box`）、path=`/home/spu-manage`。

### 任务级 Test Requirements

| TR 编号 | 类型 | 描述 / 通过条件 | 证据来源 |
|---|---|---|---|
| T5-R1 | rule | 登录后前端 `getMenu()` 返回体在「商品管理」children 里包含 name=SPU管理 的项，path 以 `/home/spu-manage` 结尾 | F12 menu 接口响应 |
| T5-R2 | rule | 侧边栏能看到该项，点击后路由跳转到 `/home/spu-manage`，HomePage 下 router-view 渲染对应页面 | 浏览器手动验证 |

### 交付物
- 修改：`[frontier_test_backend]/src/main/java/org/example/service/FrontierServiceImpl.java`（getMenu 部分）

---

## 任务 6：前端 TS 类型补齐（types/index.ts）

- **Priority**: high
- **Status**: pending
- **Blocked By**: Task 1（Thrift 定义已明确，不需等后端完成）
- **Parents AC**: AC-06

### 描述
在 [types/index.ts](file:///d:/code/frontier_test_admin/src/types/index.ts) 追加：

- `interface Spu`：id/name/description/categoryId/brandId/sort/status/createTime（大部分 optional，name/categoryId 必填）
- `interface SpuQuery`：categoryId? / brandId? / name? / status? / page? / pageSize?
- `interface SpuPageResult`：list: Spu[] + total / page / pageSize
- `interface SpuCreateRequest`：name / categoryId；description? / brandId? / sort? / status?
- `interface SpuUpdateRequest`：id / name；description? / categoryId? / brandId? / sort? / status?
- `interface SpuStatusRequest`：id / status
- `interface SpuSortRequest`：id / sort

并在 `interface FrontierService` 中追加 7 个 SPU 方法签名，参数/返回类型与 Backend Thrift & REST 对齐。

### 任务级 Test Requirements

| TR 编号 | 类型 | 描述 / 通过条件 | 证据来源 |
|---|---|---|---|
| T6-R1 | rule | TS 接口字段与 product.thrift 对齐（optional 字段加 `?`，类型 number / string / undefined 合理） | IDE diagnostics 无错误 |
| T6-R2 | rule | `FrontierService` 接口扩展编译通过 | IDE diagnostics 0 error / vue-tsc 不抛 TS 错 |

### 交付物
- 修改：`[frontier_test_admin]/src/types/index.ts`

---

## 任务 7：前端 API 封装补齐（api/index.ts）

- **Priority**: high
- **Status**: pending
- **Blocked By**: Task 6
- **Parents AC**: AC-07

### 描述
在 [api/index.ts](file:///d:/code/frontier_test_admin/src/api/index.ts) 的 `FrontierServiceImpl` 类中，紧接平台属性一组方法之后，添加 7 个 SPU 方法：
- `listSpu(query?)` → `GET /api/product/spus?xxx`（URLSearchParams 方式拼 query，不传用默认值）
- `getSpuById(id)` → `GET /product/spus/{id}`（注意 base path 已包含 `/api`）
- `createSpu(req)` → `POST /product/spus`，body = req
- `updateSpu(req)` → `PUT /product/spus/{req.id}`，body = req（去掉 id 放入路径？保留 body 里也没关系，按现有 `updateBrand / updatePlatformAttr` 的写法来）
- `updateSpuStatus(req)` → `PATCH /product/spus/{req.id}/status`，body = `{ status: req.status }`
- `updateSpuSort(req)` → `PATCH /product/spus/{req.id}/sort`，body = `{ sort: req.sort }`
- `deleteSpuById(id)` → `DELETE /product/spus/{id}`

所有方法走 `this.request<T>(url, init, true)`（第三个参数 true = 携带 token），保持当前 request wrapper 的 401 自动登出。

### 任务级 Test Requirements

| TR 编号 | 类型 | 描述 / 通过条件 | 证据来源 |
|---|---|---|---|
| T7-R1 | rule | 方法名/参数名/返回 Promise 类型与 `FrontierService` 接口匹配，不触发 TS 报错 | IDE diagnostics |
| T7-R2 | rule | URL 与 Task 4 后端 ProductController 的 7 个端点完全对应 | 代码审查对照 |

### 交付物
- 修改：`[frontier_test_admin]/src/api/index.ts`

---

## 任务 8：前端路由补齐

- **Priority**: high
- **Status**: pending
- **Blocked By**: Task 9（SPUManage.vue 创建，可并行但先在 tasks 里放在 SPUManage.vue 前更合理；实际依赖：仅需 import 时文件存在）
- **Parents AC**: AC-08, AC-19

### 描述
在 [router/index.ts](file:///d:/code/frontier_test_admin/src/router/index.ts)：
1. `import SPUManage from '../views/pages/SPUManage.vue'`
2. `/home` 的 children 中新增 `{ path: 'spu-manage', component: SPUManage }`（路径与后端菜单对齐）

### 任务级 Test Requirements

| TR 编号 | 类型 | 描述 / 通过条件 | 证据来源 |
|---|---|---|---|
| T8-R1 | rule | 登录后浏览器直接访问 `http://localhost:5174/home/spu-manage` 能渲染 SPUManage，404 不命中 | 浏览器访问 |
| T8-R2 | rule | 路由 children 的 path 不含前导 `/`，与其他子路由一致 | 代码审查 |

### 交付物
- 修改：`[frontier_test_admin]/src/router/index.ts`

---

## 任务 9：创建 SPUManage.vue 页面

- **Priority**: high
- **Status**: pending
- **Blocked By**: Task 7（API 封装就绪）、Task 8（路由就绪）
- **Parents AC**: AC-09, AC-10, AC-11, AC-12, AC-13, AC-14, AC-15, AC-16, AC-20

### 描述
在 `src/views/pages/` 新建 `SPUManage.vue`。可参考 [PlatformAttrManage.vue](file:///d:/code/frontier_test_admin/src/views/pages/PlatformAttrManage.vue) 的 UI 结构与交互，但单文件写完整（暂不拆 AttrToolbar/AttrTable 子组件，保持易读）。

页面结构要点：

1. **toolbar 卡片**（白底+边框+圆角 `16px padding / margin-bottom 12px / border-radius 8px`）：
   - 第一行 `.category-cascader`：三下拉级联（level1 / level2 / level3，每个 180px、`margin-left 12px` 间距）。
   - 第二行 `.toolbar-right`（`space-between`，左侧搜索表单，右侧「+ 添加SPU」按钮）。
     - 搜索表单：「SPU名称」180px el-input；「状态」120px el-select（label="全部"/启用(1)/禁用(0)）；「搜索」「重置」按钮。
     - `+ 添加SPU`：type=primary，禁用条件 `!selectedLevel3Id || loading`。

2. **table-card 卡片**（白底+边框+圆角 `16px padding`）：
   - el-table `v-loading / border / stripe`。
   - 空态：未选三级分类 → "请先选择三级分类后查看SPU"；已选空列表 → "暂无数据"。
   - 列：序号 / SPU名称（160+） / SPU描述（min-width 560 多行自动换行） / 操作（width 240，4 个按钮）。
   - 操作列：
     1. el-button 蓝色 link `Plus`（打开添加弹窗）。
     2. el-button 黄色/橘色 link `Edit`（打开编辑弹窗）。
     3. el-button 灰色 link `Switch` / `SwitchButton`（切换状态：先确认提示，再反置 status 调 updateSpuStatus）。
     4. el-button 红色 link `Delete`（ElMessageBox.confirm 再删）。
   - pagination：margin-top 16，flex justify flex-end；`layout=total, sizes, prev, pager, next, jumper`；page-sizes `[10,20,50,100]`。

3. **新增/编辑弹窗**（el-dialog `v-model:dialogVisible`，宽度 620px，`@closed="resetForm"`）：
   - 所属分类：只读 `<div class="category-readonly">{{ categoryPathLabel }}</div>`（`categoryPathLabel = `${l1Name} / ${l2Name} / ${l3Name}` `）
   - SPU 名称：`el-input`，必填 1-100，rules trigger=blur。
   - SPU 描述：`el-input type="textarea" rows=4 maxlength=2000 show-word-limit`，可选。
   - 关联品牌：`el-select`（placeholder="请选择品牌，可选"，width 240，可清空 clearable；选项来源：`onMounted` 额外调一次 `listBrand(pageSize: 1000)` 拉一次，用 name 做 label、id 做 value）。
   - 排序：`el-input-number` min=0 max=9999，默认 0。
   - 状态：`el-switch` active=1 inactive=0，默认 1。
   - 提交：`handleSubmit` 校验 → 调 `createSpu` / `updateSpu` → 成功 toast → 关弹窗 → `loadList()`。
   - 取消：直接关弹窗 → `@closed -> resetForm()` 清副本，不会污染原数据（直接 copy 值 + values 新数组 机制与平台属性页面相同）。

4. **样式**：复用 PlatformAttrManage 的 `.toolbar / .category-cascader / .toolbar-right / .search-form / .table-card / .pagination / .category-readonly` 样式块，保证与平台属性 / 品牌页面视觉一致。

### 任务级 Test Requirements

| TR 编号 | 类型 | 描述 / 通过条件 | 证据来源 |
|---|---|---|---|
| T9-R1 | rule | IDE diagnostics 0 error / vue-tsc（单独针对该文件）无报错 | IDE / vue-tsc 输出 |
| T9-R2 | rule | 三下拉联动：选一级 → 二级 options 加载、二级解锁；选二级 → 三级加载、三级解锁；空选一级 → 二三级 disabled | 浏览器手动 / 截图对比 |
| T9-R3 | rule | 未选三级 → 添加SPU按钮 disabled；表格显示「请先选择三级分类…」 | 同上 |
| T9-R4 | rule | 搜索输入 SPU 名关键字，搜索后列表过滤；重置清空关键字并回到第 1 页 | 浏览器手动验证 |
| T9-R5 | rule | 分页器 total/页数正确；切 page / pageSize 均触发刷新 | 同上 |
| T9-R6 | rule | 新增SPU提交后，表格立即出现新行，正确显示 | 同上 |
| T9-R7 | rule | 编辑时表单从 row 回填（不额外调 getSpuById），取消不影响原数据；保存后立即刷新 | 同上 |
| T9-R8 | rule | 切换状态后，对应行状态在后端已反置，可通过"禁用"搜索查到或排除（取决于动作前状态） | 后端接口 / 浏览器手动 |
| T9-R9 | rule | 删除确认后，行立即消失；再用 getSpuById 查为 NOT_FOUND（或通过搜索名称确认已不在） | 浏览器手动验证 |
| T9-R10 | rubric | 视觉一致性（卡片/间距/分页器/弹窗），得分 ≥1/2 | 视觉自检 + 截图对比 |

### 交付物
- 新增：`[frontier_test_admin]/src/views/pages/SPUManage.vue`

---

## 任务 10：后端启动 + 联调验证

- **Priority**: high
- **Status**: pending
- **Blocked By**: Task 1–9
- **Parents AC**: AC-03, AC-04, AC-05, AC-08, AC-10, AC-11, AC-12, AC-13, AC-14, AC-15, AC-16, AC-17, AC-18, AC-19, AC-20

### 描述
1. 后端：`mvn spring-boot:run`（或当前工程已有启动方式）启动，监听 8080（或现有端口），确保 no compile error；
2. 前端：`pnpm dev` 启动；
3. 登录 `admin/123456` → 点击「商品管理 / SPU管理」菜单；
4. 完整跑一遍 AC 中的关键验证：
   - 三下拉联动 + 列表分页（≥4 页）
   - 搜索/重置
   - 新增（成功、同分类重名失败、名称为空失败）
   - 编辑（取消不改数据、保存生效）
   - 状态切换（成功后搜索筛选正确）
   - 删除（确认后删除成功）
5. IDE diagnostics 全 0 error，`vue-tsc` 不再有 SPU 相关新错误。

### 任务级 Test Requirements

| TR 编号 | 类型 | 描述 / 通过条件 | 证据来源 |
|---|---|---|---|
| T10-R1 | rule | 后端启动无异常，接口 `/api/product/spus?categoryId=&page=1&pageSize=10` 返回 total≥31 | 运行日志 + 接口响应 |
| T10-R2 | rule | 浏览器登录后完成一次完整 CRUD + 启禁用 + 搜索 + 分页切换全流程 | 浏览器手动验证 + 截图 |
| T10-R3 | rule | 前端文件 IDE diagnostics 全部 clean（0 error） | IDE diagnostics |

### 交付物
- 验证通过的运行证据，无新增错误。
