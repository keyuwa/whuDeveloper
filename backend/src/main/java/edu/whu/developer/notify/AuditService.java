package edu.whu.developer.notify;

import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private final StatusEventRepository events;

    public AuditService(StatusEventRepository events) {
        this.events = events;
    }

    public void record(Long actorId, String entityType, Long entityId, String action, String detail) {
        StatusEvent event = new StatusEvent();
        event.setActorId(actorId);
        event.setEntityType(entityType);
        event.setEntityId(entityId);
        event.setAction(action);
        event.setDetail(detail);
        events.save(event);
    }
}
