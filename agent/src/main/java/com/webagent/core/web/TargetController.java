package com.webagent.core.web;

import com.webagent.core.store.WhitelistStore;
import com.webagent.core.gate.PreGate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** FR-01 目标注册与白名单管理。 */
@RestController
@RequestMapping("/targets")
public class TargetController {

    private final PreGate preGate;
    private final WhitelistStore whitelist;

    public TargetController(PreGate preGate, WhitelistStore whitelist) {
        this.preGate = preGate;
        this.whitelist = whitelist;
    }

    /** 注册目标（必须 authorization_ack=true，否则拒绝）。 */
    @PostMapping
    public Map<String, Object> register(@RequestBody Map<String, Object> body) {
        boolean ack = Boolean.TRUE.equals(body.get("authorization_ack"))
                || "true".equalsIgnoreCase(String.valueOf(body.get("authorization_ack")));
        if (!ack) {
            return Map.of("status", "REJECTED", "reason_code", "AUTH_MISSING",
                    "message", "缺少授权声明，无法注册");
        }
        String name = String.valueOf(body.getOrDefault("name", "unnamed"));
        String sourceTag = String.valueOf(body.getOrDefault("source_tag", "self-made"));
        String apkPath = String.valueOf(body.get("apk_path"));
        try {
            String id = preGate.registerTarget(name, sourceTag, apkPath, true);
            return Map.of("status", "CONFIRMED", "target_id", id);
        } catch (Exception e) {
            return Map.of("status", "REJECTED", "reason_code", "TOOL_FAILURE",
                    "message", "注册失败: " + e.getMessage());
        }
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return whitelist.listAll().stream()
                .map(t -> Map.<String, Object>of(
                        "target_id", t.targetId(), "name", t.name(),
                        "source_tag", t.sourceTag(), "apk_path", t.apkPath()))
                .toList();
    }

    @GetMapping("/{targetId}")
    public Map<String, Object> get(@PathVariable String targetId) {
        return whitelist.find(targetId)
                .<Map<String, Object>>map(t -> Map.of("target_id", t.targetId(), "name", t.name(),
                        "source_tag", t.sourceTag(), "apk_path", t.apkPath()))
                .orElseGet(() -> Map.of("status", "REJECTED", "reason_code", "UNAUTHORIZED_TARGET"));
    }
}
