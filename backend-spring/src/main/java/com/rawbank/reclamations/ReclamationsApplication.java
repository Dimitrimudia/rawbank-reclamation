package com.rawbank.reclamations;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
   import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
   @ConfigurationPropertiesScan
public class ReclamationsApplication {
    public static void main(String[] args) {
        SpringApplication.run(ReclamationsApplication.class, args);
    }
}
