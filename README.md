# Account Lockout (Java / Spring Boot)

本地可运行的登录失败防护与账户锁定服务。凭据校验保持最小实现（用户名/密码来自配置），
防护层只依赖校验器返回的「账户是否存在 / 凭据是否匹配」三态结果，不依赖任何校验实现细节。

## 运行

```bash
mvn spring-boot:run
```

默认演示用户：`alice/password123`、`bob/hunter2`；管理员令牌：`admin-secret-token`。

## HTTP 接口

### 登录

```
POST /api/login
Content-Type: application/json

{"username":"alice","password":"password123","source":"1.2.3.4"}
```

`source` 为来源标识（IP、设备等），缺失时记为 `unknown`。

响应体：`{"code": "...", "message": "...", "retryAfterMillis": 42000}`，
其中 `retryAfterMillis` 仅在账户锁定 / 来源拦截时出现。

### 人工解锁（需要管理员权限）

```
POST /api/admin/unlock
X-Admin-Token: admin-secret-token
Content-Type: application/json

{"account":"alice"}
```

### 审计查询（需要管理员权限）

```
GET /api/admin/audit?account=alice&type=LOCK
X-Admin-Token: admin-secret-token
```

查询参数均可省略；`type` 取值：`FAILURE`、`LOCK`、`ESCALATION`、`UNLOCK`。

## 错误码

| code | HTTP 状态 | 含义 |
| --- | --- | --- |
| `SUCCESS` | 200 | 凭据正确且未被锁定 |
| `BAD_CREDENTIALS` | 401 | 账户不存在或密码错误（两者响应完全一致，无法区分） |
| `ACCOUNT_LOCKED` | 423 | 账户处于锁定中，凭据正确也拒绝；`retryAfterMillis` 为剩余锁定时间 |
| `SOURCE_BLOCKED` | 429 | 来源在滑动窗口内失败过多，仅拒绝该来源的尝试 |
| 管理员接口无有效令牌 | 403 | 越权的解锁 / 审计请求被拒绝 |

解锁接口另有 409 `NOT_LOCKED`（账户当前未锁定）。

## 锁定语义

### 两个计数维度

- 账户维度：按用户名在滑动窗口内累计失败，达到 `lockout.account.threshold` 后账户锁定。
- 来源维度：按来源在滑动窗口内累计失败，达到 `lockout.source.threshold` 后仅拒绝该来源的请求，
  不锁定账户；随窗口滑动，窗口内计数回落到阈值以下后来源自动恢复。

判定顺序：账户锁定 → 来源拦截 → 凭据校验。账户锁定期间的请求直接返回 `ACCOUNT_LOCKED`，
不计入任何失败计数。

### 滑动窗口

- 窗口长度可配置；窗口边界之外（时间差 `>= window`）的旧失败自动失效，判定前先剪枝。
- 恰好在边界上的旧失败被排除，边界内 1ms 的失败仍计入。
- 失败时间戳保存在有序列表中，剪枝只移除列表头部的过期项。

### 分级锁定

- 锁定时长按配置的级别列表逐级延长：`lockout.account.levels[0..n]`。
- 一次锁定到期后再次失败到阈值，锁定级别向上提高一级（封顶最高级），时长随之增加。
- 同一时刻一个账户只有一个生效级别；任何升级都以 `max(当前到期时刻, now + 新时长)`
  计算到期时刻，因此绝不会缩短剩余锁定时间。
- 触发锁定的同时清空该账户的窗口失败计数，因此「锁定到期后的下一次失败从最低级别重新计数」
  （失败计数重新从 1 开始累计）。级别链只有在人工解锁后才回到最低级。
- 人工解锁会清空失败计数、锁定级别与到期时刻。

### 并发一致性

- 所有登录判定在单把 `ReentrantLock` 内串行完成，阈值临界点的并发失败恰好触发一次锁定
  （只产生一条 `LOCK` / `ESCALATION` 审计），后续请求必然观察到最新锁定状态。
- 每次状态变更都在锁内完成持久化，避免竞争导致丢失或重复计数。

### 未知账户

- 账户不存在时不写入账户计数、不写入来源计数、不写审计，响应与密码错误完全一致
  （相同错误码、相同消息、相同 HTTP 状态）。
- 凭据正确的登录只在非锁定状态下重置该账户的失败计数。

### 持久化与重启恢复

- 计数、锁定级别、绝对到期时刻、审计记录保存在本地 JSON 文件（`lockout.storage-file`），
  写入采用临时文件 + 原子移动。
- 服务启动时完整加载，计数不重置；锁定到期时刻是绝对时间，因此重启不会缩短剩余锁定时间。
  锁定临界点（触发锁定的那次失败之前）重启，恢复后的行为与不重启完全一致。

### 审计

仅追加四类事件：`FAILURE`、`LOCK`（首次锁定）、`ESCALATION`（升级锁定）、`UNLOCK`。
普通登录（成功 / 密码错误但未到阈值 / 未知账户 / 被锁定拒绝）不会修改既有审计记录；
越权的解锁请求不会产生审计记录。

## 配置项

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `lockout.storage-file` | `data/lockout-state.json` | 计数 / 锁定 / 审计的本地持久化文件 |
| `lockout.account.window` | `5m` | 账户失败计数滑动窗口 |
| `lockout.account.threshold` | `5` | 账户锁定阈值（窗口内失败次数） |
| `lockout.account.levels` | `1m,5m,30m` | 分级锁定时长，逐级延长 |
| `lockout.source.window` | `5m` | 来源失败计数滑动窗口 |
| `lockout.source.threshold` | `10` | 来源拦截阈值（仅拒绝该来源） |
| `lockout.users.<name>` | `alice`、`bob` | 最小凭据存储：用户名=密码 |
| `lockout.admin-tokens` | `admin-secret-token` | 解锁与审计查询认可的管理员令牌 |

时长使用 Spring Boot 的 `Duration` 格式（如 `10s`、`5m`、`1h`）。

## 测试

```bash
mvn test
```

覆盖：窗口滑动与边界包含/排除、并发临界（恰好一次锁定 / 一次来源拦截）、
分级升级与不缩短剩余时间、重启恢复（计数与绝对到期时刻、临界点重启）、
未知账户不可区分且不计数、越权解锁被拒绝，以及 HTTP 错误码映射。
