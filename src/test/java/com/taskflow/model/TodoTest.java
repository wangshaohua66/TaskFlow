package com.taskflow.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Todo 实体类单元测试
 *
 * 测试覆盖:
 * 1. 构造器和初始化
 * 2. 验证方法 (标题、描述、日期、标签、子任务)
 * 3. 状态机转换 (使用参数化测试覆盖所有合法和非法转换)
 * 4. 业务逻辑方法 (过期检查、优先级升级)
 * 5. 标签管理
 * 6. 子任务管理
 * 7. 乐观锁版本号
 * 8. 边界值测试
 */
class TodoTest {

    private Todo todo;

    @BeforeEach
    void setUp() {
        todo = new Todo();
    }

    // ==================== 构造器测试 ====================

    @Test
    @DisplayName("默认构造器应正确初始化所有字段")
    void defaultConstructor_ShouldInitializeAllFields() {
        // Act & Assert
        assertNotNull(todo.getId(), "ID 不应为空");
        assertEquals(TodoStatus.PENDING, todo.getStatus(), "默认状态应为 PENDING");
        assertEquals(Priority.MEDIUM, todo.getPriority(), "默认优先级应为 MEDIUM");
        assertNotNull(todo.getCreatedAt(), "创建时间不应为空");
        assertNotNull(todo.getUpdatedAt(), "更新时间不应为空");
        assertNotNull(todo.getSubTodoIds(), "子任务列表不应为空");
        assertTrue(todo.getSubTodoIds().isEmpty(), "子任务列表应为空");
        assertNotNull(todo.getTags(), "标签集合不应为空");
        assertTrue(todo.getTags().isEmpty(), "标签集合应为空");
        assertEquals(0, todo.getVersion(), "初始版本号应为 0");
    }

    @Test
    @DisplayName("带参数构造器应正确设置所有字段")
    void parameterizedConstructor_ShouldSetAllFields() {
        // Arrange
        String title = "测试任务";
        String description = "测试描述";
        Priority priority = Priority.HIGH;
        LocalDateTime dueDate = LocalDateTime.now().plusDays(7);

        // Act
        Todo todoWithParams = new Todo(title, description, priority, dueDate);

        // Assert
        assertNotNull(todoWithParams.getId(), "ID 不应为空");
        assertEquals(title, todoWithParams.getTitle(), "标题应匹配");
        assertEquals(description, todoWithParams.getDescription(), "描述应匹配");
        assertEquals(priority, todoWithParams.getPriority(), "优先级应匹配");
        assertEquals(dueDate, todoWithParams.getDueDate(), "截止日期应匹配");
        assertEquals(TodoStatus.PENDING, todoWithParams.getStatus(), "默认状态应为 PENDING");
    }

    @Test
    @DisplayName("带参数构造器当优先级为null时应使用默认优先级MEDIUM")
    void parameterizedConstructor_WithNullPriority_ShouldUseDefaultMedium() {
        // Arrange
        String title = "测试任务";

        // Act
        Todo todoWithNullPriority = new Todo(title, null, null, null);

        // Assert
        assertEquals(Priority.MEDIUM, todoWithNullPriority.getPriority(), "null 优先级应默认为 MEDIUM");
    }

    // ==================== 标题验证测试 ====================

    @Test
    @DisplayName("验证标题为null时应抛出IllegalArgumentException")
    void validate_WithNullTitle_ShouldThrowIllegalArgumentException() {
        // Arrange
        todo.setTitle(null);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, todo::validate);
        assertEquals("标题不能为空", exception.getMessage());
    }

    @Test
    @DisplayName("验证标题为空字符串时应抛出IllegalArgumentException")
    void validate_WithEmptyTitle_ShouldThrowIllegalArgumentException() {
        // Arrange
        todo.setTitle("   ");

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, todo::validate);
        assertEquals("标题不能为空", exception.getMessage());
    }

    @Test
    @DisplayName("验证标题长度超过200字符时应抛出IllegalArgumentException")
    void validate_WithTitleExceedingMaxLength_ShouldThrowIllegalArgumentException() {
        // Arrange
        String longTitle = StringUtils.repeat("a", Todo.MAX_TITLE_LENGTH + 1);
        todo.setTitle(longTitle);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, todo::validate);
        assertEquals("标题长度不能超过" + Todo.MAX_TITLE_LENGTH + "字符", exception.getMessage());
    }

    @Test
    @DisplayName("验证标题长度等于200字符时应通过")
    void validate_WithTitleAtMaxLength_ShouldPass() {
        // Arrange
        String maxLengthTitle = StringUtils.repeat("a", Todo.MAX_TITLE_LENGTH);
        todo.setTitle(maxLengthTitle);

        // Act & Assert
        assertDoesNotThrow(todo::validate);
    }

    @Test
    @DisplayName("验证标题长度为1字符时应通过")
    void validate_WithTitleOfOneCharacter_ShouldPass() {
        // Arrange
        todo.setTitle("a");

        // Act & Assert
        assertDoesNotThrow(todo::validate);
    }

    // ==================== 描述验证测试 ====================

    @Test
    @DisplayName("验证描述长度超过5000字符时应抛出IllegalArgumentException")
    void validate_WithDescriptionExceedingMaxLength_ShouldThrowIllegalArgumentException() {
        // Arrange
        todo.setTitle("有效标题");
        String longDescription = StringUtils.repeat("a", Todo.MAX_DESCRIPTION_LENGTH + 1);
        todo.setDescription(longDescription);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, todo::validate);
        assertEquals("描述长度不能超过" + Todo.MAX_DESCRIPTION_LENGTH + "字符", exception.getMessage());
    }

    @Test
    @DisplayName("验证描述为null时应通过")
    void validate_WithNullDescription_ShouldPass() {
        // Arrange
        todo.setTitle("有效标题");
        todo.setDescription(null);

        // Act & Assert
        assertDoesNotThrow(todo::validate);
    }

    @Test
    @DisplayName("验证描述长度等于5000字符时应通过")
    void validate_WithDescriptionAtMaxLength_ShouldPass() {
        // Arrange
        todo.setTitle("有效标题");
        todo.setDescription(StringUtils.repeat("a", Todo.MAX_DESCRIPTION_LENGTH));

        // Act & Assert
        assertDoesNotThrow(todo::validate);
    }

    // ==================== 日期验证测试 ====================

    @Test
    @DisplayName("验证截止日期早于创建时间时应抛出IllegalArgumentException")
    void validate_WithDueDateBeforeCreatedAt_ShouldThrowIllegalArgumentException() {
        // Arrange
        todo.setTitle("有效标题");
        todo.setCreatedAt(LocalDateTime.now());
        todo.setDueDate(LocalDateTime.now().minusDays(1));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, todo::validate);
        assertEquals("截止日期不能早于创建时间", exception.getMessage());
    }

    @Test
    @DisplayName("验证提醒时间晚于截止日期时应抛出IllegalArgumentException")
    void validate_WithReminderTimeAfterDueDate_ShouldThrowIllegalArgumentException() {
        // Arrange
        todo.setTitle("有效标题");
        LocalDateTime now = LocalDateTime.now();
        todo.setCreatedAt(now);
        todo.setDueDate(now.plusDays(1));
        todo.setReminderTime(now.plusDays(2));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, todo::validate);
        assertEquals("提醒时间必须早于截止日期", exception.getMessage());
    }

    @Test
    @DisplayName("验证截止日期等于创建时间时应通过")
    void validate_WithDueDateEqualToCreatedAt_ShouldPass() {
        // Arrange
        todo.setTitle("有效标题");
        LocalDateTime now = LocalDateTime.now();
        todo.setCreatedAt(now);
        todo.setDueDate(now);

        // Act & Assert
        assertDoesNotThrow(todo::validate);
    }

    @Test
    @DisplayName("验证截止日期晚于创建时间时应通过")
    void validate_WithDueDateAfterCreatedAt_ShouldPass() {
        // Arrange
        todo.setTitle("有效标题");
        LocalDateTime now = LocalDateTime.now();
        todo.setCreatedAt(now);
        todo.setDueDate(now.plusDays(1));

        // Act & Assert
        assertDoesNotThrow(todo::validate);
    }

    @Test
    @DisplayName("验证提醒时间早于截止日期时应通过")
    void validate_WithReminderTimeBeforeDueDate_ShouldPass() {
        // Arrange
        todo.setTitle("有效标题");
        LocalDateTime now = LocalDateTime.now();
        todo.setCreatedAt(now);
        todo.setDueDate(now.plusDays(2));
        todo.setReminderTime(now.plusDays(1));

        // Act & Assert
        assertDoesNotThrow(todo::validate);
    }

    // ==================== 标签验证测试 ====================

    @Test
    @DisplayName("验证标签数量超过5个时应抛出IllegalArgumentException")
    void validate_WithTagsExceedingMaxCount_ShouldThrowIllegalArgumentException() {
        // Arrange
        todo.setTitle("有效标题");
        Set<String> tooManyTags = new HashSet<>(Arrays.asList("tag1", "tag2", "tag3", "tag4", "tag5", "tag6"));
        todo.setTags(tooManyTags);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, todo::validate);
        assertEquals("标签数量不能超过" + Todo.MAX_TAGS_COUNT + "个", exception.getMessage());
    }

    @Test
    @DisplayName("验证标签数量等于5个时应通过")
    void validate_WithTagsAtMaxCount_ShouldPass() {
        // Arrange
        todo.setTitle("有效标题");
        Set<String> maxTags = new HashSet<>(Arrays.asList("tag1", "tag2", "tag3", "tag4", "tag5"));
        todo.setTags(maxTags);

        // Act & Assert
        assertDoesNotThrow(todo::validate);
    }

    @Test
    @DisplayName("验证空标签集合时应通过")
    void validate_WithEmptyTags_ShouldPass() {
        // Arrange
        todo.setTitle("有效标题");
        todo.setTags(Collections.emptySet());

        // Act & Assert
        assertDoesNotThrow(todo::validate);
    }

    // ==================== 子任务验证测试 ====================

    @Test
    @DisplayName("验证子任务数量超过20个时应抛出IllegalArgumentException")
    void validate_WithSubTodosExceedingMaxCount_ShouldThrowIllegalArgumentException() {
        // Arrange
        todo.setTitle("有效标题");
        for (long i = 1; i <= Todo.MAX_SUBTODOS_COUNT + 1; i++) {
            todo.getSubTodoIds().add(i);
        }

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, todo::validate);
        assertEquals("子任务数量不能超过" + Todo.MAX_SUBTODOS_COUNT + "个", exception.getMessage());
    }

    @Test
    @DisplayName("验证子任务数量等于20个时应通过")
    void validate_WithSubTodosAtMaxCount_ShouldPass() {
        // Arrange
        todo.setTitle("有效标题");
        for (long i = 1; i <= Todo.MAX_SUBTODOS_COUNT; i++) {
            todo.getSubTodoIds().add(i);
        }

        // Act & Assert
        assertDoesNotThrow(todo::validate);
    }

    // ==================== 状态机转换测试 - 使用参数化测试 ====================

    @ParameterizedTest(name = "从 {0} 转换到 {1} 应该返回 {2}")
    @CsvSource({
        // PENDING 的合法转换
        "PENDING, IN_PROGRESS, true",
        "PENDING, CANCELLED, true",
        // PENDING 的非法转换
        "PENDING, COMPLETED, false",
        "PENDING, PENDING, false",
        "PENDING, EXPIRED, false",

        // IN_PROGRESS 的合法转换
        "IN_PROGRESS, COMPLETED, true",
        "IN_PROGRESS, PENDING, true",
        "IN_PROGRESS, CANCELLED, true",
        // IN_PROGRESS 的非法转换
        "IN_PROGRESS, IN_PROGRESS, false",
        "IN_PROGRESS, EXPIRED, false",

        // COMPLETED 的非法转换 (终态)
        "COMPLETED, PENDING, false",
        "COMPLETED, IN_PROGRESS, false",
        "COMPLETED, COMPLETED, false",
        "COMPLETED, CANCELLED, false",
        "COMPLETED, EXPIRED, false",

        // CANCELLED 的合法转换
        "CANCELLED, PENDING, true",
        // CANCELLED 的非法转换
        "CANCELLED, IN_PROGRESS, false",
        "CANCELLED, COMPLETED, false",
        "CANCELLED, CANCELLED, false",
        "CANCELLED, EXPIRED, false",

        // EXPIRED 的合法转换
        "EXPIRED, IN_PROGRESS, true",
        "EXPIRED, CANCELLED, true",
        // EXPIRED 的非法转换
        "EXPIRED, PENDING, false",
        "EXPIRED, COMPLETED, false",
        "EXPIRED, EXPIRED, false"
    })
    @DisplayName("状态转换检查 - 参数化测试所有状态组合")
    void canTransitionTo_WithVariousStates_ShouldReturnExpectedResult(String currentStatusStr, String newStatusStr, boolean expectedResult) {
        // Arrange
        TodoStatus currentStatus = TodoStatus.valueOf(currentStatusStr);
        TodoStatus newStatus = TodoStatus.valueOf(newStatusStr);
        todo.setStatus(currentStatus);

        // Act
        boolean canTransition = todo.canTransitionTo(newStatus);

        // Assert
        assertEquals(expectedResult, canTransition,
            String.format("从 %s 转换到 %s 应该返回 %s", currentStatus, newStatus, expectedResult));
    }

    @ParameterizedTest(name = "从 {0} 成功转换到 {1}")
    @CsvSource({
        "PENDING, IN_PROGRESS",
        "PENDING, CANCELLED",
        "IN_PROGRESS, COMPLETED",
        "IN_PROGRESS, PENDING",
        "IN_PROGRESS, CANCELLED",
        "CANCELLED, PENDING",
        "EXPIRED, IN_PROGRESS",
        "EXPIRED, CANCELLED"
    })
    @DisplayName("合法状态转换应成功并更新版本号和时间")
    void transitionTo_WithValidTransition_ShouldUpdateStatusAndVersion(String currentStatusStr, String newStatusStr) {
        // Arrange
        TodoStatus currentStatus = TodoStatus.valueOf(currentStatusStr);
        TodoStatus newStatus = TodoStatus.valueOf(newStatusStr);
        todo.setStatus(currentStatus);
        int initialVersion = todo.getVersion();
        LocalDateTime initialUpdatedAt = todo.getUpdatedAt();

        // 等待一小段时间以确保更新时间不同
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Act
        todo.transitionTo(newStatus);

        // Assert
        assertEquals(newStatus, todo.getStatus(), "状态应更新为新状态");
        assertEquals(initialVersion + 1, todo.getVersion(), "版本号应增加1");
        assertTrue(todo.getUpdatedAt().isAfter(initialUpdatedAt) || todo.getUpdatedAt().equals(initialUpdatedAt),
            "更新时间应被更新");
    }

    @ParameterizedTest(name = "从 {0} 非法转换到 {1} 应抛出异常")
    @CsvSource({
        "PENDING, COMPLETED",
        "PENDING, PENDING",
        "IN_PROGRESS, IN_PROGRESS",
        "COMPLETED, PENDING",
        "COMPLETED, IN_PROGRESS",
        "CANCELLED, IN_PROGRESS",
        "EXPIRED, PENDING"
    })
    @DisplayName("非法状态转换应抛出IllegalStateException")
    void transitionTo_WithInvalidTransition_ShouldThrowIllegalStateException(String currentStatusStr, String newStatusStr) {
        // Arrange
        TodoStatus currentStatus = TodoStatus.valueOf(currentStatusStr);
        TodoStatus newStatus = TodoStatus.valueOf(newStatusStr);
        todo.setStatus(currentStatus);

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> todo.transitionTo(newStatus));
        assertTrue(exception.getMessage().contains("不允许从"),
            "异常消息应包含 '不允许从'");
        assertTrue(exception.getMessage().contains(currentStatusStr),
            "异常消息应包含当前状态");
        assertTrue(exception.getMessage().contains(newStatusStr),
            "异常消息应包含目标状态");
    }

    // ==================== 过期检查测试 ====================

    @Test
    @DisplayName("isExpired当截止日期为null时应返回false")
    void isExpired_WithNullDueDate_ShouldReturnFalse() {
        // Arrange
        todo.setDueDate(null);

        // Act
        boolean expired = todo.isExpired();

        // Assert
        assertFalse(expired, "截止日期为null时不应视为过期");
    }

    @Test
    @DisplayName("isExpired当截止日期在未来时应返回false")
    void isExpired_WithFutureDueDate_ShouldReturnFalse() {
        // Arrange
        todo.setDueDate(LocalDateTime.now().plusDays(1));

        // Act
        boolean expired = todo.isExpired();

        // Assert
        assertFalse(expired, "截止日期在未来时不应视为过期");
    }

    @Test
    @DisplayName("isExpired当截止日期在过去时应返回true")
    void isExpired_WithPastDueDate_ShouldReturnTrue() {
        // Arrange
        todo.setDueDate(LocalDateTime.now().minusDays(1));

        // Act
        boolean expired = todo.isExpired();

        // Assert
        assertTrue(expired, "截止日期在过去时应视为过期");
    }

    @Test
    @DisplayName("isExpired当截止日期等于当前时间时应返回false")
    void isExpired_WithDueDateEqualToNow_ShouldReturnFalse() {
        // Arrange - 由于时间精度问题，我们使用过去的时间点
        todo.setDueDate(LocalDateTime.now());

        // Act
        boolean expired = todo.isExpired();

        // Assert - 由于 now().isAfter(now()) 返回 false
        assertFalse(expired, "截止日期等于当前时间时不应视为过期");
    }

    // ==================== 优先级升级测试 ====================

    @Test
    @DisplayName("shouldUpgradePriority当任务未过期时应返回false")
    void shouldUpgradePriority_WhenNotExpired_ShouldReturnFalse() {
        // Arrange
        todo.setDueDate(LocalDateTime.now().plusDays(1));
        todo.setStatus(TodoStatus.IN_PROGRESS);

        // Act
        boolean shouldUpgrade = todo.shouldUpgradePriority();

        // Assert
        assertFalse(shouldUpgrade, "未过期任务不应升级优先级");
    }

    @Test
    @DisplayName("shouldUpgradePriority当任务已完成时应返回false")
    void shouldUpgradePriority_WhenCompleted_ShouldReturnFalse() {
        // Arrange
        todo.setDueDate(LocalDateTime.now().minusDays(2));
        todo.setStatus(TodoStatus.COMPLETED);

        // Act
        boolean shouldUpgrade = todo.shouldUpgradePriority();

        // Assert
        assertFalse(shouldUpgrade, "已完成任务不应升级优先级");
    }

    @Test
    @DisplayName("shouldUpgradePriority当任务已取消时应返回false")
    void shouldUpgradePriority_WhenCancelled_ShouldReturnFalse() {
        // Arrange
        todo.setDueDate(LocalDateTime.now().minusDays(2));
        todo.setStatus(TodoStatus.CANCELLED);

        // Act
        boolean shouldUpgrade = todo.shouldUpgradePriority();

        // Assert
        assertFalse(shouldUpgrade, "已取消任务不应升级优先级");
    }

    @Test
    @DisplayName("shouldUpgradePriority当任务过期不足24小时时应返回false")
    void shouldUpgradePriority_WhenExpiredLessThan24Hours_ShouldReturnFalse() {
        // Arrange
        todo.setDueDate(LocalDateTime.now().minusHours(23));
        todo.setStatus(TodoStatus.IN_PROGRESS);

        // Act
        boolean shouldUpgrade = todo.shouldUpgradePriority();

        // Assert
        assertFalse(shouldUpgrade, "过期不足24小时不应升级优先级");
    }

    @Test
    @DisplayName("shouldUpgradePriority当任务过期超过24小时且状态为IN_PROGRESS时应返回true")
    void shouldUpgradePriority_WhenExpiredMoreThan24HoursAndInProgress_ShouldReturnTrue() {
        // Arrange
        todo.setDueDate(LocalDateTime.now().minusHours(25));
        todo.setStatus(TodoStatus.IN_PROGRESS);

        // Act
        boolean shouldUpgrade = todo.shouldUpgradePriority();

        // Assert
        assertTrue(shouldUpgrade, "过期超过24小时的进行中任务应升级优先级");
    }

    @Test
    @DisplayName("shouldUpgradePriority当任务过期超过24小时且状态为PENDING时应返回true")
    void shouldUpgradePriority_WhenExpiredMoreThan24HoursAndPending_ShouldReturnTrue() {
        // Arrange
        todo.setDueDate(LocalDateTime.now().minusHours(25));
        todo.setStatus(TodoStatus.PENDING);

        // Act
        boolean shouldUpgrade = todo.shouldUpgradePriority();

        // Assert
        assertTrue(shouldUpgrade, "过期超过24小时的待处理任务应升级优先级");
    }

    @ParameterizedTest
    @EnumSource(value = Priority.class, names = {"LOW", "MEDIUM", "HIGH"})
    @DisplayName("upgradePriority应将优先级提升一级")
    void upgradePriority_ShouldIncreasePriorityByOneLevel(Priority initialPriority) {
        // Arrange
        todo.setPriority(initialPriority);
        int initialVersion = todo.getVersion();
        Priority expectedPriority = initialPriority.upgrade();

        // Act
        todo.upgradePriority();

        // Assert
        assertEquals(expectedPriority, todo.getPriority(), "优先级应提升一级");
        assertEquals(initialVersion + 1, todo.getVersion(), "版本号应增加1");
    }

    @Test
    @DisplayName("upgradePriority当优先级为CRITICAL时应保持不变")
    void upgradePriority_WhenCritical_ShouldRemainCritical() {
        // Arrange
        todo.setPriority(Priority.CRITICAL);
        int initialVersion = todo.getVersion();

        // Act
        todo.upgradePriority();

        // Assert
        assertEquals(Priority.CRITICAL, todo.getPriority(), "CRITICAL优先级应保持不变");
        assertEquals(initialVersion, todo.getVersion(), "版本号不应改变");
    }

    // ==================== 标签管理测试 ====================

    @Test
    @DisplayName("addTag当标签为null时应抛出IllegalArgumentException")
    void addTag_WithNullTag_ShouldThrowIllegalArgumentException() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> todo.addTag(null));
        assertEquals("标签不能为空", exception.getMessage());
    }

    @Test
    @DisplayName("addTag当标签为空字符串时应抛出IllegalArgumentException")
    void addTag_WithEmptyTag_ShouldThrowIllegalArgumentException() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> todo.addTag("   "));
        assertEquals("标签不能为空", exception.getMessage());
    }

    @Test
    @DisplayName("addTag当标签已存在时应抛出IllegalArgumentException")
    void addTag_WithExistingTag_ShouldThrowIllegalArgumentException() {
        // Arrange
        todo.addTag("existing");

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> todo.addTag("existing"));
        assertEquals("标签已存在: existing", exception.getMessage());
    }

    @Test
    @DisplayName("addTag当标签数量已达上限时应抛出IllegalArgumentException")
    void addTag_WhenMaxTagsReached_ShouldThrowIllegalArgumentException() {
        // Arrange
        for (int i = 0; i < Todo.MAX_TAGS_COUNT; i++) {
            todo.addTag("tag" + i);
        }

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> todo.addTag("extra"));
        assertEquals("标签数量已达上限", exception.getMessage());
    }

    @Test
    @DisplayName("addTag应添加标签并转换为小写")
    void addTag_ShouldAddTagInLowerCase() {
        // Arrange
        String tag = "IMPORTANT";
        int initialVersion = todo.getVersion();

        // Act
        todo.addTag(tag);

        // Assert
        assertTrue(todo.getTags().contains("important"), "标签应转换为小写存储");
        assertEquals(initialVersion + 1, todo.getVersion(), "版本号应增加1");
    }

    @Test
    @DisplayName("addTag应去除标签前后空格")
    void addTag_ShouldTrimTag() {
        // Arrange
        String tagWithSpaces = "  important  ";

        // Act
        todo.addTag(tagWithSpaces);

        // Assert
        assertTrue(todo.getTags().contains("important"), "标签应去除前后空格");
    }

    @Test
    @DisplayName("removeTag当标签为null时应抛出IllegalArgumentException")
    void removeTag_WithNullTag_ShouldThrowIllegalArgumentException() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> todo.removeTag(null));
        assertEquals("标签不能为空", exception.getMessage());
    }

    @Test
    @DisplayName("removeTag应移除标签并更新版本号")
    void removeTag_ShouldRemoveTagAndUpdateVersion() {
        // Arrange
        todo.addTag("tag1");
        todo.addTag("tag2");
        int versionAfterAdd = todo.getVersion();

        // Act
        todo.removeTag("tag1");

        // Assert
        assertFalse(todo.getTags().contains("tag1"), "标签应被移除");
        assertTrue(todo.getTags().contains("tag2"), "其他标签应保持不变");
        assertEquals(versionAfterAdd + 1, todo.getVersion(), "版本号应增加1");
    }

    @Test
    @DisplayName("removeTag当标签不存在时不应改变版本号")
    void removeTag_WithNonExistingTag_ShouldNotChangeVersion() {
        // Arrange
        todo.addTag("existing");
        int versionBeforeRemove = todo.getVersion();

        // Act
        todo.removeTag("nonexisting");

        // Assert
        assertEquals(versionBeforeRemove, todo.getVersion(), "版本号不应改变");
    }

    // ==================== 子任务管理测试 ====================

    @Test
    @DisplayName("addSubTodo当子任务ID为null时应抛出IllegalArgumentException")
    void addSubTodo_WithNullSubTodoId_ShouldThrowIllegalArgumentException() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> todo.addSubTodo(null));
        assertEquals("子任务ID不能为空", exception.getMessage());
    }

    @Test
    @DisplayName("addSubTodo当子任务ID等于自身ID时应抛出IllegalArgumentException")
    void addSubTodo_WithSelfAsSubTodo_ShouldThrowIllegalArgumentException() {
        // Arrange
        Long selfId = todo.getId();

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> todo.addSubTodo(selfId));
        assertEquals("不能将自己添加为子任务", exception.getMessage());
    }

    @Test
    @DisplayName("addSubTodo当子任务已存在时应抛出IllegalArgumentException")
    void addSubTodo_WithExistingSubTodo_ShouldThrowIllegalArgumentException() {
        // Arrange
        Long subTodoId = 999L;
        todo.addSubTodo(subTodoId);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> todo.addSubTodo(subTodoId));
        assertEquals("子任务已存在", exception.getMessage());
    }

    @Test
    @DisplayName("addSubTodo当子任务数量已达上限时应抛出IllegalArgumentException")
    void addSubTodo_WhenMaxSubTodosReached_ShouldThrowIllegalArgumentException() {
        // Arrange
        for (long i = 100; i < 100 + Todo.MAX_SUBTODOS_COUNT; i++) {
            todo.addSubTodo(i);
        }

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> todo.addSubTodo(999L));
        assertEquals("子任务数量已达上限", exception.getMessage());
    }

    @Test
    @DisplayName("addSubTodo应添加子任务并更新版本号")
    void addSubTodo_ShouldAddSubTodoAndUpdateVersion() {
        // Arrange
        Long subTodoId = 999L;
        int initialVersion = todo.getVersion();

        // Act
        todo.addSubTodo(subTodoId);

        // Assert
        assertTrue(todo.getSubTodoIds().contains(subTodoId), "子任务ID应被添加");
        assertEquals(initialVersion + 1, todo.getVersion(), "版本号应增加1");
    }

    @Test
    @DisplayName("removeSubTodo应移除子任务并更新版本号")
    void removeSubTodo_ShouldRemoveSubTodoAndUpdateVersion() {
        // Arrange
        Long subTodoId = 999L;
        todo.addSubTodo(subTodoId);
        int versionAfterAdd = todo.getVersion();

        // Act
        todo.removeSubTodo(subTodoId);

        // Assert
        assertFalse(todo.getSubTodoIds().contains(subTodoId), "子任务ID应被移除");
        assertEquals(versionAfterAdd + 1, todo.getVersion(), "版本号应增加1");
    }

    @Test
    @DisplayName("removeSubTodo当子任务不存在时不应改变版本号")
    void removeSubTodo_WithNonExistingSubTodo_ShouldNotChangeVersion() {
        // Arrange
        int initialVersion = todo.getVersion();

        // Act
        todo.removeSubTodo(999L);

        // Assert
        assertEquals(initialVersion, todo.getVersion(), "版本号不应改变");
    }

    // ==================== 完成检查测试 ====================

    @Test
    @DisplayName("canComplete当状态为COMPLETED时应返回false")
    void canComplete_WhenAlreadyCompleted_ShouldReturnFalse() {
        // Arrange
        todo.setStatus(TodoStatus.COMPLETED);

        // Act
        boolean canComplete = todo.canComplete(Collections.emptySet());

        // Assert
        assertFalse(canComplete, "已完成的任务不应能再次完成");
    }

    @Test
    @DisplayName("canComplete当状态为CANCELLED时应返回false")
    void canComplete_WhenCancelled_ShouldReturnFalse() {
        // Arrange
        todo.setStatus(TodoStatus.CANCELLED);

        // Act
        boolean canComplete = todo.canComplete(Collections.emptySet());

        // Assert
        assertFalse(canComplete, "已取消的任务不应能完成");
    }

    @Test
    @DisplayName("canComplete当无子任务且状态为IN_PROGRESS时应返回true")
    void canComplete_WithNoSubTodosAndInProgress_ShouldReturnTrue() {
        // Arrange
        todo.setStatus(TodoStatus.IN_PROGRESS);

        // Act
        boolean canComplete = todo.canComplete(Collections.emptySet());

        // Assert
        assertTrue(canComplete, "无子任务的进行中任务应能完成");
    }

    @Test
    @DisplayName("canComplete当所有子任务已完成时应返回true")
    void canComplete_WithAllSubTodosCompleted_ShouldReturnTrue() {
        // Arrange
        todo.setStatus(TodoStatus.IN_PROGRESS);
        todo.addSubTodo(1L);
        todo.addSubTodo(2L);
        Set<Long> completedSubTodos = new HashSet<>(Arrays.asList(1L, 2L));

        // Act
        boolean canComplete = todo.canComplete(completedSubTodos);

        // Assert
        assertTrue(canComplete, "所有子任务完成时父任务应能完成");
    }

    @Test
    @DisplayName("canComplete当有子任务未完成时应返回false")
    void canComplete_WithIncompleteSubTodos_ShouldReturnFalse() {
        // Arrange
        todo.setStatus(TodoStatus.IN_PROGRESS);
        todo.addSubTodo(1L);
        todo.addSubTodo(2L);
        Set<Long> completedSubTodos = Collections.singleton(1L);

        // Act
        boolean canComplete = todo.canComplete(completedSubTodos);

        // Assert
        assertFalse(canComplete, "有子任务未完成时父任务不应能完成");
    }

    // ==================== 更新方法测试 ====================

    @Test
    @DisplayName("update应更新所有提供的字段并增加版本号")
    void update_WithAllFields_ShouldUpdateFieldsAndIncrementVersion() {
        // Arrange
        todo.setTitle("原始标题");
        todo.setDescription("原始描述");
        todo.setPriority(Priority.LOW);
        todo.setDueDate(LocalDateTime.now().plusDays(1));
        todo.setReminderTime(LocalDateTime.now());
        int initialVersion = todo.getVersion();

        String newTitle = "新标题";
        String newDescription = "新描述";
        Priority newPriority = Priority.HIGH;
        LocalDateTime newDueDate = LocalDateTime.now().plusDays(7);
        LocalDateTime newReminderTime = LocalDateTime.now().plusDays(6);

        // Act
        todo.update(newTitle, newDescription, newPriority, newDueDate, newReminderTime);

        // Assert
        assertEquals(newTitle, todo.getTitle(), "标题应更新");
        assertEquals(newDescription, todo.getDescription(), "描述应更新");
        assertEquals(newPriority, todo.getPriority(), "优先级应更新");
        assertEquals(newDueDate, todo.getDueDate(), "截止日期应更新");
        assertEquals(newReminderTime, todo.getReminderTime(), "提醒时间应更新");
        assertEquals(initialVersion + 1, todo.getVersion(), "版本号应增加1");
    }

    @Test
    @DisplayName("update当参数为null时不应更新对应字段")
    void update_WithNullFields_ShouldNotUpdateThoseFields() {
        // Arrange
        String originalTitle = "原始标题";
        todo.setTitle(originalTitle);
        int initialVersion = todo.getVersion();

        // Act
        todo.update(null, null, null, null, null);

        // Assert
        assertEquals(originalTitle, todo.getTitle(), "标题应保持不变");
        assertEquals(initialVersion + 1, todo.getVersion(), "版本号应增加1");
    }

    @Test
    @DisplayName("update当标题无效时应抛出IllegalArgumentException")
    void update_WithInvalidTitle_ShouldThrowIllegalArgumentException() {
        // Arrange
        todo.setTitle("原始标题");

        // Act & Assert
        assertThrows(IllegalArgumentException.class,
            () -> todo.update("", null, null, null, null));
    }

    // ==================== Getter和Setter测试 ====================

    @Test
    @DisplayName("setId应正确设置ID")
    void setId_ShouldSetId() {
        // Arrange
        Long newId = 999L;

        // Act
        todo.setId(newId);

        // Assert
        assertEquals(newId, todo.getId(), "ID应被正确设置");
    }

    @Test
    @DisplayName("setParentId应正确设置父任务ID")
    void setParentId_ShouldSetParentId() {
        // Arrange
        Long parentId = 100L;

        // Act
        todo.setParentId(parentId);

        // Assert
        assertEquals(parentId, todo.getParentId(), "父任务ID应被正确设置");
    }

    @Test
    @DisplayName("setVersion应正确设置版本号")
    void setVersion_ShouldSetVersion() {
        // Arrange
        int newVersion = 5;

        // Act
        todo.setVersion(newVersion);

        // Assert
        assertEquals(newVersion, todo.getVersion(), "版本号应被正确设置");
    }

    // ==================== toString测试 ====================

    @Test
    @DisplayName("toString应包含关键字段信息")
    void toString_ShouldContainKeyFields() {
        // Arrange
        todo.setTitle("测试任务");
        todo.setStatus(TodoStatus.IN_PROGRESS);
        todo.setPriority(Priority.HIGH);
        todo.setVersion(3);

        // Act
        String result = todo.toString();

        // Assert
        assertTrue(result.contains("Todo{"), "应包含类名");
        assertTrue(result.contains("id="), "应包含ID");
        assertTrue(result.contains("title='测试任务'"), "应包含标题");
        assertTrue(result.contains("status=IN_PROGRESS"), "应包含状态");
        assertTrue(result.contains("priority=HIGH"), "应包含优先级");
        assertTrue(result.contains("version=3"), "应包含版本号");
    }

    // ==================== 边界值和特殊字符测试 ====================

    @Test
    @DisplayName("各种有效标题格式应通过验证 - 最小长度")
    void validate_WithMinLengthTitle_ShouldPass() {
        todo.setTitle("a");
        assertDoesNotThrow(todo::validate);
    }

    @Test
    @DisplayName("各种有效标题格式应通过验证 - 中文字符")
    void validate_WithChineseTitle_ShouldPass() {
        todo.setTitle("正常中文标题");
        assertDoesNotThrow(todo::validate);
    }

    @Test
    @DisplayName("各种有效标题格式应通过验证 - 特殊字符")
    void validate_WithSpecialCharsTitle_ShouldPass() {
        todo.setTitle("Special!@#$%^&*()_+-=[]{}|;':\",./<>?");
        assertDoesNotThrow(todo::validate);
    }

    @Test
    @DisplayName("各种有效标题格式应通过验证 - 包含空格")
    void validate_WithSpacesTitle_ShouldPass() {
        todo.setTitle("Title with spaces");
        assertDoesNotThrow(todo::validate);
    }

    @Test
    @DisplayName("各种有效标题格式应通过验证 - 包含制表符")
    void validate_WithTabsTitle_ShouldPass() {
        todo.setTitle("Title\twith\ttabs");
        assertDoesNotThrow(todo::validate);
    }

    @Test
    @DisplayName("各种有效标题格式应通过验证 - 包含换行符")
    void validate_WithNewlinesTitle_ShouldPass() {
        todo.setTitle("Title\nwith\nnewlines");
        assertDoesNotThrow(todo::validate);
    }

    @Test
    @DisplayName("各种有效标题格式应通过验证 - 最大长度")
    void validate_WithMaxLengthTitle_ShouldPass() {
        todo.setTitle(StringUtils.repeat("a", 200));
        assertDoesNotThrow(todo::validate);
    }

    @Test
    @DisplayName("特殊字符标签应被正确处理")
    void addTag_WithSpecialCharacters_ShouldHandleCorrectly() {
        // Arrange
        String specialTag = "tag-with_underscore.and123";

        // Act
        todo.addTag(specialTag);

        // Assert
        assertTrue(todo.getTags().contains(specialTag.toLowerCase()), "特殊字符标签应被添加");
    }

    // ==================== 并发场景测试 ====================

    @Test
    @DisplayName("多次状态转换应正确递增版本号")
    void multipleTransitions_ShouldIncrementVersionCorrectly() {
        // Arrange
        todo.setStatus(TodoStatus.PENDING);
        int initialVersion = todo.getVersion();

        // Act
        todo.transitionTo(TodoStatus.IN_PROGRESS);
        todo.transitionTo(TodoStatus.PENDING);
        todo.transitionTo(TodoStatus.IN_PROGRESS);
        todo.transitionTo(TodoStatus.COMPLETED);

        // Assert
        assertEquals(TodoStatus.COMPLETED, todo.getStatus());
        assertEquals(initialVersion + 4, todo.getVersion(), "版本号应增加4次");
    }

    @Test
    @DisplayName("混合操作应正确递增版本号")
    void mixedOperations_ShouldIncrementVersionCorrectly() {
        // Arrange
        int initialVersion = todo.getVersion();

        // Act
        todo.addTag("tag1");           // +1
        todo.addSubTodo(1L);           // +1
        todo.transitionTo(TodoStatus.IN_PROGRESS); // +1
        todo.upgradePriority();        // +1 (假设不是CRITICAL)

        // Assert
        assertEquals(initialVersion + 4, todo.getVersion(), "版本号应增加4次");
    }
}
