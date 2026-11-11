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
        return new SiteConfigView(
                settings.getString("tutorial_doc_url", ""),
                settings.getString("tutorial_banner_text", "查看使用教程"),
                settings.getString("plaza_blurb_ta", DEFAULT_BLURB_TA),
                settings.getString("plaza_blurb_intern", DEFAULT_BLURB_INTERN),
                settings.getString("plaza_blurb_ra", DEFAULT_BLURB_RA),
                settings.getString("plaza_blurb_team", DEFAULT_BLURB_TEAM));
    }

    public static final String DEFAULT_BLURB_TA = "助教任务：课程支持、答疑与教学协作";
    public static final String DEFAULT_BLURB_INTERN = "开发任务：产品功能、工程实现与开源贡献";
    public static final String DEFAULT_BLURB_RA = "科研任务：以揭榜形式开放方向，学生提交方案竞标";
    public static final String DEFAULT_BLURB_TEAM = "项目：运营、标注等协作需求，按岗位报名，一次可招多人";

    public record SiteConfigView(
            String tutorialDocUrl,
            String tutorialBannerText,
            String plazaBlurbTa,
            String plazaBlurbIntern,
            String plazaBlurbRa,
            String plazaBlurbTeam
    ) {
    }
}
