package com.flightsai.assistant.internal;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.flightsai.assistant.internal.PiiDetector.Finding;
import com.flightsai.assistant.internal.PiiDetector.Kind;

/**
 * Troca PII por tokens antes de o texto sair para o modelo ou para a memória, e guarda o valor real por
 * conversa. O mesmo valor na mesma conversa recebe sempre o mesmo token.
 * <p>
 * Cartão vira {@value #CARD_REMOVED} e não é guardado: nenhuma tool precisa dele.
 */
@Component
class PiiVault {

    static final String CARD_REMOVED = "[CARTAO_REMOVIDO]";
    private static final Pattern TOKEN = Pattern.compile("\\[(DOC|EMAIL|TEL)_\\d+\\]");

    private final JdbcTemplate jdbc;
    private final Clock clock;

    PiiVault(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    String tokenize(UUID conversationId, String text) {
        List<Finding> findings = PiiDetector.find(text);
        if (findings.isEmpty()) {
            return text;
        }
        StringBuilder out = new StringBuilder();
        int last = 0;
        for (Finding f : findings) {
            out.append(text, last, f.start());
            out.append(f.kind() == Kind.CARD ? CARD_REMOVED : tokenFor(conversationId, f));
            last = f.end();
        }
        out.append(text.substring(last));
        return out.toString();
    }

    /** Troca os tokens conhecidos da conversa pelo valor real. Token desconhecido fica como está. */
    @Transactional(readOnly = true)
    String detokenize(UUID conversationId, String text) {
        if (text == null || !TOKEN.matcher(text).find()) {
            return text;
        }
        Map<String, String> values = jdbc.query("SELECT token, value FROM assistant_pii_tokens WHERE conversation_id = ?",
                        (rs, i) -> Map.entry(rs.getString(1), rs.getString(2)), conversationId)
                .stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        Matcher m = TOKEN.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(out, Matcher.quoteReplacement(values.getOrDefault(m.group(), m.group())));
        }
        m.appendTail(out);
        return out.toString();
    }

    private String tokenFor(UUID conversationId, Finding finding) {
        String value = finding.kind() == Kind.DOCUMENT ? finding.value().toUpperCase() : finding.value();
        List<String> existing = jdbc.queryForList(
                "SELECT token FROM assistant_pii_tokens WHERE conversation_id = ? AND value = ?",
                String.class, conversationId, value);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM assistant_pii_tokens WHERE conversation_id = ? AND token LIKE ?",
                Integer.class, conversationId, "[" + finding.kind().tokenPrefix + "_%");
        String token = "[" + finding.kind().tokenPrefix + "_" + (count + 1) + "]";
        jdbc.update("INSERT INTO assistant_pii_tokens (conversation_id, token, value, created_at) VALUES (?, ?, ?, ?)",
                conversationId, token, value, clock.instant().atOffset(ZoneOffset.UTC));
        return token;
    }
}
