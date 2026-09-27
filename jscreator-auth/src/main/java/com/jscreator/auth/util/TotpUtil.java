package com.jscreator.auth.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * TOTP（RFC 6238）实现，参数逐项对齐原版 otplib 12.0.1 的 {@code authenticator} 默认值：
 *
 * <ul>
 *   <li>步长 30 秒、6 位数字、HMAC-SHA1</li>
 *   <li>window = 0：只认当前步长（otplib 的 {@code totpDefaultOptions().window} 默认 0，没有 ±1 容错）</li>
 *   <li>密钥用 Base32 存储（{@code @otplib/plugin-thirty-two}）：解码遇 '=' 停止，非法字符抛错</li>
 *   <li>{@code generateSecret()} 取 10 字节随机数 → Base32 编码并去掉 '='（16 个字符）</li>
 *   <li>密钥不足 10 字节时按 {@code totpPadSecret} 的规则用自身重复补齐（原版直接操作 hex 串）</li>
 * </ul>
 *
 * 只用 JDK 自带的 MessageDigest/Mac，不引新依赖。
 */
public final class TotpUtil {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    /** 原版 otplib 的默认步长（秒）。 */
    public static final int STEP_SECONDS = 30;
    /** 原版 otplib 的默认位数。 */
    public static final int DIGITS = 6;

    private static final SecureRandom RANDOM = new SecureRandom();

    private TotpUtil() {
    }

    /** 生成 Base32 密钥：10 字节随机数，去掉 '=' 补位，与 {@code authenticator.generateSecret()} 一致。 */
    public static String generateSecret() {
        byte[] raw = new byte[10];
        RANDOM.nextBytes(raw);
        return base32Encode(raw);
    }

    /** otpauth:// 二维码 URL，格式与 {@code authenticator.keyuri(account, issuer, secret)} 一致。 */
    public static String keyUri(String account, String issuer, String secret) {
        return "otpauth://totp/"
                + encodeUriComponent(issuer) + ":" + encodeUriComponent(account)
                + "?secret=" + secret
                + "&period=" + STEP_SECONDS
                + "&digits=" + DIGITS
                + "&algorithm=SHA1"
                + "&issuer=" + encodeUriComponent(issuer);
    }

    /** 校验动态码：与原版 {@code authenticator.check(code, secret)} 等价（严格相等，window=0）。 */
    public static boolean check(String code, String secret) {
        if (code == null || secret == null) {
            return false;
        }
        return code.equals(generate(secret));
    }

    /** 当前步长的动态码。 */
    public static String generate(String secret) {
        byte[] key = hmacKey(decodeBase32(secret));
        long counter = System.currentTimeMillis() / 1000L / STEP_SECONDS;
        byte[] message = new byte[8];
        for (int i = 7; i >= 0; i--) {
            message[i] = (byte) (counter & 0xff);
            counter >>= 8;
        }
        byte[] digest;
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            digest = mac.doFinal(message);
        } catch (Exception e) {
            throw new IllegalStateException("TOTP 计算失败", e);
        }
        int offset = digest[digest.length - 1] & 0x0f;
        int binary = ((digest[offset] & 0x7f) << 24)
                | ((digest[offset + 1] & 0xff) << 16)
                | ((digest[offset + 2] & 0xff) << 8)
                | (digest[offset + 3] & 0xff);
        int token = binary % 1_000_000;
        return String.format("%06d", token);
    }

    // ------------------------------------------------------------------ 内部

    /**
     * otplib 的 {@code totpPadSecret(secret, 'hex', 20)}：把解码出来的密钥转成 hex 串，
     * 短于 20 个字符时用自身重复补齐，再取前 10 字节做 HMAC 密钥（SHA-1 → 20 hex = 10 字节）。
     */
    private static byte[] hmacKey(byte[] decoded) {
        StringBuilder sb = new StringBuilder(decoded.length * 2);
        for (byte b : decoded) {
            sb.append(HEX[(b >> 4) & 0x0f]).append(HEX[b & 0x0f]);
        }
        String hex = sb.toString();
        if (hex.isEmpty()) {
            return new byte[0];
        }
        while (hex.length() < 20) {
            hex = hex + hex; // 原版 new Array(20 - len + 1).join(hex)
        }
        String first = hex.substring(0, Math.min(20, hex.length()));
        byte[] key = new byte[first.length() / 2];
        for (int i = 0; i < key.length; i++) {
            key[i] = (byte) Integer.parseInt(first.substring(i * 2, i * 2 + 2), 16);
        }
        return key;
    }

    /**
     * Base32 解码，逐行照搬 thirty-two 的实现（原版 {@code @otplib/plugin-thirty-two}）：
     *
     * <ul>
     *   <li>遇到 '=' 停止；字符串按 UTF-8 拆成字节逐个处理</li>
     *   <li>用 {@code byteTable[charCode - 0x30]} 取值，<b>只判断上界不判断下界</b>：
     *       charCode &lt; 0x30（如 '!'、空格）时下标为负，取到 undefined，按 0 参与位运算（不报错）；
     *       表里的 0xff（如 '0'、'1'、':'）会被当成数字 255 用——这是原库的怪癖，照抄</li>
     *   <li>charCode &gt; 0x7F 才抛错（{@code Invalid input - it is not base32 encoded string}）</li>
     * </ul>
     */
    public static byte[] decodeBase32(String encoded) {
        byte[] source = encoded.getBytes(StandardCharsets.UTF_8);
        byte[] decoded = new byte[(int) Math.ceil(source.length * 5.0 / 8)];
        int shiftIndex = 0;
        int plainDigit = 0;
        int plainChar = 0;
        int plainPos = 0;
        for (byte value : source) {
            int code = value & 0xff;
            if (code == 0x3d) { // '='
                break;
            }
            int index = code - 0x30;
            if (index >= BASE32_TABLE.length) {
                throw new IllegalArgumentException("Invalid input - it is not base32 encoded string");
            }
            plainDigit = (index < 0) ? 0 : BASE32_TABLE[index]; // 负下标 → undefined → 位运算当 0
            if (shiftIndex <= 3) {
                shiftIndex = (shiftIndex + 5) % 8;
                if (shiftIndex == 0) {
                    plainChar |= plainDigit;
                    decoded[plainPos++] = (byte) plainChar;
                    plainChar = 0;
                } else {
                    plainChar |= 0xff & (plainDigit << (8 - shiftIndex));
                }
            } else {
                shiftIndex = (shiftIndex + 5) % 8;
                plainChar |= 0xff & (plainDigit >>> shiftIndex);
                decoded[plainPos++] = (byte) plainChar;
                plainChar = 0xff & (plainDigit << (8 - shiftIndex));
            }
        }
        return java.util.Arrays.copyOf(decoded, plainPos);
    }

    /** thirty-two 的 byteTable 原样搬运（80 项）。 */
    private static final int[] BASE32_TABLE = {
            0xff, 0xff, 0x1a, 0x1b, 0x1c, 0x1d, 0x1e, 0x1f,
            0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
            0xff, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06,
            0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e,
            0x0f, 0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16,
            0x17, 0x18, 0x19, 0xff, 0xff, 0xff, 0xff, 0xff,
            0xff, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06,
            0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e,
            0x0f, 0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16,
            0x17, 0x18, 0x19, 0xff, 0xff, 0xff, 0xff, 0xff
    };

    private static String base32Encode(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                bits -= 5;
                sb.append(ALPHABET.charAt((buffer >> bits) & 0x1f));
            }
        }
        if (bits > 0) {
            sb.append(ALPHABET.charAt((buffer << (5 - bits)) & 0x1f));
        }
        return sb.toString();
    }

    /**
     * JS {@code encodeURIComponent} 的等价实现：只放行 {@code A-Za-z0-9 - _ . ! ~ * ' ( )}，
     * 其余按 UTF-8 逐字节 %XX（原版 keyuri 用它拼 label 与 issuer）。
     */
    private static String encodeUriComponent(String value) {
        StringBuilder sb = new StringBuilder();
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xff;
            boolean unreserved = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '-' || c == '_' || c == '.' || c == '!' || c == '~' || c == '*' || c == '\'' || c == '(' || c == ')';
            if (unreserved) {
                sb.append((char) c);
            } else {
                sb.append('%').append(Character.toUpperCase(HEX[(c >> 4) & 0x0f])).append(Character.toUpperCase(HEX[c & 0x0f]));
            }
        }
        return sb.toString();
    }
}
