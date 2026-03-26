package com.rag.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI 默认通常提供 ChatClient.Builder，而不是直接提供 ChatClient Bean。
 * 这里显式构建一个 ChatClient，供业务层注入使用。
 */
@Configuration
public class AiChatClientConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}

