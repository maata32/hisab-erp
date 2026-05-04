package com.minierp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.modulith.Modulithic;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@Modulithic(systemName = "Mini-ERP")
@EnableCaching
@EnableScheduling
@EnableAsync
public class MiniErpApplication {

    public static void main(String[] args) {
        SpringApplication.run(MiniErpApplication.class, args);
    }
}
