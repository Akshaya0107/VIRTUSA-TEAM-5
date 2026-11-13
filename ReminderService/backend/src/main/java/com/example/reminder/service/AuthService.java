package com.example.reminder.service;

import com.example.reminder.dto.ApiModels.*;
import com.example.reminder.entity.User;
import com.example.reminder.exception.ApiException;
import com.example.reminder.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository users; private final PasswordEncoder passwords; private final JwtEncoder encoder;
    private final Clock clock; private final long ttl; private final String dummyHash;
    public AuthService(UserRepository users,PasswordEncoder passwords,JwtEncoder encoder,Clock clock,
                       @Value("${app.jwt.ttl-seconds:900}") long ttl) {
        this.users=users;this.passwords=passwords;this.encoder=encoder;this.clock=clock;this.ttl=ttl;
        if(ttl<60 || ttl>3600) throw new IllegalStateException("JWT TTL must be 60-3600 seconds");
        this.dummyHash=passwords.encode(UUID.randomUUID().toString());
    }
    @Transactional public UserView register(RegisterRequest r) {
        checkPassword(r.password()); String email=normalize(r.email()); ZoneId.of(r.timeZone());
        if(users.existsByEmail(email)) throw new ApiException(HttpStatus.CONFLICT,"Email is already registered");
        return UserView.from(users.saveAndFlush(new User(email,passwords.encode(r.password()),r.displayName(),r.timeZone())));
    }
    @Transactional(readOnly=true) public TokenView login(LoginRequest r) {
        checkPassword(r.password()); var user=users.findByEmail(normalize(r.email()));
        boolean valid=passwords.matches(r.password(),user.map(User::getPasswordHash).orElse(dummyHash));
        if(!valid || user.isEmpty()) throw new ApiException(HttpStatus.UNAUTHORIZED,"Invalid email or password");
        User u=user.get(); Instant now=clock.instant(),expires=now.plusSeconds(ttl);
        var claims=JwtClaimsSet.builder().issuer("smart-reminder").subject(u.getId().toString())
                .issuedAt(now).expiresAt(expires).id(UUID.randomUUID().toString()).claim("v",u.getTokenVersion()).build();
        String token=encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue();
        return new TokenView(token,"Bearer",expires,UserView.from(u));
    }
    @Transactional public void logout(Long id) { users.revokeTokens(id,clock.instant()); }
    @Transactional(readOnly=true) public UserView profile(Long id) { return UserView.from(user(id)); }
    @Transactional public UserView updateProfile(Long id,ProfileRequest r) { User u=user(id);u.updateProfile(r.displayName(),r.timeZone());return UserView.from(u); }
    private User user(Long id) { return users.findById(id).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"User not found")); }
    private String normalize(String email) { return email.strip().toLowerCase(Locale.ROOT); }
    private void checkPassword(String p) { if(p.getBytes(StandardCharsets.UTF_8).length>72) throw new ApiException(HttpStatus.BAD_REQUEST,"Password must be at most 72 UTF-8 bytes"); }
}
