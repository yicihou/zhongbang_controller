# Zhongbang Controller

中邦控制器后端基础骨架（Spring Boot 3 + Java 21）。

当前仓库只搭好分层架构与一个可运行的设备（Device）示例模块，业务逻辑由后续自行扩展。

## 技术栈

- Java 21
- Spring Boot 3.4
- Spring Web / Validation / Data JPA / Actuator
- 开发环境：H2 内存库
- 生产环境：MySQL（`prod` profile）

## 目录结构

```text
src/main/java/com/zhongbang/controller
├── ZhongbangControllerApplication.java
├── common/                 # 统一响应、异常、错误码
├── config/                 # CORS 等基础设施配置
├── domain/                 # 公共实体基类 BaseEntity
├── device/                 # 示例业务模块（可按此模式复制）
│   ├── controller/
│   ├── service/
│   ├── repository/
│   ├── domain/
│   └── dto/
└── system/                 # 系统探活接口
```

建议新增业务时按 `device` 同样拆包：`controller → service → repository`，DTO 与领域实体分离。

## 快速启动

```bash
mvn spring-boot:run
```

默认端口：`3847`  
默认 profile：`dev`（H2）

可选：

```bash
# 指定生产配置（需提供 MySQL）
export SPRING_PROFILES_ACTIVE=prod
export DB_URL=jdbc:mysql://localhost:3306/zhongbang
export DB_USERNAME=zhongbang
export DB_PASSWORD=secret
mvn spring-boot:run
```

## 已提供接口

| Method | Path | 说明 |
|--------|------|------|
| GET | `/api/v1/system/ping` | 服务探活 |
| GET | `/api/v1/devices` | 设备分页列表 |
| GET | `/api/v1/devices/{id}` | 设备详情 |
| POST | `/api/v1/devices` | 创建设备 |
| PUT | `/api/v1/devices/{id}` | 更新设备 |
| DELETE | `/api/v1/devices/{id}` | 删除设备 |
| GET | `/actuator/health` | Actuator 健康检查 |

统一响应格式：

```json
{
  "code": 0,
  "message": "ok",
  "data": {}
}
```

## 测试

```bash
mvn test
```

## 后续扩展建议

1. 复制 `device` 包作为新业务模块模板。
2. 在 `ErrorCode` 中补充业务错误码。
3. 需要鉴权时在 `config` 下增加 Security 配置。
4. 切换生产库时使用 `prod` profile，并设置 `DB_*` 环境变量。
