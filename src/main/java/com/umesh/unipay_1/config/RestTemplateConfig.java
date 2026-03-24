package com.umesh.unipay_1.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    /**
     * Shared RestTemplate bean with connection and read timeouts configured.
     * Used by FirebaseAuthService for REST API calls to Firebase Identity Toolkit.
     */
    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);  // 5 seconds connection timeout
        factory.setReadTimeout(10_000);    // 10 seconds read timeout
        return new RestTemplate(factory);
    }
}
