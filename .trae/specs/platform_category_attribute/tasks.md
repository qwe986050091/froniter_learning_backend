# 平台分类与平台属性管理 实施任务清单

> 依赖：`spec.md` 已完成；所有任务按 **Thrift → 生成 → 后端 → 前端** 的严格顺序推进。只有前序任务完成才允许开始下一任务。

---

## 任务 1：新增 Thrift IDL (`product.thrift`)

- **Priority**: high
- **Status**: pending
- **Blocked By**: 无
- **Parents AC**: AC-01

### 描述
在 `d:\code\frontier_test_backend\src\main\resources\thrift\` 新增 `product.thrift`，内容严格遵循 `spec.md §2.1`：
- namespace `java org.example.thrift.product`；
- `include "common.thrift"`；
- struct：`Category`、`PlatformAttr`、`PlatformAttrValue`、`PlatformAttrWithValues`；
- query：`CategoryQuery`、`PlatformAttrQuery`、`PlatformAttrPageResult`；
- request：`CategoryCreateRequest`、`CategoryUpdateRequest`、`CategoryStatusRequest`、`PlatformAttrCreateRequest`、`PlatformAttrUpdateRequest`；
- service：`ProductService`（15 个方法，全部 `throws common.ServiceException`）。

### 任务级 Test Requirements

| TR 编号 | 类型 | 描述 / 通过条件 | 证据来源 |
|---|---|---|---|
| T1-R1 | rule | `product.thrift` 文件存在，`thrift --gen java product.thrift` 不报错（语法正确） | 本地 `thrift --gen java` 或 CI 生成阶段日志 |
| T1-R2 | rule | 文件包含所有 struct / service 定义，`namespace / include / 15 方法` 齐全，对照 spec 无缺漏 | Grep 关键字统计 + 与 §2.1 清单比对 |

### 交付物
- 新增：`[frontier_test_backend]/src/main/resources/thrift/product.thrift`

---

## 任务 2：执行 thriftGenerator.txt 同款命令生成代码

- **Priority**: high
- **Status**: pending
- **Blocked By**: Task 1
- **Parents AC**: AC-01

### 描述
执行 `src/main/resources/thrift/thriftGenerator.txt` 中同款的本地 thrift exe 命令（**不用 pom.xml 中的 exec-maven-plugin**）：

```
D:\code\thrift-0.24.0.exe --gen java:jakarta_annotations -out D:\code\frontier_test_backend\src\main\java D:\code\frontier_test_backend\src\main\resources\thrift\product.thrift
```

确认：
1. 命令退出码为 0；
2. 生成目录：`src/main/java/org/example/thrift/product/`；
3. 其中至少有 `ProductService.java`（含 `Processor` 内部类）、`Category.java`、`PlatformAttr.java`、`PlatformAttrValue.java`、`PlatformAttrWithValues.java`、`PlatformAttrQuery.java`、`PlatformAttrPageResult.java`、`PlatformAttrCreateRequest.java`、`PlatformAttrUpdateRequest.java`、`CategoryQuery.java`、`CategoryCreateRequest.java`、`CategoryUpdateRequest.java`、`CategoryStatusRequest.java`。

### 任务级 Test Requirements

| TR 编号 | 类型 | 描述 / 通过条件 | 证据来源 |
|---|---|---|---|
| T2-R1 | rule | `thrift-0.24.0.exe` 命令执行后退出码为 0，无 error 级输出 | shell 输出 |
| T2-R2 | rule | `src/main/java/org/example/thrift/product/ProductService.java` 存在，且文件内包含 `class Processor` | Grep 结果 |
| T2-R3 | rule | 生成文件数 ≥ 13（与上述清单对齐） | `ls` 数量统计 |

### 交付物
- 新增生成产物：`[frontier_test_backend]/src/main/java/org/example/thrift/product/` 目录下全部文件

---

## 任务 3：后端实现 FrontierServiceImpl 实现 ProductService.Iface

- **Priority**: high
- **Status**: pending
- **Blocked By**: Task 2
- **Parents AC**: AC-03, AC-04, AC-05, AC-06, AC-07, AC-08

### 描述
在 `d:\code\frontier_test_backend\src\main\java\org\example\service\FrontierServiceImpl.java` 中：

1. `implements` 列表新增 `ProductService.Iface`；
2. 新增三个 store：`categoryStore (id→Category)` / `attrStore (id→PlatformAttr)` / `attrValueStore (id→PlatformAttrValue)`，以及各自 `AtomicLong idSeq`；
3. 启动种子：
   - 3 个一级（手机 / 家电 / 服装）；
   - 每一级 3 个二级（如 手机→手机通讯、手机数码、手机配件）；
   - 每二级至少 2 个三级（总计至少 18 个三级）；
   - 每个三级至少 2 条平台属性（合计 ≥ 36 条属性，10/页 ≥ 4 页）；
   - 每条属性 3+ 条属性值。
4. 实现 15 个方法：
   - 分类 list/get/create/update/updateStatus/delete（含 HAS_CHILDREN 校验、分类下有属性也禁删）；
   - 属性 list/get/create/update/delete：
     - 创建：同分类 name 查重（`DUPLICATE_NAME`）；values 长度 <1 → `VALIDATION_ERROR`；
     - 更新：同左，values 覆盖（先删旧 values 再插新）；
     - 删除：级联删除该 attr 下所有 values。

### 任务级 Test Requirements

| TR 编号 | 类型 | 描述 / 通过条件 | 证据来源 |
|---|---|---|---|
| T3-R1 | rule | `FrontierServiceImpl.java` 编译通过（`mvn compile`）且类签名 implements `ProductService.Iface` | 编译日志 + Grep |
| T3-R2 | rule | 启动种子属性条数 ≥ 36：直接调 `listPlatformAttr(page=1,pageSize=10)` 返回 total ≥ 36 | 代码运行或单元调用日志 |
| T3-R3 | rule | 删除一级分类（有子）抛 `HAS_CHILDREN`；删除有属性绑定的三级分类也抛 | 对应 ServiceException code 字段值 |
| T3-R4 | rule | 同分类同名属性第二次创建返回 `DUPLICATE_NAME`；values 为空返回 `VALIDATION_ERROR` | 同上 |
| T3-R5 | rule | 删除属性后查该 attr id → 返回 `ServiceException code=NOT_FOUND` | 同上 |

### 交付物
- 修改：`[frontier_test_backend]/src/main/java/org/example/service/FrontierServiceImpl.java`

---

## 任务 4：后端 ThriftServerConfig 注册 ProductService

- **Priority**: high
- **Status**: pending
- **Blocked By**: Task 3
- **Parents AC**: AC-02

### 描述
在 `ThriftServerConfig.start()` 的 `TMultiplexedProcessor` 注册代码块中，新增一行：

```java
multiplexedProcessor.registerProcessor(
    ProductService.class.getSimpleName(),
    new ProductService.Processor<>(frontierService));
```

并更新启动日志行，使其打印 4 个 service 名字。

### 任务级 Test Requirements

| TR 编号 | 类型 | 描述 / 通过条件 | 证据来源 |
|---|---|---|---|
| T4-R1 | rule | 后端启动后日志 `Starting Thrift server on port: 9090 (services: AuthService, MenuService, BrandService, ProductService)` 出现 | 启动输出日志 |
| T4-R2 | rule | 端口 9090 正常监听且不抛 ClassNotFound | `netstat` + 日志无 ERROR |

### 交付物
- 修改：`[frontier_test_backend]/src/main/java/org/example/config/ThriftServerConfig.java`

---

## 任务 5：后端 ProductController（REST 暴露）

- **Priority**: high
- **Status**: pending
- **Blocked By**: Task 4
- **Parents AC**: AC-05, AC-06, AC-07, AC-08

### 描述
新建 `d:\code\frontier_test_backend\src\main\java\org\example\controller\ProductController.java`。

结构完全仿照 `BrandController`：

```
@Slf4j
@RestController
@RequestMapping("/api/product")
@RequireAuth
public class ProductController {
    // 分类：
    GET  /categories          listCategories(level, parentId)
    POST /categories          createCategory
    PUT  /categories/{id}     updateCategory
    PATCH/categories/{id}/status updateCategoryStatus
    DELETE/categories/{id}    deleteCategoryById

    // 平台属性：
    GET  /attrs               listPlatformAttrs(categoryId, name, status, page, pageSize)
    GET  /attrs/{id}          getPlatformAttrById
    POST /attrs               createPlatformAttr
    PUT  /attrs/{id}          updatePlatformAttr
    DELETE/attrs/{id}         deletePlatformAttrById
}
```

GET /attrs/{id} 返回 `PlatformAttrWithValues`（其他接口按 thrift 中对应 struct）。

ServiceException 捕获后统一 `ResponseEntity.badRequest().body(Map.of("code", e.code, "message", e.description))`。

### 任务级 Test Requirements

| TR 编号 | 类型 | 描述 / 通过条件 | 证据来源 |
|---|---|---|---|
| T5-R1 | rule | 所有接口能命中（可用 Postman / fetch：登录拿 token → 调 `/api/product/categories?level=1` 返回 JSON 数组） | HTTP 200 + JSON |
| T5-R2 | rule | `/api/product/attrs` 分页返回结构字段齐全：list/total/page/pageSize | 对照 AC-03 |
| T5-R3 | rule | 删除有子分类接口返回 HTTP 400 且 body.code == HAS_CHILDREN | 对照 AC-04 |
| T5-R4 | rule | `POST /attrs` 后再 `GET /attrs/{id}`，values 数量与创建值匹配 | 对照 AC-06 |

### 交付物
- 新增：`[frontier_test_backend]/src/main/java/org/example/controller/ProductController.java`

---

## 任务 6：后端 MenuService 菜单补充「商品管理/平台属性」

- **Priority**: medium
- **Status**: pending
- **Blocked By**: Task 5
- **Parents AC**: AC-09

### 描述
`FrontierServiceImpl.getMenu()` 返回的 `MenuItem` 树里，找到「商品管理」父节点，在其 children 新增：

```
id=platform-attr
name=平台属性
icon=SetUp (或任意 icon 字符串，前端不显示也可)
path=/home/platform-attr-manage
desc=平台分类与平台属性管理
```

### 任务级 Test Requirements

| TR 编号 | 类型 | 描述 / 通过条件 | 证据来源 |
|---|---|---|---|
| T6-R1 | rule | 登录后 `GET /api/menu` 返回 JSON 中 `商品管理.children` 含有一项 `name===平台属性` 且 `path.endsWith('platform-attr-manage')` | fetch 返回数据 |

### 交付物
- 修改：`[frontier_test_backend]/src/main/java/org/example/service/FrontierServiceImpl.java`

---

## 任务 7：前端补齐 TS 类型（types/index.ts）

- **Priority**: high
- **Status**: pending
- **Blocked By**: Task 1（仅需知道字段，可与任务 2 并行但建议串行确保稳定）
- **Parents AC**: AC-09..AC-14（所有前端 AC 的基础）

### 描述
在 `d:\code\frontier_test_admin\src\types\index.ts` 中品牌模块类型之后追加：

```
interface Category { id, name, parentId, level, sort, status, createTime }
interface PlatformAttr { id, name, categoryId, sort, status, createTime }
interface PlatformAttrValue { id, attrId, value, sort }
interface PlatformAttrWithValues extends PlatformAttr { values: PlatformAttrValue[] }
interface CategoryQuery { level?, parentId? }
interface PlatformAttrQuery { categoryId?, name?, status?, page?, pageSize? }
interface PlatformAttrPageResult { list, total, page, pageSize }
interface CategoryCreateRequest { name, parentId, level, sort?, status? }
interface CategoryUpdateRequest { id, name, parentId?, level?, sort?, status? }
interface PlatformAttrCreateRequest { name, categoryId, values: string[], sort?, status? }
interface PlatformAttrUpdateRequest { id, name, categoryId?, values: string[], sort?, status? }
```

在同一文件底部的 `interface FrontierService {` 中，加入 13 个方法签名：

```
listCategory(query?: CategoryQuery): Promise<Category[]>
createCategory(req: CategoryCreateRequest): Promise<Category>
updateCategory(req: CategoryUpdateRequest): Promise<Category>
updateCategoryStatus(req: { id:number, status:number }): Promise<{ success: boolean }>
deleteCategoryById(id: number): Promise<{ success: boolean }>

listPlatformAttr(query?: PlatformAttrQuery): Promise<PlatformAttrPageResult>
getPlatformAttrById(id: number): Promise<PlatformAttrWithValues>
createPlatformAttr(req: PlatformAttrCreateRequest): Promise<PlatformAttr>
updatePlatformAttr(req: PlatformAttrUpdateRequest): Promise<PlatformAttr>
deletePlatformAttrById(id: number): Promise<{ success: boolean }>
```

### 任务级 Test Requirements

| TR 编号 | 类型 | 描述 / 通过条件 | 证据来源 |
|---|---|---|---|
| T7-R1 | rule | 文件 TS 无语法错误（`GetDiagnostics` 无新增 error） | IDE/TSC 诊断 |
| T7-R2 | rule | 与 thrift product.thrift 字段一一对应（i64→number，optional→?） | 人工对照 |

### 交付物
- 修改：`[frontier_test_admin]/src/types/index.ts`

---

## 任务 8：前端补齐 api/index.ts（FrontierServiceImpl）

- **Priority**: high
- **Status**: pending
- **Blocked By**: Task 7
- **Parents AC**: AC-09..AC-14

### 描述
在 `d:\code\frontier_test_admin\src\api\index.ts` 的 `FrontierServiceImpl` 中，品牌模块方法之后追加 13 个方法（路径规则完全照搬品牌，品牌的 `/brand` 对应这里分类用 `/product/categories`、属性用 `/product/attrs`）。

注意：`updatePlatformAttr` 走 `PUT /product/attrs/${req.id}`，body 带 values 数组；`updateCategoryStatus` 走 `PATCH /product/categories/${id}/status`，body `{status}`；所有请求第三个参数 auth=true。

### 任务级 Test Requirements

| TR 编号 | 类型 | 描述 / 通过条件 | 证据来源 |
|---|---|---|---|
| T8-R1 | rule | `GetDiagnostics` 无新增 TS error；类型与 `FrontierService` 接口匹配 | IDE/TSC 诊断 |
| T8-R2 | rule | 浏览器 Network 下任意一次调用，请求头带 `Authorization: Bearer xxx` | DevTools Network |

### 交付物
- 修改：`[frontier_test_admin]/src/api/index.ts`

---

## 任务 9：前端新增路由 + 页面组件 PlatformAttrManage.vue

- **Priority**: high
- **Status**: pending
- **Blocked By**: Task 8
- **Parents AC**: AC-09, AC-10, AC-11, AC-12, AC-13, AC-14, AC-16

### 描述

**9.1 路由**  
在 `router/index.ts`：
- `import PlatformAttrManage from '../views/pages/PlatformAttrManage.vue'`
- Home children 追加 `{ path: 'platform-attr-manage', component: PlatformAttrManage }`

**9.2 页面**  
新建 `views/pages/PlatformAttrManage.vue`，交互如用户截图：

1. 顶部 3 个 `el-select` 级联（一级 / 二级 / 三级）；
2. 一级：页面 `onMounted` 调 `listCategory({level:1})`；
3. 一级变化 → 清空二、三级 → 调 `listCategory({level:2, parentId:一级id})`；
4. 二级变化 → 清空三级 → 调 `listCategory({level:3, parentId:二级id})`；
5. 「+ 添加平台属性」按钮：仅当三级选中才能点击（否则 disabled + `ElMessage.warning('请先选中三级分类')`）；
6. 表格列：序号、属性名称、属性值名称（多个 `el-tag` 两色循环，与截图接近）、操作（编辑/删除）；
7. 新增 / 编辑弹窗内：
   - 属性名（必填）、排序（默认 0）、状态（1/0 用 el-switch 或 el-select）；
   - 属性值：用 `el-input` + `@keyup.enter + 添加按钮` → 推入 `form.values: string[]`（数组），每个值可单独删除（`el-tag` 的 `closable`）。
8. 删除按钮：`ElMessageBox.confirm('确认删除属性「xxx」吗？','提示',{type:'warning'})` → 成功后刷新表格；
9. 分页：分页器 total / current-page / page-size 与品牌页一致，page-sizes [10,20,50,100]。
10. 搜索/筛选：页面 search-bar 保留「属性名称输入 + 状态下拉」，参数传给 `listPlatformAttr`。

### 任务级 Test Requirements

| TR 编号 | 类型 | 描述 / 通过条件 | 证据来源 |
|---|---|---|---|
| T9-R1 | rule | 浏览器打开 `/home/platform-attr-manage` → 渲染无错误 | 控制台无红色 Vue 异常 |
| T9-R2 | rule | 级联：一级选「手机」→ 二级出现 3 项 → 选第一项 → 三级出现 2+ 项（对照种子） | 截图或交互日志 |
| T9-R3 | rule | 三级未选时点「+ 添加平台属性」按钮不可用或提示 | 实际交互 |
| T9-R4 | rule | 新建属性「测试1」+ values:[A,B,C] → 保存后，表格当前行显示 3 个 tag | 截图 |
| T9-R5 | rule | 编辑该属性 → values 整体改成 [D,E] → 保存后行内显示 2 个 tag | 截图 |
| T9-R6 | rule | 删除该属性 → confirm 后行消失，Total 减 1 | 分页器 Total |
| T9-R7 | rule | 当前分类属性总条数 ≥ 12 → 分页器页码出现 2+；翻页高亮正确 | 截图 |
| T9-R8 | rubric | 与截图相似度评分（0-2），≥ 1 才算通过本任务 | 肉眼比对截图 + 实现 |

### 交付物
- 修改：`[frontier_test_admin]/src/router/index.ts`
- 新增：`[frontier_test_admin]/src/views/pages/PlatformAttrManage.vue`

---

## 任务 10：全流程联调 + 浏览器验收（对应 AC 独立证据）

- **Priority**: high
- **Status**: pending
- **Blocked By**: Tasks 1..9 全部 completed
- **Parents AC**: 全部 AC（01–16）

### 描述
独立在浏览器进行一轮端到端联调验证：

1. `mvn spring-boot:run` 启动后端，观察日志 ProductService 注册 OK（AC-01 / 02）；
2. `pnpm dev` 启动前端，登录 admin / 123456；
3. 进「平台属性」菜单页（AC-09）；
4. 点「一级=手机 → 二级=手机通讯 → 三级=手机 / 对讲机」（AC-10）；
5. 未选三级时点「+ 添加平台属性」验证禁用（AC-11）；
6. 选三级后新增，观察表格 tag 数量（AC-06 / 12）；
7. 用 Postman/控制台调删除有子分类的一级分类 → 返回 HAS_CHILDREN（AC-04）；
8. 翻到第 2 页表格，高亮正确（AC-14）；
9. 整体 UI 打分（AC-16）、代码一致性打分（AC-15）。

### 任务级 Test Requirements
每个对应 AC 要产生 1 条证据。

### 交付物
- 不新增代码文件；所有证据写入各任务 `Completion Evidence` 字段，Review 阶段汇总到 review.md。
