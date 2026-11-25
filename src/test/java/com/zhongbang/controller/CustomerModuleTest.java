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
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 客户模块只有老板能用。管理员、业务员和客户令牌都要 403。
 */
@SpringBootTest
@AutoConfigureMockMvc
class CustomerModuleTest {

    private static final byte[] WAV = new byte[]{
            'R', 'I', 'F', 'F', 36, 0, 0, 0, 'W', 'A', 'V', 'E',
            'f', 'm', 't', ' ', 16, 0, 0, 0, 1, 0, 1, 0,
            0x44, (byte) 0xAC, 0, 0, (byte) 0x88, 0x58, 1, 0,
            2, 0, 16, 0,
            'd', 'a', 't', 'a', 0, 0, 0, 0
    };

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void onlyBossCanOpenCustomerApis() throws Exception {
        mockMvc.perform(get("/api/admin/customers"))
                .andExpect(status().isUnauthorized());

        String admin = login("admin", "admin123");
        mockMvc.perform(get("/api/admin/customers").header("Authorization", "Bearer " + admin))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(2003));

        String sales = createSales(admin);
        mockMvc.perform(get("/api/admin/customers").header("Authorization", "Bearer " + sales))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(2003));
        mockMvc.perform(get("/api/admin/orders").header("Authorization", "Bearer " + sales))
                .andExpect(status().isOk());

        String customer = loginCustomer("customer-module-buyer");
        mockMvc.perform(get("/api/admin/customers").header("Authorization", "Bearer " + customer))
                .andExpect(status().isForbidden());

        String boss = login("boss", "boss123");
        mockMvc.perform(get("/api/admin/customers").header("Authorization", "Bearer " + boss))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list").isArray());
        mockMvc.perform(get("/api/admin/staff").header("Authorization", "Bearer " + boss))
                .andExpect(status().isForbidden());
    }

    @Test
    void bossCanCreateSearchAndExportACustomer() throws Exception {
        String boss = login("boss", "boss123");
        JsonNode created = read(mockMvc.perform(post("/api/admin/customers")
                        .header("Authorization", "Bearer " + boss)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"测试服装厂","mobile":"13900008821","customerType":"服装厂","region":"杭州市西湖区",
                                 "shopName":"测试厂","source":"电话下单","tags":"月结,老客户","machineCount":6,"mainProducts":"T恤"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profile.customerType").value("FACTORY"))
                .andExpect(jsonPath("$.data.profile.mobile").value("13900008821"))
                .andReturn());
        long id = created.path("data").path("profile").path("id").asLong();

        mockMvc.perform(get("/api/admin/customers")
                        .header("Authorization", "Bearer " + boss)
                        .param("name", "测试服装厂")
                        .param("customerType", "FACTORY")
                        .param("tag", "月结")
                        .param("region", "西湖"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].id").value(id))
                .andExpect(jsonPath("$.data.list[0].orderAmount").value(0))
                .andExpect(jsonPath("$.data.list[0].unpaidAmount").value(0));

        mockMvc.perform(post("/api/admin/customers/" + id + "/follows")
                        .header("Authorization", "Bearer " + boss)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"下周去看车间\",\"nextDate\":\"2026-12-01\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.follows[0].content").value("下周去看车间"));

        mockMvc.perform(put("/api/admin/customers/" + id + "/level")
                        .header("Authorization", "Bearer " + boss)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"levelCode\":\"VIP\",\"manual\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profile.levelCode").value("VIP"))
                .andExpect(jsonPath("$.data.profile.levelManual").value(true));

        mockMvc.perform(get("/api/admin/customers/export")
                        .header("Authorization", "Bearer " + boss)
                        .param("name", "测试服装厂"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(containsString("测试服装厂")));
    }

    @Test
    void agreementReminderIsVisibleOnlyToBoss() throws Exception {
        String boss = login("boss", "boss123");
        String admin = login("admin", "admin123");
        JsonNode created = read(mockMvc.perform(post("/api/admin/customers")
                        .header("Authorization", "Bearer " + boss)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"欠款客户\",\"mobile\":\"13900008822\",\"source\":\"门店散客\"}"))
                .andExpect(status().isOk())
                .andReturn());
        long id = created.path("data").path("profile").path("id").asLong();
        mockMvc.perform(post("/api/admin/customers/" + id + "/agreements")
                        .header("Authorization", "Bearer " + boss)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scope":"CUSTOMER","dueDate":"2020-01-01","agreedAmount":8800,"note":"月底前还清",
                                 "materials":[{"kind":"TEXT","text":"口头说好了"}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overdue").value(true));

        mockMvc.perform(get("/api/admin/customers/repayments")
                        .header("Authorization", "Bearer " + boss)
                        .param("bucket", "OVERDUE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[?(@.customerId==" + id + ")].agreedAmount").exists());

        int createdNotices = read(mockMvc.perform(post("/api/admin/customers/repayments/scan")
                        .header("Authorization", "Bearer " + boss))
                .andExpect(status().isOk())
                .andReturn()).path("data").asInt();
        assertThat(createdNotices).isGreaterThan(0);

        mockMvc.perform(get("/api/admin/notices").param("unread", "1").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[?(@.eventType=='REPAY_OVERDUE')]").isEmpty());
        mockMvc.perform(get("/api/admin/notices").param("unread", "1").header("Authorization", "Bearer " + boss))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[?(@.audience=='BOSS')].eventType").isNotEmpty());

        mockMvc.perform(post("/api/admin/customers/repayments/scan").header("Authorization", "Bearer " + boss))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(0));
    }

    @Test
    void missingDeepSeekKeyIsAFriendlyResponse() throws Exception {
        String boss = login("boss", "boss123");
        JsonNode created = read(mockMvc.perform(post("/api/admin/customers")
                        .header("Authorization", "Bearer " + boss)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"分析客户\",\"mobile\":\"13900008823\"}"))
                .andReturn());
        long id = created.path("data").path("profile").path("id").asLong();
        mockMvc.perform(post("/api/admin/customers/" + id + "/insights").header("Authorization", "Bearer " + boss))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(false))
                .andExpect(jsonPath("$.data.message", containsString("DEEPSEEK_API_KEY")));
        mockMvc.perform(post("/api/admin/customers/insights").header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void bossCanImportAndUploadAgreementFiles() throws Exception {
        String boss = login("boss", "boss123");
        String admin = login("admin", "admin123");
        String csv = "姓名,手机,客户类型,地区,等级\n导入客户,13900008824,加工作坊,宁波市鄞州区,银卡\n";
        mockMvc.perform(multipart("/api/admin/customers/import")
                        .file(new MockMultipartFile("file", "customers.csv", "text/csv", csv.getBytes()))
                        .header("Authorization", "Bearer " + boss))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.created").value(1));
        mockMvc.perform(get("/api/admin/customers")
                        .header("Authorization", "Bearer " + boss)
                        .param("mobile", "13900008824"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].customerType").value("WORKSHOP"))
                .andExpect(jsonPath("$.data.list[0].levelManual").value(true))
                .andExpect(jsonPath("$.data.list[0].levelCode").value("SILVER"));

        mockMvc.perform(multipart("/api/admin/files")
                        .file(new MockMultipartFile("file", "talk.wav", "audio/wav", WAV))
                        .param("biz", "agreement")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isForbidden());
        mockMvc.perform(multipart("/api/admin/files")
                        .file(new MockMultipartFile("file", "talk.wav", "audio/wav", WAV))
                        .param("biz", "payment")
                        .header("Authorization", "Bearer " + boss))
                .andExpect(status().isBadRequest());
        mockMvc.perform(multipart("/api/admin/files")
                        .file(new MockMultipartFile("file", "talk.wav", "audio/wav", WAV))
                        .param("biz", "agreement")
                        .header("Authorization", "Bearer " + boss))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].kind").value("AUDIO"))
                .andExpect(jsonPath("$.data[0].url", containsString("/uploads/agreement/")));
    }

    @Test
    void demoDataCanBeSeededAndClearedByBossOnly() throws Exception {
        String admin = login("admin", "admin123");
        String boss = login("boss", "boss123");
        mockMvc.perform(post("/api/admin/customers/demo").header("Authorization", "Bearer " + admin))
                .andExpect(status().isForbidden());
        try {
            JsonNode seeded = read(mockMvc.perform(post("/api/admin/customers/demo").header("Authorization", "Bearer " + boss))
                    .andExpect(status().isOk())
                    .andReturn());
            assertThat(seeded.path("data").path("created").asBoolean()).isTrue();
            assertThat(seeded.path("data").path("customers").asInt()).isEqualTo(30);
            mockMvc.perform(post("/api/admin/customers/demo").header("Authorization", "Bearer " + boss))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.created").value(false));
            mockMvc.perform(get("/api/admin/customers")
                            .header("Authorization", "Bearer " + boss)
                            .param("name", "沈建华")
                            .param("unpaidOnly", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.list[0].overdue").value(true))
                    .andExpect(jsonPath("$.data.list[0].unpaidAmount").isNumber());
            mockMvc.perform(get("/api/admin/customers/overview").header("Authorization", "Bearer " + boss))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.customerCount").isNumber())
                    .andExpect(jsonPath("$.data.levels").isArray());
        } finally {
            mockMvc.perform(delete("/api/admin/customers/demo").header("Authorization", "Bearer " + boss))
                    .andExpect(status().isOk());
        }
    }

    private String createSales(String admin) throws Exception {
        mockMvc.perform(post("/api/admin/staff")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"客户模块业务员","mobile":"13900007701","role":"SALES","username":"custsales","password":"sales123"}
                                """))
                .andExpect(status().isOk());
        return login("custsales", "sales123");
    }

    private String adminToken() throws Exception {
        return login("admin", "admin123");
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
