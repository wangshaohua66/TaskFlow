package com.taskflow.service;

import com.taskflow.config.SessionManager;
import com.taskflow.model.User;
import com.taskflow.model.UserRole;
import com.taskflow.model.UserStatus;
import com.taskflow.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 认证服务 - 处理用户登录、注册等认证相关操作
 */
@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SessionManager sessionManager;

    /**
     * 用户登录
     */
    public Map<String, Object> login(String username, String password) {
        Map<String, Object> result = new HashMap<>();

        // 查找用户
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (!userOpt.isPresent()) {
            result.put("success", false);
            result.put("message", "用户名或密码错误");
            return result;
        }

        User user = userOpt.get();

        // 检查用户状态
        if (user.getStatus() == UserStatus.LOCKED) {
            result.put("success", false);
            result.put("message", "账号已被锁定，请联系管理员");
            return result;
        }

        if (user.getStatus() == UserStatus.INACTIVE) {
            result.put("success", false);
            result.put("message", "账号未激活");
            return result;
        }

        // 验证密码
        if (!userRepository.verifyPassword(password, user.getPassword())) {
            result.put("success", false);
            result.put("message", "用户名或密码错误");
            return result;
        }

        // 创建会话
        String token = sessionManager.createSession(user);

        // 更新最后登录时间
        userRepository.updateLastLogin(user.getId());

        // 返回用户信息（不包含密码）
        Map<String, Object> userInfo = getUserInfo(user);
        userInfo.put("token", token);

        result.put("success", true);
        result.put("data", userInfo);
        result.put("message", "登录成功");

        return result;
    }

    /**
     * 用户注册
     */
    public Map<String, Object> register(String username, String password, String email, String displayName) {
        Map<String, Object> result = new HashMap<>();

        // 验证输入
        if (username == null || username.trim().isEmpty()) {
            result.put("success", false);
            result.put("message", "用户名不能为空");
            return result;
        }

        if (password == null || password.length() < 6) {
            result.put("success", false);
            result.put("message", "密码长度至少为6位");
            return result;
        }

        if (email == null || email.trim().isEmpty()) {
            result.put("success", false);
            result.put("message", "邮箱不能为空");
            return result;
        }

        // 检查用户名是否已存在
        if (userRepository.existsByUsername(username)) {
            result.put("success", false);
            result.put("message", "用户名已存在");
            return result;
        }

        // 检查邮箱是否已存在
        if (userRepository.existsByEmail(email)) {
            result.put("success", false);
            result.put("message", "邮箱已被注册");
            return result;
        }

        // 创建新用户
        User user = new User();
        user.setUsername(username);
        user.setPassword(userRepository.hashPassword(password));
        user.setEmail(email);
        user.setDisplayName(displayName != null ? displayName : username);
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);

        userRepository.save(user);

        result.put("success", true);
        result.put("message", "注册成功");
        result.put("data", getUserInfo(user));

        return result;
    }

    /**
     * 用户登出
     */
    public void logout(String token) {
        if (token != null && !token.isEmpty()) {
            sessionManager.invalidateSession(token);
        }
    }

    /**
     * 获取当前用户信息
     */
    public Map<String, Object> getCurrentUser(String token) {
        SessionManager.Session session = sessionManager.getSession(token);
        if (session == null) {
            return null;
        }
        return getUserInfo(session.getUser());
    }

    /**
     * 获取所有用户（管理员功能）
     */
    public Map<String, Object> getAllUsers() {
        Map<String, Object> result = new HashMap<>();
        result.put("users", userRepository.findAll().stream()
                .map(this::getUserInfo)
                .toArray());
        result.put("total", userRepository.count());
        return result;
    }

    /**
     * 删除用户（管理员功能）
     */
    public Map<String, Object> deleteUser(Long userId) {
        Map<String, Object> result = new HashMap<>();

        Optional<User> userOpt = userRepository.findById(userId);
        if (!userOpt.isPresent()) {
            result.put("success", false);
            result.put("message", "用户不存在");
            return result;
        }

        User user = userOpt.get();

        // 不允许删除最后一个管理员
        if (user.getRole() == UserRole.ADMIN) {
            long adminCount = userRepository.findAll().stream()
                    .filter(u -> u.getRole() == UserRole.ADMIN)
                    .count();
            if (adminCount <= 1) {
                result.put("success", false);
                result.put("message", "不能删除最后一个管理员账号");
                return result;
            }
        }

        userRepository.deleteById(userId);
        result.put("success", true);
        result.put("message", "删除成功");

        return result;
    }

    /**
     * 更新用户角色（管理员功能）
     */
    public Map<String, Object> updateUserRole(Long userId, UserRole role) {
        Map<String, Object> result = new HashMap<>();

        Optional<User> userOpt = userRepository.findById(userId);
        if (!userOpt.isPresent()) {
            result.put("success", false);
            result.put("message", "用户不存在");
            return result;
        }

        User user = userOpt.get();
        user.setRole(role);
        userRepository.save(user);

        result.put("success", true);
        result.put("message", "更新成功");
        result.put("data", getUserInfo(user));

        return result;
    }

    /**
     * 更新用户状态（管理员功能）
     */
    public Map<String, Object> updateUserStatus(Long userId, UserStatus status) {
        Map<String, Object> result = new HashMap<>();

        Optional<User> userOpt = userRepository.findById(userId);
        if (!userOpt.isPresent()) {
            result.put("success", false);
            result.put("message", "用户不存在");
            return result;
        }

        User user = userOpt.get();
        user.setStatus(status);
        userRepository.save(user);

        result.put("success", true);
        result.put("message", "更新成功");
        result.put("data", getUserInfo(user));

        return result;
    }

    /**
     * 修改密码
     */
    public Map<String, Object> changePassword(String token, String oldPassword, String newPassword) {
        Map<String, Object> result = new HashMap<>();

        SessionManager.Session session = sessionManager.getSession(token);
        if (session == null) {
            result.put("success", false);
            result.put("message", "请先登录");
            return result;
        }

        User user = session.getUser();

        // 验证旧密码
        if (!userRepository.verifyPassword(oldPassword, user.getPassword())) {
            result.put("success", false);
            result.put("message", "原密码错误");
            return result;
        }

        // 验证新密码
        if (newPassword == null || newPassword.length() < 6) {
            result.put("success", false);
            result.put("message", "新密码长度至少为6位");
            return result;
        }

        // 更新密码
        user.setPassword(userRepository.hashPassword(newPassword));
        userRepository.save(user);

        result.put("success", true);
        result.put("message", "密码修改成功");

        return result;
    }

    /**
     * 将用户对象转换为不包含敏感信息的Map
     */
    private Map<String, Object> getUserInfo(User user) {
        Map<String, Object> info = new HashMap<>();
        info.put("id", user.getId());
        info.put("username", user.getUsername());
        info.put("email", user.getEmail());
        info.put("displayName", user.getDisplayName());
        info.put("role", user.getRole().name());
        info.put("status", user.getStatus().name());
        info.put("createdAt", user.getCreatedAt().toString());
        info.put("lastLoginAt", user.getLastLoginAt() != null ? user.getLastLoginAt().toString() : null);
        return info;
    }
}
