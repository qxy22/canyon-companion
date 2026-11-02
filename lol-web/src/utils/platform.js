/**
 * 运行环境判定。
 *
 * 桌面客户端由 preload 注入 window.lolDesktop；普通浏览器里它是 undefined。
 * 这份判断决定「录入战绩」能不能真干活 —— 读本机 LCU 只有客户端做得到。
 */
export const IS_DESKTOP = typeof window !== 'undefined' && !!window.lolDesktop
