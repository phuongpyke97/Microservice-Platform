# Command Template: Add Entity to Service

## Description

Prompt template để thêm một entity mới vào một service Java đã tồn tại. Bao gồm entity class, Flyway migration, repository interface và cập nhật PROJECT_TREE.md.

## Usage

```text
Thêm entity mới tên {ENTITY_NAME} vào service {SERVICE_NAME}. Các field của entity gồm: {FIELDS}. Output ra entity class, Flyway migration SQL, repository interface, và cập nhật file PROJECT_TREE.md.
```

## Placeholders

- `{SERVICE_NAME}`: Tên service đích, ví dụ `auth-service`
- `{ENTITY_NAME}`: Tên entity (PascalCase, số ít), ví dụ `User`
- `{FIELDS}`: Danh sách field dạng `tên:kiểu:ràng_buộc`, ví dụ:
  `username:String:not_null_unique, email:String:not_null_unique, passwordHash:String:not_null, status:String:not_null`

## Rules

- Entity đặt trong package `{PACKAGE_NAME}.entity`
- Mọi entity kế thừa các field audit: `id`, `createdAt`, `updatedAt`
- Tên bảng: snake_case, số nhiều (ví dụ `users`)
- Tên cột: snake_case
- Migration đặt trong `src/main/resources/db/migration/`, naming `V{n}__{description}.sql`
- Repository kế thừa `JpaRepository<{ENTITY_NAME}, Long>`

## Example Output Structure (Concept)

```java
// {SERVICE_NAME}/src/main/java/{PACKAGE_PATH}/entity/{ENTITY_NAME}.java
package {PACKAGE_NAME}.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "{table_name}")
public class {ENTITY_NAME} {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ... các field từ {FIELDS} ...

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // Getters & Setters
}
```

```sql
-- {SERVICE_NAME}/src/main/resources/db/migration/V{n}__create_{table_name}_table.sql
CREATE TABLE {table_name} (
    id          BIGSERIAL PRIMARY KEY,
    -- ... các cột từ {FIELDS} ...
    created_at  TIMESTAMP NOT NULL,
    updated_at  TIMESTAMP NOT NULL
);
```

```java
// {SERVICE_NAME}/src/main/java/{PACKAGE_PATH}/repository/{ENTITY_NAME}Repository.java
package {PACKAGE_NAME}.repository;

import {PACKAGE_NAME}.entity.{ENTITY_NAME};
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface {ENTITY_NAME}Repository extends JpaRepository<{ENTITY_NAME}, Long> {
    // Custom query methods nếu cần
}
```

## PROJECT_TREE.md Update

Thêm dòng entity, migration, repository mới vào nhánh `{SERVICE_NAME}` trong `PROJECT_TREE.md`. Nếu file chưa tồn tại thì tạo mới.
