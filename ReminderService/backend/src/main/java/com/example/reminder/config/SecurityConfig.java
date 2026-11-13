package com.example.reminder.config;

import com.example.reminder.exception.ApiError;
import com.example.reminder.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean SecretKey jwtKey(@Value("${app.jwt.secret}") String secret) {
        byte[] bytes=secret.getBytes(StandardCharsets.UTF_8);
        if(bytes.length<32) throw new IllegalStateException("JWT_SECRET must be at least 32 UTF-8 bytes");
        return new SecretKeySpec(bytes,"HmacSHA256");
    }
    @Bean JwtEncoder jwtEncoder(SecretKey key) { return new NimbusJwtEncoder(new ImmutableSecret<>(key)); }
    @Bean JwtDecoder jwtDecoder(SecretKey key,UserRepository users) {
        NimbusJwtDecoder decoder=NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> version=jwt->{
            try {
                var user=users.findById(Long.parseLong(jwt.getSubject()));
                Number v=jwt.getClaim("v");
                if(user.isPresent() && v!=null && user.get().getTokenVersion()==v.longValue()) return OAuth2TokenValidatorResult.success();
            } catch(RuntimeException ignored) { }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token","Token is revoked or user no longer exists",null));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer("smart-reminder"),version));
        return decoder;
    }
    @Bean SecurityFilterChain security(HttpSecurity http,ObjectMapper mapper,CorsConfigurationSource cors) throws Exception {
        return http.csrf(c->c.disable()).cors(c->c.configurationSource(cors))
                .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a->a
                        .requestMatchers(HttpMethod.POST,"/api/auth/register","/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET,"/api/health","/actuator/health","/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o->o.jwt(j->{})
                        .authenticationEntryPoint((r,s,e)->{s.setStatus(401);s.setContentType("application/json");mapper.writeValue(s.getOutputStream(),ApiError.of(401,"Authentication required or token invalid",r.getRequestURI()));}))
                .exceptionHandling(e->e
                        .authenticationEntryPoint((r,s,x)->{s.setStatus(401);s.setContentType("application/json");mapper.writeValue(s.getOutputStream(),ApiError.of(401,"Authentication required",r.getRequestURI()));})
                        .accessDeniedHandler((r,s,x)->{s.setStatus(403);s.setContentType("application/json");mapper.writeValue(s.getOutputStream(),ApiError.of(403,"Access denied",r.getRequestURI()));}))
                .build();
    }
    @Bean CorsConfigurationSource cors(@Value("${app.cors.origins:http://localhost:5173,http://127.0.0.1:5173}") String origins) {
        CorsConfiguration c=new CorsConfiguration(); c.setAllowedOrigins(List.of(origins.split(",")));
        c.setAllowedMethods(List.of("GET","POST","PUT","DELETE","OPTIONS"));
        c.setAllowedHeaders(List.of("Authorization","Content-Type")); c.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source=new UrlBasedCorsConfigurationSource(); source.registerCorsConfiguration("/api/**",c); return source;
    }
}
