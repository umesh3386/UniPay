package com.umesh.unipay_1;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@SpringBootApplication
@EnableMethodSecurity(prePostEnabled = true)
public class Unipay1Application {

    public static void main(String[] args) {
        SpringApplication.run(Unipay1Application.class, args);
    }

}
