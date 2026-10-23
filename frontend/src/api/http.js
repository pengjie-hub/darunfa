/**
 * HTTP 请求客户端 —— 对齐 admin.html:167 的 api() 语义。
 *
 * 原实现签名：async function api(path, method = 'GET', body = null, extraHeaders = null, raw = false)
 * 新封装在 axios 基础上逐个参数对齐：
 *   - extraHeaders：透传到每个请求；空值/空字符串自动跳过（admin.html:171-177 的 fetch 行为）
 *   - raw：为 true 时返回完整 {code, subCode, message, data} 且 code !== 0 不抛异常
 *          —— 小运卡片确认第二次返回 code=0 + subCode=DUPLICATE_ACTION 时必须能拿到 subCode
 *   - 401：清 token + 跳登录（需配合路由守卫防循环）
 *   - 403：只抛错，不登出
 *   - 超时 90s：AI 接口后端 deepseek.timeout-seconds=60，默认 Axios 超时会截断
 */
import axios from 'axios'

const TOKEN_KEY = 'token'
const USER_KEY = 'user'

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/api',
  timeout: 90000
})

// 请求拦截器：注入 token
http.interceptors.request.use(config => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers['Authorization'] = `Bearer ${token}`
  }
  // extraHeaders 跳过空值（admin.html:171-177）
  const extra = config.extraHeaders
  if (extra && typeof extra === 'object') {
    for (const [k, v] of Object.entries(extra)) {
      if (v !== null && v !== undefined && String(v) !== '') {
        config.headers[k] = String(v)
      }
    }
  }
  delete config.extraHeaders
  return config
})

// 响应拦截器：处理 HTTP 状态码 + ApiResponse 信封
http.interceptors.response.use(
  resp => {
    const rawMode = resp.config.raw === true
    const json = resp.data

    // HTTP 401/403 分支优先处理（两种模式行为一致）
    if (resp.status === 401) {
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
      // 路由守卫会在跳转前再检查一次，这里只清状态并抛错
      // 不直接 router.push('/login')——避免死循环（拦截器不知道当前路由）
      const err = new Error('登录已过期')
      err.code = 401
      throw err
    }
    if (resp.status === 403) {
      throw new Error(json?.message || '需要管理员权限')
    }

    if (rawMode) {
      // raw=true：返回完整信封，code !== 0 不抛错
      return json
    }
    // 默认模式：code !== 0 抛 message
    if (json.code !== 0) {
      const err = new Error(json.message || '请求失败')
      err.code = json.code
      err.subCode = json.subCode
      throw err
    }
    return json.data
  },
  // 网络错误 / 超时（axios 会走这里）
  err => {
    // 已经在 401/403 里手动 throw 的，status 也是 4xx，但 axios 不一定会走这里
    if (err.response?.status === 401) {
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
      throw new Error('登录已过期')
    }
    if (err.response?.status === 403) {
      throw new Error(err.response.data?.message || '需要管理员权限')
    }
    // 连接失败（后端没起 / ECONNREFUSED）
    if (!err.response && err.code === 'ERR_NETWORK') {
      throw new Error('无法连接到服务器，请确认后端已启动（http://localhost:8080）')
    }
    if (err.code === 'ECONNABORTED') {
      throw new Error('请求超时，请稍后重试（AI 长回复可能需要更久）')
    }
    throw err
  }
)

/**
 * 便捷 api() 包装 —— 与 admin.html 的签名完全一致。
 * @param {string} path   以 / 开头，不带 /api 前缀（baseURL 已处理）
 * @param {string} method HTTP 方法，默认 GET
 * @param {any} body      请求体，对象会自动 JSON.stringify
 * @param {object} extraHeaders  合并的额外请求头（如 Idempotency-Key / X-Proposal-Id）
 * @param {boolean} raw   true 返回完整信封且 code !== 0 不抛错
 * @returns 默认模式直接返回 data 字段；raw 模式返回完整 envelope
 */
export function api(path, method = 'GET', body = null, extraHeaders = null, raw = false) {
  return http.request({
    url: path,
    method,
    data: body ?? undefined,
    extraHeaders,
    raw
  })
}

export { TOKEN_KEY, USER_KEY, http }
export default http
