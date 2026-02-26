package com.tirsansapkota.internshiptracker.service;

import com.tirsansapkota.internshiptracker.model.AppUser;
import com.tirsansapkota.internshiptracker.model.ApplicationStatus;
import com.tirsansapkota.internshiptracker.model.InternshipApplication;
import com.tirsansapkota.internshiptracker.repository.InternshipApplicationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class InternshipApplicationService
{

    private final InternshipApplicationRepository repository;

    public InternshipApplicationService(InternshipApplicationRepository repository)
    {
        this.repository = repository;
    }

    public List<InternshipApplication> getAll()
    {
        return repository.findAll();
    }

    public InternshipApplication save(InternshipApplication app)
    {
        return repository.save(app);
    }

    public void deleteById(Long id)
    {
        repository.deleteById(id);
    }

    public Optional<InternshipApplication> getById(Long id)
    {
        return repository.findById(id);
    }

    public List<InternshipApplication> filter(String q, ApplicationStatus status)
    {
        List<InternshipApplication> all = repository.findAll();

        String query = (q == null) ? "" : q.trim().toLowerCase();
        boolean hasQuery = !query.isEmpty();

        return all.stream()
                .filter(app -> status == null || app.getStatus() == status)
                .filter(app ->
                {
                    if (!hasQuery)
                        return true;

                    String company = app.getCompany() == null ? "" : app.getCompany().toLowerCase();
                    String role = app.getRole() == null ? "" : app.getRole().toLowerCase();

                    return company.contains(query) || role.contains(query);
                })
                .toList();
    }

    // user owned versions:

    public List<InternshipApplication> filterForUser(String username, String q, ApplicationStatus status)
    {
        List<InternshipApplication> all = repository.findAllByOwnerUsernameAndDeletedAtIsNull(username);

        String query = (q == null) ? "" : q.trim().toLowerCase();
        boolean hasQuery = !query.isEmpty();

        return all.stream()
                .filter(app -> status == null || app.getStatus() == status)
                .filter(app ->
                {
                    if (!hasQuery)
                        return true;

                    String company = app.getCompany() == null ? "" : app.getCompany().toLowerCase();
                    String role = app.getRole() == null ? "" : app.getRole().toLowerCase();

                    return company.contains(query) || role.contains(query);
                })
                .toList();
    }

    public List<InternshipApplication> getAllForUser(String username) {
        return repository.findAllByOwnerUsernameAndDeletedAtIsNull(username);
    }

    public Optional<InternshipApplication> getByIdForUser(Long id, String username) {
        return repository.findByIdAndOwnerUsernameAndDeletedAtIsNull(id, username);
    }

    public List<InternshipApplication> getDeletedForUser(String username) {
        return repository.findAllByOwnerUsernameAndDeletedAtIsNotNull(username);
    }


    @Transactional
    public void softDeleteByIdForUser(Long id, String username) {
        InternshipApplication app = repository
                .findByIdAndOwnerUsernameAndDeletedAtIsNull(id, username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        app.setDeletedAt(LocalDateTime.now());
        repository.save(app);
    }

    @Transactional
    public void restoreByIdForUser(Long id, String username) {
        InternshipApplication app = repository
                .findByIdAndOwnerUsername(id, username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        app.setDeletedAt(null);
        repository.save(app);
    }

    @Transactional
    public void hardDeleteByIdForUser(Long id, String username) {
        InternshipApplication app = repository
                .findByIdAndOwnerUsername(id, username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        repository.delete(app);
    }
}
