# 🚀 EasyChat 启动指南

## ✅ 编译成功！

恭喜！项目已经成功编译，现在需要完成以下步骤来启动应用：

## 📋 启动前准备

### 1. 数据库准备
确保MySQL服务正在运行，然后执行以下SQL脚本：

```sql
-- 1. 创建数据库（如果还没有）
CREATE DATABASE IF NOT EXISTS easychat_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 2. 使用数据库
USE easychat_db;

-- 3. 执行初始化脚本
-- 运行 src/main/resources/db/init_user_status.sql 中的SQL语句
```

### 2. 服务依赖检查
确保以下服务正在运行：

- ✅ **MySQL** (端口 3306)
- ✅ **Redis** (端口 6379) 
- ✅ **RabbitMQ** (端口 5672)

### 3. 环境变量配置
确保以下环境变量已设置（或在application-local.properties中配置）：

```properties
# 数据库密码
spring.datasource.password=你的MySQL密码

# 邮箱配置
spring.mail.password=你的邮箱授权码

# JWT密钥
jwt.secret=你的JWT密钥(至少64位)

# 阿里云OSS (可选)
aliyun.oss.accessKeyId=你的AccessKeyId
aliyun.oss.accessKeySecret=你的AccessKeySecret
```

## 🚀 启动应用

### 方法1：IDE启动
1. 在IDE中运行 `EasychatApplication.java` 主类
2. 等待应用启动完成

### 方法2：命令行启动
```bash
# 使用Maven Wrapper启动
.\mvnw.cmd spring-boot:run

# 或者先打包再运行
.\mvnw.cmd clean package
java -jar target/easychat-0.0.1-SNAPSHOT.jar
```

## 🔍 验证启动

启动成功后，你应该看到：

1. **控制台输出**：
   ```
   Socket.IO服务器已启动在端口: 9092
   Started EasychatApplication in X.XXX seconds
   ```

2. **访问地址**：
   - 后端API: http://localhost:8081
   - API文档: http://localhost:8081/swagger-ui.html
   - 健康检查: http://localhost:8081/admin/error-monitor/health

## ❌ 常见问题解决

### 1. 数据库连接失败
```bash
# 检查MySQL服务状态
net start mysql

# 检查数据库配置
spring.datasource.url=jdbc:mysql://localhost:3306/easychat_db?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
```

### 2. Redis连接失败
```bash
# 启动Redis服务
redis-server
```

### 3. RabbitMQ连接失败
```bash
# 检查RabbitMQ服务状态
# 访问管理界面: http://localhost:15672 (guest/guest)
```

### 4. 端口冲突
如果8081端口被占用，可以修改 `application.properties`：
```properties
server.port=8082
```

## 🎯 功能测试

启动成功后，可以测试以下功能：

1. **错误监控**：
   ```bash
   curl http://localhost:8081/admin/error-monitor/health
   ```

2. **特别关心API**：
   ```bash
   # 需要先登录获取token，然后测试特别关心功能
   ```

## 📝 下一步

1. 配置前端项目 (easychat-client)
2. 测试完整的前后端交互
3. 配置生产环境部署

---

🎉 **恭喜！EasyChat后端服务已准备就绪！**