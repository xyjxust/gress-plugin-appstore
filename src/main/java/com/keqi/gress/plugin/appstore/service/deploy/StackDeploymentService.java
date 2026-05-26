package com.keqi.gress.plugin.appstore.service.deploy;

import com.alibaba.fastjson2.JSON;
import com.keqi.gress.plugin.appstore.dao.StackConfigDao;
import com.keqi.gress.plugin.appstore.dao.StackDeploymentDao;
import com.keqi.gress.plugin.appstore.dao.StackDeploymentLogDao;
import com.keqi.gress.plugin.appstore.dao.StackTargetDao;
import com.keqi.gress.plugin.appstore.domain.entity.StackConfigEntity;
import com.keqi.gress.plugin.appstore.domain.entity.StackDeploymentEntity;
import com.keqi.gress.plugin.appstore.domain.entity.StackDeploymentLogEntity;
import com.keqi.gress.plugin.appstore.domain.entity.StackTargetEntity;
import com.keqi.gress.plugin.appstore.service.NodeManagementService;
import com.keqi.gress.plugin.appstore.service.middleware.execution.ExecutionEnvironment;
import com.keqi.gress.plugin.appstore.service.middleware.execution.ExecutionEnvironmentFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Stack 部署执行器（MVP）
 *
 * 目标：
 * - 先把“创建部署任务”变成“可执行的远程 compose up + 健康检查 + 可选 Nginx reload”
 * - 细节（滚动/回滚/更丰富模板）后续迭代
 */
@Service
@Slf4j
public class StackDeploymentService {

    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Autowired(required = false)
    private NodeManagementService nodeManagementService;

    @Autowired(required = false)
    private StackConfigDao stackConfigDao;

    @Autowired(required = false)
    private StackTargetDao stackTargetDao;

    @Autowired(required = false)
    private StackDeploymentDao stackDeploymentDao;

    @Autowired(required = false)
    private StackDeploymentLogDao stackDeploymentLogDao;

    private final ExecutionEnvironmentFactory envFactory = new ExecutionEnvironmentFactory();

    public void startAsync(String deploymentId) {
        executor.submit(() -> runDeploymentSafe(deploymentId));
    }

    private void runDeploymentSafe(String deploymentId) {
        try {
            runDeployment(deploymentId);
        } catch (Exception e) {
            log.error("部署任务执行异常: deploymentId={}", deploymentId, e);
            tryMarkFailed(deploymentId, "部署执行异常: " + e.getMessage());
            appendLog(deploymentId, null, "EXCEPTION", "FAIL", e.toString());
        }
    }

    private void runDeployment(String deploymentId) {
        if (stackDeploymentDao == null || stackConfigDao == null || stackTargetDao == null || stackDeploymentLogDao == null) {
            throw new IllegalStateException("部署相关 DAO 未注入");
        }
        if (nodeManagementService == null) {
            throw new IllegalStateException("NodeManagementService 未注入");
        }

        StackDeploymentEntity dep = stackDeploymentDao.findByDeploymentId(deploymentId)
            .orElseThrow(() -> new IllegalArgumentException("deployment 不存在: " + deploymentId));

        StackConfigEntity stack = stackConfigDao.findByStackId(dep.getStackId())
            .orElseThrow(() -> new IllegalArgumentException("stack 不存在: " + dep.getStackId()));

        List<StackTargetEntity> targets = stackTargetDao.listByStackId(dep.getStackId());
        if (targets.isEmpty()) {
            throw new IllegalStateException("stack 未绑定任何 target: " + dep.getStackId());
        }

        markRunning(dep);
        appendLog(deploymentId, null, "START", "RUNNING", "deployment started");

        List<StackTargetEntity> webTargets = targets.stream()
            .filter(t -> t.getEnabled() == null || Boolean.TRUE.equals(t.getEnabled()))
            .filter(t -> t.getRoles() == null || t.getRoles().isBlank() || containsRole(t.getRoles(), "web"))
            .toList();

        if (webTargets.isEmpty()) {
            throw new IllegalStateException("未找到可用 web target");
        }

        // NGINX_ONLY：只更新入口 Nginx upstream / server，不执行任何节点部署
        if ("NGINX_ONLY".equalsIgnoreCase(dep.getMode())) {
            if (!Boolean.TRUE.equals(dep.getJoinNginx())) {
                appendLog(deploymentId, null, "NGINX_ONLY_SKIP", "SUCCESS", "joinNginx=false, nothing to do");
            } else {
                updateEntryNginx(dep, stack, webTargets);
            }
        } else {
            for (StackTargetEntity t : webTargets) {
                deployToTarget(dep, stack, t);
            }

            if (Boolean.TRUE.equals(dep.getJoinNginx())) {
                updateEntryNginx(dep, stack, webTargets);
            }
        }

        dep.setStatus("SUCCESS");
        dep.setEndedAt(System.currentTimeMillis());
        stackDeploymentDao.updateById(dep);
        appendLog(deploymentId, null, "DONE", "SUCCESS", "deployment success");
    }

    private void deployToTarget(StackDeploymentEntity dep, StackConfigEntity stack, StackTargetEntity target) {
        String deploymentId = dep.getDeploymentId();
        String nodeId = target.getNodeId();

        NodeManagementService.NodeInfo nodeInfo = nodeManagementService.getNode(nodeId)
            .orElseThrow(() -> new IllegalArgumentException("节点不存在: " + nodeId));
        if (!nodeInfo.isEnabled()) {
            appendLog(deploymentId, nodeId, "SKIP", "SUCCESS", "node disabled");
            return;
        }

        List<String> stepLogs = new ArrayList<>();
        ExecutionEnvironment env = envFactory.create(nodeInfo, line -> {
            // 尽量别太频繁落库；这里做一个简单累积
            stepLogs.add(line);
        });

        appendLog(deploymentId, nodeId, "CONNECT", "RUNNING", "env=" + env.getIdentifier());
        if (!env.isAvailable()) {
            throw new IllegalStateException("执行环境不可用: " + env.getIdentifier());
        }

        String runtime = resolveRuntimeDir(stack, dep.getStackId());
        String composeRemotePath = runtime + "/docker-compose.yml";
        String envRemotePath = runtime + "/.env";

        try {
            appendLog(deploymentId, nodeId, "RENDER", "RUNNING", "render compose/env");
            Path composeFile = writeTempFile("compose-", ".yml", renderCompose(stack, dep, target));
            Path envFile = writeTempFile("env-", ".env", renderEnv(stack, dep, target));

            execOrThrow(env, List.of("sh", "-lc", "mkdir -p " + shellQuote(runtime)), Duration.ofMinutes(1));

            appendLog(deploymentId, nodeId, "UPLOAD", "RUNNING", "upload compose/env to " + runtime);
            env.uploadFile(composeFile, composeRemotePath);
            env.uploadFile(envFile, envRemotePath);

            appendLog(deploymentId, nodeId, "COMPOSE_UP", "RUNNING", "docker compose up -d");
            execOrThrow(env,
                List.of("sh", "-lc", "cd " + shellQuote(runtime) + " && docker compose up -d"),
                Duration.ofMinutes(10));

            // 健康检查（最简单的：curl localhost:port/actuator/health）
            Integer port = resolveWebPort(stack, target);
            appendLog(deploymentId, nodeId, "HEALTHCHECK", "RUNNING", "curl localhost:" + port + "/actuator/health");
            healthCheckWithRetry(env,
                "http://127.0.0.1:" + port + "/actuator/health",
                20,
                Duration.ofSeconds(2),
                Duration.ofSeconds(3));

            if (Boolean.TRUE.equals(dep.getDeployFronted())) {
                Integer frontPort = resolveFrontedPort(stack, target);
                appendLog(deploymentId, nodeId, "FRONTED_HEALTH", "RUNNING", "curl localhost:" + frontPort);
                healthCheckWithRetry(env,
                    "http://127.0.0.1:" + frontPort,
                    20,
                    Duration.ofSeconds(2),
                    Duration.ofSeconds(3));
            }

            appendLog(deploymentId, nodeId, "TARGET_DONE", "SUCCESS", "node ok");
        } catch (Exception e) {
            appendLog(deploymentId, nodeId, "TARGET_FAIL", "FAIL",
                truncate("err=" + e.getMessage() + "\n" + String.join("\n", stepLogs), 8000));
            throw new RuntimeException("部署到节点失败: " + nodeId + ", " + e.getMessage(), e);
        }
    }

    private void updateEntryNginx(StackDeploymentEntity dep, StackConfigEntity stack, List<StackTargetEntity> webTargets) {
        String deploymentId = dep.getDeploymentId();
        String entryNodeId = stack.getEntryNodeId();
        if (entryNodeId == null || entryNodeId.isBlank()) {
            // MVP：不做复杂推断；没有 entryNodeId 就跳过，避免误操作
            appendLog(deploymentId, null, "NGINX_SKIP", "FAIL", "entryNodeId is empty");
            throw new IllegalStateException("entryNodeId 为空，无法更新入口 Nginx");
        }

        NodeManagementService.NodeInfo nodeInfo = nodeManagementService.getNode(entryNodeId)
            .orElseThrow(() -> new IllegalArgumentException("入口节点不存在: " + entryNodeId));
        ExecutionEnvironment env = envFactory.create(nodeInfo);
        appendLog(deploymentId, entryNodeId, "NGINX_RENDER", "RUNNING", "render upstream config");

        String nginxConf = renderNginxUpstream(dep.getStackId(), stack, webTargets);
        try {
            Path confFile = writeTempFile("nginx-", ".conf", nginxConf);
            String remote = resolveNginxConfPath(stack, dep.getStackId());
            // 确保远程目录存在
            String remoteDir = remote.contains("/") ? remote.substring(0, remote.lastIndexOf('/')) : null;
            if (remoteDir != null && !remoteDir.isBlank()) {
                execOrThrow(env, List.of("sh", "-lc", "mkdir -p " + shellQuote(remoteDir)), Duration.ofMinutes(1));
            }
            env.uploadFile(confFile, remote);

            appendLog(deploymentId, entryNodeId, "NGINX_TEST_RELOAD", "RUNNING", "nginx -t && reload");
            execOrThrow(env, List.of("sh", "-lc",
                "nginx -t && nginx -s reload"), Duration.ofMinutes(1));

            appendLog(deploymentId, entryNodeId, "NGINX_DONE", "SUCCESS", "nginx updated");
        } catch (Exception e) {
            appendLog(deploymentId, entryNodeId, "NGINX_FAIL", "FAIL", truncate(e.getMessage(), 4000));
            throw new RuntimeException("更新 Nginx 失败: " + e.getMessage(), e);
        }
    }

    private String renderCompose(StackConfigEntity stack, StackDeploymentEntity dep, StackTargetEntity target) {
        Integer port = resolveWebPort(stack, target);
        String webImage = stack.getWebImage() != null && !stack.getWebImage().isBlank() ? stack.getWebImage() : "gress/gress-web:latest";
        Integer frontedPort = resolveFrontedPort(stack, target);
        String frontedImage = stack.getFrontedImage() != null && !stack.getFrontedImage().isBlank() ? stack.getFrontedImage() : "gress/gress-fronted:latest";

        StringBuilder sb = new StringBuilder();
        sb.append("name: gress-stack-").append(dep.getStackId()).append("\n");
        sb.append("services:\n");
        sb.append("  gress-web:\n");
        sb.append("    image: ").append(webImage).append("\n");
        sb.append("    container_name: gress-web-").append(dep.getStackId()).append("\n");
        sb.append("    restart: unless-stopped\n");
        sb.append("    env_file:\n");
        sb.append("      - ./.env\n");
        sb.append("    environment:\n");
        sb.append("      - SERVER_PORT=").append(port).append("\n");
        sb.append("    ports:\n");
        sb.append("      - \"").append(port).append(":").append(port).append("\"\n");

        if (Boolean.TRUE.equals(dep.getDeployFronted())) {
            sb.append("  gress-fronted:\n");
            sb.append("    image: ").append(frontedImage).append("\n");
            sb.append("    container_name: gress-fronted-").append(dep.getStackId()).append("\n");
            sb.append("    restart: unless-stopped\n");
            sb.append("    depends_on:\n");
            sb.append("      - gress-web\n");
            sb.append("    ports:\n");
            sb.append("      - \"").append(frontedPort).append(":80\"").append("\n");
        }

        return sb.toString();
    }

    private String renderEnv(StackConfigEntity stack, StackDeploymentEntity dep, StackTargetEntity target) {
        Map<String, Object> extra = parseExtraConfig(stack.getExtraConfig());
        Integer port = resolveWebPort(stack, target);

        Map<String, String> env = new LinkedHashMap<>();
        env.put("STACK_ID", dep.getStackId());
        env.put("SERVER_PORT", String.valueOf(port));
        env.put("MYSQL_DATABASE", stack.getMysqlDatabase());
        env.put("REDIS_DB", stack.getRedisDb() != null ? String.valueOf(stack.getRedisDb()) : "0");

        // extra_config 可覆盖/补充所有环境变量（例如 MYSQL_HOST、MYSQL_USERNAME、MYSQL_PASSWORD、REDIS_HOST 等）
        for (Map.Entry<String, Object> e : extra.entrySet()) {
            if (e.getKey() == null || e.getKey().isBlank() || e.getValue() == null) continue;
            env.put(e.getKey(), String.valueOf(e.getValue()));
        }

        StringBuilder sb = new StringBuilder();
        env.forEach((k, v) -> sb.append(k).append("=").append(escapeEnvValue(v)).append("\n"));
        return sb.toString();
    }

    private String renderNginxUpstream(String stackId, StackConfigEntity stack, List<StackTargetEntity> webTargets) {
        String upstreamName = "gress_stack_" + stackId + "_web";
        StringBuilder sb = new StringBuilder();
        sb.append("upstream ").append(upstreamName).append(" {\n");
        sb.append("  least_conn;\n");
        for (StackTargetEntity t : webTargets) {
            String host = resolveTargetHost(t.getNodeId());
            Integer port = resolveWebPort(stack, t);
            sb.append("  server ").append(host).append(":").append(port).append(" max_fails=3 fail_timeout=10s;\n");
        }
        sb.append("}\n");
        sb.append("\n");

        // server block（入口反代到 upstream）
        String domain = stack.getDomain();
        sb.append("server {\n");
        sb.append("  listen 80;\n");
        if (domain != null && !domain.isBlank()) {
            sb.append("  server_name ").append(domain).append(";\n");
        } else {
            sb.append("  server_name _;\n");
        }
        sb.append("  client_max_body_size 50m;\n");
        sb.append("  location / {\n");
        sb.append("    proxy_set_header Host $host;\n");
        sb.append("    proxy_set_header X-Real-IP $remote_addr;\n");
        sb.append("    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;\n");
        sb.append("    proxy_set_header X-Forwarded-Proto $scheme;\n");
        sb.append("    proxy_http_version 1.1;\n");
        sb.append("    proxy_set_header Connection \"\";\n");
        sb.append("    proxy_buffering off;\n");
        sb.append("    proxy_read_timeout 3600s;\n");
        sb.append("    proxy_send_timeout 3600s;\n");
        sb.append("    proxy_pass http://").append(upstreamName).append(";\n");
        sb.append("  }\n");
        sb.append("}\n");
        return sb.toString();
    }

    private Integer resolveWebPort(StackConfigEntity stack, StackTargetEntity target) {
        if (target.getWebPort() != null && target.getWebPort() > 0) return target.getWebPort();
        if (stack.getWebHostPort() != null && stack.getWebHostPort() > 0) return stack.getWebHostPort();
        return 8082;
    }

    private Integer resolveFrontedPort(StackConfigEntity stack, StackTargetEntity target) {
        if (target.getFrontedPort() != null && target.getFrontedPort() > 0) return target.getFrontedPort();
        if (stack.getFrontedHostPort() != null && stack.getFrontedHostPort() > 0) return stack.getFrontedHostPort();
        return 8080;
    }

    private String resolveRuntimeDir(StackConfigEntity stack, String stackId) {
        if (stack.getRuntimeBaseDir() != null && !stack.getRuntimeBaseDir().isBlank()) {
            return normalizeRequiredPath(stack.getRuntimeBaseDir(), "runtimeBaseDir");
        }
        return normalizeRequiredPath("/home/gress/" + stackId + "/runtime", "runtimeBaseDir");
    }

    private String resolveNginxConfPath(StackConfigEntity stack, String stackId) {
        Map<String, Object> extra = parseExtraConfig(stack.getExtraConfig());
        Object p = extra.get("NGINX_CONF_PATH");
        if (p != null && !String.valueOf(p).isBlank()) {
            return normalizeRequiredPath(String.valueOf(p), "NGINX_CONF_PATH");
        }
        // 默认：入口节点 nginx 的 conf.d（需要入口节点有权限写入；没有权限可通过 extra_config 覆盖到 runtime 下再由宿主 include）
        return normalizeRequiredPath("/etc/nginx/conf.d/gress-stacks-" + stackId + ".conf", "NGINX_CONF_PATH");
    }

    private String resolveTargetHost(String nodeId) {
        try {
            NodeManagementService.NodeInfo nodeInfo = nodeManagementService.getNode(nodeId).orElse(null);
            if (nodeInfo == null || nodeInfo.getConfig() == null) return "127.0.0.1";
            NodeManagementService.NodeConfig cfg = nodeInfo.getConfig();
            if (cfg instanceof NodeManagementService.SshNodeConfig ssh) {
                if (ssh.getHost() != null && !ssh.getHost().isBlank()) return ssh.getHost();
            }
            return "127.0.0.1";
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    private void markRunning(StackDeploymentEntity dep) {
        dep.setStatus("RUNNING");
        dep.setStartedAt(System.currentTimeMillis());
        stackDeploymentDao.updateById(dep);
    }

    private void tryMarkFailed(String deploymentId, String message) {
        try {
            StackDeploymentEntity dep = stackDeploymentDao.findByDeploymentId(deploymentId).orElse(null);
            if (dep == null) return;
            dep.setStatus("FAILED");
            dep.setMessage(message);
            dep.setEndedAt(System.currentTimeMillis());
            stackDeploymentDao.updateById(dep);
        } catch (Exception ignored) {
        }
    }

    private void appendLog(String deploymentId, String nodeId, String step, String status, String output) {
        if (stackDeploymentLogDao == null) return;
        StackDeploymentLogEntity e = new StackDeploymentLogEntity();
        e.setDeploymentId(deploymentId);
        e.setNodeId(nodeId);
        e.setStep(step);
        e.setStatus(status);
        e.setOutput(truncate(output, 12000));
        e.setTimestamp(System.currentTimeMillis());
        stackDeploymentLogDao.save(e);
    }

    private void execOrThrow(ExecutionEnvironment env, List<String> cmd, Duration timeout) {
        var r = env.executeCommand(cmd, Collections.emptyMap(), timeout);
        if (r == null || r.getExitCode() != 0) {
            throw new RuntimeException("cmd failed: " + String.join(" ", cmd)
                + ", exitCode=" + (r != null ? r.getExitCode() : "null")
                + ", out=" + (r != null ? r.getOutput() : "")
                + ", err=" + (r != null ? r.getErrorOutput() : ""));
        }
    }

    private void healthCheckWithRetry(ExecutionEnvironment env,
                                      String url,
                                      int maxAttempts,
                                      Duration sleepBetween,
                                      Duration curlTimeout) {
        int attempts = Math.max(1, maxAttempts);
        for (int i = 1; i <= attempts; i++) {
            var r = env.executeCommand(
                List.of("sh", "-lc", "curl -fsS --max-time " + Math.max(1, curlTimeout.toSeconds()) + " " + shellQuote(url) + " >/dev/null"),
                Collections.emptyMap(),
                curlTimeout.plusSeconds(2));
            if (r != null && r.getExitCode() == 0) {
                return;
            }
            if (i == attempts) {
                throw new RuntimeException("healthcheck failed: url=" + url + ", attempts=" + attempts);
            }
            try {
                Thread.sleep(Math.max(0, sleepBetween.toMillis()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("healthcheck interrupted");
            }
        }
    }

    private static boolean containsRole(String roles, String role) {
        String[] parts = roles.split(",");
        for (String p : parts) {
            if (p != null && p.trim().equalsIgnoreCase(role)) return true;
        }
        return false;
    }

    private static Path writeTempFile(String prefix, String suffix, String content) throws IOException {
        Path p = Files.createTempFile(prefix, suffix);
        Files.writeString(p, content != null ? content : "", StandardCharsets.UTF_8);
        return p;
    }

    private static Map<String, Object> parseExtraConfig(String json) {
        if (json == null || json.isBlank()) return Collections.emptyMap();
        try {
            Object o = JSON.parse(json);
            if (o instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) o;
                return m;
            }
        } catch (Exception ignored) {
        }
        return Collections.emptyMap();
    }

    private static String escapeEnvValue(String v) {
        if (v == null) return "";
        // 简单处理：包含空格/特殊字符时用双引号包裹
        if (v.contains(" ") || v.contains("#") || v.contains("\"")) {
            return "\"" + v.replace("\"", "\\\"") + "\"";
        }
        return v;
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        if (s.length() <= max) return s;
        return s.substring(0, max) + "\n...<truncated>...";
    }

    private static String shellQuote(String s) {
        if (s == null) return "''";
        return "'" + s.replace("'", "'\\''") + "'";
    }

    private String normalizeRequiredPath(String path, String fieldName) {
        if (path == null) {
            throw new IllegalStateException(fieldName + " 不能为空");
        }
        String normalized = path.trim();
        if (normalized.isEmpty()) {
            throw new IllegalStateException(fieldName + " 不能为空");
        }
        return normalized;
    }
}
