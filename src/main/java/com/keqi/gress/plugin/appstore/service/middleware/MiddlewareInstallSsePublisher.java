package com.keqi.gress.plugin.appstore.service.middleware;

import  com.keqi.gress.common.event.Event;
import  com.keqi.gress.common.event.EventDispatcher;
import  com.keqi.gress.common.plugin.annotion.Inject;
import  com.keqi.gress.common.plugin.annotion.Service;
import  com.keqi.gress.common.sse.SseMessage;
import  com.keqi.gress.common.sse.SseMessageEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * 中间件安装 SSE 消息发布器
 *
 * <p>通过全局 EventDispatcher 派发 {@link SseMessageEvent}，由主应用统一转发到 SSE 客户端。
 */
@Service
public class MiddlewareInstallSsePublisher {

    @Inject(source = Inject.BeanSource.SPRING)
    private EventDispatcher<Event<?>> eventDispatcher;

    private static final String BUSINESS_TYPE = "MIDDLEWARE_INSTALL";

    public void sendStart(String clientId, String pluginId, String message) {
        SseMessage msg = baseBuilder(clientId, pluginId)
                .type(SseMessage.MessageType.STATUS)
                .status("START")
                .message(message != null ? message : "开始安装中间件")
                .build();
        dispatchSse(eventDispatcher, msg);
    }

    public void sendLog(String clientId, String pluginId, String line) {
        if (line == null || line.isEmpty()) {
            return;
        }
        Map<String, Object> data = new HashMap<>();
        data.put("line", line);

        SseMessage msg = baseBuilder(clientId, pluginId)
                .type(SseMessage.MessageType.DATA)
                .status("RUNNING")
                .data(data)
                .build();
        dispatchSse(eventDispatcher, msg);
    }

    public void sendSuccess(String clientId, String pluginId, String message) {
        SseMessage msg = baseBuilder(clientId, pluginId)
                .type(SseMessage.MessageType.STATUS)
                .status("SUCCESS")
                .message(message != null ? message : "中间件安装成功")
                .build();
        dispatchSse(eventDispatcher, msg);
    }

    public void sendError(String clientId, String pluginId, String error) {
        SseMessage msg = baseBuilder(clientId, pluginId)
                .type(SseMessage.MessageType.ERROR)
                .status("FAILED")
                .message(error)
                .error(error)
                .build();
        dispatchSse(eventDispatcher, msg);
    }

    public void sendComplete(String clientId, String pluginId) {
        SseMessage msg = baseBuilder(clientId, pluginId)
                .type(SseMessage.MessageType.COMPLETE)
                .status("COMPLETE")
                .message("中间件安装流程结束")
                .build();
        dispatchSse(eventDispatcher, msg);
    }

    /**
     * 发送步骤进度信息
     * 
     * @param clientId 客户端ID
     * @param pluginId 插件ID
     * @param currentStep 当前步骤序号（从1开始）
     * @param totalSteps 总步骤数
     * @param stepName 步骤名称
     */
    public void sendStepProgress(String clientId, String pluginId, int currentStep, int totalSteps, String stepName) {
        SseMessage msg = baseBuilder(clientId, pluginId)
                .type(SseMessage.MessageType.PROGRESS)
                .status("RUNNING")
                .progress(currentStep, totalSteps, stepName != null ? stepName : "执行步骤")
                .message(String.format("步骤 %d/%d: %s", currentStep, totalSteps, stepName != null ? stepName : ""))
                .build();
        dispatchSse(eventDispatcher, msg);
    }

    private SseMessage.Builder baseBuilder(String clientId, String pluginId) {
        Map<String, Object> metadata = new HashMap<>();
        if (clientId != null) {
            metadata.put("clientId", clientId);
        }
        return SseMessage.builder()
                .businessType(BUSINESS_TYPE)
                .businessId(pluginId)
                .metadata(metadata);
    }

    /**
     * 通过 EventDispatcher 派发 SseMessageEvent
     */
    private static void dispatchSse(EventDispatcher<Event<?>> dispatcher, SseMessage message) {
        if (dispatcher == null || message == null) {
            return;
        }
        dispatcher.dispatch(SseMessageEvent.builder()
                .type("sse.message")
                .data(message)
                .metadata(message.getMetadata())
                .build());
    }
}

