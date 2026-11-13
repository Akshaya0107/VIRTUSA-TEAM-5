package com.example.reminder.controller;

import com.example.reminder.dto.ApiModels.*;
import com.example.reminder.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AuthController {
    private final AuthService auth;
    public AuthController(AuthService auth) { this.auth=auth; }
    @PostMapping("/auth/register") public ResponseEntity<UserView> register(@Valid @RequestBody RegisterRequest r) { return ResponseEntity.status(201).body(auth.register(r)); }
    @PostMapping("/auth/login") public TokenView login(@Valid @RequestBody LoginRequest r) { return auth.login(r); }
    @PostMapping("/auth/logout") @ResponseStatus(HttpStatus.NO_CONTENT) public void logout(@AuthenticationPrincipal Jwt jwt) { auth.logout(Long.valueOf(jwt.getSubject())); }
    @GetMapping("/profile") public UserView profile(@AuthenticationPrincipal Jwt jwt) { return auth.profile(Long.valueOf(jwt.getSubject())); }
    @PutMapping("/profile") public UserView update(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody ProfileRequest r) { return auth.updateProfile(Long.valueOf(jwt.getSubject()),r); }
}
