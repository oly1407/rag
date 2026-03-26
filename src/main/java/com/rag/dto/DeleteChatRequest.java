package com.rag.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DeleteChatRequest {
    @NotBlank(message = "sessionId不能为空")
    private String sessionId;
}

