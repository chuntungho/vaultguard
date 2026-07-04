package com.vaultguard.service;

import com.vaultguard.config.VaultGuardProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Thin wrapper around the (optional) JavaMailSender. When SMTP is not configured the
 * server degrades gracefully, mirroring the Rust implementation's mail_enabled() checks.
 */
@Service
public class MailService {

    private final JavaMailSender mailSender;
    private final VaultGuardProperties props;

    public MailService(ObjectProvider<JavaMailSender> mailSender, VaultGuardProperties props) {
        this.mailSender = mailSender.getIfAvailable();
        this.props = props;
    }

    public boolean isEnabled() {
        return mailSender != null;
    }

    /** Sends a plain-text mail; returns false when mail is disabled or sending failed. */
    public boolean send(String to, String subject, String text) {
        if (mailSender == null) return false;
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(props.getMail().getFrom());
        msg.setTo(to);
        msg.setSubject(subject);
        msg.setText(text);
        try {
            mailSender.send(msg);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
