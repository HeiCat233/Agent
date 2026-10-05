package com.webagent.skill.apk;

import com.webagent.core.Contracts;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stage 3 定位（方案 4.4）：自然语言问题 -> 候选方法（≤5）。
 * 三策略按序：a) 字符串反查（query 关键词/中文意图词 -> strings 表）
 * b) API 指纹（意图 -> API 模式库） c) 入口点（Activity/onCreate）。
 * 候选为空 -> 上层 UNCERTAIN/NO_CANDIDATE（禁止编造）。
 */
@Component
public class Locator {

    /** 中文/常见意图 -> API 指纹（smali 形态）。可由 audit 日志持续补充。 */
    private static final Map<String, List<String>> API_FINGERPRINTS = Map.of(
            "登录", List.of("Landroid/widget/EditText;", "MessageDigest", "equals"),
            "密码", List.of("MessageDigest", "Landroid/content/SharedPreferences;"),
            "口令", List.of("MessageDigest", "equals"),
            "加密", List.of("Ljavax/crypto/Cipher;", "Ljavax/crypto/Mac;", "SecretKey"),
            "存储", List.of("Landroid/content/SharedPreferences;", "openOrCreateDatabase"),
            "网络", List.of("Ljava/net/URL;", "HttpURLConnection", "OkHttpClient"),
            "deeplink", List.of("Landroid/net/Uri;", "getData", "onCreate"),
            "支付", List.of("IAB", "Landroid/app/Activity;->startActivityForResult")
    );

    public record Candidate(SmaliIndexer.IndexedMethod method, String strategy) {}

    public List<Candidate> locate(Connection c, String query) throws SQLException {
        LinkedHashMap<String, Candidate> ranked = new LinkedHashMap<>();   // key=class#method 保持顺序去重

        // a) 字符串反查：query 中较长的词元 + 中文意图词直接 LIKE
        for (String kw : keywords(query)) {
            put(ranked, indexer().findByString(c, "%" + escapeLike(kw) + "%"), "string:" + kw);
        }
        // b) API 指纹：中文意图词 -> API 模式
        for (Map.Entry<String, List<String>> e : API_FINGERPRINTS.entrySet()) {
            if (query.contains(e.getKey())) {
                for (String api : e.getValue()) {
                    put(ranked, indexer().findByApi(c, "%" + escapeLike(api) + "%"), "api:" + e.getKey());
                }
            }
        }
        // c) 入口点：Activity/onCreate
        if (ranked.isEmpty() || query.toLowerCase().contains("入口") || query.toLowerCase().contains("activity")) {
            put(ranked, indexer().findByClass(c, "%Activity;"), "entrypoint");
        }
        return new ArrayList<>(ranked.values());
    }

    private void put(Map<String, Candidate> ranked, List<SmaliIndexer.IndexedMethod> methods, String strategy) {
        for (SmaliIndexer.IndexedMethod m : methods) {
            ranked.putIfAbsent(m.className() + "#" + m.name(), new Candidate(m, strategy));
            if (ranked.size() >= Contracts.MAX_CANDIDATES) return;
        }
    }

    /** 提取长度≥2 的词元（中英文混合），过滤停用词；中文整句也作为一个词元尝试。 */
    static List<String> keywords(String query) {
        List<String> out = new ArrayList<>();
        if (query == null) return out;
        for (String tok : query.split("[\\s，。？?！!、,.()（）\"']+")) {
            if (tok.length() >= 2 && tok.length() <= 60) out.add(tok);
        }
        return out;
    }

    static String escapeLike(String s) {
        return s.replace("'", "''");
    }

    // Seam：便于测试注入 indexer（构造注入亦可，保持 Spring 单例时用静态委托）
    private SmaliIndexer indexer() { return new SmaliIndexer(); }
}
