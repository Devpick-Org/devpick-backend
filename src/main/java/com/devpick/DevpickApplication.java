package com.devpick;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DevpickApplication {

    public static void main(String[] args) {
        SpringApplication.run(DevpickApplication.class, args);
    }
}
