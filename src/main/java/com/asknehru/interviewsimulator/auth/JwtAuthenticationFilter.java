package com.asknehru.interviewsimulator.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Slf4j
@Component("simulatorJwtAuthenticationFilter")
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/interview/") &&
               !path.startsWith("/api/documents/") &&
               !path.startsWith("/api/resume/") &&
               !path.startsWith("/api/roadmap/") &&
               !path.startsWith("/api/aspiration/") &&
               !path.startsWith("/api/coding/") &&
               !path.startsWith("/api/mcq/") &&
               !path.startsWith("/api/candidate/") &&
               !path.startsWith("/api/job-description/") &&
               !path.startsWith("/api/hr-voice/") &&
               !path.startsWith("/api/chat/") &&
               !path.startsWith("/api/repository/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7).trim();
        String username = null;
        String email = null;
        boolean isInternalToken = false;

        // 1. Try standard internal HMAC JWT
        try {
            username = jwtUtils.extractUsername(jwt);
            isInternalToken = true;
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"message\": \"JWT token has expired\"}");
            return;
        } catch (Exception e) {
            // 2. Fall back to parsing as Google ID token / external OAuth JWT payload
            try {
                String[] parts = jwt.split("\\.");
                if (parts.length >= 2) {
                    byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
                    String payloadJson = new String(decoded, StandardCharsets.UTF_8);
                    JsonNode node = objectMapper.readTree(payloadJson);

                    if (node.has("exp")) {
                        long expSeconds = node.get("exp").asLong();
                        if ((System.currentTimeMillis() / 1000) > expSeconds) {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"message\": \"Token has expired\"}");
                            return;
                        }
                    }

                    if (node.has("email") && !node.get("email").isNull()) {
                        email = node.get("email").asText().trim();
                    }
                    if (node.has("name") && !node.get("name").isNull()) {
                        username = node.get("name").asText().trim();
                    } else if (node.has("sub") && !node.get("sub").isNull()) {
                        username = node.get("sub").asText().trim();
                    }
                }
            } catch (Exception parseEx) {
                log.warn("Failed to parse JWT payload: {}", parseEx.getMessage());
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"message\": \"Invalid JWT token\"}");
                return;
            }
        }

        if (username == null && email == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"message\": \"Invalid JWT token: no identity claim found\"}");
            return;
        }

        Authentication currentAuth = SecurityContextHolder.getContext().getAuthentication();
        boolean shouldProvision = (currentAuth == null || !(currentAuth.getPrincipal() instanceof User));

        if (shouldProvision) {
            final String lookupEmail = email != null ? email.toLowerCase().trim() : null;
            final String lookupUsername = username != null ? username.trim() : null;

            User user = null;
            if (lookupEmail != null) {
                user = userRepository.findByEmail(lookupEmail).orElse(null);
            }
            if (user == null && lookupUsername != null) {
                user = userRepository.findByUsername(lookupUsername).orElse(null);
            }

            // Designate admin users: nehru00123@gmail.com, jawaharlalnehruelumalai@gmail.com, soundaryasouu@gmail.com
            boolean isAdmin = (lookupEmail != null && (
                    lookupEmail.equals("nehru00123@gmail.com") ||
                    lookupEmail.equals("jawaharlalnehruelumalai@gmail.com") ||
                    lookupEmail.equals("soundaryasouu@gmail.com")
            )) || (lookupUsername != null && (
                    lookupUsername.equalsIgnoreCase("narasimha") ||
                    lookupUsername.equalsIgnoreCase("nehru00123@gmail.com") ||
                    lookupUsername.equalsIgnoreCase("jawaharlalnehruelumalai@gmail.com") ||
                    lookupUsername.equalsIgnoreCase("soundaryasouu@gmail.com")
            ));

            if (user == null) {
                String provisionUsername = lookupEmail != null ? lookupEmail : lookupUsername;
                String provisionEmail = lookupEmail != null ? lookupEmail : lookupUsername + "@example.com";
                User newUser = User.builder()
                        .username(provisionUsername)
                        .email(provisionEmail)
                        .password("N/A")
                        .isActive(true)
                        .isStaff(isAdmin)
                        .isSuperuser(isAdmin)
                        .build();
                user = userRepository.save(newUser);
            } else if (isAdmin && (!user.isStaff() || !user.isSuperuser())) {
                user.setStaff(true);
                user.setSuperuser(true);
                user = userRepository.save(user);
            }

            if (isInternalToken && !jwtUtils.validateToken(jwt, user)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"message\": \"Token validation failed\"}");
                return;
            }

            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                    user,
                    null,
                    user.getAuthorities()
            );
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authToken);
        }

        filterChain.doFilter(request, response);
    }
}
