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
 * 打通后的主路径：员工登录、客户下多行订单、未付款先发货、发货后不能取消、部分收款拆出余款。
 * 用内存库，Flyway 会先放进四台样例机器和 admin 账号。
 */
@SpringBootTest
@AutoConfigureMockMvc
class OrderTradeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void placeOrderShipThenSplitRemainder() throws Exception {
        String staff = loginStaff();
        String customer = loginCustomer("buyer-trade-1");

        mockMvc.perform(get("/api/admin/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(2001));

        mockMvc.perform(get("/api/admin/orders").header("Authorization", "Bearer " + customer))
                .andExpect(status().isForbidden());

        String body = """
                {
                  "requestId": "trade-req-1",
                  "contactName": "张三",
                  "contactMobile": "13800001111",
                  "addressText": "杭州市西湖区文三路 1 号",
                  "remark": "请周末联系",
                  "items": [
                    {"skuId": 1011, "quantity": 1, "deliveryMethod": "ON_SITE_INSTALL", "installDate": "2026-12-01", "installSlot": "AM"},
                    {"skuId": 1021, "quantity": 2, "deliveryMethod": "EXPRESS"}
                  ]
                }
                """;
        JsonNode created = read(mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn());
        long orderId = created.path("data").path("orderId").asLong();
        assertThat(created.path("data").path("totalAmount").asLong()).isEqualTo(328000L + 158000L * 2);
        assertThat(created.path("data").path("status").asText()).isEqualTo("PENDING_PAYMENT");

        JsonNode again = read(mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(again.path("data").path("orderId").asLong()).isEqualTo(orderId);

        mockMvc.perform(get("/api/orders").header("Authorization", "Bearer " + customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].orderId").doesNotExist())
                .andExpect(jsonPath("$.data.list[0].id").value(orderId))
                .andExpect(jsonPath("$.data.list[0].statusText").value("待付款"));

        JsonNode detail = read(mockMvc.perform(get("/api/admin/orders/" + orderId)
                        .header("Authorization", "Bearer " + staff))
                .andExpect(status().isOk())
                .andReturn());
        JsonNode items = detail.path("data").path("items");
        assertThat(items.size()).isEqualTo(2);
        long expressItemId = 0;
        boolean installFeeMissing = false;
        for (JsonNode item : items) {
            if ("EXPRESS".equals(item.path("deliveryMethod").asText())) {
                expressItemId = item.path("id").asLong();
                assertThat(item.path("quantity").asInt()).isEqualTo(2);
            }
            if ("ON_SITE_INSTALL".equals(item.path("deliveryMethod").asText())) {
                installFeeMissing = !item.has("installFee") || item.get("installFee").isNull();
                assertThat(item.path("installSlot").asText()).isEqualTo("上午");
            }
        }
        assertThat(expressItemId).isPositive();
        assertThat(installFeeMissing).isTrue();
        long paymentId = detail.path("data").path("payments").get(0).path("id").asLong();
        long payable = detail.path("data").path("payableAmount").asLong();

        JsonNode shipped = read(mockMvc.perform(post("/api/admin/orders/" + orderId + "/shipments")
                        .header("Authorization", "Bearer " + staff)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderItemId\":" + expressItemId + ",\"carrier\":\"顺丰\",\"trackingNo\":\"SF10001\"}"))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(shipped.path("data").path("status").asText()).isEqualTo("PENDING_PAYMENT");
        int shippedLines = 0;
        for (JsonNode item : shipped.path("data").path("items")) {
            if ("DISPATCHED".equals(item.path("shipment").path("status").asText())) {
                shippedLines += 1;
                assertThat(item.path("shipment").path("trackingNo").asText()).isEqualTo("SF10001");
            }
        }
        assertThat(shippedLines).isEqualTo(1);

        mockMvc.perform(post("/api/admin/orders/" + orderId + "/cancel")
                        .header("Authorization", "Bearer " + staff)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"不想要了\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(3003));

        mockMvc.perform(post("/api/admin/payments/" + paymentId + "/offline-confirm")
                        .header("Authorization", "Bearer " + staff)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":10000,\"method\":\"CASH\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.amount").value(10000))
                .andExpect(jsonPath("$.data.offlineMethod").value("CASH"));

        JsonNode afterPay = read(mockMvc.perform(get("/api/admin/orders/" + orderId)
                        .header("Authorization", "Bearer " + staff))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(afterPay.path("data").path("status").asText()).isEqualTo("PARTIALLY_PAID");
        assertThat(afterPay.path("data").path("paidAmount").asLong()).isEqualTo(10000L);
        boolean foundRemainder = false;
        for (JsonNode payment : afterPay.path("data").path("payments")) {
            if ("WAIT".equals(payment.path("status").asText())) {
                assertThat(payment.path("title").asText()).contains("（余款）");
                assertThat(payment.path("splitFromId").asLong()).isEqualTo(paymentId);
                assertThat(payment.path("amount").asLong()).isEqualTo(payable - 10000);
                foundRemainder = true;
            }
        }
        assertThat(foundRemainder).isTrue();

        mockMvc.perform(get("/api/admin/notices").param("unread", "1").header("Authorization", "Bearer " + staff))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].eventType").value("ORDER_CREATED"))
                .andExpect(jsonPath("$.data.list[0].bizId").value(orderId));

        mockMvc.perform(get("/api/admin/receivables").header("Authorization", "Bearer " + staff))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void staffCanManageProductShelfAndPrice() throws Exception {
        String staff = loginStaff();
        JsonNode created = read(mockMvc.perform(post("/api/admin/products")
                        .header("Authorization", "Bearer " + staff)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "测试平车",
                                  "brand": "众邦",
                                  "categoryId": 2,
                                  "model": "T-1",
                                  "coverImage": "https://img.example/t1.png",
                                  "skus": [{"title": "标准", "price": 19900, "stock": 3}]
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn());
        long id = created.path("data").path("id").asLong();
        long skuId = created.path("data").path("skus").get(0).path("id").asLong();
        assertThat(created.path("data").path("coverImage").asText()).isEqualTo("https://img.example/t1.png");

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.coverImage").value("https://img.example/t1.png"))
                .andExpect(jsonPath("$.data.skus[0].price").value(19900));

        mockMvc.perform(post("/api/admin/products/" + id + "/price")
                        .header("Authorization", "Bearer " + staff)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skuId\":" + skuId + ",\"price\":18800}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.price").value(18800));

        mockMvc.perform(post("/api/admin/products/" + id + "/off-shelf")
                        .header("Authorization", "Bearer " + staff))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OFF"));

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(4040));

        mockMvc.perform(post("/api/admin/products/" + id + "/on-shelf")
                        .header("Authorization", "Bearer " + staff))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ON"));
    }

    private String loginStaff() throws Exception {
        JsonNode body = read(mockMvc.perform(post("/api/auth/staff/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.staff.type").value("STAFF"))
                .andExpect(jsonPath("$.data.staff.role").value("ROLE_ADMIN"))
                .andReturn());
        String token = body.path("data").path("token").asText();
        assertThat(token.split("\\.")).hasSize(3);
        return token;
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
