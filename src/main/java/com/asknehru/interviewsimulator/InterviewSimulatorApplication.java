package com.asknehru.interviewsimulator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class InterviewSimulatorApplication {

	public static void main(String[] args) {
		SpringApplication.run(InterviewSimulatorApplication.class, args);
	}

}
