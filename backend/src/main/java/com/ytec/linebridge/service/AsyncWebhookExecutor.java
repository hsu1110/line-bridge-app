package com.ytec.linebridge.service;

import com.ytec.linebridge.entity.BotAccount;
import com.ytec.linebridge.entity.WebhookEventLog;
import com.ytec.linebridge.mapper.WebhookEventLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * LINE Webhook 非同步背景處理器（處理加好友、入群、封鎖、退群事件，並以免費 Reply API 回應）
 */
@Service
public class AsyncWebhookExecutor {

    private static final Logger log = LoggerFactory.getLogger(AsyncWebhookExecutor.class);

    private final LineApiService lineApiService;
    private final ContactBindingService contactBindingService;
    private final WebhookEventLogMapper webhookEventLogMapper;

    public AsyncWebhookExecutor(
            LineApiService lineApiService,
            ContactBindingService contactBindingService,
            WebhookEventLogMapper webhookEventLogMapper) {
        this.lineApiService = lineApiService;
        this.contactBindingService = contactBindingService;
        this.webhookEventLogMapper = webhookEventLogMapper;
    }

    @Async("lineAsyncExecutor")
    @SuppressWarnings("unchecked")
    public void processEventAsync(String eventLogId, BotAccount bot, Map<String, Object> event) {
        WebhookEventLog update = new WebhookEventLog();
        update.setId(eventLogId);

        try {
            String type = asString(event.get("type"));
            String replyToken = asString(event.get("replyToken"));
            Map<String, Object> source = (Map<String, Object>) event.get("source");
            String token = bot.getChannelAccessToken();

            if ("follow".equals(type) && source != null) {
                String userId = asString(source.get("userId"));
                Map<String, Object> profile = lineApiService.getProfile(token, userId);
                String displayName = profile != null && profile.get("displayName") != null
                        ? profile.get("displayName").toString()
                        : "Unknown User";

                contactBindingService.upsertAndBind(userId, displayName, "user", bot.getId());
                lineApiService.replyTextMessage(
                        token,
                        replyToken,
                        "歡迎 " + displayName + "！我是「" + bot.getName() + "」。\n\n"
                                + "本帳號為系統自動通知專用，後續將透過此對話發送相關告警與系統通知（僅提供單向推播，不支援訊息回覆）。\n\n"
                                + "如需申請開通或調整通知項目，請與 IT 人員聯繫。"
                );
            } else if ("join".equals(type) && source != null) {
                String groupId = asString(source.get("groupId"));
                if (groupId != null) {
                    Map<String, Object> summary = lineApiService.getGroupSummary(token, groupId);
                    String groupName = summary != null && summary.get("groupName") != null
                            ? summary.get("groupName").toString()
                            : "Unknown Group";

                    contactBindingService.upsertAndBind(groupId, groupName, "group", bot.getId());
                    lineApiService.replyTextMessage(
                            token,
                            replyToken,
                            "大家好！我是「" + bot.getName() + "」。\n\n"
                                    + "本機器人將用於發送系統自動告警與通知至「" + groupName + "」（僅提供單向推播，不支援訊息回覆）。\n\n"
                                    + "如需開通或調整通知項目，請與 IT 人員聯繫。"
                    );
                }
            } else if ("unfollow".equals(type) && source != null) {
                String userId = asString(source.get("userId"));
                if (userId != null) {
                    contactBindingService.unbindByLineId(userId, bot.getId());
                }
            } else if ("leave".equals(type) && source != null) {
                String targetId = source.get("groupId") != null
                        ? asString(source.get("groupId"))
                        : asString(source.get("roomId"));
                if (targetId != null) {
                    contactBindingService.unbindByLineId(targetId, bot.getId());
                }
            }

            update.setProcessStatus("PROCESSED");
            webhookEventLogMapper.updateById(update);
        } catch (Exception e) {
            log.error("處理 LINE Webhook 事件失敗 (eventLogId={})", eventLogId, e);
            update.setProcessStatus("FAILED");
            update.setErrorMessage(e.getMessage());
            webhookEventLogMapper.updateById(update);
        }
    }

    private String asString(Object val) {
        return val != null ? val.toString() : null;
    }
}
