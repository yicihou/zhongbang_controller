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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 图片上传和员工管理：员工才能上传，只有老板能改员工，停用的师傅不能再派工。
 * 管理员访问员工管理要 403，但报修和安装用的启用中师傅名单仍然能查。
 */
@SpringBootTest
@AutoConfigureMockMvc
class StaffAndUploadTest {

    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void staffCanUploadAnImageAndOpenItWithoutAToken() throws Exception {
        String admin = login("admin", "admin123");
        mockMvc.perform(multipart("/api/admin/files").file(png("shot.png")).param("biz", "payment"))
                .andExpect(status().isUnauthorized());

        MvcResult uploaded = mockMvc.perform(multipart("/api/admin/files")
                        .file(png("shot.png"))
                        .param("biz", "payment")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].url").isString())
                .andReturn();
        String url = read(uploaded).path("data").get(0).path("url").asText();
        assertThat(url).startsWith("/uploads/payment/");
        assertThat(Files.exists(Path.of("uploads").resolve(url.substring("/uploads/".length())))).isTrue();

        mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(content().bytes(PNG));

        mockMvc.perform(multipart("/api/admin/files")
                        .file(new MockMultipartFile("file", "note.jpg", "image/jpeg", "not-a-picture".getBytes()))
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));

        String customer = loginCustomer("upload-buyer");
        JsonNode order = read(mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contactName":"上传客户","contactMobile":"13800002222","addressText":"杭州市西湖区",
                                 "items":[{"skuId":1021,"quantity":1,"deliveryMethod":"EXPRESS"}]}
                                """))
                .andExpect(status().isOk())
                .andReturn());
        long orderId = order.path("data").path("orderId").asLong();
        JsonNode detail = read(mockMvc.perform(get("/api/admin/orders/" + orderId)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn());
        long paymentId = detail.path("data").path("payments").get(0).path("id").asLong();
        mockMvc.perform(post("/api/admin/payments/" + paymentId + "/offline-confirm")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"WECHAT_TRANSFER\",\"voucherUrls\":[\"" + url + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.voucherUrls[0]").value(url));
    }

    @Test
    void onlyBossManagesStaffAndDisabledTechnicianCannotBeAssigned() throws Exception {
        String admin = login("admin", "admin123");
        String boss = login("boss", "boss123");
        mockMvc.perform(get("/api/admin/staff").header("Authorization", "Bearer " + admin))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(2003));
        mockMvc.perform(post("/api/admin/staff")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"不该建成","mobile":"13900001111","role":"SALES","username":"nope1","password":"sales123"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(2003));

        mockMvc.perform(get("/api/admin/staff").header("Authorization", "Bearer " + boss))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[?(@.username=='boss')].role", hasItem("BOSS")));

        mockMvc.perform(post("/api/admin/staff/user-2/status")
                        .header("Authorization", "Bearer " + boss)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg").value("不能停用自己的账号"));
        mockMvc.perform(put("/api/admin/staff/user-2")
                        .header("Authorization", "Bearer " + boss)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"老板","mobile":"13900000000","role":"ADMIN","status":"ENABLED"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg").value("不能改掉自己的老板角色"));

        JsonNode sales = read(mockMvc.perform(post("/api/admin/staff")
                        .header("Authorization", "Bearer " + boss)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"周业务","mobile":"13911112222","role":"SALES","username":"sales1","password":"sales123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loginAccount").value(true))
                .andReturn());
        String salesRef = sales.path("data").path("ref").asText();
        mockMvc.perform(post("/api/admin/staff/" + salesRef + "/password")
                        .header("Authorization", "Bearer " + boss)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"newpass1\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/staff/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"sales1\",\"password\":\"sales123\"}"))
                .andExpect(status().isUnauthorized());
        String salesToken = login("sales1", "newpass1");
        mockMvc.perform(get("/api/admin/staff").header("Authorization", "Bearer " + salesToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(2003));
        mockMvc.perform(get("/api/admin/orders").header("Authorization", "Bearer " + salesToken))
                .andExpect(status().isOk());

        JsonNode tech = read(mockMvc.perform(post("/api/admin/staff")
                        .header("Authorization", "Bearer " + boss)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"赵师傅","mobile":"13922223333","role":"TECHNICIAN","region":"城南","skills":["平缝机"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loginAccount").value(false))
                .andReturn());
        long technicianId = tech.path("data").path("technicianId").asLong();
        String techRef = tech.path("data").path("ref").asText();
        mockMvc.perform(get("/api/admin/technicians").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id==" + technicianId + ")].name", hasItem("赵师傅")));

        mockMvc.perform(post("/api/admin/staff/" + techRef + "/status")
                        .header("Authorization", "Bearer " + boss)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"));
        mockMvc.perform(get("/api/admin/technicians").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id==" + technicianId + ")]").isEmpty());

        String customer = loginCustomer("staff-repair-buyer");
        JsonNode created = read(mockMvc.perform(post("/api/repairs")
                        .header("Authorization", "Bearer " + customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectDate":"2026-12-08","lines":[{
                                  "external":{"brand":"外牌","model":"M8","faultDesc":"不转","images":["https://img.example/c.jpg"]},
                                  "faultType":"NOISE","faultDesc":"不转","images":["https://img.example/c.jpg"]
                                }]}
                                """))
                .andExpect(status().isOk())
                .andReturn());
        long ticketId = created.path("data").path("id").asLong();
        JsonNode detail = read(mockMvc.perform(get("/api/admin/repairs/" + ticketId)
                        .header("Authorization", "Bearer " + admin))
                .andReturn());
        long lineId = detail.path("data").path("lines").get(0).path("id").asLong();
        mockMvc.perform(post("/api/admin/repairs/" + ticketId + "/quote")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inWarranty\":true,\"lines\":[{\"lineId\":" + lineId + ",\"lineQuoteAmount\":0}]}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/repairs/" + ticketId + "/assign")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lineId\":" + lineId + ",\"technicianId\":" + technicianId + ",\"appointAt\":\"2026-12-08 09:30:00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg").value("师傅已停用，不能派工"));

        mockMvc.perform(put("/api/admin/staff/" + techRef)
                        .header("Authorization", "Bearer " + boss)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"赵师傅","mobile":"13922223333","role":"TECHNICIAN","status":"ENABLED","region":"城南","skills":["平缝机"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ENABLED"));
        mockMvc.perform(post("/api/admin/repairs/" + ticketId + "/assign")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lineId\":" + lineId + ",\"technicianId\":" + technicianId + ",\"appointAt\":\"2026-12-08 09:30:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lines[0].technicianName").value("赵师傅"));
    }

    private MockMultipartFile png(String name) {
        return new MockMultipartFile("file", name, "image/png", PNG);
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
