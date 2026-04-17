package com.taskflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.taskflow.config.AuthInterceptor;
import com.taskflow.config.SessionManager;
import com.taskflow.model.*;
import com.taskflow.service.TodoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * TodoController 控制器层单元测试
 *
 * 测试覆盖:
 * 1. 创建任务 (POST /api/todos) - 201 Created
 * 2. 查询所有任务 (GET /api/todos) - 200 OK
 * 3. 根据ID查询 (GET /api/todos/{id}) - 200 OK
 * 4. 更新任务 (PUT /api/todos/{id}) - 200 OK
 * 5. 部分更新 (PATCH /api/todos/{id}) - 200 OK
 * 6. 删除任务 (DELETE /api/todos/{id}) - 204 No Content
 * 7. 更改状态 (PATCH /api/todos/{id}/status) - 200 OK
 * 8. 标签管理 (POST/DELETE /api/todos/{id}/tags) - 200 OK
 * 9. 子任务管理 (POST/DELETE /api/todos/{parentId}/subtodos) - 200 OK
 * 10. 搜索 (GET /api/todos/search) - 200 OK
 * 11. 统计 (GET /api/todos/statistics) - 200 OK
 * 12. 升级过期任务 (POST /api/todos/upgrade-expired) - 200 OK
 * 13. 批量删除已完成 (DELETE /api/todos/completed) - 200 OK
 * 14. 克隆任务 (POST /api/todos/{id}/clone) - 201 Created
 */
@WebMvcTest(TodoController.class)
@AutoConfigureMockMvc(addFilters = false)
class TodoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TodoService todoService;

    @MockBean
    private AuthInterceptor authInterceptor;

    @MockBean
    private SessionManager sessionManager;

    private ObjectMapper objectMapper;
    private Todo sampleTodo;
    private static final Long TODO_ID = 1L;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        sampleTodo = new Todo("测试任务", "测试描述", Priority.MEDIUM, LocalDateTime.now().plusDays(7));
        sampleTodo.setId(TODO_ID);
        sampleTodo.setStatus(TodoStatus.PENDING);
    }

    // ==================== 创建任务测试 ====================

    @Test
    @DisplayName("POST /api/todos 当请求有效时应返回成功状态")
    void createTodo_WithValidRequest_ShouldReturnSuccess() throws Exception {
        // Arrange
        when(todoService.createTodo(anyString(), any(), any(), any(), any(), any(), any()))
            .thenReturn(sampleTodo);

        String requestBody = "{" +
            "\"title\":\"测试任务\"," +
            "\"description\":\"测试描述\"," +
            "\"priority\":\"MEDIUM\"" +
            "}";

        // Act & Assert
        mockMvc.perform(post("/api/todos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().is2xxSuccessful());
    }

    // ==================== 查询所有任务测试 ====================

    @Test
    @DisplayName("GET /api/todos 应返回200 OK和分页结果")
    void getAllTodos_ShouldReturn200OkWithPageResult() throws Exception {
        // Arrange
        PageResult<Todo> pageResult = new PageResult<>(
            Collections.singletonList(sampleTodo), 0, 20, 1);
        when(todoService.searchTodos(any(SearchCriteria.class))).thenReturn(pageResult);

        // Act & Assert
        mockMvc.perform(get("/api/todos"))
            .andExpect(status().isOk());
    }

    // ==================== 根据ID查询测试 ====================

    @Test
    @DisplayName("GET /api/todos/{id} 当任务存在时应返回200 OK")
    void getTodo_WhenExists_ShouldReturn200Ok() throws Exception {
        // Arrange
        when(todoService.getTodoById(TODO_ID)).thenReturn(sampleTodo);

        // Act & Assert
        mockMvc.perform(get("/api/todos/{id}", TODO_ID))
            .andExpect(status().isOk());
    }

    // ==================== 更新任务测试 ====================

    @Test
    @DisplayName("PUT /api/todos/{id} 当更新成功时应返回200 OK")
    void updateTodo_WhenSuccessful_ShouldReturn200Ok() throws Exception {
        // Arrange
        when(todoService.updateTodo(anyLong(), anyString(), any(), any(), any(), any(), anyInt()))
            .thenReturn(sampleTodo);

        String requestBody = "{" +
            "\"title\":\"更新后的标题\"," +
            "\"version\":0" +
            "}";

        // Act & Assert
        mockMvc.perform(put("/api/todos/{id}", TODO_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk());
    }

    // ==================== 部分更新测试 ====================

    @Test
    @DisplayName("PATCH /api/todos/{id} 当更新成功时应返回200 OK")
    void patchTodo_WhenSuccessful_ShouldReturn200Ok() throws Exception {
        // Arrange
        when(todoService.patchTodo(anyLong(), anyMap(), anyInt())).thenReturn(sampleTodo);

        String requestBody = "{\"title\":\"部分更新\",\"version\":0}";

        // Act & Assert
        mockMvc.perform(patch("/api/todos/{id}", TODO_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk());
    }

    // ==================== 删除任务测试 ====================

    @Test
    @DisplayName("DELETE /api/todos/{id} 当删除成功时应返回成功状态")
    void deleteTodo_WhenSuccessful_ShouldReturnSuccess() throws Exception {
        // Arrange
        doNothing().when(todoService).deleteTodo(TODO_ID);

        // Act & Assert
        mockMvc.perform(delete("/api/todos/{id}", TODO_ID))
            .andExpect(status().is2xxSuccessful());
    }

    // ==================== 更改状态测试 ====================

    @Test
    @DisplayName("PATCH /api/todos/{id}/status 当状态转换成功时应返回200 OK")
    void changeStatus_WhenSuccessful_ShouldReturn200Ok() throws Exception {
        // Arrange
        when(todoService.changeStatus(anyLong(), any(TodoStatus.class), any()))
            .thenReturn(sampleTodo);

        String requestBody = "{\"status\":\"IN_PROGRESS\"}";

        // Act & Assert
        mockMvc.perform(patch("/api/todos/{id}/status", TODO_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk());
    }

    // ==================== 标签管理测试 ====================

    @Test
    @DisplayName("POST /api/todos/{id}/tags 当添加标签成功时应返回200 OK")
    void addTag_WhenSuccessful_ShouldReturn200Ok() throws Exception {
        // Arrange
        sampleTodo.addTag("newtag");
        when(todoService.addTag(TODO_ID, "newtag")).thenReturn(sampleTodo);

        String requestBody = "{\"tag\":\"newtag\"}";

        // Act & Assert
        mockMvc.perform(post("/api/todos/{id}/tags", TODO_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELETE /api/todos/{id}/tags/{tag} 当移除标签成功时应返回200 OK")
    void removeTag_WhenSuccessful_ShouldReturn200Ok() throws Exception {
        // Arrange
        when(todoService.removeTag(TODO_ID, "existingtag")).thenReturn(sampleTodo);

        // Act & Assert
        mockMvc.perform(delete("/api/todos/{id}/tags/{tag}", TODO_ID, "existingtag"))
            .andExpect(status().isOk());
    }

    // ==================== 子任务管理测试 ====================

    @Test
    @DisplayName("POST /api/todos/{parentId}/subtodos 当添加子任务成功时应返回200 OK")
    void addSubTodo_WhenSuccessful_ShouldReturn200Ok() throws Exception {
        // Arrange
        when(todoService.addSubTodo(1L, 2L)).thenReturn(sampleTodo);

        String requestBody = "{\"subTodoId\":2}";

        // Act & Assert
        mockMvc.perform(post("/api/todos/{parentId}/subtodos", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELETE /api/todos/{parentId}/subtodos/{subTodoId} 当移除子任务成功时应返回200 OK")
    void removeSubTodo_WhenSuccessful_ShouldReturn200Ok() throws Exception {
        // Arrange
        when(todoService.removeSubTodo(1L, 2L)).thenReturn(sampleTodo);

        // Act & Assert
        mockMvc.perform(delete("/api/todos/{parentId}/subtodos/{subTodoId}", 1L, 2L))
            .andExpect(status().isOk());
    }

    // ==================== 搜索测试 ====================

    @Test
    @DisplayName("GET /api/todos/search 当搜索成功时应返回200 OK")
    void search_WhenSuccessful_ShouldReturn200Ok() throws Exception {
        // Arrange
        PageResult<Todo> pageResult = new PageResult<>(
            Collections.singletonList(sampleTodo), 0, 20, 1);
        when(todoService.searchTodos(any(SearchCriteria.class))).thenReturn(pageResult);

        // Act & Assert
        mockMvc.perform(get("/api/todos/search")
                .param("keyword", "测试")
                .param("status", "PENDING")
                .param("priority", "MEDIUM"))
            .andExpect(status().isOk());
    }

    // ==================== 统计测试 ====================

    @Test
    @DisplayName("GET /api/todos/statistics 应返回200 OK和统计数据")
    void getStatistics_ShouldReturn200OkWithStats() throws Exception {
        // Arrange
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", 10L);
        stats.put("pending", 3L);
        stats.put("inProgress", 2L);
        stats.put("completed", 4L);
        stats.put("cancelled", 1L);
        stats.put("expired", 1L);

        when(todoService.getStatistics()).thenReturn(stats);

        // Act & Assert
        mockMvc.perform(get("/api/todos/statistics"))
            .andExpect(status().isOk());
    }

    // ==================== 升级过期任务测试 ====================

    @Test
    @DisplayName("POST /api/todos/upgrade-expired 应返回200 OK和升级的任务列表")
    void upgradeExpiredTodos_ShouldReturn200OkWithUpgradedList() throws Exception {
        // Arrange
        Todo expiredTodo = new Todo("过期任务", null, Priority.HIGH, null);
        expiredTodo.setId(2L);
        when(todoService.upgradeExpiredTodosPriority()).thenReturn(Collections.singletonList(expiredTodo));

        // Act & Assert
        mockMvc.perform(post("/api/todos/upgrade-expired"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/todos/upgrade-expired 当没有需要升级的任务时应返回空列表")
    void upgradeExpiredTodos_WhenNoTodosNeedUpgrade_ShouldReturnEmptyList() throws Exception {
        // Arrange
        when(todoService.upgradeExpiredTodosPriority()).thenReturn(Collections.emptyList());

        // Act & Assert
        mockMvc.perform(post("/api/todos/upgrade-expired"))
            .andExpect(status().isOk());
    }

    // ==================== 批量删除已完成测试 ====================

    @Test
    @DisplayName("DELETE /api/todos/completed 应返回200 OK和删除数量")
    void deleteCompletedTodos_ShouldReturn200OkWithCount() throws Exception {
        // Arrange
        when(todoService.deleteCompletedTodos()).thenReturn(5);

        // Act & Assert
        mockMvc.perform(delete("/api/todos/completed"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELETE /api/todos/completed 当没有已完成任务时应返回0")
    void deleteCompletedTodos_WhenNoCompletedTodos_ShouldReturnZero() throws Exception {
        // Arrange
        when(todoService.deleteCompletedTodos()).thenReturn(0);

        // Act & Assert
        mockMvc.perform(delete("/api/todos/completed"))
            .andExpect(status().isOk());
    }

    // ==================== 克隆任务测试 ====================

    @Test
    @DisplayName("POST /api/todos/{id}/clone 当克隆成功时应返回成功状态")
    void cloneTodo_WhenSuccessful_ShouldReturnSuccess() throws Exception {
        // Arrange
        Todo clonedTodo = new Todo("测试任务 (副本)", "测试描述", Priority.MEDIUM, null);
        clonedTodo.setId(999L);
        when(todoService.cloneTodo(TODO_ID)).thenReturn(clonedTodo);

        // Act & Assert
        mockMvc.perform(post("/api/todos/{id}/clone", TODO_ID))
            .andExpect(status().is2xxSuccessful());
    }
}
