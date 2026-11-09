package com.dailyatelier.dailyatelier;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DailyatelierApplication {

	public static void main(String[] args) {
		SpringApplication.run(DailyatelierApplication.class, args);
	}

}
