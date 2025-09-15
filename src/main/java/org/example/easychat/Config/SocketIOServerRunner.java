package org.example.easychat.Config;

import com.corundumstudio.socketio.SocketIOServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;

@Component
@Order(1)
@Slf4j
public class SocketIOServerRunner implements CommandLineRunner {

    @Autowired
    private SocketIOServer socketIOServer;

    @Override
    public void run(String... args) throws Exception {
        socketIOServer.start();
        log.info("Socket.IO服务器启动成功，端口: {}", socketIOServer.getConfiguration().getPort());
    }

    @PreDestroy
    public void destroy() {
        if (socketIOServer != null) {
            socketIOServer.stop();
            log.info("Socket.IO服务器已停止");
        }
    }
}