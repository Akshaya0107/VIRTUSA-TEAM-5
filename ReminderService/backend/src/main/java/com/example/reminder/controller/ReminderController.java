package com.example.reminder.controller;

import com.example.reminder.dto.ApiModels.*;
import com.example.reminder.service.ReminderService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ReminderController {
    private final ReminderService service;
    public ReminderController(ReminderService service) { this.service=service; }
    private Long owner(Jwt jwt) { return Long.valueOf(jwt.getSubject()); }
    @PostMapping("/reminders") public ResponseEntity<ReminderView> create(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody ReminderRequest r) { return ResponseEntity.status(201).body(service.create(owner(jwt),r)); }
    @GetMapping("/reminders") public PageView<ReminderView> list(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return service.list(owner(jwt),page,size); }
    @GetMapping("/reminders/{id}") public ReminderView get(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id) { return service.get(owner(jwt),id); }
    @PutMapping("/reminders/{id}") public ReminderView update(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id,@Valid @RequestBody ReminderRequest r) { return service.update(owner(jwt),id,r); }
    @PostMapping("/reminders/{id}/cancel") public ReminderView cancel(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id) { return service.cancel(owner(jwt),id); }
    @DeleteMapping("/reminders/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id) { service.delete(owner(jwt),id); }
    @GetMapping("/reminders/history") public PageView<LogView> history(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return service.history(owner(jwt),null,page,size); }
    @GetMapping("/email-logs/{id}") public PageView<LogView> logs(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return service.history(owner(jwt),id,page,size); }
    @GetMapping("/dashboard") public DashboardView dashboard(@AuthenticationPrincipal Jwt jwt) { return service.dashboard(owner(jwt)); }
}
