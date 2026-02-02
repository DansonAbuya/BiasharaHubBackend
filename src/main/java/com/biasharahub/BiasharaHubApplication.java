package com.biasharahub;

import com.biasharahub.config.DatabaseBootstrap;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class BiasharaHubApplication {

    public static void main(String[] args) {
        DatabaseBootstrap.ensureDatabaseExists();
        SpringApplication.run(BiasharaHubApplication.class, args);
    }
}
