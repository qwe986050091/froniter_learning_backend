namespace java org.example.thrift.common

// 用户信息（跨域共享，如 Auth 登录返回、后端鉴权上下文）
struct UserInfo {
    1: required string userId
    2: required string username
    3: optional string nickname
    4: optional string avatar
    5: optional list<string> roles
    6: optional map<string, string> extra
}

// 侧边栏菜单项（后端下发给前端）
struct MenuItem {
    1: required string id
    2: required string name
    3: optional string icon
    4: optional string path
    5: optional string desc
    6: optional list<MenuItem> children
}

// 统一业务异常（所有 service 共用）
exception ServiceException {
    1: required string code
    2: required string description
}
