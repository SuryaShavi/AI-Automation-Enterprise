package com.aieap.platform.notification.ws;

import com.aieap.platform.notification.NotificationController;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class NotificationStreamService {
    private static final Logger log = LoggerFactory.getLogger(NotificationStreamService.class);
    private static final long SSE_TIMEOUT_MS = 30 * 60 * 1000L;

    private final Map<String, CopyOnWriteArrayList<SseEmitter>> emittersByUser = new ConcurrentHashMap<>();

    public SseEmitter register(String userId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emittersByUser.computeIfAbsent(userId, ignored -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(userId, emitter));
        emitter.onTimeout(() -> removeEmitter(userId, emitter));
        emitter.onError(error -> removeEmitter(userId, emitter));

        try {
            emitter.send(SseEmitter.event().name("connected").data(Map.of("status", "connected")));
        } catch (IOException ex) {
            removeEmitter(userId, emitter);
        }

        return emitter;
    }

    public void publish(String userId, NotificationController.NotificationItem notification) {
        List<SseEmitter> emitters = emittersByUser.get(userId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        List<SseEmitter> stale = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("notification").id(notification.id()).data(notification));
            } catch (IOException ex) {
                stale.add(emitter);
            }
        }

        stale.forEach(emitter -> removeEmitter(userId, emitter));
        if (!stale.isEmpty()) {
            log.debug("Removed {} stale SSE emitter(s) for user {}", stale.size(), userId);
        }
    }

    private void removeEmitter(String userId, SseEmitter emitter) {
        List<SseEmitter> emitters = emittersByUser.get(userId);
        if (emitters == null) {
            return;
        }
        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            emittersByUser.remove(userId);
        }
    }
}
