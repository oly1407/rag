package com.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rag.model.AiChatMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiChatMessageMapper extends BaseMapper<AiChatMessage> {
}

