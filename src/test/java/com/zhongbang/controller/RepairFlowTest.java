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
 * 报修主路径：客户提交多行（含外购）、管理端报价、部分收款拆余款、派工、完工。
 * 另外确认未知路径是 404「接口不存在」，以及下单时价格变了会拒绝。
 */
@SpringBootTest
@AutoConfigureMockMvc
class RepairFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void missingApiIsNotFoundWithoutServerError() throws Exception {
        String staff = loginStaff();
        mockMvc.perform(get("/api/admin/does-not-exist").header("Authorization", "Bearer " + staff))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(4040))
                .andExpect(jsonPath("$.msg").value("接口不存在"));
    }

    @Test
    void submitQuotePayAndAssign() throws Exception {
        String staff = loginStaff();
        String customer = loginCustomer("repair-buyer-1");

        JsonNode asset = read(mockMvc.perform(post("/api/assets/bind")
                        .header("Authorization", "Bearer " + customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sn\":\"SN-REPAIR-1\"}"))
                .andExpect(status().isOk())
                .andReturn());
        long assetId = asset.path("data").path("id").asLong();

        String body = """
                {
                  "expectDate": "2026-12-02",
                  "expectSlot": "AM",
                  "lines": [
                    {
                      "assetId": %d,
                      "faultType": "THREAD_BREAK",
                      "faultDesc": "高速断线",
                      "images": ["https://img.example/a.jpg"]
                    },
                    {
                      "external": {
                        "brand": "某某牌",
                        "model": "家用 X9",
                        "sn": "X9-REPAIR-1",
                        "faultDesc": "跳针",
                        "images": ["https://img.example/b.jpg"],
                        "purchaseChannel": "电商"
                      },
                      "faultType": "THREAD_BREAK",
                      "faultDesc": "跳针",
                      "images": ["https://img.example/b.jpg"]
                    }
                  ]
                }
                """.formatted(assetId);
        JsonNode created = read(mockMvc.perform(post("/api/repairs")
                        .header("Authorization", "Bearer " + customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                .andReturn());
        long ticketId = created.path("data").path("ticketId").asLong();
        assertThat(created.path("data").path("id").asLong()).isEqualTo(ticketId);
        assertThat(created.path("data").path("lineCount").asInt()).isEqualTo(2);

        mockMvc.perform(get("/api/repairs/" + ticketId).header("Authorization", "Bearer " + customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lines.length()").value(2))
                .andExpect(jsonPath("$.data.lines[1].source").value("EXTERNAL"))
                .andExpect(jsonPath("$.data.lines[1].brand").value("某某牌"));

        mockMvc.perform(get("/api/admin/repairs").param("status", "SUBMITTED").header("Authorization", "Bearer " + staff))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").isNumber());

        mockMvc.perform(get("/api/admin/notices").param("unread", "1").header("Authorization", "Bearer " + staff))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].eventType").value("REPAIR_CREATED"))
                .andExpect(jsonPath("$.data.list[0].bizType").value("REPAIR"))
                .andExpect(jsonPath("$.data.list[0].bizId").value(ticketId));

        mockMvc.perform(post("/api/admin/repairs/" + ticketId + "/assign")
                        .header("Authorization", "Bearer " + staff)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lineId\":1,\"technicianId\":90,\"appointAt\":\"2026-12-02 09:30:00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(3003));

        JsonNode detail = read(mockMvc.perform(get("/api/admin/repairs/" + ticketId)
                        .header("Authorization", "Bearer " + staff))
                .andExpect(status().isOk())
                .andReturn());
        long line1 = detail.path("data").path("lines").get(0).path("id").asLong();
        long line2 = detail.path("data").path("lines").get(1).path("id").asLong();

        JsonNode quoted = read(mockMvc.perform(post("/api/admin/repairs/" + ticketId + "/quote")
                        .header("Authorization", "Bearer " + staff)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"inWarranty":false,"lines":[
                                  {"lineId":%d,"lineQuoteAmount":8000},
                                  {"lineId":%d,"lineQuoteAmount":12000}
                                ]}
                                """.formatted(line1, line2)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(quoted.path("data").path("status").asText()).isEqualTo("PENDING_PAYMENT");
        assertThat(quoted.path("data").path("payableAmount").asLong()).isEqualTo(20000L);
        JsonNode payment = quoted.path("data").path("payments").get(0);
        assertThat(payment.path("type").asText()).isEqualTo("REPAIR");
        assertThat(payment.path("amount").asLong()).isEqualTo(20000L);
        long paymentId = payment.path("id").asLong();

        mockMvc.perform(get("/api/admin/receivables").header("Authorization", "Bearer " + staff))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[?(@.bizType=='REPAIR' && @.bizId==%d)]".formatted(ticketId)).exists());

        mockMvc.perform(post("/api/admin/payments/" + paymentId + "/offline-confirm")
                        .header("Authorization", "Bearer " + staff)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":10000,\"method\":\"CASH\",\"paidAt\":\"2026-11-24 10:00:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.amount").value(10000))
                .andExpect(jsonPath("$.data.offlineMethod").value("CASH"));

        JsonNode partial = read(mockMvc.perform(get("/api/admin/repairs/" + ticketId)
                        .header("Authorization", "Bearer " + staff))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(partial.path("data").path("status").asText()).isEqualTo("PENDING_PAYMENT");
        long restId = 0;
        for (JsonNode row : partial.path("data").path("payments")) {
            if ("WAIT".equals(row.path("status").asText())) {
                assertThat(row.path("title").asText()).contains("（余款）");
                assertThat(row.path("splitFromId").asLong()).isEqualTo(paymentId);
                assertThat(row.path("amount").asLong()).isEqualTo(10000L);
                restId = row.path("id").asLong();
            }
        }
        assertThat(restId).isPositive();

        mockMvc.perform(post("/api/admin/payments/" + restId + "/offline-confirm")
                        .header("Authorization", "Bearer " + staff)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"BANK_TRANSFER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));

        JsonNode paid = read(mockMvc.perform(get("/api/admin/repairs/" + ticketId)
                        .header("Authorization", "Bearer " + staff))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(paid.path("data").path("status").asText()).isEqualTo("PAID");

        JsonNode assigned = read(mockMvc.perform(post("/api/admin/repairs/" + ticketId + "/assign")
                        .header("Authorization", "Bearer " + staff)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lineId\":" + line1 + ",\"technicianId\":90,\"appointAt\":\"2026-12-02 09:30:00\"}"))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(assigned.path("data").path("status").asText()).isEqualTo("IN_SERVICE");
        assertThat(assigned.path("data").path("lines").get(0).path("technicianName").asText()).isEqualTo("陈静");
        assertThat(assigned.path("data").path("lines").get(0).path("workOrderId").asLong()).isPositive();

        mockMvc.perform(post("/api/admin/repairs/" + ticketId + "/assign")
                        .header("Authorization", "Bearer " + staff)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lineId\":" + line2 + ",\"technicianId\":90,\"appointAt\":\"2026-12-02 15:00:00\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/repairs/" + ticketId + "/complete")
                        .header("Authorization", "Bearer " + staff))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DONE"));

        mockMvc.perform(get("/api/me/changes").param("since", "1").header("Authorization", "Bearer " + customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.changed").value(true))
                .andExpect(jsonPath("$.data.repairs").value(true));
        mockMvc.perform(get("/api/me/changes").param("since", "9999999999999").header("Authorization", "Bearer " + customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.changed").value(false));
    }

    @Test
    void warrantyQuoteCanAssignWithoutPayment() throws Exception {
        String staff = loginStaff();
        String customer = loginCustomer("repair-buyer-2");
        JsonNode created = read(mockMvc.perform(post("/api/repairs")
                        .header("Authorization", "Bearer " + customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expectDate": "2026-12-03",
                                  "lines": [{
                                    "external": {"brand":"本店外","model":"M1","faultDesc":"异响","images":["c.jpg"]},
                                    "faultType": "NOISE",
                                    "faultDesc": "异响",
                                    "images": ["c.jpg"]
                                  }]
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn());
        long ticketId = created.path("data").path("id").asLong();
        JsonNode detail = read(mockMvc.perform(get("/api/admin/repairs/" + ticketId)
                        .header("Authorization", "Bearer " + staff))
                .andReturn());
        long lineId = detail.path("data").path("lines").get(0).path("id").asLong();
        JsonNode quoted = read(mockMvc.perform(post("/api/admin/repairs/" + ticketId + "/quote")
                        .header("Authorization", "Bearer " + staff)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inWarranty\":true,\"lines\":[{\"lineId\":" + lineId + ",\"lineQuoteAmount\":0}]}"))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(quoted.path("data").path("status").asText()).isEqualTo("CONFIRMED");
        assertThat(quoted.path("data").path("payments")).isEmpty();

        mockMvc.perform(get("/api/admin/repairs").param("status", "CONFIRMED").header("Authorization", "Bearer " + staff))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].lines").isArray());

        mockMvc.perform(post("/api/admin/repairs/" + ticketId + "/assign")
                        .header("Authorization", "Bearer " + staff)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lineId\":" + lineId + ",\"technicianId\":88,\"appointAt\":\"2026-12-03 10:00:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_SERVICE"));
    }

    @Test
    void orderRejectsStalePrice() throws Exception {
        String staff = loginStaff();
        String customer = loginCustomer("repair-buyer-price");
        JsonNode product = read(mockMvc.perform(post("/api/admin/products")
                        .header("Authorization", "Bearer " + staff)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"改价校验机","brand":"众邦","skus":[{"title":"标准","price":25000,"stock":5}]}
                                """))
                .andExpect(status().isOk())
                .andReturn());
        long skuId = product.path("data").path("skus").get(0).path("id").asLong();
        String order = """
                {"contactName":"赵六","contactMobile":"13900002222","addressText":"杭州市西湖区","items":[
                  {"skuId":%d,"quantity":1,"deliveryMethod":"EXPRESS","expectedPrice":%d}
                ]}
                """;
        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(order.formatted(skuId, 100)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(3004))
                .andExpect(jsonPath("$.msg").value("商品价格已变化，请确认后再提交"));

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(order.formatted(skuId, 25000)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_PAYMENT"));
    }

    private String loginStaff() throws Exception {
        JsonNode body = read(mockMvc.perform(post("/api/auth/staff/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
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
