package com.jscreator.content.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jscreator.content.mapper.ArticleMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AI 文章导读，对齐 Express 版 legacy-utils/ai-summary + llm：
 * <ul>
 *   <li>未配置 {@code DEEPSEEK_API_KEY} → 直接算失败（原版 getClient 抛「LLM 未配置」），返回 null；
 *       调用方据此给「AI 总结生成失败，请重试或检查 LLM 配置」（与原版一致）</li>
 *   <li>配置了就按 OpenAI 兼容协议打 {@code {DEEPSEEK_BASE_URL}/chat/completions}，解析 JSON 后写回 article.ai_summary</li>
 *   <li>发布/更新文章时是「不阻塞响应」的后台调用（失败只记日志）</li>
 * </ul>
 */
@Service
public class AiSummaryService {

    private static final Logger log = LoggerFactory.getLogger(AiSummaryService.class);

    private static final String SYSTEM_PROMPT = """
            你是一位资深技术编辑和内容评审。请阅读用户提供的文章，输出一份结构化的 AI 导读。要求：
            1. summary：一句话概括文章主旨（30 字内）
            2. key_points：3-5 条核心要点，每条 20-40 字，使用列表
            3. analysis：3-5 条分析评估（文章结构、内容深度、代码质量、优点、可改进处），每条 20-40 字
            4. advice：2-3 条读者建议（适合谁读、怎么读更有效、可延伸的学习方向），每条 20-40 字

            只输出 JSON，不要输出 markdown 代码块或其他文字。JSON 格式：
            {"summary":"...","key_points":["..."],"analysis":["..."],"advice":["..."]}""";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ArticleMapper articleMapper;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

    /** 原版是 fire-and-forget，这里同样放后台线程，不拖慢发布/更新接口。 */
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ai-summary");
        t.setDaemon(true);
        return t;
    });

    public AiSummaryService(ArticleMapper articleMapper,
                            @Value("${DEEPSEEK_API_KEY:}") String apiKey,
                            @Value("${DEEPSEEK_BASE_URL:https://api.deepseek.com}") String baseUrl,
                            @Value("${DEEPSEEK_MODEL:deepseek-chat}") String model) {
        this.articleMapper = articleMapper;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
    }

    /** 发布/更新文章后的后台生成（原版 .then().catch() 吞掉异常）。 */
    public void summarizeAndSaveAsync(Object articleId, String title, String content) {
        executor.execute(() -> summarizeAndSave(articleId, title, content));
    }

    /** 生成并保存；失败返回 null（原版 summarizeAndSave 的语义）。 */
    public Map<String, Object> summarizeAndSave(Object articleId, String title, String content) {
        try {
            Map<String, Object> result = generate(articleId, title, content);
            articleMapper.updateAiSummary(articleId, JSON.writeValueAsString(result));
            return result;
        } catch (Exception e) {
            log.error("文章 {} AI 总结生成失败: {}", articleId, e.getMessage());
            return null;
        }
    }

    private Map<String, Object> generate(Object articleId, String title, String content) throws Exception {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("LLM 未配置：请在 .env 设置 DEEPSEEK_API_KEY");
        }
        String text = "【文章标题】" + (title == null ? "" : title) + "\n\n【文章内容】\n"
                + slice(content == null ? "" : content, 8000);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
        messages.add(Map.of("role", "user", "content", text));
        payload.put("messages", messages);
        payload.put("temperature", 0.4);
        payload.put("max_tokens", 1200);

        String url = baseUrl.replaceAll("/+$", "") + "/chat/completions";
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(payload), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("LLM 返回 " + response.statusCode());
        }
        Map<?, ?> body = JSON.readValue(response.body(), Map.class);
        String raw = "";
        Object choices = body.get("choices");
        if (choices instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> first) {
            Object message = first.get("message");
            if (message instanceof Map<?, ?> msg && msg.get("content") != null) {
                raw = String.valueOf(msg.get("content"));
            }
        }
        return parseSummary(raw);
    }

    /** 对齐原版 generateArticleSummary 的解析与截断规则。 */
    private static Map<String, Object> parseSummary(String raw) throws Exception {
        String cleaned = raw.replaceAll("(?is)^```(?:json)?\\s*", "").replaceAll("(?s)```\\s*$", "").trim();
        Map<?, ?> obj;
        try {
            obj = JSON.readValue(cleaned, Map.class);
        } catch (Exception e) {
            log.error("AI 总结 JSON 解析失败，原文: {}", slice(cleaned, 200));
            throw new IllegalStateException("AI 总结解析失败");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("summary", slice(obj.get("summary") == null ? "" : String.valueOf(obj.get("summary")), 200));
        out.put("key_points", strList(obj.get("key_points"), 6));
        out.put("analysis", strList(obj.get("analysis"), 6));
        out.put("advice", strList(obj.get("advice"), 4));
        return out;
    }

    private static List<String> strList(Object v, int max) {
        List<String> out = new ArrayList<>();
        if (v instanceof List<?> list) {
            for (Object o : list) {
                if (out.size() >= max) {
                    break;
                }
                out.add(String.valueOf(o));
            }
        }
        return out;
    }

    private static String slice(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
