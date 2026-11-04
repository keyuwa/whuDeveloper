package edu.whu.developer.notify;

import edu.whu.developer.common.AuthSupport;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository notifications;

    public NotificationService(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    public void push(Long userId, String title, String body) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setTitle(title);
        notification.setBody(body);
        notifications.save(notification);
    }

    @Transactional(readOnly = true)
    public List<Notification> mine() {
        return notifications.findByUserIdOrderByIdDesc(AuthSupport.current().getId());
    }

    @Transactional(readOnly = true)
    public long unread() {
        return notifications.countByUserIdAndReadAtIsNull(AuthSupport.current().getId());
    }

    @Transactional
    public void markRead(Long id) {
        Notification notification = notifications.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "通知不存在"));
        if (!notification.getUserId().equals(AuthSupport.current().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "不能操作别人的通知");
        }
        if (notification.getReadAt() == null) {
            notification.setReadAt(LocalDateTime.now());
        }
    }

    @Transactional
    public int markAllRead() {
        return notifications.markAllRead(AuthSupport.current().getId(), LocalDateTime.now());
    }

    @Transactional
    public void delete(Long id) {
        Notification notification = notifications.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "通知不存在"));
        if (!notification.getUserId().equals(AuthSupport.current().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "不能删除别人的通知");
        }
        notifications.delete(notification);
    }

    @Transactional
    public int deleteAllMine() {
        return notifications.deleteAllByUserId(AuthSupport.current().getId());
    }
}
