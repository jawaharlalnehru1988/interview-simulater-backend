package com.asknehru.interviewsimulator.core;

import com.asknehru.interviewsimulator.auth.dto.AuthResponse;
import com.asknehru.interviewsimulator.auth.dto.LoginRequest;
import com.asknehru.interviewsimulator.auth.dto.RegisterRequest;
import com.asknehru.interviewsimulator.auth.User;
import com.asknehru.interviewsimulator.auth.UserRepository;
import com.asknehru.interviewsimulator.auth.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final AuthenticationManager authenticationManager;

    @PostMapping("/interview/auth/register/")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            return ResponseEntity.badRequest().body(Map.of("detail", "Username already exists"));
        }

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
            .firstName(request.getFirstName() != null ? request.getFirstName() : "")
            .lastName(request.getLastName() != null ? request.getLastName() : "")
                .isActive(true)
                .build();

        User savedUser = userRepository.save(user);
        return ResponseEntity.ok(Map.of(
                "id", savedUser.getId(),
                "username", savedUser.getUsername()
        ));
    }

    @PostMapping("/auth/token/")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow();
        String token = jwtUtils.generateToken(user);
        return ResponseEntity.ok(AuthResponse.builder().access(token).refresh(token).build());
    }

    @PostMapping("/auth/token/refresh/")
    public ResponseEntity<?> refresh(@RequestBody Map<String, String> requestBody) {
        String refresh = requestBody.get("refresh");
        if (refresh == null || refresh.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("detail", "Refresh token is required"));
        }
        try {
            String username = jwtUtils.extractUsername(refresh);
            User user = userRepository.findByUsername(username).orElseThrow();
            String newToken = jwtUtils.generateToken(new org.springframework.security.core.userdetails.User(
                    user.getUsername(), user.getPassword(), new java.util.ArrayList<>()));
            return ResponseEntity.ok(Map.of("access", newToken));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("detail", "Invalid or expired refresh token"));
        }
    }
}

