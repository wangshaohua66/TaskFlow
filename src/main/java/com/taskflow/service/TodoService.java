package com.taskflow.service;

import com.taskflow.exception.BusinessRuleException;
import com.taskflow.exception.OptimisticLockException;
import com.taskflow.exception.ResourceNotFoundException;
import com.taskflow.model.*;
import com.taskflow.repository.TodoRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 任务业务服务层
 * 
 * 核心功能:
 * 1. 任务状态机管理
 * 2. 子任务依赖检查
 * 3. 优先级自动升级
 * 4. 并发冲突处理(乐观锁)
 * 5. 级联操作
 */
@Service
public class TodoService {
    private final TodoRepository todoRepository;

    public TodoService(TodoRepository todoRepository) {
        this.todoRepository = todoRepository;
    }

    /**
     * 创建Todo
     */
    public Todo createTodo(String title, String description, Priority priority,
                          LocalDateTime dueDate, LocalDateTime reminderTime,
                          Set<String> tags, Long parentId) {
        Todo todo = new Todo(title, description, priority, dueDate);
        todo.setReminderTime(reminderTime);

        // 验证
        todo.validate();

        // 如果有父任务,建立关联
        if (parentId != null) {
            Todo parent = todoRepository.findById(parentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Parent Todo", parentId));

            // 检查父任务是否可以有子任务
            if (parent.getStatus() == TodoStatus.COMPLETED) {
                throw new BusinessRuleException("已完成的任务不能添加子任务");
            }

            todo.setParentId(parentId);
            parent.addSubTodo(todo.getId());
            todoRepository.save(parent);
        }

        // 添加标签
        if (tags != null) {
            for (String tag : tags) {
                todo.addTag(tag);
            }
        }

        return todoRepository.save(todo);
    }

    /**
     * 根据ID查询
     */
    public Todo getTodoById(Long id) {
        return todoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Todo", id));
    }

    /**
     * 更新Todo (完整更新)
     */
    public Todo updateTodo(Long id, String title, String description, Priority priority,
                          LocalDateTime dueDate, LocalDateTime reminderTime, int expectedVersion) {
        Todo todo = getTodoById(id);

        // 检查乐观锁
        if (todo.getVersion() != expectedVersion) {
            throw new OptimisticLockException(id);
        }

        // 已完成的不能修改
        if (todo.getStatus() == TodoStatus.COMPLETED) {
            throw new BusinessRuleException("已完成的任务不能修改");
        }

        todo.update(title, description, priority, dueDate, reminderTime);
        return todoRepository.save(todo);
    }

    /**
     * 部分更新Todo
     */
    public Todo patchTodo(Long id, Map<String, Object> updates, int expectedVersion) {
        Todo todo = getTodoById(id);

        if (todo.getVersion() != expectedVersion) {
            throw new OptimisticLockException(id);
        }

        if (todo.getStatus() == TodoStatus.COMPLETED) {
            throw new BusinessRuleException("已完成的任务不能修改");
        }

        // 应用更新
        if (updates.containsKey("title")) {
            todo.setTitle((String) updates.get("title"));
        }
        if (updates.containsKey("description")) {
            todo.setDescription((String) updates.get("description"));
        }
        if (updates.containsKey("priority")) {
            todo.setPriority(Priority.valueOf((String) updates.get("priority")));
        }
        if (updates.containsKey("dueDate") && updates.get("dueDate") != null) {
            todo.setDueDate(LocalDateTime.parse((String) updates.get("dueDate")));
        }

        todo.setUpdatedAt(LocalDateTime.now());
        todo.setVersion(expectedVersion + 1);
        todo.validate();

        return todoRepository.save(todo);
    }

    /**
     * 删除Todo (级联删除子任务)
     */
    public void deleteTodo(Long id) {
        Todo todo = getTodoById(id);

        // 检查是否有未完成子任务
        List<Todo> subTodos = todoRepository.findByParentId(id);
        List<Todo> incompleteSubTodos = subTodos.stream()
                .filter(sub -> sub.getStatus() != TodoStatus.COMPLETED &&
                              sub.getStatus() != TodoStatus.CANCELLED)
                .collect(Collectors.toList());

        if (!incompleteSubTodos.isEmpty()) {
            throw new BusinessRuleException(
                "存在未完成的子任务,不能删除父任务。未完成的子任务: " +
                incompleteSubTodos.size() + " 个"
            );
        }

        // 从父任务中移除引用
        if (todo.getParentId() != null) {
            Todo parent = todoRepository.findById(todo.getParentId()).orElse(null);
            if (parent != null) {
                parent.removeSubTodo(id);
                todoRepository.save(parent);
            }
        }

        todoRepository.deleteById(id);
    }

    /**
     * 转换状态
     */
    public Todo changeStatus(Long id, TodoStatus newStatus, Set<Long> completedSubTodoIds) {
        Todo todo = getTodoById(id);

        // 检查状态转换合法性
        if (!todo.canTransitionTo(newStatus)) {
            throw new BusinessRuleException(
                String.format("不允许从 %s 转换到 %s", todo.getStatus(), newStatus)
            );
        }

        // 如果要完成任务,检查子任务
        if (newStatus == TodoStatus.COMPLETED) {
            if (!todo.canComplete(completedSubTodoIds != null ? completedSubTodoIds : Collections.emptySet())) {
                throw new BusinessRuleException("所有子任务必须完成后才能完成父任务");
            }
        }

        todo.transitionTo(newStatus);
        return todoRepository.save(todo);
    }

    /**
     * 添加标签
     */
    public Todo addTag(Long id, String tag) {
        Todo todo = getTodoById(id);
        todo.addTag(tag);
        return todoRepository.save(todo);
    }

    /**
     * 移除标签
     */
    public Todo removeTag(Long id, String tag) {
        Todo todo = getTodoById(id);
        todo.removeTag(tag);
        return todoRepository.save(todo);
    }

    /**
     * 添加子任务
     */
    public Todo addSubTodo(Long parentId, Long subTodoId) {
        Todo parent = getTodoById(parentId);
        Todo subTodo = getTodoById(subTodoId);

        // 检查循环依赖
        if (isCircularDependency(parentId, subTodoId)) {
            throw new BusinessRuleException("不能添加会导致循环依赖的子任务");
        }

        // 检查子任务是否已有父任务
        if (subTodo.getParentId() != null) {
            throw new BusinessRuleException("该任务已有父任务");
        }

        parent.addSubTodo(subTodoId);
        subTodo.setParentId(parentId);

        todoRepository.save(parent);
        return todoRepository.save(subTodo);
    }

    /**
     * 检查是否存在循环依赖
     */
    private boolean isCircularDependency(Long parentId, Long subTodoId) {
        // 检查subTodo的子孙任务中是否包含parentId
        Set<Long> visited = new HashSet<>();
        Queue<Long> queue = new LinkedList<>();
        queue.add(subTodoId);

        while (!queue.isEmpty()) {
            Long currentId = queue.poll();
            if (currentId.equals(parentId)) {
                return true;
            }
            if (visited.contains(currentId)) {
                continue;
            }
            visited.add(currentId);

            Todo current = todoRepository.findById(currentId).orElse(null);
            if (current != null) {
                queue.addAll(current.getSubTodoIds());
            }
        }
        return false;
    }

    /**
     * 移除子任务
     */
    public Todo removeSubTodo(Long parentId, Long subTodoId) {
        Todo parent = getTodoById(parentId);
        Todo subTodo = getTodoById(subTodoId);

        parent.removeSubTodo(subTodoId);
        subTodo.setParentId(null);

        todoRepository.save(parent);
        return todoRepository.save(subTodo);
    }

    /**
     * 搜索Todo
     */
    public PageResult<Todo> searchTodos(SearchCriteria criteria) {
        return todoRepository.search(criteria);
    }

    /**
     * 获取所有过期任务并升级优先级
     */
    public List<Todo> upgradeExpiredTodosPriority() {
        List<Todo> todosToUpgrade = todoRepository.findTodosNeedingPriorityUpgrade();
        List<Todo> upgraded = new ArrayList<>();

        for (Todo todo : todosToUpgrade) {
            Priority oldPriority = todo.getPriority();
            todo.upgradePriority();
            if (!oldPriority.equals(todo.getPriority())) {
                upgraded.add(todoRepository.save(todo));
            }
        }

        return upgraded;
    }

    /**
     * 获取任务统计信息
     */
    public Map<String, Object> getStatistics() {
        List<Todo> allTodos = todoRepository.findAll();

        Map<String, Object> stats = new HashMap<>();
        stats.put("total", allTodos.size());
        stats.put("pending", allTodos.stream().filter(t -> t.getStatus() == TodoStatus.PENDING).count());
        stats.put("inProgress", allTodos.stream().filter(t -> t.getStatus() == TodoStatus.IN_PROGRESS).count());
        stats.put("completed", allTodos.stream().filter(t -> t.getStatus() == TodoStatus.COMPLETED).count());
        stats.put("cancelled", allTodos.stream().filter(t -> t.getStatus() == TodoStatus.CANCELLED).count());
        stats.put("expired", allTodos.stream().filter(Todo::isExpired).count());

        // 按优先级统计
        Map<String, Long> byPriority = allTodos.stream()
                .collect(Collectors.groupingBy(
                    t -> t.getPriority().name(),
                    Collectors.counting()
                ));
        stats.put("byPriority", byPriority);

        return stats;
    }

    /**
     * 批量删除已完成任务
     */
    public int deleteCompletedTodos() {
        List<Todo> completedTodos = todoRepository.findByStatus(TodoStatus.COMPLETED);
        int count = completedTodos.size();

        for (Todo todo : completedTodos) {
            try {
                deleteTodo(todo.getId());
            } catch (BusinessRuleException e) {
                // 跳过有未完成子任务的情况
                continue;
            }
        }

        return count;
    }

    /**
     * 克隆任务
     */
    public Todo cloneTodo(Long id) {
        Todo original = getTodoById(id);

        Todo cloned = new Todo(
            original.getTitle() + " (副本)",
            original.getDescription(),
            original.getPriority(),
            original.getDueDate() != null ? original.getDueDate().plusDays(7) : null
        );
        cloned.setReminderTime(original.getReminderTime());

        // 复制标签
        for (String tag : original.getTags()) {
            cloned.addTag(tag);
        }

        return todoRepository.save(cloned);
    }
}
