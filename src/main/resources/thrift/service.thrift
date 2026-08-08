namespace java org.example.thrift

struct Request {
    1: required string id
    2: optional string name
    3: optional map<string, string> metadata
}

struct Response {
    1: required string code
    2: required string message
    3: optional string data
}

exception ServiceException {
    1: required string code
    2: required string description
}

service FrontierService {
    Response processRequest(1: Request req) throws (1: ServiceException e)
    Response healthCheck()
}