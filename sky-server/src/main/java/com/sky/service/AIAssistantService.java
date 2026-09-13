package com.sky.service;

public interface AIAssistantService {

    /**
     * AI管家对话
     *
     * @param sessionId   会话ID（用于多轮对话记忆）
     * @param userMessage 用户输入
     * @return AI回复
     */
    String chat(String sessionId, String userMessage);
}
