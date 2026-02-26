package com.tirsansapkota.internshiptracker.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "verification_tokens")
public class VerificationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable=false, unique=true, length=80)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable=false)
    private TokenType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="user_id", nullable=false)
    private AppUser user;

    @Column(nullable=false)
    private LocalDateTime expiresAt;

    @Column(nullable=false)
    private boolean used = false;

    private LocalDateTime usedAt;

    private String payload;

    public boolean isUsed() { return used; }
    public boolean isExpired() { return LocalDateTime.now().isAfter(expiresAt); }

    // getters/setters
    public Long getId() { return id; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public TokenType getType() { return type; }
    public void setType(TokenType type) { this.type = type; }

    public AppUser getUser() { return user; }
    public void setUser(AppUser user) { this.user = user; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

//    public boolean getUsed() { return used; } // optional
    public void setUsed(boolean used) { this.used = used; }

    public LocalDateTime getUsedAt() { return usedAt; }
    public void setUsedAt(LocalDateTime usedAt) { this.usedAt = usedAt; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
}