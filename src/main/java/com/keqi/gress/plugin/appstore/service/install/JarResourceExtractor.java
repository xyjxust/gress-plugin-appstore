package com.keqi.gress.plugin.appstore.service.install;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * 从插件 JAR 中提取资源文件（如 docker-compose.yml）。
 */
public class JarResourceExtractor {
    private static final Log log = LogFactory.get(JarResourceExtractor.class);

    /**
     * 在 JAR 内查找任意路径的 docker-compose.yml（优先根目录），并提取到指定目录。
     */
    public Optional<Path> extractDockerCompose(Path jarFile, Path targetDir) {
        try (JarFile jf = new JarFile(jarFile.toFile())) {
            JarEntry best = null;
            Enumeration<JarEntry> entries = jf.entries();
            while (entries.hasMoreElements()) {
                JarEntry e = entries.nextElement();
                if (e.isDirectory()) {
                    continue;
                }
                String name = e.getName();
                if (name == null) {
                    continue;
                }
                if (name.endsWith("docker-compose.yml")) {
                    // 优先根目录 docker-compose.yml
                    if ("docker-compose.yml".equals(name)) {
                        best = e;
                        break;
                    }
                    if (best == null) {
                        best = e;
                    }
                }
            }

            if (best == null) {
                return Optional.empty();
            }

            Files.createDirectories(targetDir);
            Path out = targetDir.resolve("docker-compose.yml");
            try (InputStream in = jf.getInputStream(best)) {
                Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
            }
            log.info("已从插件包提取 docker-compose.yml: entry={}, out={}", best.getName(), out);
            return Optional.of(out);
        } catch (Exception e) {
            log.warn("提取 docker-compose.yml 失败: jar={}", jarFile, e);
            return Optional.empty();
        }
    }
}











