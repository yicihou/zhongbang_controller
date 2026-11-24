/** 新订单提示音。用 Web Audio 合成两个短音，没有音频文件。浏览器可能拦截，调用方接住异常即可。 */
export function playChime(): void {
  const Ctx = window.AudioContext || (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext
  if (!Ctx) return
  const ctx = new Ctx()
  const now = ctx.currentTime
  ;[880, 1174].forEach((freq, index) => {
    const osc = ctx.createOscillator()
    const gain = ctx.createGain()
    osc.type = 'sine'
    osc.frequency.value = freq
    const start = now + index * 0.12
    gain.gain.setValueAtTime(0.0001, start)
    gain.gain.exponentialRampToValueAtTime(0.05, start + 0.02)
    gain.gain.exponentialRampToValueAtTime(0.0001, start + 0.16)
    osc.connect(gain)
    gain.connect(ctx.destination)
    osc.start(start)
    osc.stop(start + 0.18)
  })
  window.setTimeout(() => {
    void ctx.close()
  }, 700)
}
