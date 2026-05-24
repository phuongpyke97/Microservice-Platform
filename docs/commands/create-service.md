# Command Template: Create New Java Service

## Description

Prompt template để tạo một service Java mới hoàn chỉnh theo kiến trúc Microservice Platform. Bao gồm cấu hình cơ bản, Dockerfile và tích hợp Eureka/Config Server.

## Usage

```text
Tạo service Java mới tên là {SERVICE_NAME} với port là {PORT}, tên database là {DB_NAME}, và package gốc là {PACKAGE_NAME}. Output ra các file pom.xml, application.yml, Dockerfile, config-server yml, Eureka registration, main class và cấu trúc package của service đó.
```

## Placeholders

- `{SERVICE_NAME}`: Tên service, ví dụ `user-service` (dùng kebab-case)
- `{PORT}`: Cổng HTTP của service, ví dụ `8087`
- `{DB_NAME}`: Tên database cho service, ví dụ `user_db`
- `{PACKAGE_NAME}`: Package gốc của service, ví dụ `com.platform.userservice`

## Example Output Structure (Concept)

```xml
<!-- {SERVICE_NAME}/pom.xml -->
<project>
    <parent>
        <groupId>com.platform</groupId>
        <artifactId>microservice-platform</artifactId>
        <version>1.0-SNAPSHOT</version>
    </parent>
    <modelVersion>4.0.0</modelVersion>

    <artifactId>{SERVICE_NAME}</artifactId>
    <packaging>jar</packaging>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-config</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-core</artifactId>
        </dependency>
        <dependency>
            <groupId>com.platform.common</groupId>
            <artifactId>common-core</artifactId>
            <version>1.0-SNAPSHOT</n>
        </dependency>
        <dependency>
            <groupId>com.platform.common</groupId>
            <artifactId>common-security</artifactId>
            <version>1.0-SNAPSHOT</n>
        </dependency>
    </dependencies>

</project>
```

```yaml
# {SERVICE_NAME}/src/main/resources/application.yml
server:
  port: {PORT}

spring:
  application:
    name: {SERVICE_NAME}
  config:
    import: "optional:configserver:http://config-server:8888"
  datasource:
    url: jdbc:postgresql://postgresql:5432/{DB_NAME}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: true
  flyway:
    enabled: true
    locations: classpath:db/migration

eureka:
  client:
    service-url:
      defaultZone: http://eureka-server:8761/eureka
  instance:
    hostname: {SERVICE_NAME}
    prefer-ip-address: true

```

```dockerfile
# {SERVICE_NAME}/Dockerfile
FROM openjdk:21-jdk-slim
ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} app.jar
ENTRYPOINT ["java","-jar","/app.jar"]
EXPOSE {PORT}
```

```yaml
# config-repo/{SERVICE_NAME}.yml (for config-server)
spring:
  datasource:
    url: jdbc:postgresql://postgresql:5432/{DB_NAME}
    username: myuser
    password: mypassword

# ... other service-specific configurations
```

```java
// {SERVICE_NAME}/src/main/java/{PACKAGE_NAME}/Application.java
package {PACKAGE_NAME};

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableEurekaClient
@EnableFeignClients // Nếu service này cần gọi FeignClient
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

```
// Cấu trúc package chuẩn
{SERVICE_NAME}/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── {PACKAGE_PATH}/
│   │   │       ├── Application.java
│   │   │       ├── config/
│   │   │       ├── controller/
│   │   │       ├── service/
│   │   │       ├── repository/
│   │   │       ├── entity/
│   │   │       ├── dto/
│   │   │       │   ├── request/
│   │   │       │   └── response/
│   │   │       ├── event/
│   │   │       ├── listener/
│   │   │       ├── client/
│   │   │       ├── exception/
│   │   │       └── util/
│   │   └── resources/
│   │       ├── application.yml
│   │       └── db/
│   │           └── migration/
│   └── test/
│       └── java/
│           └── {PACKAGE_PATH}/
└── pom.xml
```
