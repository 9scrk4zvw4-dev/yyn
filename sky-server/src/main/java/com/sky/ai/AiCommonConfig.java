package com.sky.ai;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiCommonConfig {
    @Autowired
    private ChatMemoryProvider chatMemoryProvider;
    @Autowired
    private AiTools aiTools;
//    @Autowired
//    private ContentRetriever contentRetriever;

    @Bean
    public ConsultantService consultantService(ChatModel chatModel,StreamingChatModel streamingChatModel) {
        ConsultantService consultantService= AiServices.builder(ConsultantService.class)
                .chatModel(chatModel)
                .streamingChatModel(streamingChatModel)
                .chatMemoryProvider(chatMemoryProvider)
//                .contentRetriever(contentRetriever)
                .tools(aiTools)
                .build();
        return consultantService;
    }

}
