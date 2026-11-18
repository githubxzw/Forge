package com.forge.setting;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 各通知渠道的消息体格式（P1-2：新增 Slack，钉钉/企微/飞书格式不变） */
class NotifyPayloadTest {

    @Test
    void dingtalkAndWecomUseTextMsgtype() {
        for (String type : new String[]{"dingtalk", "wecom"}) {
            String p = NotifyService.payload(type, "标题", "内容行");
            assertTrue(p.startsWith("{\"msgtype\":\"text\",\"text\":{\"content\":"), type + ": " + p);
            assertTrue(p.contains("标题\\n内容行"), type);
        }
    }

    @Test
    void feishuUsesMsgTypeText() {
        String p = NotifyService.payload("feishu", "t", "m");
        assertTrue(p.startsWith("{\"msg_type\":\"text\",\"content\":{\"text\":"), p);
    }

    @Test
    void slackUsesTextField() {
        String p = NotifyService.payload("slack", "Forge 构建失败", "项目：demo");
        assertTrue(p.startsWith("{\"text\":"), p);
        assertTrue(p.contains("Forge 构建失败\\n项目：demo"), p);
    }

    @Test
    void unknownFallsBackToRawJson() {
        String p = NotifyService.payload("raw", "t", "m");
        assertTrue(p.contains("\"title\":\"t\"") && p.contains("\"message\":\"m\""), p);
        // 中文与换行走 JSON 转义，不产生裸换行破坏 JSON 结构
        assertTrue(!p.contains("\n"), "payload 内不允许出现裸换行");
    }
}
