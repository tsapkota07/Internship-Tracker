package com.tirsansapkota.internshiptracker.model;

import jakarta.persistence.*;

@Entity
@Table(name = "user_preferences")
public class UserPreferences {

    @Id
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Theme theme = Theme.LIGHT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = true)
    private ApplicationStatus defaultStatus;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public AppUser getUser() { return user; }
    public void setUser(AppUser user) { this.user = user; }

    public Theme getTheme() { return theme; }
    public void setTheme(Theme theme) { this.theme = theme; }

    public ApplicationStatus getDefaultStatus() { return defaultStatus; }
    public void setDefaultStatus(ApplicationStatus defaultStatus) { this.defaultStatus = defaultStatus; }
}