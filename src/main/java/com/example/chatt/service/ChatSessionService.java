package com.example.chatt.service;

import com.example.chatt.model.Message;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ChatSessionService {
    // En mémoire : Map<sessionId, List<Message>>
    private final Map<String, List<Message>> sessions = new HashMap<>();

    public synchronized void addMessage(String sessionId, String role, String content) {
        sessions.computeIfAbsent(sessionId, k -> new ArrayList<>())
                .add(new Message(role, content));
    }

    public synchronized List<Message> getMessages(String sessionId) {
        return new ArrayList<>(sessions.getOrDefault(sessionId, Collections.emptyList()));
    }

    public synchronized void clearSession(String sessionId) {
        sessions.remove(sessionId);
    }
}
