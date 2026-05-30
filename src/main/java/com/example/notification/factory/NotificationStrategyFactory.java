package com.example.notification.factory;

import com.example.notification.model.NotificationChannel;
import com.example.notification.strategy.NotificationStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class NotificationStrategyFactory {

    private final Map<NotificationChannel, NotificationStrategy> strategies = new EnumMap<>(NotificationChannel.class);

    @Autowired
    public NotificationStrategyFactory(List<NotificationStrategy> strategyList) {
        // Automatically register all NotificationStrategy beans into our factory lookup map
        for (NotificationStrategy strategy : strategyList) {
            strategies.put(strategy.getChannel(), strategy);
        }
    }

    /**
     * Factory method to retrieve the appropriate strategy for a given channel.
     *
     * @param channel The delivery channel
     * @return The concrete strategy implementation
     * @throws IllegalArgumentException if no strategy is registered for the channel
     */
    public NotificationStrategy getStrategy(NotificationChannel channel) {
        NotificationStrategy strategy = strategies.get(channel);
        if (strategy == null) {
            throw new IllegalArgumentException("No notification strategy registered for channel: " + channel);
        }
        return strategy;
    }
}
