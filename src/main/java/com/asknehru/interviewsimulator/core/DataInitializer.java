package com.asknehru.interviewsimulator.core;

import com.asknehru.interviewsimulator.auth.User;
import com.asknehru.interviewsimulator.auth.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;

import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;


    @Override
    public void run(String... args) throws Exception {
        if (!userRepository.existsByUsername("narasimha")) {
            User user = User.builder()
                    .username("narasimha")
                    .password("N/A")
                    .email("narasimha@example.com")
                    .firstName("Narasimha")
                    .isActive(true)
                    .build();
            userRepository.save(user);
            System.out.println("Default user 'narasimha' created.");
        }
    }
}
