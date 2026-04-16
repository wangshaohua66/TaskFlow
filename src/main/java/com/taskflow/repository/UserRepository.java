package com.taskflow.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.taskflow.model.User;
import com.taskflow.model.UserRole;
import com.taskflow.model.UserStatus;
import org.springframework.stereotype.Repository;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 用户仓库 - 使用JSON文件存储用户数据
 */
@Repository
public class UserRepository {

    private static final String DATA_DIR = "data";
    private static final String USERS_FILE = "users.json";
    private final ObjectMapper objectMapper;
    private final CopyOnWriteArrayList<User> users = new CopyOnWriteArrayList<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    public UserRepository() {
        // 配置ObjectMapper以支持Java 8日期时间类型
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @PostConstruct
    public void init() {
        loadData();
        // 如果没有用户，创建默认管理员账号
        if (users.isEmpty()) {
            createDefaultAdmin();
        }
    }

    /**
     * 创建默认管理员账号
     */
    private void createDefaultAdmin() {
        User admin = new User("admin", hashPassword("admin123"), "admin@taskflow.com", "系统管理员");
        admin.setId(idGenerator.getAndIncrement());
        admin.setRole(UserRole.ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        admin.setCreatedAt(LocalDateTime.now());
        admin.setUpdatedAt(LocalDateTime.now());
        users.add(admin);
        saveData();
        System.out.println("已创建默认管理员账号: admin / admin123");
    }

    /**
     * 加载数据
     */
    private void loadData() {
        try {
            Path dataDir = Paths.get(DATA_DIR);
            if (!Files.exists(dataDir)) {
                Files.createDirectories(dataDir);
            }

            File file = new File(DATA_DIR, USERS_FILE);
            if (file.exists() && file.length() > 0) {
                List<User> loadedUsers = objectMapper.readValue(file, new TypeReference<List<User>>() {});
                users.clear();
                users.addAll(loadedUsers);

                // 更新ID生成器
                long maxId = users.stream()
                        .mapToLong(User::getId)
                        .max()
                        .orElse(0);
                idGenerator.set(maxId + 1);
            }
        } catch (IOException e) {
            System.err.println("加载用户数据失败: " + e.getMessage());
        }
    }

    /**
     * 保存数据
     */
    private synchronized void saveData() {
        try {
            Path dataDir = Paths.get(DATA_DIR);
            if (!Files.exists(dataDir)) {
                Files.createDirectories(dataDir);
            }

            File file = new File(DATA_DIR, USERS_FILE);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, users);
        } catch (IOException e) {
            System.err.println("保存用户数据失败: " + e.getMessage());
            throw new RuntimeException("保存用户数据失败", e);
        }
    }

    /**
     * 密码哈希（简单实现，生产环境应使用BCrypt）
     */
    public String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("密码加密失败", e);
        }
    }

    /**
     * 验证密码
     */
    public boolean verifyPassword(String rawPassword, String hashedPassword) {
        return hashPassword(rawPassword).equals(hashedPassword);
    }

    /**
     * 根据用户名查找用户
     */
    public Optional<User> findByUsername(String username) {
        return users.stream()
                .filter(u -> u.getUsername().equalsIgnoreCase(username))
                .findFirst();
    }

    /**
     * 根据ID查找用户
     */
    public Optional<User> findById(Long id) {
        return users.stream()
                .filter(u -> u.getId().equals(id))
                .findFirst();
    }

    /**
     * 根据邮箱查找用户
     */
    public Optional<User> findByEmail(String email) {
        return users.stream()
                .filter(u -> u.getEmail().equalsIgnoreCase(email))
                .findFirst();
    }

    /**
     * 获取所有用户
     */
    public List<User> findAll() {
        return new ArrayList<>(users);
    }

    /**
     * 保存或更新用户
     */
    public User save(User user) {
        if (user.getId() == null) {
            // 新用户
            user.setId(idGenerator.getAndIncrement());
            user.setCreatedAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());
            users.add(user);
        } else {
            // 更新现有用户
            user.setUpdatedAt(LocalDateTime.now());
            int index = -1;
            for (int i = 0; i < users.size(); i++) {
                if (users.get(i).getId().equals(user.getId())) {
                    index = i;
                    break;
                }
            }
            if (index >= 0) {
                users.set(index, user);
            } else {
                users.add(user);
            }
        }
        saveData();
        return user;
    }

    /**
     * 删除用户
     */
    public void deleteById(Long id) {
        users.removeIf(u -> u.getId().equals(id));
        saveData();
    }

    /**
     * 检查用户名是否存在
     */
    public boolean existsByUsername(String username) {
        return users.stream()
                .anyMatch(u -> u.getUsername().equalsIgnoreCase(username));
    }

    /**
     * 检查邮箱是否存在
     */
    public boolean existsByEmail(String email) {
        return users.stream()
                .anyMatch(u -> u.getEmail().equalsIgnoreCase(email));
    }

    /**
     * 统计用户数量
     */
    public long count() {
        return users.size();
    }

    /**
     * 更新最后登录时间
     */
    public void updateLastLogin(Long userId) {
        findById(userId).ifPresent(user -> {
            user.setLastLoginAt(LocalDateTime.now());
            save(user);
        });
    }
}
