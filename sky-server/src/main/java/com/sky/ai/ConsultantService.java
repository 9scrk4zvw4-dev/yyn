package com.sky.ai;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import reactor.core.publisher.Flux;

/**
 * AI 助手（由 AiServices 动态生成实现类）
 */

public interface ConsultantService {

   @SystemMessage("你是苍穹外卖助手小苍，我能帮你解决麻烦哟！")
   String chat(@MemoryId String memoryId, @UserMessage String message);

   @SystemMessage("你是苍穹外卖助手小苍，我能帮你解决麻烦哟！")
   Flux<String> chatStream(@MemoryId String memoryId, @UserMessage String message);
}
