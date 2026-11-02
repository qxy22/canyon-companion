# 峡谷同行 · 桌面端

把「LOL 战绩查询与交友系统」装进一个窗口，同时补上云端永远做不到的那件事：**读本机游戏客户端**。

## 为什么需要它

「录入战绩」的实现是后端 JVM 直接扫描本机磁盘和进程，找到 `LeagueClientUx.exe` 的  
`--app-port` 与 `--remoting-auth-token`，再连 `127.0.0.1:<port>` 拉最近 10 场。

后端一上云，这段逻辑扫的就是云服务器的磁盘 —— 永远找不到凭证。这不是 bug，是架构决定的：  
**玩家的游戏客户端只在那台电脑上**。

所以桌面端只做两件事，不做第三件：

1. 打开一个窗口，加载你服务器上的页面（好友、组队、聊天、在线状态全部照旧）；
2. 在这台电脑上读 LCU，把结果 **POST 到 `/api/match/import`**。

服务端一行都不用改 —— `/api/match/import` 本来就是给本地读取器用的接口。

```
┌──────────────────────── 玩家电脑 ────────────────────────┐
│                                                          │
│  Electron 主进程                    BrowserWindow         │
│  ┌──────────────────┐              ┌──────────────────┐  │
│  │ 读本机 LCU        │              │ 服务器上的页面    │  │
│  │  ↓ 召唤师/段位    │◄─ IPC ───────│ （Vue 打包产物）  │  │
│  │  ↓ 最近 10 场     │              └────────┬─────────┘  │
│  └────────┬─────────┘                       │            │
│           │ POST /api/match/import           │ 普通 HTTP  │
└───────────┼─────────────────────────────────┼────────────┘
            ▼                                 ▼
      ┌──────────────────────────────────────────────┐
      │  你的服务器（nginx → Spring Boot → MySQL/Redis）│
      └──────────────────────────────────────────────┘
```

## 目录

```
lol-desktop/
├── src/
│   ├── main.js        主进程：窗口、导航白名单、IPC、菜单
│   ├── preload.js     向页面暴露 window.lolDesktop（只暴露具名方法）
│   ├── lcu.js         本机 LCU 读取（lol-lcu-reader 的 Node 版）
│   ├── store.js       服务器地址 / 游戏目录持久化（地址有内置默认值）
│   ├── server-url.js  默认地址 + 地址规整（纯模块，可脱离 Electron 单测）
│   └── pages/
│       ├── setup.html    改服务器地址（菜单进入；地址预填）
│       └── offline.html  连不上服务器时的兜底页
├── tools/
│   ├── mock-lcu.js    假 LCU 服务器（自签 HTTPS + Basic Auth），没游戏也能测
│   └── make-icon.js   生成 assets/icon.ico（纯代码，无第三方库）
└── assets/icon.ico
```

## 开发

```bash
cd lol-desktop
npm install
npm start
```

首次启动**不需要填地址** —— 默认服务器已内置（`src/server-url.js` 的
`DEFAULT_SERVER_BASE`，当前是 `http://47.110.138.40`），打开就直接进站。
地址存在 `%APPDATA%/峡谷同行/config.json`，菜单「文件 → 设置服务器地址」可以改。

改默认地址只改 `DEFAULT_SERVER_BASE` 一处，重新打包；**已装用户不用重装**，
走菜单改即可。

## 打包

```bash
npm run dist        # 产出 release/峡谷同行 Setup 1.0.1.exe（实测约 107MB）
npm run pack        # 只出免安装目录 release/win-unpacked/（约 369MB，含完整 Chromium）
```

安装向导（NSIS）非一键式，用户可选安装目录、生成桌面与开始菜单快捷方式。

### 关于 Electron 二进制下载

国内直连 GitHub 会 0 字节。用镜像：

```bash
ELECTRON_MIRROR=https://registry.npmmirror.com/-/binary/electron/ \
  node node_modules/electron/install.js
```

实测 `registry.npmmirror.com`（约 380 KB/s）比 `cdn.npmmirror.com`（约 198 KB/s）快一倍。

如果 electron-builder 打包时还要再下一次（它有自己的缓存），同样加镜像变量：

```bash
ELECTRON_MIRROR=https://registry.npmmirror.com/-/binary/electron/ npm run dist
```

## 分发

只需要给对方 `峡谷同行 Setup 1.0.1.exe`。安装后：

1. 双击打开 → 直接进站（地址已内置，不用填）；
2. 用系统里的账号登录（和数据网页端同一个）；
3. 点「录入战绩」→ 本机读客户端 → 自动入库。

换服务器地址不用重装：菜单「文件 → 设置服务器地址…」随时能改（设置页会预填当前
地址，从菜单进来时还有「返回，不改了」），保存在  
`%APPDATA%/峡谷同行/config.json`。

**改前端不用重新发客户端**。页面从服务器加载，`dist` 一换所有人下次打开就是新版；  
只有改 `lol-desktop/` 里的代码才需要重新打包。

## 录入战绩的三种处境

同一个按钮，在不同环境下的行为是分开的，避免给出牛头不对马嘴的提示：

| 你在哪                   | 点了会发生什么                             |
| --------------------- | ----------------------------------- |
| 桌面端                   | 本机读 LCU → 上报 `/api/match/import`    |
| 浏览器 + 本机后端（本地开发/答辩演示） | 后端直接读本机 LCU（`/api/match/sync`，原有逻辑） |
| 浏览器 + 远程后端            | 直接提示「录入战绩要在桌面端进行」，不发起请求             |

第三种情况如果照旧发请求，用户会收到「未找到本地游戏客户端凭证」——可他游戏明明开着，  
这个提示会把人带偏。所以前端先判断环境，再决定说什么。

## 找不到客户端时

`lcu.js` 的发现顺序（先的可信度高，会优先试）：

1. `LeagueClientUx.exe` 的命令行 —— **此刻真在跑的**，端口和 token 都新鲜；
2. 游戏目录里的 `lockfile`；
3. 最新的那份 `LeagueClientUx*.log` —— **最不可靠**，它记的是上一次启动的端口；
4. 常见安装路径兜底（C/D/E 盘的 WeGameApps、Riot Games）。

### 装在别的盘、别的目录怎么办：不用管，会自动找

早期版本只猜 C/D/E 盘的几个常见路径，别人装在 `F:\Games\英雄联盟` 就永远找不到，
只能让他手填 —— 而大多数用户根本不知道该填哪个目录。现在有三条路，**按顺序试**：

1. 设置页里手填的目录（有就用，优先级最高）；
2. **注册表卸载表**：卸载项里 `InstallLocation` 常为空，但 `UninstallString`
   一定有（形如 `C:\WeGameApps\英雄联盟\英雄联盟卸载.exe`），顺着它反推根目录；
3. **盘符扫描**：只看各盘根目录下名字像的分支（英雄联盟 / League / WeGame / Riot /
   Tencent / Games）往里两层，命中很快，不翻整个盘；
4. 从正在运行的进程 exe 路径反推；
5. 常见安装路径兜底（C/D/E 盘的 WeGameApps、Riot Games）。

问出来的目录会写进配置，下次直接查，不用再翻一遍盘。**即使这次没连上也写** ——
目录找到了而客户端恰好没开，是很常见的情况。

仍然失败时，在设置页「游戏装在别的地方？」里手动指定目录即可（填到含
`LeagueClient` 的那一层，例如 `F:\Games\英雄联盟`）。

命令行读取走 `Get-CimInstance Win32_Process`（新版 Windows 已移除 `wmic`）。  
带名称过滤的那条查询在少数机器上会被安全软件挡掉（表现为返回空而不是报错），  
这时会自动换成全量遍历的写法再试一次。若仍被权限挡住，用管理员身份运行一次。

### `connect ECONNREFUSED 127.0.0.1:<端口>` 是怎么回事

**不是服务器的问题，也不是没装对。** 它说的是：拿到了一个端口，敲上去没人应答。

最常见的成因是**凭证过期**：客户端每次启动都会换端口、换 token，而日志里留着的是
上一次的。程序已经拿到旧端口，自然连不上。

现在的处理（`connectLive`）：

1. 每个候选先用 TCP 探一下端口通不通（不通立刻换下一个，不白等 HTTP 超时）；
2. 全都不通就**重新发现一轮再试**，最多三轮、每轮隔 2 秒 —— 因为客户端从「进程出现」
   到「接口能应答」中间有几十秒，早点的那一轮必然撞上还没监听的端口；
3. 三轮都不行，才报错，而且报错按原因分开说：

| 诊断            | 界面上会怎么说                                  |
| ------------- | ---------------------------------------- |
| `not-found`   | 没找到客户端：请启动并登录到主界面（只开 WeGame / 登录器不算）    |
| `refused`     | 客户端没响应：多半正在启动或刚退出，等十几秒再点；反复如此就关掉重开      |
| `starting`    | 客户端还在启动中（接口回 404），等进主界面再点                |
| `auth`        | 凭证失效：完全退出英雄联盟后重新登录                       |
| `timeout`     | 连接超时：客户端可能卡住了，重启再试                       |

报错里还会带上端口号和凭证来源（如「凭证来自日志（可能是上一次启动留下的）」），
方便一眼看出是不是读到了旧端口。

### 用户自己能做的三步

1. 确认英雄联盟**客户端本身**已启动并登录到主界面 —— 只开着 WeGame 或登录器不算；
2. 还是不行就**等十几秒再点一次**（录按钮会自动重试三轮，覆盖启动中的情况）；
3. 反复失败就**关掉客户端重开**，让端口和 token 重新生成。

## 没装游戏怎么测

```js
const mock = require('./tools/mock-lcu')
const lcu = require('./src/lcu')

const inst = await mock.start({ count: 3 })
mock.writeLockfile(tmpDir, inst)       // 写一个指向假服务的 lockfile
const result = await lcu.collect(tmpDir, 3)
// result.request 可以直接 POST 到 /api/match/import
await inst.close()
```

假服务用自签证书 + Basic Auth，返回结构与国服实测一致，并且**故意用老结构**  
（`participants` 里没有 puuid，身份在 `participantIdentities` 里按 `participantId` 关联），  
这样 `findSelf` 最容易写错的那条分支才会被真正覆盖到。

## 已知限制

- **只支持 Windows**。LCU 凭证发现依赖 Windows 的进程查询，macOS 上游戏本来也跑不了。
- `/api/match/import` 没有来源校验，理论上可以直接构造请求伪造战绩。  
  桌面端方案没有让它更糟（本来就是这个设计），但要彻底解决得给上传加签名。
- 桌面端不管理登录态：token 在页面的 `localStorage` 里，preload 同源读取后随 IPC 传给主进程。
