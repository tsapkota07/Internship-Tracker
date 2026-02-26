package com.tirsansapkota.internshiptracker.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

@Entity
@Table(name = "app_users")
public class AppUser {

    @Column(nullable = false)
    private boolean demoUser = false;

    public boolean isDemoUser() { return demoUser; }
    public void setDemoUser(boolean demoUser) { this.demoUser = demoUser; }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    @NotBlank(message = "Username is required")
    private String username;

    @Column(nullable = false)
    @NotBlank(message = "Password is required")
    private String passwordHash;

    @Column(nullable = true, unique = true)
    private String email;

    @Column(nullable = false)
    private boolean emailVerified = false;

    @Column(nullable = true, unique = false)
    private String pendingEmail;

    private String role = "USER";

    public AppUser() {}
    public AppUser(String username, String passwordHash) {
        this.username = username;
        this.passwordHash = passwordHash;
    }

    // getters/setters...

    public Long getId() { return id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }


    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public boolean isEmailVerified() { return emailVerified; }
    public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }

    public String getPendingEmail() { return pendingEmail; }
    public void setPendingEmail(String pendingEmail) { this.pendingEmail = pendingEmail; }
}