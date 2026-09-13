package com.sky.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * AI管家对话请求DTO
 */
@Data
public class ChatRequestDTO implements Serializable {

    /**
     * 用户输入的消息
     */
    private String message;
}
