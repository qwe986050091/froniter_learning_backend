package org.example.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.example.annotation.RequireAuth;
import org.example.service.FrontierServiceImpl;
import org.example.thrift.common.UserInfo;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final FrontierServiceImpl frontierService;
    private final ObjectMapper objectMapper;

    public AuthInterceptor(FrontierServiceImpl frontierService, ObjectMapper objectMapper) {
        this.frontierService = frontierService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RequireAuth methodAnnotation = handlerMethod.getMethodAnnotation(RequireAuth.class);
        RequireAuth classAnnotation = handlerMethod.getBeanType().getAnnotation(RequireAuth.class);

        RequireAuth annotation = methodAnnotation != null ? methodAnnotation : classAnnotation;

        if (annotation == null) {
            return true;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            writeUnauthorized(response, annotation.message());
            return false;
        }

        String token = authHeader.substring(7);
        UserInfo userInfo = frontierService.getUserByToken(token);

        if (userInfo == null) {
            writeUnauthorized(response, "Token is invalid or expired");
            return false;
        }

        String[] requiredRoles = annotation.roles();
        if (requiredRoles.length > 0) {
            java.util.List<String> userRoles = userInfo.getRoles();
            if (userRoles == null || !userRoles.contains(java.util.Arrays.asList(requiredRoles).get(0))) {
                writeForbidden(response, "Insufficient permissions");
                return false;
            }
        }

        request.setAttribute("currentUser", userInfo);
        request.setAttribute("currentToken", token);
        
        log.debug("Authenticated user: {}", userInfo.getUsername());
        return true;
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        Map<String, Object> result = new HashMap<>();
        result.put("code", "UNAUTHORIZED");
        result.put("message", message);
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }

    private void writeForbidden(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        Map<String, Object> result = new HashMap<>();
        result.put("code", "FORBIDDEN");
        result.put("message", message);
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }
}
