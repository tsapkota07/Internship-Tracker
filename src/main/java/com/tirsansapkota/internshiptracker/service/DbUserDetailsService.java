package com.tirsansapkota.internshiptracker.service;

import com.tirsansapkota.internshiptracker.repository.UserRepository;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service
public class DbUserDetailsService implements UserDetailsService {

    private final UserRepository users;

    public DbUserDetailsService(UserRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String login) throws UsernameNotFoundException {

        var userOpt = users.findByUsername(login);

        // allow login by email too
        if (userOpt.isEmpty()) {
            userOpt = users.findByEmail(login);
        }

        var user = userOpt.orElseThrow(() -> new UsernameNotFoundException("User not found"));

        String role = user.getRole();
        if (role != null && role.startsWith("ROLE_")) {
            role = role.substring("ROLE_".length());
        }

        return User.withUsername(user.getUsername())
                .password(user.getPasswordHash())
                .roles(role == null ? "USER" : role)
                .build();
    }
}