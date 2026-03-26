package com.rag.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatSessionVO {
    private String sessionId;
    private String title;
    private Integer status;
    private Integer roundCount;
    private LocalDateTime lastMessageAt;
}

