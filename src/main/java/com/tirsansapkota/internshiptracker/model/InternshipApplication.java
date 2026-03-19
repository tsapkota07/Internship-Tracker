package com.tirsansapkota.internshiptracker.model;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import java.time.LocalDate;
import java.util.Optional;

// InternshipApplication is an entity.
@Entity
public class InternshipApplication
{


    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // Fields
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "Status is required.")
    @Enumerated(EnumType.STRING)
    private ApplicationStatus status;

    @NotBlank(message = "Company is required.")
    private String company;

    @NotBlank(message = "Role is required.")
    private String role;

    @NotNull(message = "Applied date is required")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate appliedDate;


    private String link;
    private String location;
    @Column(length = 2000)
    @jakarta.validation.constraints.Size(max = 2000, message = "Notes must be 2000 characters or fewer.")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false) // if they're not an user, don't let them into the db.
    private AppUser owner;



    // Constructor
    public InternshipApplication() {
    }


    // Getters and Setters for Fields
    public Long getId()
    {
        return id;
    }

    // setter ID is still present to prevent possible friction later on.
    public void setId(Long id)
    {
        this.id = id;
    }

    public String getCompany()
    {
        return company;
    }

    public void setCompany(String company)
    {
        this.company = company;
    }

    public String getRole()
    {
        return role;
    }

    public void setRole(String role)
    {
        this.role = role;
    }

    public ApplicationStatus getStatus()
    {
        return status;
    }

    public void setStatus(ApplicationStatus status)
    {
        this.status = status;
    }

    public LocalDate getAppliedDate()
    {
        return appliedDate;
    }

    public void setAppliedDate(LocalDate appliedDate)
    {
        this.appliedDate = appliedDate;
    }

    public String getLink()
    {
        return link;
    }

    public void setLink(String link)
    {
        this.link = link;
    }

    public String getNotes()
    {
        return notes;
    }

    public void setNotes(String notes)
    {
        this.notes = notes;
    }

    public String getLocation() {return location;}
    public void setLocation(String location) {this.location = location;}


    public AppUser getOwner() { return owner; }
    public void setOwner(AppUser owner) { this.owner = owner; }

    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }

    public boolean isDeleted() { return deletedAt != null; }


}
