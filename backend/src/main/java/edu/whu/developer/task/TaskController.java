package edu.whu.developer.task;

import edu.whu.developer.task.TaskDtos.TaskRequest;
import edu.whu.developer.task.TaskDtos.TaskResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class TaskController {

    private final TaskService tasks;

    public TaskController(TaskService tasks) {
        this.tasks = tasks;
    }

    @GetMapping("/tasks")
    public List<TaskResponse> list() {
        return tasks.listOpen();
    }

    @GetMapping("/tasks/{id}")
    public TaskResponse get(@PathVariable Long id) {
        return tasks.get(id);
    }

    @GetMapping("/publisher/tasks")
    public List<TaskResponse> mine() {
        return tasks.mine();
    }

    @PostMapping("/publisher/tasks")
    public TaskResponse create(@Valid @RequestBody TaskRequest request, @RequestParam(defaultValue = "false") boolean publish) {
        return tasks.create(request, publish);
    }

    @PutMapping("/publisher/tasks/{id}")
    public TaskResponse update(@PathVariable Long id, @Valid @RequestBody TaskRequest request) {
        return tasks.update(id, request);
    }

    @PostMapping("/publisher/tasks/{id}/publish")
    public TaskResponse publish(@PathVariable Long id) {
        return tasks.publish(id);
    }

    @PostMapping("/publisher/tasks/{id}/close")
    public TaskResponse close(@PathVariable Long id) {
        return tasks.close(id);
    }
}
