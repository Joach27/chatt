package com.example.chatt.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class OpenRouterService {

    private final WebClient webClient;
    private final String model;
    private final ChatSessionService sessionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenRouterService(
            @Value("${openrouter.api-key}") String apiKey,
            @Value("${openrouter.base-url}") String baseUrl,
            @Value("${openrouter.default-model}") String model,
            ChatSessionService sessionService
    ) {
        this.model = model;
        this.sessionService = sessionService;
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    // With--out session history
    public String sendMessage(String userMessage) {
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "user", "content", userMessage)
                )
        );

        JsonNode response = webClient.post()
                .uri("/chat/completions")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        JsonNode contentNode = response.at("/choices/0/message/content");
        return contentNode.isMissingNode()
                    ? "Aucune réponse."
                    : contentNode.asText();
    }

    /**
     * Simple (blocking) send with history.
     */
    public String sendMessageWithHistory(String sessionId, String userMessage) {
        sessionService.addMessage(sessionId, "user", userMessage);

        List<Map<String, String>> messages = sessionService.getMessages(sessionId).stream()
                .map(m -> Map.of("role", m.getRole(), "content", m.getContent()))
                .toList();

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", messages
        );

        JsonNode response = webClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        String reply = "Aucune réponse.";
        if (response != null) {
            JsonNode node = response.at("/choices/0/message/content");
            if (node.isMissingNode()) {
                // fallback: try to read text directly from choices[0].text
                node = response.at("/choices/0/text");
            }
            if (!node.isMissingNode()) {
                reply = node.asText();
            }
        }

        sessionService.addMessage(sessionId, "assistant", reply);
        return reply;
    }

    /**
     * Streaming call : returns a Flux of text chunks (String). It:
     * - builds messages from session history
     * - calls OpenRouter with "stream": true
     * - parses newline-delimited stream (SSE-like)
     */
    public Flux<String> streamMessage(String sessionId, String userMessage) {
        sessionService.addMessage(sessionId, "user", userMessage);

        List<Map<String, String>> messages = sessionService.getMessages(sessionId).stream()
                .map(m -> Map.of("role", m.getRole(), "content", m.getContent()))
                .toList();

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", messages);
        requestBody.put("stream", true);

        AtomicBoolean finished = new AtomicBoolean(false);

        return webClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(requestBody)
                .exchangeToFlux(this::parseStreamBody)
                // stop if we detect "[DONE]" or finished
                .takeUntil(s -> s.equals("[DONE]") || finished.get())
                .filter(s -> !s.equals("[DONE]"))
                .doOnNext(chunk -> {
                    // chunk is incremental text; add to session's latest assistant message (append)
                    // for simplicity, we append new assistant content cumulatively
                    sessionService.addMessage(sessionId, "assistant", chunk);
                });
    }

    /**
     * Parse response body DataBuffer stream into String chunks.
     * The server may return either SSE "data: {...}\n\n" blocks or raw JSON chunks.
     */
    private Flux<String> parseStreamBody(ClientResponse clientResponse) {
    return clientResponse.bodyToFlux(DataBuffer.class)
            .map(dataBuffer -> {
                byte[] bytes = new byte[dataBuffer.readableByteCount()];
                dataBuffer.read(bytes);
                DataBufferUtils.release(dataBuffer);
                return new String(bytes, StandardCharsets.UTF_8);
            })
            // nettoie les "data:" multiples ou lignes inutiles
            .flatMap(body -> {
                // découpe les lignes du flux
                String[] lines = body.split("\\r?\\n");
                return Flux.fromArray(lines);
            })
            .map(line -> {
                // supprime préfixes "data:", "data:data:", etc.
                while (line.startsWith("data:")) {
                    line = line.substring(5);
                }
                return line.trim();
            })
            .filter(line -> !line.isEmpty() && !line.equals("[DONE]"));
}

}
