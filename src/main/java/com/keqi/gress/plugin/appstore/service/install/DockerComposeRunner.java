package com.keqi.gress.plugin.appstore.service.install;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 运行 docker compose（支持 DinD：透传 DOCKER_HOST/DOCKER_TLS_VERIFY/DOCKER_CERT_PATH 等环境变量）。
 */
public class DockerComposeRunner {
    private static final Log log = LogFactory.get(DockerComposeRunner.class);

    public ResultExec execComposeUp(Path composeFile, String projectName) {
        // docker compose -f xxx -p name up -d
        List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        cmd.add("compose");
        cmd.add("-f");
        cmd.add(composeFile.toAbsolutePath().toString());
        if (projectName != null && !projectName.isBlank()) {
            cmd.add("-p");
            cmd.add(projectName);
        }
        cmd.add("up");
        cmd.add("-d");
        return exec(cmd, Duration.ofMinutes(5));
    }

    public boolean dockerAvailable() {
        ResultExec r = exec(List.of("docker", "info"), Duration.ofSeconds(10));
        return r.exitCode == 0;
    }

    public boolean dockerComposeAvailable() {
        ResultExec r = exec(List.of("docker", "compose", "version"), Duration.ofSeconds(10));
        return r.exitCode == 0;
    }

    private ResultExec exec(List<String> cmd, Duration timeout) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);

            // 透传环境变量以支持 DinD
            Map<String, String> env = pb.environment();
            passThrough(env, "DOCKER_HOST");
            passThrough(env, "DOCKER_TLS_VERIFY");
            passThrough(env, "DOCKER_CERT_PATH");
            passThrough(env, "DOCKER_CONTEXT");

            Process p = pb.start();
            StringBuilder out = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    out.append(line).append("\n");
                }
            }

            boolean finished = p.waitFor(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!finished) {
                p.destroyForcibly();
                return new ResultExec(124, out + "\n<timeout>");
            }
            int code = p.exitValue();
            if (code != 0) {
                log.warn("命令执行失败: cmd={}, exitCode={}, output={}", cmd, code, out);
            } else {
                log.debug("命令执行成功: cmd={}, output={}", cmd, out);
            }
            return new ResultExec(code, out.toString());
        } catch (Exception e) {
            return new ResultExec(1, "exec error: " + e.getMessage());
        }
    }

    private void passThrough(Map<String, String> env, String key) {
        String v = System.getenv(key);
        if (v != null && !v.isBlank()) {
            env.put(key, v);
        }
    }

    public static class ResultExec {
        public final int exitCode;
        public final String output;

        public ResultExec(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }
    }
}











