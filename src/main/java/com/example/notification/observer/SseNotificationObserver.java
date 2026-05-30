package com.example.notification.observer;

import com.example.notification.model.Notification;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class SseNotificationObserver {

    // Thread-safe registry of connected observers (SSE Emitters)
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    /**
     * Registers a new client (emitter) to receive real-time updates.
     */
    public SseEmitter registerEmitter() {
        // Create an emitter that will stay open for up to 30 minutes
        SseEmitter emitter = new SseEmitter(1800000L);
        
        this.emitters.add(emitter);

        // Remove the emitter on completion, timeout, or error
        emitter.onCompletion(() -> this.emitters.remove(emitter));
        emitter.onTimeout(() -> this.emitters.remove(emitter));
        emitter.onError((ex) -> this.emitters.remove(emitter));

        // Send a connection success message to the client
        try {
            emitter.send(SseEmitter.event()
                    .name("connection")
                    .data("Connected successfully! Listening to real-time notification events."));
        } catch (IOException e) {
            this.emitters.remove(emitter);
        }

        return emitter;
    }

    /**
     * Listens for notification state change events and broadcasts them to all registered observers.
     */
    @EventListener
    public void handleNotificationStatusChanged(NotificationStatusChangedEvent event) {
        Notification notification = event.getNotification();
        System.out.println("[Observer] Broadcasting state update for Notification ID " 
                + notification.getId() + " to " + emitters.size() + " active dashboard client(s).");
        
        List<SseEmitter> deadEmitters = new CopyOnWriteArrayList<>();

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("notification-update")
                        .data(notification));
            } catch (Exception e) {
                deadEmitters.add(emitter);
            }
        }

        // Clean up closed or disconnected emitters
        if (!deadEmitters.isEmpty()) {
            this.emitters.removeAll(deadEmitters);
        }
    }
}
