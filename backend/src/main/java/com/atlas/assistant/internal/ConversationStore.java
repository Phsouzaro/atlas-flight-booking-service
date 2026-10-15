package com.atlas.assistant.internal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Conversas e mensagens do assistente. Toda leitura filtra pelo dono. */
@Component
class ConversationStore {

    static final String USER = "USER";
    static final String ASSISTANT = "ASSISTANT";

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    ConversationStore(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    record StoredReply(UUID id, String content, String promptVersion, List<String> toolCalls,
                       List<UUID> pendingActionIds, Attachments attachments, int promptTokens, int completionTokens) {
    }

    @Transactional
    ConversationView create(UUID userId, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO assistant_conversations (id, user_id, created_at, updated_at) VALUES (?, ?, ?, ?)",
                id, userId, utc(now), utc(now));
        return new ConversationView(id, now, now);
    }

    List<ConversationView> list(UUID userId) {
        return jdbc.query("SELECT id, created_at, updated_at FROM assistant_conversations WHERE user_id = ? "
                        + "ORDER BY updated_at DESC",
                (rs, i) -> new ConversationView(rs.getObject(1, UUID.class), instant(rs, 2), instant(rs, 3)), userId);
    }

    boolean isOwner(UUID conversationId, UUID userId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM assistant_conversations WHERE id = ? AND user_id = ?)",
                Boolean.class, conversationId, userId));
    }

    Optional<UUID> findUserMessage(UUID conversationId, String idempotencyKey) {
        return jdbc.queryForList("SELECT id FROM assistant_messages WHERE conversation_id = ? AND idempotency_key = ?",
                UUID.class, conversationId, idempotencyKey).stream().findFirst();
    }

    Optional<StoredReply> findReply(UUID userMessageId) {
        return jdbc.query("SELECT id, content, prompt_version, tool_calls::text, pending_action_ids::text, "
                        + "attachments::text, coalesce(prompt_tokens, 0), coalesce(completion_tokens, 0) "
                        + "FROM assistant_messages WHERE reply_to_id = ?",
                (rs, i) -> new StoredReply(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        read(rs.getString(4), new TypeReference<List<String>>() {
                        }), read(rs.getString(5), new TypeReference<List<UUID>>() {
                        }), attachments(rs.getString(6)), rs.getInt(7), rs.getInt(8)),
                userMessageId).stream().findFirst();
    }

    /** @throws org.springframework.dao.DuplicateKeyException se a chave já foi usada na conversa */
    @Transactional
    UUID insertUserMessage(UUID conversationId, String content, String idempotencyKey, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO assistant_messages (id, conversation_id, role, content, created_at, idempotency_key) "
                + "VALUES (?, ?, 'USER', ?, ?, ?)", id, conversationId, content, utc(now), idempotencyKey);
        return id;
    }

    @Transactional
    void deleteMessage(UUID id) {
        jdbc.update("DELETE FROM assistant_messages WHERE id = ?", id);
    }

    @Transactional
    UUID insertReply(UUID conversationId, UUID replyToId, String content, String promptVersion, List<String> toolCalls,
                     List<UUID> pendingActionIds, Attachments attachments, String toolContext, TokenUsage usage,
                     Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO assistant_messages (id, conversation_id, role, content, created_at, reply_to_id, "
                        + "prompt_version, tool_calls, pending_action_ids, attachments, tool_context, prompt_tokens, "
                        + "completion_tokens) VALUES (?, ?, 'ASSISTANT', ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?, ?)",
                id, conversationId, content, utc(now), replyToId, promptVersion, write(toolCalls), write(pendingActionIds),
                write(attachments), toolContext, usage.promptTokens(), usage.completionTokens());
        jdbc.update("UPDATE assistant_conversations SET updated_at = ? WHERE id = ?", utc(now), conversationId);
        return id;
    }

    /** As últimas {@code limit} mensagens antes de {@code beforeMessageId}, da mais antiga para a mais nova. */
    List<MessageView> history(UUID conversationId, UUID beforeMessageId, int limit) {
        List<MessageView> recent = jdbc.query("SELECT " + COLUMNS + " FROM assistant_messages WHERE conversation_id = ? "
                        + "AND seq < (SELECT seq FROM assistant_messages WHERE id = ?) ORDER BY seq DESC LIMIT ?",
                this::message, conversationId, beforeMessageId, limit);
        return recent.reversed();
    }

    List<MessageView> messages(UUID conversationId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM assistant_messages WHERE conversation_id = ? ORDER BY seq",
                this::message, conversationId);
    }

    private static final String COLUMNS =
            "id, role, content, created_at, tool_calls::text, pending_action_ids::text, attachments::text, tool_context";

    private MessageView message(ResultSet rs, int row) throws SQLException {
        return new MessageView(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), instant(rs, 4),
                read(rs.getString(5), new TypeReference<List<String>>() {
                }), read(rs.getString(6), new TypeReference<List<UUID>>() {
                }), attachments(rs.getString(7)), rs.getString(8));
    }

    private Attachments attachments(String value) {
        if (value == null) {
            return Attachments.EMPTY;
        }
        try {
            return json.readValue(value, Attachments.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Corrupt assistant message attachments", e);
        }
    }

    private <T> List<T> read(String value, TypeReference<List<T>> type) {
        if (value == null) {
            return List.of();
        }
        try {
            return json.readValue(value, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Corrupt assistant message metadata", e);
        }
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet rs, int column) throws SQLException {
        return rs.getObject(column, OffsetDateTime.class).toInstant();
    }
}
