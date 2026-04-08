-- App Store plugin: signing key management table
-- Used by appstore-admin for signing plugins and by appstore for trusted roots verification (stage B).

CREATE TABLE IF NOT EXISTS appstore_signing_key (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',

  key_id VARCHAR(64) NOT NULL COMMENT '密钥稳定ID（用于客户端/页面引用）',
  alias VARCHAR(128) NOT NULL COMMENT 'keystore alias',

  keystore_url VARCHAR(1024) NOT NULL COMMENT 'keystore 文件在 FileStorageService 中的 URL',

  store_password_enc TEXT NOT NULL COMMENT '加密后的 keystore store password（AES-GCM 密文）',
  key_password_enc TEXT NOT NULL COMMENT '加密后的 keystore key password（AES-GCM 密文）',

  public_key_pem LONGTEXT NOT NULL COMMENT '受信公钥材料（PEM：CERTIFICATE 或 PUBLIC KEY，self-signed 推荐 CERTIFICATE）',
  public_fingerprint_sha256 VARCHAR(128) NOT NULL COMMENT '公钥指纹（SHA-256，hex）',

  active TINYINT NOT NULL DEFAULT 0 COMMENT '是否为当前签名使用中密钥',
  trusted_until DATETIME NULL COMMENT '可信到期时间（用于轮换窗口）；为空表示永久可信',
  revoked_at DATETIME NULL COMMENT '吊销时间（吊销后不再作为可信根）',

  created_by VARCHAR(64) NULL COMMENT '创建人',
  updated_by VARCHAR(64) NULL COMMENT '更新人',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  PRIMARY KEY (id),
  UNIQUE KEY uk_key_id (key_id),
  KEY idx_active (active),
  KEY idx_trusted_until (trusted_until),
  KEY idx_revoked_at (revoked_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应用商店-签名密钥管理表';

