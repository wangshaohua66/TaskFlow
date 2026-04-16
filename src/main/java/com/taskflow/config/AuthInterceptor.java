package com.taskflow.config;

import com.taskflow.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 身份验证拦截器 - 验证用户会话
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Autowired
    private SessionManager sessionManager;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 允许OPTIONS请求（CORS预检）
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // 获取Authorization头
        String authHeader = request.getHeader("Authorization");
        String token = null;

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        } else if (authHeader != null) {
            token = authHeader;
        }

        // 验证会话
        if (token == null || token.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"未登录或会话已过期\",\"code\":401}");
            return false;
        }

        SessionManager.Session session = sessionManager.getSession(token);
        if (session == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"会话无效或已过期\",\"code\":401}");
            return false;
        }

        // 将用户信息添加到请求属性中，供后续使用
        User user = session.getUser();
        request.setAttribute("currentUser", user);
        request.setAttribute("currentUserId", user.getId());
        request.setAttribute("currentUserRole", user.getRole());

        return true;
    }
}
