# Account Lockout (Java / Spring Boot)

本地可运行的登录失败防护与账户锁定服务。防护层独立于凭据校验，凭据服务仅提供最小内存实现。

## 运行

```bash
mvn spring-boot:run
# 或
mvn package && java -jar target/account-lockout-1.0-SNAPSHOT.jar
```

默认服务端口 `8080`，演示用户：`alice/password1`、`bob/password2`。

## 接口

### 登录

```bash
curl -i -X POST http://localhost:8080/api/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"password1"}'
```

来源（source）取 `X-Forwarded-For` / `X-Real-IP` 请求头，缺省为 TCP 对端地址。

### 管理接口（需 Bearer Token）

```bash
# 人工解锁
curl -i -X POST 'http://localhost:8080/api/admin/unlock?username=alice&operator=ops-1' \
  -H 'Authorization: Bearer change-me-admin-token'

# 审计查询（可按用户名过滤）
curl 'http://localhost:8080/api/admin/audit?username=alice&limit=50' \
  -H 'Authorization: Bearer change-me-admin-token'

# 锁定状态
curl 'http://localhost:8080/api/admin/status?username=alice' \
  -H 'Authorization: Bearer change-me-admin-token'
```

## 锁定语义

1. **双维度失败计数**：按“账户”和“来源 IP”分别进行滑动窗口计数。
   - 账户失败数达到最低锁定级别阈值后账户进入锁定，锁定期间即使凭据正确也返回 `ACCOUNT_LOCKED`。
   - 来源失败数达到 `lockout.source-threshold` 后仅拒绝该来源的尝试，返回 `SOURCE_BLOCKED`；账户优先于来源判定。
2. **分级锁定**：随累计失败次数逐级提高锁定时长。同一账户同一时刻只有一个生效级别；升级时新到期时间取
   `max(原到期时间, 当前时间 + 新级别时长)`，因此升级不会缩短剩余锁定时间。
3. **滑动窗口**：窗口之外的旧失败在每次判定前自动剔除；窗口边界采用排他语义（恰好等于窗口长度的失败仍计入）。
4. **串行一致**：所有登录判定在进程内同一监视器上串行执行；阈值临界点的一批并发失败恰好触发一次锁定事件，
   后完成判定的请求一定观察到最新锁定状态。
5. **锁定期间**：所有尝试（无论凭据对错）都被拒绝并按失败计数，使锁定期间可继续累计升级；
   锁定到期后下一次失败从最低级别重新计数。
6. **未知账户**：不创建任何账户计数状态，HTTP 响应与密码错误完全一致（`401 INVALID_CREDENTIALS`）。
7. **成功登录**：仅在账户未锁定、来源未被拦截时放行；放行后重置该账户的失败计数。来源计数不受影响。
8. **人工解锁**：清空账户失败计数与锁定状态，并记录 `UNLOCKED` 审计事件；需要管理员 Token，越权返回 `403 FORBIDDEN`。

## 持久化与重启恢复

- 状态文件：`${lockout.storage-dir}/state.json`，采用“写临时文件 + 原子 rename”，每次状态变更后立即落盘。
- 审计事件与计数/锁定状态在同一份状态中原子写入，普通登录只追加审计，不修改历史事件。
- 服务启动时加载磁盘状态：失败计数、累计计数、锁定级别与**绝对到期时间戳**完整恢复，
  因此重启不会重置计数，也不会缩短或延长剩余锁定时间。

## 错误码

| 场景            | HTTP | errorCode            |
|-----------------|------|----------------------|
| 密码错误/未知账户 | 401  | `INVALID_CREDENTIALS`|
| 账户已锁定       | 423  | `ACCOUNT_LOCKED`     |
| 来源被拦截        | 403  | `SOURCE_BLOCKED`     |
| 越权管理请求      | 403  | `FORBIDDEN`          |
| 畸形请求体        | 400  | `BAD_REQUEST`        |

## 配置项（`application.properties`）

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `lockout.failure-window` | 滑动计数窗口长度（Duration，如 `15m`） | `15m` |
| `lockout.source-threshold` | 来源维度失败阈值 | `10` |
| `lockout.levels[n].failures` | 触发第 n+1 级锁定的累计失败数，须严格递增 | 5 / 8 / 12 |
| `lockout.levels[n].duration` | 第 n+1 级锁定时长，须不递减 | 1m / 5m / 30m |
| `lockout.storage-dir` | 状态文件目录 | `data/lockout` |
| `lockout.admin-token` | 管理接口 Bearer Token（可用 `LOCKOUT_ADMIN_TOKEN` 覆盖） | `change-me-admin-token` |
| `lockout.demo-users` | 内存用户列表，格式 `user:pass,user:pass` | `alice:password1,bob:password2` |

最低锁定级别的 `failures` 同时作为账户首次锁定阈值。

## 审计事件

`FAILURE`（每次凭据错误）、`LOCKED`（首次进入锁定）、`ESCALATED`（锁定期间升级）、
`UNLOCKED`（人工解锁）。事件含全局递增序号、时间、类型、用户名、来源、级别与详情，
可通过 `/api/admin/audit` 查询。

## 测试

```bash
mvn test
```

覆盖：滑动窗口与边界排他、并发临界（恰好一次锁定、无丢失/重复计数）、分级升级且不缩短剩余时间、
锁定到期回到最低级、重启恢复（含临界点与剩余时间）、未知账户不可区分、来源拦截、越权解锁与审计查询。
