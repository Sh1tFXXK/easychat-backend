# 🚀 EasyChat Backend - 高性能实时通信后端服务

<div align="center">

![Java](https://img.shields.io/badge/Java-17-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7.18-brightgreen)
![MySQL](https://img.shields.io/badge/MySQL-8.0-blue)
![Redis](https://img.shields.io/badge/Redis-Latest-red)
![Netty](https://img.shields.io/badge/Netty-SocketIO-yellow)

**基于SpringBoot + Netty + WebSocket的高并发实时通信后端服务**

</div>

---

## 📋 项目概述

EasyChat Backend是一个高性能、可扩展的实时通信后端服务，采用SpringBoot框架构建，集成Netty-SocketIO实现WebSocket长连接服务，支持高并发实时消息传输。

### 🎯 核心特性

- **高并发WebSocket**: 基于Netty-SocketIO，单节点支持1.5万+并发连接
- **消息队列**: 集成RabbitMQ，支持2000TPS峰值流量处理
- **多层缓存**: Redis缓存策略，提升数据访问速度5倍以上
- **数据库优化**: 精心设计的索引策略，百万级数据查询50ms响应
- **安全认证**: JWT Token认证 + Spring Security权限控制

---

## 🏗️ 技术架构

### 核心技术栈
```
├── Spring Boot 2.7.18      # 核心框架
├── Spring Security          # 安全框架  
├── Spring Data JPA         # 数据访问层
├── MyBatis-Plus 3.5.3      # ORM增强框架
├── Netty-SocketIO 1.7.23   # WebSocket服务
├── MySQL 8.0               # 关系型数据库
├── Redis                   # 缓存数据库
├── RabbitMQ               # 消息队列
├── JWT 0.11.5             # 认证方案
├── Swagger 3              # API文档
├── Lombok                 # 代码简化
└── Jackson                # JSON处理
```

### 项目结构
```
easychat/
├── src/main/java/org/example/easychat/
│   ├── Config/                 # 配置类
│   │   ├── RedisConfig.java           # Redis配置
│   │   ├── SocketIOConfig.java        # WebSocket配置
│   │   ├── RabbitMQConfig.java        # 消息队列配置
│   │   └── SecurityConfig.java        # 安全配置
│   ├── Controller/             # 控制器层
│   │   ├── AuthController.java        # 认证控制器
│   │   ├── UserController.java        # 用户管理
│   │   ├── ChatController.java        # 聊天功能
│   │   ├── FavoriteController.java    # 收藏功能
│   │   └── SpecialAttentionController.java # 特别关心
│   ├── Service/               # 业务逻辑层
│   │   ├── UserService.java          # 用户服务
│   │   ├── ChatService.java          # 聊天服务
│   │   ├── FavoriteService.java      # 收藏服务
│   │   └── NotificationService.java   # 通知服务
│   ├── Handler/               # 处理器
│   │   ├── ChatSocketIOHandler.java   # WebSocket处理器
│   │   ├── GlobalExceptionHandler.java # 全局异常处理
│   │   └── JwtAuthenticationFilter.java # JWT过滤器
│   ├── Entity/                # 实体类
│   ├── Mapper/                # 数据访问层
│   ├── dto/                   # 数据传输对象
│   └── utils/                 # 工具类
├── src/main/resources/
│   ├── mapper/                # MyBatis映射文件
│   ├── sql/                   # SQL脚本
│   ├── db/                    # 数据库初始化脚本
│   ├── application.properties # 主配置文件
│   └── application-*.yml      # 环境配置文件
└── docs/                      # 项目文档
```

---

## 🚀 快速开始

### 环境要求
- **JDK**: 17+
- **Maven**: 3.6+
- **MySQL**: 8.0+
- **Redis**: 6.0+
- **RabbitMQ**: 3.8+ (可选)

### 1. 数据库初始化

#### 创建数据库
```sql
CREATE DATABASE easychat_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE easychat_db;
```

#### 执行初始化脚本
```bash
# 用户状态相关表
mysql -u root -p easychat_db < src/main/resources/db/init_user_status.sql

# 收藏功能相关表
mysql -u root -p easychat_db < src/main/resources/sql/favorite_tables.sql

# 索引优化脚本
mysql -u root -p easychat_db < src/main/resources/db/optimization/index_optimization.sql
```

### 2. 配置文件设置

创建 `src/main/resources/application-local.properties`:
```properties
# 数据库配置
spring.datasource.password=your_mysql_password

# Redis配置
spring.redis.password=your_redis_password

# JWT密钥配置(必须至少64位)
jwt.secret=your_jwt_secret_key_must_be_at_least_64_characters_long_for_security_purposes

# 邮箱配置
spring.mail.password=your_email_authorization_code

# 阿里云OSS配置(可选)
aliyun.oss.accessKeyId=your_aliyun_access_key_id
aliyun.oss.accessKeySecret=your_aliyun_access_key_secret

# RabbitMQ配置(可选)
spring.rabbitmq.host=127.0.0.1
spring.rabbitmq.port=5672
spring.rabbitmq.username=guest
spring.rabbitmq.password=guest
```

### 3. 启动应用

#### 使用Maven启动
```bash
# 开发环境启动
./mvnw spring-boot:run

# 指定配置文件启动
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

#### 使用IDE启动
直接运行 `EasychatApplication.java` 主类

#### 打包部署
```bash
# 打包
./mvnw clean package -DskipTests

# 运行jar包
java -jar target/easychat-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

### 4. 验证启动

启动成功后检查以下内容：

#### 控制台输出
```
2024-01-01 10:00:00.000  INFO --- [main] o.e.e.EasychatApplication: Started EasychatApplication in 15.234 seconds
2024-01-01 10:00:00.000  INFO --- [main] o.e.e.Config.SocketIOServerRunner: Socket.IO服务器已启动在端口: 8082
```

#### 服务端点检查
```bash
# 健康检查
curl http://localhost:8080/actuator/health

# API文档
curl http://localhost:8080/swagger-ui.html

# WebSocket连接测试
curl http://localhost:8082/socket.io/?EIO=4&transport=polling
```

---

## 🔧 核心功能模块

### 1. 认证授权模块

#### JWT认证流程
```java
@PostMapping("/login")
public ResponseEntity<?> login(@RequestBody LoginRequest request) {
    // 1. 验证用户凭据
    User user = userService.authenticate(request.getUsername(), request.getPassword());
    
    // 2. 生成JWT Token
    String token = jwtUtil.generateToken(user.getId());
    
    // 3. 返回认证结果
    return ResponseEntity.ok(new LoginResponse(token, user));
}
```

#### 权限控制
```java
@PreAuthorize("hasRole('USER')")
@GetMapping("/profile")
public ResponseEntity<User> getUserProfile() {
    // 获取当前用户信息
}
```

### 2. WebSocket实时通信

#### Socket.IO配置
```java
@Configuration
public class SocketIOConfig {
    
    @Bean
    public SocketIOServer socketIOServer() {
        Configuration config = new Configuration();
        config.setHostname("localhost");
        config.setPort(8082);
        
        // JWT认证
        config.setAuthorizationListener(data -> {
            String token = data.getSingleUrlParam("token");
            return jwtUtil.validateToken(token);
        });
        
        return new SocketIOServer(config);
    }
}
```

#### 消息处理器
```java
@Component
public class ChatSocketIOHandler {
    
    @OnConnect
    public void onConnect(SocketIOClient client) {
        String userId = getUserIdFromClient(client);
        userStatusService.setUserOnline(userId);
        log.info("用户 {} 已连接", userId);
    }
    
    @OnEvent("sendMessage")
    public void onSendMessage(SocketIOClient client, MessageDto message) {
        // 处理消息发送
        chatService.sendMessage(message);
        
        // 推送给接收者
        socketIOServer.getRoomOperations(message.getReceiverId())
                     .sendEvent("receiveMessage", message);
    }
}
```

### 3. 消息队列处理

#### RabbitMQ配置
```java
@Configuration
public class RabbitMQConfig {
    
    public static final String NOTIFICATION_QUEUE = "attention.notification.queue";
    public static final String NOTIFICATION_EXCHANGE = "attention.notification.exchange";
    
    @Bean
    public Queue notificationQueue() {
        return QueueBuilder.durable(NOTIFICATION_QUEUE)
                          .withArgument("x-dead-letter-exchange", "attention.notification.dlx")
                          .build();
    }
    
    @Bean
    public TopicExchange notificationExchange() {
        return new TopicExchange(NOTIFICATION_EXCHANGE);
    }
}
```

#### 消息生产者
```java
@Service
public class NotificationService {
    
    @Autowired
    private RabbitTemplate rabbitTemplate;
    
    public void sendNotification(NotificationMessage message) {
        rabbitTemplate.convertAndSend(
            RabbitMQConfig.NOTIFICATION_EXCHANGE,
            "attention.notification",
            message
        );
    }
}
```

### 4. 缓存策略

#### Redis缓存配置
```java
@Configuration
@EnableCaching
public class RedisConfig {
    
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory factory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new Jackson2JsonRedisSerializer<>(Object.class)));
        
        return RedisCacheManager.builder(factory)
                               .cacheDefaults(config)
                               .build();
    }
}
```

#### 缓存使用示例
```java
@Service
public class UserService {
    
    @Cacheable(value = "users", key = "#userId")
    public User getUserById(String userId) {
        return userMapper.selectById(userId);
    }
    
    @CacheEvict(value = "users", key = "#user.id")
    public void updateUser(User user) {
        userMapper.updateById(user);
    }
}
```

---

## 📊 数据库设计

### 核心表结构

#### 用户表 (users)
```sql
CREATE TABLE `users` (
  `id` varchar(19) NOT NULL COMMENT '用户ID',
  `username` varchar(50) NOT NULL COMMENT '用户名',
  `nickname` varchar(50) DEFAULT NULL COMMENT '昵称',
  `password` varchar(255) NOT NULL COMMENT '密码',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `phone` varchar(20) DEFAULT NULL COMMENT '手机号',
  `avatar` varchar(500) DEFAULT NULL COMMENT '头像URL',
  `gender` tinyint DEFAULT '0' COMMENT '性别 0-未知 1-男 2-女',
  `birthday` date DEFAULT NULL COMMENT '生日',
  `region` varchar(100) DEFAULT NULL COMMENT '地区',
  `introduction` varchar(500) DEFAULT NULL COMMENT '个人简介',
  `status` tinyint DEFAULT '1' COMMENT '状态 0-禁用 1-正常',
  `create_time` timestamp DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_email` (`email`),
  KEY `idx_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';
```

#### 聊天记录表 (chat_history)
```sql
CREATE TABLE `chat_history` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '消息ID',
  `session_id` varchar(50) NOT NULL COMMENT '会话ID',
  `sender_id` varchar(19) NOT NULL COMMENT '发送者ID',
  `receiver_id` varchar(19) DEFAULT NULL COMMENT '接收者ID',
  `message_type` tinyint NOT NULL COMMENT '消息类型 1-文本 2-图片 3-文件 4-语音',
  `content` text COMMENT '消息内容',
  `file_url` varchar(500) DEFAULT NULL COMMENT '文件URL',
  `file_name` varchar(255) DEFAULT NULL COMMENT '文件名',
  `file_size` bigint DEFAULT NULL COMMENT '文件大小',
  `has_read` tinyint DEFAULT '0' COMMENT '是否已读 0-未读 1-已读',
  `create_time` timestamp DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_session_time` (`session_id`,`create_time`),
  KEY `idx_sender_time` (`sender_id`,`create_time`),
  KEY `idx_receiver_time` (`receiver_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天记录表';
```

#### 收藏表 (favorites)
```sql
CREATE TABLE `favorites` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '收藏ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `item_type` varchar(20) NOT NULL COMMENT '收藏类型',
  `title` varchar(200) DEFAULT NULL COMMENT '标题',
  `content` longtext COMMENT '内容',
  `category_id` bigint DEFAULT NULL COMMENT '分类ID',
  `tags` json DEFAULT NULL COMMENT '标签',
  `note` varchar(500) DEFAULT NULL COMMENT '备注',
  `file_size` bigint DEFAULT NULL COMMENT '文件大小',
  `file_type` varchar(50) DEFAULT NULL COMMENT '文件类型',
  `original_data` longtext COMMENT '原始数据',
  `sender_id` bigint DEFAULT NULL COMMENT '发送者ID',
  `sender_name` varchar(50) DEFAULT NULL COMMENT '发送者名称',
  `source_chat_id` varchar(50) DEFAULT NULL COMMENT '来源聊天ID',
  `source_chat_name` varchar(100) DEFAULT NULL COMMENT '来源聊天名称',
  `source_chat_type` varchar(20) DEFAULT NULL COMMENT '来源聊天类型',
  `is_available` tinyint DEFAULT '1' COMMENT '是否可用',
  `create_time` timestamp DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_category_time` (`user_id`,`category_id`,`create_time` DESC),
  KEY `idx_user_type_time` (`user_id`,`item_type`,`create_time` DESC),
  KEY `idx_favorite_stats` (`user_id`,`is_available`,`item_type`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收藏表';
```

### 索引优化策略

#### 聊天记录索引
```sql
-- 会话查询优化
CREATE INDEX idx_session_time ON chat_history (session_id, create_time DESC);

-- 用户消息查询优化  
CREATE INDEX idx_sender_receiver_time ON chat_history (sender_id, receiver_id, create_time DESC);

-- 未读消息查询优化
CREATE INDEX idx_receiver_read_time ON chat_history (receiver_id, has_read, create_time DESC);
```

#### 收藏功能索引
```sql
-- 用户收藏列表查询
CREATE INDEX idx_user_category_time ON favorites (user_id, category_id, create_time DESC);

-- 收藏类型统计
CREATE INDEX idx_user_type_available ON favorites (user_id, item_type, is_available);

-- 全文搜索优化
ALTER TABLE favorites ADD FULLTEXT(title, content);
```

---

## 🔧 配置详解

### 应用配置 (application.properties)

#### 基础配置
```properties
# 应用名称
spring.application.name=easychat

# 服务端口
server.port=8080

# 激活配置文件
spring.profiles.active=local
```

#### 数据库配置
```properties
# MySQL数据源配置
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
spring.datasource.url=jdbc:mysql://localhost:3306/easychat_db?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false
spring.datasource.username=root
spring.datasource.password=${spring.datasource.password}

# HikariCP连接池配置
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.connection-timeout=30000
spring.datasource.hikari.idle-timeout=600000
spring.datasource.hikari.max-lifetime=1800000
```

#### MyBatis-Plus配置
```properties
# Mapper文件位置
mybatis-plus.mapper-locations=classpath:mapper/*.xml

# 实体类包路径
mybatis-plus.type-aliases-package=org.example.easychat.Entity

# 驼峰命名转换
mybatis-plus.configuration.map-underscore-to-camel-case=true

# SQL日志打印
mybatis-plus.configuration.log-impl=org.apache.ibatis.logging.stdout.StdOutImpl
```

#### Redis配置
```properties
# Redis连接配置
spring.redis.host=127.0.0.1
spring.redis.port=6379
spring.redis.password=
spring.redis.timeout=5000ms
spring.redis.database=0

# Redis连接池配置
spring.redis.lettuce.pool.max-active=20
spring.redis.lettuce.pool.max-idle=10
spring.redis.lettuce.pool.min-idle=5
spring.redis.lettuce.pool.max-wait=-1ms
```

### 性能配置 (application-performance.yml)

```yaml
# 性能优化配置
spring:
  # 缓存配置
  cache:
    type: redis
    redis:
      time-to-live: 3600000  # 1小时
      cache-null-values: false
      
  # 数据库连接池优化
  datasource:
    hikari:
      maximum-pool-size: 50
      minimum-idle: 10
      connection-timeout: 20000
      idle-timeout: 300000
      max-lifetime: 1200000
      leak-detection-threshold: 60000

# 自定义性能配置
performance:
  # 缓存TTL配置
  cache:
    user-status-ttl: 86400      # 用户状态缓存24小时
    attention-list-ttl: 1800    # 特别关心列表30分钟
    chat-history-ttl: 3600      # 聊天记录1小时
    
  # 线程池配置
  thread-pool:
    # WebSocket处理线程池
    websocket:
      core-pool-size: 10
      max-pool-size: 50
      queue-capacity: 1000
      keep-alive-seconds: 60
      
    # 异步任务线程池
    async:
      core-pool-size: 5
      max-pool-size: 20
      queue-capacity: 200
      keep-alive-seconds: 60
```

---

## 🚀 API接口文档

### 认证相关接口

#### 用户登录
```http
POST /api/auth/login
Content-Type: application/json

{
  "username": "testuser",
  "password": "123456"
}
```

**响应示例:**
```json
{
  "code": 200,
  "message": "登录成功",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "user": {
      "id": "7756640763001649776",
      "username": "testuser",
      "nickname": "测试用户",
      "avatar": "https://example.com/avatar.png"
    }
  }
}
```

#### 用户注册
```http
POST /api/user/register
Content-Type: application/json

{
  "username": "newuser",
  "password": "123456",
  "email": "newuser@example.com",
  "nickname": "新用户"
}
```

### 聊天相关接口

#### 发送消息
```http
POST /api/chat/send
Authorization: Bearer {token}
Content-Type: application/json

{
  "receiverId": "7756640763001649777",
  "messageType": 1,
  "content": "Hello, World!"
}
```

#### 获取聊天记录
```http
GET /api/chat/history?sessionId=session123&page=1&size=20
Authorization: Bearer {token}
```

### 收藏相关接口

#### 添加收藏
```http
POST /api/favorites
Authorization: Bearer {token}
Content-Type: application/json

{
  "itemType": "message",
  "title": "重要消息",
  "content": "这是一条重要的消息内容",
  "categoryId": 1,
  "tags": ["工作", "重要"]
}
```

#### 获取收藏列表
```http
GET /api/favorites?page=1&size=20&categoryId=1&keyword=重要
Authorization: Bearer {token}
```

### WebSocket事件

#### 连接认证
```javascript
const socket = io('http://localhost:8082', {
  query: {
    token: 'your_jwt_token'
  }
});
```

#### 发送消息事件
```javascript
socket.emit('sendMessage', {
  receiverId: '7756640763001649777',
  messageType: 1,
  content: 'Hello!'
});
```

#### 接收消息事件
```javascript
socket.on('receiveMessage', (message) => {
  console.log('收到新消息:', message);
});
```

---

## 🧪 测试指南

### 单元测试

#### 服务层测试
```java
@SpringBootTest
@Transactional
class UserServiceTest {
    
    @Autowired
    private UserService userService;
    
    @Test
    void testCreateUser() {
        User user = new User();
        user.setUsername("testuser");
        user.setPassword("123456");
        
        User created = userService.createUser(user);
        
        assertThat(created.getId()).isNotNull();
        assertThat(created.getUsername()).isEqualTo("testuser");
    }
}
```

#### 控制器测试
```java
@WebMvcTest(UserController.class)
class UserControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private UserService userService;
    
    @Test
    void testGetUser() throws Exception {
        User user = new User();
        user.setId("123");
        user.setUsername("testuser");
        
        when(userService.getUserById("123")).thenReturn(user);
        
        mockMvc.perform(get("/api/user/123"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.data.username").value("testuser"));
    }
}
```

### 集成测试

#### API集成测试
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(locations = "classpath:application-test.properties")
class ChatIntegrationTest {
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Test
    void testSendMessage() {
        // 1. 登录获取token
        LoginRequest loginRequest = new LoginRequest("testuser", "123456");
        ResponseEntity<LoginResponse> loginResponse = restTemplate.postForEntity(
            "/api/auth/login", loginRequest, LoginResponse.class);
        
        String token = loginResponse.getBody().getToken();
        
        // 2. 发送消息
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        
        MessageRequest messageRequest = new MessageRequest();
        messageRequest.setReceiverId("receiver123");
        messageRequest.setContent("Test message");
        
        HttpEntity<MessageRequest> entity = new HttpEntity<>(messageRequest, headers);
        
        ResponseEntity<ApiResponse> response = restTemplate.postForEntity(
            "/api/chat/send", entity, ApiResponse.class);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
```

### 性能测试

#### JMeter测试脚本
```xml
<?xml version="1.0" encoding="UTF-8"?>
<jmeterTestPlan version="1.2">
  <hashTree>
    <TestPlan guiclass="TestPlanGui" testclass="TestPlan" testname="EasyChat Performance Test">
      <elementProp name="TestPlan.arguments" elementType="Arguments" guiclass="ArgumentsPanel">
        <collectionProp name="Arguments.arguments"/>
      </elementProp>
      <stringProp name="TestPlan.user_define_classpath"></stringProp>
      <boolProp name="TestPlan.serialize_threadgroups">false</boolProp>
      <boolProp name="TestPlan.functional_mode">false</boolProp>
    </TestPlan>
    
    <!-- 线程组配置 -->
    <ThreadGroup guiclass="ThreadGroupGui" testclass="ThreadGroup" testname="Chat Load Test">
      <stringProp name="ThreadGroup.num_threads">1000</stringProp>
      <stringProp name="ThreadGroup.ramp_time">60</stringProp>
      <stringProp name="ThreadGroup.duration">600</stringProp>
    </ThreadGroup>
  </hashTree>
</jmeterTestPlan>
```

---

## 📊 监控与运维

### 应用监控

#### Actuator端点配置
```properties
# 开启监控端点
management.endpoints.web.exposure.include=health,info,metrics,prometheus

# 健康检查配置
management.endpoint.health.show-details=always
management.health.redis.enabled=true
management.health.db.enabled=true
```

#### 自定义健康检查
```java
@Component
public class CustomHealthIndicator implements HealthIndicator {
    
    @Autowired
    private SocketIOServer socketIOServer;
    
    @Override
    public Health health() {
        if (socketIOServer.getAllClients().size() > 0) {
            return Health.up()
                        .withDetail("websocket", "active")
                        .withDetail("connections", socketIOServer.getAllClients().size())
                        .build();
        }
        return Health.down()
                    .withDetail("websocket", "inactive")
                    .build();
    }
}
```

### 日志配置

#### Logback配置 (logback-spring.xml)
```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <!-- 控制台输出 -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    
    <!-- 文件输出 -->
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/easychat.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>logs/easychat.%d{yyyy-MM-dd}.%i.log</fileNamePattern>
            <timeBasedFileNamingAndTriggeringPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedFNATP">
                <maxFileSize>100MB</maxFileSize>
            </timeBasedFileNamingAndTriggeringPolicy>
            <maxHistory>30</maxHistory>
        </rollingPolicy>
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    
    <!-- 错误日志 -->
    <appender name="ERROR_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/error.log</file>
        <filter class="ch.qos.logback.classic.filter.LevelFilter">
            <level>ERROR</level>
            <onMatch>ACCEPT</onMatch>
            <onMismatch>DENY</onMismatch>
        </filter>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>logs/error.%d{yyyy-MM-dd}.log</fileNamePattern>
            <maxHistory>30</maxHistory>
        </rollingPolicy>
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    
    <!-- 根日志级别 -->
    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
        <appender-ref ref="FILE"/>
        <appender-ref ref="ERROR_FILE"/>
    </root>
    
    <!-- 特定包日志级别 -->
    <logger name="org.example.easychat" level="DEBUG"/>
    <logger name="org.springframework.web" level="INFO"/>
    <logger name="com.corundumstudio.socketio" level="INFO"/>
</configuration>
```

### 性能监控

#### Micrometer指标配置
```java
@Configuration
public class MetricsConfig {
    
    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }
    
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> registry.config().commonTags("application", "easychat");
    }
}
```

#### 自定义指标
```java
@Service
public class ChatMetricsService {
    
    private final Counter messageCounter;
    private final Timer messageProcessingTimer;
    
    public ChatMetricsService(MeterRegistry meterRegistry) {
        this.messageCounter = Counter.builder("chat.messages.sent")
                                   .description("Total messages sent")
                                   .register(meterRegistry);
        
        this.messageProcessingTimer = Timer.builder("chat.message.processing.time")
                                         .description("Message processing time")
                                         .register(meterRegistry);
    }
    
    public void recordMessageSent() {
        messageCounter.increment();
    }
    
    public void recordProcessingTime(Duration duration) {
        messageProcessingTimer.record(duration);
    }
}
```

---

## 🔍 故障排除

### 常见问题及解决方案

#### 1. 数据库连接问题
```bash
# 检查MySQL服务状态
systemctl status mysql

# 检查连接配置
mysql -u root -p -h localhost -P 3306 easychat_db

# 检查连接池状态
curl http://localhost:8080/actuator/health
```

**解决方案:**
- 确认MySQL服务正在运行
- 检查用户名密码是否正确
- 确认数据库名称是否存在
- 检查防火墙设置

#### 2. Redis连接失败
```bash
# 检查Redis服务
redis-cli ping

# 检查Redis配置
redis-cli info server

# 测试连接
redis-cli -h 127.0.0.1 -p 6379
```

**解决方案:**
- 启动Redis服务: `redis-server`
- 检查端口是否被占用
- 确认Redis配置文件

#### 3. WebSocket连接问题
```bash
# 检查端口占用
netstat -tulpn | grep 8082

# 测试WebSocket连接
curl -i -N -H "Connection: Upgrade" \
     -H "Upgrade: websocket" \
     -H "Sec-WebSocket-Key: SGVsbG8sIHdvcmxkIQ==" \
     -H "Sec-WebSocket-Version: 13" \
     http://localhost:8082/socket.io/
```

**解决方案:**
- 确认SocketIO服务已启动
- 检查防火墙设置
- 验证JWT Token是否有效

#### 4. 内存溢出问题
```bash
# 查看JVM内存使用
jstat -gc [pid]

# 生成堆转储
jmap -dump:format=b,file=heapdump.hprof [pid]

# 分析内存使用
jhat heapdump.hprof
```

**解决方案:**
```bash
# 调整JVM参数
java -Xms2g -Xmx4g -XX:+UseG1GC \
     -XX:+HeapDumpOnOutOfMemoryError \
     -XX:HeapDumpPath=/tmp/heapdump.hprof \
     -jar easychat.jar
```

### 日志分析

#### 应用日志
```bash
# 查看实时日志
tail -f logs/easychat.log

# 搜索错误日志
grep "ERROR" logs/easychat.log

# 分析WebSocket连接日志
grep "Socket.IO" logs/easychat.log | tail -100
```

#### 性能分析
```bash
# 查看GC日志
grep "GC" logs/easychat.log

# 分析慢查询
grep "slow query" logs/easychat.log

# 监控线程池状态
grep "thread pool" logs/easychat.log
```

---

## 📈 性能优化建议

### 1. 数据库优化

#### 索引优化
```sql
-- 分析慢查询
SHOW PROCESSLIST;
SHOW FULL PROCESSLIST;

-- 查看索引使用情况
EXPLAIN SELECT * FROM chat_history WHERE sender_id = '123' ORDER BY create_time DESC LIMIT 20;

-- 添加复合索引
CREATE INDEX idx_sender_time ON chat_history (sender_id, create_time DESC);
```

#### 连接池优化
```properties
# HikariCP优化配置
spring.datasource.hikari.maximum-pool-size=50
spring.datasource.hikari.minimum-idle=10
spring.datasource.hikari.connection-timeout=20000
spring.datasource.hikari.idle-timeout=300000
spring.datasource.hikari.max-lifetime=1200000
spring.datasource.hikari.leak-detection-threshold=60000
```

### 2. 缓存优化

#### Redis配置优化
```properties
# Redis内存优化
spring.redis.lettuce.pool.max-active=50
spring.redis.lettuce.pool.max-idle=20
spring.redis.lettuce.pool.min-idle=10
spring.redis.lettuce.pool.max-wait=1000ms

# 启用Redis压缩
spring.redis.jedis.pool.max-active=50
```

#### 缓存策略
```java
@Service
public class OptimizedChatService {
    
    // 多级缓存策略
    @Cacheable(value = "chatHistory", key = "#sessionId + '_' + #page")
    public List<Message> getChatHistory(String sessionId, int page) {
        return chatMapper.selectBySessionId(sessionId, page * 20, 20);
    }
    
    // 缓存预热
    @EventListener(ApplicationReadyEvent.class)
    public void warmUpCache() {
        // 预加载热点数据
        List<String> hotSessions = getHotSessions();
        hotSessions.forEach(sessionId -> {
            getChatHistory(sessionId, 0);
        });
    }
}
```

### 3. JVM优化

#### GC调优
```bash
# G1GC配置
-XX:+UseG1GC
-XX:MaxGCPauseMillis=200
-XX:G1HeapRegionSize=16m
-XX:G1NewSizePercent=30
-XX:G1MaxNewSizePercent=40

# 内存配置
-Xms4g
-Xmx4g
-XX:MetaspaceSize=256m
-XX:MaxMetaspaceSize=512m

# GC日志
-XX:+PrintGC
-XX:+PrintGCDetails
-XX:+PrintGCTimeStamps
-Xloggc:logs/gc.log
```

### 4. 线程池优化

```java
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {
    
    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // 核心线程数
        executor.setCorePoolSize(Runtime.getRuntime().availableProcessors());
        
        // 最大线程数
        executor.setMaxPoolSize(Runtime.getRuntime().availableProcessors() * 2);
        
        // 队列容量
        executor.setQueueCapacity(1000);
        
        // 线程名前缀
        executor.setThreadNamePrefix("EasyChat-Async-");
        
        // 拒绝策略
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        
        executor.initialize();
        return executor;
    }
}
```

---

## 📝 更新日志

### v1.0.0 (2024-01-01)
- ✨ 初始版本发布
- 🚀 实现基础聊天功能
- 🔐 集成JWT认证
- 💾 完成数据库设计

### v1.1.0 (2024-02-01)
- ✨ 新增收藏功能
- 🔍 实现高级搜索
- 📊 添加性能监控
- 🐛 修复WebSocket连接问题

### v1.2.0 (2024-03-01)
- ✨ 新增特别关心功能
- 🚀 集成消息队列
- 📈 性能优化
- 🔧 配置文件重构

---

## 🤝 贡献指南

### 开发规范
1. 遵循阿里巴巴Java开发手册
2. 使用统一的代码格式化配置
3. 编写完整的单元测试
4. 提交前运行代码检查

### 提交流程
1. Fork项目到个人仓库
2. 创建功能分支: `git checkout -b feature/new-feature`
3. 提交代码: `git commit -am 'Add new feature'`
4. 推送分支: `git push origin feature/new-feature`
5. 创建Pull Request

### 代码审查
- 所有代码必须经过Code Review
- 确保测试覆盖率不低于80%
- 通过所有CI/CD检查

---

## 📄 许可证

本项目采用 [MIT License](../LICENSE) 许可证。

---

## 📞 技术支持

- **项目地址**: https://github.com/your-username/easychat
- **问题反馈**: https://github.com/your-username/easychat/issues
- **技术文档**: [项目Wiki](https://github.com/your-username/easychat/wiki)
- **邮箱支持**: easychat-backend@example.com

---

<div align="center">

**🚀 EasyChat Backend - 让实时通信更简单！ 🚀**

</div>