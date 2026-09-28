export function formatDate(value) {
  if (!value) return ''
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return String(value)
  return d.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  })
}

// 仅日期：YYYY-MM-DD（用于归档 / 卡片等场景）
export function formatDay(value) {
  if (!value) return ''
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return String(value)
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

// 从 markdown 提取纯文本摘要（去掉代码块、链接、标记符号）
export function excerpt(markdown, len = 110) {
  const text = String(markdown || '')
    .replace(/```[\s\S]*?```/g, ' ')
    .replace(/`[^`]*`/g, ' ')
    .replace(/!\[[^\]]*\]\([^)]*\)/g, ' ')
    .replace(/\[([^\]]*)\]\([^)]*\)/g, '$1')
    .replace(/[#>*_~|]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
  return text.length > len ? text.slice(0, len) + '…' : text
}

// 估算阅读时长（分钟）：中文按 300 字/分钟，英文按 200 词/分钟
export function readingTime(content) {
  const text = String(content || '')
  const cjk = (text.match(/[\u4e00-\u9fa5]/g) || []).length
  const words = (text.replace(/[\u4e00-\u9fa5]/g, ' ').match(/[A-Za-z0-9]+/g) || []).length
  const minutes = Math.ceil(cjk / 300 + words / 200)
  return Math.max(1, minutes)
}
