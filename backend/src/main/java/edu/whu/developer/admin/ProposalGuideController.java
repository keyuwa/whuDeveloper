package edu.whu.developer.admin;

import edu.whu.developer.auth.Role;
import edu.whu.developer.common.AuthSupport;
import edu.whu.developer.notify.AuditService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class ProposalGuideController {
    private final SettingService settings;
    private final AuditService audit;

    public ProposalGuideController(SettingService settings, AuditService audit) {
        this.settings = settings;
        this.audit = audit;
    }

    @GetMapping("/proposal-guides")
    @Transactional(readOnly = true)
    public Map<Channel, Guide> list() {
        var legacy = new Guide(
                settings.getRaw("proposal_guide_introduction", "## 自我介绍"),
                settings.getRaw("proposal_guide_experience", "## 相关经验"),
                settings.getRaw("proposal_guide_approach", "## 技术方案"),
                settings.getRaw("proposal_guide_schedule", "## 时间计划"));
        var research = new Guide("## 理解与切入点", "## 研究/技术方案", "## 已有基础与证据", "## 周期与里程碑");
        var guides = new EnumMap<Channel, Guide>(Channel.class);
        for (Channel channel : Channel.values()) {
            var fallback = channel == Channel.RA ? research : legacy;
            String prefix = channel.prefix();
            guides.put(channel, new Guide(
                    settings.getRaw(prefix + "introduction", fallback.introduction()),
                    settings.getRaw(prefix + "experience", fallback.experience()),
                    settings.getRaw(prefix + "approach", fallback.approach()),
                    settings.getRaw(prefix + "schedule", fallback.schedule())));
        }
        return guides;
    }

    @PutMapping("/admin/settings/proposal/{channel}")
    @Transactional
    public Guide update(@PathVariable Channel channel, @Valid @RequestBody Guide guide) {
        var admin = AuthSupport.requireRole(Role.ADMIN);
        String prefix = channel.prefix();
        settings.put(prefix + "introduction", guide.introduction());
        settings.put(prefix + "experience", guide.experience());
        settings.put(prefix + "approach", guide.approach());
        settings.put(prefix + "schedule", guide.schedule());
        audit.record(admin.getId(), "SETTINGS", 0L, "UPDATE", "proposal:" + channel.name());
        return guide;
    }

    public enum Channel {
        TA, INTERN, RA, TEAM;

        String prefix() {
            return "proposal_guide_" + name().toLowerCase(Locale.ROOT) + "_";
        }
    }

    public record Guide(
            @NotNull @Size(max = 10000) String introduction,
            @NotNull @Size(max = 10000) String experience,
            @NotNull @Size(max = 10000) String approach,
            @NotNull @Size(max = 10000) String schedule
    ) {}
}
