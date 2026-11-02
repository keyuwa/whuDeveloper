package edu.whu.developer.admin;

import edu.whu.developer.auth.Role;
import edu.whu.developer.auth.User;
import edu.whu.developer.auth.UserRepository;
import edu.whu.developer.auth.UserResponse;
import edu.whu.developer.common.AuthSupport;
import edu.whu.developer.common.AuthUser;
import edu.whu.developer.notify.AuditService;
import edu.whu.developer.notify.StatusEvent;
import edu.whu.developer.notify.StatusEventRepository;
import edu.whu.developer.reminder.Reminder;
import edu.whu.developer.reminder.ReminderService;
import edu.whu.developer.task.TaskDtos.TaskResponse;
import edu.whu.developer.task.TaskService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private static final Set<String> LEVELS = Set.of("L1", "L2", "L3", "L4", "L5");

    private final UserRepository users;
    private final TaskService tasks;
    private final ReminderService reminders;
    private final SettingService settings;
    private final StatusEventRepository events;
    private final AuditService audit;

    public AdminController(UserRepository users,
                           TaskService tasks,
                           ReminderService reminders,
                           SettingService settings,
                           StatusEventRepository events,
                           AuditService audit) {
        this.users = users;
        this.tasks = tasks;
        this.reminders = reminders;
        this.settings = settings;
        this.events = events;
        this.audit = audit;
    }

    @GetMapping("/users")
    public List<UserResponse> users() {
        AuthSupport.requireRole(Role.ADMIN);
        return users.findAll().stream().map(UserResponse::from).toList();
    }

    @PatchMapping("/users/{id}")
    public UserResponse updateUser(@PathVariable Long id, @Valid @RequestBody AbilityRequest request) {
        AuthUser admin = AuthSupport.requireRole(Role.ADMIN);
        User user = users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
        if (user.getRole() != Role.STUDENT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "只能调整学生的能力等级");
        }
        String level = request.abilityLevel().trim().toUpperCase();
        if (!LEVELS.contains(level)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "等级只能是 L1 到 L5");
        }
        user.setAbilityLevel(level);
        users.save(user);
        audit.record(admin.getId(), "USER", user.getId(), "ABILITY", level);
        return UserResponse.from(user);
    }

    @GetMapping("/tasks")
    public List<TaskResponse> tasks() {
        return tasks.allForAdmin();
    }

    @PatchMapping("/tasks/{id}")
    public TaskResponse taskStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        return tasks.adminStatus(id, request.status().trim().toUpperCase());
    }

    @GetMapping("/reminders")
    public List<ReminderView> reminders() {
        AuthSupport.requireRole(Role.ADMIN);
        return reminders.list().stream()
                .map(item -> new ReminderView(item.getId(), item.getType(), item.getTargetType(), item.getTargetId(), item.getStatus(), item.getDedupeKey(), item.getCreatedAt()))
                .toList();
    }

    @PostMapping("/reminders/scan")
    public Map<String, Integer> scan() {
        AuthSupport.requireRole(Role.ADMIN);
        return Map.of("created", reminders.scan());
    }

    @PostMapping("/reminders/{id}/escalate")
    public ReminderView escalate(@PathVariable Long id) {
        AuthUser admin = AuthSupport.requireRole(Role.ADMIN);
        return view(reminders.escalate(id, admin.getId()));
    }

    @PostMapping("/reminders/{id}/resolve")
    public ReminderView resolve(@PathVariable Long id) {
        AuthUser admin = AuthSupport.requireRole(Role.ADMIN);
        return view(reminders.resolve(id, admin.getId()));
    }

    @GetMapping("/settings")
    public SettingsView settings() {
        AuthSupport.requireRole(Role.ADMIN);
        return currentSettings();
    }

    @PutMapping("/settings")
    public SettingsView updateSettings(@Valid @RequestBody SettingsRequest request) {
        AuthUser admin = AuthSupport.requireRole(Role.ADMIN);
        settings.put("review_sla_days", String.valueOf(request.reviewSlaDays()));
        settings.put("escalate_sla_days", String.valueOf(request.escalateSlaDays()));
        audit.record(admin.getId(), "SETTINGS", 0L, "SLA", request.reviewSlaDays() + "/" + request.escalateSlaDays());
        return currentSettings();
    }

    @GetMapping("/audit")
    public List<AuditView> audit() {
        AuthSupport.requireRole(Role.ADMIN);
        return events.findTop50ByOrderByIdDesc().stream()
                .map(item -> new AuditView(item.getId(), item.getActorId(), item.getEntityType(), item.getEntityId(), item.getAction(), item.getDetail(), item.getCreatedAt()))
                .toList();
    }

    private SettingsView currentSettings() {
        return new SettingsView(settings.getInt("review_sla_days", 7), settings.getInt("escalate_sla_days", 14));
    }

    private ReminderView view(Reminder reminder) {
        return new ReminderView(reminder.getId(), reminder.getType(), reminder.getTargetType(), reminder.getTargetId(), reminder.getStatus(), reminder.getDedupeKey(), reminder.getCreatedAt());
    }

    public record AbilityRequest(@NotBlank String abilityLevel) {
    }

    public record StatusRequest(@NotBlank String status) {
    }

    public record SettingsRequest(@NotNull @Min(0) @Max(60) Integer reviewSlaDays, @NotNull @Min(0) @Max(120) Integer escalateSlaDays) {
    }

    public record SettingsView(int reviewSlaDays, int escalateSlaDays) {
    }

    public record ReminderView(Long id, String type, String targetType, Long targetId, String status, String dedupeKey, LocalDateTime createdAt) {
    }

    public record AuditView(Long id, Long actorId, String entityType, Long entityId, String action, String detail, LocalDateTime createdAt) {
    }
}
