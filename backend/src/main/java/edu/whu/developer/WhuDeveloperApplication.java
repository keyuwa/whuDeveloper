package edu.whu.developer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class WhuDeveloperApplication {

    public static void main(String[] args) {
        SpringApplication.run(WhuDeveloperApplication.class, args);
    }
}
