package edu.whu.developer.message;

import edu.whu.developer.auth.Role;
import edu.whu.developer.auth.User;
import edu.whu.developer.auth.UserRepository;
import edu.whu.developer.common.AuthSupport;
import edu.whu.developer.common.AuthUser;
import edu.whu.developer.notify.NotificationService;
import edu.whu.developer.task.Task;
import edu.whu.developer.task.TaskRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MessageService {

    private final TaskMessageRepository messages;
    private final TaskRepository tasks;
    private final UserRepository users;
    private final NotificationService notifications;

    public MessageService(TaskMessageRepository messages,
                          TaskRepository tasks,
                          UserRepository users,
                          NotificationService notifications) {
        this.messages = messages;
        this.tasks = tasks;
        this.users = users;
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public List<MessageView> thread(Long taskId, Long studentId) {
        AuthUser user = AuthSupport.current();
        Long threadStudent = resolveStudent(taskId, studentId, user);
        return messages.findByTaskIdAndStudentIdOrderByIdAsc(taskId, threadStudent).stream().map(this::toView).toList();
    }

    @Transactional
    public MessageView send(Long taskId, SendRequest request) {
        AuthUser user = AuthSupport.current();
        Task task = tasks.findById(taskId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在"));
        Long threadStudent = resolveStudent(taskId, request.studentId(), user);
        User student = users.findById(threadStudent).orElseThrow();
        User sender = users.findById(user.getId()).orElseThrow();
        TaskMessage message = new TaskMessage();
        message.setTask(task);
        message.setStudent(student);
        message.setSender(sender);
        message.setBody(request.body().trim());
        messages.save(message);
        Long recipient = user.getId().equals(threadStudent) ? task.getPublisher().getId() : threadStudent;
        notifications.push(recipient, "任务私信", user.getDisplayName() + " 在「" + task.getTitle() + "」发来消息");
        return toView(message);
    }

    @Transactional(readOnly = true)
    public List<ThreadSummary> inbox() {
        AuthUser user = AuthSupport.current();
        Map<String, ThreadSummary> grouped = new LinkedHashMap<>();
        for (TaskMessage message : messages.inboxFor(user.getId())) {
            if (user.getRole() == Role.PUBLISHER && !message.getTask().getPublisher().getId().equals(user.getId())
                    && !message.getStudent().getId().equals(user.getId())) {
                continue;
            }
            String key = message.getTask().getId() + ":" + message.getStudent().getId();
            grouped.putIfAbsent(key, new ThreadSummary(
                    message.getTask().getId(),
                    message.getTask().getTitle(),
                    message.getStudent().getId(),
                    message.getStudent().getDisplayName(),
                    message.getBody(),
                    message.getCreatedAt()));
        }
        return new ArrayList<>(grouped.values());
    }

    private Long resolveStudent(Long taskId, Long requestedStudentId, AuthUser user) {
        Task task = tasks.findById(taskId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在"));
        if (user.getRole() == Role.STUDENT) {
            return user.getId();
        }
        if (user.getRole() == Role.PUBLISHER && task.getPublisher().getId().equals(user.getId())) {
            if (requestedStudentId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请指定学生");
            }
            return requestedStudentId;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "不能查看这条私信");
    }

    private MessageView toView(TaskMessage message) {
        return new MessageView(
                message.getId(),
                message.getTask().getId(),
                message.getStudent().getId(),
                message.getSender().getId(),
                message.getSender().getDisplayName(),
                message.getBody(),
                message.getCreatedAt());
    }

    public record SendRequest(Long studentId, @NotBlank(message = "请填写消息") @Size(max = 2000) String body) {
    }

    public record MessageView(Long id, Long taskId, Long studentId, Long senderId, String senderName, String body, LocalDateTime createdAt) {
    }

    public record ThreadSummary(Long taskId, String taskTitle, Long studentId, String studentName, String lastBody, LocalDateTime createdAt) {
    }
}
