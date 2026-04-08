package com.keqi.gress.plugin.appstore.service.security;

import com.keqi.gress.common.model.Result;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Verify a plugin JAR is signed by the pinned public key.
 *
 * <p>Expected JAR signature:
 * - META-INF/*.SF
 * - META-INF/*.RSA (or *.DSA)
 *
 * <p>Note: pinned key is loaded from an embedded PEM resource in this plugin.
 */
public class JarSignatureVerifier {

    private static final String PINNED_PUBLIC_KEY_RESOURCE = "/appstore-signing-public-key.pem";

    /**
     * Trusted roots are stored as encoded public key strings for O(1) matching.
     * <p>
     * Stage B: supports multiple roots to make rotation smooth (old+new trusted window).
     * </p>
     */
    private volatile java.util.Set<String> trustedPublicKeyEncodings = java.util.Collections.emptySet();
    private final Object trustLock = new Object();

    private final String initError;

    public JarSignatureVerifier() {
        PublicKey pk = null;
        String err = null;
        try {
            pk = loadPinnedPublicKey();
        } catch (Exception e) {
            err = e.getMessage();
        }
        this.initError = err;
        if (pk != null) {
            this.trustedPublicKeyEncodings = java.util.Set.of(encodePublicKey(pk));
        }
    }

    /**
     * Refresh trusted roots with PEM-encoded certificates/public keys.
     *
     * <p>When stage B is enabled, caller should periodically fetch trusted roots from
     * appstore-admin and refresh this verifier.</p>
     *
     * @param pemList PEM strings (each can be CERTIFICATE or PUBLIC KEY)
     */
    public void refreshTrustedRootsFromPems(java.util.List<String> pemList) {
        if (pemList == null || pemList.isEmpty()) {
            return;
        }

        java.util.Set<String> next = new java.util.HashSet<>();
        for (String pem : pemList) {
            if (pem == null || pem.trim().isEmpty()) {
                continue;
            }
            PublicKey pk = parsePublicKeyFromPem(pem);
            if (pk != null) {
                next.add(encodePublicKey(pk));
            }
        }
        if (!next.isEmpty()) {
            synchronized (trustLock) {
                this.trustedPublicKeyEncodings = java.util.Collections.unmodifiableSet(next);
            }
        }
    }

    /**
     * Verify the jar signature by trusted roots.
     *
     * @param jarPath jar file path
     */
    public Result<Void> verify(Path jarPath) {
        if (jarPath == null) {
            return Result.error("非法插件包，拒绝安装（文件路径为空）");
        }
        if (!java.nio.file.Files.exists(jarPath)) {
            return Result.error("非法插件包，拒绝安装（文件不存在）");
        }
        if (initError != null && (trustedPublicKeyEncodings == null || trustedPublicKeyEncodings.isEmpty())) {
            return Result.error("验签配置异常，请联系管理员（可信公钥初始化失败）");
        }
        if (trustedPublicKeyEncodings == null || trustedPublicKeyEncodings.isEmpty()) {
            return Result.error("验签配置异常，请联系管理员（未配置可信公钥）");
        }

        try (JarFile jarFile = new JarFile(jarPath.toFile(), true)) {
            boolean hasSf = false;
            boolean hasRsaOrDsa = false;

            var entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry e = entries.nextElement();
                if (e.isDirectory()) {
                    continue;
                }
                String name = e.getName();
                if (!name.startsWith("META-INF/")) {
                    continue;
                }
                if (name.endsWith(".SF")) {
                    hasSf = true;
                } else if (name.endsWith(".RSA") || name.endsWith(".DSA")) {
                    hasRsaOrDsa = true;
                }
            }

            if (!hasSf || !hasRsaOrDsa) {
                return Result.error("非法插件包，拒绝安装（缺少签名信息）");
            }

            boolean matched = false;
            entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }

                // Trigger signature verification by requesting certificates.
                Certificate[] certs;
                try {
                    certs = entry.getCertificates();
                } catch (SecurityException se) {
                    return Result.error("非法插件包，拒绝安装（签名校验失败）");
                }

                if (certs == null || certs.length == 0) {
                    continue;
                }

                for (Certificate cert : certs) {
                    if (!(cert instanceof X509Certificate signerCert)) {
                        continue;
                    }
                    PublicKey signerPk = signerCert.getPublicKey();
                    if (signerPk == null) {
                        continue;
                    }
                    String enc = encodePublicKey(signerPk);
                    if (trustedPublicKeyEncodings.contains(enc)) {
                        matched = true;
                        break;
                    }
                }
                if (matched) {
                    break;
                }
            }

            if (!matched) {
                return Result.error("非法插件包，拒绝安装（签名来源不受信任）");
            }

            return Result.success(null);
        } catch (Exception e) {
            return Result.error("非法插件包，拒绝安装（签名校验异常）");
        }
    }

    private static PublicKey loadPinnedPublicKey() {
        try (InputStream is = JarSignatureVerifier.class.getResourceAsStream(PINNED_PUBLIC_KEY_RESOURCE)) {
            if (is == null) {
                throw new IllegalStateException("Pinned public key resource not found: " + PINNED_PUBLIC_KEY_RESOURCE);
            }

            String pem = readAllToString(is).trim();
            if (pem.isEmpty() || pem.contains("REPLACE_ME")) {
                throw new IllegalStateException("Pinned public key PEM is placeholder; please replace it before enabling verifySignature.");
            }

            return parsePublicKeyFromPem(pem);
        } catch (Exception e) {
            // Fail fast; the caller should surface a clear error to operator.
            throw new IllegalStateException("Failed to load pinned public key: " + e.getMessage(), e);
        }
    }

    private static PublicKey parsePublicKeyFromPem(String pem) {
        String normalized = pem.trim();
        if (normalized.contains("BEGIN CERTIFICATE")) {
            try {
                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                X509Certificate cert = (X509Certificate) cf.generateCertificate(
                        new java.io.ByteArrayInputStream(normalized.getBytes(StandardCharsets.UTF_8)));
                return cert.getPublicKey();
            } catch (Exception e) {
                throw new IllegalStateException("Failed to parse CERTIFICATE PEM: " + e.getMessage(), e);
            }
        }

        if (normalized.contains("BEGIN PUBLIC KEY")) {
            String base64 = normalized
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s+", "");
            byte[] decoded = Base64.getDecoder().decode(base64);

            // Try RSA first; fallback to EC.
            X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
            try {
                return KeyFactory.getInstance("RSA").generatePublic(spec);
            } catch (Exception ignored) {
                try {
                    return KeyFactory.getInstance("EC").generatePublic(spec);
                } catch (Exception e) {
                    throw new IllegalStateException("Failed to parse PUBLIC KEY PEM: " + e.getMessage(), e);
                }
            }
        }

        throw new IllegalStateException("Unsupported PEM format. Expect CERTIFICATE or PUBLIC KEY.");
    }

    private static String encodePublicKey(PublicKey pk) {
        // Use Base64 string for stable equality and cheap matching.
        return Base64.getEncoder().encodeToString(pk.getEncoded());
    }

    private static String readAllToString(InputStream is) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read resource stream", e);
        }
        return sb.toString();
    }
}

