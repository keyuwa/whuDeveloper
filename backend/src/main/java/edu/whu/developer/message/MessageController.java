package edu.whu.developer.message;

import edu.whu.developer.message.MessageService.MessageView;
import edu.whu.developer.message.MessageService.SendRequest;
import edu.whu.developer.message.MessageService.ThreadSummary;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class MessageController {

    private final MessageService messages;

    public MessageController(MessageService messages) {
        this.messages = messages;
    }

    @GetMapping("/messages")
    public List<ThreadSummary> inbox() {
        return messages.inbox();
    }

    @GetMapping("/tasks/{taskId}/messages")
    public List<MessageView> thread(@PathVariable Long taskId, @RequestParam(required = false) Long studentId) {
        return messages.thread(taskId, studentId);
    }

    @PostMapping("/tasks/{taskId}/messages/open")
    public List<MessageView> open(@PathVariable Long taskId) {
        return messages.open(taskId);
    }

    @PostMapping("/tasks/{taskId}/messages")
    public MessageView send(@PathVariable Long taskId, @Valid @RequestBody SendRequest request) {
        return messages.send(taskId, request);
    }

    @DeleteMapping("/tasks/{taskId}/messages")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteThread(@PathVariable Long taskId, @RequestParam(required = false) Long studentId) {
        messages.deleteThread(taskId, studentId);
    }
}
