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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 排期页要能在页面上直接排单：先从这里拿到还没排师傅的安装行，派工之后再从列表里消失。
 * 这个列表是 7 天宽表改成月历之后新加的接口，订单详情之外的第二条派工入口。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ScheduleAssignTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void pendingInstallDisappearsAfterAssign() throws Exception {
        String boss = login("boss", "boss123");
        long skuId = createSku(boss, "排期测试机", 3);
        String customer = loginCustomer("schedule-buyer-1");

        // 下单时选上门安装但不填日期，这一行才会进「待排」。
        JsonNode created = read(mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestId": "schedule-req-1",
                                  "contactName": "排期客户",
                                  "contactMobile": "13800003333",
                                  "addressText": "杭州市萧山区市心路 8 号",
                                  "items": [{"skuId": %d, "quantity": 1, "deliveryMethod": "ON_SITE_INSTALL"}]
                                }
                                """.formatted(skuId)))
                .andExpect(status().isOk())
                .andReturn());
        long orderId = created.path("data").path("orderId").asLong();

        JsonNode pending = pendingInstalls(boss);
        long orderItemId = 0;
        for (JsonNode row : pending) {
            if (row.path("orderId").asLong() == orderId) {
                orderItemId = row.path("orderItemId").asLong();
            }
        }
        assertThat(orderItemId).as("下单后应该出现在待排列表里").isNotZero();

        JsonNode technicians = read(mockMvc.perform(get("/api/admin/technicians")
                        .header("Authorization", "Bearer " + boss))
                .andExpect(status().isOk())
                .andReturn()).path("data");
        long technicianId = technicians.get(0).path("id").asLong();
        String date = "2026-12-05";

        mockMvc.perform(post("/api/admin/orders/" + orderId + "/assign")
                        .header("Authorization", "Bearer " + boss)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orderItemId":%d,"technicianId":%d,"installDate":"%s","installSlot":"上午"}
                                """.formatted(orderItemId, technicianId, date)))
                .andExpect(status().isOk());

        boolean stillPending = false;
        for (JsonNode row : pendingInstalls(boss)) {
            if (row.path("orderItemId").asLong() == orderItemId) {
                stillPending = true;
            }
        }
        assertThat(stillPending).as("派工之后不该再出现在待排列表里").isFalse();

        JsonNode schedules = read(mockMvc.perform(get("/api/admin/technicians/schedules")
                        .param("from", date)
                        .param("to", date)
                        .header("Authorization", "Bearer " + boss))
                .andExpect(status().isOk())
                .andReturn()).path("data");
        boolean seated = false;
        for (JsonNode row : schedules) {
            if (row.path("technicianId").asLong() == technicianId && row.path("timeSlot").asText().equals("上午")) {
                seated = row.path("used").asInt() >= 1;
            }
        }
        assertThat(seated).as("派工之后档期里应该有占用").isTrue();
    }

    private JsonNode pendingInstalls(String token) throws Exception {
        return read(mockMvc.perform(get("/api/admin/technicians/pending-installs")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()).path("data");
    }

    private long createSku(String token, String name, int stock) throws Exception {
        JsonNode created = read(mockMvc.perform(post("/api/admin/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","brand":"众邦","categoryId":2,"status":"ON",
                                 "skus":[{"title":"标准","price":268000,"stock":%d}]}
                                """.formatted(name, stock)))
                .andExpect(status().isOk())
                .andReturn());
        return created.path("data").path("skus").get(0).path("id").asLong();
    }

    private String login(String username, String password) throws Exception {
        JsonNode body = read(mockMvc.perform(post("/api/auth/staff/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn());
        return body.path("data").path("token").asText();
    }

    private String loginCustomer(String code) throws Exception {
        JsonNode body = read(mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andReturn());
        return body.path("data").path("token").asText();
    }

    private JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
