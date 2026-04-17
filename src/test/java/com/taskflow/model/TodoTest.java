package com.taskflow.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Todo实体类测试")
class TodoTest {

    private static String repeatString(String s, int n) {
        return Stream.generate(() -> s).limit(n).collect(Collectors.joining());
    }

    private static Set<Long> createLongSet(Long... values) {
        Set<Long> set = new HashSet<>();
        Collections.addAll(set, values);
        return set;
    }

    private static Stream<Arguments> provideValidTransitions() {
        return Stream.of(
                Arguments.of(TodoStatus.PENDING, TodoStatus.IN_PROGRESS),
                Arguments.of(TodoStatus.PENDING, TodoStatus.CANCELLED),
                Arguments.of(TodoStatus.IN_PROGRESS, TodoStatus.COMPLETED),
                Arguments.of(TodoStatus.IN_PROGRESS, TodoStatus.PENDING),
                Arguments.of(TodoStatus.IN_PROGRESS, TodoStatus.CANCELLED),
                Arguments.of(TodoStatus.CANCELLED, TodoStatus.PENDING),
                Arguments.of(TodoStatus.EXPIRED, TodoStatus.IN_PROGRESS),
                Arguments.of(TodoStatus.EXPIRED, TodoStatus.CANCELLED)
        );
    }

    private static Stream<Arguments> provideInvalidTransitions() {
        return Stream.of(
                Arguments.of(TodoStatus.PENDING, TodoStatus.COMPLETED),
                Arguments.of(TodoStatus.PENDING, TodoStatus.EXPIRED),
                Arguments.of(TodoStatus.IN_PROGRESS, TodoStatus.EXPIRED),
                Arguments.of(TodoStatus.COMPLETED, TodoStatus.PENDING),
                Arguments.of(TodoStatus.COMPLETED, TodoStatus.IN_PROGRESS),
                Arguments.of(TodoStatus.COMPLETED, TodoStatus.CANCELLED),
                Arguments.of(TodoStatus.COMPLETED, TodoStatus.EXPIRED),
                Arguments.of(TodoStatus.CANCELLED, TodoStatus.IN_PROGRESS),
                Arguments.of(TodoStatus.CANCELLED, TodoStatus.COMPLETED),
                Arguments.of(TodoStatus.CANCELLED, TodoStatus.EXPIRED),
                Arguments.of(TodoStatus.EXPIRED, TodoStatus.COMPLETED),
                Arguments.of(TodoStatus.EXPIRED, TodoStatus.PENDING),
                Arguments.of(TodoStatus.PENDING, TodoStatus.PENDING),
                Arguments.of(TodoStatus.IN_PROGRESS, TodoStatus.IN_PROGRESS),
                Arguments.of(TodoStatus.COMPLETED, TodoStatus.COMPLETED)
        );
    }

    @Nested
    @DisplayName("验证测试")
    class ValidationTests {

        @Test
        @DisplayName("创建Todo_默认值_应正确初始化")
        void createTodo_WithDefaultValues_ShouldInitializeCorrectly() {
            Todo todo = new Todo();

            assertNotNull(todo.getId());
            assertEquals(TodoStatus.PENDING, todo.getStatus());
            assertEquals(Priority.MEDIUM, todo.getPriority());
            assertNotNull(todo.getCreatedAt());
            assertNotNull(todo.getUpdatedAt());
            assertNotNull(todo.getSubTodoIds());
            assertNotNull(todo.getTags());
            assertEquals(0, todo.getVersion());
        }

        @Test
        @DisplayName("验证_标题为空_应抛出异常")
        void validate_WithNullTitle_ShouldThrowException() {
            Todo todo = new Todo();
            todo.setTitle(null);

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, todo::validate);
            assertEquals("标题不能为空", exception.getMessage());
        }

        @Test
        @DisplayName("验证_标题为空字符串_应抛出异常")
        void validate_WithEmptyTitle_ShouldThrowException() {
            Todo todo = new Todo();
            todo.setTitle("   ");

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, todo::validate);
            assertEquals("标题不能为空", exception.getMessage());
        }

        @Test
        @DisplayName("验证_标题超长_应抛出异常")
        void validate_WithTitleTooLong_ShouldThrowException() {
            Todo todo = new Todo();
            todo.setTitle(repeatString("A", Todo.MAX_TITLE_LENGTH + 1));

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, todo::validate);
            assertTrue(exception.getMessage().contains("标题长度不能超过"));
        }

        @Test
        @DisplayName("验证_标题含特殊字符_应成功")
        void validate_WithSpecialCharsInTitle_ShouldSucceed() {
            Todo todo = new Todo();
            todo.setTitle("Task!@#$%^&*()_+-=[]{}|;':\",./<>?");
            todo.validate();

            assertEquals("Task!@#$%^&*()_+-=[]{}|;':\",./<>?", todo.getTitle());
        }

        @Test
        @DisplayName("验证_标题含Unicode字符_应成功")
        void validate_WithUnicodeInTitle_ShouldSucceed() {
            Todo todo = new Todo();
            todo.setTitle("任务🚀中文日本語にほんご한국어ελληνικά");
            todo.validate();

            assertEquals("任务🚀中文日本語にほんご한국어ελληνικά", todo.getTitle());
        }

        @Test
        @DisplayName("验证_标题含SQL注入尝试_应存储但验证成功")
        void validate_WithSQLInjectionAttempt_ShouldSucceed() {
            Todo todo = new Todo();
            todo.setTitle("'; DROP TABLE todos; --");
            todo.validate();

            assertEquals("'; DROP TABLE todos; --", todo.getTitle());
        }

        @Test
        @DisplayName("验证_标题含XSS脚本尝试_应存储但验证成功")
        void validate_WithXSSAttempt_ShouldSucceed() {
            Todo todo = new Todo();
            todo.setTitle("<script>alert('xss')</script>");
            todo.validate();

            assertEquals("<script>alert('xss')</script>", todo.getTitle());
        }

        @Test
        @DisplayName("验证_标题含换行和制表符_应成功")
        void validate_WithNewlinesAndTabsInTitle_ShouldSucceed() {
            Todo todo = new Todo();
            todo.setTitle("Line1\nLine2\tTabbed");
            todo.validate();

            assertEquals("Line1\nLine2\tTabbed", todo.getTitle());
        }

        @Test
        @DisplayName("验证_描述超长_应抛出异常")
        void validate_WithDescriptionTooLong_ShouldThrowException() {
            Todo todo = new Todo("Valid Title", repeatString("A", Todo.MAX_DESCRIPTION_LENGTH + 1), Priority.MEDIUM, null);

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, todo::validate);
            assertTrue(exception.getMessage().contains("描述长度不能超过"));
        }

        @Test
        @DisplayName("验证_描述含特殊字符和Unicode_应成功")
        void validate_WithSpecialCharsAndUnicodeInDescription_ShouldSucceed() {
            Todo todo = new Todo("Valid Title", "详细说明🚀包含!@#$%特殊字符和中文日本語", Priority.MEDIUM, null);
            todo.validate();

            assertEquals("详细说明🚀包含!@#$%特殊字符和中文日本語", todo.getDescription());
        }

        @Test
        @DisplayName("验证_null描述_应成功")
        void validate_WithNullDescription_ShouldSucceed() {
            Todo todo = new Todo("Valid Title", null, Priority.MEDIUM, null);
            todo.validate();

            assertNull(todo.getDescription());
        }

        @Test
        @DisplayName("验证_截止日期早于创建时间_应抛出异常")
        void validate_WithDueDateBeforeCreatedAt_ShouldThrowException() {
            Todo todo = new Todo();
            todo.setTitle("Test");
            todo.setDueDate(todo.getCreatedAt().minusDays(1));

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, todo::validate);
            assertEquals("截止日期不能早于创建时间", exception.getMessage());
        }

        @Test
        @DisplayName("验证_提醒时间晚于截止日期_应抛出异常")
        void validate_WithReminderTimeAfterDueDate_ShouldThrowException() {
            Todo todo = new Todo();
            todo.setTitle("Test");
            LocalDateTime dueDate = todo.getCreatedAt().plusDays(1);
            todo.setDueDate(dueDate);
            todo.setReminderTime(dueDate.plusHours(1));

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, todo::validate);
            assertEquals("提醒时间必须早于截止日期", exception.getMessage());
        }

        @Test
        @DisplayName("验证_标签数量超过上限_应抛出异常")
        void validate_WithTooManyTags_ShouldThrowException() {
            Todo todo = new Todo("Test", null, Priority.MEDIUM, null);
            for (int i = 0; i <= Todo.MAX_TAGS_COUNT; i++) {
                todo.getTags().add("tag" + i);
            }

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, todo::validate);
            assertTrue(exception.getMessage().contains("标签数量不能超过"));
        }

        @Test
        @DisplayName("验证_子任务数量超过上限_应抛出异常")
        void validate_WithTooManySubTodos_ShouldThrowException() {
            Todo todo = new Todo("Test", null, Priority.MEDIUM, null);
            for (int i = 0; i <= Todo.MAX_SUBTODOS_COUNT; i++) {
                todo.getSubTodoIds().add((long) i);
            }

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, todo::validate);
            assertTrue(exception.getMessage().contains("子任务数量不能超过"));
        }

        @Test
        @DisplayName("验证_标题刚好达到最大长度_应成功")
        void validate_WithTitleAtMaxLength_ShouldSucceed() {
            Todo todo = new Todo();
            todo.setTitle(repeatString("A", Todo.MAX_TITLE_LENGTH));

            assertDoesNotThrow(todo::validate);
        }

        @Test
        @DisplayName("验证_标题包含特殊字符_应成功")
        void validate_WithTitleContainingSpecialChars_ShouldSucceed() {
            Todo todo = new Todo();
            todo.setTitle("Task!@#$%^&*()_+-=[]{}|;:,.<>?");

            assertDoesNotThrow(todo::validate);
        }

        @Test
        @DisplayName("验证_标题包含Unicode字符_应成功")
        void validate_WithTitleContainingUnicode_ShouldSucceed() {
            Todo todo = new Todo();
            todo.setTitle("任务名称_包含中文_日本語_한국어_𝓜𝓪𝓽𝓱");

            assertDoesNotThrow(todo::validate);
        }

        @Test
        @DisplayName("验证_标题包含SQL注入字符_应成功")
        void validate_WithTitleContainingSqlInjection_ShouldSucceed() {
            Todo todo = new Todo();
            todo.setTitle("'; DROP TABLE todos; --");

            assertDoesNotThrow(todo::validate);
        }

        @Test
        @DisplayName("验证_标题包含XSS脚本_应成功")
        void validate_WithTitleContainingXss_ShouldSucceed() {
            Todo todo = new Todo();
            todo.setTitle("<script>alert('xss')</script>");

            assertDoesNotThrow(todo::validate);
        }

        @Test
        @DisplayName("验证_截止日期等于创建时间_应成功")
        void validate_WithDueDateEqualToCreatedAt_ShouldSucceed() {
            Todo todo = new Todo();
            todo.setTitle("Test");
            todo.setDueDate(todo.getCreatedAt());

            assertDoesNotThrow(todo::validate);
        }

        @Test
        @DisplayName("验证_提醒时间等于截止日期_应成功")
        void validate_WithReminderTimeEqualToDueDate_ShouldSucceed() {
            Todo todo = new Todo();
            todo.setTitle("Test");
            LocalDateTime dueDate = todo.getCreatedAt().plusDays(1);
            todo.setDueDate(dueDate);
            todo.setReminderTime(dueDate);

            assertDoesNotThrow(todo::validate);
        }
    }

    @Nested
    @DisplayName("状态机转换测试")
    class StateMachineTests {

        @ParameterizedTest
        @MethodSource("com.taskflow.model.TodoTest#provideValidTransitions")
        @DisplayName("状态转换_合法转换_应返回true")
        void canTransitionTo_WithValidTransition_ShouldReturnTrue(TodoStatus fromStatus, TodoStatus toStatus) {
            Todo todo = new Todo();
            todo.setStatus(fromStatus);

            assertTrue(todo.canTransitionTo(toStatus));
        }

        @ParameterizedTest
        @MethodSource("com.taskflow.model.TodoTest#provideInvalidTransitions")
        @DisplayName("状态转换_非法转换_应返回false")
        void canTransitionTo_WithInvalidTransition_ShouldReturnFalse(TodoStatus fromStatus, TodoStatus toStatus) {
            Todo todo = new Todo();
            todo.setStatus(fromStatus);

            assertFalse(todo.canTransitionTo(toStatus));
        }

        @ParameterizedTest
        @MethodSource("com.taskflow.model.TodoTest#provideValidTransitions")
        @DisplayName("执行转换_合法转换_应成功")
        void transitionTo_WithValidTransition_ShouldSucceed(TodoStatus fromStatus, TodoStatus toStatus) {
            Todo todo = new Todo();
            todo.setStatus(fromStatus);
            int initialVersion = todo.getVersion();

            todo.transitionTo(toStatus);

            assertEquals(toStatus, todo.getStatus());
            assertEquals(initialVersion + 1, todo.getVersion());
        }

        @ParameterizedTest
        @MethodSource("com.taskflow.model.TodoTest#provideInvalidTransitions")
        @DisplayName("执行转换_非法转换_应抛出异常")
        void transitionTo_WithInvalidTransition_ShouldThrowException(TodoStatus fromStatus, TodoStatus toStatus) {
            Todo todo = new Todo();
            todo.setStatus(fromStatus);

            IllegalStateException exception = assertThrows(IllegalStateException.class, () -> todo.transitionTo(toStatus));
            assertTrue(exception.getMessage().contains("不允许从"));
        }
    }

    @Nested
    @DisplayName("时间逻辑测试")
    class TimeLogicTests {

        @Test
        @DisplayName("是否过期_无截止日期_应返回false")
        void isExpired_WithoutDueDate_ShouldReturnFalse() {
            Todo todo = new Todo("Test", null, Priority.MEDIUM, null);

            assertFalse(todo.isExpired());
        }

        @Test
        @DisplayName("是否过期_截止日期在未来_应返回false")
        void isExpired_WithFutureDueDate_ShouldReturnFalse() {
            Todo todo = new Todo("Test", null, Priority.MEDIUM, LocalDateTime.now().plusDays(1));

            assertFalse(todo.isExpired());
        }

        @Test
        @DisplayName("是否过期_截止日期在过去_应返回true")
        void isExpired_WithPastDueDate_ShouldReturnTrue() {
            Todo todo = new Todo();
            todo.setTitle("Test");
            todo.setDueDate(LocalDateTime.now().minusDays(1));

            assertTrue(todo.isExpired());
        }

        @Test
        @DisplayName("是否升级优先级_未过期_应返回false")
        void shouldUpgradePriority_NotExpired_ShouldReturnFalse() {
            Todo todo = new Todo("Test", null, Priority.MEDIUM, LocalDateTime.now().plusDays(1));

            assertFalse(todo.shouldUpgradePriority());
        }

        @Test
        @DisplayName("是否升级优先级_已完成_应返回false")
        void shouldUpgradePriority_CompletedStatus_ShouldReturnFalse() {
            Todo todo = new Todo();
            todo.setTitle("Test");
            todo.setStatus(TodoStatus.COMPLETED);
            todo.setDueDate(LocalDateTime.now().minusDays(3));

            assertFalse(todo.shouldUpgradePriority());
        }

        @Test
        @DisplayName("是否升级优先级_已取消_应返回false")
        void shouldUpgradePriority_CancelledStatus_ShouldReturnFalse() {
            Todo todo = new Todo();
            todo.setTitle("Test");
            todo.setStatus(TodoStatus.CANCELLED);
            todo.setDueDate(LocalDateTime.now().minusDays(3));

            assertFalse(todo.shouldUpgradePriority());
        }

        @Test
        @DisplayName("是否升级优先级_过期不足24小时_应返回false")
        void shouldUpgradePriority_ExpiredLessThan24Hours_ShouldReturnFalse() {
            Todo todo = new Todo();
            todo.setTitle("Test");
            todo.setDueDate(LocalDateTime.now().minusHours(12));

            assertFalse(todo.shouldUpgradePriority());
        }

        @Test
        @DisplayName("是否升级优先级_过期超过24小时_应返回true")
        void shouldUpgradePriority_ExpiredMoreThan24Hours_ShouldReturnTrue() {
            Todo todo = new Todo();
            todo.setTitle("Test");
            todo.setDueDate(LocalDateTime.now().minusHours(25));

            assertTrue(todo.shouldUpgradePriority());
        }

        @Test
        @DisplayName("升级优先级_从LOW升级_应变为MEDIUM")
        void upgradePriority_FromLow_ShouldBecomeMedium() {
            Todo todo = new Todo("Test", null, Priority.LOW, null);
            int initialVersion = todo.getVersion();

            todo.upgradePriority();

            assertEquals(Priority.MEDIUM, todo.getPriority());
            assertEquals(initialVersion + 1, todo.getVersion());
        }

        @Test
        @DisplayName("升级优先级_从MEDIUM升级_应变为HIGH")
        void upgradePriority_FromMedium_ShouldBecomeHigh() {
            Todo todo = new Todo("Test", null, Priority.MEDIUM, null);

            todo.upgradePriority();

            assertEquals(Priority.HIGH, todo.getPriority());
        }

        @Test
        @DisplayName("升级优先级_从HIGH升级_应变为CRITICAL")
        void upgradePriority_FromHigh_ShouldBecomeCritical() {
            Todo todo = new Todo("Test", null, Priority.HIGH, null);

            todo.upgradePriority();

            assertEquals(Priority.CRITICAL, todo.getPriority());
        }

        @Test
        @DisplayName("升级优先级_从CRITICAL升级_应保持CRITICAL")
        void upgradePriority_FromCritical_ShouldRemainCritical() {
            Todo todo = new Todo("Test", null, Priority.CRITICAL, null);
            int initialVersion = todo.getVersion();

            todo.upgradePriority();

            assertEquals(Priority.CRITICAL, todo.getPriority());
            assertEquals(initialVersion, todo.getVersion());
        }
    }

    @Nested
    @DisplayName("标签管理测试")
    class TagManagementTests {

        @Test
        @DisplayName("添加标签_合法标签_应成功")
        void addTag_ValidTag_ShouldSucceed() {
            Todo todo = new Todo("Test", null, Priority.MEDIUM, null);
            int initialVersion = todo.getVersion();

            todo.addTag("Urgent");

            assertTrue(todo.getTags().contains("urgent"));
            assertEquals(initialVersion + 1, todo.getVersion());
        }

        @Test
        @DisplayName("添加标签_空标签_应抛出异常")
        void addTag_NullTag_ShouldThrowException() {
            Todo todo = new Todo("Test", null, Priority.MEDIUM, null);

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> todo.addTag(null));
            assertEquals("标签不能为空", exception.getMessage());
        }

        @Test
        @DisplayName("添加标签_重复标签_应抛出异常")
        void addTag_DuplicateTag_ShouldThrowException() {
            Todo todo = new Todo("Test", null, Priority.MEDIUM, null);
            todo.addTag("work");

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> todo.addTag("WORK"));
            assertTrue(exception.getMessage().contains("标签已存在"));
        }

        @Test
        @DisplayName("移除标签_存在的标签_应成功")
        void removeTag_ExistingTag_ShouldSucceed() {
            Todo todo = new Todo("Test", null, Priority.MEDIUM, null);
            todo.addTag("work");
            int versionBeforeRemove = todo.getVersion();

            todo.removeTag("WORK");

            assertFalse(todo.getTags().contains("work"));
            assertEquals(versionBeforeRemove + 1, todo.getVersion());
        }

        @Test
        @DisplayName("添加标签_标签含特殊字符_应成功")
        void addTag_WithSpecialChars_ShouldSucceed() {
            Todo todo = new Todo("Test", null, Priority.MEDIUM, null);

            todo.addTag("urgent-important!");

            assertTrue(todo.getTags().contains("urgent-important!"));
        }

        @Test
        @DisplayName("添加标签_大小写不同_视为同一标签并抛出异常")
        void addTag_DifferentCase_ShouldTreatAsSame() {
            Todo todo = new Todo("Test", null, Priority.MEDIUM, null);
            todo.addTag("WORK");

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> todo.addTag("work"));
            assertTrue(exception.getMessage().contains("标签已存在"));
        }

        @Test
        @DisplayName("添加标签_空白标签_应抛出异常")
        void addTag_EmptyOrWhitespace_ShouldThrowException() {
            Todo todo = new Todo("Test", null, Priority.MEDIUM, null);

            assertThrows(IllegalArgumentException.class, () -> todo.addTag(""));
            assertThrows(IllegalArgumentException.class, () -> todo.addTag("   "));
            assertThrows(IllegalArgumentException.class, () -> todo.addTag(null));
        }

        @Test
        @DisplayName("移除标签_null_应抛出异常")
        void removeTag_Null_ShouldThrowException() {
            Todo todo = new Todo("Test", null, Priority.MEDIUM, null);

            assertThrows(IllegalArgumentException.class, () -> todo.removeTag(null));
        }

        @Test
        @DisplayName("移除标签_不存在的标签_版本号不变")
        void removeTag_NonExistent_ShouldNotChangeVersion() {
            Todo todo = new Todo("Test", null, Priority.MEDIUM, null);
            int versionBefore = todo.getVersion();

            todo.removeTag("nonexistent");

            assertEquals(versionBefore, todo.getVersion());
        }
    }

    @Nested
    @DisplayName("子任务管理测试")
    class SubTodoManagementTests {

        @Test
        @DisplayName("添加子任务_有效ID_应成功")
        void addSubTodo_ValidId_ShouldSucceed() {
            Todo todo = new Todo("Test", null, Priority.MEDIUM, null);
            Long subTodoId = 999L;
            int initialVersion = todo.getVersion();

            todo.addSubTodo(subTodoId);

            assertTrue(todo.getSubTodoIds().contains(subTodoId));
            assertEquals(initialVersion + 1, todo.getVersion());
        }

        @Test
        @DisplayName("添加子任务_nullID_应抛出异常")
        void addSubTodo_NullId_ShouldThrowException() {
            Todo todo = new Todo("Test", null, Priority.MEDIUM, null);

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> todo.addSubTodo(null));
            assertEquals("子任务ID不能为空", exception.getMessage());
        }

        @Test
        @DisplayName("添加子任务_自己为子任务_应抛出异常")
        void addSubTodo_SelfReference_ShouldThrowException() {
            Todo todo = new Todo("Test", null, Priority.MEDIUM, null);

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> todo.addSubTodo(todo.getId()));
            assertEquals("不能将自己添加为子任务", exception.getMessage());
        }

        @Test
        @DisplayName("添加子任务_重复ID_应抛出异常")
        void addSubTodo_DuplicateId_ShouldThrowException() {
            Todo todo = new Todo("Test", null, Priority.MEDIUM, null);
            Long subTodoId = 999L;
            todo.addSubTodo(subTodoId);

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> todo.addSubTodo(subTodoId));
            assertEquals("子任务已存在", exception.getMessage());
        }

        @Test
        @DisplayName("可以完成_无子任务_应返回true")
        void canComplete_WithoutSubTodos_ShouldReturnTrue() {
            Todo todo = new Todo("Test", null, Priority.MEDIUM, null);
            todo.setStatus(TodoStatus.IN_PROGRESS);

            assertTrue(todo.canComplete(Collections.emptySet()));
        }

        @Test
        @DisplayName("可以完成_所有子任务已完成_应返回true")
        void canComplete_WithAllSubTodosCompleted_ShouldReturnTrue() {
            Todo todo = new Todo("Parent", null, Priority.MEDIUM, null);
            todo.setStatus(TodoStatus.IN_PROGRESS);
            todo.addSubTodo(1L);
            todo.addSubTodo(2L);

            assertTrue(todo.canComplete(createLongSet(1L, 2L)));
        }

        @Test
        @DisplayName("可以完成_有未完成子任务_应返回false")
        void canComplete_WithIncompleteSubTodos_ShouldReturnFalse() {
            Todo todo = new Todo("Parent", null, Priority.MEDIUM, null);
            todo.setStatus(TodoStatus.IN_PROGRESS);
            todo.addSubTodo(1L);
            todo.addSubTodo(2L);

            assertFalse(todo.canComplete(createLongSet(1L)));
        }

        @Test
        @DisplayName("可以完成_状态错误_应返回false")
        void canComplete_WithWrongStatus_ShouldReturnFalse() {
            Todo todo = new Todo("Test", null, Priority.MEDIUM, null);
            todo.setStatus(TodoStatus.COMPLETED);

            assertFalse(todo.canComplete(Collections.emptySet()));
        }
    }

    @Nested
    @DisplayName("更新测试")
    class UpdateTests {

        @Test
        @DisplayName("更新字段_全部更新_应成功")
        void update_AllFields_ShouldSucceed() {
            Todo todo = new Todo("Original Title", "Original Description", Priority.LOW, LocalDateTime.now().plusDays(7));
            int initialVersion = todo.getVersion();

            String newTitle = "New Title";
            String newDescription = "New Description";
            Priority newPriority = Priority.HIGH;
            LocalDateTime newDueDate = LocalDateTime.now().plusDays(14);
            LocalDateTime newReminderTime = LocalDateTime.now().plusDays(13);

            todo.update(newTitle, newDescription, newPriority, newDueDate, newReminderTime);

            assertEquals(newTitle, todo.getTitle());
            assertEquals(newDescription, todo.getDescription());
            assertEquals(newPriority, todo.getPriority());
            assertEquals(newDueDate, todo.getDueDate());
            assertEquals(newReminderTime, todo.getReminderTime());
            assertEquals(initialVersion + 1, todo.getVersion());
        }
    }
}
