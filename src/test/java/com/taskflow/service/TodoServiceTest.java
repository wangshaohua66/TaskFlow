package com.taskflow.service;

import com.taskflow.exception.BusinessRuleException;
import com.taskflow.exception.OptimisticLockException;
import com.taskflow.exception.ResourceNotFoundException;
import com.taskflow.model.*;
import com.taskflow.repository.TodoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * TodoService 服务层单元测试
 *
 * 测试覆盖:
 * 1. 创建任务 (带父任务、带标签)
 * 2. 查询任务
 * 3. 更新任务 (完整更新、部分更新)
 * 4. 删除任务 (级联删除、未完成子任务检查)
 * 5. 状态转换
 * 6. 标签管理
 * 7. 子任务管理 (循环依赖检测)
 * 8. 搜索功能
 * 9. 优先级自动升级
 * 10. 统计功能
 * 11. 乐观锁冲突
 * 12. 克隆任务
 */
@ExtendWith(MockitoExtension.class)
class TodoServiceTest {

    @Mock
    private TodoRepository todoRepository;

    @InjectMocks
    private TodoService todoService;

    private Todo sampleTodo;
    private static final Long TODO_ID = 1L;
    private static final Long PARENT_ID = 100L;
    private static final Long SUB_TODO_ID = 200L;

    @BeforeEach
    void setUp() {
        sampleTodo = new Todo("测试任务", "测试描述", Priority.MEDIUM, LocalDateTime.now().plusDays(7));
        sampleTodo.setId(TODO_ID);
    }

    // ==================== 创建任务测试 ====================

    @Test
    @DisplayName("createTodo当参数有效时应创建并返回任务")
    void createTodo_WithValidParams_ShouldCreateAndReturnTodo() {
        // Arrange
        String title = "新任务";
        String description = "任务描述";
        Priority priority = Priority.HIGH;
        LocalDateTime dueDate = LocalDateTime.now().plusDays(7);
        LocalDateTime reminderTime = LocalDateTime.now().plusDays(6);
        Set<String> tags = new HashSet<>(Arrays.asList("tag1", "tag2"));

        when(todoRepository.save(any(Todo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Todo result = todoService.createTodo(title, description, priority, dueDate, reminderTime, tags, null);

        // Assert
        assertNotNull(result);
        assertEquals(title, result.getTitle());
        assertEquals(description, result.getDescription());
        assertEquals(priority, result.getPriority());
        assertEquals(dueDate, result.getDueDate());
        assertEquals(reminderTime, result.getReminderTime());
        assertEquals(2, result.getTags().size());
        verify(todoRepository, times(1)).save(any(Todo.class));
    }

    @Test
    @DisplayName("createTodo当标题无效时应抛出IllegalArgumentException")
    void createTodo_WithInvalidTitle_ShouldThrowIllegalArgumentException() {
        // Arrange
        String invalidTitle = "";

        // Act & Assert
        assertThrows(IllegalArgumentException.class,
            () -> todoService.createTodo(invalidTitle, "描述", Priority.MEDIUM, null, null, null, null));
        verify(todoRepository, never()).save(any(Todo.class));
    }

    @Test
    @DisplayName("createTodo当带父任务时应建立关联")
    void createTodo_WithParentId_ShouldEstablishParentChildRelationship() {
        // Arrange
        Todo parentTodo = new Todo("父任务", null, Priority.MEDIUM, null);
        parentTodo.setId(PARENT_ID);
        parentTodo.setStatus(TodoStatus.PENDING);

        when(todoRepository.findById(PARENT_ID)).thenReturn(Optional.of(parentTodo));
        when(todoRepository.save(any(Todo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Todo result = todoService.createTodo("子任务", "描述", Priority.MEDIUM, null, null, null, PARENT_ID);

        // Assert
        assertEquals(PARENT_ID, result.getParentId());
        verify(todoRepository, times(2)).save(any(Todo.class)); // 保存父任务和子任务
    }

    @Test
    @DisplayName("createTodo当父任务不存在时应抛出ResourceNotFoundException")
    void createTodo_WithNonExistingParent_ShouldThrowResourceNotFoundException() {
        // Arrange
        when(todoRepository.findById(PARENT_ID)).thenReturn(Optional.empty());

        // Act & Assert
        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class,
            () -> todoService.createTodo("子任务", "描述", Priority.MEDIUM, null, null, null, PARENT_ID));
        assertTrue(exception.getMessage().contains("Parent Todo"));
        assertTrue(exception.getMessage().contains(PARENT_ID.toString()));
    }

    @Test
    @DisplayName("createTodo当父任务已完成时应抛出BusinessRuleException")
    void createTodo_WithCompletedParent_ShouldThrowBusinessRuleException() {
        // Arrange
        Todo parentTodo = new Todo("父任务", null, Priority.MEDIUM, null);
        parentTodo.setId(PARENT_ID);
        parentTodo.setStatus(TodoStatus.COMPLETED);

        when(todoRepository.findById(PARENT_ID)).thenReturn(Optional.of(parentTodo));

        // Act & Assert
        BusinessRuleException exception = assertThrows(BusinessRuleException.class,
            () -> todoService.createTodo("子任务", "描述", Priority.MEDIUM, null, null, null, PARENT_ID));
        assertEquals("已完成的任务不能添加子任务", exception.getMessage());
    }

    // ==================== 查询任务测试 ====================

    @Test
    @DisplayName("getTodoById当任务存在时应返回任务")
    void getTodoById_WhenTodoExists_ShouldReturnTodo() {
        // Arrange
        when(todoRepository.findById(TODO_ID)).thenReturn(Optional.of(sampleTodo));

        // Act
        Todo result = todoService.getTodoById(TODO_ID);

        // Assert
        assertNotNull(result);
        assertEquals(TODO_ID, result.getId());
        assertEquals("测试任务", result.getTitle());
    }

    @Test
    @DisplayName("getTodoById当任务不存在时应抛出ResourceNotFoundException")
    void getTodoById_WhenTodoNotExists_ShouldThrowResourceNotFoundException() {
        // Arrange
        Long nonExistingId = 999L;
        when(todoRepository.findById(nonExistingId)).thenReturn(Optional.empty());

        // Act & Assert
        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class,
            () -> todoService.getTodoById(nonExistingId));
        assertTrue(exception.getMessage().contains("Todo"));
        assertTrue(exception.getMessage().contains(nonExistingId.toString()));
    }

    // ==================== 更新任务测试 ====================

    @Test
    @DisplayName("updateTodo当参数有效时应更新并返回任务")
    void updateTodo_WithValidParams_ShouldUpdateAndReturnTodo() {
        // Arrange
        String newTitle = "更新后的标题";
        String newDescription = "更新后的描述";
        Priority newPriority = Priority.HIGH;
        LocalDateTime newDueDate = LocalDateTime.now().plusDays(14);
        LocalDateTime newReminderTime = LocalDateTime.now().plusDays(13);
        int currentVersion = sampleTodo.getVersion();

        when(todoRepository.findById(TODO_ID)).thenReturn(Optional.of(sampleTodo));
        when(todoRepository.save(any(Todo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Todo result = todoService.updateTodo(TODO_ID, newTitle, newDescription, newPriority,
            newDueDate, newReminderTime, currentVersion);

        // Assert
        assertEquals(newTitle, result.getTitle());
        assertEquals(newDescription, result.getDescription());
        assertEquals(newPriority, result.getPriority());
        assertEquals(newDueDate, result.getDueDate());
        assertEquals(newReminderTime, result.getReminderTime());
    }

    @Test
    @DisplayName("updateTodo当版本号不匹配时应抛出OptimisticLockException")
    void updateTodo_WithVersionMismatch_ShouldThrowOptimisticLockException() {
        // Arrange
        int wrongVersion = sampleTodo.getVersion() + 1;
        when(todoRepository.findById(TODO_ID)).thenReturn(Optional.of(sampleTodo));

        // Act & Assert
        OptimisticLockException exception = assertThrows(OptimisticLockException.class,
            () -> todoService.updateTodo(TODO_ID, "新标题", null, null, null, null, wrongVersion));
        assertTrue(exception.getMessage().contains(TODO_ID.toString()));
    }

    @Test
    @DisplayName("updateTodo当任务已完成时应抛出BusinessRuleException")
    void updateTodo_WhenTodoCompleted_ShouldThrowBusinessRuleException() {
        // Arrange
        sampleTodo.setStatus(TodoStatus.COMPLETED);
        when(todoRepository.findById(TODO_ID)).thenReturn(Optional.of(sampleTodo));

        // Act & Assert
        BusinessRuleException exception = assertThrows(BusinessRuleException.class,
            () -> todoService.updateTodo(TODO_ID, "新标题", null, null, null, null, sampleTodo.getVersion()));
        assertEquals("已完成的任务不能修改", exception.getMessage());
    }

    @Test
    @DisplayName("updateTodo当任务不存在时应抛出ResourceNotFoundException")
    void updateTodo_WhenTodoNotExists_ShouldThrowResourceNotFoundException() {
        // Arrange
        Long nonExistingId = 999L;
        when(todoRepository.findById(nonExistingId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class,
            () -> todoService.updateTodo(nonExistingId, "标题", null, null, null, null, 0));
    }

    // ==================== 部分更新测试 ====================

    @Test
    @DisplayName("patchTodo当参数有效时应部分更新并返回任务")
    void patchTodo_WithValidParams_ShouldPartiallyUpdateAndReturnTodo() {
        // Arrange
        Map<String, Object> updates = new HashMap<>();
        updates.put("title", "部分更新的标题");
        updates.put("priority", "CRITICAL");

        int currentVersion = sampleTodo.getVersion();

        when(todoRepository.findById(TODO_ID)).thenReturn(Optional.of(sampleTodo));
        when(todoRepository.save(any(Todo.class))).thenAnswer(invocation -> {
            Todo saved = invocation.getArgument(0);
            // 创建一个新的 Todo 对象来模拟保存后的结果
            Todo result = new Todo();
            result.setId(saved.getId());
            result.setTitle(saved.getTitle());
            result.setDescription(saved.getDescription());
            result.setPriority(saved.getPriority());
            result.setDueDate(saved.getDueDate());
            result.setStatus(saved.getStatus());
            result.setVersion(saved.getVersion());
            return result;
        });

        // Act
        Todo result = todoService.patchTodo(TODO_ID, updates, currentVersion);

        // Assert
        assertEquals("部分更新的标题", result.getTitle());
        assertEquals(Priority.CRITICAL, result.getPriority());
        verify(todoRepository).save(any(Todo.class));
    }

    @Test
    @DisplayName("patchTodo当版本号不匹配时应抛出OptimisticLockException")
    void patchTodo_WithVersionMismatch_ShouldThrowOptimisticLockException() {
        // Arrange
        Map<String, Object> updates = new HashMap<>();
        updates.put("title", "新标题");
        int wrongVersion = sampleTodo.getVersion() + 1;

        when(todoRepository.findById(TODO_ID)).thenReturn(Optional.of(sampleTodo));

        // Act & Assert
        assertThrows(OptimisticLockException.class,
            () -> todoService.patchTodo(TODO_ID, updates, wrongVersion));
    }

    @Test
    @DisplayName("patchTodo当任务已完成时应抛出BusinessRuleException")
    void patchTodo_WhenTodoCompleted_ShouldThrowBusinessRuleException() {
        // Arrange
        sampleTodo.setStatus(TodoStatus.COMPLETED);
        Map<String, Object> updates = new HashMap<>();
        updates.put("title", "新标题");

        when(todoRepository.findById(TODO_ID)).thenReturn(Optional.of(sampleTodo));

        // Act & Assert
        BusinessRuleException exception = assertThrows(BusinessRuleException.class,
            () -> todoService.patchTodo(TODO_ID, updates, sampleTodo.getVersion()));
        assertEquals("已完成的任务不能修改", exception.getMessage());
    }

    // ==================== 删除任务测试 ====================

    @Test
    @DisplayName("deleteTodo当任务存在且无未完成子任务时应删除成功")
    void deleteTodo_WhenNoIncompleteSubTodos_ShouldDeleteSuccessfully() {
        // Arrange
        Todo completedSubTodo = new Todo("已完成子任务", null, Priority.MEDIUM, null);
        completedSubTodo.setId(SUB_TODO_ID);
        completedSubTodo.setStatus(TodoStatus.COMPLETED);
        sampleTodo.addSubTodo(SUB_TODO_ID);

        when(todoRepository.findById(TODO_ID)).thenReturn(Optional.of(sampleTodo));
        when(todoRepository.findByParentId(TODO_ID)).thenReturn(Collections.singletonList(completedSubTodo));

        // Act
        assertDoesNotThrow(() -> todoService.deleteTodo(TODO_ID));

        // Assert
        verify(todoRepository).deleteById(TODO_ID);
    }

    @Test
    @DisplayName("deleteTodo当存在未完成子任务时应抛出BusinessRuleException")
    void deleteTodo_WithIncompleteSubTodos_ShouldThrowBusinessRuleException() {
        // Arrange
        Todo incompleteSubTodo = new Todo("未完成子任务", null, Priority.MEDIUM, null);
        incompleteSubTodo.setId(SUB_TODO_ID);
        incompleteSubTodo.setStatus(TodoStatus.IN_PROGRESS);
        sampleTodo.addSubTodo(SUB_TODO_ID);

        when(todoRepository.findById(TODO_ID)).thenReturn(Optional.of(sampleTodo));
        when(todoRepository.findByParentId(TODO_ID)).thenReturn(Collections.singletonList(incompleteSubTodo));

        // Act & Assert
        BusinessRuleException exception = assertThrows(BusinessRuleException.class,
            () -> todoService.deleteTodo(TODO_ID));
        assertTrue(exception.getMessage().contains("存在未完成的子任务"));
        assertTrue(exception.getMessage().contains("1"));
    }

    @Test
    @DisplayName("deleteTodo当任务不存在时应抛出ResourceNotFoundException")
    void deleteTodo_WhenTodoNotExists_ShouldThrowResourceNotFoundException() {
        // Arrange
        Long nonExistingId = 999L;
        when(todoRepository.findById(nonExistingId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> todoService.deleteTodo(nonExistingId));
    }

    @Test
    @DisplayName("deleteTodo当任务有父任务时应从父任务中移除引用")
    void deleteTodo_WithParent_ShouldRemoveReferenceFromParent() {
        // Arrange
        Todo parentTodo = new Todo("父任务", null, Priority.MEDIUM, null);
        parentTodo.setId(PARENT_ID);
        parentTodo.addSubTodo(TODO_ID);
        sampleTodo.setParentId(PARENT_ID);

        when(todoRepository.findById(TODO_ID)).thenReturn(Optional.of(sampleTodo));
        when(todoRepository.findByParentId(TODO_ID)).thenReturn(Collections.emptyList());
        when(todoRepository.findById(PARENT_ID)).thenReturn(Optional.of(parentTodo));
        when(todoRepository.save(any(Todo.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(todoRepository).deleteById(TODO_ID);

        // Act
        todoService.deleteTodo(TODO_ID);

        // Assert
        verify(todoRepository, times(1)).save(any(Todo.class)); // 只保存父任务
        verify(todoRepository).deleteById(TODO_ID);
    }

    // ==================== 状态转换测试 ====================

    @Test
    @DisplayName("changeStatus当转换合法时应更新状态")
    void changeStatus_WithValidTransition_ShouldUpdateStatus() {
        // Arrange
        sampleTodo.setStatus(TodoStatus.PENDING);
        when(todoRepository.findById(TODO_ID)).thenReturn(Optional.of(sampleTodo));
        when(todoRepository.save(any(Todo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Todo result = todoService.changeStatus(TODO_ID, TodoStatus.IN_PROGRESS, Collections.emptySet());

        // Assert
        assertEquals(TodoStatus.IN_PROGRESS, result.getStatus());
    }

    @Test
    @DisplayName("changeStatus当转换非法时应抛出BusinessRuleException")
    void changeStatus_WithInvalidTransition_ShouldThrowBusinessRuleException() {
        // Arrange
        sampleTodo.setStatus(TodoStatus.COMPLETED);
        when(todoRepository.findById(TODO_ID)).thenReturn(Optional.of(sampleTodo));

        // Act & Assert
        BusinessRuleException exception = assertThrows(BusinessRuleException.class,
            () -> todoService.changeStatus(TODO_ID, TodoStatus.PENDING, Collections.emptySet()));
        assertTrue(exception.getMessage().contains("不允许从"));
    }

    @Test
    @DisplayName("changeStatus当完成任务但子任务未完成时应抛出BusinessRuleException")
    void changeStatus_ToCompletedWithIncompleteSubTodos_ShouldThrowBusinessRuleException() {
        // Arrange
        sampleTodo.setStatus(TodoStatus.IN_PROGRESS);
        sampleTodo.addSubTodo(SUB_TODO_ID);
        when(todoRepository.findById(TODO_ID)).thenReturn(Optional.of(sampleTodo));

        // Act & Assert
        BusinessRuleException exception = assertThrows(BusinessRuleException.class,
            () -> todoService.changeStatus(TODO_ID, TodoStatus.COMPLETED, Collections.emptySet()));
        assertEquals("所有子任务必须完成后才能完成父任务", exception.getMessage());
    }

    @Test
    @DisplayName("changeStatus当完成任务且所有子任务已完成时应成功")
    void changeStatus_ToCompletedWithAllSubTodosCompleted_ShouldSucceed() {
        // Arrange
        sampleTodo.setStatus(TodoStatus.IN_PROGRESS);
        sampleTodo.addSubTodo(SUB_TODO_ID);
        when(todoRepository.findById(TODO_ID)).thenReturn(Optional.of(sampleTodo));
        when(todoRepository.save(any(Todo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Set<Long> completedSubTodos = Collections.singleton(SUB_TODO_ID);

        // Act
        Todo result = todoService.changeStatus(TODO_ID, TodoStatus.COMPLETED, completedSubTodos);

        // Assert
        assertEquals(TodoStatus.COMPLETED, result.getStatus());
    }

    @Test
    @DisplayName("changeStatus当任务不存在时应抛出ResourceNotFoundException")
    void changeStatus_WhenTodoNotExists_ShouldThrowResourceNotFoundException() {
        // Arrange
        Long nonExistingId = 999L;
        when(todoRepository.findById(nonExistingId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class,
            () -> todoService.changeStatus(nonExistingId, TodoStatus.IN_PROGRESS, Collections.emptySet()));
    }

    // ==================== 标签管理测试 ====================

    @Test
    @DisplayName("addTag当参数有效时应添加标签并保存")
    void addTag_WithValidParams_ShouldAddTagAndSave() {
        // Arrange
        when(todoRepository.findById(TODO_ID)).thenReturn(Optional.of(sampleTodo));
        when(todoRepository.save(any(Todo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Todo result = todoService.addTag(TODO_ID, "newtag");

        // Assert
        assertTrue(result.getTags().contains("newtag"));
        verify(todoRepository).save(any(Todo.class));
    }

    @Test
    @DisplayName("addTag当任务不存在时应抛出ResourceNotFoundException")
    void addTag_WhenTodoNotExists_ShouldThrowResourceNotFoundException() {
        // Arrange
        when(todoRepository.findById(TODO_ID)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> todoService.addTag(TODO_ID, "tag"));
    }

    @Test
    @DisplayName("removeTag当标签存在时应移除标签并保存")
    void removeTag_WithExistingTag_ShouldRemoveTagAndSave() {
        // Arrange
        sampleTodo.addTag("existingtag");
        when(todoRepository.findById(TODO_ID)).thenReturn(Optional.of(sampleTodo));
        when(todoRepository.save(any(Todo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Todo result = todoService.removeTag(TODO_ID, "existingtag");

        // Assert
        assertFalse(result.getTags().contains("existingtag"));
        verify(todoRepository).save(any(Todo.class));
    }

    @Test
    @DisplayName("removeTag当任务不存在时应抛出ResourceNotFoundException")
    void removeTag_WhenTodoNotExists_ShouldThrowResourceNotFoundException() {
        // Arrange
        when(todoRepository.findById(TODO_ID)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> todoService.removeTag(TODO_ID, "tag"));
    }

    // ==================== 子任务管理测试 ====================

    @Test
    @DisplayName("addSubTodo当参数有效时应建立父子关系")
    void addSubTodo_WithValidParams_ShouldEstablishRelationship() {
        // Arrange
        Todo subTodo = new Todo("子任务", null, Priority.MEDIUM, null);
        subTodo.setId(SUB_TODO_ID);

        when(todoRepository.findById(TODO_ID)).thenReturn(Optional.of(sampleTodo));
        when(todoRepository.findById(SUB_TODO_ID)).thenReturn(Optional.of(subTodo));
        when(todoRepository.save(any(Todo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Todo result = todoService.addSubTodo(TODO_ID, SUB_TODO_ID);

        // Assert
        assertTrue(sampleTodo.getSubTodoIds().contains(SUB_TODO_ID));
        assertEquals(TODO_ID, subTodo.getParentId());
        verify(todoRepository, times(2)).save(any(Todo.class));
    }

    @Test
    @DisplayName("addSubTodo当父任务不存在时应抛出ResourceNotFoundException")
    void addSubTodo_WhenParentNotExists_ShouldThrowResourceNotFoundException() {
        // Arrange
        when(todoRepository.findById(TODO_ID)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> todoService.addSubTodo(TODO_ID, SUB_TODO_ID));
    }

    @Test
    @DisplayName("addSubTodo当子任务不存在时应抛出ResourceNotFoundException")
    void addSubTodo_WhenSubTodoNotExists_ShouldThrowResourceNotFoundException() {
        // Arrange
        when(todoRepository.findById(TODO_ID)).thenReturn(Optional.of(sampleTodo));
        when(todoRepository.findById(SUB_TODO_ID)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> todoService.addSubTodo(TODO_ID, SUB_TODO_ID));
    }

    @Test
    @DisplayName("addSubTodo当子任务已有父任务时应抛出BusinessRuleException")
    void addSubTodo_WhenSubTodoHasParent_ShouldThrowBusinessRuleException() {
        // Arrange
        Todo subTodo = new Todo("子任务", null, Priority.MEDIUM, null);
        subTodo.setId(SUB_TODO_ID);
        subTodo.setParentId(999L); // 已有父任务

        when(todoRepository.findById(TODO_ID)).thenReturn(Optional.of(sampleTodo));
        when(todoRepository.findById(SUB_TODO_ID)).thenReturn(Optional.of(subTodo));

        // Act & Assert
        BusinessRuleException exception = assertThrows(BusinessRuleException.class,
            () -> todoService.addSubTodo(TODO_ID, SUB_TODO_ID));
        assertEquals("该任务已有父任务", exception.getMessage());
    }

    @Test
    @DisplayName("addSubTodo当会导致循环依赖时应抛出BusinessRuleException")
    void addSubTodo_WhenCircularDependency_ShouldThrowBusinessRuleException() {
        // Arrange: A -> B -> C，尝试让 C -> A 形成循环
        Todo taskA = new Todo("任务A", null, Priority.MEDIUM, null);
        taskA.setId(1L);
        Todo taskB = new Todo("任务B", null, Priority.MEDIUM, null);
        taskB.setId(2L);
        taskB.setParentId(1L);
        taskA.addSubTodo(2L);
        Todo taskC = new Todo("任务C", null, Priority.MEDIUM, null);
        taskC.setId(3L);
        taskC.setParentId(2L);  // C的父任务是B
        taskB.addSubTodo(3L);

        // 现在尝试让 C 成为 A 的子任务，形成 A->B->C->A 的循环
        // 注意：在检查循环依赖之前，会先检查子任务是否已有父任务
        // 所以我们需要先把C的父任务设为null，或者测试另一种场景
        // 这里我们测试另一种场景：A -> B, C 是独立的，尝试让 C -> B -> A 然后 A -> C

        // 重新设置：A -> B，C是独立的，尝试让 B -> C -> A 形成循环
        Todo taskA2 = new Todo("任务A2", null, Priority.MEDIUM, null);
        taskA2.setId(10L);
        Todo taskB2 = new Todo("任务B2", null, Priority.MEDIUM, null);
        taskB2.setId(20L);
        taskB2.setParentId(10L);  // B的父任务是A
        taskA2.addSubTodo(20L);
        Todo taskC2 = new Todo("任务C2", null, Priority.MEDIUM, null);
        taskC2.setId(30L);
        taskC2.setParentId(20L);  // C的父任务是B
        taskB2.addSubTodo(30L);

        // 尝试让 A 成为 C 的子任务，形成 A->B->C->A 的循环
        when(todoRepository.findById(30L)).thenReturn(Optional.of(taskC2));
        when(todoRepository.findById(10L)).thenReturn(Optional.of(taskA2));
        when(todoRepository.findById(20L)).thenReturn(Optional.of(taskB2));

        // Act & Assert
        BusinessRuleException exception = assertThrows(BusinessRuleException.class,
            () -> todoService.addSubTodo(30L, 10L));
        assertEquals("不能添加会导致循环依赖的子任务", exception.getMessage());
    }

    @Test
    @DisplayName("removeSubTodo当参数有效时应解除父子关系")
    void removeSubTodo_WithValidParams_ShouldRemoveRelationship() {
        // Arrange
        Todo subTodo = new Todo("子任务", null, Priority.MEDIUM, null);
        subTodo.setId(SUB_TODO_ID);
        subTodo.setParentId(TODO_ID);
        sampleTodo.addSubTodo(SUB_TODO_ID);

        when(todoRepository.findById(TODO_ID)).thenReturn(Optional.of(sampleTodo));
        when(todoRepository.findById(SUB_TODO_ID)).thenReturn(Optional.of(subTodo));
        when(todoRepository.save(any(Todo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Todo result = todoService.removeSubTodo(TODO_ID, SUB_TODO_ID);

        // Assert
        assertFalse(sampleTodo.getSubTodoIds().contains(SUB_TODO_ID));
        assertNull(subTodo.getParentId());
        verify(todoRepository, times(2)).save(any(Todo.class));
    }

    @Test
    @DisplayName("removeSubTodo当父任务不存在时应抛出ResourceNotFoundException")
    void removeSubTodo_WhenParentNotExists_ShouldThrowResourceNotFoundException() {
        // Arrange
        when(todoRepository.findById(TODO_ID)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> todoService.removeSubTodo(TODO_ID, SUB_TODO_ID));
    }

    @Test
    @DisplayName("removeSubTodo当子任务不存在时应抛出ResourceNotFoundException")
    void removeSubTodo_WhenSubTodoNotExists_ShouldThrowResourceNotFoundException() {
        // Arrange
        when(todoRepository.findById(TODO_ID)).thenReturn(Optional.of(sampleTodo));
        when(todoRepository.findById(SUB_TODO_ID)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> todoService.removeSubTodo(TODO_ID, SUB_TODO_ID));
    }

    // ==================== 搜索功能测试 ====================

    @Test
    @DisplayName("searchTodos应返回分页结果")
    void searchTodos_ShouldReturnPageResult() {
        // Arrange
        SearchCriteria criteria = new SearchCriteria();
        criteria.setPage(0);
        criteria.setSize(10);

        PageResult<Todo> expectedResult = new PageResult<>(
            Collections.singletonList(sampleTodo), 0, 10, 1);
        when(todoRepository.search(criteria)).thenReturn(expectedResult);

        // Act
        PageResult<Todo> result = todoService.searchTodos(criteria);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals(0, result.getPage());
        assertEquals(10, result.getSize());
        assertEquals(1, result.getTotalElements());
    }

    // ==================== 优先级升级测试 ====================

    @Test
    @DisplayName("upgradeExpiredTodosPriority应升级过期任务的优先级")
    void upgradeExpiredTodosPriority_ShouldUpgradeExpiredTodos() {
        // Arrange
        Todo expiredTodo = new Todo("过期任务", null, Priority.MEDIUM, null);
        expiredTodo.setId(2L);
        expiredTodo.setDueDate(LocalDateTime.now().minusHours(25));
        expiredTodo.setStatus(TodoStatus.IN_PROGRESS);

        when(todoRepository.findTodosNeedingPriorityUpgrade()).thenReturn(Collections.singletonList(expiredTodo));
        when(todoRepository.save(any(Todo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        List<Todo> result = todoService.upgradeExpiredTodosPriority();

        // Assert
        assertEquals(1, result.size());
        assertEquals(Priority.HIGH, result.get(0).getPriority());
    }

    @Test
    @DisplayName("upgradeExpiredTodosPriority当没有需要升级的任务时应返回空列表")
    void upgradeExpiredTodosPriority_WhenNoTodosNeedUpgrade_ShouldReturnEmptyList() {
        // Arrange
        when(todoRepository.findTodosNeedingPriorityUpgrade()).thenReturn(Collections.emptyList());

        // Act
        List<Todo> result = todoService.upgradeExpiredTodosPriority();

        // Assert
        assertTrue(result.isEmpty());
    }

    // ==================== 统计功能测试 ====================

    @Test
    @DisplayName("getStatistics应返回正确的统计数据")
    void getStatistics_ShouldReturnCorrectStatistics() {
        // Arrange
        Todo todo1 = new Todo("任务1", null, Priority.MEDIUM, null);
        todo1.setStatus(TodoStatus.PENDING);
        Todo todo2 = new Todo("任务2", null, Priority.HIGH, null);
        todo2.setStatus(TodoStatus.IN_PROGRESS);
        Todo todo3 = new Todo("任务3", null, Priority.LOW, null);
        todo3.setStatus(TodoStatus.COMPLETED);
        todo3.setDueDate(LocalDateTime.now().minusDays(1)); // 已过期

        when(todoRepository.findAll()).thenReturn(Arrays.asList(todo1, todo2, todo3));

        // Act
        Map<String, Object> stats = todoService.getStatistics();

        // Assert
        assertEquals(3, ((Number) stats.get("total")).longValue());
        assertEquals(1, ((Number) stats.get("pending")).longValue());
        assertEquals(1, ((Number) stats.get("inProgress")).longValue());
        assertEquals(1, ((Number) stats.get("completed")).longValue());
        assertEquals(1, ((Number) stats.get("expired")).longValue());

        @SuppressWarnings("unchecked")
        Map<String, Long> byPriority = (Map<String, Long>) stats.get("byPriority");
        assertNotNull(byPriority);
        assertEquals(1L, byPriority.get("MEDIUM"));
        assertEquals(1L, byPriority.get("HIGH"));
        assertEquals(1L, byPriority.get("LOW"));
    }

    @Test
    @DisplayName("getStatistics当没有任务时应返回零值")
    void getStatistics_WhenNoTodos_ShouldReturnZeroValues() {
        // Arrange
        when(todoRepository.findAll()).thenReturn(Collections.emptyList());

        // Act
        Map<String, Object> stats = todoService.getStatistics();

        // Assert
        assertEquals(0, ((Number) stats.get("total")).longValue());
        assertEquals(0, ((Number) stats.get("pending")).longValue());
        assertEquals(0, ((Number) stats.get("inProgress")).longValue());
        assertEquals(0, ((Number) stats.get("completed")).longValue());
        assertEquals(0, ((Number) stats.get("expired")).longValue());
    }

    // ==================== 批量删除测试 ====================

    @Test
    @DisplayName("deleteCompletedTodos应删除所有已完成的任务")
    void deleteCompletedTodos_ShouldDeleteAllCompletedTodos() {
        // Arrange
        Todo completedTodo = new Todo("已完成", null, Priority.MEDIUM, null);
        completedTodo.setId(2L);
        completedTodo.setStatus(TodoStatus.COMPLETED);

        when(todoRepository.findByStatus(TodoStatus.COMPLETED)).thenReturn(Collections.singletonList(completedTodo));
        when(todoRepository.findById(2L)).thenReturn(Optional.of(completedTodo));
        when(todoRepository.findByParentId(2L)).thenReturn(Collections.emptyList());

        // Act
        int count = todoService.deleteCompletedTodos();

        // Assert
        assertEquals(1, count);
        verify(todoRepository).deleteById(2L);
    }

    @Test
    @DisplayName("deleteCompletedTodos当任务有未完成子任务时应跳过")
    void deleteCompletedTodos_WithIncompleteSubTodos_ShouldSkip() {
        // Arrange
        Todo completedTodo = new Todo("已完成", null, Priority.MEDIUM, null);
        completedTodo.setId(2L);
        completedTodo.setStatus(TodoStatus.COMPLETED);
        completedTodo.addSubTodo(3L);

        Todo incompleteSubTodo = new Todo("未完成子任务", null, Priority.MEDIUM, null);
        incompleteSubTodo.setId(3L);
        incompleteSubTodo.setStatus(TodoStatus.IN_PROGRESS);

        when(todoRepository.findByStatus(TodoStatus.COMPLETED)).thenReturn(Collections.singletonList(completedTodo));
        when(todoRepository.findById(2L)).thenReturn(Optional.of(completedTodo));
        when(todoRepository.findByParentId(2L)).thenReturn(Collections.singletonList(incompleteSubTodo));

        // Act
        int count = todoService.deleteCompletedTodos();

        // Assert
        assertEquals(1, count); // 返回总数，但可能实际删除0个
        verify(todoRepository, never()).deleteById(2L);
    }

    // ==================== 克隆任务测试 ====================

    @Test
    @DisplayName("cloneTodo应创建任务的副本")
    void cloneTodo_ShouldCreateCopyOfTodo() {
        // Arrange
        sampleTodo.setDueDate(LocalDateTime.now().plusDays(7));
        sampleTodo.addTag("tag1");
        sampleTodo.addTag("tag2");

        when(todoRepository.findById(TODO_ID)).thenReturn(Optional.of(sampleTodo));
        when(todoRepository.save(any(Todo.class))).thenAnswer(invocation -> {
            Todo saved = invocation.getArgument(0);
            saved.setId(999L);
            return saved;
        });

        // Act
        Todo cloned = todoService.cloneTodo(TODO_ID);

        // Assert
        assertNotNull(cloned);
        assertEquals("测试任务 (副本)", cloned.getTitle());
        assertEquals("测试描述", cloned.getDescription());
        assertEquals(Priority.MEDIUM, cloned.getPriority());
        assertNotNull(cloned.getDueDate());
        assertEquals(2, cloned.getTags().size());
        assertTrue(cloned.getTags().contains("tag1"));
        assertTrue(cloned.getTags().contains("tag2"));
        assertNotEquals(TODO_ID, cloned.getId());
    }

    @Test
    @DisplayName("cloneTodo当原任务没有截止日期时副本也不应有截止日期")
    void cloneTodo_WhenOriginalHasNoDueDate_ShouldNotHaveDueDate() {
        // Arrange
        Todo original = new Todo("原任务", "描述", Priority.LOW, null);
        original.setId(TODO_ID);

        when(todoRepository.findById(TODO_ID)).thenReturn(Optional.of(original));
        when(todoRepository.save(any(Todo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Todo cloned = todoService.cloneTodo(TODO_ID);

        // Assert
        assertNull(cloned.getDueDate());
    }

    @Test
    @DisplayName("cloneTodo当任务不存在时应抛出ResourceNotFoundException")
    void cloneTodo_WhenTodoNotExists_ShouldThrowResourceNotFoundException() {
        // Arrange
        when(todoRepository.findById(TODO_ID)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> todoService.cloneTodo(TODO_ID));
    }

    // ==================== 边界值和异常测试 ====================

    @ParameterizedTest
    @CsvSource({
        "PENDING, IN_PROGRESS, true",
        "IN_PROGRESS, COMPLETED, true",
        "CANCELLED, PENDING, true"
    })
    @DisplayName("状态转换边界值测试")
    void changeStatus_BoundaryValues_ShouldWorkCorrectly(String fromStatus, String toStatus, boolean shouldSucceed) {
        // Arrange
        sampleTodo.setStatus(TodoStatus.valueOf(fromStatus));
        when(todoRepository.findById(TODO_ID)).thenReturn(Optional.of(sampleTodo));
        if (shouldSucceed) {
            when(todoRepository.save(any(Todo.class))).thenAnswer(invocation -> invocation.getArgument(0));
        }

        // Act & Assert
        if (shouldSucceed) {
            assertDoesNotThrow(() -> todoService.changeStatus(TODO_ID, TodoStatus.valueOf(toStatus), Collections.emptySet()));
        } else {
            assertThrows(BusinessRuleException.class,
                () -> todoService.changeStatus(TODO_ID, TodoStatus.valueOf(toStatus), Collections.emptySet()));
        }
    }

    @Test
    @DisplayName("复杂循环依赖检测应正确工作")
    void addSubTodo_ComplexCircularDependency_ShouldBeDetected() {
        // Arrange: 创建 A -> B -> C -> D 的链，然后尝试让 A 成为 D 的子任务形成循环
        Todo taskA = new Todo("A", null, Priority.MEDIUM, null);
        taskA.setId(100L);
        Todo taskB = new Todo("B", null, Priority.MEDIUM, null);
        taskB.setId(200L);
        taskB.setParentId(100L);
        taskA.addSubTodo(200L);
        Todo taskC = new Todo("C", null, Priority.MEDIUM, null);
        taskC.setId(300L);
        taskC.setParentId(200L);
        taskB.addSubTodo(300L);
        Todo taskD = new Todo("D", null, Priority.MEDIUM, null);
        taskD.setId(400L);
        taskD.setParentId(300L);
        taskC.addSubTodo(400L);

        // 尝试让 A 成为 D 的子任务，形成 A->B->C->D->A 的循环
        when(todoRepository.findById(400L)).thenReturn(Optional.of(taskD));
        when(todoRepository.findById(100L)).thenReturn(Optional.of(taskA));
        when(todoRepository.findById(200L)).thenReturn(Optional.of(taskB));
        when(todoRepository.findById(300L)).thenReturn(Optional.of(taskC));

        // Act & Assert
        BusinessRuleException exception = assertThrows(BusinessRuleException.class,
            () -> todoService.addSubTodo(400L, 100L));
        assertEquals("不能添加会导致循环依赖的子任务", exception.getMessage());
    }

    @Test
    @DisplayName("多级子任务完成检查应正确工作")
    void changeStatus_DeepSubTodoCompletionCheck_ShouldWorkCorrectly() {
        // Arrange: 父任务有子任务，但子任务未完成
        Todo parent = new Todo("父任务", null, Priority.MEDIUM, null);
        parent.setId(1L);
        parent.setStatus(TodoStatus.IN_PROGRESS);
        parent.addSubTodo(2L);
        parent.addSubTodo(3L);

        when(todoRepository.findById(1L)).thenReturn(Optional.of(parent));

        // Act & Assert: 只提供一个子任务的完成状态
        Set<Long> partiallyCompleted = Collections.singleton(2L);
        BusinessRuleException exception = assertThrows(BusinessRuleException.class,
            () -> todoService.changeStatus(1L, TodoStatus.COMPLETED, partiallyCompleted));
        assertEquals("所有子任务必须完成后才能完成父任务", exception.getMessage());
    }

    @Test
    @DisplayName("空标签集合创建任务应正常工作")
    void createTodo_WithEmptyTags_ShouldWork() {
        // Arrange
        when(todoRepository.save(any(Todo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Todo result = todoService.createTodo("任务", "描述", Priority.MEDIUM, null, null, Collections.emptySet(), null);

        // Assert
        assertNotNull(result);
        assertTrue(result.getTags().isEmpty());
    }

    @Test
    @DisplayName("null标签集合创建任务应正常工作")
    void createTodo_WithNullTags_ShouldWork() {
        // Arrange
        when(todoRepository.save(any(Todo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Todo result = todoService.createTodo("任务", "描述", Priority.MEDIUM, null, null, null, null);

        // Assert
        assertNotNull(result);
        assertTrue(result.getTags().isEmpty());
    }
}
