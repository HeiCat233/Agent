package com.webagent.skill.apk;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Stage 2 索引（管线基础）：遍历 workdir 下 smali 目录的全部 .smali 文件，建 SQLite 索引。
 * 表：classes / methods / strings / api_calls。方法行号=smali 文件内物理行号
 * （片段提取与引用回验的基准）。baksmali 库方案保留（ADR-008，dex 级重建时启用）。
 */
@Component
public class SmaliIndexer {

    private static final Pattern P_CLASS = Pattern.compile("^\\.class[^L]*L([^;]+);");
    private static final Pattern P_METHOD = Pattern.compile("^\\.method\\s+(.*)$");
    private static final Pattern P_STRING = Pattern.compile("const-string(?:/jumbo)?\\s+\\S+,\\s+\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern P_INVOKE = Pattern.compile("invoke-\\w+(?:/range)?\\s+\\{[^}]*\\},\\s+(\\S+)");

    public record IndexedMethod(long id, String className, String name, String descriptor,
                                String file, int lineStart, int lineEnd) {}

    public Path buildIndex(Path workdir, Path dbFile) throws IOException, SQLException {
        Files.createDirectories(dbFile.getParent());
        Path db = dbFile.toAbsolutePath();
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db)) {
            schema(c);
            c.setAutoCommit(false);
            try (Stream<Path> smali = Files.walk(workdir)) {
                smali.filter(p -> p.toString().endsWith(".smali")).forEach(p -> indexFile(c, p));
            }
            c.commit();
        }
        return db;
    }

    public Connection open(Path db) throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath());
    }

    private void schema(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.executeUpdate("""
                DROP TABLE IF EXISTS classes; DROP TABLE IF EXISTS methods;
                DROP TABLE IF EXISTS strings; DROP TABLE IF EXISTS api_calls;""");
            st.executeUpdate("CREATE TABLE classes(id INTEGER PRIMARY KEY, name TEXT UNIQUE, smali_file TEXT)");
            st.executeUpdate("""
                CREATE TABLE methods(
                  id INTEGER PRIMARY KEY, class_name TEXT, name TEXT, signature TEXT,
                  file TEXT, line_start INTEGER, line_end INTEGER)""");
            st.executeUpdate("CREATE TABLE strings(method_id INTEGER, value TEXT)");
            st.executeUpdate("CREATE TABLE api_calls(method_id INTEGER, signature TEXT)");
            st.executeUpdate("CREATE INDEX idx_strings_v ON strings(value)");
            st.executeUpdate("CREATE INDEX idx_methods_c ON methods(class_name, name)");
        }
    }

    /** 单文件状态机：.class 换类；.method 开方法（立即落库拿 id）；内容归属当前方法；.end method 收口行号。 */
    private void indexFile(Connection c, Path file) {
        try {
            List<String> lines = Files.readAllLines(file);
            String relFile = file.toString();
            String currentClass = null;
            long methodId = -1;
            int methodStart = -1;
            String mSignature = null;

            try (PreparedStatement insClass = c.prepareStatement("INSERT OR IGNORE INTO classes(name,smali_file) VALUES(?,?)");
                 PreparedStatement insMethod = c.prepareStatement(
                         "INSERT INTO methods(class_name,name,signature,file,line_start,line_end) VALUES(?,?,?,?,?,?)",
                         Statement.RETURN_GENERATED_KEYS);
                 PreparedStatement updMethod = c.prepareStatement("UPDATE methods SET line_end=? WHERE id=?");
                 PreparedStatement insStr = c.prepareStatement("INSERT INTO strings(method_id,value) VALUES(?,?)");
                 PreparedStatement insApi = c.prepareStatement("INSERT INTO api_calls(method_id,signature) VALUES(?,?)")) {

                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    int fileLine = i + 1;

                    Matcher mc = P_CLASS.matcher(line);
                    if (mc.find()) {
                        currentClass = mc.group(1).replace('/', '.');   // 统一点分形式（展示/导出友好）
                        insClass.setString(1, currentClass);
                        insClass.setString(2, relFile);
                        insClass.executeUpdate();
                        continue;
                    }
                    if (line.trim().startsWith(".method")) {
                        closeMethod(updMethod, methodId, methodStart, fileLine - 1);
                        mSignature = line.trim().substring(".method".length()).trim();
                        // 方法名 = 签名中 '(' 前最后一个空格后的 token（剥离 public/static 等修饰符）
                        String head = mSignature.split("\\(")[0].trim();
                        String name = head.contains(" ") ? head.substring(head.lastIndexOf(' ') + 1) : head;
                        insMethod.setString(1, currentClass);
                        insMethod.setString(2, name);
                        insMethod.setString(3, mSignature);
                        insMethod.setString(4, relFile);
                        insMethod.setInt(5, fileLine);
                        insMethod.setInt(6, fileLine);
                        insMethod.executeUpdate();
                        try (ResultSet keys = insMethod.getGeneratedKeys()) {
                            methodId = keys.next() ? keys.getLong(1) : -1;
                        }
                        methodStart = fileLine;
                        continue;
                    }
                    if (line.trim().startsWith(".end method")) {
                        closeMethod(updMethod, methodId, methodStart, fileLine);
                        methodId = -1;
                        continue;
                    }
                    if (methodId < 0) continue;

                    Matcher ms = P_STRING.matcher(line);
                    if (ms.find()) { insStr.setLong(1, methodId); insStr.setString(2, ms.group(1)); insStr.executeUpdate(); }

                    Matcher mi = P_INVOKE.matcher(line);
                    if (mi.find()) { insApi.setLong(1, methodId); insApi.setString(2, mi.group(1)); insApi.executeUpdate(); }
                }
                closeMethod(updMethod, methodId, methodStart, lines.size());
            }
        } catch (Exception e) {
            throw new IllegalStateException("索引失败 " + file + ": " + e.getMessage(), e);
        }
    }

    private void closeMethod(PreparedStatement upd, long methodId, int start, int end) throws SQLException {
        if (methodId >= 0 && end >= start) {
            upd.setInt(1, end);
            upd.setLong(2, methodId);
            upd.executeUpdate();
        }
    }

    // ===== 查询方法（Stage 3 定位的数据源）=====

    /** 字符串反查：LIKE 匹配 -> 所在方法（≤limit）。 */
    public List<IndexedMethod> findByString(Connection c, String like) throws SQLException {
        List<IndexedMethod> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT DISTINCT m.id,m.class_name,m.name,m.signature,m.file,m.line_start,m.line_end
                FROM strings s JOIN methods m ON m.id=s.method_id
                WHERE s.value LIKE ? LIMIT ?""")) {
            ps.setString(1, like);
            ps.setInt(2, Contracts_MAX);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) out.add(row(rs)); }
        }
        return out;
    }

    /** API 指纹反查。 */
    public List<IndexedMethod> findByApi(Connection c, String apiLike) throws SQLException {
        List<IndexedMethod> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT DISTINCT m.id,m.class_name,m.name,m.signature,m.file,m.line_start,m.line_end
                FROM api_calls a JOIN methods m ON m.id=a.method_id
                WHERE a.signature LIKE ? LIMIT ?""")) {
            ps.setString(1, apiLike);
            ps.setInt(2, Contracts_MAX);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) out.add(row(rs)); }
        }
        return out;
    }

    /** 入口点/类名定位：class_name LIKE。 */
    public List<IndexedMethod> findByClass(Connection c, String classLike) throws SQLException {
        List<IndexedMethod> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT id,class_name,name,signature,file,line_start,line_end
                FROM methods WHERE class_name LIKE ? LIMIT ?""")) {
            ps.setString(1, classLike);
            ps.setInt(2, Contracts_MAX);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) out.add(row(rs)); }
        }
        return out;
    }

    /** 注释树/结构用：包名 -> 类计数。 */
    public MapCount packageCounts(Connection c) throws SQLException {
        MapCount mc = new MapCount();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT substr(class_name,1,instr(substr(class_name,2),'/')+1) pkg, COUNT(*) n " +
                     "FROM classes GROUP BY pkg ORDER BY n DESC LIMIT 50")) {
            while (rs.next()) mc.add(rs.getString(1), rs.getInt(2));
        }
        return mc;
    }

    private static IndexedMethod row(ResultSet rs) throws SQLException {
        return new IndexedMethod(rs.getLong(1), rs.getString(2), rs.getString(3),
                rs.getString(4), rs.getString(5), rs.getInt(6), rs.getInt(7));
    }

    private static final int Contracts_MAX = 5;

    /** 简易计数容器（包级注释用）。 */
    public static final class MapCount {
        private final List<String[]> items = new ArrayList<>();
        public void add(String k, int v) { items.add(new String[]{k, String.valueOf(v)}); }
        public List<String[]> items() { return items; }
    }
}
