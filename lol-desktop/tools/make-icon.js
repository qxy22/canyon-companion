/**
 * 生成桌面端图标 assets/icon.ico
 *
 * 不引第三方库：自己拼 PNG（zlib 压缩 + CRC32），再套 ICO 容器。
 * 一个 .ico 里塞多个尺寸，安装向导和任务栏取不同分辨率时才不会糊。
 *
 * 造型取自项目主题：墨蓝底 + 海克斯金描边菱形 + 峡谷青的一点高光。
 */
const fs = require('fs')
const path = require('path')
const zlib = require('zlib')

const INK = [0x0a, 0x14, 0x28]      // --ink
const PANEL = [0x12, 0x26, 0x3f]    // --panel
const GOLD = [0xc8, 0xaa, 0x6e]     // --gold
const TEAL = [0x0a, 0xc8, 0xb9]     // --teal

// ------------------------------------------------------------------ CRC32

const CRC_TABLE = (() => {
  const t = new Int32Array(256)
  for (let n = 0; n < 256; n++) {
    let c = n
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1
    t[n] = c
  }
  return t
})()

function crc32(buf) {
  let c = -1
  for (let i = 0; i < buf.length; i++) c = CRC_TABLE[(c ^ buf[i]) & 0xff] ^ (c >>> 8)
  return (c ^ -1) >>> 0
}

function chunk(type, data) {
  const len = Buffer.alloc(4)
  len.writeUInt32BE(data.length)
  const body = Buffer.concat([Buffer.from(type, 'ascii'), data])
  const crc = Buffer.alloc(4)
  crc.writeUInt32BE(crc32(body))
  return Buffer.concat([len, body, crc])
}

/** RGBA 像素数组 -> PNG 字节流。 */
function encodePng(width, height, rgba) {
  const raw = Buffer.alloc((width * 4 + 1) * height)
  let p = 0
  for (let y = 0; y < height; y++) {
    raw[p++] = 0 // filter: None
    for (let x = 0; x < width; x++) {
      const i = (y * width + x) * 4
      raw[p++] = rgba[i]
      raw[p++] = rgba[i + 1]
      raw[p++] = rgba[i + 2]
      raw[p++] = rgba[i + 3]
    }
  }
  const ihdr = Buffer.alloc(13)
  ihdr.writeUInt32BE(width, 0)
  ihdr.writeUInt32BE(height, 4)
  ihdr[8] = 8    // bit depth
  ihdr[9] = 6    // color type RGBA
  ihdr[10] = 0
  ihdr[11] = 0
  ihdr[12] = 0
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr),
    chunk('IDAT', zlib.deflateSync(raw, { level: 9 })),
    chunk('IEND', Buffer.alloc(0)),
  ])
}

// ------------------------------------------------------------------ 绘制

/** 圆角矩形的覆盖率：在外部时用圆角半径，进入主体就是 1。 */
function roundedRect(x, y, w, h, r) {
  if (r > w / 2) r = w / 2
  if (r > h / 2) r = h / 2
  return (px, py) => {
    if (px < x || px >= x + w || py < y || py >= y + h) return 0
    const dx = Math.min(px - x + 0.5, x + w - px - 0.5)
    const dy = Math.min(py - y + 0.5, y + h - py - 0.5)
    if (dx >= r || dy >= r) return 1
    const cx = dx < r ? r - dx : 0
    const cy = dy < r ? r - dy : 0
    if (cx === 0 || cy === 0) return 1
    return Math.hypot(cx, cy) <= r ? 1 : 0
  }
}

/** 以中心定义的菱形（旋转 45° 的方），返回覆盖率。border<0 表示实心。 */
function diamond(cx, cy, half, border) {
  return (px, py) => {
    const d = Math.abs(px - cx + 0.5) + Math.abs(py - cy + 0.5)
    if (border <= 0) return d <= half ? 1 : 0
    return d <= half && d >= half - border ? 1 : 0
  }
}

function mix(dst, i, color, alpha) {
  if (alpha <= 0) return
  dst[i] = Math.round(dst[i] * (1 - alpha) + color[0] * alpha)
  dst[i + 1] = Math.round(dst[i + 1] * (1 - alpha) + color[1] * alpha)
  dst[i + 2] = Math.round(dst[i + 2] * (1 - alpha) + color[2] * alpha)
  dst[i + 3] = Math.max(dst[i + 3], Math.round(255 * alpha))
}

/**
 * 一张尺寸图。
 * 构图：墨蓝圆角底 + 竖向渐变到 panel + 金色菱形描边 + 青色小菱形芯。
 */
function render(size) {
  const rgba = Buffer.alloc(size * size * 4)
  const s = size / 256 // 按 256 设计的坐标换算到当前尺寸

  const plate = roundedRect(0, 0, size, size, 56 * s)

  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      const i = (y * size + x) * 4
      const t = y / size
      // 底色两级：顶INK 到底PANEL，明度差故意压得小
      rgba[i] = Math.round(INK[0] + (PANEL[0] - INK[0]) * t)
      rgba[i + 1] = Math.round(INK[1] + (PANEL[1] - INK[1]) * t)
      rgba[i + 2] = Math.round(INK[2] + (PANEL[2] - INK[2]) * t)
      rgba[i + 3] = Math.round(255 * plate(x, y))
    }
  }

  const cx = size / 2
  const cy = size / 2
  const outer = diamond(cx, cy, 82 * s, 11 * s)
  const inner = diamond(cx, cy, 34 * s, -1)

  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      const i = (y * size + x) * 4
      const base = plate(x, y)
      if (base <= 0) continue
      mix(rgba, i, GOLD, outer(x, y) * base)
      mix(rgba, i, TEAL, inner(x, y) * base)
    }
  }
  return rgba
}

// ------------------------------------------------------------------ ICO

const SIZES = [256, 128, 64, 48, 32, 16]

function buildIco() {
  const images = SIZES.map(size => ({ size, png: encodePng(size, size, render(size)) }))

  const header = Buffer.alloc(6)
  header.writeUInt16LE(0, 0)          // reserved
  header.writeUInt16LE(1, 2)          // type: icon
  header.writeUInt16LE(images.length, 4)

  const entries = []
  let offset = 6 + 16 * images.length
  for (const img of images) {
    const e = Buffer.alloc(16)
    e[0] = img.size === 256 ? 0 : img.size  // 256 在单字节里溢出，按规范写 0
    e[1] = img.size === 256 ? 0 : img.size
    e[2] = 0                                 // palette count
    e[3] = 0                                 // reserved
    e.writeUInt16LE(1, 4)                    // color planes
    e.writeUInt16LE(32, 6)                   // bits per pixel
    e.writeUInt32LE(img.png.length, 8)
    e.writeUInt32LE(offset, 12)
    offset += img.png.length
    entries.push(e)
  }

  return Buffer.concat([header, ...entries, ...images.map(i => i.png)])
}

const out = path.join(__dirname, '..', 'assets', 'icon.ico')
fs.mkdirSync(path.dirname(out), { recursive: true })
const ico = buildIco()
fs.writeFileSync(out, ico)
console.log('已写出 %s（%d 字节，尺寸 %s）', out, ico.length, SIZES.join('/'))
