package edu.whu.developer.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.stereotype.Service;

@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String from;

    public MailService(ObjectProvider<JavaMailSender> mailSender,
                       @Value("${app.mail.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    public void send(String to, String subject, String body) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.info("DEV MAIL to={} subject={} body={}", to, subject, body);
            return;
        }
        try {
            deliver(sender, to, subject, body);
        } catch (RuntimeException ex) {
            log.warn("SMTP send failed, kept as log. to={} subject={}", to, subject, ex);
            log.info("DEV MAIL to={} subject={} body={}", to, subject, body);
        }
    }

    public void sendRequired(String to, String subject, String body) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.error("Required mail delivery is unavailable: SMTP is not configured");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "验证码邮件暂时无法发送，请稍后重试");
        }
        try {
            deliver(sender, to, subject, body);
        } catch (MailException ex) {
            log.warn("Required SMTP delivery failed: {}", ex.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "验证码邮件暂时无法发送，请稍后重试");
        }
    }

    private void deliver(JavaMailSender sender, String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        sender.send(message);
    }
}
