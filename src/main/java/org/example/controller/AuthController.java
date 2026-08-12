package org.example.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.example.annotation.RequireAuth;
import org.example.service.FrontierServiceImpl;
import org.example.thrift.auth.LoginRequest;
import org.example.thrift.auth.LoginResponse;
import org.example.thrift.common.ServiceException;
import org.example.thrift.common.UserInfo;
import org.apache.thrift.TException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final FrontierServiceImpl frontierService;

    public AuthController(FrontierServiceImpl frontierService) {
        this.frontierService = frontierService;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        String captcha = body.get("captcha");
        String loginType = body.get("loginType");

        LoginRequest request = new LoginRequest();
        request.setUsername(username != null ? username : "");
        request.setPassword(password != null ? password : "");
        if (captcha != null) {
            request.setCaptcha(captcha);
        }
        if (loginType != null) {
            request.setLoginType(loginType);
        }

        try {
            LoginResponse response = frontierService.login(request);
            Map<String, Object> result = new HashMap<>();
            result.put("code", response.getCode());
            result.put("message", response.getMessage());
            result.put("token", response.getToken());
            result.put("refreshToken", response.getRefreshToken());
            result.put("userInfo", response.getUserInfo());
            return ResponseEntity.ok(result);
        } catch (ServiceException e) {
            log.warn("Login failed: code={}, description={}", e.getCode(), e.getDescription());
            Map<String, Object> error = new HashMap<>();
            error.put("code", e.getCode());
            error.put("message", e.getDescription());
            return ResponseEntity.badRequest().body(error);
        } catch (TException e) {
            log.error("Thrift error during login", e);
            Map<String, Object> error = new HashMap<>();
            error.put("code", "INTERNAL_ERROR");
            error.put("message", "Internal server error");
            return ResponseEntity.internalServerError().body(error);
        }
    }

    @GetMapping("/verify")
    @RequireAuth
    public ResponseEntity<Map<String, Object>> verifyToken(HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        UserInfo userInfo = (UserInfo) request.getAttribute("currentUser");
        result.put("valid", true);
        result.put("message", "Token is valid");
        result.put("userInfo", userInfo);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/logout")
    @RequireAuth
    public ResponseEntity<Map<String, Object>> logout(HttpServletRequest request) {
        String token = (String) request.getAttribute("currentToken");
        if (token != null) {
            frontierService.logout(token);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "Logout successful");
        return ResponseEntity.ok(result);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> result = new HashMap<>();
        result.put("status", "UP");
        result.put("service", "frontier-test-backend");
        return ResponseEntity.ok(result);
    }
}
