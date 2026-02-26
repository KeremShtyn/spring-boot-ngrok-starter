package com.kermel.ngrok.sample;

import com.kermel.ngrok.event.NgrokReadyEvent;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.event.EventListener;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimal sample application demonstrating ngrok-spring-boot-starter.
 *
 * <p>Run with: {@code NGROK_AUTHTOKEN=xxx mvn spring-boot:run -Dspring.profiles.active=dev}
 */
@SpringBootApplication
@RestController
public class BasicSampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(BasicSampleApplication.class, args);
    }

    @GetMapping("/")
    public String hello() {
        return "Hello from Spring Boot + ngrok!";
    }

    @GetMapping("/health")
    public String health() {
        return "OK";
    }

    @EventListener
    public void onNgrokReady(NgrokReadyEvent event) {
        event.getTunnels().forEach(tunnel ->
                System.out.println("ngrok tunnel ready: " + tunnel.publicUrl()));
    }
}
