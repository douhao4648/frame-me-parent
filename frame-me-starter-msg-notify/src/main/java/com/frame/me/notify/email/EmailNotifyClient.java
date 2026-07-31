package com.frame.me.notify.email;

import com.frame.me.notify.api.INotifyClient;
import com.frame.me.notify.api.INotifyTemplateEngine;
import com.frame.me.notify.config.EmailChannelProperties;
import com.frame.me.notify.model.NotifyChannelType;
import com.frame.me.notify.model.NotifyMessage;
import com.frame.me.notify.model.NotifyResult;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Properties;

/**
 * 邮件通知客户端实现.
 */
@Slf4j
public class EmailNotifyClient implements INotifyClient {

    private final String name;
    private final EmailChannelProperties properties;
    private final Session session;
    private final List<INotifyTemplateEngine> templateEngines;
    private final boolean includeErrorDetail;

    public EmailNotifyClient(String name, EmailChannelProperties properties,
                             List<INotifyTemplateEngine> templateEngines) {
        this(name, properties, templateEngines, false);
    }

    public EmailNotifyClient(String name, EmailChannelProperties properties,
                             List<INotifyTemplateEngine> templateEngines,
                             boolean includeErrorDetail) {
        this.name = name;
        this.properties = properties;
        this.session = createSession(properties);
        this.templateEngines = templateEngines == null ? List.of() : templateEngines;
        this.includeErrorDetail = includeErrorDetail;
    }

    private Session createSession(EmailChannelProperties props) {
        Properties mailProps = new Properties();
        mailProps.put("mail.smtp.host", props.getHost());
        mailProps.put("mail.smtp.port", String.valueOf(props.getPort()));
        mailProps.put("mail.smtp.auth", String.valueOf(props.isAuth()));
        mailProps.put("mail.smtp.starttls.enable", String.valueOf(props.isStartTls()));
        mailProps.put("mail.smtp.connectiontimeout", String.valueOf(props.getConnectionTimeout()));
        mailProps.put("mail.smtp.timeout", String.valueOf(props.getReadTimeout()));
        if (props.isSsl()) {
            mailProps.put("mail.smtp.ssl.enable", "true");
        }

        if (props.isAuth()) {
            return Session.getInstance(mailProps, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(props.getUsername(), props.getPassword());
                }
            });
        }
        return Session.getInstance(mailProps);
    }

    @Override
    public NotifyResult send(NotifyMessage message) {
        try {
            String content = resolveContent(message);
            MimeMessage mimeMessage = buildMimeMessage(message, content);
            Transport.send(mimeMessage);
            log.debug("Email sent via client '{}': to={}, subject={}",
                    name, message.getReceivers(), message.getTitle());
            return NotifyResult.ok(mimeMessage.getMessageID());
        } catch (Exception e) {
            log.error("Email send failed via client '{}': {}", name, e.getMessage(), e);
            String failMessage = includeErrorDetail ? e.getMessage() : "Email send failed";
            return NotifyResult.fail("EMAIL_SEND_ERROR", failMessage);
        }
    }

    private String resolveContent(NotifyMessage message) {
        String template = message.getTemplate();
        if (template == null || template.isEmpty()) {
            return message.getContent();
        }

        String templateType = message.getTemplateType();
        for (INotifyTemplateEngine engine : templateEngines) {
            if (engine.supports(templateType)) {
                return engine.render(template, message.getTemplateParams());
            }
        }
        // 无引擎认领（如显式指定 freemarker 但 optional 依赖缺席）：
        // 告警后返回原文，避免"静默成功"地发出未渲染的模板
        log.warn("No template engine supports type '{}', sending raw template via client '{}'", templateType, name);
        return template;
    }

    private MimeMessage buildMimeMessage(NotifyMessage message, String content)
            throws MessagingException, IOException {
        MimeMessage mimeMessage = new MimeMessage(session);
        mimeMessage.setFrom(new InternetAddress(properties.getFrom(), properties.getFromName()));

        addRecipients(mimeMessage, Message.RecipientType.TO, message.getReceivers());
        addRecipients(mimeMessage, Message.RecipientType.CC, message.getCc());
        addRecipients(mimeMessage, Message.RecipientType.BCC, message.getBcc());

        mimeMessage.setSubject(message.getTitle());

        boolean hasAttachments = message.getAttachments() != null && !message.getAttachments().isEmpty();
        boolean html = isHtml(message) || message.getTemplate() != null;

        if (hasAttachments) {
            mimeMessage.setContent(buildMultipart(content, message, html));
        } else if (html) {
            mimeMessage.setContent(content, "text/html;charset=UTF-8");
        } else {
            mimeMessage.setText(content);
        }

        return mimeMessage;
    }

    private void addRecipients(MimeMessage mimeMessage, Message.RecipientType type, List<String> recipients)
            throws MessagingException {
        if (recipients == null) {
            return;
        }
        for (String recipient : recipients) {
            mimeMessage.addRecipient(type, new InternetAddress(recipient));
        }
    }

    private boolean isHtml(NotifyMessage message) {
        if (message.getExtras() == null) {
            return false;
        }
        Object html = message.getExtras().get("html");
        return Boolean.TRUE.equals(html) || "true".equals(html);
    }

    private Multipart buildMultipart(String content, NotifyMessage message, boolean html)
            throws MessagingException, IOException {
        Multipart multipart = new MimeMultipart();
        BodyPart textPart = new MimeBodyPart();
        if (html) {
            textPart.setContent(content, "text/html;charset=UTF-8");
        } else {
            textPart.setText(content);
        }
        multipart.addBodyPart(textPart);

        for (String path : message.getAttachments()) {
            // 防任意文件读取：拒绝绝对路径（如 /etc/passwd）与路径穿越（../），
            // 附件路径应为相对路径（相对工作目录或配置的附件根目录）
            if (path == null || path.isBlank()) {
                continue;
            }
            File file = new File(path);
            String canonical = file.getCanonicalPath();
            // 补分隔符再比较，防前缀绕过：workdir=/opt/app 时 ../app-backup/x 规范化后
            // 仍以 /opt/app 开头，纯 startsWith(workdir) 会误放行兄弟目录
            String workdir = new File(".").getCanonicalPath() + File.separator;
            if (!canonical.startsWith(workdir)) {
                log.warn("Skip attachment outside workdir: {}", path);
                continue;
            }
            if (!file.exists() || !file.isFile()) {
                log.warn("Skip attachment not found: {}", path);
                continue;
            }
            MimeBodyPart attachmentPart = new MimeBodyPart();
            attachmentPart.attachFile(file);
            multipart.addBodyPart(attachmentPart);
        }
        return multipart;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getChannelType() {
        return NotifyChannelType.EMAIL.getCode();
    }

    @Override
    public boolean isAvailable() {
        return session != null;
    }
}
