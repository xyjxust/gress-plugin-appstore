package com.keqi.gress.plugin.appstore.service.install;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import  com.keqi.gress.common.model.Result;
import  com.keqi.gress.common.plugin.PluginMetadataParser;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * 从插件包内提取 docker-compose.yml 并执行 docker compose up -d。
 *
 * 约定：
 * - 插件 jar 内包含 docker-compose.yml（建议放在 resources 根目录）。
 * - 仅当存在该文件时才执行；否则跳过，不影响安装流程。
 */
public class DockerComposeInstallHook implements PluginInstallHook {
    private static final Log log = LogFactory.get(DockerComposeInstallHook.class);

    private final JarResourceExtractor extractor = new JarResourceExtractor();
    private final DockerComposeRunner runner = new DockerComposeRunner();

    @Override
    public Result<Void> beforeInstall(Path jarFile, PluginInstallContext ctx) {
        return ensureMiddleware(jarFile, ctx);
    }

    @Override
    public Result<Void> beforeUpgrade(Path jarFile, PluginInstallContext ctx) {
        return ensureMiddleware(jarFile, ctx);
    }

    private Result<Void> ensureMiddleware(Path jarFile, PluginInstallContext ctx) {
        try {
            Optional<Path> composeOpt = extractor.extractDockerCompose(jarFile, resolveWorkDir(jarFile, ctx));
            if (composeOpt.isEmpty()) {
                return Result.success();
            }

            if (!runner.dockerAvailable()) {
                return Result.error("Docker 不可用：请检查 Docker daemon（DinD 场景需正确设置 DOCKER_HOST 或挂载 /var/run/docker.sock）");
            }
            if (!runner.dockerComposeAvailable()) {
                return Result.error("Docker Compose 不可用：请检查 docker compose 是否可用");
            }

            String projectName = buildProjectName(jarFile, ctx);
            DockerComposeRunner.ResultExec r = runner.execComposeUp(composeOpt.get(), projectName);
            if (r.exitCode != 0) {
                return Result.error("docker compose 部署失败: " + r.output);
            }

            log.info("docker compose 部署成功: project={}, compose={}", projectName, composeOpt.get());
            return Result.success();
        } catch (Exception e) {
            log.error("docker compose 部署异常", e);
            return Result.error("docker compose 部署异常: " + e.getMessage());
        }
    }

    private Path resolveWorkDir(Path jarFile, PluginInstallContext ctx) {
        // ~/.gress/plugin-middleware/<pluginId>/<version>/
        String home = System.getProperty("user.home", ".");
        String pluginId = ctx.getPackageIdHint();
        String version = ctx.getVersionHint();

        // 如果上层没传 hint，则尝试解析 jar 元数据
        if ((pluginId == null || pluginId.isBlank()) || (version == null || version.isBlank())) {
            try {
                PluginMetadataParser.PluginMetadata md = PluginMetadataParser.parseFromJar(jarFile);
                if (pluginId == null || pluginId.isBlank()) {
                    pluginId = md.getPluginId();
                }
                if (version == null || version.isBlank()) {
                    version = md.getVersion();
                }
            } catch (Exception ignored) {
            }
        }

        if (pluginId == null || pluginId.isBlank()) {
            pluginId = "unknown";
        }
        if (version == null || version.isBlank()) {
            version = "unknown";
        }

        return Paths.get(home, ".gress", "plugin-middleware", pluginId, version);
    }

    private String buildProjectName(Path jarFile, PluginInstallContext ctx) {
        String pluginId = ctx.getPackageIdHint();
        if (pluginId == null || pluginId.isBlank()) {
            try {
                pluginId = PluginMetadataParser.parseFromJar(jarFile).getPluginId();
            } catch (Exception ignored) {
                pluginId = "plugin";
            }
        }
        // projectName 需满足 compose 约束：字母数字下划线
        return ("gress_" + pluginId).replaceAll("[^a-zA-Z0-9_]", "_");
    }
}











