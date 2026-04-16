package com.taskflow.model;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 任务实体类
 * 
 * 业务规则:
 * 1. 标题不能为空,长度1-200字符
 * 2. 描述可选,最大5000字符
 * 3. 截止日期不能早于创建时间
 * 4. 提醒时间必须早于截止日期
 * 5. 标签最多5个,不能重复
 * 6. 子任务数量最多20个
 * 7. 父任务完成需要所有子任务完成
 * 8. 过期任务自动提升优先级
 * 9. 支持乐观锁(版本号)
 */
public class Todo {
    private static final AtomicLong ID_GENERATOR = new AtomicLong(1);

    // 常量定义
    public static final int MAX_TITLE_LENGTH = 200;
    public static final int MAX_DESCRIPTION_LENGTH = 5000;
    public static final int MAX_TAGS_COUNT = 5;
    public static final int MAX_SUBTODOS_COUNT = 20;
    public static final long EXPIRED_HOURS_THRESHOLD = 24; // 过期24小时后升级优先级

    private Long id;
    private String title;
    private String description;
    private TodoStatus status;
    private Priority priority;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime dueDate;
    private LocalDateTime reminderTime;
    private Long parentId;
    private List<Long> subTodoIds;
    private Set<String> tags;
    private int version; // 乐观锁版本号

    public Todo() {
        this.id = ID_GENERATOR.getAndIncrement();
        this.status = TodoStatus.PENDING;
        this.priority = Priority.MEDIUM;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.subTodoIds = new ArrayList<>();
        this.tags = new HashSet<>();
        this.version = 0;
    }

    public Todo(String title, String description, Priority priority, LocalDateTime dueDate) {
        this();
        this.title = title;
        this.description = description;
        this.priority = priority != null ? priority : Priority.MEDIUM;
        this.dueDate = dueDate;
    }

    // ==================== 验证方法 ====================

    /**
     * 验证Todo数据合法性
     * @throws IllegalArgumentException 如果数据不合法
     */
    public void validate() {
        validateTitle();
        validateDescription();
        validateDates();
        validateTags();
        validateSubTodos();
    }

    private void validateTitle() {
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("标题不能为空");
        }
        if (title.length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException("标题长度不能超过" + MAX_TITLE_LENGTH + "字符");
        }
    }

    private void validateDescription() {
        if (description != null && description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException("描述长度不能超过" + MAX_DESCRIPTION_LENGTH + "字符");
        }
    }

    private void validateDates() {
        if (dueDate != null) {
            if (dueDate.isBefore(createdAt)) {
                throw new IllegalArgumentException("截止日期不能早于创建时间");
            }
            if (reminderTime != null && reminderTime.isAfter(dueDate)) {
                throw new IllegalArgumentException("提醒时间必须早于截止日期");
            }
        }
    }

    private void validateTags() {
        if (tags.size() > MAX_TAGS_COUNT) {
            throw new IllegalArgumentException("标签数量不能超过" + MAX_TAGS_COUNT + "个");
        }
    }

    private void validateSubTodos() {
        if (subTodoIds.size() > MAX_SUBTODOS_COUNT) {
            throw new IllegalArgumentException("子任务数量不能超过" + MAX_SUBTODOS_COUNT + "个");
        }
    }

    // ==================== 状态转换逻辑 ====================

    /**
     * 检查状态转换是否合法
     */
    public boolean canTransitionTo(TodoStatus newStatus) {
        switch (this.status) {
            case PENDING:
                return newStatus == TodoStatus.IN_PROGRESS || newStatus == TodoStatus.CANCELLED;
            case IN_PROGRESS:
                return newStatus == TodoStatus.COMPLETED ||
                       newStatus == TodoStatus.PENDING ||
                       newStatus == TodoStatus.CANCELLED;
            case COMPLETED:
                return false; // 终态,不允许转换
            case CANCELLED:
                return newStatus == TodoStatus.PENDING; // 可重新激活
            case EXPIRED:
                return newStatus == TodoStatus.IN_PROGRESS || newStatus == TodoStatus.CANCELLED;
            default:
                return false;
        }
    }

    /**
     * 执行状态转换
     */
    public void transitionTo(TodoStatus newStatus) {
        if (!canTransitionTo(newStatus)) {
            throw new IllegalStateException(
                String.format("不允许从 %s 转换到 %s", this.status, newStatus)
            );
        }
        this.status = newStatus;
        this.updatedAt = LocalDateTime.now();
        this.version++;
    }

    // ==================== 业务逻辑方法 ====================

    /**
     * 检查是否已过期
     */
    public boolean isExpired() {
        if (dueDate == null) {
            return false;
        }
        return LocalDateTime.now().isAfter(dueDate);
    }

    /**
     * 检查是否需要升级优先级(过期超过24小时)
     */
    public boolean shouldUpgradePriority() {
        if (!isExpired() || status == TodoStatus.COMPLETED || status == TodoStatus.CANCELLED) {
            return false;
        }
        LocalDateTime expiredSince = LocalDateTime.now().minusHours(EXPIRED_HOURS_THRESHOLD);
        return dueDate.isBefore(expiredSince);
    }

    /**
     * 升级优先级
     */
    public void upgradePriority() {
        if (priority != Priority.CRITICAL) {
            this.priority = priority.upgrade();
            this.updatedAt = LocalDateTime.now();
            this.version++;
        }
    }

    /**
     * 添加标签
     */
    public void addTag(String tag) {
        if (tag == null || tag.trim().isEmpty()) {
            throw new IllegalArgumentException("标签不能为空");
        }
        if (tags.contains(tag)) {
            throw new IllegalArgumentException("标签已存在: " + tag);
        }
        if (tags.size() >= MAX_TAGS_COUNT) {
            throw new IllegalArgumentException("标签数量已达上限");
        }
        tags.add(tag.trim().toLowerCase());
        this.updatedAt = LocalDateTime.now();
        this.version++;
    }

    /**
     * 移除标签
     */
    public void removeTag(String tag) {
        if (tag == null) {
            throw new IllegalArgumentException("标签不能为空");
        }
        boolean removed = tags.remove(tag.toLowerCase());
        if (removed) {
            this.updatedAt = LocalDateTime.now();
            this.version++;
        }
    }

    /**
     * 添加子任务ID
     */
    public void addSubTodo(Long subTodoId) {
        if (subTodoId == null) {
            throw new IllegalArgumentException("子任务ID不能为空");
        }
        if (subTodoId.equals(this.id)) {
            throw new IllegalArgumentException("不能将自己添加为子任务");
        }
        if (subTodoIds.contains(subTodoId)) {
            throw new IllegalArgumentException("子任务已存在");
        }
        if (subTodoIds.size() >= MAX_SUBTODOS_COUNT) {
            throw new IllegalArgumentException("子任务数量已达上限");
        }
        subTodoIds.add(subTodoId);
        this.updatedAt = LocalDateTime.now();
        this.version++;
    }

    /**
     * 移除子任务ID
     */
    public void removeSubTodo(Long subTodoId) {
        boolean removed = subTodoIds.remove(subTodoId);
        if (removed) {
            this.updatedAt = LocalDateTime.now();
            this.version++;
        }
    }

    /**
     * 检查是否可以完成(如果有子任务,需要所有子任务都完成)
     */
    public boolean canComplete(Set<Long> completedSubTodoIds) {
        if (status != TodoStatus.IN_PROGRESS && status != TodoStatus.PENDING) {
            return false;
        }
        if (subTodoIds.isEmpty()) {
            return true;
        }
        // 所有子任务都必须已完成
        return completedSubTodoIds.containsAll(subTodoIds);
    }

    /**
     * 更新字段(用于部分更新)
     */
    public void update(String title, String description, Priority priority,
                      LocalDateTime dueDate, LocalDateTime reminderTime) {
        if (title != null) {
            this.title = title;
        }
        if (description != null) {
            this.description = description;
        }
        if (priority != null) {
            this.priority = priority;
        }
        if (dueDate != null) {
            this.dueDate = dueDate;
        }
        if (reminderTime != null) {
            this.reminderTime = reminderTime;
        }
        this.updatedAt = LocalDateTime.now();
        this.version++;
        validate();
    }

    // ==================== Getters and Setters ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public TodoStatus getStatus() { return status; }
    public void setStatus(TodoStatus status) { this.status = status; }

    public Priority getPriority() { return priority; }
    public void setPriority(Priority priority) { this.priority = priority; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public LocalDateTime getDueDate() { return dueDate; }
    public void setDueDate(LocalDateTime dueDate) { this.dueDate = dueDate; }

    public LocalDateTime getReminderTime() { return reminderTime; }
    public void setReminderTime(LocalDateTime reminderTime) { this.reminderTime = reminderTime; }

    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }

    public List<Long> getSubTodoIds() { return subTodoIds; }
    public void setSubTodoIds(List<Long> subTodoIds) { this.subTodoIds = subTodoIds; }

    public Set<String> getTags() { return tags; }
    public void setTags(Set<String> tags) { this.tags = tags; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    @Override
    public String toString() {
        return "Todo{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", status=" + status +
                ", priority=" + priority +
                ", version=" + version +
                '}';
    }
}
