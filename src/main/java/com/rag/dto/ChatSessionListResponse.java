package com.rag.dto;

import lombok.Data;

import java.util.List;

@Data
public class ChatSessionListResponse {
    private String userNo;
    private List<ChatSessionVO> sessions;
}

