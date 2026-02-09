package com.biasharahub;

import com.biasharahub.config.DatabaseBootstrap;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = {KafkaAutoConfiguration.class})
@EnableCaching
@EnableAsync
@EnableScheduling
public class BiasharaHubApplication {

    public static void main(String[] args) {
        DatabaseBootstrap.ensureDatabaseExists();
        SpringApplication.run(BiasharaHubApplication.class, args);
    }
}
