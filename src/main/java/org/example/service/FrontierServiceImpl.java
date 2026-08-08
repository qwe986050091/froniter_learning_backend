package org.example.service;

import org.example.thrift.FrontierService;
import org.example.thrift.LoginRequest;
import org.example.thrift.LoginResponse;
import org.example.thrift.ServiceException;
import org.example.thrift.UserInfo;
import org.apache.thrift.TException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class FrontierServiceImpl implements FrontierService.Iface {

    private final Map<String, UserInfo> userStore = new ConcurrentHashMap<>();
    private final AtomicLong tokenCounter = new AtomicLong(0);

    public FrontierServiceImpl() {
        UserInfo admin = new UserInfo();
        admin.setUserId("1");
        admin.setUsername("admin");
        admin.setNickname("Administrator");
        admin.setAvatar("/avatars/admin.png");
        admin.setRoles(List.of("ADMIN", "USER"));
        admin.setExtra(Map.of("department", "IT", "level", "9"));
        userStore.put("admin", admin);

        UserInfo user = new UserInfo();
        user.setUserId("2");
        user.setUsername("user");
        user.setNickname("Normal User");
        user.setAvatar("/avatars/user.png");
        user.setRoles(List.of("USER"));
        user.setExtra(Map.of("department", "Operations", "level", "3"));
        userStore.put("user", user);
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

        String token = UUID.randomUUID().toString().replace("-", "");
        String refreshToken = UUID.randomUUID().toString().replace("-", "");

        LoginResponse response = new LoginResponse();
        response.setCode("200");
        response.setMessage("Login successful");
        response.setToken(token);
        response.setRefreshToken(refreshToken);
        response.setUserInfo(userInfo);

        return response;
    }
}