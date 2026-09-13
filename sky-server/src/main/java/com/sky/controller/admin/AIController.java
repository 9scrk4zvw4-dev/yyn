package com.sky.controller.admin;

import com.sky.dto.ChatRequestDTO;
import com.sky.result.Result;
import com.sky.service.AIAssistantService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI管家控制器
 */
@RestController
@RequestMapping("/admin/ai")
@Slf4j
@Api(tags = "AI管家相关接口")
public class AIController {

    @Autowired
    private AIAssistantService aiAssistantService;

    /**
     * AI管家对话
     */
    @PostMapping("/chat")
    @ApiOperation("AI管家对话")
    public Result<String> chat(@RequestParam String sessionId,
                               @RequestBody ChatRequestDTO request) {
        String userMessage = request.getMessage();
        log.info("AI管家对话: {}", userMessage);
        String reply = aiAssistantService.chat(sessionId, userMessage);
        return Result.success(reply);
    }
}
