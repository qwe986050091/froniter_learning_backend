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

exception ServiceException {
    1: required string code
    2: required string description
}

service FrontierService {
    LoginResponse login(1: LoginRequest req) throws (1: ServiceException e)
}