package com.rag.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class SensitiveWordService {

    private final Set<String> sensitiveWords = Collections.synchronizedSet(new HashSet<>());

    @PostConstruct
    public void init() {
        try {
            var resource = new ClassPathResource("sensitive-words.txt");
            try (BufferedReader br = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String w = line.trim();
                    if (!w.isEmpty()) {
                        sensitiveWords.add(w);
                    }
                }
            }
        } catch (Exception e) {
            // 容错：找不到敏感词文件时，系统仍可运行
        }
    }

    public String mask(String text) {
        if (text == null || text.isEmpty() || sensitiveWords.isEmpty()) return text;
        String out = text;
        for (String w : sensitiveWords) {
            String escaped = Pattern.quote(w);
            out = out.replaceAll(escaped, "***");
        }
        return out;
    }
}

