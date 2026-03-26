package com.rag.controller;

import com.rag.dto.ChatHistoryResponse;
import com.rag.dto.ChatSessionListResponse;
import com.rag.dto.ChatSendRequest;
import com.rag.dto.ChatSendResponse;
import com.rag.dto.DeleteChatRequest;
import com.rag.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/send")
    public ChatSendResponse send(@Valid @RequestBody ChatSendRequest request) {
        return chatService.send(request);
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(@Valid @RequestBody ChatSendRequest request) {
        return chatService.stream(request)
                .map(data -> ServerSentEvent.builder(data).build());
    }

    @PostMapping("/delete")
    public ResponseEntity<?> delete(@Valid @RequestBody DeleteChatRequest request) {
        boolean ok = chatService.deleteSession(request.getSessionId());
        if (!ok) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().body(java.util.Map.of("ok", true));
    }

    @GetMapping("/history")
    public ChatHistoryResponse history(@RequestParam("sessionId") String sessionId) {
        return chatService.history(sessionId);
    }

    @GetMapping("/sessions")
    public ChatSessionListResponse sessions(@RequestParam(value = "userNo", required = false) String userNo,
                                            @RequestParam(value = "limit", required = false, defaultValue = "50") int limit) {
        return chatService.listSessions(userNo, limit);
    }

    @PostMapping("/create")
    public ResponseEntity<?> create(@RequestParam(value = "userNo", required = false) String userNo) {
        String sessionId = chatService.createSession(userNo);
        return ResponseEntity.ok().body(java.util.Map.of("sessionId", sessionId));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam("sessionId") String sessionId) {
        byte[] bytes = chatService.exportExcel(sessionId);
        String fileName = "chat_" + sessionId + ".xlsx";
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded);
        return ResponseEntity.ok().headers(headers).body(bytes);
    }
}

