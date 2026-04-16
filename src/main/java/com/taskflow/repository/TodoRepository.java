package com.taskflow.repository;

import com.taskflow.exception.OptimisticLockException;
import com.taskflow.exception.ResourceNotFoundException;
import com.taskflow.model.*;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 任务数据访问层 - 内存实现
 * 
 * 特性:
 * 1. 支持并发访问(ConcurrentHashMap)
 * 2. 乐观锁版本号检查
 * 3. 级联删除逻辑
 * 4. 复杂条件搜索过滤
 */
@Repository
public class TodoRepository {
    private final ConcurrentHashMap<Long, Todo> store = new ConcurrentHashMap<>();

    /**
     * 保存Todo (创建或更新)
     */
    public Todo save(Todo todo) {
        Todo existing = store.get(todo.getId());
        
        if (existing == null) {
            // 新建
            todo.validate();
            store.put(todo.getId(), todo);
        } else {
            // 更新 - 检查乐观锁
            if (existing.getVersion() != todo.getVersion()) {
                throw new OptimisticLockException(todo.getId());
            }
            todo.validate();
            todo.setVersion(existing.getVersion() + 1);
            store.put(todo.getId(), todo);
        }
        return todo;
    }

    /**
     * 根据ID查找
     */
    public Optional<Todo> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    /**
     * 查找所有
     */
    public List<Todo> findAll() {
        return new ArrayList<>(store.values());
    }

    /**
     * 分页查询所有
     */
    public PageResult<Todo> findAll(int page, int size) {
        List<Todo> all = findAll();
        long total = all.size();
        int fromIndex = page * size;
        int toIndex = Math.min(fromIndex + size, all.size());

        List<Todo> content = fromIndex < all.size() ?
            all.subList(fromIndex, toIndex) : Collections.emptyList();

        return new PageResult<>(content, page, size, total);
    }

    /**
     * 根据状态查询
     */
    public List<Todo> findByStatus(TodoStatus status) {
        return store.values().stream()
                .filter(todo -> todo.getStatus() == status)
                .collect(Collectors.toList());
    }

    /**
     * 根据优先级查询
     */
    public List<Todo> findByPriority(Priority priority) {
        return store.values().stream()
                .filter(todo -> todo.getPriority() == priority)
                .collect(Collectors.toList());
    }

    /**
     * 查询过期任务
     */
    public List<Todo> findExpiredTodos() {
        return store.values().stream()
                .filter(Todo::isExpired)
                .collect(Collectors.toList());
    }

    /**
     * 查询需要升级优先级的任务
     */
    public List<Todo> findTodosNeedingPriorityUpgrade() {
        return store.values().stream()
                .filter(Todo::shouldUpgradePriority)
                .collect(Collectors.toList());
    }

    /**
     * 根据父任务ID查询子任务
     */
    public List<Todo> findByParentId(Long parentId) {
        return store.values().stream()
                .filter(todo -> parentId.equals(todo.getParentId()))
                .collect(Collectors.toList());
    }

    /**
     * 根据标签查询(包含所有指定标签)
     */
    public List<Todo> findByTags(Set<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return Collections.emptyList();
        }
        return store.values().stream()
                .filter(todo -> todo.getTags().containsAll(tags))
                .collect(Collectors.toList());
    }

    /**
     * 关键词搜索(标题或描述)
     */
    public List<Todo> searchByKeyword(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String lowerKeyword = keyword.toLowerCase();
        return store.values().stream()
                .filter(todo ->
                    (todo.getTitle() != null && todo.getTitle().toLowerCase().contains(lowerKeyword)) ||
                    (todo.getDescription() != null && todo.getDescription().toLowerCase().contains(lowerKeyword))
                )
                .collect(Collectors.toList());
    }

    /**
     * 复杂条件组合查询
     */
    public PageResult<Todo> search(SearchCriteria criteria) {
        criteria.validate();

        List<Todo> result = store.values().stream()
                .filter(todo -> matchesCriteria(todo, criteria))
                .sorted(Comparator.comparing(Todo::getCreatedAt).reversed())
                .collect(Collectors.toList());

        long total = result.size();
        int fromIndex = criteria.getPage() * criteria.getSize();
        int toIndex = Math.min(fromIndex + criteria.getSize(), result.size());

        List<Todo> content = fromIndex < result.size() ?
            result.subList(fromIndex, toIndex) : Collections.emptyList();

        return new PageResult<>(content, criteria.getPage(), criteria.getSize(), total);
    }

    private boolean matchesCriteria(Todo todo, SearchCriteria criteria) {
        // 关键词过滤
        if (criteria.getKeyword() != null && !criteria.getKeyword().trim().isEmpty()) {
            String keyword = criteria.getKeyword().toLowerCase();
            boolean titleMatch = todo.getTitle() != null && todo.getTitle().toLowerCase().contains(keyword);
            boolean descMatch = todo.getDescription() != null && todo.getDescription().toLowerCase().contains(keyword);
            if (!titleMatch && !descMatch) {
                return false;
            }
        }

        // 状态过滤
        if (criteria.getStatus() != null && todo.getStatus() != criteria.getStatus()) {
            return false;
        }

        // 优先级过滤
        if (criteria.getPriority() != null && todo.getPriority() != criteria.getPriority()) {
            return false;
        }

        // 截止日期范围过滤
        if (criteria.getDueDateFrom() != null &&
            (todo.getDueDate() == null || todo.getDueDate().isBefore(criteria.getDueDateFrom()))) {
            return false;
        }
        if (criteria.getDueDateTo() != null &&
            (todo.getDueDate() == null || todo.getDueDate().isAfter(criteria.getDueDateTo()))) {
            return false;
        }

        // 标签过滤
        if (criteria.getTags() != null && !criteria.getTags().isEmpty()) {
            if (!todo.getTags().containsAll(criteria.getTags())) {
                return false;
            }
        }

        // 子任务过滤
        if (criteria.getHasSubTodos() != null) {
            boolean hasSubTodos = !todo.getSubTodoIds().isEmpty();
            if (hasSubTodos != criteria.getHasSubTodos()) {
                return false;
            }
        }

        // 过期过滤
        if (criteria.getIsExpired() != null) {
            boolean isExpired = todo.isExpired();
            if (isExpired != criteria.getIsExpired()) {
                return false;
            }
        }

        // 父任务过滤
        if (criteria.getParentId() != null &&
            !criteria.getParentId().equals(todo.getParentId())) {
            return false;
        }

        return true;
    }

    /**
     * 删除Todo (级联删除子任务)
     */
    public void deleteById(Long id) {
        Todo todo = store.remove(id);
        if (todo == null) {
            throw new ResourceNotFoundException("Todo", id);
        }

        // 递归删除所有子任务
        List<Long> subTodoIds = new ArrayList<>(todo.getSubTodoIds());
        for (Long subTodoId : subTodoIds) {
            try {
                deleteById(subTodoId);
            } catch (ResourceNotFoundException e) {
                // 子任务可能已被删除,忽略
            }
        }
    }

    /**
     * 删除所有
     */
    public void deleteAll() {
        store.clear();
    }

    /**
     * 获取总数
     */
    public long count() {
        return store.size();
    }

    /**
     * 检查是否存在
     */
    public boolean existsById(Long id) {
        return store.containsKey(id);
    }

    /**
     * 批量保存
     */
    public List<Todo> saveAll(List<Todo> todos) {
        return todos.stream()
                .map(this::save)
                .collect(Collectors.toList());
    }

    /**
     * 根据ID列表批量查询
     */
    public List<Todo> findAllById(List<Long> ids) {
        return ids.stream()
                .map(store::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 重置ID生成器(用于测试)
     */
    public void resetIdGenerator() {
        // 注意:实际实现中需要反射修改Todo类的ID_GENERATOR
        // 这里简化处理,仅清空存储
        store.clear();
    }
}
