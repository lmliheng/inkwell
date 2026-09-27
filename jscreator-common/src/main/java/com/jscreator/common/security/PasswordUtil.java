package com.jscreator.common.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 密码哈希，必须与 Express 版 legacy-utils/crypto-password 字节级一致：
 * <pre>CryptoJS.SHA256(password).toString()  →  小写十六进制 SHA-256</pre>
 * 因为库里存的是老哈希，算法一变存量用户就登不进来。
 */
public final class PasswordUtil {

    private PasswordUtil() {
    }

    public static String toHash(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    public static boolean compare(String rawPassword, String hashedPassword) {
        if (rawPassword == null || hashedPassword == null) {
            return false;
        }
        return toHash(rawPassword).equals(hashedPassword);
    }
}
