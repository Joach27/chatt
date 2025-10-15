package com.example.chatt.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.io.IOException;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "http://localhost:5173")
public class ChatController {

    private final WebClient webClient;
    private final String apiKey;
    @Value("${openrouter.default-model}")  
    private String defaultModel;  // modèle par défaut dans application.yml ou properties

    public ChatController(@Value("${openrouter.api-key}") String apiKey,
                          @Value("${openrouter.base-url}") String baseUrl) {
        this.apiKey = apiKey;
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @GetMapping("/stream")
    public SseEmitter stream(
        @RequestParam String sessionId,
        @RequestParam String message,
        @RequestParam(required = false) String model  // permet de passer un autre modèle
    ) {
        String chosenModel = (model != null && !model.isBlank()) ? model : defaultModel;
        SseEmitter emitter = new SseEmitter(0L);

        // Prépare le corps JSON pour OpenRouter avec streaming et le modèle choisi
        String requestBody = """
            {
              "model": "%s",
              "messages": [
                { "role": "user", "content": "%s" }
              ],
              "stream": true
            }
            """.formatted(escapeJson(chosenModel), escapeJson(message));

        Flux<String> flux = webClient.post()
                .uri("/chat/completions")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToFlux(String.class);

        flux.subscribe(
            chunk -> {
                try {
                    emitter.send(SseEmitter.event().name("chat").data(chunk));
                } catch (IOException e) {
                    emitter.completeWithError(e);
                }
            },
            error -> {
                emitter.completeWithError(error);
            },
            () -> {
                try {
                    emitter.send(SseEmitter.event().name("done").data(""));
                } catch (IOException e) {
                    // ignore
                }
                emitter.complete();
            }
        );

        return emitter;
    }

    private String escapeJson(String s) {
        return s.replace("\"", "\\\"");
    }
}
