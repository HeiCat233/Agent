package com.webagent.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 逆向学习辅助Agent —— 共享核心启动类（ADR-008：Spring Boot 3）。
 *
 * 部署形态（ADR-007）：本进程为可信面，仅监听本机；不可信面（apktool 解析）
 * 由 Docker 沙箱隔离（application.yml: webagent.sandbox）。
 */
@SpringBootApplication
public class AgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(AgentApplication.class, args);
    }
}
