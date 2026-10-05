package com.webagent.core.web;

import com.webagent.core.store.Db;
import org.springframework.web.bind.annotation.*;

import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;

/** 会话状态查询（预算/冻结）。 */
@RestController
@RequestMapping("/sessions")
public class SessionController {

    private final Db db;

    public SessionController(Db db) { this.db = db; }

    @GetMapping("/{sessionId}")
    public Map<String, Object> get(@PathVariable String sessionId) {
        try (Connection c = db.open();
             var ps = c.prepareStatement(
                     "SELECT used_budget,reject_count,frozen,updated_at FROM sessions WHERE session_id=?")) {
            ps.setString(1, sessionId);
            try (var rs = ps.executeQuery()) {
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("session_id", sessionId);
                if (rs.next()) {
                    out.put("used_budget", rs.getInt(1));
                    out.put("reject_count", rs.getInt(2));
                    out.put("frozen", rs.getInt(3) == 1);
                    out.put("updated_at", rs.getString(4));
                } else {
                    out.put("used_budget", 0);
                    out.put("reject_count", 0);
                    out.put("frozen", false);
                }
                return out;
            }
        } catch (Exception e) {
            throw new IllegalStateException("会话查询失败: " + e.getMessage(), e);
        }
    }
}
