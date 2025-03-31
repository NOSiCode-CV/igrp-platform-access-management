package cv.igrp.platform.access_management.shared.domain.events;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class EventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public EventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    /**
     * Publishes an event in the Spring context.
     *
     * @param event the event that will be published
     * @param <T> the event type
     */
    public <T> void publish(T event) {
        applicationEventPublisher.publishEvent(event);
    }
}