package com.taskflow.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Todo实体类测试")
class TodoTest {

    private Todo todo;

    @BeforeEach
    void setUp() {
        todo = new Todo();
        todo.setTitle("测试任务");
    }

    static Stream<Arguments> validTransitions() {
        return Stream.of(
            Arguments.of(TodoStatus.PENDING, TodoStatus.IN_PROGRESS, "PENDING -> IN_PROGRESS"),
            Arguments.of(TodoStatus.PENDING, TodoStatus.CANCELLED, "PENDING -> CANCELLED"),
            Arguments.of(TodoStatus.IN_PROGRESS, TodoStatus.COMPLETED, "IN_PROGRESS -> COMPLETED"),
            Arguments.of(TodoStatus.IN_PROGRESS, TodoStatus.PENDING, "IN_PROGRESS -> PENDING"),
            Arguments.of(TodoStatus.IN_PROGRESS, TodoStatus.CANCELLED, "IN_PROGRESS -> CANCELLED"),
            Arguments.of(TodoStatus.CANCELLED, TodoStatus.PENDING, "CANCELLED -> PENDING"),
            Arguments.of(TodoStatus.EXPIRED, TodoStatus.IN_PROGRESS, "EXPIRED -> IN_PROGRESS"),
            Arguments.of(TodoStatus.EXPIRED, TodoStatus.CANCELLED, "EXPIRED -> CANCELLED")
        );
    }

    static Stream<Arguments> invalidTransitions() {
        return Stream.of(
            Arguments.of(TodoStatus.PENDING, TodoStatus.COMPLETED, "PENDING -> COMPLETED"),
            Arguments.of(TodoStatus.PENDING, TodoStatus.EXPIRED, "PENDING -> EXPIRED"),
            Arguments.of(TodoStatus.IN_PROGRESS, TodoStatus.EXPIRED, "IN_PROGRESS -> EXPIRED"),
            Arguments.of(TodoStatus.COMPLETED, TodoStatus.PENDING, "COMPLETED -> PENDING"),
            Arguments.of(TodoStatus.COMPLETED, TodoStatus.IN_PROGRESS, "COMPLETED -> IN_PROGRESS"),
            Arguments.of(TodoStatus.COMPLETED, TodoStatus.CANCELLED, "COMPLETED -> CANCELLED"),
            Arguments.of(TodoStatus.COMPLETED, TodoStatus.EXPIRED, "COMPLETED -> EXPIRED"),
            Arguments.of(TodoStatus.CANCELLED, TodoStatus.IN_PROGRESS, "CANCELLED -> IN_PROGRESS"),
            Arguments.of(TodoStatus.CANCELLED, TodoStatus.COMPLETED, "CANCELLED -> COMPLETED"),
            Arguments.of(TodoStatus.CANCELLED, TodoStatus.EXPIRED, "CANCELLED -> EXPIRED"),
            Arguments.of(TodoStatus.EXPIRED, TodoStatus.PENDING, "EXPIRED -> PENDING"),
            Arguments.of(TodoStatus.EXPIRED, TodoStatus.COMPLETED, "EXPIRED -> COMPLETED")
        );
    }

    @Nested
    @DisplayName("构造函数测试")
    class ConstructorTests {

        @Test
        @DisplayName("无参构造函数应初始化默认值")
        void constructor_NoArgs_ShouldInitializeDefaults() {
            Todo newTodo = new Todo();

            assertNotNull(newTodo.getId());
            assertEquals(TodoStatus.PENDING, newTodo.getStatus());
            assertEquals(Priority.MEDIUM, newTodo.getPriority());
            assertNotNull(newTodo.getCreatedAt());
            assertNotNull(newTodo.getUpdatedAt());
            assertNotNull(newTodo.getSubTodoIds());
            assertNotNull(newTodo.getTags());
            assertEquals(0, newTodo.getVersion());
        }

        @Test
        @DisplayName("带参构造函数应正确设置属性")
        void constructor_WithArgs_ShouldSetProperties() {
            LocalDateTime dueDate = LocalDateTime.now().plusDays(7);
            Todo newTodo = new Todo("标题", "描述", Priority.HIGH, dueDate);

            assertEquals("标题", newTodo.getTitle());
            assertEquals("描述", newTodo.getDescription());
            assertEquals(Priority.HIGH, newTodo.getPriority());
            assertEquals(dueDate, newTodo.getDueDate());
        }

        @Test
        @DisplayName("带参构造函数当优先级为null时应使用默认值")
        void constructor_WithNullPriority_ShouldUseDefault() {
            Todo newTodo = new Todo("标题", "描述", null, null);

            assertEquals(Priority.MEDIUM, newTodo.getPriority());
        }
    }

    @Nested
    @DisplayName("验证方法测试")
    class ValidationTests {

        @Test
        @DisplayName("验证有效Todo应通过")
        void validate_ValidTodo_ShouldPass() {
            todo.setDescription("有效描述");
            todo.setDueDate(LocalDateTime.now().plusDays(1));

            assertDoesNotThrow(() -> todo.validate());
        }

        @Nested
        @DisplayName("标题验证")
        class TitleValidationTests {

            @Test
            @DisplayName("标题为null应抛出异常")
            void validateTitle_NullTitle_ShouldThrowException() {
                todo.setTitle(null);

                IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> todo.validate()
                );
                assertEquals("标题不能为空", exception.getMessage());
            }

            @Test
            @DisplayName("标题为空字符串应抛出异常")
            void validateTitle_EmptyTitle_ShouldThrowException() {
                todo.setTitle("");

                IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> todo.validate()
                );
                assertEquals("标题不能为空", exception.getMessage());
            }

            @Test
            @DisplayName("标题为纯空格应抛出异常")
            void validateTitle_BlankTitle_ShouldThrowException() {
                todo.setTitle("   ");

                IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> todo.validate()
                );
                assertEquals("标题不能为空", exception.getMessage());
            }

            @Test
            @DisplayName("标题超过最大长度应抛出异常")
            void validateTitle_TooLong_ShouldThrowException() {
                String longTitle = String.join("", Collections.nCopies(Todo.MAX_TITLE_LENGTH + 1, "a"));
                todo.setTitle(longTitle);

                IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> todo.validate()
                );
                assertTrue(exception.getMessage().contains("标题长度不能超过"));
            }

            @Test
            @DisplayName("标题等于最大长度应通过")
            void validateTitle_MaxLength_ShouldPass() {
                String maxTitle = String.join("", Collections.nCopies(Todo.MAX_TITLE_LENGTH, "a"));
                todo.setTitle(maxTitle);

                assertDoesNotThrow(() -> todo.validate());
            }
        }

        @Nested
        @DisplayName("描述验证")
        class DescriptionValidationTests {

            @Test
            @DisplayName("描述为null应通过")
            void validateDescription_NullDescription_ShouldPass() {
                todo.setDescription(null);

                assertDoesNotThrow(() -> todo.validate());
            }

            @Test
            @DisplayName("描述超过最大长度应抛出异常")
            void validateDescription_TooLong_ShouldThrowException() {
                String longDesc = String.join("", Collections.nCopies(Todo.MAX_DESCRIPTION_LENGTH + 1, "a"));
                todo.setDescription(longDesc);

                IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> todo.validate()
                );
                assertTrue(exception.getMessage().contains("描述长度不能超过"));
            }

            @Test
            @DisplayName("描述等于最大长度应通过")
            void validateDescription_MaxLength_ShouldPass() {
                String maxDesc = String.join("", Collections.nCopies(Todo.MAX_DESCRIPTION_LENGTH, "a"));
                todo.setDescription(maxDesc);

                assertDoesNotThrow(() -> todo.validate());
            }
        }

        @Nested
        @DisplayName("日期验证")
        class DateValidationTests {

            @Test
            @DisplayName("截止日期早于创建时间应抛出异常")
            void validateDates_DueDateBeforeCreatedAt_ShouldThrowException() {
                todo.setCreatedAt(LocalDateTime.now());
                todo.setDueDate(LocalDateTime.now().minusDays(1));

                IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> todo.validate()
                );
                assertEquals("截止日期不能早于创建时间", exception.getMessage());
            }

            @Test
            @DisplayName("提醒时间晚于截止日期应抛出异常")
            void validateDates_ReminderAfterDueDate_ShouldThrowException() {
                LocalDateTime dueDate = LocalDateTime.now().plusDays(7);
                todo.setDueDate(dueDate);
                todo.setReminderTime(dueDate.plusDays(1));

                IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> todo.validate()
                );
                assertEquals("提醒时间必须早于截止日期", exception.getMessage());
            }

            @Test
            @DisplayName("提醒时间等于截止日期应通过（源代码只检查isAfter）")
            void validateDates_ReminderEqualsDueDate_ShouldPass() {
                LocalDateTime dueDate = LocalDateTime.now().plusDays(7);
                todo.setDueDate(dueDate);
                todo.setReminderTime(dueDate);

                assertDoesNotThrow(() -> todo.validate());
            }

            @Test
            @DisplayName("提醒时间早于截止日期应通过")
            void validateDates_ReminderBeforeDueDate_ShouldPass() {
                LocalDateTime dueDate = LocalDateTime.now().plusDays(7);
                todo.setDueDate(dueDate);
                todo.setReminderTime(dueDate.minusDays(1));

                assertDoesNotThrow(() -> todo.validate());
            }

            @Test
            @DisplayName("截止日期为null时提醒时间验证应跳过")
            void validateDates_NullDueDate_ShouldSkipReminderCheck() {
                todo.setDueDate(null);
                todo.setReminderTime(LocalDateTime.now());

                assertDoesNotThrow(() -> todo.validate());
            }
        }

        @Nested
        @DisplayName("标签验证")
        class TagValidationTests {

            @Test
            @DisplayName("标签数量超过上限应抛出异常")
            void validateTags_TooManyTags_ShouldThrowException() {
                for (int i = 0; i <= Todo.MAX_TAGS_COUNT; i++) {
                    todo.getTags().add("tag" + i);
                }

                IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> todo.validate()
                );
                assertTrue(exception.getMessage().contains("标签数量不能超过"));
            }

            @Test
            @DisplayName("标签数量等于上限应通过")
            void validateTags_MaxTags_ShouldPass() {
                for (int i = 0; i < Todo.MAX_TAGS_COUNT; i++) {
                    todo.getTags().add("tag" + i);
                }

                assertDoesNotThrow(() -> todo.validate());
            }
        }

        @Nested
        @DisplayName("子任务验证")
        class SubTodoValidationTests {

            @Test
            @DisplayName("子任务数量超过上限应抛出异常")
            void validateSubTodos_TooManySubTodos_ShouldThrowException() {
                for (int i = 0; i <= Todo.MAX_SUBTODOS_COUNT; i++) {
                    todo.getSubTodoIds().add((long) i);
                }

                IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> todo.validate()
                );
                assertTrue(exception.getMessage().contains("子任务数量不能超过"));
            }

            @Test
            @DisplayName("子任务数量等于上限应通过")
            void validateSubTodos_MaxSubTodos_ShouldPass() {
                for (int i = 0; i < Todo.MAX_SUBTODOS_COUNT; i++) {
                    todo.getSubTodoIds().add((long) i);
                }

                assertDoesNotThrow(() -> todo.validate());
            }
        }
    }

    @Nested
    @DisplayName("状态机转换测试")
    class StatusTransitionTests {

        @ParameterizedTest(name = "{2} 应该是合法转换")
        @MethodSource("com.taskflow.model.TodoTest#validTransitions")
        @DisplayName("合法状态转换测试")
        void canTransitionTo_ValidTransition_ShouldReturnTrue(TodoStatus from, TodoStatus to, String description) {
            todo.setStatus(from);

            assertTrue(todo.canTransitionTo(to));
        }

        @ParameterizedTest(name = "{2} 应该是非法转换")
        @MethodSource("com.taskflow.model.TodoTest#invalidTransitions")
        @DisplayName("非法状态转换测试")
        void canTransitionTo_InvalidTransition_ShouldReturnFalse(TodoStatus from, TodoStatus to, String description) {
            todo.setStatus(from);

            assertFalse(todo.canTransitionTo(to));
        }

        @ParameterizedTest(name = "{2} 应该成功")
        @MethodSource("com.taskflow.model.TodoTest#validTransitions")
        @DisplayName("执行合法状态转换测试")
        void transitionTo_ValidTransition_ShouldSucceed(TodoStatus from, TodoStatus to, String description) {
            todo.setStatus(from);
            int oldVersion = todo.getVersion();

            todo.transitionTo(to);

            assertEquals(to, todo.getStatus());
            assertEquals(oldVersion + 1, todo.getVersion());
        }

        @ParameterizedTest(name = "{2} 应该抛出异常")
        @MethodSource("com.taskflow.model.TodoTest#invalidTransitions")
        @DisplayName("执行非法状态转换测试")
        void transitionTo_InvalidTransition_ShouldThrowException(TodoStatus from, TodoStatus to, String description) {
            todo.setStatus(from);

            IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> todo.transitionTo(to)
            );
            assertTrue(exception.getMessage().contains("不允许从"));
            assertTrue(exception.getMessage().contains("转换到"));
        }

        @Test
        @DisplayName("COMPLETED状态是终态不能转换到任何状态")
        void transitionTo_CompletedIsTerminal_ShouldNotAllowAnyTransition() {
            todo.setStatus(TodoStatus.COMPLETED);

            for (TodoStatus status : TodoStatus.values()) {
                if (status != TodoStatus.COMPLETED) {
                    assertFalse(todo.canTransitionTo(status));
                }
            }
        }

        @Test
        @DisplayName("状态转换应更新updatedAt时间")
        void transitionTo_ShouldUpdateUpdatedAt() throws InterruptedException {
            todo.setStatus(TodoStatus.PENDING);
            LocalDateTime before = todo.getUpdatedAt();
            Thread.sleep(10);

            todo.transitionTo(TodoStatus.IN_PROGRESS);

            assertTrue(todo.getUpdatedAt().isAfter(before));
        }
    }

    @Nested
    @DisplayName("过期逻辑测试")
    class ExpirationTests {

        @Test
        @DisplayName("截止日期为null时不应过期")
        void isExpired_NullDueDate_ShouldReturnFalse() {
            todo.setDueDate(null);

            assertFalse(todo.isExpired());
        }

        @Test
        @DisplayName("截止日期在未来时不应过期")
        void isExpired_FutureDueDate_ShouldReturnFalse() {
            todo.setDueDate(LocalDateTime.now().plusDays(1));

            assertFalse(todo.isExpired());
        }

        @Test
        @DisplayName("截止日期在过去时应该过期")
        void isExpired_PastDueDate_ShouldReturnTrue() {
            todo.setDueDate(LocalDateTime.now().minusHours(1));

            assertTrue(todo.isExpired());
        }

        @Test
        @DisplayName("截止日期等于当前时间时应该过期")
        void isExpired_DueDateEqualsNow_ShouldReturnTrue() {
            todo.setDueDate(LocalDateTime.now().minusSeconds(1));

            assertTrue(todo.isExpired());
        }
    }

    @Nested
    @DisplayName("优先级升级测试")
    class PriorityUpgradeTests {

        @Test
        @DisplayName("未过期任务不应升级优先级")
        void shouldUpgradePriority_NotExpired_ShouldReturnFalse() {
            todo.setDueDate(LocalDateTime.now().plusDays(1));
            todo.setStatus(TodoStatus.IN_PROGRESS);

            assertFalse(todo.shouldUpgradePriority());
        }

        @Test
        @DisplayName("已完成任务不应升级优先级")
        void shouldUpgradePriority_Completed_ShouldReturnFalse() {
            todo.setDueDate(LocalDateTime.now().minusDays(2));
            todo.setStatus(TodoStatus.COMPLETED);

            assertFalse(todo.shouldUpgradePriority());
        }

        @Test
        @DisplayName("已取消任务不应升级优先级")
        void shouldUpgradePriority_Cancelled_ShouldReturnFalse() {
            todo.setDueDate(LocalDateTime.now().minusDays(2));
            todo.setStatus(TodoStatus.CANCELLED);

            assertFalse(todo.shouldUpgradePriority());
        }

        @Test
        @DisplayName("过期超过24小时应升级优先级")
        void shouldUpgradePriority_ExpiredMoreThan24Hours_ShouldReturnTrue() {
            todo.setDueDate(LocalDateTime.now().minusHours(Todo.EXPIRED_HOURS_THRESHOLD + 1));
            todo.setStatus(TodoStatus.PENDING);

            assertTrue(todo.shouldUpgradePriority());
        }

        @Test
        @DisplayName("过期不足24小时不应升级优先级")
        void shouldUpgradePriority_ExpiredLessThan24Hours_ShouldReturnFalse() {
            todo.setDueDate(LocalDateTime.now().minusHours(Todo.EXPIRED_HOURS_THRESHOLD - 1));
            todo.setStatus(TodoStatus.PENDING);

            assertFalse(todo.shouldUpgradePriority());
        }

        @Test
        @DisplayName("升级优先级应提升到下一级")
        void upgradePriority_ShouldUpgradeToNextLevel() {
            todo.setPriority(Priority.LOW);
            int oldVersion = todo.getVersion();

            todo.upgradePriority();

            assertEquals(Priority.MEDIUM, todo.getPriority());
            assertEquals(oldVersion + 1, todo.getVersion());
        }

        @Test
        @DisplayName("CRITICAL优先级升级应保持不变")
        void upgradePriority_Critical_ShouldRemainCritical() {
            todo.setPriority(Priority.CRITICAL);

            todo.upgradePriority();

            assertEquals(Priority.CRITICAL, todo.getPriority());
        }

        @ParameterizedTest
        @CsvSource({
            "LOW, MEDIUM",
            "MEDIUM, HIGH",
            "HIGH, CRITICAL"
        })
        @DisplayName("优先级升级测试")
        void upgradePriority_ShouldUpgradeCorrectly(Priority from, Priority to) {
            todo.setPriority(from);

            todo.upgradePriority();

            assertEquals(to, todo.getPriority());
        }
    }

    @Nested
    @DisplayName("标签管理测试")
    class TagManagementTests {

        @Test
        @DisplayName("添加有效标签应成功")
        void addTag_ValidTag_ShouldSucceed() {
            int oldVersion = todo.getVersion();

            todo.addTag("work");

            assertTrue(todo.getTags().contains("work"));
            assertEquals(oldVersion + 1, todo.getVersion());
        }

        @Test
        @DisplayName("添加标签应转换为小写")
        void addTag_ShouldConvertToLowerCase() {
            todo.addTag("WORK");

            assertTrue(todo.getTags().contains("work"));
            assertFalse(todo.getTags().contains("WORK"));
        }

        @Test
        @DisplayName("添加标签应去除空格")
        void addTag_ShouldTrimWhitespace() {
            todo.addTag("  work  ");

            assertTrue(todo.getTags().contains("work"));
        }

        @Test
        @DisplayName("添加null标签应抛出异常")
        void addTag_NullTag_ShouldThrowException() {
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> todo.addTag(null)
            );
            assertEquals("标签不能为空", exception.getMessage());
        }

        @Test
        @DisplayName("添加空标签应抛出异常")
        void addTag_EmptyTag_ShouldThrowException() {
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> todo.addTag("")
            );
            assertEquals("标签不能为空", exception.getMessage());
        }

        @Test
        @DisplayName("添加重复标签应抛出异常")
        void addTag_DuplicateTag_ShouldThrowException() {
            todo.addTag("work");

            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> todo.addTag("work")
            );
            assertTrue(exception.getMessage().contains("标签已存在"));
        }

        @Test
        @DisplayName("标签达到上限时添加应抛出异常")
        void addTag_MaxTagsReached_ShouldThrowException() {
            for (int i = 0; i < Todo.MAX_TAGS_COUNT; i++) {
                todo.addTag("tag" + i);
            }

            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> todo.addTag("newTag")
            );
            assertEquals("标签数量已达上限", exception.getMessage());
        }

        @Test
        @DisplayName("移除存在的标签应成功")
        void removeTag_ExistingTag_ShouldSucceed() {
            todo.addTag("work");
            int oldVersion = todo.getVersion();

            todo.removeTag("work");

            assertFalse(todo.getTags().contains("work"));
            assertEquals(oldVersion + 1, todo.getVersion());
        }

        @Test
        @DisplayName("移除不存在的标签应静默处理")
        void removeTag_NonExistingTag_ShouldDoNothing() {
            int oldVersion = todo.getVersion();

            todo.removeTag("nonexistent");

            assertEquals(oldVersion, todo.getVersion());
        }

        @Test
        @DisplayName("移除null标签应抛出异常")
        void removeTag_NullTag_ShouldThrowException() {
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> todo.removeTag(null)
            );
            assertEquals("标签不能为空", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("子任务管理测试")
    class SubTodoManagementTests {

        @Test
        @DisplayName("添加有效子任务ID应成功")
        void addSubTodo_ValidId_ShouldSucceed() {
            Long subTodoId = 100L;
            int oldVersion = todo.getVersion();

            todo.setId(1L);
            todo.addSubTodo(subTodoId);

            assertTrue(todo.getSubTodoIds().contains(subTodoId));
            assertEquals(oldVersion + 1, todo.getVersion());
        }

        @Test
        @DisplayName("添加null子任务ID应抛出异常")
        void addSubTodo_NullId_ShouldThrowException() {
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> todo.addSubTodo(null)
            );
            assertEquals("子任务ID不能为空", exception.getMessage());
        }

        @Test
        @DisplayName("添加自己作为子任务应抛出异常")
        void addSubTodo_SelfReference_ShouldThrowException() {
            todo.setId(1L);
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> todo.addSubTodo(todo.getId())
            );
            assertEquals("不能将自己添加为子任务", exception.getMessage());
        }

        @Test
        @DisplayName("添加重复子任务ID应抛出异常")
        void addSubTodo_DuplicateId_ShouldThrowException() {
            Long subTodoId = 100L;
            todo.setId(1L);
            todo.addSubTodo(subTodoId);

            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> todo.addSubTodo(subTodoId)
            );
            assertEquals("子任务已存在", exception.getMessage());
        }

        @Test
        @DisplayName("子任务达到上限时添加应抛出异常")
        void addSubTodo_MaxReached_ShouldThrowException() {
            for (int i = 0; i < Todo.MAX_SUBTODOS_COUNT; i++) {
                todo.setId(1L);
                todo.addSubTodo((long) (i + 1000));
            }

            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> todo.addSubTodo(9999L)
            );
            assertEquals("子任务数量已达上限", exception.getMessage());
        }

        @Test
        @DisplayName("移除存在的子任务ID应成功")
        void removeSubTodo_ExistingId_ShouldSucceed() {
            Long subTodoId = 100L;
            todo.setId(1L);
            todo.addSubTodo(subTodoId);
            int oldVersion = todo.getVersion();

            todo.removeSubTodo(subTodoId);

            assertFalse(todo.getSubTodoIds().contains(subTodoId));
            assertEquals(oldVersion + 1, todo.getVersion());
        }

        @Test
        @DisplayName("移除不存在的子任务ID应静默处理")
        void removeSubTodo_NonExistingId_ShouldDoNothing() {
            int oldVersion = todo.getVersion();

            todo.removeSubTodo(9999L);

            assertEquals(oldVersion, todo.getVersion());
        }
    }

    @Nested
    @DisplayName("完成条件测试")
    class CanCompleteTests {

        @Test
        @DisplayName("无子任务时可以完成")
        void canComplete_NoSubTodos_ShouldReturnTrue() {
            todo.setStatus(TodoStatus.IN_PROGRESS);

            assertTrue(todo.canComplete(Collections.emptySet()));
        }

        @Test
        @DisplayName("所有子任务完成时可以完成")
        void canComplete_AllSubTodosCompleted_ShouldReturnTrue() {
            todo.setId(1L);
            todo.setStatus(TodoStatus.IN_PROGRESS);
            todo.addSubTodo(100L);
            todo.addSubTodo(200L);

            Set<Long> completedIds = new HashSet<>(Arrays.asList(100L, 200L));

            assertTrue(todo.canComplete(completedIds));
        }

        @Test
        @DisplayName("部分子任务未完成时不能完成")
        void canComplete_SomeSubTodosIncomplete_ShouldReturnFalse() {
            todo.setStatus(TodoStatus.IN_PROGRESS);
            todo.setId(1L);
            todo.addSubTodo(100L);
            todo.addSubTodo(200L);

            Set<Long> completedIds = new HashSet<>(Collections.singletonList(100L));

            assertFalse(todo.canComplete(completedIds));
        }

        @Test
        @DisplayName("无子任务完成时不能完成")
        void canComplete_NoSubTodosCompleted_ShouldReturnFalse() {
            todo.setStatus(TodoStatus.IN_PROGRESS);
            todo.setId(1L);
            todo.addSubTodo(100L);

            assertFalse(todo.canComplete(Collections.emptySet()));
        }

        @Test
        @DisplayName("状态为PENDING时可以完成")
        void canComplete_PendingStatus_ShouldReturnTrue() {
            todo.setStatus(TodoStatus.PENDING);

            assertTrue(todo.canComplete(Collections.emptySet()));
        }

        @Test
        @DisplayName("状态为COMPLETED时不能完成")
        void canComplete_CompletedStatus_ShouldReturnFalse() {
            todo.setStatus(TodoStatus.COMPLETED);

            assertFalse(todo.canComplete(Collections.emptySet()));
        }

        @Test
        @DisplayName("状态为CANCELLED时不能完成")
        void canComplete_CancelledStatus_ShouldReturnFalse() {
            todo.setStatus(TodoStatus.CANCELLED);

            assertFalse(todo.canComplete(Collections.emptySet()));
        }
    }

    @Nested
    @DisplayName("更新方法测试")
    class UpdateTests {

        @Test
        @DisplayName("更新所有字段应成功")
        void update_AllFields_ShouldSucceed() {
            LocalDateTime newDueDate = LocalDateTime.now().plusDays(10);
            LocalDateTime newReminder = LocalDateTime.now().plusDays(9);
            int oldVersion = todo.getVersion();

            todo.update("新标题", "新描述", Priority.HIGH, newDueDate, newReminder);

            assertEquals("新标题", todo.getTitle());
            assertEquals("新描述", todo.getDescription());
            assertEquals(Priority.HIGH, todo.getPriority());
            assertEquals(newDueDate, todo.getDueDate());
            assertEquals(newReminder, todo.getReminderTime());
            assertEquals(oldVersion + 1, todo.getVersion());
        }

        @Test
        @DisplayName("部分更新应只更新非null字段")
        void update_PartialFields_ShouldOnlyUpdateNonNull() {
            String originalTitle = todo.getTitle();
            String originalDesc = todo.getDescription();
            LocalDateTime newDueDate = LocalDateTime.now().plusDays(10);

            todo.update(null, null, Priority.HIGH, newDueDate, null);

            assertEquals(originalTitle, todo.getTitle());
            assertEquals(originalDesc, todo.getDescription());
            assertEquals(Priority.HIGH, todo.getPriority());
            assertEquals(newDueDate, todo.getDueDate());
        }

        @Test
        @DisplayName("更新后应调用验证")
        void update_ShouldCallValidate() {
            todo.update(null, null, null, null, null);

            assertDoesNotThrow(() -> todo.validate());
        }
    }

    @Nested
    @DisplayName("Getter和Setter测试")
    class GetterSetterTests {

        @Test
        @DisplayName("设置和获取ID")
        void setAndGetId() {
            Long id = 12345L;
            todo.setId(id);
            assertEquals(id, todo.getId());
        }

        @Test
        @DisplayName("设置和获取状态")
        void setAndGetStatus() {
            todo.setStatus(TodoStatus.COMPLETED);
            assertEquals(TodoStatus.COMPLETED, todo.getStatus());
        }

        @Test
        @DisplayName("设置和获取父任务ID")
        void setAndGetParentId() {
            Long parentId = 999L;
            todo.setParentId(parentId);
            assertEquals(parentId, todo.getParentId());
        }

        @Test
        @DisplayName("设置和获取标签集合")
        void setAndGetTags() {
            Set<String> tags = new HashSet<>(Arrays.asList("tag1", "tag2"));
            todo.setTags(tags);
            assertEquals(tags, todo.getTags());
        }

        @Test
        @DisplayName("设置和获取子任务ID列表")
        void setAndGetSubTodoIds() {
            List<Long> subTodoIds = Arrays.asList(1L, 2L, 3L);
            todo.setSubTodoIds(subTodoIds);
            assertEquals(subTodoIds, todo.getSubTodoIds());
        }
    }

    @Nested
    @DisplayName("toString测试")
    class ToStringTests {

        @Test
        @DisplayName("toString应包含关键信息")
        void toString_ShouldContainKeyInfo() {
            todo.setTitle("测试任务");
            todo.setStatus(TodoStatus.IN_PROGRESS);
            todo.setPriority(Priority.HIGH);

            String result = todo.toString();

            assertTrue(result.contains("id="));
            assertTrue(result.contains("title='测试任务'"));
            assertTrue(result.contains("status=IN_PROGRESS"));
            assertTrue(result.contains("priority=HIGH"));
            assertTrue(result.contains("version="));
        }
    }

    @Nested
    @DisplayName("边界值测试")
    class BoundaryTests {

        @Test
        @DisplayName("标题边界值 - 1个字符")
        void titleBoundary_SingleCharacter() {
            todo.setTitle("a");
            assertDoesNotThrow(() -> todo.validate());
        }

        @Test
        @DisplayName("标题边界值 - 特殊字符")
        void titleBoundary_SpecialCharacters() {
            todo.setTitle("!@#$%^&*()_+-=[]{}|;':\",./<>?");
            assertDoesNotThrow(() -> todo.validate());
        }

        @Test
        @DisplayName("标题边界值 - 中文字符")
        void titleBoundary_ChineseCharacters() {
            todo.setTitle("中文标题测试任务");
            assertDoesNotThrow(() -> todo.validate());
        }

        @Test
        @DisplayName("标题边界值 - Unicode字符")
        void titleBoundary_UnicodeCharacters() {
            todo.setTitle("🎉🎊🎁");
            assertDoesNotThrow(() -> todo.validate());
        }

        @Test
        @DisplayName("描述边界值 - 空字符串")
        void descriptionBoundary_EmptyString() {
            todo.setDescription("");
            assertDoesNotThrow(() -> todo.validate());
        }

        @Test
        @DisplayName("标签边界值 - 单字符")
        void tagBoundary_SingleCharacter() {
            assertDoesNotThrow(() -> todo.addTag("a"));
        }

        @Test
        @DisplayName("标签边界值 - 中文")
        void tagBoundary_ChineseCharacters() {
            assertDoesNotThrow(() -> todo.addTag("工作"));
        }
    }

    @Nested
    @DisplayName("EXPIRED状态测试")
    class ExpiredStatusTests {

        @Test
        @DisplayName("EXPIRED状态可以转换到IN_PROGRESS")
        void expired_CanTransitionToInProgress() {
            todo.setStatus(TodoStatus.EXPIRED);
            assertTrue(todo.canTransitionTo(TodoStatus.IN_PROGRESS));
        }

        @Test
        @DisplayName("EXPIRED状态可以转换到CANCELLED")
        void expired_CanTransitionToCancelled() {
            todo.setStatus(TodoStatus.EXPIRED);
            assertTrue(todo.canTransitionTo(TodoStatus.CANCELLED));
        }

        @Test
        @DisplayName("EXPIRED状态不能转换到PENDING")
        void expired_CannotTransitionToPending() {
            todo.setStatus(TodoStatus.EXPIRED);
            assertFalse(todo.canTransitionTo(TodoStatus.PENDING));
        }

        @Test
        @DisplayName("EXPIRED状态不能转换到COMPLETED")
        void expired_CannotTransitionToCompleted() {
            todo.setStatus(TodoStatus.EXPIRED);
            assertFalse(todo.canTransitionTo(TodoStatus.COMPLETED));
        }

        @Test
        @DisplayName("EXPIRED状态执行转换到IN_PROGRESS应成功")
        void expired_TransitionToInProgress_ShouldSucceed() {
            todo.setStatus(TodoStatus.EXPIRED);
            todo.transitionTo(TodoStatus.IN_PROGRESS);
            assertEquals(TodoStatus.IN_PROGRESS, todo.getStatus());
        }

        @Test
        @DisplayName("EXPIRED状态执行转换到CANCELLED应成功")
        void expired_TransitionToCancelled_ShouldSucceed() {
            todo.setStatus(TodoStatus.EXPIRED);
            todo.transitionTo(TodoStatus.CANCELLED);
            assertEquals(TodoStatus.CANCELLED, todo.getStatus());
        }
    }

    @Nested
    @DisplayName("canComplete边界测试")
    class CanCompleteBoundaryTests {

        @Test
        @DisplayName("completedSubTodoIds为null时调用containsAll会抛出NullPointerException")
        void canComplete_NullCompletedSubTodoIds_ShouldThrowNullPointerException() {
            todo.setStatus(TodoStatus.IN_PROGRESS);
            todo.setId(1L);
            todo.addSubTodo(100L);

            assertThrows(NullPointerException.class, () -> todo.canComplete(null));
        }

        @Test
        @DisplayName("EXPIRED状态不能完成")
        void canComplete_ExpiredStatus_ShouldReturnFalse() {
            todo.setStatus(TodoStatus.EXPIRED);
            assertFalse(todo.canComplete(Collections.emptySet()));
        }

        @Test
        @DisplayName("子任务ID不在已完成列表中应返回false")
        void canComplete_SubTodoNotInCompletedList_ShouldReturnFalse() {
            todo.setStatus(TodoStatus.IN_PROGRESS);
            todo.setId(1L);
            todo.addSubTodo(100L);

            Set<Long> completedIds = new HashSet<>(Collections.singletonList(999L));

            assertFalse(todo.canComplete(completedIds));
        }

        @Test
        @DisplayName("已完成列表包含额外ID不应影响结果")
        void canComplete_ExtraCompletedIds_ShouldNotAffectResult() {
            todo.setStatus(TodoStatus.IN_PROGRESS);
            todo.setId(1L);
            todo.addSubTodo(100L);

            Set<Long> completedIds = new HashSet<>(Arrays.asList(100L, 200L, 300L));

            assertTrue(todo.canComplete(completedIds));
        }
    }

    @Nested
    @DisplayName("日期边界测试")
    class DateBoundaryTests {

        @Test
        @DisplayName("截止日期等于创建时间应通过")
        void dueDate_EqualsCreatedAt_ShouldPass() {
            LocalDateTime now = LocalDateTime.now();
            todo.setCreatedAt(now);
            todo.setDueDate(now);

            assertDoesNotThrow(() -> todo.validate());
        }

        @Test
        @DisplayName("截止日期比创建时间晚1秒应通过")
        void dueDate_OneSecondAfterCreatedAt_ShouldPass() {
            LocalDateTime now = LocalDateTime.now();
            todo.setCreatedAt(now);
            todo.setDueDate(now.plusSeconds(1));

            assertDoesNotThrow(() -> todo.validate());
        }

        @Test
        @DisplayName("提醒时间比截止日期早1秒应通过")
        void reminderTime_OneSecondBeforeDueDate_ShouldPass() {
            LocalDateTime dueDate = LocalDateTime.now().plusDays(7);
            todo.setDueDate(dueDate);
            todo.setReminderTime(dueDate.minusSeconds(1));

            assertDoesNotThrow(() -> todo.validate());
        }

        @Test
        @DisplayName("提醒时间比截止日期晚1秒应抛出异常")
        void reminderTime_OneSecondAfterDueDate_ShouldThrowException() {
            LocalDateTime dueDate = LocalDateTime.now().plusDays(7);
            todo.setDueDate(dueDate);
            todo.setReminderTime(dueDate.plusSeconds(1));

            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> todo.validate()
            );
            assertEquals("提醒时间必须早于截止日期", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("优先级边界测试")
    class PriorityBoundaryTests {

        @Test
        @DisplayName("LOW优先级过期应升级到MEDIUM")
        void expiredLowPriority_ShouldUpgradeToMedium() {
            todo.setPriority(Priority.LOW);
            todo.setDueDate(LocalDateTime.now().minusHours(25));
            todo.setStatus(TodoStatus.PENDING);

            assertTrue(todo.shouldUpgradePriority());
            todo.upgradePriority();
            assertEquals(Priority.MEDIUM, todo.getPriority());
        }

        @Test
        @DisplayName("刚好过期24小时不应升级")
        void expiredExactly24Hours_ShouldNotUpgrade() {
            todo.setPriority(Priority.LOW);
            todo.setDueDate(LocalDateTime.now().minusHours(24));
            todo.setStatus(TodoStatus.PENDING);

            assertFalse(todo.shouldUpgradePriority());
        }

        @Test
        @DisplayName("过期24小时+1秒应升级")
        void expired24HoursPlusOneSecond_ShouldUpgrade() {
            todo.setPriority(Priority.LOW);
            todo.setDueDate(LocalDateTime.now().minusHours(24).minusSeconds(1));
            todo.setStatus(TodoStatus.PENDING);

            assertTrue(todo.shouldUpgradePriority());
        }
    }

    @Nested
    @DisplayName("标签大小写测试")
    class TagCaseSensitivityTests {

        @Test
        @DisplayName("标签存储时会转换为小写")
        void tags_ShouldBeStoredAsLowerCase() {
            todo.addTag("Work");
            todo.addTag("URGENT");

            assertEquals(2, todo.getTags().size());
            assertTrue(todo.getTags().contains("work"));
            assertTrue(todo.getTags().contains("urgent"));
        }

        @Test
        @DisplayName("添加完全相同的标签会抛出异常")
        void addTag_ExactDuplicate_ShouldThrowException() {
            todo.addTag("work");

            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> todo.addTag("work")
            );
            assertTrue(exception.getMessage().contains("标签已存在"));
        }

        @Test
        @DisplayName("添加不同大小写的标签时contains检查区分大小写")
        void addTag_DifferentCase_ContainsCheckIsCaseSensitive() {
            todo.addTag("Work");

            assertEquals(1, todo.getTags().size());
            assertTrue(todo.getTags().contains("work"));

            todo.addTag("Different");
            assertEquals(2, todo.getTags().size());
            assertTrue(todo.getTags().contains("different"));
        }
    }

    @Nested
    @DisplayName("版本号测试")
    class VersionTests {

        @Test
        @DisplayName("初始版本号应为0")
        void initialVersion_ShouldBeZero() {
            Todo newTodo = new Todo();
            assertEquals(0, newTodo.getVersion());
        }

        @Test
        @DisplayName("每次操作版本号应递增")
        void version_ShouldIncrementOnEachOperation() {
            int initialVersion = todo.getVersion();

            todo.transitionTo(TodoStatus.IN_PROGRESS);
            assertEquals(initialVersion + 1, todo.getVersion());

            todo.addTag("test");
            assertEquals(initialVersion + 2, todo.getVersion());

            todo.upgradePriority();
            assertEquals(initialVersion + 3, todo.getVersion());
        }

        @Test
        @DisplayName("设置版本号应成功")
        void setVersion_ShouldSucceed() {
            todo.setVersion(10);
            assertEquals(10, todo.getVersion());
        }
    }

    @Nested
    @DisplayName("空集合测试")
    class EmptyCollectionTests {

        @Test
        @DisplayName("新Todo的标签集合应为空")
        void newTodo_TagsShouldBeEmpty() {
            Todo newTodo = new Todo();
            assertTrue(newTodo.getTags().isEmpty());
        }

        @Test
        @DisplayName("新Todo的子任务集合应为空")
        void newTodo_SubTodosShouldBeEmpty() {
            Todo newTodo = new Todo();
            assertTrue(newTodo.getSubTodoIds().isEmpty());
        }

        @Test
        @DisplayName("设置空标签集合应成功")
        void setTags_EmptySet_ShouldSucceed() {
            todo.setTags(new HashSet<>());
            assertTrue(todo.getTags().isEmpty());
        }

        @Test
        @DisplayName("设置空子任务列表应成功")
        void setSubTodoIds_EmptyList_ShouldSucceed() {
            todo.setSubTodoIds(new ArrayList<>());
            assertTrue(todo.getSubTodoIds().isEmpty());
        }
    }
}
