package com.tirsansapkota.internshiptracker.service;

import com.tirsansapkota.internshiptracker.model.*;
import com.tirsansapkota.internshiptracker.repository.VerificationTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class VerificationService {

    private final VerificationTokenRepository tokens;

    public VerificationService(VerificationTokenRepository tokens) {
        this.tokens = tokens;
    }

    public VerificationToken createToken(AppUser user, TokenType type, int minutesValid, String payload) {
        VerificationToken t = new VerificationToken();
        t.setUser(user);
        t.setType(type);
        t.setToken(UUID.randomUUID().toString());
        t.setExpiresAt(LocalDateTime.now().plusMinutes(minutesValid));
        t.setPayload(payload);
        return tokens.save(t);
    }

    // without payload.
    public VerificationToken createToken(AppUser user, TokenType type, int minutesValid) {
        return createToken(user, type, minutesValid, null);
    }

    public VerificationToken requireValid(String token, TokenType type) {
        VerificationToken t = tokens.findByTokenAndType(token, type)
                .orElseThrow(() -> new IllegalArgumentException("Invalid token"));

        if (t.isUsed()) throw new IllegalArgumentException("Token already used");
        if (t.isExpired()) throw new IllegalArgumentException("Token expired");
        return t;
    }

    // Overloaded method that only accepts token and disregards tokenType.
    public VerificationToken requireValid(String token) {
        VerificationToken t = tokens.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid token"));

        if (t.isUsed()) throw new IllegalArgumentException("Token already used");
        if (t.isExpired()) throw new IllegalArgumentException("Token expired");
        return t;
    }

    @Transactional
    public void markUsed(VerificationToken t) {
        if (t.isUsed()) return;

        t.setUsed(true);
        t.setUsedAt(LocalDateTime.now());
        tokens.save(t);
    }

    @Transactional
    public void invalidateActiveTokensForUser(AppUser user, TokenType type) {
        var now = LocalDateTime.now();
        var active = tokens.findActiveByUserAndType(user, type, now);

        for (var t : active) {
            if (!t.isUsed()) {
                t.setUsed(true);
                t.setUsedAt(now);
                tokens.save(t);
            }
        }
    }
}