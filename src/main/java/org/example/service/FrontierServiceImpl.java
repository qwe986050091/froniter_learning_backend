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
import org.example.thrift.product.*;
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
        implements AuthService.Iface, MenuService.Iface, BrandService.Iface, ProductService.Iface {

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
                new MenuItem("platform-attr", "平台属性")
                        .setIcon("Collection")
                        .setPath("/home/platform-attr-manage")
                        .setDesc("平台分类与平台属性管理。"),
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

    // ==================== 平台分类与平台属性 ====================

    private final Map<Long, Category> categoryStore = new ConcurrentHashMap<>();
    private final Map<Long, PlatformAttr> attrStore = new ConcurrentHashMap<>();
    private final Map<Long, PlatformAttrValue> attrValueStore = new ConcurrentHashMap<>();

    private final AtomicLong categoryIdSeq = new AtomicLong(0);
    private final AtomicLong attrIdSeq = new AtomicLong(0);
    private final AtomicLong attrValueIdSeq = new AtomicLong(0);

    {
        // ---- 分类树：3 一级 × 3 二级 × 2 三级 = 18 个三级 ----
        // 1. 手机
        long phoneL1 = seedCategory("手机", 0, 1, 1, 1).getId();
            long commL2  = seedCategory("手机通讯", phoneL1, 2, 1, 1).getId();
                long phoneL3      = seedCategory("手机", commL2, 3, 1, 1).getId();
                long radioL3      = seedCategory("对讲机", commL2, 3, 2, 1).getId();
            long digitalL2 = seedCategory("手机数码", phoneL1, 2, 2, 1).getId();
                long padL3        = seedCategory("平板", digitalL2, 3, 1, 1).getId();
                long watchL3      = seedCategory("智能手表", digitalL2, 3, 2, 1).getId();
            long accessoryL2 = seedCategory("手机配件", phoneL1, 2, 3, 1).getId();
                long earphoneL3   = seedCategory("耳机", accessoryL2, 3, 1, 1).getId();
                long chargerL3    = seedCategory("充电器", accessoryL2, 3, 2, 1).getId();
        // 2. 家电
        long appL1 = seedCategory("家电", 0, 1, 2, 1).getId();
            long kitchenL2 = seedCategory("厨房小电", appL1, 2, 1, 1).getId();
                long ricecookerL3 = seedCategory("电饭煲", kitchenL2, 3, 1, 1).getId();
                long kettleL3     = seedCategory("电水壶", kitchenL2, 3, 2, 1).getId();
            long bigL2 = seedCategory("大家电", appL1, 2, 2, 1).getId();
                long fridgeL3     = seedCategory("冰箱", bigL2, 3, 1, 1).getId();
                long washerL3     = seedCategory("洗衣机", bigL2, 3, 2, 1).getId();
            long careL2 = seedCategory("个护健康", appL1, 2, 3, 1).getId();
                long dryerL3      = seedCategory("吹风机", careL2, 3, 1, 1).getId();
                long toothL3      = seedCategory("电动牙刷", careL2, 3, 2, 1).getId();
        // 3. 服装
        long cloL1 = seedCategory("服装", 0, 1, 3, 1).getId();
            long womenL2 = seedCategory("女装", cloL1, 2, 1, 1).getId();
                long dressL3      = seedCategory("连衣裙", womenL2, 3, 1, 1).getId();
                long wtL3         = seedCategory("T恤", womenL2, 3, 2, 1).getId();
            long menL2 = seedCategory("男装", cloL1, 2, 2, 1).getId();
                long shirtL3      = seedCategory("衬衫", menL2, 3, 1, 1).getId();
                long jacketL3     = seedCategory("夹克", menL2, 3, 2, 1).getId();
            long sportL2 = seedCategory("运动户外", cloL1, 2, 3, 1).getId();
                long shoesL3      = seedCategory("运动鞋", sportL2, 3, 1, 1).getId();
                long outdoorL3    = seedCategory("冲锋衣", sportL2, 3, 2, 0).getId();

        // ---- 平台属性与属性值：每个三级分类 2 条属性，每条 3~6 个值 ----
        seedAttrValues(phoneL3, "手机一级", List.of("苹果手机","安卓手机","安卓手机222","安卓手机111","安卓手机222","苹果手机"), List.of("安卓手机222","安卓手机111"));
        seedAttrValues(phoneL3, "电池容量", List.of("1200mAh以下","3000mAh以上","1200mAh到3000mAh"), 1);
        seedAttrValues(phoneL3, "运行内存", List.of("128G","6G","256G"), 2);
        seedAttrValues(phoneL3, "机身内存", List.of("128G","512G","64G","256G","1T","32G"), 3);
        seedAttrValues(phoneL3, "CPU型号", List.of("骁龙730G","麒麟990","骁龙439","骁龙845","5G骁龙768G"), 4);
        seedAttrValues(phoneL3, "屏幕尺寸", List.of("6.75-6.84英寸","6.55-6.64英寸","6.95英寸及以上","6.85-6.94英寸","6.65-6.74英寸","6.0-6.24英寸"), 5);

        seedAttrValues(radioL3, "对讲机频道数", List.of("16 频道","22 频道","99 频道","128 频道"), 1);
        seedAttrValues(radioL3, "对讲距离", List.of("1-3 公里","3-5 公里","5-10 公里","10 公里以上"), 2);

        seedAttrValues(padL3, "操作系统", List.of("iPadOS","Android","HarmonyOS","Windows"), 1);
        seedAttrValues(padL3, "屏幕尺寸", List.of("7.9 英寸及以下","8-10 英寸","10.1-12 英寸","12 英寸以上"), 2);

        seedAttrValues(watchL3, "表壳材质", List.of("铝合金","不锈钢","钛金属","陶瓷"), 1);
        seedAttrValues(watchL3, "续航时间", List.of("1 天以内","1-3 天","3-7 天","7 天以上"), 2);

        seedAttrValues(earphoneL3, "佩戴方式", List.of("入耳式","头戴式","半入耳","骨传导","挂耳式"), 1);
        seedAttrValues(earphoneL3, "连接方式", List.of("有线","真无线TWS","颈挂式蓝牙","头戴式蓝牙"), 2);

        seedAttrValues(chargerL3, "充电功率", List.of("18W","33W","45W","65W","100W 及以上"), 1);
        seedAttrValues(chargerL3, "接口类型", List.of("Type-C","Lightning","USB-A","GaN 多口"), 2);

        seedAttrValues(ricecookerL3, "容量", List.of("1-2L","3L","4L","5L","6L 及以上"), 1);
        seedAttrValues(ricecookerL3, "内胆", List.of("不粘内胆","厚釜","不锈钢","陶瓷","IH"), 2);

        seedAttrValues(kettleL3, "材质", List.of("不锈钢","高硼硅玻璃","塑料","陶瓷"), 1);
        seedAttrValues(kettleL3, "容量", List.of("1L 及以下","1.2-1.5L","1.7-2L","2L 以上"), 2);

        seedAttrValues(fridgeL3, "门款式", List.of("单门","双门","三门","对开门","多门","十字对开"), 1);
        seedAttrValues(fridgeL3, "总容积", List.of("200L 以下","201-300L","301-500L","501L 以上"), 2);

        seedAttrValues(washerL3, "洗涤方式", List.of("波轮","滚筒","洗烘一体","迷你壁挂"), 1);
        seedAttrValues(washerL3, "洗涤容量", List.of("5kg 以下","5-8kg","9-10kg","10kg 以上"), 2);

        seedAttrValues(dryerL3, "风温档位", List.of("冷风","一档热风","二档热风","智能恒温"), 1);
        seedAttrValues(dryerL3, "功率", List.of("1200W 以下","1200-1600W","1800-2100W","2200W 以上"), 2);

        seedAttrValues(toothL3, "震动频率", List.of("2 万次/分钟以下","2-3 万次/分钟","3-4 万次/分钟","4 万次以上"), 1);
        seedAttrValues(toothL3, "模式数", List.of("2 种模式","3 种模式","4 种模式","5 种及以上"), 2);

        seedAttrValues(dressL3, "风格", List.of("法式","日韩","通勤","复古","国风"), 1);
        seedAttrValues(dressL3, "裙长", List.of("短裙","及膝","中长","及踝","拖地"), 2);

        seedAttrValues(wtL3, "版型", List.of("修身","常规","宽松","廓形","Oversize"), 1);
        seedAttrValues(wtL3, "袖长", List.of("无袖","短袖","七分袖","长袖"), 2);

        seedAttrValues(shirtL3, "领型", List.of("方领","尖领","温莎领","立领","亨利领"), 1);
        seedAttrValues(shirtL3, "材质", List.of("全棉","牛津纺","亚麻","混纺","真丝"), 2);

        seedAttrValues(jacketL3, "厚度", List.of("薄款","常规","加绒","夹棉","羽绒"), 1);
        seedAttrValues(jacketL3, "款式", List.of("棒球服","工装","牛仔","风衣","皮夹克"), 2);

        seedAttrValues(shoesL3, "适用场景", List.of("跑步","篮球","健身训练","滑板","户外徒步","日常生活"), 1);
        seedAttrValues(shoesL3, "缓震科技", List.of("Air 气垫","ZoomX","Boost","䨻","FlyteFoam"), 2);

        seedAttrValues(outdoorL3, "防水等级", List.of("防泼水","5000mm 防水","10000mm 防水","20000mm 以上"), 1);
        seedAttrValues(outdoorL3, "适用季节", List.of("春","夏","秋冬","四季通用"), 2);
    }

    private Category seedCategory(String name, long parentId, int level, int sort, int status) {
        Category c = new Category();
        c.setName(name);
        c.setParentId(parentId);
        c.setLevel(level);
        c.setSort(sort);
        c.setStatus(status);
        long id = categoryIdSeq.incrementAndGet();
        c.setId(id);
        c.setCreateTime(LocalDateTime.now().format(DT_FMT));
        categoryStore.put(id, c);
        return c;
    }

    /**
     * 在三级分类下创建属性 + 属性值（按名字批量插入）
     * 额外参数 extras：额外的 values 值（用于截图中出现的「重复多种品牌名字」场景）
     */
    private void seedAttrValues(long cateId, String attrName, List<String> values, int sort) {
        PlatformAttr attr = new PlatformAttr();
        attr.setName(attrName);
        attr.setCategoryId(cateId);
        attr.setSort(sort);
        attr.setStatus(1);
        long aid = attrIdSeq.incrementAndGet();
        attr.setId(aid);
        attr.setCreateTime(LocalDateTime.now().format(DT_FMT));
        attrStore.put(aid, attr);

        int vSort = 1;
        for (String v : values) {
            PlatformAttrValue pav = new PlatformAttrValue();
            pav.setAttrId(aid);
            pav.setValue(v);
            pav.setSort(vSort++);
            long vid = attrValueIdSeq.incrementAndGet();
            pav.setId(vid);
            attrValueStore.put(vid, pav);
        }
    }

    private void seedAttrValues(long cateId, String attrName, List<String> values, List<String> extras) {
        List<String> all = new ArrayList<>(values);
        if (extras != null) all.addAll(extras);
        seedAttrValues(cateId, attrName, all, 1);
    }

    private List<PlatformAttrValue> valuesOfAttr(long attrId) {
        return attrValueStore.values().stream()
                .filter(v -> v.getAttrId() == attrId)
                .sorted(Comparator
                        .comparingInt((PlatformAttrValue v) -> v.isSetSort() ? v.getSort() : 0)
                        .thenComparingLong(PlatformAttrValue::getId))
                .collect(Collectors.toList());
    }

    private PlatformAttrWithValues toWithValues(PlatformAttr a) {
        PlatformAttrWithValues w = new PlatformAttrWithValues();
        w.setId(a.getId());
        w.setName(a.getName());
        w.setCategoryId(a.getCategoryId());
        if (a.isSetSort()) w.setSort(a.getSort());
        if (a.isSetStatus()) w.setStatus(a.getStatus());
        if (a.isSetCreateTime()) w.setCreateTime(a.getCreateTime());
        w.setValues(valuesOfAttr(a.getId()));
        return w;
    }

    private void insertValuesForAttr(long attrId, List<String> values) {
        int sort = 1;
        for (String v : values) {
            PlatformAttrValue pav = new PlatformAttrValue();
            pav.setAttrId(attrId);
            pav.setValue(v);
            pav.setSort(sort++);
            long vid = attrValueIdSeq.incrementAndGet();
            pav.setId(vid);
            attrValueStore.put(vid, pav);
        }
    }

    private void deleteValuesOfAttr(long attrId) {
        attrValueStore.entrySet().removeIf(e -> e.getValue().getAttrId() == attrId);
    }

    @Override
    public List<Category> listCategory(CategoryQuery query) throws ServiceException, TException {
        return categoryStore.values().stream()
                .filter(c -> {
                    if (query != null && query.isSetLevel()) {
                        if (c.getLevel() != query.getLevel()) return false;
                    }
                    if (query != null && query.isSetParentId()) {
                        if (c.getParentId() != query.getParentId()) return false;
                    }
                    return true;
                })
                .sorted(Comparator
                        .comparingInt((Category c) -> c.isSetSort() ? c.getSort() : 0)
                        .thenComparingLong(Category::getId))
                .collect(Collectors.toList());
    }

    @Override
    public Category getCategoryById(long id) throws ServiceException, TException {
        Category c = categoryStore.get(id);
        if (c == null) throw new ServiceException("NOT_FOUND", "Category not found: id=" + id);
        return c;
    }

    @Override
    public Category createCategory(CategoryCreateRequest req) throws ServiceException, TException {
        if (req == null || req.getName() == null || req.getName().isBlank()) {
            throw new ServiceException("INVALID_PARAM", "Category name is required");
        }
        if (!categoryStore.containsKey(req.getParentId()) && req.getParentId() != 0) {
            throw new ServiceException("NOT_FOUND", "parentId not found: " + req.getParentId());
        }
        Category c = new Category();
        c.setName(req.getName().trim());
        c.setParentId(req.getParentId());
        c.setLevel(req.getLevel());
        c.setSort(req.isSetSort() ? req.getSort() : 0);
        c.setStatus(req.isSetStatus() ? req.getStatus() : 1);
        long id = categoryIdSeq.incrementAndGet();
        c.setId(id);
        c.setCreateTime(LocalDateTime.now().format(DT_FMT));
        categoryStore.put(id, c);
        return c;
    }

    @Override
    public Category updateCategory(CategoryUpdateRequest req) throws ServiceException, TException {
        if (req == null) throw new ServiceException("INVALID_PARAM", "request is required");
        Category c = categoryStore.get(req.getId());
        if (c == null) throw new ServiceException("NOT_FOUND", "Category not found: id=" + req.getId());
        if (req.getName() == null || req.getName().isBlank()) {
            throw new ServiceException("INVALID_PARAM", "Category name is required");
        }
        c.setName(req.getName().trim());
        if (req.isSetParentId()) c.setParentId(req.getParentId());
        if (req.isSetLevel()) c.setLevel(req.getLevel());
        if (req.isSetSort()) c.setSort(req.getSort());
        if (req.isSetStatus()) c.setStatus(req.getStatus());
        return c;
    }

    @Override
    public boolean updateCategoryStatus(CategoryStatusRequest req) throws ServiceException, TException {
        if (req == null) throw new ServiceException("INVALID_PARAM", "request is required");
        Category c = categoryStore.get(req.getId());
        if (c == null) throw new ServiceException("NOT_FOUND", "Category not found: id=" + req.getId());
        c.setStatus(req.getStatus());
        return true;
    }

    @Override
    public boolean deleteCategoryById(long id) throws ServiceException, TException {
        Category c = categoryStore.get(id);
        if (c == null) throw new ServiceException("NOT_FOUND", "Category not found: id=" + id);
        // 1) 有子分类不能删
        boolean hasChildren = categoryStore.values().stream().anyMatch(x -> x.getParentId() == id);
        if (hasChildren) {
            throw new ServiceException("HAS_CHILDREN", "该分类下存在子分类，无法删除");
        }
        // 2) 分类下有平台属性也不能删（只禁 level=3；其他 level 走 hasChildren）
        boolean hasAttr = attrStore.values().stream().anyMatch(a -> a.getCategoryId() == id);
        if (hasAttr) {
            throw new ServiceException("HAS_CHILDREN", "该分类下存在平台属性，无法删除");
        }
        categoryStore.remove(id);
        return true;
    }

    @Override
    public PlatformAttrPageResult listPlatformAttr(PlatformAttrQuery query) throws ServiceException, TException {
        int page = (query != null && query.isSetPage() && query.getPage() > 0) ? query.getPage() : 1;
        int pageSize = (query != null && query.isSetPageSize() && query.getPageSize() > 0) ? query.getPageSize() : 10;

        List<PlatformAttr> all = attrStore.values().stream()
                .filter(a -> {
                    if (query != null && query.isSetCategoryId()) {
                        if (a.getCategoryId() != query.getCategoryId()) return false;
                    }
                    if (query != null && query.isSetName() && !query.getName().isEmpty()) {
                        if (!a.getName().contains(query.getName())) return false;
                    }
                    if (query != null && query.isSetStatus()) {
                        if (a.getStatus() != query.getStatus()) return false;
                    }
                    return true;
                })
                .sorted(Comparator
                        .comparingInt((PlatformAttr a) -> a.isSetSort() ? a.getSort() : 0)
                        .thenComparingLong(PlatformAttr::getId))
                .collect(Collectors.toList());

        long total = all.size();
        int from = (page - 1) * pageSize;
        int to = Math.min(from + pageSize, all.size());
        List<PlatformAttr> slice = from >= all.size() ? List.of() : all.subList(from, to);

        PlatformAttrPageResult r = new PlatformAttrPageResult();
        r.setList(slice.stream().map(this::toWithValues).collect(Collectors.toList()));
        r.setTotal(total);
        r.setPage(page);
        r.setPageSize(pageSize);
        return r;
    }

    @Override
    public PlatformAttrWithValues getPlatformAttrById(long id) throws ServiceException, TException {
        PlatformAttr a = attrStore.get(id);
        if (a == null) throw new ServiceException("NOT_FOUND", "PlatformAttr not found: id=" + id);
        return toWithValues(a);
    }

    @Override
    public PlatformAttr createPlatformAttr(PlatformAttrCreateRequest req) throws ServiceException, TException {
        if (req == null || req.getName() == null || req.getName().isBlank()) {
            throw new ServiceException("INVALID_PARAM", "attr name is required");
        }
        if (!categoryStore.containsKey(req.getCategoryId())) {
            throw new ServiceException("NOT_FOUND", "categoryId not found: " + req.getCategoryId());
        }
        if (req.getValues() == null || req.getValues().isEmpty()) {
            throw new ServiceException("VALIDATION_ERROR", "values must not be empty");
        }
        // 同分类下同名检测
        boolean dup = attrStore.values().stream()
                .anyMatch(a -> a.getCategoryId() == req.getCategoryId() && a.getName().equals(req.getName().trim()));
        if (dup) throw new ServiceException("DUPLICATE_NAME", "同分类下已存在属性：" + req.getName());

        PlatformAttr a = new PlatformAttr();
        a.setName(req.getName().trim());
        a.setCategoryId(req.getCategoryId());
        a.setSort(req.isSetSort() ? req.getSort() : 0);
        a.setStatus(req.isSetStatus() ? req.getStatus() : 1);
        long aid = attrIdSeq.incrementAndGet();
        a.setId(aid);
        a.setCreateTime(LocalDateTime.now().format(DT_FMT));
        attrStore.put(aid, a);
        insertValuesForAttr(aid, req.getValues());
        return a;
    }

    @Override
    public PlatformAttr updatePlatformAttr(PlatformAttrUpdateRequest req) throws ServiceException, TException {
        if (req == null) throw new ServiceException("INVALID_PARAM", "request is required");
        PlatformAttr a = attrStore.get(req.getId());
        if (a == null) throw new ServiceException("NOT_FOUND", "PlatformAttr not found: id=" + req.getId());
        if (req.getName() == null || req.getName().isBlank()) {
            throw new ServiceException("INVALID_PARAM", "attr name is required");
        }
        if (req.getValues() == null || req.getValues().isEmpty()) {
            throw new ServiceException("VALIDATION_ERROR", "values must not be empty");
        }
        // 同分类下同名，排除自己
        long cateId = req.isSetCategoryId() ? req.getCategoryId() : a.getCategoryId();
        boolean dup = attrStore.values().stream()
                .filter(x -> x.getId() != req.getId())
                .anyMatch(x -> x.getCategoryId() == cateId && x.getName().equals(req.getName().trim()));
        if (dup) throw new ServiceException("DUPLICATE_NAME", "同分类下已存在属性：" + req.getName());

        a.setName(req.getName().trim());
        if (req.isSetCategoryId()) a.setCategoryId(req.getCategoryId());
        if (req.isSetSort()) a.setSort(req.getSort());
        if (req.isSetStatus()) a.setStatus(req.getStatus());
        // 覆盖 values
        deleteValuesOfAttr(a.getId());
        insertValuesForAttr(a.getId(), req.getValues());
        return a;
    }

    @Override
    public boolean deletePlatformAttrById(long id) throws ServiceException, TException {
        PlatformAttr removed = attrStore.remove(id);
        if (removed == null) throw new ServiceException("NOT_FOUND", "PlatformAttr not found: id=" + id);
        deleteValuesOfAttr(id);
        return true;
    }
}
