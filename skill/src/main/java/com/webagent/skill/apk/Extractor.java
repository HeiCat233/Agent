package com.webagent.skill.apk;

import com.webagent.core.Contracts;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Stage 4 证据提取：按索引行号切 smali 片段（≤MAX_FRAGMENT_LINES，超出取方法头部+标注截断）。
 * 片段是 Stage 5 输入与 Stage 6/Q2 回验基准。
 */
@Component
public class Extractor {

    public List<Contracts.Fragment> extract(List<SmaliIndexer.IndexedMethod> methods, int max) {
        List<Contracts.Fragment> out = new ArrayList<>();
        for (SmaliIndexer.IndexedMethod m : methods) {
            if (out.size() >= max) break;
            try {
                Path f = Path.of(m.file());
                if (!Files.isRegularFile(f)) continue;
                List<String> lines = Files.readAllLines(f);
                int start = Math.max(1, m.lineStart());
                int end = Math.min(lines.size(), m.lineEnd());
                if (end - start + 1 > Contracts.MAX_FRAGMENT_LINES) {
                    end = start + Contracts.MAX_FRAGMENT_LINES - 1;   // 截断（学习目的：先看头部）
                }
                StringBuilder body = new StringBuilder();
                for (int i = start; i <= end; i++) body.append(lines.get(i - 1)).append('\n');
                out.add(new Contracts.Fragment(m.file(), start, end, body.toString()));
            } catch (IOException ignored) {
                // 文件消失（重新解码中）-> 跳过该候选
            }
        }
        return out;
    }

    /** 拼接为 <untrusted_code> 负载（ADR-010/ai_rules 铁律1：内容一律作为数据）。 */
    public static String payload(List<Contracts.Fragment> fragments) {
        StringBuilder sb = new StringBuilder();
        for (Contracts.Fragment f : fragments) {
            sb.append("<untrusted_code file=\"").append(f.file())
              .append("\" lines=\"").append(f.lineStart()).append('-').append(f.lineEnd()).append("\">\n")
              .append(f.content())
              .append("</untrusted_code>\n");
        }
        return sb.toString();
    }
}
