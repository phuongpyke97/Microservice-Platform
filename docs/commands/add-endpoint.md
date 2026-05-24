# Command Template: Add API Endpoint

## Description

Prompt template để thêm một API endpoint RESTful mới vào một service Java đã tồn tại. Bao gồm controller method, service method, request/response DTO và cập nhật `docs/API.md`.

## Usage

```text
Thêm API endpoint mới vào service {SERVICE_NAME}. HTTP method là {HTTP_METHOD}, path là {PATH}. Request body là {REQUEST_BODY} (nếu có), response body là {RESPONSE_BODY}. Output ra controller method, service method, request/response DTO, và cập nhật docs/API.md.
```

## Placeholders

- `{SERVICE_NAME}`: Tên service đích, ví dụ `auth-service`
- `{HTTP_METHOD}`: HTTP Method, ví dụ `POST`, `GET`, `PUT`, `DELETE`
- `{PATH}`: Path của endpoint, ví dụ `/api/v1/users`, `/api/v1/auth/login`
- `{REQUEST_BODY}`: Tên DTO request (PascalCase) và các field, ví dụ `LoginRequest: email:String, password:String` (nếu GET thì có thể là path variable hoặc query param)
- `{RESPONSE_BODY}`: Tên DTO response (PascalCase) và các field, ví dụ `UserResponse: id:Long, email:String, username:String`

## Rules

- Controller đặt trong package `{PACKAGE_NAME}.controller`
- Service logic đặt trong package `{PACKAGE_NAME}.service`
- Request DTO đặt trong `{PACKAGE_NAME}.dto.request`
- Response DTO đặt trong `{PACKAGE_NAME}.dto.response`
- Mọi response thành công PHẢI wrap trong `ApiResponse<T>`
- Endpoint PHẢI có `@PreAuthorize` nếu cần phân quyền

## Example Output Structure (Concept)

```java
// {SERVICE_NAME}/src/main/java/{PACKAGE_PATH}/controller/{SERVICE_NAME}Controller.java
package {PACKAGE_NAME}.controller;

import {PACKAGE_NAME}.dto.request.{REQUEST_DTO_NAME};
import {PACKAGE_NAME}.dto.response.{RESPONSE_DTO_NAME};
import {PACKAGE_NAME}.service.{SERVICE_NAME}Service;
import com.platform.common.core.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("{base_path}")
public class {SERVICE_NAME}Controller {

    private final {SERVICE_NAME}Service {service_name}Service;

    public {SERVICE_NAME}Controller({SERVICE_NAME}Service {service_name}Service) {
        this.{service_name}Service = {service_name}Service;
    }

    @DeleteMapping("{endpoint_path}") // Ví dụ
    @ResponseStatus(HttpStatus.NO_CONTENT) // Ví dụ cho DELETE
    // @PreAuthorize("hasRole('ADMIN')") // Ví dụ phân quyền
    public ApiResponse<{RESPONSE_DTO_NAME}> {method_name}(@RequestBody {REQUEST_DTO_NAME} request) {
        {RESPONSE_DTO_NAME} response = {service_name}Service.{service_method_name}(request);
        return new ApiResponse<>(true, "Successful", response, System.currentTimeMillis());
    }
}
```

```java
// {SERVICE_NAME}/src/main/java/{PACKAGE_PATH}/service/{SERVICE_NAME}Service.java
package {PACKAGE_NAME}.service;

import {PACKAGE_NAME}.dto.request.{REQUEST_DTO_NAME};
import {PACKAGE_NAME}.dto.response.{RESPONSE_DTO_NAME};
import org.springframework.stereotype.Service;

@Service
public class {SERVICE_NAME}Service {

    public {RESPONSE_DTO_NAME} {service_method_name}({REQUEST_DTO_NAME} request) {
        // Business logic here
        return new {RESPONSE_DTO_NAME}(/* ... */);
    }
}
```

```java
// {SERVICE_NAME}/src/main/java/{PACKAGE_PATH}/dto/request/{REQUEST_DTO_NAME}.java
package {PACKAGE_NAME}.dto.request;

import lombok.Data;

@Data
public class {REQUEST_DTO_NAME} {
    // ... fields from {REQUEST_BODY} ...
}
```

```java
// {SERVICE_NAME}/src/main/java/{PACKAGE_PATH}/dto/response/{RESPONSE_DTO_NAME}.java
package {PACKAGE_NAME}.dto.response;

import lombok.Data;

@Data
public class {RESPONSE_DTO_NAME} {
    // ... fields from {RESPONSE_BODY} ...
}
```

## docs/API.md Update

Thêm mô tả API endpoint mới vào `docs/API.md`. Nếu file chưa tồn tại thì tạo mới.

```markdown
# API Documentation

## {SERVICE_NAME}

### {HTTP_METHOD} {PATH}

**Description**: Mô tả ngắn gọn về endpoint.

**Request Body**: `{REQUEST_DTO_NAME}`

```json
{
  // ... example request body ...
}
```

**Response Body**: `ApiResponse<{RESPONSE_DTO_NAME}>`

```json
{
  "success": true,
  "message": "Successful",
  "data": {
    // ... example response data ...
  },
  "timestamp": 1678886400000
}
```

**Error Codes**:
- `SERVICE_ERROR_CODE_XYZ`

```
