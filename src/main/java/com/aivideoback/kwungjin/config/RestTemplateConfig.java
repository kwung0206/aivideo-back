package com.aivideoback.kwungjin.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        // 필요하면 타임아웃 등을 여기서 설정해도 됨
        return new RestTemplate();
    }
}
