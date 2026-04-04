package com.tirsansapkota.internshiptracker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            LoginSuccessHandler loginSuccessHandler // <— inject your handler
    ) throws Exception {

        http.authorizeHttpRequests(auth -> auth

                // ---------------------------
                // PUBLIC (no auth required)
                // ---------------------------
                .requestMatchers(
                        "/",
                        "/css/**",
                        "/js/**",

                        "/login",
                        "/register",
                        "/forgot-password",
                        "/reset-password",

                        "/logout-success",
                        "/contact",

                        // email verification links from emails
                        "/account/verify-email/**"
                ).permitAll()

                // ---------------------------
                // GUEST-ALLOWED (your app pages allowed for guests)
                // ---------------------------
                .requestMatchers(
                        "/apps",
                        "/apps/new",
                        "/apps/{id}/edit"
                ).permitAll()
                .requestMatchers(HttpMethod.POST,
                        "/apps",
                        "/apps/{id}/edit",
                        "/apps/{id}/delete"
                ).permitAll()

                // ---------------------------
                // DEV ONLY
                // ---------------------------
                .requestMatchers("/dev/**").hasRole("DEV")

                // ---------------------------
                // AUTHENTICATED ONLY
                // ---------------------------
                .requestMatchers(
                        "/apps/deleted",
                        "/apps/import-guest",
                        "/apps/import-csv",
                        "/apps/import-csv/**",
                        "/account/**",
                        "/preferences",
                        "/export",
                        "/export/**"
                ).authenticated()

                .anyRequest().authenticated()
        );

        http.formLogin(form -> form
                .loginPage("/login")

                // If you use a success handler, DON'T also force defaultSuccessUrl(true)
                .successHandler(loginSuccessHandler)

                .failureHandler((request, response, exception) -> {
                    if (exception instanceof DisabledException) {
                        response.sendRedirect("/login?unverified");
                    } else {
                        response.sendRedirect("/login?error");
                    }
                })
                .permitAll()
        );

        http.logout(logout -> logout
                .logoutSuccessUrl("/logout-success")
        );

        http.sessionManagement(sm -> sm
                .sessionFixation(sf -> sf.migrateSession())
        );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}