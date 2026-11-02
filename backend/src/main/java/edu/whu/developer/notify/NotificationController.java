package edu.whu.developer.notify;

import edu.whu.developer.common.AuthSupport;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    public List<NotificationView> list() {
        AuthSupport.current();
        return notifications.mine().stream()
                .map(item -> new NotificationView(item.getId(), item.getTitle(), item.getBody(), item.getReadAt(), item.getCreatedAt()))
                .toList();
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unread() {
        return Map.of("count", notifications.unread());
    }

    @PostMapping("/{id}/read")
    public void read(@PathVariable Long id) {
        notifications.markRead(id);
    }

    public record NotificationView(Long id, String title, String body, LocalDateTime readAt, LocalDateTime createdAt) {
    }
}
