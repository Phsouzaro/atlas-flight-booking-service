package com.flightsai.assistant.internal;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.flightsai.assistant.internal.ToolViews.PolicyExcerpt;

/**
 * Base de políticas do RAG. Os documentos ficam em {@code assistant/policies/*.md}; cada seção que começa
 * com {@code ## } vira um trecho no pgvector, com o arquivo e o hash do conteúdo nos metadados.
 */
@Component
class PolicyStore {

    private static final Logger log = LoggerFactory.getLogger(PolicyStore.class);
    static final String LOCATION = "classpath:assistant/policies/*.md";

    private final VectorStore vectors;
    private final JdbcTemplate jdbc;
    private final AssistantProperties props;

    PolicyStore(VectorStore vectors, JdbcTemplate jdbc, AssistantProperties props) {
        this.vectors = vectors;
        this.jdbc = jdbc;
        this.props = props;
    }

    List<PolicyExcerpt> search(String question) {
        return vectors.similaritySearch(SearchRequest.builder().query(question).topK(props.policies().topK()).build())
                .stream()
                .map(d -> new PolicyExcerpt(String.valueOf(d.getMetadata().get("source")),
                        String.valueOf(d.getMetadata().get("section")), d.getText()))
                .toList();
    }

    /**
     * Indexa o que mudou: arquivo com o mesmo hash é pulado; arquivo alterado tem os trechos antigos
     * apagados e os novos gravados. Gera embeddings, então chama a OpenAI.
     *
     * @return quantos arquivos foram (re)indexados
     */
    int ingest() throws IOException {
        int indexed = 0;
        for (Resource resource : new PathMatchingResourcePatternResolver().getResources(LOCATION)) {
            String source = resource.getFilename();
            String content = resource.getContentAsString(UTF_8);
            String hash = sha256(content);
            Integer current = jdbc.queryForObject("SELECT count(*) FROM vector_store "
                    + "WHERE metadata->>'source' = ? AND metadata->>'hash' = ?", Integer.class, source, hash);
            if (current != null && current > 0) {
                continue;
            }
            jdbc.update("DELETE FROM vector_store WHERE metadata->>'source' = ?", source);
            vectors.add(sections(source, hash, content));
            indexed++;
            log.info("Indexed policy {}", source);
        }
        return indexed;
    }

    static List<Document> sections(String source, String hash, String content) {
        List<Document> docs = new ArrayList<>();
        String title = source;
        StringBuilder current = new StringBuilder();
        String section = null;
        for (String line : content.split("\\R")) {
            if (line.startsWith("# ")) {
                title = line.substring(2).trim();
            } else if (line.startsWith("## ")) {
                addSection(docs, source, hash, title, section, current);
                section = line.substring(3).trim();
                current.setLength(0);
            } else {
                current.append(line).append('\n');
            }
        }
        addSection(docs, source, hash, title, section, current);
        return docs;
    }

    private static void addSection(List<Document> docs, String source, String hash, String title, String section,
                                   StringBuilder body) {
        String text = body.toString().trim();
        if (section == null || text.isEmpty()) {
            return;
        }
        docs.add(new Document(title + " - " + section + "\n\n" + text,
                Map.of("source", source, "section", section, "hash", hash)));
    }

    private static String sha256(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
