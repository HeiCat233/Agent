package com.webagent.skill.apk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Stage 1 解码（FR-02/03）：调用 apktool 解包 APK。
 * 开发期：宿主机 `java -jar apktool.jar d` 直调（本机 JDK17 与 apktool 同源）；
 * 正式期：同一接口替换为 Docker 沙箱执行（ADR-007，仅替换实现不改签名）。
 * 不可信输入防护：超时销毁进程；workdir 使用 targetId 隔离；正式期叠加无网络/只读/限额。
 */
@Component
public class ApktoolRunner {

    private static final Logger log = LoggerFactory.getLogger(ApktoolRunner.class);
    private final String jarPath;
    private final long timeoutSeconds;

    public ApktoolRunner(@Value("${webagent.apktool.jar-path:D:/dev/apktool/apktool.jar}") String jarPath,
                         @Value("${webagent.sandbox.timeout-seconds:120}") long timeoutSeconds) {
        this.jarPath = jarPath;
        this.timeoutSeconds = timeoutSeconds;
    }

    /** 解码 APK -> workdir；失败抛 IOException（上层转 UNCERTAIN/TOOL_FAILURE）。 */
    public Path decode(Path apk, Path workdir) throws IOException, InterruptedException {
        Files.createDirectories(workdir);
        Path jar = Path.of(jarPath);
        if (!Files.isRegularFile(jar)) {
            throw new IOException("apktool.jar 不存在: " + jar + "（见 RUNNING.md 环境准备）");
        }
        Process p = new ProcessBuilder(
                "java", "-jar", jar.toString(), "d", "-f", "-o", workdir.toString(), apk.toString())
                .redirectErrorStream(true)
                .start();
        String output = new String(p.getInputStream().readAllBytes());
        boolean ok = p.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!ok) {
            p.destroyForcibly();
            throw new IOException("apktool 超时（>" + timeoutSeconds + "s）");
        }
        if (p.exitValue() != 0) {
            log.warn("apktool 退出码 {}: {}", p.exitValue(), tail(output));
            throw new IOException("apktool 解码失败（exit=" + p.exitValue() + "）");
        }
        return workdir;
    }

    /** 目录树（FR-02，Depth≤3），供 DIRECTORY_STRUCTURE 使用。 */
    public String directoryTree(Path workdir, int maxDepth) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (var walk = Files.walk(workdir, maxDepth)) {
            walk.sorted().forEach(p -> {
                int depth = workdir.relativize(p).getNameCount();
                if (p.equals(workdir)) { sb.append(workdir.getFileName()).append("/\n"); return; }
                sb.append("  ".repeat(depth)).append(p.getFileName())
                  .append(Files.isDirectory(p) ? "/" : "").append('\n');
            });
        }
        return sb.toString();
    }

    /** 组件清单（FR-03）：从 AndroidManifest.xml 提取 activity/service/receiver/provider 名称。 */
    public String componentList(Path manifest) throws IOException {
        if (!Files.isRegularFile(manifest)) return "（未找到 AndroidManifest.xml）";
        String xml = Files.readString(manifest);
        StringBuilder sb = new StringBuilder("AndroidManifest 组件清单：\n");
        component(sb, xml, "activity");
        component(sb, xml, "service");
        component(sb, xml, "receiver");
        component(sb, xml, "provider");
        return sb.toString();
    }

    private static void component(StringBuilder sb, String xml, String tag) {
        var m = java.util.regex.Pattern
                .compile("<" + tag + "[^>]*android:name=\"([^\"]+)\"")
                .matcher(xml);
        while (m.find()) sb.append("  ").append(tag).append(": ").append(m.group(1)).append('\n');
    }

    private static String tail(String s) {
        return s.length() <= 400 ? s : s.substring(s.length() - 400);
    }

    @SuppressWarnings("unused")
    private static Duration unused() { return Duration.ZERO; }   // 保留占位：正式期接 Docker exec 超时
}
