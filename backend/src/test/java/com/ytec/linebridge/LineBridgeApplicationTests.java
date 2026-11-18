package com.ytec.linebridge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ytec.linebridge.config.LineBridgeProperties;
import com.ytec.linebridge.entity.ApiClient;
import com.ytec.linebridge.entity.BotAccount;
import com.ytec.linebridge.entity.Contact;
import com.ytec.linebridge.entity.SendLog;
import com.ytec.linebridge.entity.Template;
import com.ytec.linebridge.entity.WebhookEventLog;
import com.ytec.linebridge.mapper.ApiClientMapper;
import com.ytec.linebridge.mapper.BotAccountMapper;
import com.ytec.linebridge.mapper.ContactBotMapper;
import com.ytec.linebridge.mapper.ContactMapper;
import com.ytec.linebridge.mapper.SendLogMapper;
import com.ytec.linebridge.mapper.TemplateMapper;
import com.ytec.linebridge.mapper.WebhookEventLogMapper;
import com.ytec.linebridge.service.AsyncWebhookExecutor;
import com.ytec.linebridge.service.BotQuotaCacheService;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    private SendLogMapper sendLogMapper;

    @Autowired
    private ContactBotMapper contactBotMapper;

    @Autowired
    private ContactBindingService contactBindingService;

    @Autowired
    private TemplateEngineService templateEngineService;

    @Autowired
    private BotQuotaCacheService botQuotaCacheService;

    @Autowired
    private AsyncWebhookExecutor asyncWebhookExecutor;

    @MockBean
    private LineApiService lineApiService;

    @BeforeEach
    void setup() {
        webhookEventLogMapper.delete(null);
        sendLogMapper.delete(null);
        contactBotMapper.delete(null);
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

    @Test
    void dashboardAggregatesStatsInSqlAndCachesBotQuotas() throws Exception {
        BotAccount bot = new BotAccount();
        bot.setId("bot-dash");
        bot.setBotCode("DASH_BOT");
        bot.setName("儀表板測試機器人");
        bot.setChannelAccessToken("dash-token");
        bot.setChannelSecret("dash-secret");
        bot.setQuotaLimit(200);
        bot.setEnabled(1);
        bot.setCreatedAt(LocalDateTime.now());
        bot.setUpdatedAt(LocalDateTime.now());
        botAccountMapper.insert(bot);
        botQuotaCacheService.evict("bot-dash");

        when(lineApiService.getQuota("dash-token")).thenReturn(Map.of("type", "limited", "value", 200));
        when(lineApiService.getQuotaConsumption("dash-token")).thenReturn(Map.of("totalUsage", 18));

        LocalDateTime now = LocalDateTime.now();
        insertSendLog("log-1", "DASH_BOT", "TPL_A", "SUCCESS", now);
        insertSendLog("log-2", "DASH_BOT", "TPL_A", "SUCCESS", now);
        insertSendLog("log-3", "DASH_BOT", null, "FAILED", now);
        insertSendLog("log-old", "DASH_BOT", "TPL_OLD", "SUCCESS", now.minusDays(2));

        String loginBody = objectMapper.writeValueAsString(Map.of("username", "admin", "password", "test-password"));
        String resp = mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(resp).get("token").asText();

        // 第一次查詢：驗證 SQL 聚合結果與額度 API 呼叫 1 次
        mockMvc.perform(get("/api/admin/dashboard")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todayTotal").value(3))
                .andExpect(jsonPath("$.todaySuccess").value(2))
                .andExpect(jsonPath("$.todayFailed").value(1))
                .andExpect(jsonPath("$.templateStats[0].name").value("TPL_A"))
                .andExpect(jsonPath("$.templateStats[0].count").value(2))
                .andExpect(jsonPath("$.templateStats[1].name").value("DIRECT"))
                .andExpect(jsonPath("$.botQuotas[0].totalUsage").value(18));

        verify(lineApiService, times(1)).getQuota("dash-token");

        // 第二次查詢應命中快取，不重複呼叫 LINE API
        mockMvc.perform(get("/api/admin/dashboard")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        verify(lineApiService, times(1)).getQuota("dash-token");

        // 帶 refresh=true 應強制更新額度
        mockMvc.perform(get("/api/admin/dashboard?refresh=true")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        verify(lineApiService, times(2)).getQuota("dash-token");
    }

    @Test
    void webhookRateLimitsInvalidRequestsAndTruncatesOversizedPayloads() throws Exception {
        String longInvalidJson = "{invalid:" + "x".repeat(2500);
        String attackerIp = "203.0.113.99";

        // 前 20 次寫入截斷後的紀錄並回 400
        for (int i = 0; i < 20; i++) {
            mockMvc.perform(post("/api/webhook/ANY_BOT")
                            .header("X-Forwarded-For", attackerIp)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(longInvalidJson))
                    .andExpect(status().isBadRequest());
        }

        List<WebhookEventLog> savedLogs = webhookEventLogMapper.selectList(null);
        assertThat(savedLogs).hasSize(20);
        assertThat(savedLogs.get(0).getRawPayload()).contains("...(已截斷");

        // 第 21 次應觸發失敗來源限流回 429，且不再寫入資料庫
        mockMvc.perform(post("/api/webhook/ANY_BOT")
                        .header("X-Forwarded-For", attackerIp)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(longInvalidJson))
                .andExpect(status().isTooManyRequests());

        assertThat(webhookEventLogMapper.selectCount(null)).isEqualTo(20L);
    }

    @Test
    void botCustomWelcomeMessageAndDisableSwitchWork() throws InterruptedException {
        BotAccount customBot = new BotAccount();
        customBot.setId("bot-custom-welcome");
        customBot.setBotCode("WEL_BOT");
        customBot.setName("客服小幫手");
        customBot.setChannelAccessToken("wel-token");
        customBot.setChannelSecret("wel-secret");
        customBot.setQuotaLimit(200);
        customBot.setEnabled(1);
        customBot.setWelcomeEnabled(1);
        customBot.setWelcomeMessage("哈囉 {{name}}，這是 {{bot_name}} 的自訂歡迎詞！");
        customBot.setCreatedAt(LocalDateTime.now());
        customBot.setUpdatedAt(LocalDateTime.now());

        when(lineApiService.getProfile("wel-token", "Uwel01"))
                .thenReturn(Map.of("displayName", "王小明"));

        botAccountMapper.insert(customBot);

        insertReceivedWebhookLog("evt-custom", "WEL_BOT");
        Map<String, Object> followEvent = Map.of(
                "type", "follow",
                "replyToken", "rt-custom",
                "source", Map.of("type", "user", "userId", "Uwel01")
        );
        asyncWebhookExecutor.processEventAsync("evt-custom", customBot, followEvent);
        waitUntilWebhookProcessed("evt-custom");

        verify(lineApiService).replyTextMessage(
                eq("wel-token"),
                eq("rt-custom"),
                eq("哈囉 王小明，這是 客服小幫手 的自訂歡迎詞！")
        );

        // 關閉自動歡迎後，不應再呼叫 replyTextMessage，但仍維持處理完成
        customBot.setWelcomeEnabled(0);
        insertReceivedWebhookLog("evt-silent", "WEL_BOT");
        Map<String, Object> silentEvent = Map.of(
                "type", "follow",
                "replyToken", "rt-silent",
                "source", Map.of("type", "user", "userId", "Uwel01")
        );
        asyncWebhookExecutor.processEventAsync("evt-silent", customBot, silentEvent);
        waitUntilWebhookProcessed("evt-silent");
        verify(lineApiService, never()).replyTextMessage(anyString(), eq("rt-silent"), anyString());

        // 清空自訂歡迎詞並重新啟用後，應退回預設提示（含 IT 人員聯繫說明）
        customBot.setWelcomeEnabled(1);
        customBot.setWelcomeMessage(null);
        insertReceivedWebhookLog("evt-default", "WEL_BOT");
        Map<String, Object> defaultEvent = Map.of(
                "type", "follow",
                "replyToken", "rt-default",
                "source", Map.of("type", "user", "userId", "Uwel01")
        );
        asyncWebhookExecutor.processEventAsync("evt-default", customBot, defaultEvent);
        waitUntilWebhookProcessed("evt-default");
        verify(lineApiService).replyTextMessage(
                eq("wel-token"),
                eq("rt-default"),
                contains("IT 人員聯繫")
        );
    }

    @Test
    void lineApiServiceInitializesWithJdkHttpClientPool() {
        LineBridgeProperties props = new LineBridgeProperties();
        props.setConnectTimeoutMs(1500);
        props.setReadTimeoutMs(3000);
        LineApiService realService = new LineApiService(props);
        assertThat(realService).isNotNull();
    }

    @Test
    void clearingOptionalFieldsOnUpdatePersistsSqlNull() throws Exception {
        BotAccount bot = new BotAccount();
        bot.setId("bot-clear");
        bot.setBotCode("CLEAR_BOT");
        bot.setName("清空測試機器人");
        bot.setChannelAccessToken("tok");
        bot.setChannelSecret("sec");
        bot.setQuotaLimit(200);
        bot.setEnabled(1);
        bot.setWelcomeEnabled(1);
        bot.setWelcomeMessage("原本的自訂歡迎詞");
        bot.setCreatedAt(LocalDateTime.now());
        bot.setUpdatedAt(LocalDateTime.now());
        botAccountMapper.insert(bot);

        ApiClient client = new ApiClient();
        client.setId("cli-clear");
        client.setClientCode("CLR");
        client.setClientName("清空權限測試");
        client.setApiKey("lbk_clear_test");
        client.setAllowedBotCodes("CLEAR_BOT");
        client.setAllowedTemplateCodes("TPL_1");
        client.setRateLimitPerMin(60);
        client.setEnabled(1);
        client.setCreatedAt(LocalDateTime.now());
        client.setUpdatedAt(LocalDateTime.now());
        apiClientMapper.insert(client);

        String loginBody = objectMapper.writeValueAsString(Map.of("username", "admin", "password", "test-password"));
        String resp = mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(resp).get("token").asText();

        String clearBotBody = objectMapper.writeValueAsString(Map.of(
                "botCode", "CLEAR_BOT",
                "name", "清空測試機器人",
                "welcomeEnabled", 1,
                "welcomeMessage", ""
        ));
        mockMvc.perform(put("/api/admin/bots/bot-clear")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clearBotBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.welcomeMessage").doesNotExist());

        BotAccount reloadedBot = botAccountMapper.selectById("bot-clear");
        assertThat(reloadedBot.getWelcomeMessage()).isNull();

        String clearClientBody = objectMapper.writeValueAsString(Map.of(
                "clientCode", "CLR",
                "clientName", "清空權限測試",
                "allowedBotCodes", "",
                "allowedTemplateCodes", ""
        ));
        mockMvc.perform(put("/api/admin/clients/cli-clear")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clearClientBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowedBotCodes").doesNotExist())
                .andExpect(jsonPath("$.allowedTemplateCodes").doesNotExist());

        ApiClient reloadedClient = apiClientMapper.selectById("cli-clear");
        assertThat(reloadedClient.getAllowedBotCodes()).isNull();
        assertThat(reloadedClient.getAllowedTemplateCodes()).isNull();
    }

    @Test
    void sendSupportsCamelCaseEnforcesJobOwnershipRejectsQueryKeyAndChecksLength() throws Exception {
        BotAccount bot = new BotAccount();
        bot.setId("bot-sec");
        bot.setBotCode("SEC_BOT");
        bot.setName("安全測試 Bot");
        bot.setChannelAccessToken("sec-token");
        bot.setChannelSecret("sec-secret");
        bot.setQuotaLimit(200);
        bot.setEnabled(1);
        bot.setCreatedAt(LocalDateTime.now());
        bot.setUpdatedAt(LocalDateTime.now());
        botAccountMapper.insert(bot);

        Contact target = new Contact();
        target.setId("con-sec");
        target.setCode("SEC_TARGET");
        target.setName("安全測試對象");
        target.setLineId("Usec001");
        target.setContactType("user");
        target.setCreatedAt(LocalDateTime.now());
        target.setUpdatedAt(LocalDateTime.now());
        contactMapper.insert(target);
        contactBindingService.ensureBinding("con-sec", "bot-sec");

        Template tpl = new Template();
        tpl.setId("tpl-sec");
        tpl.setCode("SEC_TPL");
        tpl.setName("安全測試模板");
        tpl.setContent("通知：{{msg}}");
        tpl.setRequiredVars("[\"msg\"]");
        tpl.setCreatedAt(LocalDateTime.now());
        tpl.setUpdatedAt(LocalDateTime.now());
        templateMapper.insert(tpl);

        ApiClient clientA = new ApiClient();
        clientA.setId("cli-a");
        clientA.setClientCode("SYS_A");
        clientA.setClientName("系統 A");
        clientA.setApiKey("lbk_sys_a");
        clientA.setRateLimitPerMin(60);
        clientA.setEnabled(1);
        clientA.setCreatedAt(LocalDateTime.now());
        clientA.setUpdatedAt(LocalDateTime.now());
        apiClientMapper.insert(clientA);

        ApiClient clientB = new ApiClient();
        clientB.setId("cli-b");
        clientB.setClientCode("SYS_B");
        clientB.setClientName("系統 B");
        clientB.setApiKey("lbk_sys_b");
        clientB.setRateLimitPerMin(60);
        clientB.setEnabled(1);
        clientB.setCreatedAt(LocalDateTime.now());
        clientB.setUpdatedAt(LocalDateTime.now());
        apiClientMapper.insert(clientB);

        // 1. 禁止透過 Query Parameter ?api_key= 傳遞金鑰
        String camelBody = """
                {
                  "botCode": "SEC_BOT",
                  "targetCode": "SEC_TARGET",
                  "templateCode": "SEC_TPL",
                  "params": { "msg": "駝峰參數測試" }
                }
                """;
        mockMvc.perform(post("/api/send?api_key=lbk_sys_a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(camelBody))
                .andExpect(status().isUnauthorized());

        // 2. camelCase 欄位名應能正常被反序列化與受理
        String sendResp = mockMvc.perform(post("/api/send")
                        .header("x-api-key", "lbk_sys_a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(camelBody))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.renderedContent").value("通知：駝峰參數測試"))
                .andReturn().getResponse().getContentAsString();
        String jobId = objectMapper.readTree(sendResp).get("jobId").asText();

        // 3. 系統 A 查自己的 jobId 應成功；系統 B 跨系統查詢應回 403
        mockMvc.perform(get("/api/send/jobs/" + jobId)
                        .header("x-api-key", "lbk_sys_a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(jobId));

        mockMvc.perform(get("/api/send/jobs/" + jobId)
                        .header("x-api-key", "lbk_sys_b"))
                .andExpect(status().isForbidden());

        // 4. 超過 5000 字元預檢應直接回 400
        String tooLongBody = objectMapper.writeValueAsString(Map.of(
                "botCode", "SEC_BOT",
                "targetCode", "SEC_TARGET",
                "message", "A".repeat(5001)
        ));
        mockMvc.perform(post("/api/send")
                        .header("x-api-key", "lbk_sys_a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tooLongBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("訊息長度不可超過 5000 字元（目前為 5001 字元）"));
    }

    private void insertReceivedWebhookLog(String id, String botCode) {
        WebhookEventLog log = new WebhookEventLog();
        log.setId(id);
        log.setBotCode(botCode);
        log.setEventType("follow");
        log.setSignatureValid(1);
        log.setIsRedelivery(0);
        log.setProcessStatus("RECEIVED");
        log.setCreatedAt(LocalDateTime.now());
        webhookEventLogMapper.insert(log);
    }

    private void waitUntilWebhookProcessed(String id) throws InterruptedException {
        for (int i = 0; i < 150; i++) {
            WebhookEventLog row = webhookEventLogMapper.selectById(id);
            if (row != null && "PROCESSED".equals(row.getProcessStatus())) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("等待非同步 Webhook 處理逾時: " + id);
    }

    private void insertSendLog(String id, String botCode, String templateCode, String status, LocalDateTime createdAt) {
        SendLog log = new SendLog();
        log.setId(id);
        log.setBotCode(botCode);
        log.setTemplateCode(templateCode);
        log.setStatus(status);
        log.setContent("test");
        log.setCreatedAt(createdAt);
        sendLogMapper.insert(log);
    }

    private String computeHmacBase64(byte[] body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(body));
    }
}
