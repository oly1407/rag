package com.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.rag.dto.ChatHistoryResponse;
import com.rag.dto.ChatMessageVO;
import com.rag.dto.ChatSessionListResponse;
import com.rag.dto.ChatSessionVO;
import com.rag.dto.ChatSendRequest;
import com.rag.dto.ChatSendResponse;
import com.rag.mapper.AiChatMessageMapper;
import com.rag.mapper.AiChatSessionMapper;
import com.rag.model.AiChatMessage;
import com.rag.model.AiChatSession;
import com.rag.model.AiCourse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import org.springframework.beans.factory.annotation.Value;

@Service
public class ChatService {

    private final AiChatSessionMapper sessionMapper;
    private final AiChatMessageMapper messageMapper;
    private final SensitiveWordService sensitiveWordService;
    private final CourseService courseService;
    private final ChatClient chatClient;

    @Value("${app.chat.max-history-messages:60}")
    private int maxHistoryMessages;

    public ChatService(AiChatSessionMapper sessionMapper,
                        AiChatMessageMapper messageMapper,
                        SensitiveWordService sensitiveWordService,
                        CourseService courseService,
                        ChatClient chatClient) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.sensitiveWordService = sensitiveWordService;
        this.courseService = courseService;
        this.chatClient = chatClient;
    }

    @Transactional
    public ChatSendResponse send(ChatSendRequest request) {
        String userNo = (request.getUserNo() == null || request.getUserNo().isBlank()) ? "anonymous" : request.getUserNo().trim();
        String userMessageRaw = request.getMessage();
        String userMessage = sensitiveWordService.mask(userMessageRaw);

        AiChatSession session = resolveOrCreateSession(request.getSessionId(), userNo);

        int nextOrder = nextMsgOrder(session.getSessionId());
        LocalDateTime now = LocalDateTime.now();

        // 1) 写入用户消息
        AiChatMessage userMsg = new AiChatMessage();
        userMsg.setSessionId(session.getSessionId());
        userMsg.setRole("user");
        userMsg.setContent(userMessage);
        userMsg.setMsgOrder(nextOrder);
        userMsg.setIsDeleted(0);
        userMsg.setCreatedAt(now);
        messageMapper.insert(userMsg);

        // 首次提问时，用第一条问题生成会话标题（最多8字，超长省略号）
        maybeInitSessionTitle(session, userMessage);

        // 2) 取历史上下文（不包含当前用户消息）
        int historyLimit = Math.max(0, maxHistoryMessages - 1);
        List<AiChatMessage> history = messageMapper.selectList(new LambdaQueryWrapper<AiChatMessage>()
                .eq(AiChatMessage::getSessionId, session.getSessionId())
                .eq(AiChatMessage::getIsDeleted, 0)
                .lt(AiChatMessage::getMsgOrder, nextOrder)
                .orderByDesc(AiChatMessage::getMsgOrder)
                .last("limit " + historyLimit));
        Collections.reverse(history);

        // 3) 课程推荐（从课程表里做关键词匹配）
        List<AiCourse> courses = courseService.recommendCourses(userMessage, 3);

        String systemPrompt = buildSystemPrompt(courses);
        String userPrompt = buildTranscript(history, userMessage);

        // 4) 调用大模型
        long startNs = System.nanoTime();
        String replyMasked = null;
        try {
            String reply = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();

            replyMasked = sensitiveWordService.mask(reply);
        } catch (Exception e) {
            // 模型调用失败时给出明确错误，避免用户拿不到原因
            throw new RuntimeException("模型调用失败：" + e.getMessage(), e);
        }
        long latencyMs = (System.nanoTime() - startNs) / 1_000_000L;

        // 5) 写入助手消息
        AiChatMessage assistantMsg = new AiChatMessage();
        assistantMsg.setSessionId(session.getSessionId());
        assistantMsg.setRole("assistant");
        assistantMsg.setContent(replyMasked);
        assistantMsg.setMsgOrder(nextOrder + 1);
        assistantMsg.setIsDeleted(0);
        assistantMsg.setLatencyMs((int) Math.min(Integer.MAX_VALUE, latencyMs));
        assistantMsg.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(assistantMsg);

        // 6) 更新会话
        int newRoundCount = (session.getRoundCount() == null ? 0 : session.getRoundCount()) + 1;
        session.setRoundCount(newRoundCount);
        session.setLastMessageAt(LocalDateTime.now());
        sessionMapper.updateById(session);

        ChatSendResponse resp = new ChatSendResponse();
        resp.setSessionId(session.getSessionId());
        resp.setReply(replyMasked);
        resp.setRoundCount(newRoundCount);
        return resp;
    }

    /**
     * 流式输出：逐步产出 assistant 的增量文本（delta）。
     * 注意：为了保证“入库=最终完整回答”，这里在流结束时才落库 assistant 消息并 round+1。
     */
    public Flux<String> stream(ChatSendRequest request) {
        String userNo = (request.getUserNo() == null || request.getUserNo().isBlank()) ? "anonymous" : request.getUserNo().trim();
        String userMessageRaw = request.getMessage();
        String userMessage = sensitiveWordService.mask(userMessageRaw);

        AiChatSession session = resolveOrCreateSession(request.getSessionId(), userNo);

        int nextOrder = nextMsgOrder(session.getSessionId());
        LocalDateTime now = LocalDateTime.now();

        // 1) 先写入用户消息
        AiChatMessage userMsg = new AiChatMessage();
        userMsg.setSessionId(session.getSessionId());
        userMsg.setRole("user");
        userMsg.setContent(userMessage);
        userMsg.setMsgOrder(nextOrder);
        userMsg.setIsDeleted(0);
        userMsg.setCreatedAt(now);
        messageMapper.insert(userMsg);

        // 首次提问时初始化标题
        maybeInitSessionTitle(session, userMessage);

        // 2) 取历史上下文（不包含当前用户消息）
        int historyLimit = Math.max(0, maxHistoryMessages - 1);
        List<AiChatMessage> history = messageMapper.selectList(new LambdaQueryWrapper<AiChatMessage>()
                .eq(AiChatMessage::getSessionId, session.getSessionId())
                .eq(AiChatMessage::getIsDeleted, 0)
                .lt(AiChatMessage::getMsgOrder, nextOrder)
                .orderByDesc(AiChatMessage::getMsgOrder)
                .last("limit " + historyLimit));
        Collections.reverse(history);

        // 3) 课程推荐
        List<AiCourse> courses = courseService.recommendCourses(userMessage, 3);

        String systemPrompt = buildSystemPrompt(courses);
        String userPrompt = buildTranscript(history, userMessage);

        StringBuilder buffer = new StringBuilder(2048);
        long startNs = System.nanoTime();

        // 4) 流式调用
        Flux<String> flux = chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .stream()
                .content()
                .map(sensitiveWordService::mask)
                .doOnNext(delta -> buffer.append(delta == null ? "" : delta))
                .doOnError(e -> {
                    // 出错不写 assistant 入库；用户消息已入库，便于排查/重试
                })
                .doOnComplete(() -> {
                    // 5) 流结束后落库 assistant + 更新会话
                    String fullReply = buffer.toString();
                    if (fullReply.isBlank()) return;

                    long latencyMs = (System.nanoTime() - startNs) / 1_000_000L;

                    AiChatMessage assistantMsg = new AiChatMessage();
                    assistantMsg.setSessionId(session.getSessionId());
                    assistantMsg.setRole("assistant");
                    assistantMsg.setContent(fullReply);
                    assistantMsg.setMsgOrder(nextOrder + 1);
                    assistantMsg.setIsDeleted(0);
                    assistantMsg.setLatencyMs((int) Math.min(Integer.MAX_VALUE, latencyMs));
                    assistantMsg.setCreatedAt(LocalDateTime.now());
                    messageMapper.insert(assistantMsg);

                    int newRoundCount = (session.getRoundCount() == null ? 0 : session.getRoundCount()) + 1;
                    session.setRoundCount(newRoundCount);
                    session.setLastMessageAt(LocalDateTime.now());
                    sessionMapper.updateById(session);
                });

        // 先发一条 meta，告诉前端当前 sessionId（用于保存/继续会话）
        return Flux.concat(Flux.just("__META__:" + session.getSessionId()), flux);
    }

    @Transactional
    public boolean deleteSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return false;

        AiChatSession session = sessionMapper.selectOne(
                Wrappers.<AiChatSession>lambdaQuery().eq(AiChatSession::getSessionId, sessionId)
        );
        if (session == null) return false;

        messageMapper.update(Wrappers.<AiChatMessage>lambdaUpdate()
                .set(AiChatMessage::getIsDeleted, 1)
                .eq(AiChatMessage::getSessionId, sessionId));

        session.setStatus(2);
        sessionMapper.updateById(session);
        return true;
    }

    public byte[] exportExcel(String sessionId) {
        List<AiChatMessage> list = messageMapper.selectList(
                Wrappers.<AiChatMessage>lambdaQuery()
                        .eq(AiChatMessage::getSessionId, sessionId)
                        .eq(AiChatMessage::getIsDeleted, 0)
                        .orderByAsc(AiChatMessage::getMsgOrder)
        );

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("chat");
            int rowIndex = 0;

            Row header = sheet.createRow(rowIndex++);
            createCell(header, 0, "msg_order");
            createCell(header, 1, "role");
            createCell(header, 2, "content");
            createCell(header, 3, "created_at");

            for (AiChatMessage m : list) {
                Row row = sheet.createRow(rowIndex++);
                createCell(row, 0, m.getMsgOrder());
                createCell(row, 1, m.getRole());
                createCell(row, 2, m.getContent());
                createCell(row, 3, m.getCreatedAt() == null ? "" : m.getCreatedAt().toString());
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("导出Excel失败：" + e.getMessage(), e);
        }
    }

    public ChatHistoryResponse history(String sessionId) {
        AiChatSession session = sessionMapper.selectOne(
                Wrappers.<AiChatSession>lambdaQuery().eq(AiChatSession::getSessionId, sessionId)
        );
        if (session == null) {
            ChatHistoryResponse resp = new ChatHistoryResponse();
            resp.setSessionId(sessionId);
            resp.setMessages(List.of());
            resp.setRoundCount(0);
            return resp;
        }

        List<AiChatMessage> list = messageMapper.selectList(
                Wrappers.<AiChatMessage>lambdaQuery()
                        .eq(AiChatMessage::getSessionId, sessionId)
                        .eq(AiChatMessage::getIsDeleted, 0)
                        .orderByAsc(AiChatMessage::getMsgOrder)
        );

        List<ChatMessageVO> messages = new ArrayList<>();
        for (AiChatMessage m : list) {
            ChatMessageVO vo = new ChatMessageVO();
            vo.setMsgOrder(m.getMsgOrder());
            vo.setRole(m.getRole());
            vo.setContent(m.getContent());
            vo.setCreatedAt(m.getCreatedAt());
            messages.add(vo);
        }

        ChatHistoryResponse resp = new ChatHistoryResponse();
        resp.setSessionId(sessionId);
        resp.setRoundCount(session.getRoundCount());
        resp.setMessages(messages);
        return resp;
    }

    public ChatSessionListResponse listSessions(String userNo, int limit) {
        String u = (userNo == null || userNo.isBlank()) ? "anonymous" : userNo.trim();
        int lim = Math.max(1, Math.min(limit, 200));

        List<AiChatSession> list = sessionMapper.selectList(
                Wrappers.<AiChatSession>lambdaQuery()
                        .eq(AiChatSession::getUserNo, u)
                        .orderByDesc(AiChatSession::getLastMessageAt)
                        .last("limit " + lim)
        );

        List<ChatSessionVO> vos = new ArrayList<>();
        if (list != null) {
            for (AiChatSession s : list) {
                ChatSessionVO vo = new ChatSessionVO();
                vo.setSessionId(s.getSessionId());
                vo.setTitle(s.getTitle());
                vo.setStatus(s.getStatus());
                vo.setRoundCount(s.getRoundCount());
                vo.setLastMessageAt(s.getLastMessageAt());
                vos.add(vo);
            }
        }

        ChatSessionListResponse resp = new ChatSessionListResponse();
        resp.setUserNo(u);
        resp.setSessions(vos);
        return resp;
    }

    @Transactional
    public String createSession(String userNo) {
        String u = (userNo == null || userNo.isBlank()) ? "anonymous" : userNo.trim();
        AiChatSession s = resolveOrCreateSession(null, u);
        return s.getSessionId();
    }

    private AiChatSession resolveOrCreateSession(String sessionId, String userNo) {
        if (sessionId != null && !sessionId.isBlank()) {
            AiChatSession s = sessionMapper.selectOne(
                    Wrappers.<AiChatSession>lambdaQuery()
                            .eq(AiChatSession::getSessionId, sessionId)
                            .eq(AiChatSession::getStatus, 1)
            );
            if (s != null) return s;
        }

        String newSessionId = UUID.randomUUID().toString().replace("-", "");
        AiChatSession s = new AiChatSession();
        s.setSessionId(newSessionId);
        s.setUserNo(userNo);
        s.setStatus(1);
        s.setRoundCount(0);
        s.setLastMessageAt(LocalDateTime.now());
        sessionMapper.insert(s);
        return s;
    }

    private void maybeInitSessionTitle(AiChatSession session, String firstQuestion) {
        if (session == null) return;
        String current = session.getTitle();
        if (current != null && !current.isBlank()) return;

        // 只有在“第一条用户消息”时才写标题（msg_order==1）
        Long count = messageMapper.selectCount(Wrappers.<AiChatMessage>lambdaQuery()
                .eq(AiChatMessage::getSessionId, session.getSessionId())
                .eq(AiChatMessage::getIsDeleted, 0));
        if (count == null || count != 1L) return;

        String title = buildTitle(firstQuestion, 8);
        session.setTitle(title);
        sessionMapper.updateById(session);
    }

    private String buildTitle(String text, int maxChars) {
        if (text == null) return null;
        String t = text.trim().replaceAll("\\s+", " ");
        if (t.isEmpty()) return null;

        int[] cps = t.codePoints().toArray();
        if (cps.length <= maxChars) return t;

        String prefix = new String(cps, 0, maxChars);
        return prefix + "…";
    }

    private int nextMsgOrder(String sessionId) {
        AiChatMessage last = messageMapper.selectOne(
                Wrappers.<AiChatMessage>lambdaQuery()
                        .eq(AiChatMessage::getSessionId, sessionId)
                        .eq(AiChatMessage::getIsDeleted, 0)
                        .orderByDesc(AiChatMessage::getMsgOrder)
                        .last("limit 1")
        );
        Integer lastOrder = last == null ? 0 : last.getMsgOrder();
        return lastOrder + 1;
    }

    private String buildSystemPrompt(List<AiCourse> courses) {
        StringBuilder coursePart = new StringBuilder();
        if (courses != null && !courses.isEmpty()) {
            coursePart.append("可推荐课程（从下列列表中选择，结合用户问题给出学习建议与理由）：\n");
            for (AiCourse c : courses) {
                coursePart.append("- ").append(c.getCourseName())
                        .append("（标签：").append(c.getLevelTag() == null ? "" : c.getLevelTag()).append("，类别：")
                        .append(c.getCategory() == null ? "" : c.getCategory()).append("）")
                        .append(" 链接：").append(c.getUrl() == null ? "" : c.getUrl())
                        .append("\n");
            }
        } else {
            coursePart.append("当前暂无匹配的课程数据。若用户想学习相关主题，请给出学习路线但说明“课程链接暂缺”。\n");
        }

        return """
                你是一位 Java 资深大师与学习教练。你的目标是：用清晰、可执行的方式帮助人们解决问题，并在合适的时候推荐学习课程与路线。
                
                【回答风格】
                - 先给结论/方案，再解释原因与步骤。
                - 分层给出：最快可用方案、长期更优方案（若用户需要）。
                - 当信息不足时，先提出 1-3 个关键澄清问题，再继续建议。
                
                【课程推荐】
                - 当用户明确表示“想学/需要课程/想系统掌握”或问题暴露出明显知识短板时，使用“可推荐课程”部分给出 1-3 个课程建议。
                - 对每个课程：说明适合人群/前置、为什么推荐、建议学习顺序（简短即可）。
                - 不得虚构课程链接或课程信息；“可推荐课程”里没有的数据就诚实说明暂缺。
                
                【对话约束】
                - 你会收到一段对话记录（含 User/Assistant）。你只需要基于最后一个用户问题给出下一步回答。
                - 你的回答要自然、连贯，避免机械复述整段记录。
                
                【安全】
                - 如果用户提出敏感/违法/危险内容，拒绝并给出合规替代建议。
                
                %s
                """.formatted(coursePart.toString());
    }

    private String buildTranscript(List<AiChatMessage> history, String currentUserMessage) {
        StringBuilder sb = new StringBuilder();
        sb.append("对话历史（按顺序）：\n");
        if (history != null) {
            for (AiChatMessage m : history) {
                if (m.getRole() == null) continue;
                if ("user".equalsIgnoreCase(m.getRole())) {
                    sb.append("User: ").append(m.getContent()).append("\n");
                } else if ("assistant".equalsIgnoreCase(m.getRole())) {
                    sb.append("Assistant: ").append(m.getContent()).append("\n");
                } else {
                    sb.append(m.getRole()).append(": ").append(m.getContent()).append("\n");
                }
            }
        }
        sb.append("User: ").append(currentUserMessage).append("\n");
        sb.append("请基于以上内容给出你的下一条回复。");
        return sb.toString();
    }

    private void createCell(Row row, int col, String value) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value == null ? "" : value);
    }

    private void createCell(Row row, int col, Integer value) {
        Cell cell = row.createCell(col);
        if (value == null) cell.setCellValue(0);
        else cell.setCellValue(value);
    }
}

