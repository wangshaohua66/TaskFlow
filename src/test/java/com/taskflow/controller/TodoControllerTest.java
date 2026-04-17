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
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TodoController.class)
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
    private User testUser;
    private SessionManager.Session testSession;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        testTodo = new Todo("测试任务", "测试描述", Priority.MEDIUM, LocalDateTime.now().plusDays(7));
        testTodo.setId(1L);
        testTodo.setVersion(0);

        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");

        testSession = mock(SessionManager.Session.class);
        when(testSession.getUser()).thenReturn(testUser);
        when(testSession.isExpired()).thenReturn(false);
        when(sessionManager.getSession(anyString())).thenReturn(testSession);
    }

    private static final String AUTH_HEADER = "Bearer test-token";

    @Nested
    @DisplayName("创建Todo API测试")
    class CreateTodoApiTests {

        @Test
        @DisplayName("POST /api/todos - 创建成功应返回201")
        void createTodo_Success_ShouldReturn201() throws Exception {
            Map<String, Object> request = new HashMap<>();
            request.put("title", "新任务");
            request.put("description", "描述");
            request.put("priority", "HIGH");

            when(todoService.createTodo(anyString(), any(), any(Priority.class),
                any(), any(), any(), any()))
                .thenReturn(testTodo);

            mockMvc.perform(post("/api/todos")
                    .header("Authorization", AUTH_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("测试任务"));
        }

        @Test
        @DisplayName("POST /api/todos - 参数无效应返回400")
        void createTodo_InvalidInput_ShouldReturn400() throws Exception {
            Map<String, Object> request = new HashMap<>();
            request.put("title", "");

            when(todoService.createTodo(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("标题不能为空"));

            mockMvc.perform(post("/api/todos")
                    .header("Authorization", AUTH_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("查询Todo API测试")
    class GetTodoApiTests {

        @Test
        @DisplayName("GET /api/todos - 查询所有应返回200")
        void getAllTodos_ShouldReturn200() throws Exception {
            PageResult<Todo> pageResult = new PageResult<>(
                Arrays.asList(testTodo), 0, 20, 1
            );

            when(todoService.searchTodos(any(SearchCriteria.class))).thenReturn(pageResult);

            mockMvc.perform(get("/api/todos")
                    .header("Authorization", AUTH_HEADER)
                    .param("page", "0")
                    .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("GET /api/todos/{id} - 查询存在的Todo应返回200")
        void getTodoById_ExistingId_ShouldReturn200() throws Exception {
            when(todoService.getTodoById(1L)).thenReturn(testTodo);

            mockMvc.perform(get("/api/todos/1")
                .header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("测试任务"));
        }

        @Test
        @DisplayName("GET /api/todos/{id} - 查询不存在的Todo应返回404")
        void getTodoById_NonExistingId_ShouldReturn404() throws Exception {
            when(todoService.getTodoById(anyLong()))
                .thenThrow(new ResourceNotFoundException("Todo", 999L));

            mockMvc.perform(get("/api/todos/999")
                .header("Authorization", AUTH_HEADER))
                .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("更新Todo API测试")
    class UpdateTodoApiTests {

        @Test
        @DisplayName("PUT /api/todos/{id} - 更新成功应返回200")
        void updateTodo_Success_ShouldReturn200() throws Exception {
            Map<String, Object> request = new HashMap<>();
            request.put("title", "更新标题");
            request.put("description", "更新描述");
            request.put("priority", "HIGH");
            request.put("version", 0);

            when(todoService.updateTodo(eq(1L), anyString(), anyString(),
                any(Priority.class), any(), any(), eq(0)))
                .thenReturn(testTodo);

            mockMvc.perform(put("/api/todos/1")
                    .header("Authorization", AUTH_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("PUT /api/todos/{id} - 资源不存在应返回404")
        void updateTodo_NotFound_ShouldReturn404() throws Exception {
            Map<String, Object> request = new HashMap<>();
            request.put("title", "更新标题");
            request.put("version", 0);

            when(todoService.updateTodo(anyLong(), any(), any(), any(), any(), any(), anyInt()))
                .thenThrow(new ResourceNotFoundException("Todo", 999L));

            mockMvc.perform(put("/api/todos/999")
                    .header("Authorization", AUTH_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("PUT /api/todos/{id} - 乐观锁冲突应返回409")
        void updateTodo_OptimisticLock_ShouldReturn409() throws Exception {
            Map<String, Object> request = new HashMap<>();
            request.put("title", "更新标题");
            request.put("version", 0);

            when(todoService.updateTodo(anyLong(), any(), any(), any(), any(), any(), anyInt()))
                .thenThrow(new OptimisticLockException(1L));

            mockMvc.perform(put("/api/todos/1")
                    .header("Authorization", AUTH_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("PUT /api/todos/{id} - 业务规则违反应返回400")
        void updateTodo_BusinessRuleViolation_ShouldReturn400() throws Exception {
            Map<String, Object> request = new HashMap<>();
            request.put("title", "更新标题");
            request.put("version", 0);

            when(todoService.updateTodo(anyLong(), any(), any(), any(), any(), any(), anyInt()))
                .thenThrow(new BusinessRuleException("已完成的任务不能修改"));

            mockMvc.perform(put("/api/todos/1")
                    .header("Authorization", AUTH_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("部分更新Todo API测试")
    class PatchTodoApiTests {

        @Test
        @DisplayName("PATCH /api/todos/{id} - 部分更新成功应返回200")
        void patchTodo_Success_ShouldReturn200() throws Exception {
            Map<String, Object> request = new HashMap<>();
            request.put("title", "新标题");
            request.put("version", 0);

            when(todoService.patchTodo(eq(1L), anyMap(), eq(0))).thenReturn(testTodo);

            mockMvc.perform(patch("/api/todos/1")
                    .header("Authorization", AUTH_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("PATCH /api/todos/{id} - 缺少版本号应返回400")
        void patchTodo_MissingVersion_ShouldReturn400() throws Exception {
            Map<String, Object> request = new HashMap<>();
            request.put("title", "新标题");

            mockMvc.perform(patch("/api/todos/1")
                    .header("Authorization", AUTH_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("PATCH /api/todos/{id} - 乐观锁冲突应返回409")
        void patchTodo_OptimisticLock_ShouldReturn409() throws Exception {
            Map<String, Object> request = new HashMap<>();
            request.put("title", "新标题");
            request.put("version", 0);

            when(todoService.patchTodo(anyLong(), anyMap(), anyInt()))
                .thenThrow(new OptimisticLockException(1L));

            mockMvc.perform(patch("/api/todos/1")
                    .header("Authorization", AUTH_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
        }
    }

    @Nested
    @DisplayName("删除Todo API测试")
    class DeleteTodoApiTests {

        @Test
        @DisplayName("DELETE /api/todos/{id} - 删除成功应返回204")
        void deleteTodo_Success_ShouldReturn204() throws Exception {
            doNothing().when(todoService).deleteTodo(1L);

            mockMvc.perform(delete("/api/todos/1")
                .header("Authorization", AUTH_HEADER))
                .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("DELETE /api/todos/{id} - 资源不存在应返回404")
        void deleteTodo_NotFound_ShouldReturn404() throws Exception {
            doThrow(new ResourceNotFoundException("Todo", 999L))
                .when(todoService).deleteTodo(999L);

            mockMvc.perform(delete("/api/todos/999")
                .header("Authorization", AUTH_HEADER))
                .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("状态变更API测试")
    class ChangeStatusApiTests {

        @Test
        @DisplayName("PATCH /api/todos/{id}/status - 状态变更成功应返回200")
        void changeStatus_Success_ShouldReturn200() throws Exception {
            Map<String, Object> request = new HashMap<>();
            request.put("status", "IN_PROGRESS");

            testTodo.setStatus(TodoStatus.IN_PROGRESS);
            when(todoService.changeStatus(eq(1L), eq(TodoStatus.IN_PROGRESS), any())).thenReturn(testTodo);

            mockMvc.perform(patch("/api/todos/1/status")
                    .header("Authorization", AUTH_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("PATCH /api/todos/{id}/status - 非法状态转换应返回400")
        void changeStatus_InvalidTransition_ShouldReturn400() throws Exception {
            Map<String, Object> request = new HashMap<>();
            request.put("status", "COMPLETED");

            when(todoService.changeStatus(anyLong(), any(TodoStatus.class), any()))
                .thenThrow(new BusinessRuleException("不允许从 PENDING 转换到 COMPLETED"));

            mockMvc.perform(patch("/api/todos/1/status")
                    .header("Authorization", AUTH_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("PATCH /api/todos/{id}/status - 资源不存在应返回404")
        void changeStatus_NotFound_ShouldReturn404() throws Exception {
            Map<String, Object> request = new HashMap<>();
            request.put("status", "IN_PROGRESS");

            when(todoService.changeStatus(anyLong(), any(TodoStatus.class), any()))
                .thenThrow(new ResourceNotFoundException("Todo", 999L));

            mockMvc.perform(patch("/api/todos/999/status")
                    .header("Authorization", AUTH_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("标签管理API测试")
    class TagApiTests {

        @Test
        @DisplayName("POST /api/todos/{id}/tags - 添加标签成功应返回200")
        void addTag_Success_ShouldReturn200() throws Exception {
            Map<String, String> request = new HashMap<>();
            request.put("tag", "work");

            testTodo.addTag("work");
            when(todoService.addTag(1L, "work")).thenReturn(testTodo);

            mockMvc.perform(post("/api/todos/1/tags")
                    .header("Authorization", AUTH_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("POST /api/todos/{id}/tags - 标签为空应返回400")
        void addTag_EmptyTag_ShouldReturn400() throws Exception {
            Map<String, String> request = new HashMap<>();
            request.put("tag", "");

            mockMvc.perform(post("/api/todos/1/tags")
                    .header("Authorization", AUTH_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("POST /api/todos/{id}/tags - 缺少tag字段应返回400")
        void addTag_MissingTag_ShouldReturn400() throws Exception {
            Map<String, String> request = new HashMap<>();

            mockMvc.perform(post("/api/todos/1/tags")
                    .header("Authorization", AUTH_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("DELETE /api/todos/{id}/tags/{tag} - 移除标签成功应返回200")
        void removeTag_Success_ShouldReturn200() throws Exception {
            when(todoService.removeTag(1L, "work")).thenReturn(testTodo);

            mockMvc.perform(delete("/api/todos/1/tags/work")
                .header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("DELETE /api/todos/{id}/tags/{tag} - 资源不存在应返回404")
        void removeTag_NotFound_ShouldReturn404() throws Exception {
            when(todoService.removeTag(anyLong(), anyString()))
                .thenThrow(new ResourceNotFoundException("Todo", 999L));

            mockMvc.perform(delete("/api/todos/999/tags/work")
                .header("Authorization", AUTH_HEADER))
                .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("子任务管理API测试")
    class SubTodoApiTests {

        @Test
        @DisplayName("POST /api/todos/{parentId}/subtodos - 添加子任务成功应返回200")
        void addSubTodo_Success_ShouldReturn200() throws Exception {
            Map<String, Long> request = new HashMap<>();
            request.put("subTodoId", 2L);

            when(todoService.addSubTodo(1L, 2L)).thenReturn(testTodo);

            mockMvc.perform(post("/api/todos/1/subtodos")
                    .header("Authorization", AUTH_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("POST /api/todos/{parentId}/subtodos - 缺少subTodoId应返回400")
        void addSubTodo_MissingSubTodoId_ShouldReturn400() throws Exception {
            Map<String, Long> request = new HashMap<>();

            mockMvc.perform(post("/api/todos/1/subtodos")
                    .header("Authorization", AUTH_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("POST /api/todos/{parentId}/subtodos - 循环依赖应返回400")
        void addSubTodo_CircularDependency_ShouldReturn400() throws Exception {
            Map<String, Long> request = new HashMap<>();
            request.put("subTodoId", 2L);

            when(todoService.addSubTodo(1L, 2L))
                .thenThrow(new BusinessRuleException("不能添加会导致循环依赖的子任务"));

            mockMvc.perform(post("/api/todos/1/subtodos")
                    .header("Authorization", AUTH_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("DELETE /api/todos/{parentId}/subtodos/{subTodoId} - 移除子任务成功应返回200")
        void removeSubTodo_Success_ShouldReturn200() throws Exception {
            when(todoService.removeSubTodo(1L, 2L)).thenReturn(testTodo);

            mockMvc.perform(delete("/api/todos/1/subtodos/2")
                .header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("搜索API测试")
    class SearchApiTests {

        @Test
        @DisplayName("GET /api/todos/search - 搜索成功应返回200")
        void search_Success_ShouldReturn200() throws Exception {
            PageResult<Todo> pageResult = new PageResult<>(
                Arrays.asList(testTodo), 0, 20, 1
            );

            when(todoService.searchTodos(any(SearchCriteria.class))).thenReturn(pageResult);

            mockMvc.perform(get("/api/todos/search")
                    .header("Authorization", AUTH_HEADER)
                    .param("keyword", "测试")
                    .param("status", "PENDING")
                    .param("priority", "HIGH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
        }

        @Test
        @DisplayName("GET /api/todos/search - 无参数搜索应返回200")
        void search_NoParams_ShouldReturn200() throws Exception {
            PageResult<Todo> pageResult = new PageResult<>(
                Arrays.asList(testTodo), 0, 20, 1
            );

            when(todoService.searchTodos(any(SearchCriteria.class))).thenReturn(pageResult);

            mockMvc.perform(get("/api/todos/search")
                .header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("统计API测试")
    class StatisticsApiTests {

        @Test
        @DisplayName("GET /api/todos/statistics - 获取统计应返回200")
        void getStatistics_ShouldReturn200() throws Exception {
            Map<String, Object> stats = new HashMap<>();
            stats.put("total", 10);
            stats.put("completed", 5);
            stats.put("pending", 3);

            when(todoService.getStatistics()).thenReturn(stats);

            mockMvc.perform(get("/api/todos/statistics")
                .header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(10))
                .andExpect(jsonPath("$.completed").value(5));
        }
    }

    @Nested
    @DisplayName("升级过期任务优先级API测试")
    class UpgradeExpiredApiTests {

        @Test
        @DisplayName("POST /api/todos/upgrade-expired - 升级成功应返回200")
        void upgradeExpiredTodos_ShouldReturn200() throws Exception {
            when(todoService.upgradeExpiredTodosPriority())
                .thenReturn(Arrays.asList(testTodo));

            mockMvc.perform(post("/api/todos/upgrade-expired")
                .header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        }
    }

    @Nested
    @DisplayName("批量删除已完成任务API测试")
    class DeleteCompletedApiTests {

        @Test
        @DisplayName("DELETE /api/todos/completed - 删除成功应返回200")
        void deleteCompletedTodos_ShouldReturn200() throws Exception {
            when(todoService.deleteCompletedTodos()).thenReturn(5);

            mockMvc.perform(delete("/api/todos/completed")
                .header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deletedCount").value(5));
        }
    }

    @Nested
    @DisplayName("克隆任务API测试")
    class CloneTodoApiTests {

        @Test
        @DisplayName("POST /api/todos/{id}/clone - 克隆成功应返回201")
        void cloneTodo_Success_ShouldReturn201() throws Exception {
            Todo cloned = new Todo("测试任务 (副本)", "测试描述", Priority.MEDIUM, null);
            cloned.setId(2L);

            when(todoService.cloneTodo(1L)).thenReturn(cloned);

            mockMvc.perform(post("/api/todos/1/clone")
                .header("Authorization", AUTH_HEADER))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("测试任务 (副本)"));
        }

        @Test
        @DisplayName("POST /api/todos/{id}/clone - 资源不存在应返回404")
        void cloneTodo_NotFound_ShouldReturn404() throws Exception {
            when(todoService.cloneTodo(anyLong()))
                .thenThrow(new ResourceNotFoundException("Todo", 999L));

            mockMvc.perform(post("/api/todos/999/clone")
                .header("Authorization", AUTH_HEADER))
                .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("边界值和异常测试")
    class BoundaryAndExceptionTests {

        @Test
        @DisplayName("POST /api/todos - 标题为null应返回400")
        void createTodo_NullTitle_ShouldReturn400() throws Exception {
            Map<String, Object> request = new HashMap<>();
            request.put("description", "描述");

            when(todoService.createTodo(isNull(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("标题不能为空"));

            mockMvc.perform(post("/api/todos")
                    .header("Authorization", AUTH_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("POST /api/todos - 标题为空字符串应返回400")
        void createTodo_EmptyTitle_ShouldReturn400() throws Exception {
            Map<String, Object> request = new HashMap<>();
            request.put("title", "   ");

            when(todoService.createTodo(anyString(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("标题不能为空"));

            mockMvc.perform(post("/api/todos")
                    .header("Authorization", AUTH_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("POST /api/todos - 标题超长应返回400")
        void createTodo_TitleTooLong_ShouldReturn400() throws Exception {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 201; i++) sb.append("a");
            String longTitle = sb.toString();
            Map<String, Object> request = new HashMap<>();
            request.put("title", longTitle);

            when(todoService.createTodo(anyString(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("标题长度不能超过200个字符"));

            mockMvc.perform(post("/api/todos")
                    .header("Authorization", AUTH_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("POST /api/todos - 无效优先级应返回500")
        void createTodo_InvalidPriority_ShouldReturn500() throws Exception {
            Map<String, Object> request = new HashMap<>();
            request.put("title", "测试任务");
            request.put("priority", "INVALID_PRIORITY");

            mockMvc.perform(post("/api/todos")
                    .header("Authorization", AUTH_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError());
        }

        @Test
        @DisplayName("PATCH /api/todos/{id}/status - 无效状态应返回500")
        void changeStatus_InvalidStatus_ShouldReturn500() throws Exception {
            Map<String, Object> request = new HashMap<>();
            request.put("status", "INVALID_STATUS");

            mockMvc.perform(patch("/api/todos/1/status")
                    .header("Authorization", AUTH_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError());
        }

        @Test
        @DisplayName("PATCH /api/todos/{id}/status - 非法状态转换应返回400")
        void changeStatus_IllegalTransition_ShouldReturn400() throws Exception {
            Map<String, Object> request = new HashMap<>();
            request.put("status", "COMPLETED");

            when(todoService.changeStatus(anyLong(), any(TodoStatus.class), any()))
                .thenThrow(new BusinessRuleException("不允许的状态转换"));

            mockMvc.perform(patch("/api/todos/1/status")
                    .header("Authorization", AUTH_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("DELETE /api/todos/{id} - 有未完成子任务应返回400")
        void deleteTodo_HasIncompleteSubTodos_ShouldReturn400() throws Exception {
            doThrow(new BusinessRuleException("存在未完成的子任务，无法删除"))
                .when(todoService).deleteTodo(1L);

            mockMvc.perform(delete("/api/todos/1")
                .header("Authorization", AUTH_HEADER))
                .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("标签API边界测试")
    class TagApiBoundaryTests {

        @Test
        @DisplayName("POST /api/todos/{id}/tags - 标签数量超限应返回400")
        void addTag_TooManyTags_ShouldReturn400() throws Exception {
            Map<String, String> request = new HashMap<>();
            request.put("tag", "newTag");

            when(todoService.addTag(anyLong(), anyString()))
                .thenThrow(new IllegalArgumentException("标签数量已达上限"));

            mockMvc.perform(post("/api/todos/1/tags")
                    .header("Authorization", AUTH_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("POST /api/todos/{id}/tags - 重复标签应返回400")
        void addTag_DuplicateTag_ShouldReturn400() throws Exception {
            Map<String, String> request = new HashMap<>();
            request.put("tag", "existingTag");

            when(todoService.addTag(anyLong(), anyString()))
                .thenThrow(new IllegalArgumentException("标签已存在"));

            mockMvc.perform(post("/api/todos/1/tags")
                    .header("Authorization", AUTH_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("DELETE /api/todos/{id}/tags/{tag} - 资源不存在应返回404")
        void removeTag_NonExistingTodo_ShouldReturn404() throws Exception {
            when(todoService.removeTag(anyLong(), anyString()))
                .thenThrow(new ResourceNotFoundException("Todo", 1L));

            mockMvc.perform(delete("/api/todos/1/tags/work")
                .header("Authorization", AUTH_HEADER))
                .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("子任务API边界测试")
    class SubTodoApiBoundaryTests {

        @Test
        @DisplayName("POST /api/todos/{parentId}/subtodos - 子任务已有父任务应返回400")
        void addSubTodo_AlreadyHasParent_ShouldReturn400() throws Exception {
            Map<String, Long> request = new HashMap<>();
            request.put("subTodoId", 2L);

            when(todoService.addSubTodo(1L, 2L))
                .thenThrow(new BusinessRuleException("该任务已有父任务"));

            mockMvc.perform(post("/api/todos/1/subtodos")
                    .header("Authorization", AUTH_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("POST /api/todos/{parentId}/subtodos - 父任务不存在应返回404")
        void addSubTodo_ParentNotFound_ShouldReturn404() throws Exception {
            Map<String, Long> request = new HashMap<>();
            request.put("subTodoId", 2L);

            when(todoService.addSubTodo(1L, 2L))
                .thenThrow(new ResourceNotFoundException("Todo", 1L));

            mockMvc.perform(post("/api/todos/1/subtodos")
                    .header("Authorization", AUTH_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("POST /api/todos/{parentId}/subtodos - 子任务不存在应返回404")
        void addSubTodo_SubTodoNotFound_ShouldReturn404() throws Exception {
            Map<String, Long> request = new HashMap<>();
            request.put("subTodoId", 2L);

            when(todoService.addSubTodo(1L, 2L))
                .thenThrow(new ResourceNotFoundException("Todo", 2L));

            mockMvc.perform(post("/api/todos/1/subtodos")
                    .header("Authorization", AUTH_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("更新API边界测试")
    class UpdateApiBoundaryTests {

        @Test
        @DisplayName("PATCH /api/todos/{id} - 版本为null应返回400")
        void patchTodo_NullVersion_ShouldReturn400() throws Exception {
            Map<String, Object> request = new HashMap<>();
            request.put("title", "新标题");
            request.put("version", null);

            mockMvc.perform(patch("/api/todos/1")
                    .header("Authorization", AUTH_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("优先级升级API测试")
    class PriorityUpgradeApiTests {

        @Test
        @DisplayName("POST /api/todos/upgrade-expired - 升级成功应返回200")
        void upgradeExpiredTodos_Success_ShouldReturn200() throws Exception {
            Todo expiredTodo = new Todo("过期任务", null, Priority.HIGH, null);
            expiredTodo.setId(1L);

            when(todoService.upgradeExpiredTodosPriority())
                .thenReturn(Arrays.asList(expiredTodo));

            mockMvc.perform(post("/api/todos/upgrade-expired")
                .header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
        }

        @Test
        @DisplayName("POST /api/todos/upgrade-expired - 无过期任务应返回空数组")
        void upgradeExpiredTodos_NoExpired_ShouldReturnEmptyArray() throws Exception {
            when(todoService.upgradeExpiredTodosPriority())
                .thenReturn(Collections.emptyList());

            mockMvc.perform(post("/api/todos/upgrade-expired")
                .header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
        }
    }
}
