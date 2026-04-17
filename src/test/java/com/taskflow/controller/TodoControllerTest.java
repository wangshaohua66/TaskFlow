package com.taskflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.taskflow.config.SessionManager;
import com.taskflow.exception.BusinessRuleException;
import com.taskflow.exception.OptimisticLockException;
import com.taskflow.exception.ResourceNotFoundException;
import com.taskflow.model.*;
import com.taskflow.service.TodoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.*;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TodoController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("TodoController控制器层测试")
class TodoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TodoService todoService;

    @MockBean
    private SessionManager sessionManager;

    private ObjectMapper objectMapper;
    private Todo testTodo;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        testTodo = new Todo("Test Task", "Test Description", Priority.HIGH, LocalDateTime.now().plusDays(7));
        testTodo.setId(1L);
        testTodo.setVersion(0);

        User testUser = new User("testuser", "password", "test@example.com", "Test User");
        testUser.setId(1L);
        SessionManager.Session testSession = new SessionManager.Session("valid-token", testUser);
        when(sessionManager.getSession(anyString())).thenReturn(testSession);
    }

    @Nested
    @DisplayName("创建任务测试")
    class CreateTodoTests {

        @Test
        @DisplayName("POST /api/todos - 有效数据 - 返回201 Created")
        void createTodo_ValidData_Returns201Created() throws Exception {
            when(todoService.createTodo(
                    anyString(), anyString(), any(), any(), any(), any(), any()
            )).thenReturn(testTodo);

            TodoController.CreateTodoRequest request = new TodoController.CreateTodoRequest();
            request.setTitle("Test Task");
            request.setDescription("Test Description");
            request.setPriority(Priority.HIGH);

            mockMvc.perform(post("/api/todos")
                            .header("Authorization", "Bearer valid-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id", is(testTodo.getId().intValue())))
                    .andExpect(jsonPath("$.title", is("Test Task")));
        }

        @Test
        @DisplayName("POST /api/todos - 标题为空 - 返回400 Bad Request")
        void createTodo_NullTitle_Returns400BadRequest() throws Exception {
            when(todoService.createTodo(
                    any(), any(), any(), any(), any(), any(), any()
            )).thenThrow(new IllegalArgumentException("标题不能为空"));

            TodoController.CreateTodoRequest request = new TodoController.CreateTodoRequest();
            request.setTitle(null);

            mockMvc.perform(post("/api/todos")
                            .header("Authorization", "Bearer valid-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("POST /api/todos - Unicode特殊字符标题 - 返回201 Created")
        void createTodo_UnicodeAndSpecialCharsTitle_Returns201Created() throws Exception {
            Todo todoWithSpecialChars = new Todo("任务🚀!@#$%中文", null, Priority.MEDIUM, null);
            todoWithSpecialChars.setId(1L);

            when(todoService.createTodo(
                    eq("任务🚀!@#$%中文"), any(), any(), any(), any(), any(), any()
            )).thenReturn(todoWithSpecialChars);

            TodoController.CreateTodoRequest request = new TodoController.CreateTodoRequest();
            request.setTitle("任务🚀!@#$%中文");
            request.setPriority(Priority.MEDIUM);

            mockMvc.perform(post("/api/todos")
                            .header("Authorization", "Bearer valid-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.title", is("任务🚀!@#$%中文")));
        }

        @Test
        @DisplayName("POST /api/todos - 超长标题 - 返回400 Bad Request")
        void createTodo_TitleTooLong_Returns400BadRequest() throws Exception {
            when(todoService.createTodo(
                    any(), any(), any(), any(), any(), any(), any()
            )).thenThrow(new IllegalArgumentException("标题长度不能超过"));

            TodoController.CreateTodoRequest request = new TodoController.CreateTodoRequest();
            request.setTitle(repeatString("A", 201));
            request.setPriority(Priority.MEDIUM);

            mockMvc.perform(post("/api/todos")
                            .header("Authorization", "Bearer valid-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    private static String repeatString(String str, int times) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < times; i++) {
            sb.append(str);
        }
        return sb.toString();
    }

    @Nested
    @DisplayName("查询任务测试")
    class GetTodoTests {

        @Test
        @DisplayName("GET /api/todos/{id} - 存在的ID - 返回200 OK")
        void getTodo_ExistingId_Returns200Ok() throws Exception {
            when(todoService.getTodoById(1L)).thenReturn(testTodo);

            mockMvc.perform(get("/api/todos/1")
                            .header("Authorization", "Bearer valid-token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(1)))
                    .andExpect(jsonPath("$.title", is("Test Task")));
        }

        @Test
        @DisplayName("GET /api/todos/{id} - 不存在的ID - 返回404 Not Found")
        void getTodo_NonExistentId_Returns404NotFound() throws Exception {
            when(todoService.getTodoById(999L)).thenThrow(new ResourceNotFoundException("Todo", 999L));

            mockMvc.perform(get("/api/todos/999")
                            .header("Authorization", "Bearer valid-token"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("GET /api/todos - 获取列表 - 返回200 OK")
        void getAllTodos_Returns200Ok() throws Exception {
            List<Todo> todos = Arrays.asList(testTodo);
            PageResult<Todo> pageResult = new PageResult<>(todos, 0, 20, 1);
            when(todoService.searchTodos(any(SearchCriteria.class))).thenReturn(pageResult);

            mockMvc.perform(get("/api/todos")
                            .header("Authorization", "Bearer valid-token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.totalElements", is(1)));
        }
    }

    @Nested
    @DisplayName("更新任务测试")
    class UpdateTodoTests {

        @Test
        @DisplayName("PUT /api/todos/{id} - 有效数据 - 返回200 OK")
        void updateTodo_ValidData_Returns200Ok() throws Exception {
            when(todoService.updateTodo(
                    eq(1L), anyString(), anyString(), any(), any(), any(), eq(0)
            )).thenReturn(testTodo);

            TodoController.UpdateTodoRequest request = new TodoController.UpdateTodoRequest();
            request.setTitle("Updated Title");
            request.setVersion(0);

            mockMvc.perform(put("/api/todos/1")
                            .header("Authorization", "Bearer valid-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("PUT /api/todos/{id} - 版本冲突 - 返回409 Conflict")
        void updateTodo_VersionMismatch_Returns409Conflict() throws Exception {
            when(todoService.updateTodo(
                    anyLong(), any(), any(), any(), any(), any(), anyInt()
            )).thenThrow(new OptimisticLockException(1L));

            TodoController.UpdateTodoRequest request = new TodoController.UpdateTodoRequest();
            request.setTitle("Updated Title");
            request.setVersion(0);

            mockMvc.perform(put("/api/todos/1")
                            .header("Authorization", "Bearer valid-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").exists());
        }

        @Test
        @DisplayName("PUT /api/todos/{id} - 业务规则违反 - 返回400 Bad Request")
        void updateTodo_BusinessRuleViolation_Returns400BadRequest() throws Exception {
            when(todoService.updateTodo(
                    anyLong(), any(), any(), any(), any(), any(), anyInt()
            )).thenThrow(new BusinessRuleException("已完成的任务不能修改"));

            TodoController.UpdateTodoRequest request = new TodoController.UpdateTodoRequest();
            request.setTitle("Updated Title");
            request.setVersion(0);

            mockMvc.perform(put("/api/todos/1")
                            .header("Authorization", "Bearer valid-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").exists());
        }

        @Test
        @DisplayName("PATCH /api/todos/{id} - 缺少版本号 - 返回400 Bad Request")
        void patchTodo_MissingVersion_Returns400BadRequest() throws Exception {
            Map<String, Object> updates = new HashMap<>();
            updates.put("title", "Patched Title");

            mockMvc.perform(patch("/api/todos/1")
                            .header("Authorization", "Bearer valid-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updates)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").exists());
        }

        @Test
        @DisplayName("PATCH /api/todos/{id} - 版本冲突 - 返回409 Conflict")
        void patchTodo_VersionMismatch_Returns409Conflict() throws Exception {
            when(todoService.patchTodo(
                    anyLong(), anyMap(), anyInt()
            )).thenThrow(new OptimisticLockException(1L));

            Map<String, Object> updates = new HashMap<>();
            updates.put("title", "Patched Title");
            updates.put("version", 0);

            mockMvc.perform(patch("/api/todos/1")
                            .header("Authorization", "Bearer valid-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updates)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").exists());
        }
    }

    @Nested
    @DisplayName("删除任务测试")
    class DeleteTodoTests {

        @Test
        @DisplayName("DELETE /api/todos/{id} - 成功删除 - 返回204 No Content")
        void deleteTodo_Successful_Returns204NoContent() throws Exception {
            mockMvc.perform(delete("/api/todos/1")
                            .header("Authorization", "Bearer valid-token"))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("DELETE /api/todos/{id} - 不存在的ID - 返回404 Not Found")
        void deleteTodo_NonExistentId_Returns404NotFound() throws Exception {
            doThrow(new ResourceNotFoundException("Todo", 999L)).when(todoService).deleteTodo(999L);

            mockMvc.perform(delete("/api/todos/999")
                            .header("Authorization", "Bearer valid-token"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("DELETE /api/todos/{id} - 有未完成子任务 - 返回400 Bad Request")
        void deleteTodo_WithIncompleteSubTodos_Returns400BadRequest() throws Exception {
            doThrow(new BusinessRuleException("存在未完成的子任务")).when(todoService).deleteTodo(1L);

            mockMvc.perform(delete("/api/todos/1")
                            .header("Authorization", "Bearer valid-token"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("状态更改测试")
    class ChangeStatusTests {

        @Test
        @DisplayName("PATCH /api/todos/{id}/status - 合法转换 - 返回200 OK")
        void changeStatus_ValidTransition_Returns200Ok() throws Exception {
            testTodo.setStatus(TodoStatus.IN_PROGRESS);
            when(todoService.changeStatus(eq(1L), eq(TodoStatus.IN_PROGRESS), any())).thenReturn(testTodo);

            TodoController.ChangeStatusRequest request = new TodoController.ChangeStatusRequest();
            request.setStatus(TodoStatus.IN_PROGRESS);

            mockMvc.perform(patch("/api/todos/1/status")
                            .header("Authorization", "Bearer valid-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is(TodoStatus.IN_PROGRESS.name())));
        }

        @Test
        @DisplayName("PATCH /api/todos/{id}/status - 非法转换 - 返回400 Bad Request")
        void changeStatus_InvalidTransition_Returns400BadRequest() throws Exception {
            when(todoService.changeStatus(eq(1L), eq(TodoStatus.COMPLETED), any()))
                    .thenThrow(new BusinessRuleException("不允许的状态转换"));

            TodoController.ChangeStatusRequest request = new TodoController.ChangeStatusRequest();
            request.setStatus(TodoStatus.COMPLETED);

            mockMvc.perform(patch("/api/todos/1/status")
                            .header("Authorization", "Bearer valid-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").exists());
        }

        @Test
        @DisplayName("PATCH /api/todos/{id}/status - 不存在的ID - 返回404 Not Found")
        void changeStatus_NonExistentId_Returns404NotFound() throws Exception {
            when(todoService.changeStatus(eq(999L), any(), any()))
                    .thenThrow(new ResourceNotFoundException("Todo", 999L));

            TodoController.ChangeStatusRequest request = new TodoController.ChangeStatusRequest();
            request.setStatus(TodoStatus.IN_PROGRESS);

            mockMvc.perform(patch("/api/todos/999/status")
                            .header("Authorization", "Bearer valid-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("标签管理测试")
    class TagManagementTests {

        @Test
        @DisplayName("POST /api/todos/{id}/tags - 有效标签 - 返回200 OK")
        void addTag_ValidTag_Returns200Ok() throws Exception {
            testTodo.addTag("urgent");
            when(todoService.addTag(eq(1L), eq("urgent"))).thenReturn(testTodo);

            Map<String, String> request = new HashMap<>();
            request.put("tag", "urgent");

            mockMvc.perform(post("/api/todos/1/tags")
                            .header("Authorization", "Bearer valid-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.tags", hasSize(1)));
        }

        @Test
        @DisplayName("POST /api/todos/{id}/tags - 空标签 - 返回400 Bad Request")
        void addTag_EmptyTag_Returns400BadRequest() throws Exception {
            Map<String, String> request = new HashMap<>();
            request.put("tag", "");

            mockMvc.perform(post("/api/todos/1/tags")
                            .header("Authorization", "Bearer valid-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").exists());
        }

        @Test
        @DisplayName("DELETE /api/todos/{id}/tags/{tag} - 成功删除 - 返回200 OK")
        void removeTag_Successful_Returns200Ok() throws Exception {
            when(todoService.removeTag(eq(1L), eq("work"))).thenReturn(testTodo);

            mockMvc.perform(delete("/api/todos/1/tags/work")
                            .header("Authorization", "Bearer valid-token"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("子任务管理测试")
    class SubTodoManagementTests {

        @Test
        @DisplayName("POST /api/todos/{parentId}/subtodos - 有效子任务 - 返回200 OK")
        void addSubTodo_Valid_Returns200Ok() throws Exception {
            when(todoService.addSubTodo(eq(1L), eq(2L))).thenReturn(testTodo);

            Map<String, Long> request = new HashMap<>();
            request.put("subTodoId", 2L);

            mockMvc.perform(post("/api/todos/1/subtodos")
                            .header("Authorization", "Bearer valid-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("POST /api/todos/{parentId}/subtodos - 循环依赖 - 返回400 Bad Request")
        void addSubTodo_CircularDependency_Returns400BadRequest() throws Exception {
            when(todoService.addSubTodo(eq(1L), eq(2L)))
                    .thenThrow(new BusinessRuleException("不能添加会导致循环依赖的子任务"));

            Map<String, Long> request = new HashMap<>();
            request.put("subTodoId", 2L);

            mockMvc.perform(post("/api/todos/1/subtodos")
                            .header("Authorization", "Bearer valid-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").exists());
        }

        @Test
        @DisplayName("POST /api/todos/{parentId}/subtodos - 缺少子任务ID - 返回400 Bad Request")
        void addSubTodo_MissingSubTodoId_Returns400BadRequest() throws Exception {
            Map<String, Long> request = new HashMap<>();

            mockMvc.perform(post("/api/todos/1/subtodos")
                            .header("Authorization", "Bearer valid-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").exists());
        }
    }

    @Nested
    @DisplayName("搜索和统计测试")
    class SearchAndStatisticsTests {

        @Test
        @DisplayName("GET /api/todos/search - 关键词搜索 - 返回200 OK")
        void search_WithKeyword_Returns200Ok() throws Exception {
            List<Todo> todos = Collections.singletonList(testTodo);
            PageResult<Todo> pageResult = new PageResult<>(todos, 0, 20, 1);
            when(todoService.searchTodos(any(SearchCriteria.class))).thenReturn(pageResult);

            mockMvc.perform(get("/api/todos/search")
                            .header("Authorization", "Bearer valid-token")
                            .param("keyword", "Test"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)));
        }

        @Test
        @DisplayName("GET /api/todos/statistics - 获取统计 - 返回200 OK")
        void getStatistics_Returns200Ok() throws Exception {
            Map<String, Object> stats = new HashMap<>();
            stats.put("total", 10);
            stats.put("pending", 5L);
            stats.put("completed", 3L);
            when(todoService.getStatistics()).thenReturn(stats);

            mockMvc.perform(get("/api/todos/statistics")
                            .header("Authorization", "Bearer valid-token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total", is(10)))
                    .andExpect(jsonPath("$.pending", is(5)));
        }
    }

    @Nested
    @DisplayName("高级功能测试")
    class AdvancedFeaturesTests {

        @Test
        @DisplayName("POST /api/todos/upgrade-expired - 升级过期任务优先级 - 返回200 OK")
        void upgradeExpiredTodos_Returns200Ok() throws Exception {
            Todo todo1 = new Todo("Expired 1", null, Priority.MEDIUM, null);
            Todo todo2 = new Todo("Expired 2", null, Priority.HIGH, null);
            when(todoService.upgradeExpiredTodosPriority()).thenReturn(Arrays.asList(todo1, todo2));

            mockMvc.perform(post("/api/todos/upgrade-expired")
                            .header("Authorization", "Bearer valid-token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)));
        }

        @Test
        @DisplayName("DELETE /api/todos/completed - 批量删除已完成 - 返回200 OK")
        void deleteCompletedTodos_Returns200Ok() throws Exception {
            when(todoService.deleteCompletedTodos()).thenReturn(5);

            mockMvc.perform(delete("/api/todos/completed")
                            .header("Authorization", "Bearer valid-token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.deletedCount", is(5)));
        }

        @Test
        @DisplayName("POST /api/todos/{id}/clone - 克隆任务 - 返回201 Created")
        void cloneTodo_Returns201Created() throws Exception {
            Todo clonedTodo = new Todo("Test Task (副本)", null, Priority.HIGH, null);
            clonedTodo.setId(2L);
            when(todoService.cloneTodo(1L)).thenReturn(clonedTodo);

            mockMvc.perform(post("/api/todos/1/clone")
                            .header("Authorization", "Bearer valid-token"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id", is(2)));
        }
    }
}
