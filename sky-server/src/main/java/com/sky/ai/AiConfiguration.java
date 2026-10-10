package com.sky.ai;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.loader.ClassPathDocumentLoader;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class AiConfiguration {
    @Autowired
    private ChatMemoryStore redisChatMemoryStore;
    @Bean
    public ChatModel chatModel(AiProperties aiProperties) {
        return OpenAiChatModel.builder()
                .baseUrl(aiProperties.getBaseUrl())
                .apiKey(aiProperties.getApiKey()) // 传 yml 里的值
                .modelName(aiProperties.getModelName())
                .logRequests(true)
                .logResponses(true)
                .build();
    }
    @Bean
    public StreamingChatModel streamingChatModel(AiProperties aiProperties) {
        return OpenAiStreamingChatModel.builder()
                .baseUrl(aiProperties.getBaseUrl())
                .apiKey(aiProperties.getApiKey()) // 传 yml 里的值
                .modelName(aiProperties.getModelName())
                .logRequests(true)
                .logResponses(true)
                .build();
    }
    @Bean
    public EmbeddingModel embeddingModel(AiProperties aiProperties){
        return OpenAiEmbeddingModel.builder()
                .baseUrl(aiProperties.getBaseUrl())
                .apiKey(aiProperties.getApiKey())
                .modelName(aiProperties.getEmbeddingModelName())
                .logRequests(true)
                .logResponses(true)
                .build();
    }
    @Bean
    public ChatMemory chatMemory(){
        return MessageWindowChatMemory.builder()
                .maxMessages(20)
                .build();

    }
    @Bean
    public ChatMemoryProvider chatMemoryProvider(){
        ChatMemoryProvider chatMemoryProvider = new ChatMemoryProvider(){
            @Override
            public ChatMemory get(Object memoryId) {
                return MessageWindowChatMemory.builder()
                        .id(memoryId)
                        .maxMessages(20)
                        .chatMemoryStore(redisChatMemoryStore)
                        .build();
            }
        };
        return chatMemoryProvider;
    }
//    @Bean
//    public EmbeddingStore store(EmbeddingModel embeddingModel){
//        //1.加载文档进内存
//        List<Document> document = ClassPathDocumentLoader.loadDocuments("content");
//        //2.构建向量数据库操作对象
//        InMemoryEmbeddingStore store=new InMemoryEmbeddingStore();
//        //构建文档分割器对象
//        DocumentSplitter ds= DocumentSplitters.recursive(500,100);
//        //3。构建一个EmbeddingStoreIngester对象，完成文本数据切割，向量化，存储
//        EmbeddingStoreIngestor ingestor=EmbeddingStoreIngestor.builder()
//                .embeddingStore(store)
//                .documentSplitter(ds)
////                .embeddingModel(embeddingModel)
//                .build();
//        ingestor.ingest(document);
//        return store;
//    }
//    @Bean
//    public ContentRetriever contentRetriever(EmbeddingStore store,EmbeddingModel embeddingModel){
//        return EmbeddingStoreContentRetriever.builder()
//                .embeddingStore(store)
//                .minScore(0.5)
//                .maxResults(3)
////                .embeddingModel(embeddingModel)
//                .build();
//    }

}
