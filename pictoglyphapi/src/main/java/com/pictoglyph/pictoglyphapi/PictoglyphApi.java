package com.pictoglyph.pictoglyphapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PictoglyphApi {

	public static void main(String[] args) {
		SpringApplication.run(PictoglyphApi.class, args);
	}
}
