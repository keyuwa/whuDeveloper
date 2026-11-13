package edu.whu.developer.task;

import edu.whu.developer.task.TaskDtos.PublisherTaskPage;
import edu.whu.developer.task.TaskDtos.TaskPage;
import edu.whu.developer.task.TaskDtos.TaskRequest;
import edu.whu.developer.task.TaskDtos.TaskResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class TaskController {

    private final TaskService tasks;

    public TaskController(TaskService tasks) {
        this.tasks = tasks;
    }

    @GetMapping("/tasks")
    public TaskPage list(
            @RequestParam(required = false) String roleType,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) String techField,
            @RequestParam(required = false) String supportLanguage,
            @RequestParam(required = false) String programmingLanguage,
            @RequestParam(required = false) String scheduleType,
            @RequestParam(required = false) Integer durationMin,
            @RequestParam(required = false) Integer durationMax,
            @RequestParam(required = false) Integer hoursMin,
            @RequestParam(required = false) Integer hoursMax,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return tasks.listOpen(
                roleType,
                difficulty,
                techField,
                supportLanguage,
                programmingLanguage,
                scheduleType,
                durationMin,
                durationMax,
                hoursMin,
                hoursMax,
                page,
                size);
    }

    @GetMapping("/tasks/{id}")
    public TaskResponse get(@PathVariable Long id) {
        return tasks.get(id);
    }

    @GetMapping("/publisher/tasks")
    public PublisherTaskPage mine(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String roleType,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return tasks.mine(status, roleType, keyword, page, size);
    }

    @PostMapping("/publisher/tasks")
    public TaskResponse create(@Valid @RequestBody TaskRequest request, @RequestParam(defaultValue = "false") boolean publish) {
        return tasks.create(request, publish);
    }

    @PutMapping("/publisher/tasks/{id}")
    public TaskResponse update(
            @PathVariable Long id,
            @Valid @RequestBody TaskRequest request,
            @RequestParam(required = false) Boolean publish) {
        return tasks.update(id, request, publish);
    }

    @PostMapping("/publisher/tasks/{id}/publish")
    public TaskResponse publish(@PathVariable Long id) {
        return tasks.publish(id);
    }

    @PostMapping("/publisher/tasks/{id}/close")
    public TaskResponse close(@PathVariable Long id) {
        return tasks.close(id);
    }

    @DeleteMapping("/publisher/tasks/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        tasks.deleteUnpublished(id);
    }
}
