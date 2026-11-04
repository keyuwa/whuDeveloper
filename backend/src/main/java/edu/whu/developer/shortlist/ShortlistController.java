package edu.whu.developer.shortlist;

import edu.whu.developer.task.TaskDtos.TaskResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class ShortlistController {

    private final ShortlistService shortlists;

    public ShortlistController(ShortlistService shortlists) {
        this.shortlists = shortlists;
    }

    @GetMapping("/me/shortlists")
    public List<TaskResponse> mine() {
        return shortlists.mine();
    }

    @PostMapping("/tasks/{taskId}/shortlist")
    public TaskResponse add(@PathVariable Long taskId) {
        return shortlists.add(taskId);
    }

    @DeleteMapping("/tasks/{taskId}/shortlist")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable Long taskId) {
        shortlists.remove(taskId);
    }
}
