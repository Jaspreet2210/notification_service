package com.example.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class NotificationApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationApplication.class, args);
        System.out.println("==================================================================");
        System.out.println("   RELIABLE MULTI-CHANNEL NOTIFICATION SERVICE RUNNING ON PORT 8080   ");
        System.out.println("   H2 Console: http://localhost:8080/h2-console                   ");
        System.out.println("   Interactive Dashboard: http://localhost:8080/                   ");
        System.out.println("==================================================================");
    }
}
