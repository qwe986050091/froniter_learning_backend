package org.example.controller;

import lombok.extern.slf4j.Slf4j;
import org.example.annotation.RequireAuth;
import org.example.thrift.UserInfo;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/user")
public class UserController {

    @GetMapping("/profile")
    @RequireAuth
    public ResponseEntity<Map<String, Object>> getProfile(HttpServletRequest request) {
        UserInfo userInfo = (UserInfo) request.getAttribute("currentUser");
        Map<String, Object> result = new HashMap<>();
        result.put("code", "200");
        result.put("userInfo", userInfo);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/admin-only")
    @RequireAuth(roles = {"ADMIN"}, message = "Admin access required")
    public ResponseEntity<Map<String, Object>> adminOnly(HttpServletRequest request) {
        UserInfo userInfo = (UserInfo) request.getAttribute("currentUser");
        Map<String, Object> result = new HashMap<>();
        result.put("code", "200");
        result.put("message", "Welcome admin: " + userInfo.getUsername());
        return ResponseEntity.ok(result);
    }
}
