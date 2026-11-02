package edu.whu.developer.admin;

import org.springframework.stereotype.Service;

@Service
public class SettingService {

    private final SystemSettingRepository settings;

    public SettingService(SystemSettingRepository settings) {
        this.settings = settings;
    }

    public int getInt(String key, int fallback) {
        return settings.findById(key)
                .map(SystemSetting::getSettingValue)
                .map(Integer::parseInt)
                .orElse(fallback);
    }

    public void put(String key, String value) {
        SystemSetting setting = settings.findById(key).orElseGet(SystemSetting::new);
        setting.setSettingKey(key);
        setting.setSettingValue(value);
        settings.save(setting);
    }
}
