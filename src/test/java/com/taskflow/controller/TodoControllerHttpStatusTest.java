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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * TodoController HTTP 状态码精确测试
 *
 * 精确验证 HTTP 状态码:
 * - 201 Created: 创建成功、克隆成功
 * - 200 OK: 查询/更新成功
 * - 204 No Content: 删除成功
 * - 400 Bad Request: 参数错误
 * - 404 Not Found: 资源不存在
 * - 409 Conflict: 乐观锁冲突
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "spring.profiles.active=test")
class TodoControllerHttpStatusTest {

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
    void setUp() throws Exception {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        sampleTodo = new Todo("测试任务", "测试描述", Priority.MEDIUM, LocalDateTime.now().plusDays(7));
        sampleTodo.setId(TODO_ID);
        sampleTodo.setStatus(TodoStatus.PENDING);

        // Mock AuthInterceptor to allow all requests
        when(authInterceptor.preHandle(any(), any(), any())).thenReturn(true);
    }

    // ==================== 201 Created 精确测试 ====================

    @Test
    @DisplayName("POST /api/todos 创建成功应返回 201 Created")
    void createTodo_Success_ShouldReturn201Created() throws Exception {
        when(todoService.createTodo(anyString(), any(), any(), any(), any(), any(), any()))
            .thenReturn(sampleTodo);

        String requestBody = "{\"title\":\"测试任务\",\"description\":\"测试描述\",\"priority\":\"MEDIUM\"}";

        MvcResult result = mockMvc.perform(post("/api/todos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andReturn();

        assertEquals(201, result.getResponse().getStatus(),
            "创建任务应该返回 201 Created");
    }

    @Test
    @DisplayName("POST /api/todos/{id}/clone 克隆成功应返回 201 Created")
    void cloneTodo_Success_ShouldReturn201Created() throws Exception {
        Todo clonedTodo = new Todo("测试任务 (副本)", "测试描述", Priority.MEDIUM, null);
        clonedTodo.setId(999L);
        when(todoService.cloneTodo(TODO_ID)).thenReturn(clonedTodo);

        MvcResult result = mockMvc.perform(post("/api/todos/{id}/clone", TODO_ID))
            .andReturn();

        assertEquals(201, result.getResponse().getStatus(),
            "克隆任务应该返回 201 Created");
    }

    // ==================== 200 OK 精确测试 ====================

    @Test
    @DisplayName("GET /api/todos 查询成功应返回 200 OK")
    void getAllTodos_Success_ShouldReturn200Ok() throws Exception {
        PageResult<Todo> pageResult = new PageResult<>(
            Collections.singletonList(sampleTodo), 0, 20, 1);
        when(todoService.searchTodos(any(SearchCriteria.class))).thenReturn(pageResult);

        MvcResult result = mockMvc.perform(get("/api/todos"))
            .andReturn();

        assertEquals(200, result.getResponse().getStatus(),
            "查询所有任务应该返回 200 OK");
    }

    @Test
    @DisplayName("GET /api/todos/{id} 查询成功应返回 200 OK")
    void getTodo_Success_ShouldReturn200Ok() throws Exception {
        when(todoService.getTodoById(TODO_ID)).thenReturn(sampleTodo);

        MvcResult result = mockMvc.perform(get("/api/todos/{id}", TODO_ID))
            .andReturn();

        assertEquals(200, result.getResponse().getStatus(),
            "根据ID查询应该返回 200 OK");
    }

    @Test
    @DisplayName("PUT /api/todos/{id} 更新成功应返回 200 OK")
    void updateTodo_Success_ShouldReturn200Ok() throws Exception {
        when(todoService.updateTodo(anyLong(), anyString(), any(), any(), any(), any(), anyInt()))
            .thenReturn(sampleTodo);

        String requestBody = "{\"title\":\"更新后的标题\",\"version\":0}";

        MvcResult result = mockMvc.perform(put("/api/todos/{id}", TODO_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andReturn();

        assertEquals(200, result.getResponse().getStatus(),
            "更新任务应该返回 200 OK");
    }

    @Test
    @DisplayName("PATCH /api/todos/{id} 部分更新成功应返回 200 OK")
    void patchTodo_Success_ShouldReturn200Ok() throws Exception {
        when(todoService.patchTodo(anyLong(), anyMap(), anyInt())).thenReturn(sampleTodo);

        String requestBody = "{\"title\":\"部分更新\",\"version\":0}";

        MvcResult result = mockMvc.perform(patch("/api/todos/{id}", TODO_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andReturn();

        assertEquals(200, result.getResponse().getStatus(),
            "部分更新应该返回 200 OK");
    }

    @Test
    @DisplayName("PATCH /api/todos/{id}/status 状态更新成功应返回 200 OK")
    void changeStatus_Success_ShouldReturn200Ok() throws Exception {
        when(todoService.changeStatus(anyLong(), any(TodoStatus.class), any()))
            .thenReturn(sampleTodo);

        String requestBody = "{\"status\":\"IN_PROGRESS\"}";

        MvcResult result = mockMvc.perform(patch("/api/todos/{id}/status", TODO_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andReturn();

        assertEquals(200, result.getResponse().getStatus(),
            "状态更新应该返回 200 OK");
    }

    @Test
    @DisplayName("POST /api/todos/{id}/tags 添加标签成功应返回 200 OK")
    void addTag_Success_ShouldReturn200Ok() throws Exception {
        when(todoService.addTag(TODO_ID, "newtag")).thenReturn(sampleTodo);

        String requestBody = "{\"tag\":\"newtag\"}";

        MvcResult result = mockMvc.perform(post("/api/todos/{id}/tags", TODO_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andReturn();

        assertEquals(200, result.getResponse().getStatus(),
            "添加标签应该返回 200 OK");
    }

    @Test
    @DisplayName("DELETE /api/todos/{id}/tags/{tag} 移除标签成功应返回 200 OK")
    void removeTag_Success_ShouldReturn200Ok() throws Exception {
        when(todoService.removeTag(TODO_ID, "existingtag")).thenReturn(sampleTodo);

        MvcResult result = mockMvc.perform(delete("/api/todos/{id}/tags/{tag}", TODO_ID, "existingtag"))
            .andReturn();

        assertEquals(200, result.getResponse().getStatus(),
            "移除标签应该返回 200 OK");
    }

    @Test
    @DisplayName("POST /api/todos/{parentId}/subtodos 添加子任务成功应返回 200 OK")
    void addSubTodo_Success_ShouldReturn200Ok() throws Exception {
        when(todoService.addSubTodo(1L, 2L)).thenReturn(sampleTodo);

        String requestBody = "{\"subTodoId\":2}";

        MvcResult result = mockMvc.perform(post("/api/todos/{parentId}/subtodos", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andReturn();

        assertEquals(200, result.getResponse().getStatus(),
            "添加子任务应该返回 200 OK");
    }

    @Test
    @DisplayName("DELETE /api/todos/{parentId}/subtodos/{subTodoId} 移除子任务成功应返回 200 OK")
    void removeSubTodo_Success_ShouldReturn200Ok() throws Exception {
        when(todoService.removeSubTodo(1L, 2L)).thenReturn(sampleTodo);

        MvcResult result = mockMvc.perform(delete("/api/todos/{parentId}/subtodos/{subTodoId}", 1L, 2L))
            .andReturn();

        assertEquals(200, result.getResponse().getStatus(),
            "移除子任务应该返回 200 OK");
    }

    @Test
    @DisplayName("GET /api/todos/search 搜索成功应返回 200 OK")
    void search_Success_ShouldReturn200Ok() throws Exception {
        PageResult<Todo> pageResult = new PageResult<>(
            Collections.singletonList(sampleTodo), 0, 20, 1);
        when(todoService.searchTodos(any(SearchCriteria.class))).thenReturn(pageResult);

        MvcResult result = mockMvc.perform(get("/api/todos/search")
                .param("keyword", "测试"))
            .andReturn();

        assertEquals(200, result.getResponse().getStatus(),
            "搜索应该返回 200 OK");
    }

    @Test
    @DisplayName("GET /api/todos/statistics 统计查询成功应返回 200 OK")
    void getStatistics_Success_ShouldReturn200Ok() throws Exception {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", 10L);
        when(todoService.getStatistics()).thenReturn(stats);

        MvcResult result = mockMvc.perform(get("/api/todos/statistics"))
            .andReturn();

        assertEquals(200, result.getResponse().getStatus(),
            "统计查询应该返回 200 OK");
    }

    @Test
    @DisplayName("POST /api/todos/upgrade-expired 升级过期任务成功应返回 200 OK")
    void upgradeExpiredTodos_Success_ShouldReturn200Ok() throws Exception {
        when(todoService.upgradeExpiredTodosPriority()).thenReturn(Collections.emptyList());

        MvcResult result = mockMvc.perform(post("/api/todos/upgrade-expired"))
            .andReturn();

        assertEquals(200, result.getResponse().getStatus(),
            "升级过期任务应该返回 200 OK");
    }

    @Test
    @DisplayName("DELETE /api/todos/completed 批量删除成功应返回 200 OK")
    void deleteCompletedTodos_Success_ShouldReturn200Ok() throws Exception {
        when(todoService.deleteCompletedTodos()).thenReturn(5);

        MvcResult result = mockMvc.perform(delete("/api/todos/completed"))
            .andReturn();

        assertEquals(200, result.getResponse().getStatus(),
            "批量删除已完成任务应该返回 200 OK");
    }

    // ==================== 204 No Content 精确测试 ====================

    @Test
    @DisplayName("DELETE /api/todos/{id} 删除成功应返回 204 No Content")
    void deleteTodo_Success_ShouldReturn204NoContent() throws Exception {
        doNothing().when(todoService).deleteTodo(TODO_ID);

        MvcResult result = mockMvc.perform(delete("/api/todos/{id}", TODO_ID))
            .andReturn();

        assertEquals(204, result.getResponse().getStatus(),
            "删除任务应该返回 204 No Content");
    }

    // ==================== 400 Bad Request 精确测试 ====================

    @Test
    @DisplayName("POST /api/todos 参数错误应返回 400 Bad Request")
    void createTodo_InvalidParams_ShouldReturn400BadRequest() throws Exception {
        when(todoService.createTodo(anyString(), any(), any(), any(), any(), any(), any()))
            .thenThrow(new IllegalArgumentException("标题不能为空"));

        String requestBody = "{\"title\":\"\",\"description\":\"测试描述\"}";

        MvcResult result = mockMvc.perform(post("/api/todos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andReturn();

        assertEquals(400, result.getResponse().getStatus(),
            "参数错误应该返回 400 Bad Request");
    }

    @Test
    @DisplayName("PATCH /api/todos/{id} 缺少版本号应返回 400 Bad Request")
    void patchTodo_MissingVersion_ShouldReturn400BadRequest() throws Exception {
        String requestBody = "{\"title\":\"部分更新\"}";  // 缺少 version

        MvcResult result = mockMvc.perform(patch("/api/todos/{id}", TODO_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andReturn();

        assertEquals(400, result.getResponse().getStatus(),
            "缺少版本号应该返回 400 Bad Request");
    }

    // ==================== 404 Not Found 精确测试 ====================

    @Test
    @DisplayName("GET /api/todos/{id} 资源不存在应返回 404 Not Found")
    void getTodo_NotFound_ShouldReturn404NotFound() throws Exception {
        when(todoService.getTodoById(999L))
            .thenThrow(new com.taskflow.exception.ResourceNotFoundException("任务", 999L));

        MvcResult result = mockMvc.perform(get("/api/todos/{id}", 999L))
            .andReturn();

        assertEquals(404, result.getResponse().getStatus(),
            "资源不存在应该返回 404 Not Found");
    }

    @Test
    @DisplayName("PUT /api/todos/{id} 资源不存在应返回 404 Not Found")
    void updateTodo_NotFound_ShouldReturn404NotFound() throws Exception {
        when(todoService.updateTodo(anyLong(), anyString(), any(), any(), any(), any(), anyInt()))
            .thenThrow(new com.taskflow.exception.ResourceNotFoundException("任务", 999L));

        String requestBody = "{\"title\":\"更新后的标题\",\"version\":0}";

        MvcResult result = mockMvc.perform(put("/api/todos/{id}", 999L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andReturn();

        assertEquals(404, result.getResponse().getStatus(),
            "资源不存在应该返回 404 Not Found");
    }

    // ==================== 409 Conflict 精确测试 ====================

    @Test
    @DisplayName("PUT /api/todos/{id} 乐观锁冲突应返回 409 Conflict")
    void updateTodo_OptimisticLock_ShouldReturn409Conflict() throws Exception {
        when(todoService.updateTodo(anyLong(), anyString(), any(), any(), any(), any(), anyInt()))
            .thenThrow(new com.taskflow.exception.OptimisticLockException(TODO_ID));

        String requestBody = "{\"title\":\"更新后的标题\",\"version\":0}";

        MvcResult result = mockMvc.perform(put("/api/todos/{id}", TODO_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andReturn();

        assertEquals(409, result.getResponse().getStatus(),
            "乐观锁冲突应该返回 409 Conflict");
    }

    @Test
    @DisplayName("PATCH /api/todos/{id} 乐观锁冲突应返回 409 Conflict")
    void patchTodo_OptimisticLock_ShouldReturn409Conflict() throws Exception {
        when(todoService.patchTodo(anyLong(), anyMap(), anyInt()))
            .thenThrow(new com.taskflow.exception.OptimisticLockException(TODO_ID));

        String requestBody = "{\"title\":\"部分更新\",\"version\":1}";

        MvcResult result = mockMvc.perform(patch("/api/todos/{id}", TODO_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andReturn();

        assertEquals(409, result.getResponse().getStatus(),
            "乐观锁冲突应该返回 409 Conflict");
    }
}
