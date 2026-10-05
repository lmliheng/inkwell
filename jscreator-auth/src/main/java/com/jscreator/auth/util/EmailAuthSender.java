package com.jscreator.auth.util;

import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Properties;

/**
 * 验证码邮件发送，对齐 Express 版 legacy-utils/email-sender（nodemailer）：
 *
 * <ul>
 *   <li>SMTP 参数全部来自环境变量：SMTP_HOST / SMTP_PORT / SMTP_SECURE / SMTP_USER / SMTP_PASS / FROM_EMAIL</li>
 *   <li><b>发送失败一律内部吞掉</b>（原版 sendMail 的 try/catch 就是吞错的），所以 /email/send-code
 *       在没配好 SMTP 时仍然返回 200「验证码已发送，请查收邮件」——这一点与原版一致</li>
 *   <li>正文 HTML 文案逐字复制原版</li>
 * </ul>
 *
 * 没有改 application.yml（共享文件），配置直接读环境变量；未配置时行为等价于原版的无密钥分支。
 */
@Component
public class EmailAuthSender {

    private static final Logger log = LoggerFactory.getLogger(EmailAuthSender.class);

    private final String host;
    private final String port;
    private final String secure;
    private final String user;
    private final String pass;
    private final String from;

    public EmailAuthSender(@Value("${SMTP_HOST:}") String host,
                           @Value("${SMTP_PORT:}") String port,
                           @Value("${SMTP_SECURE:}") String secure,
                           @Value("${SMTP_USER:}") String user,
                           @Value("${SMTP_PASS:}") String pass,
                           @Value("${FROM_EMAIL:}") String from) {
        this.host = host;
        this.port = port;
        this.secure = secure;
        this.user = user;
        this.pass = pass;
        this.from = from;
    }

    /** 与 nodemailer 的 from/host/port/secure/auth 一一对应；异常不抛出（原版吞错）。 */
    public void sendVerificationCode(String to, String code) {
        String html = """
                <div style="font-family: Arial, sans-serif; max-width: 400px; margin: auto; padding: 20px; border: 1px solid #eee; border-radius: 8px;">
                    <h2 style="color: #333;">JScreator 登录验证码</h2>
                    <p style="font-size: 16px;">你的验证码是：</p>
                    <p style="font-size: 28px; font-weight: bold; color: #409eff; letter-spacing: 4px;">%s</p>
                    <p style="color: #999; font-size: 13px;">验证码 5 分钟内有效，请勿泄露给他人。</p>
                </div>
                """.formatted(code);
        try {
            if (host == null || host.isBlank()) {
                // 原版 host 为空时 nodemailer 也会失败（DNS 查空主机名）
                throw new IllegalStateException("SMTP_HOST 未配置");
            }
            Properties props = new Properties();
            props.put("mail.smtp.host", host);
            if (port != null && !port.isBlank()) {
                props.put("mail.smtp.port", port.trim());
            }
            if ("true".equals(secure)) {
                props.put("mail.smtp.ssl.enable", "true");
            }
            // nodemailer 的 connectionTimeout 默认 2 分钟，这里收紧到 30s，避免请求长时间挂住
            props.put("mail.smtp.connectiontimeout", "30000");
            props.put("mail.smtp.timeout", "30000");
            if (user != null && !user.isEmpty()) {
                props.put("mail.smtp.auth", "true");
            }

            Session session = Session.getInstance(props,
                    (user != null && !user.isEmpty())
                            ? new jakarta.mail.Authenticator() {
                                @Override
                                protected jakarta.mail.PasswordAuthentication getPasswordAuthentication() {
                                    return new jakarta.mail.PasswordAuthentication(user, pass);
                                }
                            }
                            : null);

            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(from == null ? "" : from));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            message.setSubject("【JScreator】登录验证码", "UTF-8");
            message.setContent(html, "text/html;charset=UTF-8");
            Transport.send(message);
        } catch (Exception e) {
            log.error("发送验证码邮件失败: {}", e.getMessage());
        }
    }
}
