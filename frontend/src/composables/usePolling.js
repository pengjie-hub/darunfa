/**
 * usePolling composable —— 轮询收口（改造计划 3.5 第六条）。
 *
 * admin.html 里 setInterval 散落在 5+ 处（客服消息、风险未读、退款待处理、低库存、小运卡片），
 * 而且离开页面不清理就会泄漏（Network 面板 10 秒一条直到浏览器进程死）。
 * 统一从这里出去：
 *   - onMounted start / onBeforeUnmount stop —— 路由离开自动清
 *   - Tab（document.visibilitychange）后台时自动暂停，回到前台时恢复 —— 省电省流量
 *   - 手动 pause/resume 支持（如客服页人工发送后临时暂停轮询）
 *
 * 使用：
 *   const { start, stop, isRunning } = usePolling(() => fetch(), { interval: 5000, immediate: true })
 */
import { onMounted, onBeforeUnmount, onBeforeRouteLeave } from 'vue'

export function usePolling(fn, opts = {}) {
  const {
    interval = 5000,
    immediate = true,           // 启动时立刻跑一次（不等第一个 interval）
    pauseOnHidden = true,       // tab 隐藏时暂停
    onError                    // 单次失败回调，可选
  } = opts

  let timer = null
  let lastRunAt = 0
  const isRunning = { value: false }

  async function tick() {
    try {
      await fn()
    } catch (e) {
      onError?.(e)
    } finally {
      lastRunAt = Date.now()
    }
  }

  function start() {
    if (timer) return
    isRunning.value = true
    if (immediate) tick()
    timer = setInterval(tick, interval)
  }

  function stop() {
    if (timer) { clearInterval(timer); timer = null }
    isRunning.value = false
  }

  function pause() { stop() }
  function resume() { start() }

  // —— tab 可见性切换自动暂停/恢复 ——
  function onVisibilityChange() {
    if (document.hidden) {
      if (timer) { pause(); timer = 'paused' } // 标记一下，方便恢复时识别
    } else if (timer === 'paused') {
      resume()
    }
  }
  if (pauseOnHidden) {
    document.addEventListener('visibilitychange', onVisibilityChange)
  }

  onMounted(start)
  onBeforeUnmount(() => {
    stop()
    document.removeEventListener('visibilitychange', onVisibilityChange)
  })
  // 路由离开也清理（虽然 onBeforeUnmount 也会触发，但这里显式写更安全）
  onBeforeRouteLeave(() => {
    stop()
    document.removeEventListener('visibilitychange', onVisibilityChange)
  })

  return { start, stop, pause, resume, isRunning, lastRunAt }
}
