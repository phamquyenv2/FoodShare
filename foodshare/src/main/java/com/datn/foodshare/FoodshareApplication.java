package com.datn.foodshare;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FoodshareApplication {

	public static void main(String[] args) {
		SpringApplication.run(FoodshareApplication.class, args);
	}

}
