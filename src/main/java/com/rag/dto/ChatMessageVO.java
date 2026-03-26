package com.rag.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatMessageVO {
    private Integer msgOrder;
    private String role;
    private String content;
    private LocalDateTime createdAt;
}

