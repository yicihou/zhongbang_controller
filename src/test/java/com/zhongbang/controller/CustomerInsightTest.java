package com.zhongbang.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhongbang.controller.adapter.deepseek.DeepSeekClient;
import com.zhongbang.controller.adapter.deepseek.DeepSeekException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 分析接口会调用 DeepSeek。这里把 HTTP 客户端换成假的，确认手机号和门牌不会发走，失败也不返回 500。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "zhongbang.deepseek.api-key=test-key-not-real")
class CustomerInsightTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DeepSeekClient deepSeekClient;

    @Test
    void analysisStoresResultAndMasksContact() throws Exception {
        when(deepSeekClient.chat(anyString(), anyString())).thenReturn("客户画像：服装厂，建议跟进平缝机耗材。");
        String boss = login("boss", "boss123");
        JsonNode created = read(mockMvc.perform(post("/api/admin/customers")
                        .header("Authorization", "Bearer " + boss)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"脱敏客户","mobile":"13900008831","altMobile":"13900008832",
                                 "region":"杭州市西湖区","customerType":"FACTORY"}
                                """))
                .andExpect(status().isOk())
                .andReturn());
        long id = created.path("data").path("profile").path("id").asLong();
        mockMvc.perform(post("/api/admin/customers/" + id + "/addresses")
                        .header("Authorization", "Bearer " + boss)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"detail\":\"浙江省杭州市西湖区文三路 188 号\",\"defaultAddress\":true}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/customers/" + id + "/insights").header("Authorization", "Bearer " + boss))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(true))
                .andExpect(jsonPath("$.data.ok").value(true))
                .andExpect(jsonPath("$.data.analysis.resultText").value(org.hamcrest.Matchers.containsString("客户画像")));

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(deepSeekClient).chat(anyString(), prompt.capture());
        assertThat(prompt.getValue()).doesNotContain("13900008831");
        assertThat(prompt.getValue()).doesNotContain("13900008832");
        assertThat(prompt.getValue()).doesNotContain("文三路");
        assertThat(prompt.getValue()).contains("****");

        mockMvc.perform(get("/api/admin/customers/" + id + "/insights").header("Authorization", "Bearer " + boss))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].resultText").exists());

        when(deepSeekClient.chat(anyString(), anyString())).thenThrow(new DeepSeekException("分析超时，请稍后再试"));
        mockMvc.perform(post("/api/admin/customers/insights").header("Authorization", "Bearer " + boss))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ok").value(false))
                .andExpect(jsonPath("$.data.message").value("分析超时，请稍后再试"));
    }

    private String login(String username, String password) throws Exception {
        JsonNode body = read(mockMvc.perform(post("/api/auth/staff/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn());
        return body.path("data").path("token").asText();
    }

    private JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
