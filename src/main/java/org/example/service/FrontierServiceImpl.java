package org.example.service;

import org.example.thrift.auth.AuthService;
import org.example.thrift.auth.LoginRequest;
import org.example.thrift.auth.LoginResponse;
import org.example.thrift.brand.Brand;
import org.example.thrift.brand.BrandCreateRequest;
import org.example.thrift.brand.BrandPageResult;
import org.example.thrift.brand.BrandQuery;
import org.example.thrift.brand.BrandService;
import org.example.thrift.brand.BrandSortRequest;
import org.example.thrift.brand.BrandStatusRequest;
import org.example.thrift.brand.BrandUpdateRequest;
import org.example.thrift.common.MenuItem;
import org.example.thrift.common.ServiceException;
import org.example.thrift.common.UserInfo;
import org.example.thrift.menu.MenuService;
import org.apache.thrift.TException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Component
public class FrontierServiceImpl
        implements AuthService.Iface, MenuService.Iface, BrandService.Iface {

    // ==================== 用户/登录 ====================
    private final Map<String, UserInfo> userStore = new ConcurrentHashMap<>();
    private final Map<String, String> passwordStore = new ConcurrentHashMap<>();
    private final TokenService tokenService;

    public FrontierServiceImpl(TokenService tokenService) {
        this.tokenService = tokenService;

        UserInfo admin = new UserInfo();
        admin.setUserId("1");
        admin.setUsername("admin");
        admin.setNickname("Administrator");
        admin.setAvatar("/avatars/admin.png");
        admin.setRoles(List.of("ADMIN", "USER"));
        admin.setExtra(Map.of("department", "IT", "level", "9"));
        userStore.put("admin", admin);
        passwordStore.put("admin", "123456");

        UserInfo user = new UserInfo();
        user.setUserId("2");
        user.setUsername("user");
        user.setNickname("Normal User");
        user.setAvatar("/avatars/user.png");
        user.setRoles(List.of("USER"));
        user.setExtra(Map.of("department", "Operations", "level", "3"));
        userStore.put("user", user);
        passwordStore.put("user", "user123");
    }

    @Override
    public LoginResponse login(LoginRequest req) throws ServiceException, TException {
        if (req == null || req.getUsername() == null || req.getUsername().isEmpty()) {
            throw new ServiceException("INVALID_PARAM", "Username is required");
        }

        if (req.getPassword() == null || req.getPassword().isEmpty()) {
            throw new ServiceException("INVALID_PARAM", "Password is required");
        }

        UserInfo userInfo = userStore.get(req.getUsername());
        if (userInfo == null) {
            throw new ServiceException("USER_NOT_FOUND", "User does not exist: " + req.getUsername());
        }

        String storedPassword = passwordStore.get(req.getUsername());
        if (storedPassword == null || !storedPassword.equals(req.getPassword())) {
            throw new ServiceException("PASSWORD_INVALID", "Password is incorrect");
        }

        String token = tokenService.generateToken(req.getUsername());
        String refreshToken = tokenService.generateRefreshToken(req.getUsername());

        LoginResponse response = new LoginResponse();
        response.setCode("200");
        response.setMessage("Login successful");
        response.setToken(token);
        response.setRefreshToken(refreshToken);
        response.setUserInfo(userInfo);

        return response;
    }

    public UserInfo getUserByToken(String token) {
        String username = tokenService.validateToken(token);
        if (username == null) {
            return null;
        }
        return userStore.get(username);
    }

    public void logout(String token) {
        tokenService.invalidateToken(token);
    }

    // ==================== 菜单 ====================
    @Override
    public List<MenuItem> getMenu() throws ServiceException, TException {
        List<MenuItem> menu = new ArrayList<>();

        menu.add(new MenuItem("home", "首页")
                .setIcon("HomeFilled")
                .setPath("/home")
                .setDesc("这里是系统首页总览。"));

        MenuItem dataGroup = new MenuItem("group1", "商品管理")
                .setIcon("DataBoard")
                .setDesc("这里是商品管理分组。");
        dataGroup.setChildren(List.of(
                new MenuItem("brand-manage", "品牌管理")
                        .setIcon("Odometer")
                        .setPath("/home/brand-manage")
                        .setDesc("这里是品牌管理页面。"),
                new MenuItem("statistics", "统计分析")
                        .setIcon("TrendCharts")
                        .setPath("/home/statistics")
                        .setDesc("这里是统计分析页面。")
        ));
        menu.add(dataGroup);

        MenuItem systemGroup = new MenuItem("group2", "系统设置")
                .setIcon("Setting")
                .setDesc("这里是系统设置分组。");
        MenuItem moreGroup = new MenuItem("group2-1", "更多设置")
                .setIcon("Tools")
                .setDesc("这里是更多设置分组。");
        moreGroup.setChildren(List.of(
                new MenuItem("system-config", "系统配置")
                        .setIcon("Monitor")
                        .setPath("/home/system-config")
                        .setDesc("这里是系统配置页面。")
        ));
        systemGroup.setChildren(List.of(
                new MenuItem("user-manage", "用户管理")
                        .setIcon("User")
                        .setPath("/home/user-manage")
                        .setDesc("这里是用户管理页面。"),
                new MenuItem("role-manage", "角色管理")
                        .setIcon("Avatar")
                        .setPath("/home/role-manage")
                        .setDesc("这里是角色管理页面。"),
                moreGroup
        ));
        menu.add(systemGroup);

        return menu;
    }

    // ==================== 品牌管理 ====================

    private final Map<Long, Brand> brandStore = new ConcurrentHashMap<>();
    private final AtomicLong brandIdSeq = new AtomicLong(0);
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    {
        // 初始化一些 mock 品牌数据（对应前端 BrandManage 里的值）
        seedBrand(new Brand()
                .setName("耐克 Nike")
                .setLogo("https://img14.360buyimg.com/n1/jfs/t1/195878/22/25365/8030/648b3b73F5e3e3930/6b4e6a05d6d8f2d1.jpg")
                .setFirstLetter("N")
                .setCategory("CLOTHING")
                .setDescription("全球知名运动品牌，专注于运动装备、鞋服及配件。")
                .setSort(1)
                .setStatus(1));
        seedBrand(new Brand()
                .setName("小米 Xiaomi")
                .setLogo("https://img14.360buyimg.com/n1/jfs/t1/240456/43/9332/24961/6536a486Fa13c92c6/e08235e1d4f2e7f3.jpg")
                .setFirstLetter("X")
                .setCategory("ELECTRONICS")
                .setDescription("专注于智能硬件和电子产品研发的全球化移动互联网企业。")
                .setSort(2)
                .setStatus(1));
        seedBrand(new Brand()
                .setName("可口可乐 Coca-Cola")
                .setLogo("https://img14.360buyimg.com/n1/jfs/t1/196727/38/25589/10879/6478b9ceF7c222a52/3d168c73d2f0f63b.jpg")
                .setFirstLetter("C")
                .setCategory("FOOD")
                .setDescription("全球最大的饮料公司之一，提供多款汽水及软饮料。")
                .setSort(3)
                .setStatus(1));
        seedBrand(new Brand()
                .setName("欧莱雅 L'Oréal")
                .setLogo("https://img14.360buyimg.com/n1/jfs/t1/207268/15/24800/7478/648c5b8fF8b6f4c5a/515246e5a4a29a3a.jpg")
                .setFirstLetter("O")
                .setCategory("BEAUTY")
                .setDescription("法国知名美妆品牌，产品覆盖护肤、彩妆、美发、香水等。")
                .setSort(4)
                .setStatus(1));
        seedBrand(new Brand()
                .setName("宜家 IKEA")
                .setLogo("https://img14.360buyimg.com/n1/jfs/t1/189894/31/26234/9320/64788e8dF2f8b7e63/2d1c2b4e3c5f4c3d.jpg")
                .setFirstLetter("I")
                .setCategory("HOME")
                .setDescription("来自瑞典的全球知名家具和家居零售商。")
                .setSort(5)
                .setStatus(0));
        seedBrand(new Brand()
                .setName("阿迪达斯 Adidas")
                .setLogo("https://img14.360buyimg.com/n1/jfs/t1/190560/15/25600/7259/648b3b75F55b21d9d/b4d3d2f2e3c4c045.jpg")
                .setFirstLetter("A")
                .setCategory("CLOTHING")
                .setDescription("德国知名运动品牌，主营运动鞋服及运动配饰。")
                .setSort(6)
                .setStatus(1));
        seedBrand(new Brand()
                .setName("华为 Huawei")
                .setLogo("https://img14.360buyimg.com/n1/jfs/t1/239821/8/9189/23987/6536a2e0Fa108c3d7/b5a856e6d4d60162.jpg")
                .setFirstLetter("H")
                .setCategory("ELECTRONICS")
                .setDescription("全球领先的ICT基础设施和智能终端提供商。")
                .setSort(7)
                .setStatus(1));
        seedBrand(new Brand()
                .setName("索尼 Sony")
                .setLogo("https://img14.360buyimg.com/n1/jfs/t1/229700/26/9056/6910/654792e8F4b9d5894/b0d37c191e41a4fe.jpg")
                .setFirstLetter("S")
                .setCategory("ELECTRONICS")
                .setDescription("日本知名消费电子品牌，产品涵盖游戏机、相机、音频设备等。")
                .setSort(8)
                .setStatus(0));
    }

    private Brand seedBrand(Brand b) {
        long id = brandIdSeq.incrementAndGet();
        b.setId(id);
        b.setCreateTime(LocalDateTime.now().format(DT_FMT));
        brandStore.put(id, b);
        return b;
    }

    private List<Brand> filterBrands(BrandQuery query) {
        return brandStore.values().stream()
                .filter(b -> {
                    if (query != null && query.getName() != null && !query.getName().isEmpty()) {
                        if (!b.getName().contains(query.getName())) return false;
                    }
                    if (query != null && query.isSetStatus()) {
                        if (b.getStatus() != query.getStatus()) return false;
                    }
                    return true;
                })
                .sorted(Comparator
                        .comparingInt((Brand b) -> b.isSetSort() ? b.getSort() : 0)
                        .thenComparingLong(Brand::getId))
                .collect(Collectors.toList());
    }

    @Override
    public BrandPageResult listBrand(BrandQuery query) throws ServiceException, TException {
        int page = (query != null && query.isSetPage() && query.getPage() > 0) ? query.getPage() : 1;
        int pageSize = (query != null && query.isSetPageSize() && query.getPageSize() > 0) ? query.getPageSize() : 10;

        List<Brand> all = filterBrands(query);
        long total = all.size();
        int from = (page - 1) * pageSize;
        int to = Math.min(from + pageSize, all.size());
        List<Brand> slice = from >= all.size() ? List.of() : all.subList(from, to);

        BrandPageResult result = new BrandPageResult();
        result.setList(new ArrayList<>(slice));
        result.setTotal(total);
        result.setPage(page);
        result.setPageSize(pageSize);
        return result;
    }

    @Override
    public Brand getBrandById(long id) throws ServiceException, TException {
        Brand b = brandStore.get(id);
        if (b == null) {
            throw new ServiceException("NOT_FOUND", "Brand not found: id=" + id);
        }
        return b;
    }

    @Override
    public Brand createBrand(BrandCreateRequest req) throws ServiceException, TException {
        if (req == null || req.getName() == null || req.getName().isBlank()) {
            throw new ServiceException("INVALID_PARAM", "Brand name is required");
        }
        if (req.getFirstLetter() == null || !req.getFirstLetter().matches("^[A-Z]$")) {
            throw new ServiceException("INVALID_PARAM", "firstLetter must be a single uppercase letter A-Z");
        }
        if (req.getCategory() == null || req.getCategory().isBlank()) {
            throw new ServiceException("INVALID_PARAM", "category is required");
        }

        Brand b = new Brand();
        b.setName(req.getName().trim());
        b.setLogo(req.isSetLogo() && !req.getLogo().isBlank() ? req.getLogo()
                : defaultLogo(req.getName()));
        b.setFirstLetter(req.getFirstLetter());
        b.setCategory(req.getCategory());
        b.setDescription(req.isSetDescription() ? req.getDescription() : "");
        b.setSort(req.isSetSort() ? req.getSort() : 0);
        b.setStatus(req.isSetStatus() ? req.getStatus() : 1);

        long id = brandIdSeq.incrementAndGet();
        b.setId(id);
        b.setCreateTime(LocalDateTime.now().format(DT_FMT));
        brandStore.put(id, b);
        return b;
    }

    @Override
    public Brand updateBrand(BrandUpdateRequest req) throws ServiceException, TException {
        if (req == null) {
            throw new ServiceException("INVALID_PARAM", "request is required");
        }
        Brand b = brandStore.get(req.getId());
        if (b == null) {
            throw new ServiceException("NOT_FOUND", "Brand not found: id=" + req.getId());
        }
        if (req.getName() == null || req.getName().isBlank()) {
            throw new ServiceException("INVALID_PARAM", "Brand name is required");
        }
        if (req.getFirstLetter() == null || !req.getFirstLetter().matches("^[A-Z]$")) {
            throw new ServiceException("INVALID_PARAM", "firstLetter must be a single uppercase letter A-Z");
        }
        if (req.getCategory() == null || req.getCategory().isBlank()) {
            throw new ServiceException("INVALID_PARAM", "category is required");
        }

        b.setName(req.getName().trim());
        b.setLogo(req.isSetLogo() && !req.getLogo().isBlank() ? req.getLogo()
                : defaultLogo(req.getName()));
        b.setFirstLetter(req.getFirstLetter());
        b.setCategory(req.getCategory());
        b.setDescription(req.isSetDescription() ? req.getDescription() : "");
        if (req.isSetSort()) b.setSort(req.getSort());
        if (req.isSetStatus()) b.setStatus(req.getStatus());
        return b;
    }

    @Override
    public boolean deleteBrandById(long id) throws ServiceException, TException {
        Brand removed = brandStore.remove(id);
        if (removed == null) {
            throw new ServiceException("NOT_FOUND", "Brand not found: id=" + id);
        }
        return true;
    }

    @Override
    public boolean updateBrandStatus(BrandStatusRequest req) throws ServiceException, TException {
        if (req == null) {
            throw new ServiceException("INVALID_PARAM", "request is required");
        }
        Brand b = brandStore.get(req.getId());
        if (b == null) {
            throw new ServiceException("NOT_FOUND", "Brand not found: id=" + req.getId());
        }
        b.setStatus(req.getStatus());
        return true;
    }

    @Override
    public boolean updateBrandSort(BrandSortRequest req) throws ServiceException, TException {
        if (req == null) {
            throw new ServiceException("INVALID_PARAM", "request is required");
        }
        Brand b = brandStore.get(req.getId());
        if (b == null) {
            throw new ServiceException("NOT_FOUND", "Brand not found: id=" + req.getId());
        }
        b.setSort(req.getSort());
        return true;
    }

    private static String defaultLogo(String name) {
        char c = (name != null && !name.isBlank())
                ? Character.toUpperCase(name.charAt(0))
                : 'B';
        return "https://dummyimage.com/80x80/409eff/ffffff&text=" + c;
    }
}
