package edu.whu.developer.ability;

import edu.whu.developer.ability.AbilityService.AbilityApplicationView;
import edu.whu.developer.ability.AbilityService.ApplyRequest;
import edu.whu.developer.ability.AbilityService.CommentRequest;
import edu.whu.developer.ability.AbilityService.DecideRequest;
import edu.whu.developer.ability.AbilityService.LeaderboardEntry;
import edu.whu.developer.ability.AbilityService.MyAbilityView;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ability")
public class AbilityController {

    private final AbilityService ability;

    public AbilityController(AbilityService ability) {
        this.ability = ability;
    }

    @GetMapping("/rubric")
    public Map<String, String> rubric() {
        return Map.of("markdown", ability.rubricMarkdown());
    }

    @GetMapping("/leaderboard")
    public List<LeaderboardEntry> leaderboard() {
        return ability.leaderboard();
    }

    @GetMapping("/me")
    public MyAbilityView me() {
        return ability.mine();
    }

    @PostMapping("/applications")
    public AbilityApplicationView apply(@Valid @RequestBody ApplyRequest request) {
        return ability.apply(request);
    }

    @GetMapping("/applications/mine")
    public List<AbilityApplicationView> mine() {
        return ability.myApplications();
    }

    @GetMapping("/applications/queue")
    public List<AbilityApplicationView> queue() {
        return ability.queue();
    }

    @PostMapping("/applications/{id}/claim")
    public AbilityApplicationView claim(@PathVariable Long id) {
        return ability.claim(id);
    }

    @PostMapping("/applications/{id}/request-info")
    public AbilityApplicationView requestInfo(@PathVariable Long id, @Valid @RequestBody CommentRequest request) {
        return ability.requestInfo(id, request);
    }

    @PostMapping("/applications/{id}/decide")
    public AbilityApplicationView decide(@PathVariable Long id, @Valid @RequestBody DecideRequest request) {
        return ability.decide(id, request);
    }

    @PostMapping("/applications/{id}/withdraw")
    public AbilityApplicationView withdraw(@PathVariable Long id) {
        return ability.withdraw(id);
    }
}
