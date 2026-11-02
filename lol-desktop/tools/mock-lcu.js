'use strict'

/**
 * 假 LCU 服务器 —— 开发和回归用。
 *
 * 真客户端不是每台机器都有、也不是每次都开着，而「读本机战绩」这条链路又必须验。
 * 这里用一个自签 HTTPS + Basic Auth 的本地服务冒充 LeagueClientUx：
 * 接口路径、返回结构、认证方式都和国服实测的一致。
 *
 * 数据刻意用**国服老结构**：participants 里没有 puuid，身份在 participantIdentities，
 * 通过 participantId 关联。这正是 MatchExtractor.findSelf 最容易写错的那条分支。
 *
 * 用法：
 *   const lcu = await require('./tools/mock-lcu').start()
 *   // 在某个目录写 lockfile 指向它，然后 lcu.discover(那个目录)
 *   lcu.close()
 */

const https = require('https')
const fs = require('fs')
const os = require('os')
const path = require('path')
const { execFileSync } = require('child_process')

const TOKEN = 'mock-remoting-token-7f3a91'
const PUUID = 'MOCK-PUUID-cn-0001'
const SUMMONER_NAME = '测试召唤师'
const TAG_LINE = '1234'

const LANES = ['MIDDLE', 'TOP', 'JUNGLE', 'BOTTOM', 'UTILITY']

/** 造 n 场对局。每场 10 个人，自己在不同位置，胜负交替。 */
function buildGames(count, puuid = PUUID, selfName = SUMMONER_NAME) {
  const base = 1729000000000 // 2024-10-15 前后的一个 epoch 毫秒
  const games = []

  for (let i = 0; i < count; i++) {
    const gameId = 7000000000 + i
    const win = i % 2 === 0
    const lane = LANES[i % LANES.length]

    const participants = []
    const identities = []

    for (let p = 1; p <= 10; p++) {
      const isSelf = p === 1
      participants.push({
        participantId: p,
        // 国服老结构：这里没有 puuid
        teamId: p <= 5 ? 100 : 200,
        timeline: { lane: isSelf ? lane : LANES[(p + i) % LANES.length], role: 'SOLO' },
        stats: {
          win: isSelf ? win : p <= 5 ? win : !win,
          kills: isSelf ? 7 + i : 2 + (p % 5),
          deaths: isSelf ? 3 : 4 + (p % 3),
          assists: isSelf ? 11 + i : 5 + (p % 4),
          totalMinionsKilled: isSelf ? 182 + i * 3 : 120,
          visionScore: isSelf ? 24 + i : 12,
        },
      })

      identities.push({
        participantId: p,
        player: {
          puuid: isSelf ? puuid : `OTHER-PUUID-${i}-${p}`,
          summonerName: isSelf ? selfName : `路人${p}`,
          gameName: isSelf ? selfName : `路人${p}`,
          tagLine: isSelf ? TAG_LINE : '0001',
        },
      })
    }

    games.push({
      gameId,
      gameCreation: base + i * 3600_000,
      gameDuration: 1800 + i * 60,
      gameMode: 'CLASSIC',
      participants,
      participantIdentities: identities,
    })
  }
  return games
}

function ensureCert(dir) {
  const key = path.join(dir, 'mock-key.pem')
  const cert = path.join(dir, 'mock-cert.pem')
  if (fs.existsSync(key) && fs.existsSync(cert)) return { key, cert }

  execFileSync(
    'openssl',
    [
      'req', '-x509', '-newkey', 'rsa:2048',
      '-keyout', key, '-out', cert,
      '-days', '3', '-nodes',
      '-subj', '/CN=127.0.0.1',
    ],
    { stdio: 'ignore' }
  )
  return { key, cert }
}

/**
 * 起服务。
 *
 * @param {object} [options]
 * @param {number} [options.count] 造多少场对局，默认 4
 * @param {'GOLD'|'NONE'} [options.rankTier] 段位，传 NONE 模拟未定级
 * @param {number} [options.port] 指定端口，默认让系统分配
 * @param {string} [options.certDir] 证书缓存目录
 */
async function start(options = {}) {
  const count = options.count != null ? options.count : 4
  const rankTier = options.rankTier || 'GOLD'
  // 允许自定义身份：同一个数据库反复跑测试时，puuid 有唯一约束
  const puuid = options.puuid || PUUID
  const selfName = options.summonerName || SUMMONER_NAME

  const certDir = options.certDir || path.join(os.tmpdir(), 'lol-mock-lcu')
  fs.mkdirSync(certDir, { recursive: true })

  // createServer 的 key/cert 要 PEM 内容，不是文件路径
  const keyPath = ensureCert(certDir)
  const tls = {
    key: fs.readFileSync(keyPath.key),
    cert: fs.readFileSync(keyPath.cert),
  }

  const games = buildGames(count, puuid, selfName)
  const gameMap = new Map(games.map(g => [g.gameId, g]))

  const summoner = {
    puuid,
    gameName: selfName,
    displayName: '', // 国服实测 displayName 为空
    tagLine: TAG_LINE,
    summonerLevel: 168,
  }

  const ranked = {
    queueMap:
      rankTier === 'NONE'
        ? {}
        : {
            RANKED_SOLO_5x5: { tier: rankTier, rank: 'II', leaguePoints: 47, queueType: 'RANKED_SOLO_5x5' },
            RANKED_FLEX_SR: { tier: 'SILVER', rank: 'IV', leaguePoints: 12, queueType: 'RANKED_FLEX_SR' },
          },
  }

  const expectedAuth = 'Basic ' + Buffer.from(`riot:${TOKEN}`).toString('base64')

  const server = https.createServer(tls, (req, res) => {
    if (req.headers.authorization !== expectedAuth) {
      res.writeHead(401, { 'Content-Type': 'application/json' })
      res.end('{"errorCode":"RPC_ERROR","message":"unauthorized"}')
      return
    }

    const url = new URL(req.url, 'https://127.0.0.1')
    const p = url.pathname
    const send = body => {
      res.writeHead(200, { 'Content-Type': 'application/json' })
      res.end(JSON.stringify(body))
    }

    if (p === '/lol-summoner/v1/current-summoner') {
      return send(summoner)
    }

    if (p === '/lol-ranked/v1/current-ranked-stats') {
      return send(ranked)
    }

    if (p === `/lol-match-history/v1/products/lol/${puuid}/matches`) {
      return send({ games: { games: games.map(g => ({ gameId: g.gameId })) } })
    }

    if (p.startsWith('/lol-match-history/v1/games/')) {
      const id = Number(p.split('/').pop())
      const game = gameMap.get(id)
      if (!game) {
        res.writeHead(404, { 'Content-Type': 'application/json' })
        return res.end('{"message":"game not found"}')
      }
      return send(game)
    }

    res.writeHead(404, { 'Content-Type': 'application/json' })
    res.end('{"message":"not found"}')
  })

  await new Promise((resolve, reject) => {
    server.once('error', reject)
    server.listen(options.port || 0, '127.0.0.1', resolve)
  })

  const port = server.address().port

  return {
    port,
    token: TOKEN,
    puuid,
    summonerName: selfName,
    games,
    baseUrl: `https://127.0.0.1:${port}`,
    close: () => new Promise(resolve => server.close(resolve)),
  }
}

/**
 * 在指定目录写一个 lockfile，让 lcu.discover(dir) 能发现这个假服务。
 * 格式与真 lockfile 一致：name:pid:port:password:protocol
 */
function writeLockfile(dir, instance) {
  fs.mkdirSync(dir, { recursive: true })
  const lockPath = path.join(dir, 'lockfile')
  fs.writeFileSync(lockPath, `LeagueClient:${process.pid}:${instance.port}:${instance.token}:https`, 'utf8')
  return lockPath
}

module.exports = { start, writeLockfile, TOKEN, PUUID, SUMMONER_NAME }
