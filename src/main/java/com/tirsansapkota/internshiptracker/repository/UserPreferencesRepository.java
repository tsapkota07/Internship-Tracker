package com.tirsansapkota.internshiptracker.repository;

import com.tirsansapkota.internshiptracker.model.UserPreferences;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPreferencesRepository extends JpaRepository<UserPreferences, Long> {
}