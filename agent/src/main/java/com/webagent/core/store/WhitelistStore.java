package com.webagent.core.store;

import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/** 白名单存储（PreGate P1/P2 的数据源）。target_id = 样本 SHA-256 前 12 位。 */
@Component
public class WhitelistStore {

    private final Db db;

    public WhitelistStore(Db db) { this.db = db; }

    /** 注册新目标：必须携带授权声明（P2）。返回 target_id。 */
    public String register(String name, String sourceTag, String apkPath, boolean authorized) {
        if (!authorized) {
            throw new IllegalArgumentException("缺少授权声明，拒绝注册（ADR-001 P2）");
        }
        String sha256 = sha256Of(apkPath);
        String targetId = sha256.substring(0, 12);
        try (Connection c = db.open();
             var ps = c.prepareStatement("""
                INSERT INTO targets(target_id,name,source_tag,sha256,apk_path,authorized)
                VALUES(?,?,?,?,?,1)
                ON CONFLICT(target_id) DO UPDATE SET name=excluded.name, apk_path=excluded.apk_path""")) {
            ps.setString(1, targetId);
            ps.setString(2, name);
            ps.setString(3, sourceTag);
            ps.setString(4, sha256);
            ps.setString(5, apkPath);
            ps.executeUpdate();
            return targetId;
        } catch (SQLException e) {
            throw new IllegalStateException("白名单写入失败: " + e.getMessage(), e);
        }
    }

    /** 查询（含 P1 规范化：trim/小写/去路径分隔，防 T3 绕过）。 */
    public Optional<Target> find(String targetId) {
        String norm = PreGateNormalizer.normalize(targetId);
        try (Connection c = db.open();
             var ps = c.prepareStatement(
                     "SELECT target_id,name,source_tag,apk_path FROM targets WHERE target_id=?")) {
            ps.setString(1, norm);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new Target(rs.getString(1), rs.getString(2),
                        rs.getString(3), rs.getString(4)));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("白名单查询失败: " + e.getMessage(), e);
        }
    }

    /** 全量列表（前端白名单页）。 */
    public List<Target> listAll() {
        try (Connection c = db.open();
             var ps = c.prepareStatement("SELECT target_id,name,source_tag,apk_path FROM targets ORDER BY created_at DESC");
             ResultSet rs = ps.executeQuery()) {
            List<Target> out = new java.util.ArrayList<>();
            while (rs.next()) out.add(new Target(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)));
            return out;
        } catch (SQLException e) {
            throw new IllegalStateException("白名单列表失败: " + e.getMessage(), e);
        }
    }

    public record Target(String targetId, String name, String sourceTag, String apkPath) {}

    static String sha256Of(String path) {
        try {
            var md = MessageDigest.getInstance("SHA-256");
            try (var in = java.nio.file.Files.newInputStream(java.nio.file.Path.of(path))) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException | java.io.IOException e) {
            throw new IllegalStateException("SHA-256 计算失败: " + e.getMessage(), e);
        }
    }

    /** P1 规范化：trim/小写/去路径分隔符（防大小写/路径变体绕过）。 */
    public final class PreGateNormalizer {
        public static String normalize(String id) {
            return id == null ? "" : id.trim().toLowerCase().replace("\\", "").replace("/", "");
        }
    }
}
