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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 库存流水必须覆盖每一次余额变动，否则「期初 + 进货 − 销售 ± 盘点 = 结存」对不上。
 * 这里走一遍：期初 → 下单出库 → 取消回滚 → 改商品库存（应当无效）→ 盘点调整。
 */
@SpringBootTest
@AutoConfigureMockMvc
class StockLedgerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void everyStockChangeLeavesAPairedLedgerEntry() throws Exception {
        String boss = login("boss", "boss123");

        // 1. 新建带期初 5 台，流水应该有一条 INIT，结存 5。
        long skuId = createSku(boss, "流水测试机", "标准", 5);
        JsonNode logs = logs(boss, skuId);
        assertThat(logs.size()).isEqualTo(1);
        assertThat(logs.get(0).path("bizType").asText()).isEqualTo("INIT");
        assertThat(logs.get(0).path("quantity").asInt()).isEqualTo(5);
        assertThat(logs.get(0).path("stockAfter").asInt()).isEqualTo(5);

        // 2. 客户下单买 2 台，应该记一条 SALE_OUT -2，结存 3。
        String customer = loginCustomer("ledger-buyer-1");
        JsonNode created = read(mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestId": "ledger-req-1",
                                  "contactName": "流水客户",
                                  "contactMobile": "13800002222",
                                  "addressText": "杭州市余杭区文一西路 1 号",
                                  "items": [{"skuId": %d, "quantity": 2, "deliveryMethod": "EXPRESS"}]
                                }
                                """.formatted(skuId)))
                .andExpect(status().isOk())
                .andReturn());
        long orderId = created.path("data").path("orderId").asLong();
        logs = logs(boss, skuId);
        assertThat(logs.get(0).path("bizType").asText()).isEqualTo("SALE_OUT");
        assertThat(logs.get(0).path("quantity").asInt()).isEqualTo(-2);
        assertThat(logs.get(0).path("stockAfter").asInt()).isEqualTo(3);

        // 3. 取消订单，回滚一条 SALE_CANCEL +2，结存回到 5。
        mockMvc.perform(post("/api/admin/orders/" + orderId + "/cancel")
                        .header("Authorization", "Bearer " + boss)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"测试取消\"}"))
                .andExpect(status().isOk());
        logs = logs(boss, skuId);
        assertThat(logs.get(0).path("bizType").asText()).isEqualTo("SALE_CANCEL");
        assertThat(logs.get(0).path("quantity").asInt()).isEqualTo(2);
        assertThat(logs.get(0).path("stockAfter").asInt()).isEqualTo(5);

        // 4. 编辑商品时把库存写成 99：库存不跟着变，流水也不增加。库存只能由单据驱动。
        long productId = productIdOfSku(boss, skuId);
        mockMvc.perform(put("/api/admin/products/" + productId)
                        .header("Authorization", "Bearer " + boss)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "流水测试机",
                                  "brand": "众邦",
                                  "status": "ON",
                                  "skus": [{"id": %d, "title": "标准", "price": 10000, "stock": 99}]
                                }
                                """.formatted(skuId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skus[0].stock").value(5));
        assertThat(logs(boss, skuId).size()).isEqualTo(3);

        // 5. 盘点改成实际 4 台：记一条 ADJUST -1，结存 4。
        mockMvc.perform(post("/api/admin/stock-adjust")
                        .header("Authorization", "Bearer " + boss)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skuId\":" + skuId + ",\"actualStock\":4,\"reason\":\"盘亏一台\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.delta").value(-1));
        logs = logs(boss, skuId);
        assertThat(logs.get(0).path("bizType").asText()).isEqualTo("ADJUST");
        assertThat(logs.get(0).path("quantity").asInt()).isEqualTo(-1);
        assertThat(logs.get(0).path("stockAfter").asInt()).isEqualTo(4);

        // 6. 盘点只有老板能做，普通管理员 403。
        String admin = login("admin", "admin123");
        mockMvc.perform(post("/api/admin/stock-adjust")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skuId\":" + skuId + ",\"actualStock\":1}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(2003));
    }

    /** 流水按时间倒序，第一条就是刚刚那笔。 */
    private JsonNode logs(String token, long skuId) throws Exception {
        JsonNode page = read(mockMvc.perform(get("/api/admin/stock-logs")
                        .param("skuId", String.valueOf(skuId))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn());
        return page.path("data").path("list");
    }

    private long createSku(String boss, String name, String title, int stock) throws Exception {
        JsonNode created = read(mockMvc.perform(post("/api/admin/products")
                        .header("Authorization", "Bearer " + boss)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","brand":"众邦","categoryId":2,"status":"ON",
                                 "skus":[{"title":"%s","price":10000,"stock":%d}]}
                                """.formatted(name, title, stock)))
                .andExpect(status().isOk())
                .andReturn());
        return created.path("data").path("skus").get(0).path("id").asLong();
    }

    private long productIdOfSku(String token, long skuId) throws Exception {
        JsonNode page = read(mockMvc.perform(get("/api/admin/products")
                        .param("keyword", "流水测试机")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn());
        for (JsonNode product : page.path("data").path("list")) {
            for (JsonNode sku : product.path("skus")) {
                if (sku.path("id").asLong() == skuId) {
                    return product.path("id").asLong();
                }
            }
        }
        throw new AssertionError("商品列表里找不到这个规格");
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
