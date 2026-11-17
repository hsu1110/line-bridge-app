package com.ytec.linebridge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ytec.linebridge.entity.ApiClient;
import com.ytec.linebridge.entity.BotAccount;
import com.ytec.linebridge.entity.Contact;
import com.ytec.linebridge.entity.Template;
import com.ytec.linebridge.entity.WebhookEventLog;
import com.ytec.linebridge.mapper.ApiClientMapper;
import com.ytec.linebridge.mapper.BotAccountMapper;
import com.ytec.linebridge.mapper.ContactMapper;
import com.ytec.linebridge.mapper.TemplateMapper;
import com.ytec.linebridge.mapper.WebhookEventLogMapper;
import com.ytec.linebridge.service.ContactBindingService;
import com.ytec.linebridge.service.LineApiService;
import com.ytec.linebridge.service.TemplateEngineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LineBridgeApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BotAccountMapper botAccountMapper;

    @Autowired
    private ContactMapper contactMapper;

    @Autowired
    private TemplateMapper templateMapper;

    @Autowired
    private ApiClientMapper apiClientMapper;

    @Autowired
    private WebhookEventLogMapper webhookEventLogMapper;

    @Autowired
    private ContactBindingService contactBindingService;

    @Autowired
    private TemplateEngineService templateEngineService;

    @MockBean
    private LineApiService lineApiService;

    @BeforeEach
    void setup() {
        webhookEventLogMapper.delete(null);
        apiClientMapper.delete(null);
        templateMapper.delete(null);
        contactMapper.delete(null);
        botAccountMapper.delete(null);
    }

    @Test
    void contactBindingGeneratesSafeAlphanumericShortCodesAndTruncatesLongNames() {
        BotAccount bot = new BotAccount();
        bot.setId("bot-sc");
        bot.setBotCode("SC_BOT");
        bot.setName("ShortCode Bot");
        bot.setChannelAccessToken("token");
        bot.setChannelSecret("secret");
        bot.setQuotaLimit(200);
        bot.setEnabled(1);
        bot.setCreatedAt(LocalDateTime.now());
        bot.setUpdatedAt(LocalDateTime.now());
        botAccountMapper.insert(bot);

        String superLongName = "測試超長群組名稱🚀".repeat(30);
        Contact group = contactBindingService.upsertAndBind("Cgroup999", superLongName, "group", "bot-sc");
        assertThat(group.getCode()).matches("^GRP_[0-9A-HJ-NP-Z]{6}$");
        assertThat(group.getName().getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(180);

        Contact user = contactBindingService.upsertAndBind("Uuser999", "小明", "user", "bot-sc");
        assertThat(user.getCode()).matches("^USR_[0-9A-HJ-NP-Z]{6}$");
    }

    @Test
    void templateEngineValidatesMissingVarsAndEscapesDollarSigns() {
        List<String> vars = templateEngineService.extractVariables("機台 {{machine_id}} 報價 {{cost}} 通知");
        assertThat(vars).containsExactly("machine_id", "cost");

        String rendered = templateEngineService.renderStrict(
                "機台 {{machine_id}} 費用 {{cost}}",
                Map.of("machine_id", "M-01", "cost", "$1,200")
        );
        assertThat(rendered).isEqualTo("機台 M-01 費用 $1,200");
    }

    @Test
    void adminLoginAndAuthProtectionWork() throws Exception {
        mockMvc.perform(get("/api/admin/bots"))
                .andExpect(status().isUnauthorized());

        String loginBody = objectMapper.writeValueAsString(Map.of("username", "admin", "password", "test-password"));
        String resp = mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(resp).get("token").asText();
        mockMvc.perform(get("/api/admin/bots")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void webhookVerifiesSignatureAndDeduplicatesEventId() throws Exception {
        BotAccount bot = new BotAccount();
        bot.setId("bot-1");
        bot.setBotCode("IT_BOT");
        bot.setName("IT Bot");
        bot.setChannelAccessToken("dummy-token");
        bot.setChannelSecret("my-channel-secret");
        bot.setQuotaLimit(200);
        bot.setEnabled(1);
        bot.setCreatedAt(LocalDateTime.now());
        bot.setUpdatedAt(LocalDateTime.now());
        botAccountMapper.insert(bot);

        byte[] payload = """
                {
                  "destination": "Ubot123",
                  "events": [
                    {
                      "type": "message",
                      "webhookEventId": "01TESTULID0000000000000001",
                      "deliveryContext": { "isRedelivery": false },
                      "source": { "type": "user", "userId": "Uuser001" },
                      "message": { "type": "text", "text": "hello" }
                    }
                  ]
                }
                """.getBytes(StandardCharsets.UTF_8);

        // 錯誤簽章應回傳 403
        mockMvc.perform(post("/api/webhook/IT_BOT")
                        .header("X-Line-Signature", "invalid-sig")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden());

        String validSig = computeHmacBase64(payload, "my-channel-secret");

        // 第一次合法請求應通過
        mockMvc.perform(post("/api/webhook/IT_BOT")
                        .header("X-Line-Signature", validSig)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        // 重複相同 webhookEventId 應標記為 DUPLICATE 而非重複處理
        mockMvc.perform(post("/api/webhook/IT_BOT")
                        .header("X-Line-Signature", validSig)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        List<WebhookEventLog> logs = webhookEventLogMapper.selectList(null);
        assertThat(logs).anyMatch(l -> "DUPLICATE".equals(l.getProcessStatus()));
    }

    @Test
    void sendApiChecksApiKeyClientWhitelistBindingAndMissingParams() throws Exception {
        BotAccount itBot = new BotAccount();
        itBot.setId("bot-it");
        itBot.setBotCode("IT_BOT");
        itBot.setName("IT 報修");
        itBot.setChannelAccessToken("token-it");
        itBot.setChannelSecret("secret-it");
        itBot.setQuotaLimit(200);
        itBot.setEnabled(1);
        itBot.setCreatedAt(LocalDateTime.now());
        itBot.setUpdatedAt(LocalDateTime.now());
        botAccountMapper.insert(itBot);

        Contact target = new Contact();
        target.setId("contact-1");
        target.setCode("IT_ALERT");
        target.setName("IT 群組");
        target.setLineId("Cgroup123");
        target.setContactType("group");
        target.setCreatedAt(LocalDateTime.now());
        target.setUpdatedAt(LocalDateTime.now());
        contactMapper.insert(target);

        Template tpl = new Template();
        tpl.setId("tpl-1");
        tpl.setCode("MACHINE_ERR");
        tpl.setName("機台異常");
        tpl.setContent("機台 {{machine_id}} 發生 {{reason}}");
        tpl.setRequiredVars("[\"machine_id\",\"reason\"]");
        tpl.setCreatedAt(LocalDateTime.now());
        tpl.setUpdatedAt(LocalDateTime.now());
        templateMapper.insert(tpl);

        ApiClient erp = new ApiClient();
        erp.setId("client-erp");
        erp.setClientCode("ERP");
        erp.setClientName("ERP 系統");
        erp.setApiKey("lbk_test_erp_key");
        erp.setAllowedBotCodes("IT_BOT");
        erp.setAllowedTemplateCodes("MACHINE_ERR");
        erp.setRateLimitPerMin(60);
        erp.setEnabled(1);
        erp.setCreatedAt(LocalDateTime.now());
        erp.setUpdatedAt(LocalDateTime.now());
        apiClientMapper.insert(erp);

        String sendBody = """
                {
                  "bot_code": "IT_BOT",
                  "target_code": "IT_ALERT",
                  "template_code": "MACHINE_ERR",
                  "params": { "machine_id": "CNC-01" }
                }
                """;

        // 未帶 API Key 應回 401
        mockMvc.perform(post("/api/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sendBody))
                .andExpect(status().isUnauthorized());

        // 聯絡人尚未綁定 IT_BOT 應回 400
        mockMvc.perform(post("/api/send")
                        .header("x-api-key", "lbk_test_erp_key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sendBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("目標 [IT_ALERT] 尚未綁定機器人 [IT_BOT]"));

        // 綁定後但缺少模板參數 reason 應回 400
        contactBindingService.ensureBinding("contact-1", "bot-it");
        mockMvc.perform(post("/api/send")
                        .header("x-api-key", "lbk_test_erp_key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sendBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("缺少模板必填參數: reason"));

        // 補齊參數應回 202 QUEUED 並產出 jobId
        String completeBody = """
                {
                  "bot_code": "IT_BOT",
                  "target_code": "IT_ALERT",
                  "template_code": "MACHINE_ERR",
                  "params": { "machine_id": "CNC-01", "reason": "溫度過高" }
                }
                """;
        mockMvc.perform(post("/api/send")
                        .header("x-api-key", "lbk_test_erp_key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(completeBody))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.renderedContent").value("機台 CNC-01 發生 溫度過高"));
    }

    private String computeHmacBase64(byte[] body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(body));
    }
}
