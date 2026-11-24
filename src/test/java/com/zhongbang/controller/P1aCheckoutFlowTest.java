package com.zhongbang.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhongbang.controller.module.notify.AdminSseHub;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class P1aCheckoutFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AdminSseHub adminSseHub;

    @Test
    void cartCheckoutSseAndAdminOrderList() throws Exception {
        SseEmitter emitter = adminSseHub.subscribe();
        assertThat(adminSseHub.connectionCount()).isGreaterThanOrEqualTo(1);

        MvcResult customerLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"p1a-customer-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        String customerToken = objectMapper.readTree(customerLogin.getResponse().getContentAsString())
                .path("data").path("token").asText();

        MvcResult products = mockMvc.perform(get("/api/products").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(objectMapper.readTree(products.getResponse().getContentAsString())
                .path("data").path("list").size()).isGreaterThanOrEqualTo(2);

        mockMvc.perform(post("/api/cart")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skuId\":1011,\"quantity\":1,\"deliveryMethod\":\"EXPRESS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skuId").value(1011));

        mockMvc.perform(post("/api/cart")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skuId\":2011,\"quantity\":2,\"deliveryMethod\":\"EXPRESS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skuId").value(2011));

        mockMvc.perform(get("/api/cart").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        String requestId = UUID.randomUUID().toString();
        String createBody = """
                {
                  "requestId": "%s",
                  "items": [
                    {"skuId": 1011, "quantity": 1, "deliveryMethod": "EXPRESS"},
                    {"skuId": 2011, "quantity": 2, "deliveryMethod": "EXPRESS"}
                  ],
                  "remark": "P1a mixed cart"
                }
                """.formatted(requestId);

        MvcResult create = mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.data.goodsAmount").value(328000 + 8800 * 2))
                .andExpect(jsonPath("$.data.freightAmount").value(0))
                .andExpect(jsonPath("$.data.payableAmount").value(328000 + 8800 * 2))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.payments.length()").value(1))
                .andExpect(jsonPath("$.data.payments[0].type").value("FULL"))
                .andExpect(jsonPath("$.data.payments[0].status").value("WAIT"))
                .andReturn();

        JsonNode order = objectMapper.readTree(create.getResponse().getContentAsString()).path("data");
        long orderId = order.path("id").asLong();
        assertThat(orderId).isPositive();

        // Idempotent replay
        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(orderId));

        mockMvc.perform(get("/api/cart").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        MvcResult staffLogin = mockMvc.perform(post("/api/auth/staff-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("STAFF"))
                .andReturn();
        String staffToken = objectMapper.readTree(staffLogin.getResponse().getContentAsString())
                .path("data").path("token").asText();

        mockMvc.perform(get("/api/admin/orders").header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].id").value(orderId))
                .andExpect(jsonPath("$.data.list[0].status").value("PENDING_PAYMENT"));

        mockMvc.perform(get("/api/admin/notices").header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].bizType").value("ORDER"))
                .andExpect(jsonPath("$.data[0].bizId").value(orderId));

        emitter.complete();
    }
}
