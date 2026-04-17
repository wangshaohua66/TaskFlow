package com.taskflow.service;

import com.taskflow.exception.BusinessRuleException;
import com.taskflow.exception.OptimisticLockException;
import com.taskflow.exception.ResourceNotFoundException;
import com.taskflow.model.*;
import com.taskflow.repository.TodoRepository;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TodoService服务层测试")
class TodoServiceTest {

    private static Set<Long> createLongSet(Long... values) {
        Set<Long> set = new HashSet<>();
        Collections.addAll(set, values);
        return set;
    }

    @Mock
    private TodoRepository todoRepository;

    @InjectMocks
    private TodoService todoService;

    @Nested
    @DisplayName("创建任务测试")
    class CreateTodoTests {

        @Test
        @DisplayName("创建任务_有效数据_应成功")
        void createTodo_WithValidData_ShouldSucceed() {
            Todo todo = new Todo("Test Title", "Test Description", Priority.HIGH, LocalDateTime.now().plusDays(7));
            when(todoRepository.save(any(Todo.class))).thenReturn(todo);

            Todo result = todoService.createTodo(
                    "Test Title", "Test Description", Priority.HIGH,
                    LocalDateTime.now().plusDays(7), null, null, null
            );

            assertNotNull(result);
            assertEquals("Test Title", result.getTitle());
            verify(todoRepository, times(1)).save(any(Todo.class));
        }

        @Test
        @DisplayName("创建任务_标题为空_应抛出异常")
        void createTodo_WithNullTitle_ShouldThrowException() {
            assertThrows(IllegalArgumentException.class, () ->
                    todoService.createTodo(null, null, null, null, null, null, null)
            );
        }

        @Test
        @DisplayName("创建任务_带父任务_应成功")
        void createTodo_WithParentId_ShouldSucceed() {
            Todo parent = new Todo("Parent", null, Priority.MEDIUM, null);
            parent.setId(1L);

            when(todoRepository.findById(1L)).thenReturn(Optional.of(parent));
            when(todoRepository.save(any(Todo.class))).thenAnswer(i -> i.getArgument(0));

            Todo result = todoService.createTodo(
                    "Child Task", null, Priority.MEDIUM,
                    null, null, null, 1L
            );

            assertNotNull(result);
            assertEquals(1L, result.getParentId());
            verify(todoRepository, times(2)).save(any(Todo.class));
        }

        @Test
        @DisplayName("创建任务_父任务不存在_应抛出异常")
        void createTodo_WithNonExistentParent_ShouldThrowException() {
            when(todoRepository.findById(999L)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () ->
                    todoService.createTodo("Test", null, null, null, null, null, 999L)
            );
        }

        @Test
        @DisplayName("创建任务_父任务已完成_应抛出异常")
        void createTodo_WithCompletedParent_ShouldThrowException() {
            Todo parent = new Todo("Parent", null, Priority.MEDIUM, null);
            parent.setId(1L);
            parent.setStatus(TodoStatus.COMPLETED);

            when(todoRepository.findById(1L)).thenReturn(Optional.of(parent));

            assertThrows(BusinessRuleException.class, () ->
                    todoService.createTodo("Child", null, null, null, null, null, 1L)
            );
        }
    }

    @Nested
    @DisplayName("更新任务测试")
    class UpdateTodoTests {

        @Test
        @DisplayName("更新任务_版本匹配_应成功")
        void updateTodo_WithMatchingVersion_ShouldSucceed() {
            Todo todo = new Todo("Old Title", null, Priority.LOW, LocalDateTime.now().plusDays(3));
            todo.setId(1L);
            todo.setVersion(0);

            when(todoRepository.findById(1L)).thenReturn(Optional.of(todo));
            when(todoRepository.save(any(Todo.class))).thenAnswer(i -> i.getArgument(0));

            Todo result = todoService.updateTodo(
                    1L, "New Title", "New Description", Priority.HIGH,
                    LocalDateTime.now().plusDays(7), null, 0
            );

            assertEquals("New Title", result.getTitle());
            assertEquals(Priority.HIGH, result.getPriority());
            assertEquals(1, result.getVersion());
        }

        @Test
        @DisplayName("更新任务_版本不匹配_应抛出乐观锁异常")
        void updateTodo_WithVersionMismatch_ShouldThrowOptimisticLockException() {
            Todo todo = new Todo("Test", null, Priority.MEDIUM, null);
            todo.setId(1L);
            todo.setVersion(1);

            when(todoRepository.findById(1L)).thenReturn(Optional.of(todo));

            assertThrows(OptimisticLockException.class, () ->
                    todoService.updateTodo(1L, "New Title", null, null, null, null, 0)
            );
        }

        @Test
        @DisplayName("更新任务_已完成_应抛出异常")
        void updateTodo_CompletedStatus_ShouldThrowException() {
            Todo todo = new Todo("Test", null, Priority.MEDIUM, null);
            todo.setId(1L);
            todo.setStatus(TodoStatus.COMPLETED);
            todo.setVersion(0);

            when(todoRepository.findById(1L)).thenReturn(Optional.of(todo));

            assertThrows(BusinessRuleException.class, () ->
                    todoService.updateTodo(1L, "New Title", null, null, null, null, 0)
            );
        }
    }

    @Nested
    @DisplayName("删除任务测试")
    class DeleteTodoTests {

        @Test
        @DisplayName("删除任务_无子任务_应成功")
        void deleteTodo_WithoutSubTodos_ShouldSucceed() {
            Todo todo = new Todo("Test", null, Priority.MEDIUM, null);
            todo.setId(1L);

            when(todoRepository.findById(1L)).thenReturn(Optional.of(todo));
            when(todoRepository.findByParentId(1L)).thenReturn(Collections.emptyList());

            todoService.deleteTodo(1L);

            verify(todoRepository, times(1)).deleteById(1L);
        }

        @Test
        @DisplayName("删除任务_有未完成子任务_应抛出异常")
        void deleteTodo_WithIncompleteSubTodos_ShouldThrowException() {
            Todo parent = new Todo("Parent", null, Priority.MEDIUM, null);
            parent.setId(1L);

            Todo subTodo = new Todo("SubTodo", null, Priority.MEDIUM, null);
            subTodo.setStatus(TodoStatus.IN_PROGRESS);

            when(todoRepository.findById(1L)).thenReturn(Optional.of(parent));
            when(todoRepository.findByParentId(1L)).thenReturn(Collections.singletonList(subTodo));

            assertThrows(BusinessRuleException.class, () -> todoService.deleteTodo(1L));
        }

        @Test
        @DisplayName("删除任务_有已完成子任务_应成功")
        void deleteTodo_WithCompletedSubTodos_ShouldSucceed() {
            Todo parent = new Todo("Parent", null, Priority.MEDIUM, null);
            parent.setId(1L);

            Todo subTodo = new Todo("SubTodo", null, Priority.MEDIUM, null);
            subTodo.setStatus(TodoStatus.COMPLETED);

            when(todoRepository.findById(1L)).thenReturn(Optional.of(parent));
            when(todoRepository.findByParentId(1L)).thenReturn(Collections.singletonList(subTodo));

            todoService.deleteTodo(1L);

            verify(todoRepository, times(1)).deleteById(1L);
        }
    }

    @Nested
    @DisplayName("状态转换测试")
    class ChangeStatusTests {

        @Test
        @DisplayName("更改状态_合法转换_应成功")
        void changeStatus_ValidTransition_ShouldSucceed() {
            Todo todo = new Todo("Test", null, Priority.MEDIUM, null);
            todo.setId(1L);
            todo.setStatus(TodoStatus.PENDING);

            when(todoRepository.findById(1L)).thenReturn(Optional.of(todo));
            when(todoRepository.save(any(Todo.class))).thenAnswer(i -> i.getArgument(0));

            Todo result = todoService.changeStatus(1L, TodoStatus.IN_PROGRESS, Collections.emptySet());

            assertEquals(TodoStatus.IN_PROGRESS, result.getStatus());
        }

        @Test
        @DisplayName("更改状态_非法转换_应抛出异常")
        void changeStatus_InvalidTransition_ShouldThrowException() {
            Todo todo = new Todo("Test", null, Priority.MEDIUM, null);
            todo.setId(1L);
            todo.setStatus(TodoStatus.COMPLETED);

            when(todoRepository.findById(1L)).thenReturn(Optional.of(todo));

            assertThrows(BusinessRuleException.class, () ->
                    todoService.changeStatus(1L, TodoStatus.IN_PROGRESS, Collections.emptySet())
            );
        }

        @Test
        @DisplayName("完成任务_有未完成子任务_应抛出异常")
        void changeStatus_CompleteWithIncompleteSubTodos_ShouldThrowException() {
            Todo todo = new Todo("Test", null, Priority.MEDIUM, null);
            todo.setId(1L);
            todo.setStatus(TodoStatus.IN_PROGRESS);
            todo.addSubTodo(2L);

            when(todoRepository.findById(1L)).thenReturn(Optional.of(todo));

            assertThrows(BusinessRuleException.class, () ->
                    todoService.changeStatus(1L, TodoStatus.COMPLETED, Collections.emptySet())
            );
        }

        @Test
        @DisplayName("完成任务_所有子任务已完成_应成功")
        void changeStatus_CompleteWithAllSubTodosCompleted_ShouldSucceed() {
            Todo todo = new Todo("Test", null, Priority.MEDIUM, null);
            todo.setId(1L);
            todo.setStatus(TodoStatus.IN_PROGRESS);
            todo.addSubTodo(2L);
            todo.addSubTodo(3L);

            when(todoRepository.findById(1L)).thenReturn(Optional.of(todo));
            when(todoRepository.save(any(Todo.class))).thenAnswer(i -> i.getArgument(0));

            Todo result = todoService.changeStatus(1L, TodoStatus.COMPLETED, createLongSet(2L, 3L));

            assertEquals(TodoStatus.COMPLETED, result.getStatus());
        }
    }

    @Nested
    @DisplayName("子任务依赖测试")
    class SubTodoDependencyTests {

        @Test
        @DisplayName("添加子任务_无循环依赖_应成功")
        void addSubTodo_WithoutCircularDependency_ShouldSucceed() {
            Todo parent = new Todo("Parent", null, Priority.MEDIUM, null);
            parent.setId(1L);
            Todo subTodo = new Todo("SubTodo", null, Priority.MEDIUM, null);
            subTodo.setId(2L);

            when(todoRepository.findById(1L)).thenReturn(Optional.of(parent));
            when(todoRepository.findById(2L)).thenReturn(Optional.of(subTodo));
            when(todoRepository.save(any(Todo.class))).thenAnswer(i -> i.getArgument(0));

            Todo result = todoService.addSubTodo(1L, 2L);

            assertNotNull(result);
            assertTrue(parent.getSubTodoIds().contains(2L));
            assertEquals(1L, subTodo.getParentId());
        }

        @Test
        @DisplayName("添加子任务_直接循环依赖A→A_应抛出异常")
        void addSubTodo_SelfReference_ShouldThrowException() {
            Todo todoA = new Todo("Task A", null, Priority.MEDIUM, null);
            todoA.setId(1L);

            when(todoRepository.findById(1L)).thenReturn(Optional.of(todoA));

            BusinessRuleException exception = assertThrows(BusinessRuleException.class,
                    () -> todoService.addSubTodo(1L, 1L));
            assertTrue(exception.getMessage().contains("循环依赖"));
        }

        @Test
        @DisplayName("添加子任务_A→B→A二级循环_应抛出异常")
        void addSubTodo_WithTwoLevelCircularDependency_ShouldThrowException() {
            Todo todoA = new Todo("Task A", null, Priority.MEDIUM, null);
            todoA.setId(1L);
            Todo todoB = new Todo("Task B", null, Priority.MEDIUM, null);
            todoB.setId(2L);
            todoB.addSubTodo(1L);

            when(todoRepository.findById(1L)).thenReturn(Optional.of(todoA));
            when(todoRepository.findById(2L)).thenReturn(Optional.of(todoB));

            BusinessRuleException exception = assertThrows(BusinessRuleException.class,
                    () -> todoService.addSubTodo(1L, 2L));
            assertTrue(exception.getMessage().contains("循环依赖"));
        }

        @Test
        @DisplayName("添加子任务_A→B→C→A三级循环_应抛出异常")
        void addSubTodo_WithThreeLevelCircularDependency_ShouldThrowException() {
            Todo todoA = new Todo("A", null, Priority.MEDIUM, null);
            todoA.setId(1L);
            Todo todoB = new Todo("B", null, Priority.MEDIUM, null);
            todoB.setId(2L);
            Todo todoC = new Todo("C", null, Priority.MEDIUM, null);
            todoC.setId(3L);

            todoB.addSubTodo(1L);
            todoC.addSubTodo(2L);

            when(todoRepository.findById(1L)).thenReturn(Optional.of(todoA));
            when(todoRepository.findById(2L)).thenReturn(Optional.of(todoB));
            when(todoRepository.findById(3L)).thenReturn(Optional.of(todoC));

            assertThrows(BusinessRuleException.class, () -> todoService.addSubTodo(1L, 3L));
        }

        @Test
        @DisplayName("添加子任务_A→B→C→D→A深层循环_应抛出异常")
        void addSubTodo_WithDeepCircularDependency_ShouldThrowException() {
            Todo todoA = new Todo("A", null, Priority.MEDIUM, null);
            todoA.setId(1L);
            Todo todoB = new Todo("B", null, Priority.MEDIUM, null);
            todoB.setId(2L);
            Todo todoC = new Todo("C", null, Priority.MEDIUM, null);
            todoC.setId(3L);
            Todo todoD = new Todo("D", null, Priority.MEDIUM, null);
            todoD.setId(4L);

            todoB.addSubTodo(1L);
            todoC.addSubTodo(2L);
            todoD.addSubTodo(3L);

            when(todoRepository.findById(1L)).thenReturn(Optional.of(todoA));
            when(todoRepository.findById(2L)).thenReturn(Optional.of(todoB));
            when(todoRepository.findById(3L)).thenReturn(Optional.of(todoC));
            when(todoRepository.findById(4L)).thenReturn(Optional.of(todoD));

            assertThrows(BusinessRuleException.class, () -> todoService.addSubTodo(1L, 4L));
        }

        @Test
        @DisplayName("添加子任务_子任务已有父任务_应抛出异常")
        void addSubTodo_SubTodoAlreadyHasParent_ShouldThrowException() {
            Todo parent = new Todo("Parent", null, Priority.MEDIUM, null);
            parent.setId(1L);
            Todo subTodo = new Todo("SubTodo", null, Priority.MEDIUM, null);
            subTodo.setId(2L);
            subTodo.setParentId(3L);

            when(todoRepository.findById(1L)).thenReturn(Optional.of(parent));
            when(todoRepository.findById(2L)).thenReturn(Optional.of(subTodo));

            assertThrows(BusinessRuleException.class, () -> todoService.addSubTodo(1L, 2L));
        }

        @Test
        @DisplayName("完成父任务_子任务进行中_应抛出异常")
        void completeParentTodo_SubTodoInProgress_ShouldThrowException() {
            Todo parent = new Todo("Parent", null, Priority.MEDIUM, null);
            parent.setId(1L);
            parent.setStatus(TodoStatus.IN_PROGRESS);
            parent.addSubTodo(2L);
            parent.addSubTodo(3L);

            when(todoRepository.findById(1L)).thenReturn(Optional.of(parent));

            assertThrows(BusinessRuleException.class, () ->
                    todoService.changeStatus(1L, TodoStatus.COMPLETED, createLongSet(2L))
            );
        }

        @Test
        @DisplayName("完成父任务_子任务待处理_应抛出异常")
        void completeParentTodo_SubTodoPending_ShouldThrowException() {
            Todo parent = new Todo("Parent", null, Priority.MEDIUM, null);
            parent.setId(1L);
            parent.setStatus(TodoStatus.IN_PROGRESS);
            parent.addSubTodo(2L);

            when(todoRepository.findById(1L)).thenReturn(Optional.of(parent));

            assertThrows(BusinessRuleException.class, () ->
                    todoService.changeStatus(1L, TodoStatus.COMPLETED, Collections.emptySet())
            );
        }

        @Test
        @DisplayName("完成父任务_子任务已过期_应抛出异常")
        void completeParentTodo_SubTodoExpired_ShouldThrowException() {
            Todo parent = new Todo("Parent", null, Priority.MEDIUM, null);
            parent.setId(1L);
            parent.setStatus(TodoStatus.IN_PROGRESS);
            parent.addSubTodo(2L);

            when(todoRepository.findById(1L)).thenReturn(Optional.of(parent));

            assertThrows(BusinessRuleException.class, () ->
                    todoService.changeStatus(1L, TodoStatus.COMPLETED, Collections.emptySet())
            );
        }

        @Test
        @DisplayName("删除父任务_子任务待处理_应抛出异常")
        void deleteParentTodo_SubTodoPending_ShouldThrowException() {
            Todo parent = new Todo("Parent", null, Priority.MEDIUM, null);
            parent.setId(1L);
            parent.addSubTodo(2L);

            Todo subTodo = new Todo("SubTodo", null, Priority.MEDIUM, null);
            subTodo.setId(2L);
            subTodo.setStatus(TodoStatus.PENDING);

            when(todoRepository.findById(1L)).thenReturn(Optional.of(parent));
            when(todoRepository.findByParentId(1L)).thenReturn(Collections.singletonList(subTodo));

            assertThrows(BusinessRuleException.class, () -> todoService.deleteTodo(1L));
        }

        @Test
        @DisplayName("删除父任务_所有子任务已取消_可以删除")
        void deleteParentTodo_AllSubTodosCancelled_ShouldSucceed() {
            Todo parent = new Todo("Parent", null, Priority.MEDIUM, null);
            parent.setId(1L);
            parent.addSubTodo(2L);
            parent.addSubTodo(3L);

            Todo subTodo1 = new Todo("SubTodo 1", null, Priority.MEDIUM, null);
            subTodo1.setId(2L);
            subTodo1.setStatus(TodoStatus.CANCELLED);
            Todo subTodo2 = new Todo("SubTodo 2", null, Priority.MEDIUM, null);
            subTodo2.setId(3L);
            subTodo2.setStatus(TodoStatus.CANCELLED);

            when(todoRepository.findById(1L)).thenReturn(Optional.of(parent));
            when(todoRepository.findByParentId(1L)).thenReturn(Arrays.asList(subTodo1, subTodo2));
            doNothing().when(todoRepository).deleteById(1L);

            todoService.deleteTodo(1L);

            verify(todoRepository).deleteById(1L);
        }
    }

    @Nested
    @DisplayName("乐观锁测试")
    class OptimisticLockTests {

        @Test
        @DisplayName("PATCH更新_版本不匹配_应抛出异常")
        void patchTodo_VersionMismatch_ShouldThrowOptimisticLockException() {
            Todo todo = new Todo("Test", null, Priority.MEDIUM, null);
            todo.setId(1L);
            todo.setVersion(2);

            when(todoRepository.findById(1L)).thenReturn(Optional.of(todo));

            Map<String, Object> updates = new HashMap<>();
            updates.put("title", "New Title");

            OptimisticLockException exception = assertThrows(OptimisticLockException.class, () ->
                    todoService.patchTodo(1L, updates, 1)
            );
            assertTrue(exception.getMessage().contains("已被其他用户修改"));
        }

        @Test
        @DisplayName("PUT更新_版本不匹配_应抛出异常")
        void updateTodo_VersionMismatch_ShouldThrowOptimisticLockException() {
            Todo todo = new Todo("Test", null, Priority.MEDIUM, null);
            todo.setId(1L);
            todo.setVersion(5);

            when(todoRepository.findById(1L)).thenReturn(Optional.of(todo));

            assertThrows(OptimisticLockException.class, () ->
                    todoService.updateTodo(1L, "New Title", null, null, null, null, 3)
            );
        }



        @Test
        @DisplayName("单次更新_版本号应+1")
        void update_SingleUpdate_ShouldIncrementVersionByOne() {
            Todo todo = new Todo("Test", null, Priority.MEDIUM, null);
            todo.setId(1L);
            todo.setVersion(0);

            when(todoRepository.findById(1L)).thenReturn(Optional.of(todo));
            when(todoRepository.save(any(Todo.class))).thenAnswer(i -> i.getArgument(0));

            Todo result = todoService.updateTodo(
                    1L, "New Title", null, null, null, null, 0
            );

            assertEquals(1, result.getVersion());
        }

        @Test
        @DisplayName("连续多次更新_版本号应持续递增")
        void update_MultipleUpdates_ShouldIncrementVersionContinuously() {
            Todo todo = new Todo("Test", null, Priority.MEDIUM, null);
            todo.setId(1L);
            todo.setVersion(0);

            when(todoRepository.findById(1L)).thenReturn(Optional.of(todo));
            when(todoRepository.save(any(Todo.class))).thenAnswer(i -> i.getArgument(0));

            todoService.updateTodo(1L, "Title 1", null, null, null, null, 0);
            assertEquals(1, todo.getVersion());

            todoService.updateTodo(1L, "Title 2", null, null, null, null, 1);
            assertEquals(2, todo.getVersion());

            todoService.updateTodo(1L, "Title 3", null, null, null, null, 2);
            assertEquals(3, todo.getVersion());
        }

        @Test
        @DisplayName("状态转换_版本号应+1")
        void changeStatus_SuccessfulTransition_ShouldIncrementVersion() {
            Todo todo = new Todo("Test", null, Priority.MEDIUM, null);
            todo.setId(1L);
            todo.setStatus(TodoStatus.PENDING);
            todo.setVersion(0);

            when(todoRepository.findById(1L)).thenReturn(Optional.of(todo));
            when(todoRepository.save(any(Todo.class))).thenAnswer(i -> i.getArgument(0));

            Todo result = todoService.changeStatus(1L, TodoStatus.IN_PROGRESS, Collections.emptySet());

            assertEquals(1, result.getVersion());
            assertEquals(TodoStatus.IN_PROGRESS, result.getStatus());
        }

        @Test
        @DisplayName("添加标签_版本号应+1")
        void addTag_Successful_ShouldIncrementVersion() {
            Todo todo = new Todo("Test", null, Priority.MEDIUM, null);
            todo.setId(1L);
            todo.setVersion(0);

            when(todoRepository.findById(1L)).thenReturn(Optional.of(todo));
            when(todoRepository.save(any(Todo.class))).thenAnswer(i -> i.getArgument(0));

            Todo result = todoService.addTag(1L, "important");

            assertEquals(1, result.getVersion());
            assertTrue(result.getTags().contains("important"));
        }

        @Test
        @DisplayName("添加子任务_父任务版本号应+1")
        void addSubTodo_Successful_ParentShouldIncrementVersion() {
            Todo parentTodo = new Todo("Parent", null, Priority.MEDIUM, null);
            parentTodo.setId(1L);
            parentTodo.setVersion(0);
            Todo subTodo = new Todo("SubTodo", null, Priority.MEDIUM, null);
            subTodo.setId(2L);

            when(todoRepository.findById(1L)).thenReturn(Optional.of(parentTodo));
            when(todoRepository.findById(2L)).thenReturn(Optional.of(subTodo));
            when(todoRepository.save(any(Todo.class))).thenAnswer(i -> i.getArgument(0));

            todoService.addSubTodo(1L, 2L);

            assertEquals(1, parentTodo.getVersion());
            assertTrue(parentTodo.getSubTodoIds().contains(2L));
        }
    }

    @Nested
    @DisplayName("过期优先级升级测试")
    class ExpiredPriorityUpgradeTests {

        @Test
        @DisplayName("升级过期任务_应升级优先级")
        void upgradeExpiredTodosPriority_ShouldUpgradePriority() {
            Todo todo1 = new Todo("Expired 1", null, Priority.LOW, null);
            todo1.setDueDate(LocalDateTime.now().minusDays(2));
            todo1.setStatus(TodoStatus.PENDING);

            Todo todo2 = new Todo("Expired 2", null, Priority.MEDIUM, null);
            todo2.setDueDate(LocalDateTime.now().minusDays(3));
            todo2.setStatus(TodoStatus.IN_PROGRESS);

            List<Todo> expiredTodos = Arrays.asList(todo1, todo2);

            when(todoRepository.findTodosNeedingPriorityUpgrade()).thenReturn(expiredTodos);
            when(todoRepository.save(any(Todo.class))).thenAnswer(i -> i.getArgument(0));

            List<Todo> result = todoService.upgradeExpiredTodosPriority();

            assertEquals(2, result.size());
            assertEquals(Priority.MEDIUM, todo1.getPriority());
            assertEquals(Priority.HIGH, todo2.getPriority());
            verify(todoRepository, times(2)).save(any(Todo.class));
        }

        @Test
        @DisplayName("升级过期任务_CRITICAL优先级_不升级")
        void upgradeExpiredTodosPriority_CriticalPriority_NotUpgraded() {
            Todo todo = new Todo("Expired Critical", null, Priority.CRITICAL, null);
            todo.setDueDate(LocalDateTime.now().minusDays(2));
            todo.setStatus(TodoStatus.PENDING);

            when(todoRepository.findTodosNeedingPriorityUpgrade()).thenReturn(Collections.singletonList(todo));

            List<Todo> result = todoService.upgradeExpiredTodosPriority();

            assertEquals(0, result.size());
            verify(todoRepository, never()).save(any(Todo.class));
        }
    }

    @Nested
    @DisplayName("统计信息测试")
    class StatisticsTests {

        @Test
        @DisplayName("获取统计信息_应返回正确统计")
        void getStatistics_ShouldReturnCorrectStats() {
            Todo todo1 = new Todo("Task 1", null, Priority.HIGH, null);
            todo1.setStatus(TodoStatus.PENDING);
            Todo todo2 = new Todo("Task 2", null, Priority.MEDIUM, null);
            todo2.setStatus(TodoStatus.COMPLETED);
            Todo todo3 = new Todo("Task 3", null, Priority.LOW, null);
            todo3.setStatus(TodoStatus.IN_PROGRESS);

            when(todoRepository.findAll()).thenReturn(Arrays.asList(todo1, todo2, todo3));

            Map<String, Object> stats = todoService.getStatistics();

            assertEquals(3, stats.get("total"));
            assertEquals(1L, stats.get("pending"));
            assertEquals(1L, stats.get("completed"));
            assertEquals(1L, stats.get("inProgress"));
            assertNotNull(stats.get("byPriority"));
        }
    }

    @Nested
    @DisplayName("克隆任务测试")
    class CloneTodoTests {

        @Test
        @DisplayName("克隆任务_应创建副本")
        void cloneTodo_ShouldCreateCopy() {
            Todo original = new Todo("Original", "Description", Priority.HIGH, LocalDateTime.now().plusDays(7));
            original.setId(1L);
            original.addTag("work");
            original.addTag("important");

            when(todoRepository.findById(1L)).thenReturn(Optional.of(original));
            when(todoRepository.save(any(Todo.class))).thenAnswer(i -> i.getArgument(0));

            Todo cloned = todoService.cloneTodo(1L);

            assertNotNull(cloned);
            assertEquals("Original (副本)", cloned.getTitle());
            assertEquals("Description", cloned.getDescription());
            assertEquals(Priority.HIGH, cloned.getPriority());
            assertTrue(cloned.getTags().containsAll(Arrays.asList("work", "important")));
            assertNotEquals(original.getId(), cloned.getId());
        }
    }
}
