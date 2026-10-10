package com.sky.controller.admin;

import com.sky.ai.ConsultantService;
import com.sky.dto.ChatRequestDTO;
import com.sky.result.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 管家
 */
@RestController
@RequestMapping("/admin/ai")
public class AibutlerController {

    @Autowired
    private ConsultantService consultantService;

    /**
     * 与 AI 管家对话（完整文本返回）
     * 前端：POST /admin/ai/chat?sessionId=xxx，body {"message":"..."}
     */
    @PostMapping("/chat")
    public Result<String> chat(@RequestParam("sessionId") String sessionId,
                               @RequestBody ChatRequestDTO request) {
        String reply = consultantService.chat(sessionId, request.getMessage());
        return Result.success(reply);
    }
}
