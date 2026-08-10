namespace java org.example.thrift

struct LoginRequest {
    1: required string username
    2: required string password
    3: optional string captcha
    4: optional string loginType
}

struct LoginResponse {
    1: required string code
    2: required string message
    3: optional string token
    4: optional string refreshToken
    5: optional UserInfo userInfo
}

struct UserInfo {
    1: required string userId
    2: required string username
    3: optional string nickname
    4: optional string avatar
    5: optional list<string> roles
    6: optional map<string, string> extra
}

struct MenuItem {
    1: required string id
    2: required string name
    3: optional string icon
    4: optional string path
    5: optional list<MenuItem> children
}

exception ServiceException {
    1: required string code
    2: required string description
}

service FrontierService {
    LoginResponse login(1: LoginRequest req) throws (1: ServiceException e)
    list<MenuItem> getMenu() throws (1: ServiceException e)
}