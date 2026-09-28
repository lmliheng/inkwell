import { ref } from 'vue'
import { listArticles } from '@/api/article'

/**
 * 文章列表分页逻辑（Home / Category / Tag / Search 共用）。
 * @param {object} opts
 * @param {number} opts.pageSize 每页条数
 * @param {() => object} opts.extraParams 返回额外 query 参数（keyword / category_id 等）
 */
export function useArticleList({ pageSize = 9, extraParams = () => ({}) } = {}) {
  const list = ref([])
  const total = ref(0)
  const page = ref(1)
  const loading = ref(false)

  async function fetch(p = page.value) {
    loading.value = true
    try {
      const data = await listArticles({ page: p, pageSize, ...extraParams() })
      list.value = (data && data.list) || []
      total.value = (data && data.total) || 0
      page.value = p
    } finally {
      loading.value = false
    }
  }

  return { list, total, page, loading, fetch }
}
