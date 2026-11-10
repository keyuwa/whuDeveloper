package edu.whu.developer.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.whu.developer.admin.SettingService;
import edu.whu.developer.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ApplicationAiReviewService {

    private static final Logger log = LoggerFactory.getLogger(ApplicationAiReviewService.class);
    private static final String DEFAULT_PROMPT = """
            你是校园科研揭榜的方案可行性初判助手。只根据「榜单」与「方案」评估，不得因学校、性别、年龄等无关信息加分或降分。
            请优先判定方案在周期、方法、数据/算力与产出约束下是否现实可执行。
            维度口径（均为 0-100）：
            - completeness：问题定义、方法、计划、产出是否写全
            - fit：与榜单方向/目标是否对齐
            - feasibility：周期、方法、资源假设是否现实可执行（最重要）
            - evidence：是否有相关基础/文献/预备工作等依据
            请严格输出一个 JSON 对象（不要 Markdown 代码块），字段：
            score(0-100 整数)、completeness、fit、feasibility、evidence（均为 0-100 整数）、summary（不超过 80 字中文，说明为何可行/不可行）、risks（一句主要风险，不可空）。

            【榜单】
            标题：{{title}}
            研究方向：{{techField}}
            背景与目标：{{background}}
            产出/目标：{{outputRequirement}}
            能力与约束：{{techRequirement}}

            【方案】
            {{proposal}}
            """;

    private final TaskApplicationRepository applications;
    private final SettingService settings;
    private final PdfTextExtractor pdfTextExtractor;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private final boolean envEnabled;
    private final String envApiKey;
    private final String envBaseUrl;
    private final String envModel;

    public ApplicationAiReviewService(TaskApplicationRepository applications,
                                      SettingService settings,
                                      PdfTextExtractor pdfTextExtractor,
                                      ObjectMapper mapper,
                                      @Value("${app.ark.enabled:false}") boolean envEnabled,
                                      @Value("${app.ark.api-key:}") String envApiKey,
                                      @Value("${app.ark.base-url:}") String envBaseUrl,
                                      @Value("${app.ark.model:}") String envModel) {
        this.applications = applications;
        this.settings = settings;
        this.pdfTextExtractor = pdfTextExtractor;
        this.mapper = mapper;
        this.envEnabled = envEnabled;
        this.envApiKey = envApiKey == null ? "" : envApiKey.trim();
        this.envBaseUrl = envBaseUrl == null ? "" : envBaseUrl.trim();
        this.envModel = envModel == null ? "" : envModel.trim();
        log.info(
                "AI config bootstrap: envEnabled={} envApiKey={} envBaseUrl={} envModel={}",
                envEnabled,
                maskSecret(this.envApiKey),
                blankAsDash(this.envBaseUrl),
                blankAsDash(this.envModel));
    }

    public boolean isEnabled() {
        ArkConfig config = resolveConfig("isEnabled");
        boolean ready = config.enabled()
                && !config.apiKey().isBlank()
                && !config.baseUrl().isBlank()
                && !config.model().isBlank();
        if (!ready) {
            log.info(
                    "AI disabled/not ready: enabled={} apiKey={} baseUrl={} model={}",
                    config.enabled(),
                    maskSecret(config.apiKey()),
                    blankAsDash(config.baseUrl()),
                    blankAsDash(config.model()));
        }
        return ready;
    }

    public boolean shouldScore(Task task) {
        return task != null && "RA".equalsIgnoreCase(task.getRoleType()) && isEnabled();
    }

    /**
     * Probe Agent Plan / ARK with a tiny chat completion.
     * Blank overrides fall back to saved admin (or env) config.
     */
    public ArkTestResult testConnection(String apiKeyOverride, String baseUrlOverride, String modelOverride) {
        ArkConfig saved = resolveConfig("test");
        String apiKey = blankToNull(apiKeyOverride) != null ? apiKeyOverride.trim() : saved.apiKey();
        String baseUrl = blankToNull(baseUrlOverride) != null
                ? normalizeBaseUrl(baseUrlOverride.trim())
                : saved.baseUrl();
        String model = blankToNull(modelOverride) != null ? modelOverride.trim() : saved.model();
        log.info(
                "AI test start: apiKey={} baseUrl={} model={} overrideKey={} overrideBase={} overrideModel={}",
                maskSecret(apiKey),
                blankAsDash(baseUrl),
                blankAsDash(model),
                blankToNull(apiKeyOverride) != null,
                blankToNull(baseUrlOverride) != null,
                blankToNull(modelOverride) != null);
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("AI test aborted: missing api key");
            return ArkTestResult.fail("请先填写或保存 API Key");
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            log.warn("AI test aborted: missing base url");
            return ArkTestResult.fail("请先填写或保存 Base URL（Agent Plan 一般为 https://ark.cn-beijing.volces.com/api/plan/v1）");
        }
        if (model == null || model.isBlank()) {
            log.warn("AI test aborted: missing model");
            return ArkTestResult.fail("请先填写或保存模型 ID");
        }
        ArkConfig config = new ArkConfig(true, apiKey, baseUrl, model);
        long started = System.currentTimeMillis();
        try {
            String reply = chat(
                    config,
                    "只输出一个 JSON 对象：{\"ok\":true}",
                    "你只输出合法 JSON。");
            long latency = System.currentTimeMillis() - started;
            if (reply == null || reply.isBlank()) {
                log.warn("AI test empty response latencyMs={}", latency);
                return ArkTestResult.fail("接口返回空内容", latency);
            }
            log.info("AI test ok latencyMs={} model={} sample={}", latency, model, truncate(reply, 80));
            return ArkTestResult.ok("连通正常 · " + model, latency, truncate(reply, 120));
        } catch (Exception ex) {
            long latency = System.currentTimeMillis() - started;
            log.warn("AI test failed latencyMs={} model={} err={}", latency, model, truncate(ex.getMessage(), 280));
            return ArkTestResult.fail(truncate(ex.getMessage() == null ? "调用失败" : ex.getMessage(), 280), latency);
        }
    }

    @Async
    @Transactional
    public void scoreAsync(Long applicationId) {
        TaskApplication application = applications.findById(applicationId).orElse(null);
        if (application == null) {
            log.warn("AI score skip: application {} not found", applicationId);
            return;
        }
        Task task = application.getTask();
        if (task == null || !"RA".equalsIgnoreCase(task.getRoleType())) {
            application.setAiStatus("SKIPPED");
            applications.save(application);
            log.info("AI score skip applicationId={} reason=not-RA role={}", applicationId, task == null ? null : task.getRoleType());
            return;
        }
        if (!isEnabled()) {
            application.setAiStatus("SKIPPED");
            applications.save(application);
            log.info("AI score skip applicationId={} reason=not-ready", applicationId);
            return;
        }
        application.setAiStatus("PENDING");
        applications.save(application);
        try {
            String source = "text";
            String proposal = application.getProposalMd() == null ? "" : application.getProposalMd().trim();
            if (proposal.isBlank()) {
                byte[] pdf = application.getProposalPdf();
                if (pdf == null || pdf.length == 0) {
                    application.setAiStatus("SKIPPED");
                    application.setAiSummary("无方案正文，无法自动评分");
                    applications.save(application);
                    log.info("AI score skip applicationId={} reason=empty-proposal", applicationId);
                    return;
                }
                proposal = pdfTextExtractor.extract(pdf);
                source = "pdf";
                if (proposal.isBlank()) {
                    application.setAiStatus("SKIPPED");
                    application.setAiSummary("PDF 未能抽出可用正文（可能是扫描件），无法自动评分");
                    applications.save(application);
                    log.info("AI score skip applicationId={} reason=pdf-extract-empty bytes={}", applicationId, pdf.length);
                    return;
                }
            }
            ArkConfig config = resolveConfig("score");
            log.info(
                    "AI score start applicationId={} taskId={} source={} apiKey={} baseUrl={} model={} proposalChars={} customPrompt={}",
                    applicationId,
                    task.getId(),
                    source,
                    maskSecret(config.apiKey()),
                    config.baseUrl(),
                    config.model(),
                    proposal.length(),
                    !settings.getRaw("ra_challenge_ai_prompt", "").isBlank());
            long started = System.currentTimeMillis();
            String prompt = buildPrompt(task, proposal);
            String raw = chat(config, prompt, "你只输出合法 JSON。");
            JsonNode parsed = extractJson(raw);
            int score = clamp(parsed.path("score").asInt(-1));
            String summary = truncate(parsed.path("summary").asText(""), 500);
            int feasibility = clamp(parsed.path("feasibility").asInt(0));
            Map<String, Object> detail = new HashMap<>();
            detail.put("completeness", clamp(parsed.path("completeness").asInt(0)));
            detail.put("fit", clamp(parsed.path("fit").asInt(0)));
            detail.put("feasibility", feasibility);
            detail.put("evidence", clamp(parsed.path("evidence").asInt(0)));
            detail.put("feasibilityLabel", feasibilityLabel(feasibility));
            detail.put("risks", parsed.path("risks").asText(""));
            detail.put("source", source);
            if (score < 0) {
                throw new IllegalStateException("missing score");
            }
            application.setAiScore(score);
            String prefix = "pdf".equals(source) ? "（基于 PDF 正文）" : "";
            application.setAiSummary(summary.isBlank() ? prefix + "已完成可行性初判" : prefix + summary);
            application.setAiDetailJson(mapper.writeValueAsString(detail));
            application.setAiStatus("DONE");
            application.setAiScoredAt(LocalDateTime.now());
            applications.save(application);
            log.info(
                    "AI score done applicationId={} source={} latencyMs={} score={} feasibility={} label={}",
                    applicationId,
                    source,
                    System.currentTimeMillis() - started,
                    score,
                    feasibility,
                    feasibilityLabel(feasibility));
        } catch (Exception ex) {
            log.warn("AI score failed applicationId={}", applicationId, ex);
            application.setAiStatus("FAILED");
            application.setAiSummary("AI 评分暂不可用");
            applications.save(application);
        }
    }

    private ArkConfig resolveConfig(String scene) {
        String dbKey = settings.getRaw("ark_api_key", "");
        String dbBase = settings.getRaw("ark_base_url", "");
        String dbModel = settings.getRaw("ark_model", "");
        boolean enabled = settings.getBoolean("ark_enabled", envEnabled);

        String apiKey = !dbKey.isBlank() ? dbKey.trim() : envApiKey;
        String keySource = !dbKey.isBlank() ? "db" : (!envApiKey.isBlank() ? "env" : "none");

        String baseUrl = !dbBase.isBlank() ? normalizeBaseUrl(dbBase.trim()) : normalizeBaseUrl(envBaseUrl);
        String baseSource = !dbBase.isBlank() ? "db" : (!envBaseUrl.isBlank() ? "env" : "none");

        String model = !dbModel.isBlank() ? dbModel.trim() : envModel;
        String modelSource = !dbModel.isBlank() ? "db" : (!envModel.isBlank() ? "env" : "none");

        ArkConfig config = new ArkConfig(enabled, apiKey, baseUrl, model);
        log.info(
                "AI config resolve scene={} enabled={} apiKey={}({}) baseUrl={}({}) model={}({})",
                scene,
                enabled,
                maskSecret(apiKey),
                keySource,
                blankAsDash(baseUrl),
                baseSource,
                blankAsDash(model),
                modelSource);
        return config;
    }

    private String buildPrompt(Task task, String proposal) {
        String template = settings.getRaw("ra_challenge_ai_prompt", "");
        if (template == null || template.isBlank()) {
            template = DEFAULT_PROMPT;
        }
        return template
                .replace("{{title}}", nullToEmpty(task.getTitle()))
                .replace("{{techField}}", nullToEmpty(task.getTechField()))
                .replace("{{background}}", truncate(task.getBackground(), 2000))
                .replace("{{outputRequirement}}", truncate(task.getOutputRequirement(), 1000))
                .replace("{{techRequirement}}", truncate(task.getTechRequirement(), 1000))
                .replace("{{proposal}}", truncate(proposal, 6000));
    }

    private String chat(ArkConfig config, String userPrompt, String systemPrompt) throws Exception {
        if (config.baseUrl().isBlank()) {
            throw new IllegalStateException("Base URL 未配置");
        }
        String endpoint = config.baseUrl() + "/chat/completions";
        log.info("AI http POST {}", endpoint);
        Map<String, Object> body = Map.of(
                "model", config.model(),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                ),
                "temperature", 0.2
        );
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + config.apiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() >= 300) {
            throw new IllegalStateException("ARK HTTP " + response.statusCode() + ": " + truncate(response.body(), 300));
        }
        JsonNode root = mapper.readTree(response.body());
        return root.path("choices").path(0).path("message").path("content").asText("");
    }

    private JsonNode extractJson(String raw) throws Exception {
        String text = raw == null ? "" : raw.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            text = text.substring(start, end + 1);
        }
        return mapper.readTree(text);
    }

    static String feasibilityLabel(int feasibility) {
        if (feasibility >= 70) {
            return "初步可行";
        }
        if (feasibility >= 40) {
            return "条件可行";
        }
        return "可行性存疑";
    }

    private int clamp(int value) {
        if (value < 0) {
            return value;
        }
        return Math.min(100, Math.max(0, value));
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    private String blankAsDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String maskSecret(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= 4) {
            return "****";
        }
        return "****" + trimmed.substring(trimmed.length() - 4);
    }

    /** Normalize admin-provided base URL; never invent a product default. */
    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "";
        }
        String normalized = baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        // Agent Plan console often shows .../api/plan; chat completions live under /api/plan/v1.
        if (normalized.endsWith("/api/plan")) {
            normalized = normalized + "/v1";
            log.info("AI baseUrl normalized: appended /v1 -> {}", normalized);
        }
        return normalized;
    }

    private record ArkConfig(boolean enabled, String apiKey, String baseUrl, String model) {
    }

    public record ArkTestResult(boolean ok, String message, Long latencyMs, String sample) {
        public static ArkTestResult ok(String message, long latencyMs, String sample) {
            return new ArkTestResult(true, message, latencyMs, sample);
        }

        public static ArkTestResult fail(String message) {
            return new ArkTestResult(false, message, null, null);
        }

        public static ArkTestResult fail(String message, long latencyMs) {
            return new ArkTestResult(false, message, latencyMs, null);
        }
    }
}
