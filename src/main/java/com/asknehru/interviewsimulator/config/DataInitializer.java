package com.asknehru.interviewsimulator.config;

import com.asknehru.interviewsimulator.model.User;
import com.asknehru.interviewsimulator.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        if (!userRepository.existsByUsername("narasimha")) {
            User user = User.builder()
                    .username("narasimha")
                    .password(passwordEncoder.encode("Murari#1988"))
                    .email("narasimha@example.com")
                    .firstName("Narasimha")
                    .isActive(true)
                    .build();
            userRepository.save(user);
            System.out.println("Default user 'narasimha' created.");
        }
    }
}
