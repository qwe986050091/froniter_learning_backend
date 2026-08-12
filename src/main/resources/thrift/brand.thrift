namespace java org.example.thrift.brand

include "common.thrift"

// 品牌实体
struct Brand {
    1: optional i64 id
    2: required string name
    3: optional string logo
    4: required string firstLetter
    5: required string category
    6: optional string description
    7: optional i32 sort
    8: optional i32 status // 1 启用 0 禁用
    9: optional string createTime
}

// 品牌搜索条件
struct BrandQuery {
    1: optional string name
    2: optional i32 status // 1 启用 0 禁用；不传表示全部
    3: optional i32 page // 页码，从 1 开始
    4: optional i32 pageSize // 每页条数
}

// 分页结果
struct BrandPageResult {
    1: required list<Brand> list
    2: required i64 total
    3: required i32 page
    4: required i32 pageSize
}

// 新增品牌请求
struct BrandCreateRequest {
    1: required string name
    2: optional string logo
    3: required string firstLetter
    4: required string category
    5: optional string description
    6: optional i32 sort
    7: optional i32 status
}

// 修改品牌请求
struct BrandUpdateRequest {
    1: required i64 id
    2: required string name
    3: optional string logo
    4: required string firstLetter
    5: required string category
    6: optional string description
    7: optional i32 sort
    8: optional i32 status
}

// 仅更新品牌状态的请求
struct BrandStatusRequest {
    1: required i64 id
    2: required i32 status
}

// 仅更新品牌排序的请求
struct BrandSortRequest {
    1: required i64 id
    2: required i32 sort
}

// 品牌管理相关服务
service BrandService {
    // 分页查询
    BrandPageResult listBrand(1: BrandQuery query) throws (1: common.ServiceException e)
    // 按 ID 查详情
    Brand getBrandById(1: i64 id) throws (1: common.ServiceException e)
    // 新增
    Brand createBrand(1: BrandCreateRequest req) throws (1: common.ServiceException e)
    // 修改
    Brand updateBrand(1: BrandUpdateRequest req) throws (1: common.ServiceException e)
    // 按 ID 删除
    bool deleteBrandById(1: i64 id) throws (1: common.ServiceException e)
    // 更新启用/禁用状态
    bool updateBrandStatus(1: BrandStatusRequest req) throws (1: common.ServiceException e)
    // 更新排序
    bool updateBrandSort(1: BrandSortRequest req) throws (1: common.ServiceException e)
}
