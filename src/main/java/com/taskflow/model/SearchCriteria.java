package com.taskflow.model;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 搜索条件 - 支持多条件组合查询
 */
public class SearchCriteria {
    private String keyword; // 关键词搜索(标题或描述)
    private TodoStatus status;
    private Priority priority;
    private LocalDateTime dueDateFrom;
    private LocalDateTime dueDateTo;
    private Set<String> tags; // 包含所有指定标签
    private Boolean hasSubTodos; // 是否有子任务
    private Boolean isExpired; // 是否过期
    private Long parentId; // 父任务ID过滤
    private int page = 0;
    private int size = 20;

    public SearchCriteria() {}

    public void validate() {
        if (page < 0) {
            throw new IllegalArgumentException("页码不能为负数");
        }
        if (size <= 0 || size > 100) {
            throw new IllegalArgumentException("每页大小必须在1-100之间");
        }
        if (dueDateFrom != null && dueDateTo != null && dueDateFrom.isAfter(dueDateTo)) {
            throw new IllegalArgumentException("开始日期不能晚于结束日期");
        }
    }

    // Getters and Setters
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }

    public TodoStatus getStatus() { return status; }
    public void setStatus(TodoStatus status) { this.status = status; }

    public Priority getPriority() { return priority; }
    public void setPriority(Priority priority) { this.priority = priority; }

    public LocalDateTime getDueDateFrom() { return dueDateFrom; }
    public void setDueDateFrom(LocalDateTime dueDateFrom) { this.dueDateFrom = dueDateFrom; }

    public LocalDateTime getDueDateTo() { return dueDateTo; }
    public void setDueDateTo(LocalDateTime dueDateTo) { this.dueDateTo = dueDateTo; }

    public Set<String> getTags() { return tags; }
    public void setTags(Set<String> tags) { this.tags = tags; }

    public Boolean getHasSubTodos() { return hasSubTodos; }
    public void setHasSubTodos(Boolean hasSubTodos) { this.hasSubTodos = hasSubTodos; }

    public Boolean getIsExpired() { return isExpired; }
    public void setIsExpired(Boolean expired) { isExpired = expired; }

    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }

    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
}
