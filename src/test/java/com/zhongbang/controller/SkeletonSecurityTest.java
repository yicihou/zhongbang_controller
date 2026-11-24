package com.zhongbang.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用 MockMvc 起完整 Spring 上下文，不真正监听 8080，检查骨架的公开接口、登录和鉴权。
 *
 * <p>覆盖：探活的统一响应、健康检查 200、商品空分页、未登录访问设备得到 401 且业务码 2001、
 * 用 code 登录后能绑定一台设备并在列表里看到它、未登录访问 H2 控制台和 {@code /actuator/info} 得到 401。
 * 没有覆盖「已登录但角色不对」的 403，也没有覆盖手机号接口。
 *
 * <p>{@code @SpringBootTest} 启动应用；{@code @AutoConfigureMockMvc} 提供一个可发假请求的客户端。
 */
@SpringBootTest
@AutoConfigureMockMvc
class SkeletonSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void publicEndpointsAndAssetFlow() throws Exception {
        mockMvc.perform(get("/api/system/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("ok"))
                .andExpect(jsonPath("$.traceId").isString())
                .andExpect(jsonPath("$.data.status").value("UP"));

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.list").isArray());

        mockMvc.perform(get("/api/assets"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(2001));

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"test-user-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").isString())
                .andReturn();

        JsonNode body = objectMapper.readTree(login.getResponse().getContentAsString());
        String token = body.path("data").path("token").asText();
        assertThat(token).isNotBlank();

        mockMvc.perform(get("/api/assets").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        mockMvc.perform(post("/api/assets/bind")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sn\":\"A5-2024-TEST001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sn").value("A5-2024-TEST001"))
                .andExpect(jsonPath("$.data.warrantyStatus").isString());

        mockMvc.perform(get("/api/assets").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

        // Unauthenticated callers get 401; denyAll still blocks the console for any principal.
        mockMvc.perform(get("/h2-console"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isUnauthorized());
    }
}
