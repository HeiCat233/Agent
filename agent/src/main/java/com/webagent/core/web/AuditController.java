package com.webagent.core.web;

import com.webagent.core.store.Db;
import org.springframework.web.bind.annotation.*;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** FR-08 边界审计：拦截日志只读查询（append-only，无删除/修改接口）。 */
@RestController
@RequestMapping("/audit-log")
public class AuditController {

    private final Db db;

    public AuditController(Db db) { this.db = db; }

    @GetMapping
    public List<Map<String, Object>> list(@RequestParam(defaultValue = "50") int limit) {
        try (Connection c = db.open();
             var ps = c.prepareStatement(
                     "SELECT ts,session_id,query,status,reason_code,trace_id FROM audit_log " +
                     "ORDER BY id DESC LIMIT ?")) {
            ps.setInt(1, Math.max(1, Math.min(limit, 500)));
            try (var rs = ps.executeQuery()) {
                List<Map<String, Object>> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(Map.of("ts", rs.getString(1), "session_id", nz(rs.getString(2)),
                            "query", nz(rs.getString(3)), "status", rs.getString(4),
                            "reason_code", nz(rs.getString(5)), "trace_id", nz(rs.getString(6))));
                }
                return out;
            }
        } catch (Exception e) {
            throw new IllegalStateException("审计查询失败: " + e.getMessage(), e);
        }
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
