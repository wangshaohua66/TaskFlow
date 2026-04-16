package com.taskflow.controller;

import com.taskflow.exception.BusinessRuleException;
import com.taskflow.exception.OptimisticLockException;
import com.taskflow.exception.ResourceNotFoundException;
import com.taskflow.exception.TodoException;
import com.taskflow.model.*;
import com.taskflow.service.TodoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 任务REST API控制器
 * 
 * 提供以下功能:
 * 1. 任务CRUD操作
 * 2. 状态管理
 * 3. 标签和子任务管理
 * 4. 高级搜索
 * 5. 统计分析
 */
@RestController
@RequestMapping("/api/todos")
@CrossOrigin(origins = "*")
public class TodoController {
    private final TodoService todoService;

    public TodoController(TodoService todoService) {
        this.todoService = todoService;
    }

    /**
     * 创建Todo
     * POST /api/todos
     */
    @PostMapping
    public ResponseEntity<Todo> createTodo(@RequestBody CreateTodoRequest request) {
        try {
            Todo todo = todoService.createTodo(
                request.getTitle(),
                request.getDescription(),
                request.getPriority(),
                request.getDueDate(),
                request.getReminderTime(),
                request.getTags(),
                request.getParentId()
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(todo);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 查询所有Todo (分页)
     * GET /api/todos?page=0&size=20
     */
    @GetMapping
    public ResponseEntity<PageResult<Todo>> getAllTodos(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        SearchCriteria criteria = new SearchCriteria();
        criteria.setPage(page);
        criteria.setSize(size);
        PageResult<Todo> result = todoService.searchTodos(criteria);
        return ResponseEntity.ok(result);
    }

    /**
     * 根据ID查询
     * GET /api/todos/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<Todo> getTodo(@PathVariable Long id) {
        try {
            Todo todo = todoService.getTodoById(id);
            return ResponseEntity.ok(todo);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 更新Todo
     * PUT /api/todos/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateTodo(@PathVariable Long id, @RequestBody UpdateTodoRequest request) {
        try {
            Todo todo = todoService.updateTodo(
                id,
                request.getTitle(),
                request.getDescription(),
                request.getPriority(),
                request.getDueDate(),
                request.getReminderTime(),
                request.getVersion()
            );
            return ResponseEntity.ok(todo);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (OptimisticLockException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Collections.singletonMap("error", e.getMessage()));
        } catch (BusinessRuleException e) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("error", e.getMessage()));
        }
    }

    /**
     * 部分更新
     * PATCH /api/todos/{id}
     */
    @PatchMapping("/{id}")
    public ResponseEntity<?> patchTodo(@PathVariable Long id, @RequestBody Map<String, Object> updates) {
        try {
            Integer version = (Integer) updates.remove("version");
            if (version == null) {
                return ResponseEntity.badRequest().body(Collections.singletonMap("error", "缺少版本号"));
            }

            Todo todo = todoService.patchTodo(id, updates, version);
            return ResponseEntity.ok(todo);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (OptimisticLockException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Collections.singletonMap("error", e.getMessage()));
        }
    }

    /**
     * 删除Todo
     * DELETE /api/todos/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTodo(@PathVariable Long id) {
        try {
            todoService.deleteTodo(id);
            return ResponseEntity.noContent().build();
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (BusinessRuleException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 更改状态
     * PATCH /api/todos/{id}/status
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<?> changeStatus(@PathVariable Long id, @RequestBody ChangeStatusRequest request) {
        try {
            Todo todo = todoService.changeStatus(id, request.getStatus(), request.getCompletedSubTodoIds());
            return ResponseEntity.ok(todo);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (BusinessRuleException e) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("error", e.getMessage()));
        }
    }

    /**
     * 添加标签
     * POST /api/todos/{id}/tags
     */
    @PostMapping("/{id}/tags")
    public ResponseEntity<?> addTag(@PathVariable Long id, @RequestBody Map<String, String> request) {
        try {
            String tag = request.get("tag");
            if (tag == null || tag.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Collections.singletonMap("error", "标签不能为空"));
            }
            Todo todo = todoService.addTag(id, tag);
            return ResponseEntity.ok(todo);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("error", e.getMessage()));
        }
    }

    /**
     * 移除标签
     * DELETE /api/todos/{id}/tags/{tag}
     */
    @DeleteMapping("/{id}/tags/{tag}")
    public ResponseEntity<Todo> removeTag(@PathVariable Long id, @PathVariable String tag) {
        try {
            Todo todo = todoService.removeTag(id, tag);
            return ResponseEntity.ok(todo);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 添加子任务
     * POST /api/todos/{parentId}/subtodos
     */
    @PostMapping("/{parentId}/subtodos")
    public ResponseEntity<?> addSubTodo(@PathVariable Long parentId, @RequestBody Map<String, Long> request) {
        try {
            Long subTodoId = request.get("subTodoId");
            if (subTodoId == null) {
                return ResponseEntity.badRequest().body(Collections.singletonMap("error", "子任务ID不能为空"));
            }
            Todo todo = todoService.addSubTodo(parentId, subTodoId);
            return ResponseEntity.ok(todo);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (BusinessRuleException e) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("error", e.getMessage()));
        }
    }

    /**
     * 移除子任务
     * DELETE /api/todos/{parentId}/subtodos/{subTodoId}
     */
    @DeleteMapping("/{parentId}/subtodos/{subTodoId}")
    public ResponseEntity<?> removeSubTodo(@PathVariable Long parentId, @PathVariable Long subTodoId) {
        try {
            Todo todo = todoService.removeSubTodo(parentId, subTodoId);
            return ResponseEntity.ok(todo);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 搜索
     * GET /api/todos/search?keyword=xxx&status=PENDING&priority=HIGH
     */
    @GetMapping("/search")
    public ResponseEntity<PageResult<Todo>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) TodoStatus status,
            @RequestParam(required = false) Priority priority,
            @RequestParam(required = false) LocalDateTime dueDateFrom,
            @RequestParam(required = false) LocalDateTime dueDateTo,
            @RequestParam(required = false) Set<String> tags,
            @RequestParam(required = false) Boolean hasSubTodos,
            @RequestParam(required = false) Boolean isExpired,
            @RequestParam(required = false) Long parentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        SearchCriteria criteria = new SearchCriteria();
        criteria.setKeyword(keyword);
        criteria.setStatus(status);
        criteria.setPriority(priority);
        criteria.setDueDateFrom(dueDateFrom);
        criteria.setDueDateTo(dueDateTo);
        criteria.setTags(tags);
        criteria.setHasSubTodos(hasSubTodos);
        criteria.setIsExpired(isExpired);
        criteria.setParentId(parentId);
        criteria.setPage(page);
        criteria.setSize(size);

        try {
            PageResult<Todo> result = todoService.searchTodos(criteria);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 获取统计信息
     * GET /api/todos/statistics
     */
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getStatistics() {
        Map<String, Object> stats = todoService.getStatistics();
        return ResponseEntity.ok(stats);
    }

    /**
     * 升级过期任务优先级
     * POST /api/todos/upgrade-expired
     */
    @PostMapping("/upgrade-expired")
    public ResponseEntity<List<Todo>> upgradeExpiredTodos() {
        List<Todo> upgraded = todoService.upgradeExpiredTodosPriority();
        return ResponseEntity.ok(upgraded);
    }

    /**
     * 批量删除已完成
     * DELETE /api/todos/completed
     */
    @DeleteMapping("/completed")
    public ResponseEntity<Map<String, Integer>> deleteCompletedTodos() {
        int count = todoService.deleteCompletedTodos();
        Map<String, Integer> result = new HashMap<>();
        result.put("deletedCount", count);
        return ResponseEntity.ok(result);
    }

    /**
     * 克隆任务
     * POST /api/todos/{id}/clone
     */
    @PostMapping("/{id}/clone")
    public ResponseEntity<Todo> cloneTodo(@PathVariable Long id) {
        try {
            Todo cloned = todoService.cloneTodo(id);
            return ResponseEntity.status(HttpStatus.CREATED).body(cloned);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ==================== 请求DTO类 ====================

    public static class CreateTodoRequest {
        private String title;
        private String description;
        private Priority priority;
        private LocalDateTime dueDate;
        private LocalDateTime reminderTime;
        private Set<String> tags;
        private Long parentId;

        // Getters and Setters
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public Priority getPriority() { return priority; }
        public void setPriority(Priority priority) { this.priority = priority; }
        public LocalDateTime getDueDate() { return dueDate; }
        public void setDueDate(LocalDateTime dueDate) { this.dueDate = dueDate; }
        public LocalDateTime getReminderTime() { return reminderTime; }
        public void setReminderTime(LocalDateTime reminderTime) { this.reminderTime = reminderTime; }
        public Set<String> getTags() { return tags; }
        public void setTags(Set<String> tags) { this.tags = tags; }
        public Long getParentId() { return parentId; }
        public void setParentId(Long parentId) { this.parentId = parentId; }
    }

    public static class UpdateTodoRequest {
        private String title;
        private String description;
        private Priority priority;
        private LocalDateTime dueDate;
        private LocalDateTime reminderTime;
        private int version;

        // Getters and Setters
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public Priority getPriority() { return priority; }
        public void setPriority(Priority priority) { this.priority = priority; }
        public LocalDateTime getDueDate() { return dueDate; }
        public void setDueDate(LocalDateTime dueDate) { this.dueDate = dueDate; }
        public LocalDateTime getReminderTime() { return reminderTime; }
        public void setReminderTime(LocalDateTime reminderTime) { this.reminderTime = reminderTime; }
        public int getVersion() { return version; }
        public void setVersion(int version) { this.version = version; }
    }

    public static class ChangeStatusRequest {
        private TodoStatus status;
        private Set<Long> completedSubTodoIds;

        // Getters and Setters
        public TodoStatus getStatus() { return status; }
        public void setStatus(TodoStatus status) { this.status = status; }
        public Set<Long> getCompletedSubTodoIds() { return completedSubTodoIds; }
        public void setCompletedSubTodoIds(Set<Long> completedSubTodoIds) { this.completedSubTodoIds = completedSubTodoIds; }
    }
}
