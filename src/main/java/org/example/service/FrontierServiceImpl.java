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
import java.util.concurrent.ConcurrentHashMap;

@Component
public class FrontierServiceImpl implements FrontierService.Iface {

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
}
