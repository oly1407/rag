package com.rag.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChatSendRequest {

    private String sessionId;

    private String userNo;

    @NotBlank(message = "message不能为空")
    private String message;
}

