package com.aivideoback.kwungjin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class KwungjinApplication {

	public static void main(String[] args) {
		SpringApplication.run(KwungjinApplication.class, args);
	}

}
