package com.tirsansapkota.internshiptracker.config;

import jakarta.mail.internet.MimeMessage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.io.InputStream;

/**
 * No-op mail sender for the "test" profile.
 * Prevents Spring Boot from requiring a real SMTP host during tests.
 */
@Configuration
@Profile("test")
public class TestMailConfig {

    @Bean
    public JavaMailSender javaMailSender() {
        return new JavaMailSender() {
            @Override public MimeMessage createMimeMessage() {
                return new JavaMailSenderImpl().createMimeMessage();
            }
            @Override public MimeMessage createMimeMessage(InputStream in) {
                return new JavaMailSenderImpl().createMimeMessage(in);
            }
            @Override public void send(MimeMessage m) throws MailException {}
            @Override public void send(MimeMessage... ms) throws MailException {}
            @Override public void send(SimpleMailMessage m) throws MailException {}
            @Override public void send(SimpleMailMessage... ms) throws MailException {}
        };
    }
}
