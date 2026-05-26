package com.mu.transitmap.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * AES 加密服务（统一管理 master key 解析与加解密）
 *
 * - 优先从 Spring property `agent.crypto.master-key` 读取
 * - 回退到环境变量 AGENT_CRYPTO_MASTER_KEY
 * - 再回退到固定开发默认密钥
 * - 任何字符串都会通过 SHA-256 规范化为 32 字节 AES-256 密钥，
 *   保证同一字符串在任意环境下都得到一致的密钥
 * - 算法：AES/ECB/PKCS5Padding（生产环境建议升级 GCM）
 *
 * 加密结果含 "enc:" 前缀：enc:base64(ciphertext)
 */
@Component
public class CryptoService {

    private static final Logger log = LoggerFactory.getLogger(CryptoService.class);
    public static final String ENC_PREFIX = "enc:";
    private static final String DEFAULT_DEV_KEY = "TransitMap-default-dev-master-key-do-not-use-in-prod";

    private final SecretKeySpec keySpec;

    public CryptoService(@Value("${agent.crypto.master-key:}") String configKey) {
        String masterKey = configKey;
        if (masterKey == null || masterKey.isEmpty()) {
            masterKey = System.getenv("AGENT_CRYPTO_MASTER_KEY");
        }
        if (masterKey == null || masterKey.isEmpty()) {
            log.warn("AGENT_CRYPTO_MASTER_KEY 未配置，使用开发默认密钥，请勿在生产环境使用！");
            masterKey = DEFAULT_DEV_KEY;
        }
        this.keySpec = new SecretKeySpec(deriveKey(masterKey), "AES");
    }

    /**
     * 把任意字符串规范化为 32 字节 AES-256 密钥
     * 使用 SHA-256：保证同一字符串在任意环境/JDK 下得到完全一致的字节序列
     */
    private byte[] deriveKey(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(raw.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            // SHA-256 是 JDK 标准算法，理论不会失败
            throw new RuntimeException("SHA-256 不可用: " + e.getMessage(), e);
        }
    }

    /**
     * 加密：返回带 "enc:" 前缀的 Base64 密文
     */
    public String encrypt(String plain) {
        if (plain == null || plain.isEmpty()) return plain;
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            byte[] enc = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return ENC_PREFIX + Base64.getEncoder().encodeToString(enc);
        } catch (Exception e) {
            log.error("encrypt failed", e);
            throw new RuntimeException("加密失败: " + e.getMessage(), e);
        }
    }

    /**
     * 解密：自动剥离 "enc:" 前缀；若非密文则原样返回
     * 解密失败抛 RuntimeException，避免下游用密文当明文调外部 API
     */
    public String decrypt(String stored) {
        if (stored == null || stored.isEmpty()) return stored;
        if (!stored.startsWith(ENC_PREFIX)) return stored;
        String body = stored.substring(ENC_PREFIX.length());
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            byte[] dec = cipher.doFinal(Base64.getDecoder().decode(body));
            return new String(dec, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("decrypt failed (master key 可能与加密时不一致)", e);
            throw new RuntimeException(
                    "密文解密失败，请检查 AGENT_CRYPTO_MASTER_KEY 是否与加密时一致：" + e.getMessage(), e);
        }
    }

    /**
     * 掩码显示：sk-xxxx****yyyy（前 3 后 4）
     */
    public String mask(String plain) {
        if (plain == null || plain.isEmpty()) return "";
        if (plain.length() <= 8) return "****";
        return plain.substring(0, 3) + "****" + plain.substring(plain.length() - 4);
    }
}
