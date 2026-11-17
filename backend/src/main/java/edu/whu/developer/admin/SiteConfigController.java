package edu.whu.developer.admin;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class SiteConfigController {

    private final SettingService settings;

    public SiteConfigController(SettingService settings) {
        this.settings = settings;
    }

    @GetMapping("/site-config")
    public SiteConfigView siteConfig() {
        String legacy = settings.getString("tutorial_doc_url", "");
        String student = settings.getString("tutorial_doc_url_student", "");
        String publisher = settings.getString("tutorial_doc_url_publisher", "");
        if (student.isBlank()) {
            student = legacy;
        }
        return new SiteConfigView(
                student,
                publisher,
                settings.getString("tutorial_banner_text", "查看使用教程"),
                settings.getRaw("proposal_guide_introduction", "## 自我介绍"),
                settings.getRaw("proposal_guide_experience", "## 相关经验"),
                settings.getRaw("proposal_guide_approach", "## 技术方案"),
                settings.getRaw("proposal_guide_schedule", "## 时间计划"),
                settings.getString("plaza_blurb_ta", DEFAULT_BLURB_TA),
                settings.getString("plaza_blurb_intern", DEFAULT_BLURB_INTERN),
                settings.getString("plaza_blurb_ra", DEFAULT_BLURB_RA),
                settings.getString("plaza_blurb_team", DEFAULT_BLURB_TEAM));
    }

    public static final String DEFAULT_BLURB_TA = "助教任务：课程支持、答疑与教学协作";
    public static final String DEFAULT_BLURB_INTERN = "开发任务：产品功能、工程实现与开源贡献";
    public static final String DEFAULT_BLURB_RA = "科研任务：以揭榜形式开放方向，学生提交方案竞标";
    public static final String DEFAULT_BLURB_TEAM = "难题：运营、标注等协作需求，按岗位报名，一次可招多人";

    public record SiteConfigView(
            String tutorialDocUrlStudent,
            String tutorialDocUrlPublisher,
            String tutorialBannerText,
            String proposalGuideIntroduction,
            String proposalGuideExperience,
            String proposalGuideApproach,
            String proposalGuideSchedule,
            String plazaBlurbTa,
            String plazaBlurbIntern,
            String plazaBlurbRa,
            String plazaBlurbTeam
    ) {
    }
}
