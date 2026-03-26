package com.rag.dto;

import lombok.Data;

@Data
public class ChatSendResponse {
    private String sessionId;
    private String reply;
    private Integer roundCount;
}

