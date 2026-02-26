package com.tirsansapkota.internshiptracker.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * GlobalExceptionHandler
 *
 * Central place to map certain exceptions to friendly error pages.
 * This keeps controllers cleaner and prevents repeating try/catch everywhere.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Spring Security throws AccessDeniedException when a user is logged in
     * but does not have permission to access a route/resource.
     *
     * Example: hitting an "account-only" page without proper authorization.
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String handleAccessDenied() {
        return "error/403";
    }

    /*
    // Optional: if you want a friendly 500 page in production
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleAnyUnhandledException(Exception ex) {
        // You can log ex here if you want
        return "error/500";
    }
    */
}