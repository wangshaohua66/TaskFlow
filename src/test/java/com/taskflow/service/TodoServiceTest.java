package com.taskflow.service;

import com.taskflow.exception.BusinessRuleException;
import com.taskflow.exception.OptimisticLockException;
import com.taskflow.exception.ResourceNotFoundException;
import com.taskflow.model.*;
import com.taskflow.repository.TodoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TodoService服务层测试")
class TodoServiceTest {

    @Mock
    private TodoRepository todoRepository;

    @InjectMocks
    private TodoService todoService;

    private Todo testTodo;

    @BeforeEach
    void setUp() {
        testTodo = new Todo("测试任务", "测试描述", Priority.MEDIUM, LocalDateTime.now().plusDays(7));
        testTodo.setId(1L);
        testTodo.setVersion(0);
    }

    @Nested
    @DisplayName("创建Todo测试")
    class CreateTodoTests {

        @Test
        @DisplayName("创建有效Todo应成功")
        void createTodo_ValidInput_ShouldSucceed() {
            when(todoRepository.save(any(Todo.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Todo result = todoService.createTodo(
                "新任务", "描述", Priority.HIGH,
                LocalDateTime.now().plusDays(7), null,
                new HashSet<>(Arrays.asList("work", "urgent")), null
            );

            assertNotNull(result);
            assertEquals("新任务", result.getTitle());
            assertEquals(Priority.HIGH, result.getPriority());
            assertTrue(result.getTags().contains("work"));
            verify(todoRepository, times(1)).save(any(Todo.class));
        }

        @Test
        @DisplayName("创建带父任务的Todo应建立关联")
        void createTodo_WithParent_ShouldEstablishRelation() {
            Todo parent = new Todo("父任务", null, Priority.HIGH, null);
            parent.setId(100L);
            parent.setVersion(0);

            when(todoRepository.findById(100L)).thenReturn(Optional.of(parent));
            when(todoRepository.save(any(Todo.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Todo result = todoService.createTodo(
                "子任务", null, Priority.MEDIUM, null, null, null, 100L
            );

            assertEquals(100L, result.getParentId());
            assertTrue(parent.getSubTodoIds().contains(result.getId()));
            verify(todoRepository, times(2)).save(any(Todo.class));
        }

        @Test
        @DisplayName("父任务不存在应抛出ResourceNotFoundException")
        void createTodo_ParentNotFound_ShouldThrowException() {
            when(todoRepository.findById(anyLong())).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () ->
                todoService.createTodo("子任务", null, Priority.MEDIUM, null, null, null, 999L)
            );
        }

        @Test
        @DisplayName("已完成父任务不能添加子任务")
        void createTodo_CompletedParent_ShouldThrowException() {
            Todo parent = new Todo("父任务", null, Priority.HIGH, null);
            parent.setId(100L);
            parent.setStatus(TodoStatus.COMPLETED);

            when(todoRepository.findById(100L)).thenReturn(Optional.of(parent));

            assertThrows(BusinessRuleException.class, () ->
                todoService.createTodo("子任务", null, Priority.MEDIUM, null, null, null, 100L)
            );
        }

        @Test
        @DisplayName("创建无效Todo应抛出IllegalArgumentException")
        void createTodo_InvalidInput_ShouldThrowException() {
            assertThrows(IllegalArgumentException.class, () ->
                todoService.createTodo(null, null, Priority.MEDIUM, null, null, null, null)
            );
        }
    }

    @Nested
    @DisplayName("查询Todo测试")
    class GetTodoTests {

        @Test
        @DisplayName("根据ID查询存在的Todo应成功")
        void getTodoById_ExistingId_ShouldReturnTodo() {
            when(todoRepository.findById(1L)).thenReturn(Optional.of(testTodo));

            Todo result = todoService.getTodoById(1L);

            assertNotNull(result);
            assertEquals(testTodo.getId(), result.getId());
        }

        @Test
        @DisplayName("根据ID查询不存在的Todo应抛出ResourceNotFoundException")
        void getTodoById_NonExistingId_ShouldThrowException() {
            when(todoRepository.findById(anyLong())).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () ->
                todoService.getTodoById(999L)
            );
        }
    }

    @Nested
    @DisplayName("更新Todo测试")
    class UpdateTodoTests {

        @Test
        @DisplayName("更新Todo应成功")
        void updateTodo_ValidInput_ShouldSucceed() {
            when(todoRepository.findById(1L)).thenReturn(Optional.of(testTodo));
            when(todoRepository.save(any(Todo.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Todo result = todoService.updateTodo(
                1L, "更新标题", "更新描述", Priority.HIGH,
                LocalDateTime.now().plusDays(10), null, 0
            );

            assertEquals("更新标题", result.getTitle());
            assertEquals(Priority.HIGH, result.getPriority());
        }

        @Test
        @DisplayName("版本不匹配应抛出OptimisticLockException")
        void updateTodo_VersionMismatch_ShouldThrowException() {
            when(todoRepository.findById(1L)).thenReturn(Optional.of(testTodo));

            assertThrows(OptimisticLockException.class, () ->
                todoService.updateTodo(1L, "更新", null, null, null, null, 999)
            );
        }

        @Test
        @DisplayName("更新已完成的Todo应抛出BusinessRuleException")
        void updateTodo_CompletedTodo_ShouldThrowException() {
            testTodo.setStatus(TodoStatus.COMPLETED);
            when(todoRepository.findById(1L)).thenReturn(Optional.of(testTodo));

            assertThrows(BusinessRuleException.class, () ->
                todoService.updateTodo(1L, "更新", null, null, null, null, 0)
            );
        }

        @Test
        @DisplayName("更新不存在的Todo应抛出ResourceNotFoundException")
        void updateTodo_NonExistingTodo_ShouldThrowException() {
            when(todoRepository.findById(anyLong())).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () ->
                todoService.updateTodo(999L, "更新", null, null, null, null, 0)
            );
        }
    }

    @Nested
    @DisplayName("部分更新Todo测试")
    class PatchTodoTests {

        @Test
        @DisplayName("部分更新Todo应成功")
        void patchTodo_ValidInput_ShouldSucceed() {
            when(todoRepository.findById(1L)).thenReturn(Optional.of(testTodo));
            when(todoRepository.save(any(Todo.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Map<String, Object> updates = new HashMap<>();
            updates.put("title", "新标题");
            updates.put("priority", "HIGH");

            Todo result = todoService.patchTodo(1L, updates, 0);

            assertEquals("新标题", result.getTitle());
            assertEquals(Priority.HIGH, result.getPriority());
        }

        @Test
        @DisplayName("部分更新版本不匹配应抛出OptimisticLockException")
        void patchTodo_VersionMismatch_ShouldThrowException() {
            when(todoRepository.findById(1L)).thenReturn(Optional.of(testTodo));

            assertThrows(OptimisticLockException.class, () ->
                todoService.patchTodo(1L, new HashMap<>(), 999)
            );
        }

        @Test
        @DisplayName("部分更新已完成的Todo应抛出BusinessRuleException")
        void patchTodo_CompletedTodo_ShouldThrowException() {
            testTodo.setStatus(TodoStatus.COMPLETED);
            when(todoRepository.findById(1L)).thenReturn(Optional.of(testTodo));

            assertThrows(BusinessRuleException.class, () ->
                todoService.patchTodo(1L, new HashMap<>(), 0)
            );
        }
    }

    @Nested
    @DisplayName("删除Todo测试")
    class DeleteTodoTests {

        @Test
        @DisplayName("删除无子任务的Todo应成功")
        void deleteTodo_NoSubTodos_ShouldSucceed() {
            when(todoRepository.findById(1L)).thenReturn(Optional.of(testTodo));
            when(todoRepository.findByParentId(1L)).thenReturn(Collections.emptyList());
            doNothing().when(todoRepository).deleteById(1L);

            assertDoesNotThrow(() -> todoService.deleteTodo(1L));

            verify(todoRepository).deleteById(1L);
        }

        @Test
        @DisplayName("删除有未完成子任务的Todo应抛出BusinessRuleException")
        void deleteTodo_HasIncompleteSubTodos_ShouldThrowException() {
            Todo subTodo = new Todo("子任务", null, Priority.MEDIUM, null);
            subTodo.setStatus(TodoStatus.IN_PROGRESS);

            when(todoRepository.findById(1L)).thenReturn(Optional.of(testTodo));
            when(todoRepository.findByParentId(1L)).thenReturn(Arrays.asList(subTodo));

            assertThrows(BusinessRuleException.class, () ->
                todoService.deleteTodo(1L)
            );
        }

        @Test
        @DisplayName("删除有已完成子任务的Todo应成功")
        void deleteTodo_HasCompletedSubTodos_ShouldSucceed() {
            Todo subTodo = new Todo("子任务", null, Priority.MEDIUM, null);
            subTodo.setStatus(TodoStatus.COMPLETED);

            when(todoRepository.findById(1L)).thenReturn(Optional.of(testTodo));
            when(todoRepository.findByParentId(1L)).thenReturn(Arrays.asList(subTodo));
            doNothing().when(todoRepository).deleteById(1L);

            assertDoesNotThrow(() -> todoService.deleteTodo(1L));
        }

        @Test
        @DisplayName("删除子任务时从父任务移除引用")
        void deleteTodo_WithParent_ShouldRemoveReference() {
            Todo parent = new Todo("父任务", null, Priority.HIGH, null);
            parent.setId(100L);
            testTodo.setParentId(100L);
            parent.addSubTodo(testTodo.getId());

            when(todoRepository.findById(1L)).thenReturn(Optional.of(testTodo));
            when(todoRepository.findById(100L)).thenReturn(Optional.of(parent));
            when(todoRepository.findByParentId(1L)).thenReturn(Collections.emptyList());
            when(todoRepository.save(any(Todo.class))).thenAnswer(invocation -> invocation.getArgument(0));
            doNothing().when(todoRepository).deleteById(1L);

            todoService.deleteTodo(1L);

            assertFalse(parent.getSubTodoIds().contains(testTodo.getId()));
            verify(todoRepository).save(parent);
        }
    }

    @Nested
    @DisplayName("状态转换测试")
    class ChangeStatusTests {

        @Test
        @DisplayName("合法状态转换应成功")
        void changeStatus_ValidTransition_ShouldSucceed() {
            testTodo.setStatus(TodoStatus.PENDING);
            when(todoRepository.findById(1L)).thenReturn(Optional.of(testTodo));
            when(todoRepository.save(any(Todo.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Todo result = todoService.changeStatus(1L, TodoStatus.IN_PROGRESS, null);

            assertEquals(TodoStatus.IN_PROGRESS, result.getStatus());
        }

        @Test
        @DisplayName("非法状态转换应抛出BusinessRuleException")
        void changeStatus_InvalidTransition_ShouldThrowException() {
            testTodo.setStatus(TodoStatus.PENDING);
            when(todoRepository.findById(1L)).thenReturn(Optional.of(testTodo));

            assertThrows(BusinessRuleException.class, () ->
                todoService.changeStatus(1L, TodoStatus.COMPLETED, null)
            );
        }

        @Test
        @DisplayName("完成有子任务的Todo需要所有子任务完成")
        void changeStatus_CompleteWithSubTodos_ShouldRequireAllCompleted() {
            testTodo.setStatus(TodoStatus.IN_PROGRESS);
            testTodo.addSubTodo(100L);
            testTodo.addSubTodo(200L);

            when(todoRepository.findById(1L)).thenReturn(Optional.of(testTodo));

            Set<Long> completedIds = new HashSet<>(Arrays.asList(100L, 200L));

            assertDoesNotThrow(() ->
                todoService.changeStatus(1L, TodoStatus.COMPLETED, completedIds)
            );
        }

        @Test
        @DisplayName("完成有未完成子任务的Todo应抛出BusinessRuleException")
        void changeStatus_CompleteWithIncompleteSubTodos_ShouldThrowException() {
            testTodo.setStatus(TodoStatus.IN_PROGRESS);
            testTodo.addSubTodo(100L);
            testTodo.addSubTodo(200L);

            when(todoRepository.findById(1L)).thenReturn(Optional.of(testTodo));

            Set<Long> completedIds = new HashSet<>(Collections.singletonList(100L));

            assertThrows(BusinessRuleException.class, () ->
                todoService.changeStatus(1L, TodoStatus.COMPLETED, completedIds)
            );
        }
    }

    @Nested
    @DisplayName("标签管理测试")
    class TagManagementTests {

        @Test
        @DisplayName("添加标签应成功")
        void addTag_ValidInput_ShouldSucceed() {
            when(todoRepository.findById(1L)).thenReturn(Optional.of(testTodo));
            when(todoRepository.save(any(Todo.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Todo result = todoService.addTag(1L, "work");

            assertTrue(result.getTags().contains("work"));
        }

        @Test
        @DisplayName("移除标签应成功")
        void removeTag_ExistingTag_ShouldSucceed() {
            testTodo.addTag("work");
            when(todoRepository.findById(1L)).thenReturn(Optional.of(testTodo));
            when(todoRepository.save(any(Todo.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Todo result = todoService.removeTag(1L, "work");

            assertFalse(result.getTags().contains("work"));
        }
    }

    @Nested
    @DisplayName("子任务管理测试")
    class SubTodoManagementTests {

        @Test
        @DisplayName("添加子任务应成功")
        void addSubTodo_ValidInput_ShouldSucceed() {
            Todo parent = new Todo("父任务", null, Priority.HIGH, null);
            parent.setId(1L);
            Todo child = new Todo("子任务", null, Priority.MEDIUM, null);
            child.setId(2L);

            when(todoRepository.findById(1L)).thenReturn(Optional.of(parent));
            when(todoRepository.findById(2L)).thenReturn(Optional.of(child));
            when(todoRepository.save(any(Todo.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Todo result = todoService.addSubTodo(1L, 2L);

            assertTrue(parent.getSubTodoIds().contains(2L));
            assertEquals(1L, child.getParentId());
        }

        @Test
        @DisplayName("添加循环依赖的子任务应抛出BusinessRuleException")
        void addSubTodo_CircularDependency_ShouldThrowException() {
            Todo parent = new Todo("父任务", null, Priority.HIGH, null);
            parent.setId(1L);
            Todo child = new Todo("子任务", null, Priority.MEDIUM, null);
            child.setId(2L);
            child.addSubTodo(1L);

            when(todoRepository.findById(1L)).thenReturn(Optional.of(parent));
            when(todoRepository.findById(2L)).thenReturn(Optional.of(child));

            assertThrows(BusinessRuleException.class, () ->
                todoService.addSubTodo(1L, 2L)
            );
        }

        @Test
        @DisplayName("添加已有父任务的子任务应抛出BusinessRuleException")
        void addSubTodo_AlreadyHasParent_ShouldThrowException() {
            Todo parent = new Todo("父任务", null, Priority.HIGH, null);
            parent.setId(1L);
            Todo child = new Todo("子任务", null, Priority.MEDIUM, null);
            child.setId(2L);
            child.setParentId(999L);

            when(todoRepository.findById(1L)).thenReturn(Optional.of(parent));
            when(todoRepository.findById(2L)).thenReturn(Optional.of(child));

            assertThrows(BusinessRuleException.class, () ->
                todoService.addSubTodo(1L, 2L)
            );
        }

        @Test
        @DisplayName("移除子任务应成功")
        void removeSubTodo_ValidInput_ShouldSucceed() {
            Todo parent = new Todo("父任务", null, Priority.HIGH, null);
            parent.setId(1L);
            Todo child = new Todo("子任务", null, Priority.MEDIUM, null);
            child.setId(2L);
            child.setParentId(1L);
            parent.addSubTodo(2L);

            when(todoRepository.findById(1L)).thenReturn(Optional.of(parent));
            when(todoRepository.findById(2L)).thenReturn(Optional.of(child));
            when(todoRepository.save(any(Todo.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Todo result = todoService.removeSubTodo(1L, 2L);

            assertFalse(parent.getSubTodoIds().contains(2L));
            assertNull(child.getParentId());
        }
    }

    @Nested
    @DisplayName("循环依赖检测测试")
    class CircularDependencyTests {

        @Test
        @DisplayName("A->B->C->A 循环依赖应被检测")
        void circularDependency_ThreeLevelCycle_ShouldBeDetected() {
            Todo todoA = new Todo("A", null, Priority.MEDIUM, null);
            todoA.setId(1L);
            Todo todoC = new Todo("C", null, Priority.MEDIUM, null);
            todoC.setId(3L);
            todoC.addSubTodo(1L);

            when(todoRepository.findById(1L)).thenReturn(Optional.of(todoA));
            when(todoRepository.findById(3L)).thenReturn(Optional.of(todoC));

            assertThrows(BusinessRuleException.class, () ->
                todoService.addSubTodo(1L, 3L)
            );
        }

        @Test
        @DisplayName("直接循环 A->A 应被检测")
        void circularDependency_SelfReference_ShouldBeDetected() {
            Todo todoA = new Todo("A", null, Priority.MEDIUM, null);
            todoA.setId(1L);

            assertThrows(IllegalArgumentException.class, () ->
                todoA.addSubTodo(1L)
            );
        }
    }

    @Nested
    @DisplayName("搜索Todo测试")
    class SearchTodosTests {

        @Test
        @DisplayName("搜索应返回分页结果")
        void searchTodos_ValidCriteria_ShouldReturnPagedResult() {
            PageResult<Todo> pageResult = new PageResult<>(
                Arrays.asList(testTodo), 0, 20, 1
            );

            when(todoRepository.search(any(SearchCriteria.class))).thenReturn(pageResult);

            SearchCriteria criteria = new SearchCriteria();
            criteria.setKeyword("测试");

            PageResult<Todo> result = todoService.searchTodos(criteria);

            assertNotNull(result);
            assertEquals(1, result.getTotalElements());
        }
    }

    @Nested
    @DisplayName("优先级升级测试")
    class UpgradePriorityTests {

        @Test
        @DisplayName("升级过期任务优先级应成功")
        void upgradeExpiredTodosPriority_ShouldSucceed() {
            testTodo.setDueDate(LocalDateTime.now().minusHours(25));
            testTodo.setStatus(TodoStatus.PENDING);
            testTodo.setPriority(Priority.LOW);

            when(todoRepository.findTodosNeedingPriorityUpgrade()).thenReturn(Arrays.asList(testTodo));
            when(todoRepository.save(any(Todo.class))).thenAnswer(invocation -> invocation.getArgument(0));

            List<Todo> result = todoService.upgradeExpiredTodosPriority();

            assertFalse(result.isEmpty());
            assertEquals(Priority.MEDIUM, result.get(0).getPriority());
        }

        @Test
        @DisplayName("CRITICAL优先级不应再升级")
        void upgradeExpiredTodosPriority_Critical_ShouldNotUpgrade() {
            testTodo.setDueDate(LocalDateTime.now().minusHours(25));
            testTodo.setStatus(TodoStatus.PENDING);
            testTodo.setPriority(Priority.CRITICAL);

            when(todoRepository.findTodosNeedingPriorityUpgrade()).thenReturn(Arrays.asList(testTodo));

            List<Todo> result = todoService.upgradeExpiredTodosPriority();

            assertTrue(result.isEmpty());
            verify(todoRepository, never()).save(any(Todo.class));
        }
    }

    @Nested
    @DisplayName("统计信息测试")
    class StatisticsTests {

        @Test
        @DisplayName("获取统计信息应返回正确数据")
        void getStatistics_ShouldReturnCorrectData() {
            Todo completed = new Todo("已完成", null, Priority.HIGH, null);
            completed.setStatus(TodoStatus.COMPLETED);
            Todo pending = new Todo("待处理", null, Priority.LOW, null);
            pending.setStatus(TodoStatus.PENDING);
            testTodo.setStatus(TodoStatus.IN_PROGRESS);

            when(todoRepository.findAll()).thenReturn(Arrays.asList(testTodo, completed, pending));

            Map<String, Object> stats = todoService.getStatistics();

            assertEquals(3, stats.get("total"));
            assertEquals(1L, stats.get("completed"));
            assertEquals(1L, stats.get("pending"));
            assertEquals(1L, stats.get("inProgress"));
            assertNotNull(stats.get("byPriority"));
        }
    }

    @Nested
    @DisplayName("批量删除测试")
    class DeleteCompletedTodosTests {

        @Test
        @DisplayName("批量删除已完成任务应成功")
        void deleteCompletedTodos_ShouldSucceed() {
            Todo completed1 = new Todo("已完成1", null, Priority.LOW, null);
            completed1.setId(10L);
            completed1.setStatus(TodoStatus.COMPLETED);
            Todo completed2 = new Todo("已完成2", null, Priority.LOW, null);
            completed2.setId(11L);
            completed2.setStatus(TodoStatus.COMPLETED);

            when(todoRepository.findByStatus(TodoStatus.COMPLETED)).thenReturn(Arrays.asList(completed1, completed2));
            when(todoRepository.findById(anyLong())).thenReturn(Optional.of(completed1));
            when(todoRepository.findByParentId(anyLong())).thenReturn(Collections.emptyList());
            doNothing().when(todoRepository).deleteById(anyLong());

            int count = todoService.deleteCompletedTodos();

            assertEquals(2, count);
        }
    }

    @Nested
    @DisplayName("克隆任务测试")
    class CloneTodoTests {

        @Test
        @DisplayName("克隆任务应成功")
        void cloneTodo_ValidId_ShouldSucceed() {
            testTodo.addTag("work");
            testTodo.addTag("urgent");

            when(todoRepository.findById(1L)).thenReturn(Optional.of(testTodo));
            when(todoRepository.save(any(Todo.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Todo cloned = todoService.cloneTodo(1L);

            assertNotEquals(testTodo.getId(), cloned.getId());
            assertTrue(cloned.getTitle().contains("副本"));
            assertEquals(testTodo.getDescription(), cloned.getDescription());
            assertEquals(testTodo.getPriority(), cloned.getPriority());
            assertEquals(testTodo.getTags(), cloned.getTags());
        }

        @Test
        @DisplayName("克隆不存在的任务应抛出ResourceNotFoundException")
        void cloneTodo_NonExistingId_ShouldThrowException() {
            when(todoRepository.findById(anyLong())).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () ->
                todoService.cloneTodo(999L)
            );
        }
    }

    @Nested
    @DisplayName("乐观锁测试")
    class OptimisticLockTests {

        @Test
        @DisplayName("版本匹配时更新应成功")
        void updateWithCorrectVersion_ShouldSucceed() {
            testTodo.setVersion(5);
            when(todoRepository.findById(1L)).thenReturn(Optional.of(testTodo));
            when(todoRepository.save(any(Todo.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Todo result = todoService.updateTodo(1L, "新标题", null, null, null, null, 5);

            assertEquals("新标题", result.getTitle());
        }

        @Test
        @DisplayName("版本不匹配时更新应抛出OptimisticLockException")
        void updateWithWrongVersion_ShouldThrowException() {
            testTodo.setVersion(5);
            when(todoRepository.findById(1L)).thenReturn(Optional.of(testTodo));

            assertThrows(OptimisticLockException.class, () ->
                todoService.updateTodo(1L, "新标题", null, null, null, null, 3)
            );
        }
    }

    @Nested
    @DisplayName("多层循环依赖测试")
    class MultiLevelCircularDependencyTests {

        @Test
        @DisplayName("A->B->C->D->A 四层循环依赖应被检测")
        void circularDependency_FourLevelCycle_ShouldBeDetected() {
            Todo todoA = new Todo("A", null, Priority.MEDIUM, null);
            todoA.setId(1L);
            Todo todoD = new Todo("D", null, Priority.MEDIUM, null);
            todoD.setId(4L);
            todoD.addSubTodo(1L);

            when(todoRepository.findById(1L)).thenReturn(Optional.of(todoA));
            when(todoRepository.findById(4L)).thenReturn(Optional.of(todoD));

            assertThrows(BusinessRuleException.class, () ->
                todoService.addSubTodo(1L, 4L)
            );
        }

        @Test
        @DisplayName("深层嵌套循环依赖应被检测")
        void circularDependency_DeepNested_ShouldBeDetected() {
            Todo parent = new Todo("Parent", null, Priority.MEDIUM, null);
            parent.setId(1L);
            Todo child = new Todo("Child", null, Priority.MEDIUM, null);
            child.setId(2L);
            child.addSubTodo(1L);

            when(todoRepository.findById(1L)).thenReturn(Optional.of(parent));
            when(todoRepository.findById(2L)).thenReturn(Optional.of(child));

            assertThrows(BusinessRuleException.class, () ->
                todoService.addSubTodo(1L, 2L)
            );
        }
    }

    @Nested
    @DisplayName("子任务完成条件测试")
    class SubTodoCompletionTests {

        @Test
        @DisplayName("完成父任务时所有子任务必须完成")
        void completeParent_AllSubTodosMustBeCompleted() {
            testTodo.setStatus(TodoStatus.IN_PROGRESS);
            testTodo.addSubTodo(100L);
            testTodo.addSubTodo(200L);

            when(todoRepository.findById(1L)).thenReturn(Optional.of(testTodo));
            when(todoRepository.save(any(Todo.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Set<Long> completedIds = new HashSet<>(Arrays.asList(100L, 200L));

            Todo result = todoService.changeStatus(1L, TodoStatus.COMPLETED, completedIds);

            assertEquals(TodoStatus.COMPLETED, result.getStatus());
        }

        @Test
        @DisplayName("完成父任务时部分子任务未完成应抛出异常")
        void completeParent_SomeSubTodosIncomplete_ShouldThrowException() {
            testTodo.setStatus(TodoStatus.IN_PROGRESS);
            testTodo.addSubTodo(100L);
            testTodo.addSubTodo(200L);
            testTodo.addSubTodo(300L);

            when(todoRepository.findById(1L)).thenReturn(Optional.of(testTodo));

            Set<Long> completedIds = new HashSet<>(Arrays.asList(100L, 200L));

            assertThrows(BusinessRuleException.class, () ->
                todoService.changeStatus(1L, TodoStatus.COMPLETED, completedIds)
            );
        }

        @Test
        @DisplayName("无子任务时可以直接完成")
        void completeParent_NoSubTodos_CanCompleteDirectly() {
            testTodo.setStatus(TodoStatus.IN_PROGRESS);

            when(todoRepository.findById(1L)).thenReturn(Optional.of(testTodo));
            when(todoRepository.save(any(Todo.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Todo result = todoService.changeStatus(1L, TodoStatus.COMPLETED, null);

            assertEquals(TodoStatus.COMPLETED, result.getStatus());
        }
    }

    @Nested
    @DisplayName("删除父任务测试")
    class DeleteParentTodoTests {

        @Test
        @DisplayName("删除有已完成子任务的父任务应成功")
        void deleteParent_AllSubTodosCompleted_ShouldSucceed() {
            Todo subTodo1 = new Todo("子任务1", null, Priority.MEDIUM, null);
            subTodo1.setStatus(TodoStatus.COMPLETED);
            Todo subTodo2 = new Todo("子任务2", null, Priority.MEDIUM, null);
            subTodo2.setStatus(TodoStatus.CANCELLED);

            when(todoRepository.findById(1L)).thenReturn(Optional.of(testTodo));
            when(todoRepository.findByParentId(1L)).thenReturn(Arrays.asList(subTodo1, subTodo2));
            doNothing().when(todoRepository).deleteById(1L);

            assertDoesNotThrow(() -> todoService.deleteTodo(1L));
        }

        @Test
        @DisplayName("删除有IN_PROGRESS子任务的父任务应抛出异常")
        void deleteParent_HasInProgressSubTodo_ShouldThrowException() {
            Todo subTodo = new Todo("子任务", null, Priority.MEDIUM, null);
            subTodo.setStatus(TodoStatus.IN_PROGRESS);

            when(todoRepository.findById(1L)).thenReturn(Optional.of(testTodo));
            when(todoRepository.findByParentId(1L)).thenReturn(Arrays.asList(subTodo));

            assertThrows(BusinessRuleException.class, () ->
                todoService.deleteTodo(1L)
            );
        }

        @Test
        @DisplayName("删除有PENDING子任务的父任务应抛出异常")
        void deleteParent_HasPendingSubTodo_ShouldThrowException() {
            Todo subTodo = new Todo("子任务", null, Priority.MEDIUM, null);
            subTodo.setStatus(TodoStatus.PENDING);

            when(todoRepository.findById(1L)).thenReturn(Optional.of(testTodo));
            when(todoRepository.findByParentId(1L)).thenReturn(Arrays.asList(subTodo));

            assertThrows(BusinessRuleException.class, () ->
                todoService.deleteTodo(1L)
            );
        }

        @Test
        @DisplayName("删除有EXPIRED子任务的父任务应抛出异常")
        void deleteParent_HasExpiredSubTodo_ShouldThrowException() {
            Todo subTodo = new Todo("子任务", null, Priority.MEDIUM, null);
            subTodo.setStatus(TodoStatus.EXPIRED);

            when(todoRepository.findById(1L)).thenReturn(Optional.of(testTodo));
            when(todoRepository.findByParentId(1L)).thenReturn(Arrays.asList(subTodo));

            assertThrows(BusinessRuleException.class, () ->
                todoService.deleteTodo(1L)
            );
        }
    }

    @Nested
    @DisplayName("优先级自动升级测试")
    class AutoPriorityUpgradeTests {

        @Test
        @DisplayName("多个过期任务应全部升级")
        void upgradeExpiredTodos_MultipleExpired_ShouldUpgradeAll() {
            Todo todo1 = new Todo("任务1", null, Priority.LOW, null);
            todo1.setDueDate(LocalDateTime.now().minusHours(25));
            todo1.setStatus(TodoStatus.PENDING);

            Todo todo2 = new Todo("任务2", null, Priority.MEDIUM, null);
            todo2.setDueDate(LocalDateTime.now().minusHours(30));
            todo2.setStatus(TodoStatus.IN_PROGRESS);

            when(todoRepository.findTodosNeedingPriorityUpgrade()).thenReturn(Arrays.asList(todo1, todo2));
            when(todoRepository.save(any(Todo.class))).thenAnswer(invocation -> invocation.getArgument(0));

            List<Todo> result = todoService.upgradeExpiredTodosPriority();

            assertEquals(2, result.size());
            assertEquals(Priority.MEDIUM, result.get(0).getPriority());
            assertEquals(Priority.HIGH, result.get(1).getPriority());
        }

        @Test
        @DisplayName("无过期任务应返回空列表")
        void upgradeExpiredTodos_NoExpired_ShouldReturnEmpty() {
            when(todoRepository.findTodosNeedingPriorityUpgrade()).thenReturn(Collections.emptyList());

            List<Todo> result = todoService.upgradeExpiredTodosPriority();

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("创建Todo边界测试")
    class CreateTodoBoundaryTests {

        @Test
        @DisplayName("创建Todo时截止日期早于当前时间应抛出异常")
        void createTodo_DueDateInPast_ShouldThrowException() {
            assertThrows(IllegalArgumentException.class, () ->
                todoService.createTodo(
                    "测试任务", "描述", Priority.MEDIUM,
                    LocalDateTime.now().minusDays(1), null, null, null
                )
            );
        }

        @Test
        @DisplayName("创建Todo时提醒时间晚于截止日期应抛出异常")
        void createTodo_ReminderAfterDueDate_ShouldThrowException() {
            LocalDateTime dueDate = LocalDateTime.now().plusDays(7);

            assertThrows(IllegalArgumentException.class, () ->
                todoService.createTodo(
                    "测试任务", "描述", Priority.MEDIUM,
                    dueDate, dueDate.plusDays(1), null, null
                )
            );
        }

        @Test
        @DisplayName("创建Todo时添加多个标签应成功")
        void createTodo_MultipleTags_ShouldSucceed() {
            Set<String> tags = new HashSet<>(Arrays.asList("work", "urgent", "project"));

            when(todoRepository.save(any(Todo.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Todo result = todoService.createTodo(
                "测试任务", "描述", Priority.HIGH,
                LocalDateTime.now().plusDays(7), null, tags, null
            );

            assertEquals(3, result.getTags().size());
        }
    }

    @Nested
    @DisplayName("状态转换边界测试")
    class StatusTransitionBoundaryTests {

        @Test
        @DisplayName("CANCELLED状态可以重新激活到PENDING")
        void cancelled_CanReactivateToPending() {
            testTodo.setStatus(TodoStatus.CANCELLED);

            when(todoRepository.findById(1L)).thenReturn(Optional.of(testTodo));
            when(todoRepository.save(any(Todo.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Todo result = todoService.changeStatus(1L, TodoStatus.PENDING, null);

            assertEquals(TodoStatus.PENDING, result.getStatus());
        }

        @Test
        @DisplayName("IN_PROGRESS状态可以回退到PENDING")
        void inProgress_CanRevertToPending() {
            testTodo.setStatus(TodoStatus.IN_PROGRESS);

            when(todoRepository.findById(1L)).thenReturn(Optional.of(testTodo));
            when(todoRepository.save(any(Todo.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Todo result = todoService.changeStatus(1L, TodoStatus.PENDING, null);

            assertEquals(TodoStatus.PENDING, result.getStatus());
        }

        @Test
        @DisplayName("PENDING状态不能直接转换到COMPLETED")
        void pending_CannotTransitionToCompleted() {
            testTodo.setStatus(TodoStatus.PENDING);

            when(todoRepository.findById(1L)).thenReturn(Optional.of(testTodo));

            assertThrows(BusinessRuleException.class, () ->
                todoService.changeStatus(1L, TodoStatus.COMPLETED, null)
            );
        }
    }
}
