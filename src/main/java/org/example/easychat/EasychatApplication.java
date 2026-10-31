package org.example.easychat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;

@SpringBootApplication
public class EasychatApplication {

    public static void main(String[] args) {
        SpringApplication.run(EasychatApplication.class, args);
    }

}