package com.hisaberp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.modulith.Modulithic;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@Modulithic(systemName = "Hisab ERP")
@EnableCaching
@EnableScheduling
@EnableAsync
public class HisabErpApplication {

    public static void main(String[] args) {
        SpringApplication.run(HisabErpApplication.class, args);
    }
}
