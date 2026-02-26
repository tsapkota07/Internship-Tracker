package com.tirsansapkota.internshiptracker.repository;

import com.tirsansapkota.internshiptracker.model.InternshipApplication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InternshipApplicationRepository extends JpaRepository<InternshipApplication, Long> {

    List<InternshipApplication> findAllByOwnerUsername(String username);

    List<InternshipApplication> findAllByOwnerUsernameAndDeletedAtIsNull(String username);

    List<InternshipApplication> findAllByOwnerUsernameAndDeletedAtIsNotNull(String username);

    Optional<InternshipApplication> findByIdAndOwnerUsernameAndDeletedAtIsNull(Long id, String username);

    Optional<InternshipApplication> findByIdAndOwnerUsername(Long id, String username); // for restore/perma-delete

    void deleteAllByOwnerUsername(String username);
}