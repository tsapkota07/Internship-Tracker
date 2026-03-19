package com.tirsansapkota.internshiptracker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.io.InputStream;

import jakarta.mail.internet.MimeMessage;

/**
 * Local-only config. Provides a no-op mail sender so the app starts
 * without real SMTP credentials. Emails are logged and discarded.
 */
@Configuration
@Profile("local")
public class LocalConfig {

    @Bean
    public JavaMailSender javaMailSender() {
        return new JavaMailSender() {
            @Override
            public MimeMessage createMimeMessage() {
                return new JavaMailSenderImpl().createMimeMessage();
            }

            @Override
            public MimeMessage createMimeMessage(InputStream contentStream) {
                return new JavaMailSenderImpl().createMimeMessage(contentStream);
            }

            @Override
            public void send(MimeMessage mimeMessage) throws MailException {}

            @Override
            public void send(MimeMessage... mimeMessages) throws MailException {}

            @Override
            public void send(SimpleMailMessage simpleMessage) throws MailException {
                System.out.println("[LOCAL MAIL] To: " + String.join(",", simpleMessage.getTo())
                        + " | Subject: " + simpleMessage.getSubject()
                        + "\n" + simpleMessage.getText());
            }

            @Override
            public void send(SimpleMailMessage... simpleMessages) throws MailException {}
        };
    }
}
