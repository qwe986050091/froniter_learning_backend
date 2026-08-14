namespace java org.example.thrift.product

include "common.thrift"

// ========== 实体 ==========

/** 商品分类（一/二/三级树） */
struct Category {
    1: optional i64 id
    2: required string name
    3: required i64 parentId          // 一级分类 parentId = 0
    4: required i32 level             // 1=一级 2=二级 3=三级
    5: optional i32 sort
    6: optional i32 status            // 1 启用 0 禁用
    7: optional string createTime
}

/** 平台属性头部（绑定到三级分类） */
struct PlatformAttr {
    1: optional i64 id
    2: required string name
    3: required i64 categoryId        // 所属三级分类 id
    4: optional i32 sort
    5: optional i32 status            // 1 启用 0 禁用
    6: optional string createTime
}

/** 平台属性值（每个 attr 下多个） */
struct PlatformAttrValue {
    1: optional i64 id
    2: required i64 attrId
    3: required string value
    4: optional i32 sort
}

/** 平台属性详情：带属性值列表，前端表格行直接用 */
struct PlatformAttrWithValues {
    1: optional i64 id
    2: required string name
    3: required i64 categoryId
    4: optional i32 sort
    5: optional i32 status
    6: optional string createTime
    7: required list<PlatformAttrValue> values
}

// ========== 查询 ==========

struct CategoryQuery {
    1: optional i32 level            // 1|2|3，不传返回全量
    2: optional i64 parentId         // 指定父节点时必传（如取一级=0，取二级传一级id，取三级传二级id）
}

struct PlatformAttrQuery {
    1: optional i64 categoryId       // 三级分类 id
    2: optional string name          // 属性名模糊匹配
    3: optional i32 status           // 1 启用 0 禁用；不传=全部
    4: optional i32 page             // 从 1 开始
    5: optional i32 pageSize         // 默认 10
}

struct PlatformAttrPageResult {
    1: required list<PlatformAttrWithValues> list
    2: required i64 total
    3: required i32 page
    4: required i32 pageSize
}

// ========== 分类 CRUD 请求 ==========

struct CategoryCreateRequest {
    1: required string name
    2: required i64 parentId
    3: required i32 level
    4: optional i32 sort
    5: optional i32 status
}

struct CategoryUpdateRequest {
    1: required i64 id
    2: required string name
    3: optional i64 parentId
    4: optional i32 level
    5: optional i32 sort
    6: optional i32 status
}

struct CategoryStatusRequest {
    1: required i64 id
    2: required i32 status
}

// ========== 平台属性 CRUD 请求 ==========

struct PlatformAttrCreateRequest {
    1: required string name
    2: required i64 categoryId
    3: required list<string> values  // 一次提交所有属性值；非空
    4: optional i32 sort
    5: optional i32 status
}

struct PlatformAttrUpdateRequest {
    1: required i64 id
    2: required string name
    3: optional i64 categoryId
    4: required list<string> values  // 整体覆盖（旧值先删再插新）
    5: optional i32 sort
    6: optional i32 status
}

// ========== SPU ==========

/** SPU 实体（Standard Product Unit） */
struct Spu {
    1: optional i64 id
    2: required string name
    3: optional string description
    4: required i64 categoryId
    5: optional i64 brandId
    6: optional i32 sort
    7: optional i32 status
    8: optional string createTime
}

struct SpuQuery {
    1: optional i64 categoryId
    2: optional i64 brandId
    3: optional string name
    4: optional i32 status
    5: optional i32 page
    6: optional i32 pageSize
}

struct SpuPageResult {
    1: required list<Spu> list
    2: required i64 total
    3: required i32 page
    4: required i32 pageSize
}

struct SpuCreateRequest {
    1: required string name
    2: optional string description
    3: required i64 categoryId
    4: optional i64 brandId
    5: optional i32 sort
    6: optional i32 status
}

struct SpuUpdateRequest {
    1: required i64 id
    2: required string name
    3: optional string description
    4: optional i64 categoryId
    5: optional i64 brandId
    6: optional i32 sort
    7: optional i32 status
}

struct SpuStatusRequest {
    1: required i64 id
    2: required i32 status
}

struct SpuSortRequest {
    1: required i64 id
    2: required i32 sort
}

// ========== Service ==========

service ProductService {

    // ---- 分类 ----
    list<Category> listCategory(1: CategoryQuery query) throws (1: common.ServiceException e)
    Category getCategoryById(1: i64 id) throws (1: common.ServiceException e)
    Category createCategory(1: CategoryCreateRequest req) throws (1: common.ServiceException e)
    Category updateCategory(1: CategoryUpdateRequest req) throws (1: common.ServiceException e)
    bool updateCategoryStatus(1: CategoryStatusRequest req) throws (1: common.ServiceException e)
    bool deleteCategoryById(1: i64 id) throws (1: common.ServiceException e)

    // ---- 平台属性 ----
    PlatformAttrPageResult listPlatformAttr(1: PlatformAttrQuery query) throws (1: common.ServiceException e)
    PlatformAttrWithValues getPlatformAttrById(1: i64 id) throws (1: common.ServiceException e)
    PlatformAttr createPlatformAttr(1: PlatformAttrCreateRequest req) throws (1: common.ServiceException e)
    PlatformAttr updatePlatformAttr(1: PlatformAttrUpdateRequest req) throws (1: common.ServiceException e)
    bool deletePlatformAttrById(1: i64 id) throws (1: common.ServiceException e)

    // ---- SPU ----
    SpuPageResult listSpu(1: SpuQuery query) throws (1: common.ServiceException e)
    Spu getSpuById(1: i64 id) throws (1: common.ServiceException e)
    Spu createSpu(1: SpuCreateRequest req) throws (1: common.ServiceException e)
    Spu updateSpu(1: SpuUpdateRequest req) throws (1: common.ServiceException e)
    bool updateSpuStatus(1: SpuStatusRequest req) throws (1: common.ServiceException e)
    bool updateSpuSort(1: SpuSortRequest req) throws (1: common.ServiceException e)
    bool deleteSpuById(1: i64 id) throws (1: common.ServiceException e)
}
