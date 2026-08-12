namespace java org.example.thrift.menu

include "common.thrift"

// 菜单/侧边栏相关服务
service MenuService {
    list<common.MenuItem> getMenu() throws (1: common.ServiceException e)
}
