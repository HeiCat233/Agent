package com.webagent.core.store;

import com.webagent.core.Contracts;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.SQLException;

/** 会话预算与累积冻结（P5/P6）。拦截日志写入同在此（append-only）。 */
@Component
public class SessionStore {

    private final Db db;

    public SessionStore(Db db) { this.db = db; }

    /** P6：同 session 被拒 >= SESSION_REJECT_LIMIT 次后冻结。 */
    public boolean isFrozen(String sessionId) {
        try (Connection c = db.open();
             var ps = c.prepareStatement("SELECT frozen FROM sessions WHERE session_id=?")) {
            ps.setString(1, sessionId);
            try (var rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) == 1;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("会话查询失败: " + e.getMessage(), e);
        }
    }

    public void recordReject(String sessionId, String query, Contracts.Status status,
                             Contracts.ReasonCode reason, String traceId) {
        try (Connection c = db.open()) {
            c.setAutoCommit(false);
            try (var up = c.prepareStatement("""
                    INSERT INTO sessions(session_id) VALUES(?)
                    ON CONFLICT(session_id) DO NOTHING""")) {
                up.setString(1, sessionId);
                up.executeUpdate();
            }
            try (var up = c.prepareStatement(
                    "UPDATE sessions SET reject_count=reject_count+1, frozen=(reject_count+1>=?)," +
                    " updated_at=datetime('now','localtime') WHERE session_id=?")) {
                up.setInt(1, Contracts.SESSION_REJECT_LIMIT);
                up.setString(2, sessionId);
                up.executeUpdate();
            }
            appendAudit(c, sessionId, query, status, reason, traceId);
            c.commit();
        } catch (SQLException e) {
            throw new IllegalStateException("拒绝记录写入失败: " + e.getMessage(), e);
        }
    }

    /** P5：按步消耗预算。 */
    public void consume(String sessionId, int steps) {
        try (Connection c = db.open();
             var up = c.prepareStatement("""
                    INSERT INTO sessions(session_id,used_budget) VALUES(?,?)
                    ON CONFLICT(session_id) DO UPDATE SET
                      used_budget=used_budget+excluded.used_budget,
                      updated_at=datetime('now','localtime')""")) {
            up.setString(1, sessionId);
            up.setInt(2, steps);
            up.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("预算消耗写入失败: " + e.getMessage(), e);
        }
    }

    /** 审计：一切 REJECTED/BLOCKED/UNCERTAIN 事件（含非拒绝路径的 audit 标记）留痕。 */
    public void audit(String sessionId, String query, Contracts.Status status,
                      Contracts.ReasonCode reason, String traceId) {
        try (Connection c = db.open()) {
            appendAudit(c, sessionId, query, status, reason, traceId);
        } catch (SQLException e) {
            throw new IllegalStateException("审计写入失败: " + e.getMessage(), e);
        }
    }

    private void appendAudit(Connection c, String sessionId, String query,
                             Contracts.Status status, Contracts.ReasonCode reason,
                             String traceId) throws SQLException {
        try (var ps = c.prepareStatement(
                "INSERT INTO audit_log(session_id,query,status,reason_code,trace_id) VALUES(?,?,?,?,?)")) {
            ps.setString(1, sessionId);
            ps.setString(2, query);
            ps.setString(3, status.name());
            ps.setString(4, reason == null ? null : reason.name());
            ps.setString(5, traceId);
            ps.executeUpdate();
        }
    }
}
