package edu.whu.developer.completion;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/completion")
public class CompletionController {

    private final CompletionService completions;

    public CompletionController(CompletionService completions) {
        this.completions = completions;
    }

    @GetMapping
    public ResponseEntity<CompletionResponse> get(@PathVariable Long projectId) {
        return completions.find(projectId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping
    public CompletionResponse create(@PathVariable Long projectId, @Valid @RequestBody CompletionRequest request) {
        return completions.complete(projectId, request);
    }
}
