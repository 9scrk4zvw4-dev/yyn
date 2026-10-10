package com.sky.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * AI 管家对话请求
 */
@Data
public class ChatRequestDTO implements Serializable {

    // 用户提问内容
    private String message;
}
