/**
 * 桌面客户端的安装包地址。
 *
 * 指向前端同源的静态路径：nginx 里 /download/ 直接读盘，不经过后端。
 * 服务器上的文件名保持 ASCII、不带版本号 —— 发新版直接覆盖同一个文件，
 * 页面上的链接永远不用跟着改，也不会因为中文/空格被编码成奇怪的样子。
 *
 * 本地 `vite dev` 时这里没有对应文件（点了会 404），属于预期，
 * 因为本地通常直接跑 Electron 测录入。
 */
export const CLIENT_DOWNLOAD_URL = '/download/lol-desktop-setup.exe'

/** 客户端只支持 Windows（要读本机 LCU + 注册表里的安装目录）。 */
export const CLIENT_PLATFORM_LABEL = 'Windows'

/**
 * 触发下载。
 *
 * 用 location.href 而不是 window.open：安装包是 application/octet-stream，
 * 浏览器会直接开始下载、不跳页；open 反而可能留下一个一闪而过的空白标签页。
 */
export function downloadClient() {
  window.location.href = CLIENT_DOWNLOAD_URL
}
