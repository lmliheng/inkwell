import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js'

const md = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
  highlight(str, lang) {
    const validLang = lang && hljs.getLanguage(lang) ? lang : ''
    let codeHtml
    if (validLang) {
      try {
        codeHtml = hljs.highlight(str, { language: validLang, ignoreIllegals: true }).value
      } catch {
        codeHtml = md.utils.escapeHtml(str)
      }
    } else {
      codeHtml = md.utils.escapeHtml(str)
    }
    // data-lang 供前端 mac 风格标题栏显示语言名
    return '<pre class="hljs" data-lang="' + (validLang || 'text') + '"><code>' + codeHtml + '</code></pre>'
  },
})

// 提取 inline token 的纯文本（用于生成标题 id 与目录条目）
function inlineText(token) {
  if (!token) return ''
  let out = ''
  const walk = (t) => {
    if (!t) return
    if (t.type === 'text' || t.type === 'code_inline') out += t.content
    else if (t.type === 'softbreak' || t.type === 'hardbreak') out += ' '
    else if (t.type === 'image') out += t.content || ''
    else if (t.children) t.children.forEach(walk)
  }
  if (token.children) token.children.forEach(walk)
  return out
}

function slugify(text) {
  return String(text)
    .trim()
    .toLowerCase()
    .replace(/[^\w\u4e00-\u9fa5]+/g, '-')
    .replace(/^-+|-+$/g, '')
}

// 给标题生成稳定 id（含去重），供目录锚点跳转
md.core.ruler.push('heading_anchor', (state) => {
  const used = {}
  state.tokens.forEach((tok, idx) => {
    if (tok.type !== 'heading_open') return
    const inline = state.tokens[idx + 1]
    let id = slugify(inlineText(inline))
    if (!id) id = 'section'
    if (used[id]) {
      used[id] += 1
      id = `${id}-${used[id]}`
    } else {
      used[id] = 1
    }
    tok.attrSet('id', id)
  })
})

export function renderMarkdown(src) {
  return md.render(src || '')
}

// 提取目录：[{ level, text, id }]，id 与渲染后的标题完全一致
export function extractToc(src) {
  const tokens = md.parse(src || '', {})
  const toc = []
  for (let i = 0; i < tokens.length; i++) {
    const tok = tokens[i]
    if (tok.type === 'heading_open') {
      const inline = tokens[i + 1]
      toc.push({
        level: Number(tok.tag.slice(1)),
        text: inline && inline.type === 'inline' ? inlineText(inline) : '',
        id: tok.attrGet('id') || '',
      })
    }
  }
  return toc
}
