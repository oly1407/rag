package com.rag.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_chat_message")
public class AiChatMessage {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("session_id")
    private String sessionId;

    @TableField("role")
    private String role;

    @TableField("content")
    private String content;

    @TableField("token_input")
    private Integer tokenInput;

    @TableField("token_output")
    private Integer tokenOutput;

    @TableField("model_name")
    private String modelName;

    @TableField("latency_ms")
    private Integer latencyMs;

    @TableField("msg_order")
    private Integer msgOrder;

    @TableField("is_deleted")
    private Integer isDeleted;

    @TableField("created_at")
    private LocalDateTime createdAt;
}

