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
        // LOGO 使用 picsum.photos 的 seed 占位图：每个品牌一个 seed → 固定唯一图、刷新不变、无防盗链、本机联通OK
        seedBrand(new Brand()
                .setName("耐克 Nike")
                .setLogo("https://picsum.photos/seed/nike/120")
                .setFirstLetter("N")
                .setCategory("CLOTHING")
                .setDescription("全球知名运动品牌，专注于运动装备、鞋服及配件。")
                .setSort(1)
                .setStatus(1));
        seedBrand(new Brand()
                .setName("小米 Xiaomi")
                .setLogo("https://picsum.photos/seed/xiaomi/120")
                .setFirstLetter("X")
                .setCategory("ELECTRONICS")
                .setDescription("专注于智能硬件和电子产品研发的全球化移动互联网企业。")
                .setSort(2)
                .setStatus(1));
        seedBrand(new Brand()
                .setName("可口可乐 Coca-Cola")
                .setLogo("https://picsum.photos/seed/cocacola/120")
                .setFirstLetter("C")
                .setCategory("FOOD")
                .setDescription("全球最大的饮料公司之一，提供多款汽水及软饮料。")
                .setSort(3)
                .setStatus(1));
        seedBrand(new Brand()
                .setName("欧莱雅 L'Oréal")
                .setLogo("https://picsum.photos/seed/loreal/120")
                .setFirstLetter("O")
                .setCategory("BEAUTY")
                .setDescription("法国知名美妆品牌，产品覆盖护肤、彩妆、美发、香水等。")
                .setSort(4)
                .setStatus(1));
        seedBrand(new Brand()
                .setName("宜家 IKEA")
                .setLogo("https://picsum.photos/seed/ikea/120")
                .setFirstLetter("I")
                .setCategory("HOME")
                .setDescription("来自瑞典的全球知名家具和家居零售商。")
                .setSort(5)
                .setStatus(0));
        seedBrand(new Brand()
                .setName("阿迪达斯 Adidas")
                .setLogo("https://picsum.photos/seed/adidas/120")
                .setFirstLetter("A")
                .setCategory("CLOTHING")
                .setDescription("德国知名运动品牌，主营运动鞋服及运动配饰。")
                .setSort(6)
                .setStatus(1));
        seedBrand(new Brand()
                .setName("华为 Huawei")
                .setLogo("https://picsum.photos/seed/huawei/120")
                .setFirstLetter("H")
                .setCategory("ELECTRONICS")
                .setDescription("全球领先的ICT基础设施和智能终端提供商。")
                .setSort(7)
                .setStatus(1));
        seedBrand(new Brand()
                .setName("索尼 Sony")
                .setLogo("https://picsum.photos/seed/sony/120")
                .setFirstLetter("S")
                .setCategory("ELECTRONICS")
                .setDescription("日本知名消费电子品牌，产品涵盖游戏机、相机、音频设备等。")
                .setSort(8)
                .setStatus(0));
        seedBrand(new Brand()
                .setName("苹果 Apple")
                .setLogo("https://picsum.photos/seed/apple/120")
                .setFirstLetter("A")
                .setCategory("ELECTRONICS")
                .setDescription("美国科技公司，以 iPhone、Mac、iPad 等产品闻名。")
                .setSort(9)
                .setStatus(1));
        seedBrand(new Brand()
                .setName("三星 Samsung")
                .setLogo("https://picsum.photos/seed/samsung/120")
                .setFirstLetter("S")
                .setCategory("ELECTRONICS")
                .setDescription("韩国电子巨头，主营手机、半导体与家电。")
                .setSort(10)
                .setStatus(1));
        seedBrand(new Brand()
                .setName("奔驰 Mercedes-Benz")
                .setLogo("https://picsum.photos/seed/mercedes/120")
                .setFirstLetter("M")
                .setCategory("AUTO")
                .setDescription("德国豪华汽车品牌，拥有超过百年历史。")
                .setSort(11)
                .setStatus(1));
        seedBrand(new Brand()
                .setName("宝马 BMW")
                .setLogo("https://picsum.photos/seed/bmw/120")
                .setFirstLetter("B")
                .setCategory("AUTO")
                .setDescription("德国知名汽车与摩托车制造商。")
                .setSort(12)
                .setStatus(1));
        seedBrand(new Brand()
                .setName("星巴克 Starbucks")
                .setLogo("https://picsum.photos/seed/starbucks/120")
                .setFirstLetter("S")
                .setCategory("FOOD")
                .setDescription("全球连锁咖啡品牌，提供各类咖啡与饮品。")
                .setSort(13)
                .setStatus(1));
        seedBrand(new Brand()
                .setName("优衣库 UNIQLO")
                .setLogo("https://picsum.photos/seed/uniqlo/120")
                .setFirstLetter("U")
                .setCategory("CLOTHING")
                .setDescription("日本休闲服装品牌，以基础款和性价比著称。")
                .setSort(14)
                .setStatus(0));
        seedBrand(new Brand()
                .setName("Zara")
                .setLogo("https://picsum.photos/seed/zara/120")
                .setFirstLetter("Z")
                .setCategory("CLOTHING")
                .setDescription("西班牙快时尚品牌，主打最新流行服饰。")
                .setSort(15)
                .setStatus(1));
        seedBrand(new Brand()
                .setName("香奈儿 Chanel")
                .setLogo("https://picsum.photos/seed/chanel/120")
                .setFirstLetter("C")
                .setCategory("BEAUTY")
                .setDescription("法国奢侈品品牌，以香水和服饰闻名。")
                .setSort(16)
                .setStatus(1));
        seedBrand(new Brand()
                .setName("兰蔻 Lancôme")
                .setLogo("https://picsum.photos/seed/lancome/120")
                .setFirstLetter("L")
                .setCategory("BEAUTY")
                .setDescription("法国高端化妆品品牌。")
                .setSort(17)
                .setStatus(0));
        seedBrand(new Brand()
                .setName("无印良品 MUJI")
                .setLogo("https://picsum.photos/seed/muji/120")
                .setFirstLetter("M")
                .setCategory("HOME")
                .setDescription("日本生活杂货品牌，主打简约自然风格。")
                .setSort(18)
                .setStatus(1));
        seedBrand(new Brand()
                .setName("海尔 Haier")
                .setLogo("https://picsum.photos/seed/haier/120")
                .setFirstLetter("H")
                .setCategory("HOME")
                .setDescription("中国知名家电品牌，产品覆盖冰箱、洗衣机、空调等。")
                .setSort(19)
                .setStatus(1));
        seedBrand(new Brand()
                .setName("飞利浦 Philips")
                .setLogo("https://picsum.photos/seed/philips/120")
                .setFirstLetter("P")
                .setCategory("ELECTRONICS")
                .setDescription("荷兰跨国电子公司，产品涵盖家电、健康医疗与照明。")
                .setSort(20)
                .setStatus(1));
        seedBrand(new Brand()
                .setName("美的 Midea")
                .setLogo("https://picsum.photos/seed/midea/120")
                .setFirstLetter("M")
                .setCategory("HOME")
                .setDescription("中国家电行业领军企业之一，覆盖多品类家电。")
                .setSort(21)
                .setStatus(0));
        seedBrand(new Brand()
                .setName("联想 Lenovo")
                .setLogo("https://picsum.photos/seed/lenovo/120")
                .setFirstLetter("L")
                .setCategory("ELECTRONICS")
                .setDescription("全球最大个人电脑制造商之一。")
                .setSort(22)
                .setStatus(1));
        seedBrand(new Brand()
                .setName("耐克二 Nike SB")
                .setLogo("https://picsum.photos/seed/nikesb/120")
                .setFirstLetter("N")
                .setCategory("CLOTHING")
                .setDescription("耐克旗下的滑板运动系列。")
                .setSort(23)
                .setStatus(1));
        seedBrand(new Brand()
                .setName("特斯拉 Tesla")
                .setLogo("https://picsum.photos/seed/tesla/120")
                .setFirstLetter("T")
                .setCategory("AUTO")
                .setDescription("美国电动车与清洁能源公司。")
                .setSort(24)
                .setStatus(1));
        seedBrand(new Brand()
                .setName("丰田 Toyota")
                .setLogo("https://picsum.photos/seed/toyota/120")
                .setFirstLetter("T")
                .setCategory("AUTO")
                .setDescription("日本汽车制造巨头，全球销量领先。")
                .setSort(25)
                .setStatus(0));
        seedBrand(new Brand()
                .setName("海底捞 Haidilao")
                .setLogo("https://picsum.photos/seed/haidilao/120")
                .setFirstLetter("H")
                .setCategory("FOOD")
                .setDescription("中国知名连锁火锅品牌，以优质服务著称。")
                .setSort(26)
                .setStatus(1));
        seedBrand(new Brand()
                .setName("农夫山泉 Nongfu")
                .setLogo("https://picsum.photos/seed/nongfu/120")
                .setFirstLetter("N")
                .setCategory("FOOD")
                .setDescription("中国饮用水及饮料品牌。")
                .setSort(27)
                .setStatus(1));
        seedBrand(new Brand()
                .setName("戴森 Dyson")
                .setLogo("https://picsum.photos/seed/dyson/120")
                .setFirstLetter("D")
                .setCategory("HOME")
                .setDescription("英国科技公司，以无叶风扇、吸尘器和吹风机闻名。")
                .setSort(28)
                .setStatus(1));
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
