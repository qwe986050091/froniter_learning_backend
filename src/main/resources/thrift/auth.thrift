namespace java org.example.thrift.auth

include "common.thrift"

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
    5: optional common.UserInfo userInfo
}

// 鉴权/登录相关服务
service AuthService {
    LoginResponse login(1: LoginRequest req) throws (1: common.ServiceException e)
}
