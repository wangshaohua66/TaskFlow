package com.taskflow.config;

import com.taskflow.model.User;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 会话管理器 - 管理用户登录会话
 */
@Component
public class SessionManager {

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private static final long SESSION_TIMEOUT_HOURS = 24;

    @PostConstruct
    public void init() {
        // 启动定时任务清理过期会话
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(this::cleanupExpiredSessions, 1, 1, TimeUnit.HOURS);
    }

    /**
     * 创建新会话
     */
    public String createSession(User user) {
        String token = UUID.randomUUID().toString();
        Session session = new Session(token, user);
        sessions.put(token, session);
        return token;
    }

    /**
     * 获取会话
     */
    public Session getSession(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        Session session = sessions.get(token);
        if (session != null && !session.isExpired()) {
            session.refresh();
            return session;
        }
        if (session != null && session.isExpired()) {
            sessions.remove(token);
        }
        return null;
    }

    /**
     * 使会话失效
     */
    public void invalidateSession(String token) {
        sessions.remove(token);
    }

    /**
     * 清理过期会话
     */
    private void cleanupExpiredSessions() {
        sessions.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    /**
     * 会话内部类
     */
    public static class Session {
        private final String token;
        private final User user;
        private long lastAccessTime;

        public Session(String token, User user) {
            this.token = token;
            this.user = user;
            this.lastAccessTime = System.currentTimeMillis();
        }

        public String getToken() {
            return token;
        }

        public User getUser() {
            return user;
        }

        public boolean isExpired() {
            long now = System.currentTimeMillis();
            long timeout = TimeUnit.HOURS.toMillis(SESSION_TIMEOUT_HOURS);
            return (now - lastAccessTime) > timeout;
        }

        public void refresh() {
            this.lastAccessTime = System.currentTimeMillis();
        }

        public long getLastAccessTime() {
            return lastAccessTime;
        }
    }
}
