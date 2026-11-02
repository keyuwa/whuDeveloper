package edu.whu.developer.reminder;

import edu.whu.developer.admin.SettingService;
import edu.whu.developer.application.TaskApplication;
import edu.whu.developer.application.TaskApplicationRepository;
import edu.whu.developer.auth.Role;
import edu.whu.developer.auth.User;
import edu.whu.developer.auth.UserRepository;
import edu.whu.developer.common.MailService;
import edu.whu.developer.notify.AuditService;
import edu.whu.developer.notify.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ReminderService {

    private final TaskApplicationRepository applications;
    private final ReminderRepository reminders;
    private final UserRepository users;
    private final SettingService settings;
    private final NotificationService notifications;
    private final MailService mail;
    private final AuditService audit;

    public ReminderService(TaskApplicationRepository applications,
                           ReminderRepository reminders,
                           UserRepository users,
                           SettingService settings,
                           NotificationService notifications,
                           MailService mail,
                           AuditService audit) {
        this.applications = applications;
        this.reminders = reminders;
        this.users = users;
        this.settings = settings;
        this.notifications = notifications;
        this.mail = mail;
        this.audit = audit;
    }

    @Scheduled(fixedDelayString = "${app.reminder.scan-ms:60000}")
    @Transactional
    public int scan() {
        int reviewDays = settings.getInt("review_sla_days", 7);
        int escalateDays = settings.getInt("escalate_sla_days", 14);
        LocalDateTime now = LocalDateTime.now();
        int created = 0;
        for (TaskApplication application : applications.findByStatusOrderByIdAsc("SUBMITTED")) {
            if (!application.getCreatedAt().plusDays(reviewDays).isAfter(now)) {
                created += remindOnce(application, "APPLICATION_SLA:" + application.getId(), "SENT",
                        "申请待审催办",
                        "「" + application.getTask().getTitle() + "」有申请超过 " + reviewDays + " 天未审。");
            }
            if (!application.getCreatedAt().plusDays(escalateDays).isAfter(now)) {
                created += escalateOnce(application, escalateDays);
            }
        }
        return created;
    }

    @Transactional(readOnly = true)
    public List<Reminder> list() {
        return reminders.findAllByOrderByIdDesc();
    }

    @Transactional
    public Reminder escalate(Long id, Long actorId) {
        Reminder reminder = reminders.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "催办不存在"));
        reminder.setStatus("ESCALATED");
        audit.record(actorId, "REMINDER", reminder.getId(), "ESCALATE", reminder.getDedupeKey());
        for (User admin : users.findByRoleOrderByIdAsc(Role.ADMIN)) {
            notifications.push(admin.getId(), "催办已升级", "催办 #" + reminder.getId() + " 已进入管理队列。");
        }
        return reminder;
    }

    @Transactional
    public Reminder resolve(Long id, Long actorId) {
        Reminder reminder = reminders.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "催办不存在"));
        reminder.setStatus("RESOLVED");
        audit.record(actorId, "REMINDER", reminder.getId(), "RESOLVE", null);
        return reminder;
    }

    private int remindOnce(TaskApplication application, String key, String status, String title, String body) {
        if (reminders.existsByDedupeKey(key)) {
            return 0;
        }
        Reminder reminder = new Reminder();
        reminder.setType("APPLICATION_REVIEW");
        reminder.setTargetType("APPLICATION");
        reminder.setTargetId(application.getId());
        reminder.setStatus(status);
        reminder.setDedupeKey(key);
        reminders.save(reminder);
        application.setRemindCount(application.getRemindCount() + 1);
        User publisher = application.getTask().getPublisher();
        notifications.push(publisher.getId(), title, body);
        mail.send(publisher.getEmail(), title, body);
        audit.record(null, "APPLICATION", application.getId(), "REMIND", key);
        return 1;
    }

    private int escalateOnce(TaskApplication application, int days) {
        String key = "APPLICATION_ESCALATE:" + application.getId();
        if (reminders.existsByDedupeKey(key)) {
            return 0;
        }
        Reminder reminder = new Reminder();
        reminder.setType("APPLICATION_ESCALATED");
        reminder.setTargetType("APPLICATION");
        reminder.setTargetId(application.getId());
        reminder.setStatus("ESCALATED");
        reminder.setDedupeKey(key);
        reminders.save(reminder);
        String body = "「" + application.getTask().getTitle() + "」的申请超过 " + days + " 天仍未审，已升级管理队列。";
        for (User admin : users.findByRoleOrderByIdAsc(Role.ADMIN)) {
            notifications.push(admin.getId(), "催办升级", body);
            mail.send(admin.getEmail(), "催办升级", body);
        }
        audit.record(null, "APPLICATION", application.getId(), "ESCALATE", key);
        return 1;
    }
}
