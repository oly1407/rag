package com.rag.dto;

import lombok.Data;

import java.util.List;

@Data
public class ChatHistoryResponse {
    private String sessionId;
    private Integer roundCount;
    private List<ChatMessageVO> messages;
}

