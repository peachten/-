/**
 * 复制文本到剪贴板
 * 优先使用 navigator.clipboard（localhost / https 可用），
 * 不可用时退化为临时 textarea + execCommand。
 */
export async function copyText(text) {
  if (!text) {
    return false
  }
  if (navigator.clipboard && window.isSecureContext) {
    try {
      await navigator.clipboard.writeText(text)
      return true
    } catch {
      // 页面失焦 / 权限被拒时走下面的兜底方案
    }
  }

  const textarea = document.createElement('textarea')
  textarea.value = text
  textarea.style.position = 'fixed'
  textarea.style.opacity = '0'
  document.body.appendChild(textarea)
  textarea.select()
  const ok = document.execCommand('copy')
  document.body.removeChild(textarea)
  return ok
}
