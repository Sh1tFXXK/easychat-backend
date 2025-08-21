package org.example.easychat;

import com.corundumstudio.socketio.SocketIOServer;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class EasychatApplication {

    public static void main(String[] args) {
        SpringApplication.run(EasychatApplication.class, args);
    }

    @Bean
    public CommandLineRunner commandLineRunner(SocketIOServer socketIOServer) {
        return args -> {
            socketIOServer.start();
            System.out.println("Socket.IO服务器已启动在端口: " + socketIOServer.getConfiguration().getPort());
        };
    }
}