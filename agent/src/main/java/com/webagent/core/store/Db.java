package com.webagent.core.store;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * SQLite 连接与 schema 管理（agent/data/agent.db；见 .gitignore，运行时数据不入库）。
 *
 * 表：targets（白名单，授权声明必填）/ sessions（预算与会话冻结）/ audit_log（append-only 拦截日志）
 * 索引库（smali）在 skill-apk 模块单独建库（skill/data/index.db）。
 */
@Component
public class Db implements AutoCloseable {

    private final String url;

    public Db(@Value("${webagent.data-dir:agent/data}") String dataDir) {
        try {
            Files.createDirectories(Path.of(dataDir));
            this.url = "jdbc:sqlite:" + Path.of(dataDir, "agent.db").toAbsolutePath();
            initSchema();
        } catch (Exception e) {
            throw new IllegalStateException("SQLite 初始化失败: " + e.getMessage(), e);
        }
    }

    public Connection open() throws SQLException {
        Connection c = DriverManager.getConnection(url);
        try (Statement st = c.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL; PRAGMA foreign_keys=ON;");
        }
        return c;
    }

    private void initSchema() throws SQLException {
        try (Connection c = open(); Statement st = c.createStatement()) {
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS targets(
                  target_id   TEXT PRIMARY KEY,        -- SHA-256 前12位
                  name        TEXT NOT NULL,
                  source_tag  TEXT NOT NULL,           -- self-made / foss / teacher
                  sha256      TEXT NOT NULL,
                  apk_path    TEXT NOT NULL,
                  authorized  INTEGER NOT NULL DEFAULT 0,
                  created_at  TEXT DEFAULT (datetime('now','localtime'))
                )""");
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS sessions(
                  session_id  TEXT PRIMARY KEY,
                  used_budget INTEGER NOT NULL DEFAULT 0,
                  reject_count INTEGER NOT NULL DEFAULT 0,
                  frozen      INTEGER NOT NULL DEFAULT 0,
                  updated_at  TEXT DEFAULT (datetime('now','localtime'))
                )""");
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS audit_log(          -- append-only：仅允许 INSERT/SELECT
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  ts TEXT DEFAULT (datetime('now','localtime')),
                  session_id TEXT, query TEXT,
                  status TEXT NOT NULL, reason_code TEXT, trace_id TEXT
                )""");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_audit_ts ON audit_log(ts)");
        }
    }

    @Override
    public void close() {}
}
