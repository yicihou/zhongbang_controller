package com.zhongbang.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 进价、采购、毛利和库存预警。非老板访问财务接口要 403。
 * 未知成本不能当成 0。入库用移动加权平均，作废要把库存减回去。
 */
@SpringBootTest
@AutoConfigureMockMvc
class FinanceTradeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void staffCannotReadCostMarginOrPayables() throws Exception {
        mockMvc.perform(get("/api/admin/reports/margin")).andExpect(status().isUnauthorized());

        String admin = login("admin", "admin123");
        String sales = createSales(admin);
        for (String token : new String[]{admin, sales}) {
            mockMvc.perform(get("/api/admin/costs/settings").header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(2003));
            mockMvc.perform(get("/api/admin/reports/margin").header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/admin/reports/margin/insights").header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/admin/suppliers/1/ledger").header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/admin/imports/template").header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        }
        String boss = login("boss", "boss123");
        mockMvc.perform(get("/api/admin/costs/settings").header("Authorization", "Bearer " + boss))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.costMethod").value("MOVING_AVERAGE"));
    }

    @Test
    void costHistoryPurchaseAverageAndVoid() throws Exception {
        String boss = login("boss", "boss123");
        String admin = login("admin", "admin123");
        long skuId = createSku(admin, "进价测试机", "标准", 8);

        String hidden = mockMvc.perform(get("/api/admin/products").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(hidden).doesNotContain("costPrice");

        mockMvc.perform(put("/api/admin/costs/skus/" + skuId).header("Authorization", "Bearer " + boss)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"costPrice\":100000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.costPrice").value(100000))
                .andExpect(jsonPath("$.data.logs[0].beforePrice").doesNotExist())
                .andExpect(jsonPath("$.data.logs[0].afterPrice").value(100000));

        String withCost = mockMvc.perform(get("/api/admin/products").header("Authorization", "Bearer " + boss))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(withCost).contains("\"costPrice\":100000");

        long supplierId = supplier(boss, "进价供应商");
        String adminBody = mockMvc.perform(post("/api/admin/purchases").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplierId\":" + supplierId + ",\"lines\":[{\"skuId\":" + skuId + ",\"quantity\":1,\"unitCost\":999999}]}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(adminBody).doesNotContain("999999");
        assertThat(adminBody).doesNotContain("unitCost");
        long ignoredId = objectMapper.readTree(adminBody).path("data").path("id").asLong();
        mockMvc.perform(post("/api/admin/purchases/" + ignoredId + "/void").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());

        JsonNode draft = read(mockMvc.perform(post("/api/admin/purchases").header("Authorization", "Bearer " + boss)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"supplierId":%d,"invoiced":true,"invoiceType":"增值税专用发票","taxRate":1300,"taxIncluded":true,
                                 "freightAmount":5000,"attachmentUrls":["/uploads/demo.jpg"],
                                 "lines":[{"skuId":%d,"quantity":2,"unitCost":160000}]}
                                """.formatted(supplierId, skuId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.invoiced").value(true))
                .andExpect(jsonPath("$.data.lines[0].unitCost").value(160000))
                .andReturn());
        long purchaseId = draft.path("data").path("id").asLong();

        mockMvc.perform(post("/api/admin/purchases/" + purchaseId + "/receive").header("Authorization", "Bearer " + boss))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RECEIVED"))
                .andExpect(jsonPath("$.data.totalAmount").value(325000));

        mockMvc.perform(get("/api/admin/costs/skus/" + skuId + "/logs").header("Authorization", "Bearer " + boss))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.costPrice").value(112000));

        mockMvc.perform(get("/api/admin/suppliers/" + supplierId + "/ledger").header("Authorization", "Bearer " + admin))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/suppliers/" + supplierId + "/ledger").header("Authorization", "Bearer " + boss))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.payable").value(325000))
                .andExpect(jsonPath("$.data.balance").value(325000));
        mockMvc.perform(post("/api/admin/suppliers/" + supplierId + "/payments").header("Authorization", "Bearer " + boss)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100000,\"method\":\"BANK_TRANSFER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paid").value(100000))
                .andExpect(jsonPath("$.data.balance").value(225000));

        mockMvc.perform(put("/api/admin/costs/settings").header("Authorization", "Bearer " + boss)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"costMethod\":\"LATEST\"}"))
                .andExpect(status().isOk());
        JsonNode latest = read(mockMvc.perform(post("/api/admin/purchases").header("Authorization", "Bearer " + boss)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplierId\":" + supplierId + ",\"invoiced\":false,\"lines\":[{\"skuId\":" + skuId + ",\"quantity\":1,\"unitCost\":200000}]}"))
                .andExpect(status().isOk()).andReturn());
        long latestId = latest.path("data").path("id").asLong();
        mockMvc.perform(post("/api/admin/purchases/" + latestId + "/receive").header("Authorization", "Bearer " + boss))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/costs/skus/" + skuId + "/logs").header("Authorization", "Bearer " + boss))
                .andExpect(jsonPath("$.data.costPrice").value(200000));
        mockMvc.perform(post("/api/admin/purchases/" + latestId + "/void").header("Authorization", "Bearer " + boss))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/costs/skus/" + skuId + "/logs").header("Authorization", "Bearer " + boss))
                .andExpect(jsonPath("$.data.costPrice").value(112000));

        mockMvc.perform(put("/api/admin/costs/settings").header("Authorization", "Bearer " + boss)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"costMethod\":\"MOVING_AVERAGE\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void orderSnapshotsCostAndUnknownIsNotZero() throws Exception {
        String boss = login("boss", "boss123");
        String admin = login("admin", "admin123");
        long knownSku = createSku(admin, "已知成本机", "A", 5);
        long unknownSku = createSku(admin, "未知成本机", "B", 5);
        mockMvc.perform(put("/api/admin/costs/skus/" + knownSku).header("Authorization", "Bearer " + boss)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"costPrice\":50000}"))
                .andExpect(status().isOk());

        String customer = loginCustomer("finance-buyer");
        long knownOrder = place(customer, knownSku, "finance-known");
        long unknownOrder = place(customer, unknownSku, "finance-unknown");

        String adminView = mockMvc.perform(get("/api/admin/orders/" + knownOrder).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(adminView).doesNotContain("costPrice");
        assertThat(adminView).doesNotContain("grossProfit");

        mockMvc.perform(get("/api/admin/orders/" + knownOrder).header("Authorization", "Bearer " + boss))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].costKnown").value(true))
                .andExpect(jsonPath("$.data.items[0].costPrice").value(50000))
                .andExpect(jsonPath("$.data.goodsCost").value(50000));

        mockMvc.perform(put("/api/admin/costs/skus/" + knownSku).header("Authorization", "Bearer " + boss)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"costPrice\":80000}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/orders/" + knownOrder).header("Authorization", "Bearer " + boss))
                .andExpect(jsonPath("$.data.items[0].costPrice").value(50000));

        mockMvc.perform(get("/api/admin/orders/" + unknownOrder).header("Authorization", "Bearer " + boss))
                .andExpect(jsonPath("$.data.items[0].costKnown").value(false))
                .andExpect(jsonPath("$.data.items[0].costPrice").doesNotExist())
                .andExpect(jsonPath("$.data.goodsCost").doesNotExist());

        mockMvc.perform(put("/api/admin/orders/" + knownOrder + "/invoice").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"invoiced\":true,\"invoiceType\":\"增值税普通发票\",\"taxRate\":1300,\"taxIncluded\":true}"))
                .andExpect(status().isOk());

        JsonNode month = read(mockMvc.perform(get("/api/admin/reports/margin").header("Authorization", "Bearer " + boss)
                        .param("period", "MONTH").param("scope", "ALL"))
                .andExpect(status().isOk()).andReturn());
        long unknownSales = month.path("data").path("current").path("unknownSalesAmount").asLong();
        assertThat(unknownSales).isGreaterThanOrEqualTo(10000L);
        assertThat(month.path("data").path("current").path("goodsCost").asLong()).isPositive();
        assertThat(month.path("data").path("previous").path("periodKey").asText()).isNotBlank();
        assertThat(month.path("data").path("yearAgo").path("periodKey").asText()).isNotBlank();

        JsonNode invoicedOnly = read(mockMvc.perform(get("/api/admin/reports/margin").header("Authorization", "Bearer " + boss)
                        .param("scope", "INVOICED")).andExpect(status().isOk()).andReturn());
        assertThat(invoicedOnly.path("data").path("current").path("unknownSalesAmount").asLong()).isLessThan(unknownSales);
        assertThat(invoicedOnly.path("data").path("current").path("salesTaxIncluded").asLong())
                .isGreaterThan(invoicedOnly.path("data").path("current").path("salesTaxExcluded").asLong());

        mockMvc.perform(get("/api/admin/reports/margin/products").header("Authorization", "Bearer " + boss))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list").isArray());
        mockMvc.perform(get("/api/admin/reports/margin/export").header("Authorization", "Bearer " + boss))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/reports/margin/insights").header("Authorization", "Bearer " + boss)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"period\":\"MONTH\",\"scope\":\"ALL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configured").value(false))
                .andExpect(jsonPath("$.data.message").value(org.hamcrest.Matchers.containsString("DEEPSEEK_API_KEY")));

        JsonNode shipped = read(mockMvc.perform(get("/api/admin/orders/" + unknownOrder).header("Authorization", "Bearer " + boss))
                .andReturn());
        long itemId = shipped.path("data").path("items").get(0).path("id").asLong();
        mockMvc.perform(put("/api/admin/costs/skus/" + unknownSku).header("Authorization", "Bearer " + boss)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"costPrice\":3000}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/orders/" + unknownOrder + "/shipments").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderItemId\":" + itemId + ",\"carrier\":\"顺丰\",\"trackingNo\":\"SF-COST\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/orders/" + unknownOrder).header("Authorization", "Bearer " + boss))
                .andExpect(jsonPath("$.data.items[0].costKnown").value(true))
                .andExpect(jsonPath("$.data.items[0].costPrice").value(3000));

        // 这两张测试订单的待付款会留在「待收款」里。收掉或取消，避免影响别的用例对收款笔数的判断。
        mockMvc.perform(post("/api/admin/orders/" + knownOrder + "/cancel").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"测试结束\"}"))
                .andExpect(status().isOk());
        JsonNode open = read(mockMvc.perform(get("/api/admin/orders/" + unknownOrder).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andReturn());
        long paymentId = open.path("data").path("payments").get(0).path("id").asLong();
        long payable = open.path("data").path("payableAmount").asLong();
        mockMvc.perform(post("/api/admin/payments/" + paymentId + "/offline-confirm").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":" + payable + ",\"method\":\"CASH\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void stockAlertDoesNotRepeatUntilRecovered() throws Exception {
        String boss = login("boss", "boss123");
        String admin = login("admin", "admin123");
        long skuId = createSku(admin, "预警机", "低库存", 5);
        mockMvc.perform(put("/api/admin/stock-alerts/skus/" + skuId).header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"minStock\":6}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[?(@.skuId==" + skuId + ")].suggestQty").value(org.hamcrest.Matchers.hasItem(7)));
        assertThat(countAlerts(boss, skuId)).isEqualTo(1);
        mockMvc.perform(put("/api/admin/stock-alerts/skus/" + skuId).header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"minStock\":6}"))
                .andExpect(status().isOk());
        assertThat(countAlerts(boss, skuId)).isEqualTo(1);

        long supplierId = supplier(boss, "预警供应商");
        JsonNode draft = read(mockMvc.perform(post("/api/admin/purchases").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplierId\":" + supplierId + ",\"lines\":[{\"skuId\":" + skuId + ",\"quantity\":3}]}"))
                .andExpect(status().isOk()).andReturn());
        mockMvc.perform(get("/api/admin/stock-alerts").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.data.list[?(@.skuId==" + skuId + ")].inTransit").value(org.hamcrest.Matchers.hasItem(3)))
                .andExpect(jsonPath("$.data.list[?(@.skuId==" + skuId + ")].suggestQty").value(org.hamcrest.Matchers.hasItem(4)));

        mockMvc.perform(post("/api/admin/purchases/" + draft.path("data").path("id").asLong() + "/receive")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
        assertThat(countAlerts(boss, skuId)).isEqualTo(1);
        mockMvc.perform(post("/api/admin/purchases/" + draft.path("data").path("id").asLong() + "/void")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
        assertThat(countAlerts(boss, skuId)).isEqualTo(2);
        String adminNotices = mockMvc.perform(get("/api/admin/notices").header("Authorization", "Bearer " + admin).param("size", "100"))
                .andReturn().getResponse().getContentAsString();
        assertThat(adminNotices).doesNotContain("STOCK_LOW");
    }

    @Test
    void importSkipsBadRowsAndDemoDataIsIdempotent() throws Exception {
        String boss = login("boss", "boss123");
        String csv = "商品名称,规格,售价,进价,库存\n导入平缝机,标准,1000,600,4\n只有名字,,,,\n";
        MockMultipartFile file = new MockMultipartFile("file", "goods.csv", "text/csv", csv.getBytes());
        JsonNode preview = read(mockMvc.perform(multipart("/api/admin/imports/preview").file(file).param("kind", "PRODUCT")
                        .header("Authorization", "Bearer " + boss))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fields[0].label").value("商品名称"))
                .andReturn());
        String token = preview.path("data").path("token").asText();
        mockMvc.perform(post("/api/admin/imports/commit").header("Authorization", "Bearer " + boss)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"mapping\":{\"productName\":\"商品名称\",\"skuName\":\"规格\",\"salePrice\":\"售价\",\"costPrice\":\"进价\",\"stock\":\"库存\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.created").value(1))
                .andExpect(jsonPath("$.data.skipped").value(1))
                .andExpect(jsonPath("$.data.errors[0]").value(org.hamcrest.Matchers.containsString("规格")));

        mockMvc.perform(get("/api/admin/imports/template").header("Authorization", "Bearer " + boss).param("kind", "SUPPLIER"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("名称")));

        try {
            mockMvc.perform(post("/api/admin/trade/demo").header("Authorization", "Bearer " + login("admin", "admin123")))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/admin/trade/demo").header("Authorization", "Bearer " + boss))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.created").value(true));
            mockMvc.perform(post("/api/admin/trade/demo").header("Authorization", "Bearer " + boss))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.created").value(false));
            mockMvc.perform(get("/api/admin/suppliers").header("Authorization", "Bearer " + boss).param("keyword", "演示-华东缝配"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.list[0].name").value("演示-华东缝配"));
        } finally {
            mockMvc.perform(delete("/api/admin/trade/demo").header("Authorization", "Bearer " + boss))
                    .andExpect(status().isOk());
        }
    }

    private int countAlerts(String boss, long skuId) throws Exception {
        JsonNode page = read(mockMvc.perform(get("/api/admin/notices").header("Authorization", "Bearer " + boss).param("size", "100"))
                .andExpect(status().isOk()).andReturn());
        int count = 0;
        for (JsonNode row : page.path("data").path("list")) {
            if ("STOCK_LOW".equals(row.path("eventType").asText()) && row.path("bizId").asLong() == skuId) {
                count++;
            }
        }
        return count;
    }

    private long place(String customer, long skuId, String requestId) throws Exception {
        JsonNode created = read(mockMvc.perform(post("/api/orders").header("Authorization", "Bearer " + customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requestId":"%s","contactName":"财务客户","contactMobile":"13900004400",
                                 "addressText":"杭州市西湖区","items":[{"skuId":%d,"quantity":1,"deliveryMethod":"EXPRESS"}]}
                                """.formatted(requestId, skuId)))
                .andExpect(status().isOk()).andReturn());
        assertThat(created.path("data").toString()).doesNotContain("costPrice");
        return created.path("data").path("orderId").asLong();
    }

    private long supplier(String boss, String name) throws Exception {
        JsonNode created = read(mockMvc.perform(post("/api/admin/suppliers").header("Authorization", "Bearer " + boss)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"contact\":\"王\",\"mobile\":\"13800003301\",\"brands\":\"标准牌\",\"accountDays\":30}"))
                .andExpect(status().isOk()).andReturn());
        return created.path("data").path("id").asLong();
    }

    private long createSku(String admin, String name, String title, int stock) throws Exception {
        JsonNode created = read(mockMvc.perform(post("/api/admin/products").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"brand\":\"众邦\",\"status\":\"ON\",\"skus\":[{\"title\":\"" + title + "\",\"price\":10000,\"stock\":" + stock + "}]}"))
                .andExpect(status().isOk()).andReturn());
        return created.path("data").path("skus").get(0).path("id").asLong();
    }

    private String createSales(String admin) throws Exception {
        mockMvc.perform(post("/api/admin/staff").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"财务业务员\",\"mobile\":\"13900007721\",\"role\":\"SALES\",\"username\":\"finsales\",\"password\":\"sales123\"}"))
                .andExpect(status().isOk());
        return login("finsales", "sales123");
    }

    private String login(String username, String password) throws Exception {
        JsonNode body = read(mockMvc.perform(post("/api/auth/staff/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn());
        return body.path("data").path("token").asText();
    }

    private String loginCustomer(String code) throws Exception {
        JsonNode body = read(mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk()).andReturn());
        return body.path("data").path("token").asText();
    }

    private JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
