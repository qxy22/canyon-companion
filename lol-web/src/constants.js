// 与后端 lol-server/API.md 对齐的枚举常量

export const POSITIONS = [
  { code: 'TOP', label: '上单' },
  { code: 'JUNGLE', label: '打野' },
  { code: 'MIDDLE', label: '中单' },
  { code: 'BOTTOM', label: '下路' },
  { code: 'SUPPORT', label: '辅助' }
]

export const RANK_TIERS = [
  { code: 'IRON', label: '坚韧黑铁' },
  { code: 'BRONZE', label: '英勇黄铜' },
  { code: 'SILVER', label: '不屈白银' },
  { code: 'GOLD', label: '荣耀黄金' },
  { code: 'PLATINUM', label: '流光铂金' },
  { code: 'EMERALD', label: '璀璨翡翠' },
  { code: 'DIAMOND', label: '璀璨钻石' },
  { code: 'MASTER', label: '超凡大师' },
  { code: 'GRANDMASTER', label: '傲世宗师' },
  { code: 'CHALLENGER', label: '最强王者' }
]

export const GAME_MODES = [
  { code: 'RANKED', label: '排位' },
  { code: 'NORMAL', label: '匹配' },
  { code: 'ARAM', label: '大乱斗' }
]

export const STATUS = [
  { code: 'ONLINE', label: '在线' },
  { code: 'OFFLINE', label: '离线' },
  { code: 'BUSY', label: '游戏中' }
]

export const GENDERS = ['男', '女', '保密']

/**
 * 段位色阶。
 * 用的是游戏里段位本身的颜色，不是另配一套——这条色阶同时是
 * 「段位路标」组件的刻度，所以顺序和颜色都必须跟客户端一致。
 */
export const TIER_LADDER = [
  { code: 'IRON', label: '坚韧黑铁', short: '黑铁', color: '#6B6B6B' },
  { code: 'BRONZE', label: '英勇黄铜', short: '黄铜', color: '#A97142' },
  { code: 'SILVER', label: '不屈白银', short: '白银', color: '#A8B5C0' },
  { code: 'GOLD', label: '荣耀黄金', short: '黄金', color: '#D4A33B' },
  { code: 'PLATINUM', label: '流光铂金', short: '铂金', color: '#4FBFA8' },
  { code: 'EMERALD', label: '璀璨翡翠', short: '翡翠', color: '#3FA76C' },
  { code: 'DIAMOND', label: '璀璨钻石', short: '钻石', color: '#6FA8DC' },
  { code: 'MASTER', label: '超凡大师', short: '大师', color: '#A855C8' },
  { code: 'GRANDMASTER', label: '傲世宗师', short: '宗师', color: '#D4574E' },
  { code: 'CHALLENGER', label: '最强王者', short: '王者', color: '#E8C06A' }
]

export function positionLabel(code) {
  return (POSITIONS.find(p => p.code === code) || {}).label || code || '未填位置'
}

export function rankLabel(code) {
  return (RANK_TIERS.find(r => r.code === code) || {}).label || code || '未定段'
}

/** 段位简称，用于空间紧张的标签（黑铁 / 黄铜 / … / 王者） */
export function rankShort(code) {
  const tier = TIER_LADDER.find(t => t.code === code)
  return tier ? tier.short : '未定段'
}

export function modeLabel(code) {
  return (GAME_MODES.find(m => m.code === code) || {}).label || code || '未选模式'
}

export function statusLabel(code) {
  return (STATUS.find(s => s.code === code) || {}).label || code || '离线'
}

/** 段位在色阶上的位置，0..9；未定段返回 -1 */
export function tierIndexOf(code) {
  return TIER_LADDER.findIndex(t => t.code === code)
}

/** 段位本身的颜色，用于路标和徽章 */
export function tierRampColor(code) {
  const tier = TIER_LADDER.find(t => t.code === code)
  return tier ? tier.color : '#6B6B6B'
}

/** 实力等级 S/A/B/C/D 的颜色，与主题配色一致 */
export function gradeColor(code) {
  const map = {
    S: '#C8AA6E',
    A: '#0AC8B9',
    B: '#7E93AE',
    C: 'rgba(240,230,210,0.5)',
    D: 'rgba(240,230,210,0.32)'
  }
  return map[code] || 'rgba(240,230,210,0.32)'
}
