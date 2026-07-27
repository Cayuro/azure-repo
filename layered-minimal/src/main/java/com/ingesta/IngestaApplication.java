package com.ingesta;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@ConfigurationPropertiesScan
@SpringBootApplication
public class IngestaApplication {

    public static void main(String[] args) {
        SpringApplication.run(IngestaApplication.class, args);
    }
}
