// 登录/注册页背景漂浮粒子（🎫🎉⭐✨），参数在页面初始化时随机生成
const EMOJIS = ['🎫', '🎉', '⭐', '✨', '🎊']

export const makeParticles = (count = 16) =>
  Array.from({ length: count }, () => ({
    emoji: EMOJIS[Math.floor(Math.random() * EMOJIS.length)],
    left: Math.random() * 100, // 水平位置 %
    size: 18 + Math.random() * 20, // 字号 px
    duration: 10 + Math.random() * 12, // 上升耗时 s
    delay: -Math.random() * 20, // 负延迟：打开页面时粒子已在空中，不至于一片空白
    drift: Math.random() * 60 - 30 // 水平漂移 px
  }))
