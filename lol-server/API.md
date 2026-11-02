# LOL 战绩交友后端接口文档

- 基础路径：`/api`
- 开发环境前端通过 Vite 代理访问：`http://localhost:8080`
- 鉴权：除「注册/登录」外，所有接口都需要在请求头带 `Authorization: Bearer <token>`（登录接口返回）
- 统一返回结构：`{ "code": 200, "message": "success", "data": ... }`，`code === 200` 表示成功，其余为业务错误码

## 目录

1. [认证](#认证)
2. [用户](#用户)
3. [搜索](#搜索)
4. [在线与匹配推荐](#在线与匹配推荐)
5. [开黑组队](#开黑组队)
6. [战绩](#战绩)
7. [房间聊天](#房间聊天)
8. [缓存与 Redis](#缓存与-redis)
9. [枚举值](#枚举值)

---

## 认证

### 注册 `POST /api/auth/register`

请求体：

```json
{ "username": "duanjin", "nickname": "峡谷之巅", "password": "123456" }
```

成功响应（`data` 为注册成功的用户）：

```json
{ "code": 200, "message": "success", "data": { "id": 1, "username": "duanjin", "nickname": "峡谷之巅" } }
```

### 登录 `POST /api/auth/login`

请求体：

```json
{ "username": "duanjin", "password": "123456" }
```

成功响应：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "token": "<JWT>",
    "user": { "id": 1, "username": "duanjin", "nickname": "峡谷之巅" }
  }
}
```

---

## 用户

### 当前用户基本信息 `GET /api/user/info`

返回登录账号 + 昵称（不含密码）。

```json
{ "code": 200, "message": "success", "data": { "id": 1, "username": "duanjin", "nickname": "峡谷之巅" } }
```

### 获取交友资料 `GET /api/user/profile`

返回当前用户完整资料。

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "nickname": "峡谷之巅",
    "gender": "男",
    "avatar": "https://...",
    "bio": "上单玩得还行",
    "summonerName": "峡谷之巅",
    "puuid": "a1b2c3d4-...",
    "region": "艾欧尼亚",
    "position": "TOP",
    "rankTier": "PLATINUM",
    "status": "ONLINE",
    "lastActiveAt": "2026-10-14T10:00:00"
  }
}
```

其中 `summonerName`、`puuid`、`rankTier` 三项**不是用户填的**，是点「录入战绩」时从本机游戏客户端读出来自动写入的（详见 [战绩](#战绩) 一节的 `POST /api/match/sync`）。未录入过时为 `null`。

### 更新交友资料 `PUT /api/user/profile`

请求体（字段均可选）：

```json
{
  "gender": "男",
  "bio": "上单玩得还行",
  "region": "艾欧尼亚",
  "position": "TOP"
}
```

字段语义：

| 传值 | 效果 |
| --- | --- |
| 不传 / `null` | 保持原值不动 |
| 空字符串 `""` | 清空该字段（`position` 会置为 `null`） |
| 正常值 | 更新该字段 |

> 本接口**不能改**以下三项：
>
> | 字段 | 传非空值时的 `message` |
> | --- | --- |
> | `summonerName` | 召唤师名由「录入战绩」时从游戏客户端自动同步，不能手动填写 |
> | `rankTier` | 段位由「录入战绩」时从游戏客户端自动同步，不能手动填写 |
> | `avatar` | 头像请通过「更换头像」上传图片，不能直接填写图片地址 |
>
> 它们各有唯一的写入来源：前两项来自游戏客户端（见 [战绩](#战绩) 的 `POST /api/match/sync`），
> 头像来自下面的 `POST /api/user/avatar`。
> 段位和游戏内 ID 手填会出现「段位填王者、战绩全是青铜局」这种自相矛盾的档案；
> 头像允许填 URL 的话，别人能拿一个外站链接当头像，服务端会把它缓存下来展示给所有人，
> 那台外部服务器还能顺便知道谁在什么时候访问过。

成功返回更新后的完整资料（结构同 `GET /api/user/profile`）。

### 更换头像 `POST /api/user/avatar`

`multipart/form-data`，字段名 `file`（和聊天文件上传一致）。

| 约束 | 值 |
| --- | --- |
| 格式 | `png` / `jpg` / `jpeg` / `gif` / `webp`（扩展名 + Content-Type 两道校验） |
| 大小 | ≤ 2MB（`app.upload.avatar-max-mb`） |
| 存储 | `{app.upload.dir}/avatar/{yyyy-MM-dd}/{uuid}.{ext}`，对外 URL `/uploads/avatar/...` |

返回更新后的完整资料，`data.avatar` 即新的头像 URL。

- **存新 → 改库 → 删旧**，顺序不能反：先删旧的而新文件又没存上，用户就变成没有头像了。旧文件万一删不掉，由孤儿文件扫描收尾。
- 文件名一律换成 UUID，不接受原始文件名（防路径穿越和中文乱码）。
- 头像与聊天文件分目录存放，孤儿扫描才能各管各的。

### 移除头像 `DELETE /api/user/avatar`

删掉当前头像，回到「昵称首字」的默认头像，同时删除磁盘上的文件。返回更新后的完整资料。
没有头像时再调一次也不会报错（幂等）。

### 查看任意用户资料 `GET /api/user/{id}/profile`

用于「点击组员/玩家，查看他的资料」。返回结构与 `GET /api/user/profile` 完全一致（不含密码和登录账号）。

- 用户不存在时返回 `code: 400`，`message` 为「用户不存在」。
- 与 `GET /api/user/{id}/matches` 配合，即可拼出完整的玩家资料页。

### 切换在线状态 `PUT /api/user/status`

请求体：

```json
{ "status": "ONLINE" }
```

`status` 取值：`ONLINE` / `OFFLINE` / `BUSY`。成功返回更新后的完整资料。

### 自动在线状态（进站即在线 / 关页即离线）

上面那个接口是用户**手动选**状态。页面开着这件事由下面三个接口表达，
前端的 `usePresence` 会自动调用，一般不需要手动调。

| 接口 | 何时调用 | 作用 |
| --- | --- | --- |
| `POST /api/presence/online` | 打开网页、登录成功、从后台切回来 | 置为 `ONLINE`，返回最新资料 |
| `POST /api/presence/heartbeat` | 每 25 秒 | 只刷新活跃时间，**不改状态** |
| `POST /api/presence/offline` | 关闭页面、退出登录 | 置为 `OFFLINE` |

心跳返回：

```json
{ "code": 200, "message": "success", "data": { "online": true } }
```

`online: false` 表示这个人已经不在在线索引里了（被切成「游戏中」、被超时扫掉、或者
Redis 重启后没赶上），前端会在「用户没有手动选状态」时补一次 `/online`。

三条规则：

1. **心跳不改状态**。手动切成「游戏中」的人，心跳不会把他塞回「在线可开黑」。
2. **关页走 keepalive 请求**，页面关掉后请求仍会发完；发不出去（断网、强杀进程）时，
   服务端每 30 秒扫描一次，**超过 90 秒没有心跳就自动置为离线**。
3. **同一个浏览器的多个标签页**：关掉其中一个不算离线，最后一个关掉才报。

### 离线自动退出队伍

状态变成 `OFFLINE` 时，服务端会把这个人在队伍里移除（`OfflineTeamupCleaner`）：

- **关页 / 手动切离线**：先排 6 秒。刷新页面同样会触发一次离线，立刻退队等于
  「按一下 F5 就被踢出房间」；6 秒内重新上线就不退。到期时还会再确认一次
  （数据库状态是 `OFFLINE`、且在线索引里没有他），所以迟到的离线请求不会误伤。
- **心跳超时**：已经 90 秒没动静，不再等，当场退。
- 退队的语义和手动 `leave` 完全一致：最后一人 → 解散；队长 → 交给最早加入的人。
  房间里的提示会写成「某某 离线了，已自动退出房间」，当事人看到「你已离线，已自动退出队伍」。
  人还开着页面时，同一个原因也会通过 `/ws/notify` 的 `teamup_left` 事件推一次。
- `BUSY`（游戏中）**不退队** —— 那是"马上要一起玩"，不是离开。

---

## 搜索

### 多维搜索 `GET /api/search`

查询参数（均可选）：

| 参数 | 说明 |
| --- | --- |
| `position` | 位置，如 `TOP` |
| `rankTier` | 段位，如 `GOLD` |
| `gender` | 性别，如 `男` |
| `keyword` | 昵称/召唤师名模糊匹配 |
| `page` | 页码，默认 1 |
| `size` | 每页条数，默认 10，最大 50 |

响应 `data` 为分页结构：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "list": [ { "id": 2, "nickname": "打野King", "gender": "男", "position": "JUNGLE", "rankTier": "GOLD", "status": "ONLINE" } ],
    "total": 1,
    "page": 1,
    "size": 10
  }
}
```

---

## 在线与匹配推荐

### 在线可开黑列表 `GET /api/online/list?page=&size=`

所有状态为 `ONLINE` 的用户，按最近活跃时间倒序，分页返回。不传参数时默认第 1 页 10 条。

```json
{ "code": 200, "message": "success", "data": { "list": [ { "id": 2, "nickname": "打野King", "position": "JUNGLE", "rankTier": "GOLD", "status": "ONLINE" } ], "total": 127, "page": 1, "size": 10 } }
```

### 匹配推荐 `GET /api/match/recommend?page=&size=`

根据当前用户的段位、位置，推荐在线且段位相近/位置互补的用户，按匹配分倒序，分页返回。不传参数时默认第 1 页 10 条。

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "list": [
      { "user": { "id": 2, "nickname": "打野King", "position": "JUNGLE", "rankTier": "GOLD", "status": "ONLINE" }, "matchScore": 100, "reasons": [ "段位相同", "位置互补" ] }
    ],
    "total": 126, "page": 1, "size": 10
  }
}
```

---

## 开黑组队

### 发布组队 `POST /api/teamup`

请求体：

```json
{
  "gameMode": "RANKED",
  "positions": ["TOP", "JUNGLE"],
  "description": "来两个靠谱的，今晚冲分",
  "maxSize": 5
}
```

成功返回组队详情（发布人自动成为第一个成员，状态 `OPEN`）。

### 组队列表 `GET /api/teamup/list`

返回**我还没加入的**、状态为 `OPEN` 的组队，按发布时间倒序，分页返回（不传参数时默认第 1 页 10 条）。

我加入过的队伍不在这里 —— 它们归 `GET /api/teamup/my`，否则「我的队伍」和「招募中」会重复出现，
而且队长会在招募列表里看到自己点不进去的队伍。`data` 结构：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "list": [
      {
        "id": 1,
        "owner": { "id": 1, "nickname": "峡谷之巅", "position": "TOP", "rankTier": "PLATINUM", "status": "ONLINE" },
        "members": [
          { "id": 1, "nickname": "峡谷之巅", "position": "TOP", "rankTier": "PLATINUM", "summonerName": "峡谷之巅", "region": "艾欧尼亚", "status": "ONLINE", "skillScore": 82, "skillTier": "A" },
          { "id": 3, "nickname": "辅助小陈", "position": "SUPPORT", "rankTier": "GOLD", "summonerName": "小陈", "region": "艾欧尼亚", "status": "ONLINE", "skillScore": 74, "skillTier": "A" }
        ],
        "gameMode": "RANKED",
        "positions": ["TOP", "JUNGLE"],
        "description": "来两个靠谱的，今晚冲分",
        "maxSize": 5,
        "currentSize": 2,
        "status": "OPEN",
        "createdAt": "2026-10-14T10:00:00"
      }
    ],
    "total": 73, "page": 1, "size": 10
  }
}
```

字段说明：

| 字段 | 说明 |
| --- | --- |
| `owner` | 发布人资料，等价于 `members[0]` |
| `members` | 已加入成员的完整资料（`UserProfileVO`），队长排在第一位，顺序与加入顺序一致 |
| `currentSize` | 已加入人数，等于 `members` 的长度 |
| `positions` | 还缺的位置 |

发布组队 `POST /api/teamup` 与加入组队 `POST /api/teamup/{id}/join` 的响应结构与上表相同，可据此直接刷新界面上的成员列表。

### 加入组队 `POST /api/teamup/{id}/join`

成功返回组队详情；满员后 `status` 变为 `CLOSED`。已加入、组队已满或已结束时返回 `code: 400`。

> 并发说明：加入操作在事务内对组队行加悲观写锁后再改成员列表，两个人同时点「加入」不会互相覆盖。

### 退出组队 `POST /api/teamup/{id}/leave`

当前登录用户从该组队中退出。

- 退出后仍有成员 → 返回更新后的组队详情。
- 退出者是队长 → 队长自动交给最早加入的成员，队伍继续存在，不会因队长走了就散伙。
- **最后一个人退出 → 组队直接删除**，返回 `data: null`。前端收到 `null` 应把这条从列表里移除并提示「队伍已解散」。
- 不在队伍里 / 组队不存在 → `code: 400`。

> 满员时 `CLOSED` 的队伍，有人退出后会自动重新变为 `OPEN`，重新出现在招募列表里。

### 移除成员 `POST /api/teamup/{id}/kick/{userId}`

队长把某个成员踢出队伍。`{userId}` 是被移除者的用户 id。

- 返回更新后的组队详情。
- 操作者不是队长 → `code: 400`，message「只有队长可以移除成员」。
- 队长踢自己 → `code: 400`，message「队长不能移除自己，要离开请用「退出队伍」」。
- 被踢者不在队伍里 → `code: 400`。

---

## 战绩

### 一键录入（网页按钮）`POST /api/match/sync`

前端「录入战绩」按钮直接调用，后端会连接**本机**游戏客户端（LCU）读取最近 10 场并入库，无需请求体，需登录态。要求后端与游戏在同一台机器、游戏已启动并登录。

> **后端部署在云上时这个接口必然失败**，因为它扫的是服务器的磁盘。这种场景请用桌面端：
> 桌面端在自己电脑上读 LCU，然后走下面的 `POST /api/match/import` 上报。
> 前端已经按环境分流，浏览器 + 远程后端时不会再发这个请求，而是直接提示去桌面端操作。
> 详见 `lol-desktop/README.md`。

**这一次调用会同时把游戏内身份和段位写进资料**——这是 `summonerName` / `puuid` / `rankTier` 唯一的来源，用户不在别处填。

返回结构：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "profile": { "id": 1, "nickname": "峡谷之巅", "summonerName": "峡谷之巅", "puuid": "a1b2...", "rankTier": "PLATINUM", "skillScore": 78.5, "skillTier": "钻石", "matchCount": 10 },
    "fetchedCount": 10,
    "summonerName": "峡谷之巅",
    "rankSynced": true,
    "rankTier": "PLATINUM",
    "rankDivision": "II",
    "leaguePoints": 62,
    "note": null
  }
}
```

| 字段 | 说明 |
| --- | --- |
| `profile` | 更新后的完整资料 |
| `fetchedCount` | 本次读到的对局数 |
| `rankSynced` | 是否成功读到并写入了段位。`false` 表示客户端没返回段位（未定级），此时资料里的段位**保持原样不清空** |
| `note` | 需要告诉用户的一句话。`null` 表示一切正常；0 场时会提示检查客户端；段位没读到时会说明原因。前端应把它显示出来 |

> 段位取自 `/lol-ranked/v1/current-ranked-stats`，优先 `RANKED_SOLO_5x5`（单双排），取不到再取 `RANKED_FLEX_SR`（灵活组排）。
> 召唤师名只存纯 `gameName`（不含 `#区服` 后缀），因为战绩数据里的名字是纯名字，带后缀会匹配不到自己；唯一性由 `puuid` 保证。

### 手动上传 `POST /api/match/import`

由本地读取器（lol-lcu-reader 的 `Uploader`）调用，读取最近 N 场对局并上传；后端按 `gameId` 去重、逐场评分，重算综合实力分与等级。

请求体：

```json
{
  "puuid": "a1b2c3d4-...",
  "summonerName": "峡谷之巅",
  "rankTier": "PLATINUM",
  "matches": [
    {
      "gameId": 123456,
      "win": true,
      "lane": "MIDDLE",
      "kills": 8,
      "deaths": 3,
      "assists": 12,
      "cs": 210,
      "visionScore": 25,
      "duration": 1500,
      "playedAt": 1720000000000
    }
  ]
}
```

`playedAt` 为 epoch 毫秒。成功返回更新后的用户资料（含 `skillScore` / `skillTier` / `matchCount`）。

| 字段 | 是否必填 | 说明 |
| --- | --- | --- |
| `matches` | 是 | 对局数组 |
| `puuid` | 否 | 客户端读到的玩家唯一 id。传了就覆盖本地值 |
| `summonerName` | 否 | 客户端读到的召唤师名。传了就覆盖本地值（LCU 同步时总是传） |
| `rankTier` | 否 | 客户端读到的段位，必须是已知枚举之一，否则 `400`；不传则段位保持原样 |

> 三个身份字段都是「传了才覆盖、不传不动」，不会把已有值清掉。
> 该接口目前没有来源校验，理论上可以伪造战绩（已知待办）。

### 查看某用户战绩 `GET /api/user/{id}/matches?page=1&size=10`

返回该用户战绩分页，`data` 结构：

```json
{
  "list": [
    {
      "id": 1,
      "gameId": 123456,
      "win": true,
      "lane": "MIDDLE",
      "kills": 8,
      "deaths": 3,
      "assists": 12,
      "kda": 6.67,
      "cs": 210,
      "csPerMin": 8.4,
      "visionScore": 25,
      "score": 82.5,
      "playedAt": "2026-10-14T10:00:00"
    }
  ],
  "total": 10,
  "page": 1,
  "size": 10
}
```

### 评分与等级

- 单场评分（0-100）= 0.35×KDA + 0.25×补刀 + 0.15×视野 + 0.25×胜负（辅助补刀目标降低）。
- 综合实力分 = 最近 10 场单场评分均值。
- 等级映射：≥85 → S、≥70 → A、≥55 → B、≥40 → C、其余 → D。

用户资料接口（`/api/user/profile` 等）返回的 `data` 里已包含 `skillScore`、`skillTier`、`matchCount` 三个字段。

---

## 房间聊天

房间 = 一支组队。队伍创建时房间即存在，队伍解散时消息、文件、已读记录一并删除。
所有接口都要求「你是这个队的成员」，非成员调用返回 `code: 400`「房间不存在，或你已经不在队伍里」。

### WebSocket 通道 `ws://host/ws/chat?token=...&teamupId=...`

- 握手即鉴权：token 无效返回 401，不是成员返回 403。浏览器 WebSocket 无法自定义请求头，所以 token 走 URL。
- 服务端下发格式：`{"event":"message","data":{...消息}}` / `{"event":"notice","message":"..."}` / `{"event":"error","message":"..."}` / `{"event":"closed","message":"..."}` / `{"event":"pong"}`。
- 客户端可发：`{"type":"TEXT","content":"..."}`、`{"type":"IMAGE|VIDEO|STICKER","content":"/uploads/...","fileName":"...","fileSize":123}`、`{"type":"PING"}`。
- `closed` 事件表示服务端要求断开（被移出 / 退出 / 解散），客户端收到后不应再重连。
- 服务端限流：同一用户 5 秒内最多 10 条，超限回 `error`。
- 广播不带「是不是我发的」标记，前端用 `data.user.id === 我的id` 判断。

### 上传聊天文件 `POST /api/chat/{teamupId}/upload?kind=IMAGE|VIDEO`

multipart，字段名 `file`。图片/表情包 ≤5MB（png/jpg/jpeg/gif/webp），视频 ≤20MB（mp4/webm）。
返回 `data: { "url": "/uploads/chat/2026-10-16/xxx.mp4", "name": "原名", "size": 1024 }`，把 `url` 当消息内容从 WebSocket 发出去即可。超限由全局异常处理返回 `code: 400` 具体文案。

### 历史消息 `GET /api/chat/{teamupId}/messages?beforeId=&size=`

正序返回，`beforeId` 传最早一条的 id 用于往上翻页，`size` 默认 30。
消息结构：`{ id, teamupId, user|null, type, content, fileName, fileSize, createdAt }`；
`type` 取值 `TEXT / IMAGE / VIDEO / STICKER / SYSTEM`，系统提示 `user` 为 `null`。

### 未读汇总 `GET /api/chat/unread`

返回 `{ "队伍id": 未读条数 }`，没有未读的房间不出现。未读 = 比我已读位置新、且不是我自己发的消息。

### 标记已读 `POST /api/chat/{teamupId}/read`

body `{ "lastId": 123 }`，不传视为读到最新一条。进入房间后调一次即可消掉大厅的红点。

### 能力与限制 `GET /api/chat/limits`

返回 `{ textMax, imageMaxMb, videoMaxMb, videoMaxSeconds, types }`，前端不把限制写死。

### 生命周期

- 加入 / 退出 / 被移出：房间内广播一条 `SYSTEM` 消息（如「小飞 加入了房间」「小飞 被队长移出了房间」）；当事人还会收到 `closed` 事件并断线，措辞是第二人称（「你被队长移出了房间」）。
- 队伍解散：在线成员收到 `notice`「…队伍已解散」后断线，消息、文件、已读记录级联删除。
- 文件兜底：启动时与每 10 分钟扫描一次上传目录，删除没有任何消息引用且超过 30 分钟保护期的孤儿文件。

---

## 缓存与 Redis

Redis 在**这个系统是加速器，不是数据持有人**。MySQL 是唯一的事实来源，Redis 里有的东西删掉都能
从库里再算出来。据此定了一条硬要求：**Redis 挂掉时服务必须照常可用**，只是变慢，不能报错。

### 键与有效期

| 键 | 内容 | TTL | 写后失效时机 |
| --- | --- | --- | --- |
| `lol:user:{id}` | 单个用户资料 VO | 30 min | 改资料、换/删头像、切在线状态、录入战绩 |
| `lol:online` | 在线用户 ZSET（member=userId，score=最近一次心跳的毫秒时间戳） | 常驻 | 上下线、每次心跳更新；超过 90 秒没心跳由定时任务摘掉 |
| `lol:online:ready` | 「索引已建」标记 | 30 s | 索引重建时重写 |
| `lol:teamup:{id}:members` | 该队伍的成会员 id 集合 | 30 min | 加入 / 退出 / 踢人 / 解散 |
| `lol:teamup:{id}:lastid` | 该房间最新一条消息 id | 60 s | 新消息到达 |

### 一致性：只删不改，先落库再删

统一采用 **写库成功后再删缓存**，不尝试增量更新缓存内容。原因有两个：

- **只删不改**：成员集合的变化散落在「加入 / 退出 / 踢人 / 解散」四处，试图在每一处精确增量更新
  必然漏掉某一条路径；删掉让下次查询按库重建，永远是对的。
- **先库后缓存**：反过来会有个窗口——缓存已删、新值还没写库时，另一个请求读到旧数据并把它写回缓存，
  这份脏数据会一直留到 TTL 到期。

`lol:online:ready` 这个标记是必需的：Redis 里「集合为空」有两种含义——真的没人在线，或索引还没建。
没有这个标记区分，服务一重启就会把「索引尚未建立」误判成「当前无人在线」，在线列表永远空着。

### 降级与自愈

- 所有 Redis 调用都经过 `CacheService`，异常自吞并返回「没拿到」；调用方一律回落到数据库。
- 「没拿到」和「确定不是」必须分开表示：`isMember()` 返回 `null` 才是没拿到，返回 `false` 会把合法用户
  挡在房间外。同理，Redis 的 `SISMEMBER` 对「集合里没这个人」和「集合根本不存在」都返回 `false`，
  必须补一次 `EXISTS` 区分——否则刚发布的队伍连队长都进不了自己的房间，且索引再也建不起来。
- Redis 连不上后进入 **5 秒冷静期**，期间不再重试。没有这个机制，每个请求都要等满连接超时，
  服务会被自己拖垮，比没有缓存还糟。
- 服务启动时用 `ApplicationReadyEvent` 重建在线索引，避免冷启动后第一段时间的空窗。
- 在线索引里的 score 现在是**心跳租约**：`PresenceService` 每 30 秒扫一次，超过 90 秒没心跳的成员
  连同数据库里的 `status` 一起置为离线。Redis 不可用时这一轮直接跳过（没有活跃时间可依据，
  瞎扫只会把全站判成离线）；关页面本身由前端的 keepalive 请求兜着，所以功能不受影响。

### 实测收益

同一份数据集（214 个用户）开合 Redis 对比，数字是该接口**每次请求在 MySQL 里扫过的行数**：

| 接口 | 有 Redis | 无 Redis |
| --- | --- | --- |
| 匹配推荐 `GET /match/recommend` | **0** | 429 |
| 在线可开黑 `GET /online/list` | **0** | 428 |
| 聊天历史 `GET /chat/{id}/messages` | **0** | 6 |
| 招募大厅 `GET /teamup/list` | 59 | 273 |
| 我的队伍 `GET /teamup/my` | 59 | 64 |

四个热接口实现**零行扫描**（原本每次请求都全表扫 200~430 行）。招募大厅与我的队伍剩余的那 59 行
来自一次性批量补成员资料（不是 N+1，不随用户规模增长）。

**这套缓存也是前端轮询的前提**：首页的找队友/招募列表/在线列表每 2 秒静默刷新一次，
没有这层缓存，100 人在线就是每分钟上万次全表扫描，数据库会先于功能崩掉。

以下两处是**有意不缓存**的：`/api/search` 是用户主动触发的实时搜索；`/api/chat/unread` 的红点计数
必须实时，且前端只在进房间时并行拉一次，不做轮询。

配合把 `spring.jpa.open-in-view` 关掉（顺带修出了战绩导入依赖 session flush 的隐式耦合），
连接池不再被长事务占着，JDBC 也开了预处理语句缓存与批量改写。

Redis 整体宕机的实测结果：上述四个接口全部返回 `code: 200`，在线列表耗时从 14 ms 涨到 2043 ms，
功能完全不受影响；Redis 恢复后无需重启，缓存自行重建。

---

## 枚举值

| 字段 | 取值 |
| --- | --- |
| `position` | `TOP`(上单) `JUNGLE`(打野) `MIDDLE`(中单) `BOTTOM`(下路) `SUPPORT`(辅助) |
| `rankTier` | `IRON` `BRONZE` `SILVER` `GOLD` `PLATINUM` `EMERALD` `DIAMOND` `MASTER` `GRANDMASTER` `CHALLENGER` |
| `gameMode` | `RANKED`(排位) `NORMAL`(匹配) `ARAM`(大乱斗) |
| `status` | `ONLINE`(在线) `OFFLINE`(离线) `BUSY`(游戏中) |
| `消息 type` | `TEXT`(文本) `IMAGE`(图片) `VIDEO`(视频) `STICKER`(表情包) `SYSTEM`(系统提示) |

> 业务错误统一返回 `code: 400`，`message` 为具体原因（如「用户名已存在」「该组队已满或已结束」）。未登录或 token 失效时，HTTP 状态码为 401，前端会自动跳转登录页。
