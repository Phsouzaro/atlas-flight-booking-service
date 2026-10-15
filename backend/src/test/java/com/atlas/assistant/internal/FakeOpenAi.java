package com.atlas.assistant.internal;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Servidor HTTP que finge ser a API da OpenAI, para o CI rodar o assistente sem chave e sem custo.
 * <p>
 * O Spring AI real fala com ele: monta o pedido, lê as tool calls, executa as tools contra o banco e manda
 * os resultados de volta. Só a decisão do modelo é roteirizada pelo teste ({@link #enqueue}). Embeddings
 * são um saco de palavras determinístico, o bastante para a busca de política achar o documento certo.
 */
final class FakeOpenAi implements AutoCloseable {

    static final int DIMENSIONS = 1536;

    private final ObjectMapper json = new ObjectMapper();
    private final HttpServer server;
    private final Deque<Function<JsonNode, Reply>> script = new ArrayDeque<>();
    private final List<JsonNode> chatRequests = new ArrayList<>();

    record Reply(int status, String body) {
    }

    record Call(String name, Map<String, Object> arguments) {
    }

    private FakeOpenAi() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", this::chat);
        server.createContext("/v1/embeddings", this::embeddings);
        server.start();
    }

    static FakeOpenAi start() {
        try {
            return new FakeOpenAi();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    synchronized void reset() {
        script.clear();
        chatRequests.clear();
    }

    /** Próxima resposta do "modelo". Recebe o pedido, para o roteiro poder ler o que as tools devolveram. */
    synchronized void enqueue(Function<JsonNode, Reply> reply) {
        script.add(reply);
    }

    synchronized void enqueueToolCalls(Call... calls) {
        enqueue(request -> toolCalls(calls));
    }

    synchronized void enqueueText(String content) {
        enqueue(request -> text(content));
    }

    synchronized List<JsonNode> chatRequests() {
        return List.copyOf(chatRequests);
    }

    Reply toolCalls(Call... calls) {
        ObjectNode message = json.createObjectNode().put("role", "assistant");
        message.putNull("content");
        ArrayNode toolCalls = message.putArray("tool_calls");
        for (int i = 0; i < calls.length; i++) {
            ObjectNode call = toolCalls.addObject().put("id", "call_" + i).put("type", "function");
            call.putObject("function").put("name", calls[i].name()).put("arguments", write(calls[i].arguments()));
        }
        return completion(message, "tool_calls");
    }

    Reply text(String content) {
        return completion(json.createObjectNode().put("role", "assistant").put("content", content), "stop");
    }

    static Reply error(int status) {
        return new Reply(status, "{\"error\":{\"message\":\"simulated failure\",\"type\":\"server_error\"}}");
    }

    private Reply completion(ObjectNode message, String finishReason) {
        ObjectNode root = json.createObjectNode().put("id", "chatcmpl-fake").put("object", "chat.completion")
                .put("created", 1_700_000_000L).put("model", "gpt-4.1-mini");
        root.putArray("choices").addObject().put("index", 0).put("finish_reason", finishReason).set("message", message);
        root.putObject("usage").put("prompt_tokens", 100).put("completion_tokens", 10).put("total_tokens", 110);
        return new Reply(200, root.toString());
    }

    private void chat(HttpExchange exchange) throws IOException {
        JsonNode request = json.readTree(exchange.getRequestBody());
        Function<JsonNode, Reply> next;
        synchronized (this) {
            chatRequests.add(request);
            next = script.poll();
        }
        respond(exchange, next == null ? text("ok") : next.apply(request));
    }

    private void embeddings(HttpExchange exchange) throws IOException {
        JsonNode request = json.readTree(exchange.getRequestBody());
        List<String> inputs = new ArrayList<>();
        if (request.get("input").isArray()) {
            request.get("input").forEach(n -> inputs.add(n.asText()));
        } else {
            inputs.add(request.get("input").asText());
        }
        ObjectNode root = json.createObjectNode().put("object", "list").put("model", "text-embedding-3-small");
        ArrayNode data = root.putArray("data");
        for (int i = 0; i < inputs.size(); i++) {
            ArrayNode vector = data.addObject().put("object", "embedding").put("index", i).putArray("embedding");
            for (float v : embed(inputs.get(i))) {
                vector.add(v);
            }
        }
        root.putObject("usage").put("prompt_tokens", 1).put("total_tokens", 1);
        respond(exchange, new Reply(200, root.toString()));
    }

    /** Saco de palavras com hash: textos que dividem palavras ficam perto no cosseno. */
    static float[] embed(String text) {
        float[] v = new float[DIMENSIONS];
        for (String word : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (word.length() >= 3) {
                v[Math.floorMod(word.hashCode(), DIMENSIONS)] += 1f;
            }
        }
        double norm = 0;
        for (float x : v) {
            norm += x * x;
        }
        if (norm == 0) {
            v[0] = 1f;
            return v;
        }
        float n = (float) Math.sqrt(norm);
        for (int i = 0; i < v.length; i++) {
            v[i] /= n;
        }
        return v;
    }

    private void respond(HttpExchange exchange, Reply reply) throws IOException {
        byte[] bytes = reply.body().getBytes(UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(reply.status(), bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
