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

    public String getString(String key, String fallback) {
        return settings.findById(key)
                .map(SystemSetting::getSettingValue)
                .filter(value -> value != null && !value.isBlank())
                .orElse(fallback);
    }

    /** Raw stored value including blank; missing key returns fallback. */
    public String getRaw(String key, String fallback) {
        return settings.findById(key)
                .map(SystemSetting::getSettingValue)
                .map(value -> value == null ? "" : value)
                .orElse(fallback);
    }

    public boolean getBoolean(String key, boolean fallback) {
        return settings.findById(key)
                .map(SystemSetting::getSettingValue)
                .map(value -> {
                    if (value == null || value.isBlank()) {
                        return fallback;
                    }
                    return "true".equalsIgnoreCase(value.trim()) || "1".equals(value.trim());
                })
                .orElse(fallback);
    }

    public void put(String key, String value) {
        SystemSetting setting = settings.findById(key).orElseGet(SystemSetting::new);
        setting.setSettingKey(key);
        setting.setSettingValue(value == null ? "" : value);
        settings.save(setting);
    }
}
