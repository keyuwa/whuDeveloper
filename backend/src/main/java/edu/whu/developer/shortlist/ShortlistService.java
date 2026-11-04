package edu.whu.developer.shortlist;

import edu.whu.developer.auth.Role;
import edu.whu.developer.auth.User;
import edu.whu.developer.auth.UserRepository;
import edu.whu.developer.common.AuthSupport;
import edu.whu.developer.common.AuthUser;
import edu.whu.developer.notify.AuditService;
import edu.whu.developer.task.Task;
import edu.whu.developer.task.TaskDtos.TaskResponse;
import edu.whu.developer.task.TaskRepository;
import edu.whu.developer.task.TaskService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ShortlistService {

    private final TaskShortlistRepository shortlists;
    private final TaskRepository tasks;
    private final UserRepository users;
    private final TaskService taskService;
    private final AuditService audit;

    public ShortlistService(TaskShortlistRepository shortlists,
                            TaskRepository tasks,
                            UserRepository users,
                            TaskService taskService,
                            AuditService audit) {
        this.shortlists = shortlists;
        this.tasks = tasks;
        this.users = users;
        this.taskService = taskService;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> mine() {
        AuthUser student = AuthSupport.requireRole(Role.STUDENT);
        return shortlists.findByStudentIdOrderByIdDesc(student.getId()).stream()
                .filter(item -> "OPEN".equals(item.getTask().getStatus()))
                .map(item -> taskService.get(item.getTask().getId()))
                .toList();
    }

    @Transactional
    public TaskResponse add(Long taskId) {
        AuthUser student = AuthSupport.requireRole(Role.STUDENT);
        Task task = tasks.findById(taskId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在"));
        if (!"OPEN".equals(task.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "只能将开放中的项目加入备选");
        }
        if (!shortlists.existsByStudentIdAndTaskId(student.getId(), taskId)) {
            User studentEntity = users.findById(student.getId()).orElseThrow();
            TaskShortlist row = new TaskShortlist();
            row.setStudent(studentEntity);
            row.setTask(task);
            shortlists.save(row);
            audit.record(student.getId(), "SHORTLIST", taskId, "ADD", task.getTitle());
        }
        return taskService.get(taskId);
    }

    @Transactional
    public void remove(Long taskId) {
        AuthUser student = AuthSupport.requireRole(Role.STUDENT);
        if (!shortlists.existsByStudentIdAndTaskId(student.getId(), taskId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "不在备选中");
        }
        shortlists.deleteByStudentIdAndTaskId(student.getId(), taskId);
        audit.record(student.getId(), "SHORTLIST", taskId, "REMOVE", null);
    }
}
