package edu.whu.developer.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.whu.developer.auth.Role;
import edu.whu.developer.common.AuthSupport;
import edu.whu.developer.notify.AuditService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class RecommendationController {
    private static final String KEY = "recommendation_rules";
    private final SettingService settings;
    private final ObjectMapper mapper;
    private final AuditService audit;

    public RecommendationController(SettingService settings, ObjectMapper mapper, AuditService audit) {
        this.settings = settings;
        this.mapper = mapper;
        this.audit = audit;
    }

    @GetMapping("/recommendation-rules")
    public Rules get() throws JsonProcessingException {
        String stored = settings.getRaw(KEY, "");
        return stored.isBlank() ? new Rules(null, null, null, null, 2, 3) : mapper.readValue(stored, Rules.class);
    }

    @PutMapping("/admin/settings/recommendation")
    @Transactional
    public Rules update(@Valid @RequestBody Rules rules) throws JsonProcessingException {
        var admin = AuthSupport.requireRole(Role.ADMIN);
        settings.put(KEY, mapper.writeValueAsString(rules));
        audit.record(admin.getId(), "SETTINGS", 0L, "UPDATE", "recommendation");
        return rules;
    }

    public record Rules(
            @Size(max = 50) List<@NotNull @Valid Rule> ta,
            @Size(max = 50) List<@NotNull @Valid Rule> intern,
            @Size(max = 50) List<@NotNull @Valid Rule> ra,
            @Size(max = 50) List<@NotNull @Valid Rule> team,
            @NotNull @Min(0) @Max(4) Integer masterSlots,
            @NotNull @Min(0) @Max(4) Integer doctorSlots
    ) {}

    public enum Field { DIRECTION, TITLE, CONTENT, LANGUAGE, POSITION, ANY }

    public record Rule(
            @NotNull @Size(max = 40) List<@NotBlank @Size(max = 80) String> majors,
            @NotEmpty @Size(max = 40) List<@NotBlank @Size(max = 80) String> keywords,
            @NotNull Field field,
            @NotNull @Min(1) @Max(100) Integer score
    ) {}
}
