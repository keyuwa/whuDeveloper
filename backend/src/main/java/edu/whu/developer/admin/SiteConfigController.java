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
                settings.getString("tutorial_banner_text", "系统使用教程请参考平台文档，点击查看完整说明。"));
    }

    public record SiteConfigView(String tutorialDocUrl, String tutorialBannerText) {
    }
}
