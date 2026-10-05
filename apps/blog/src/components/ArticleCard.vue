<script setup>
import { computed } from 'vue'
import { excerpt, formatDate } from '@/utils/format'

/**
 * 文章卡片 —— 供首页 / 归档 / 搜索等列表复用。
 *
 * 主要用法（向后兼容现有页面）：
 *   <ArticleCard :article="article" />
 *
 * `article` 字段约定（后端未提供的字段会自动回退、优雅省略）：
 *   - article_id      必填，用于生成跳转链接 /article/:id
 *   - title           标题
 *   - content         正文 Markdown（用于自动生成摘要）
 *   - summary|excerpt 已生成的摘要（优先级高于 content）
 *   - cover|cover_image|thumbnail  封面图（可选，缺省不渲染）
 *   - category_names  分类名数组（渲染为标签）
 *   - tag_names|tags  标签名数组（可选，渲染为次级标签）
 *   - created_at      发布时间
 *   - reading_time|readingTime  阅读时长（分钟，可选）
 *   - author_name     作者（可选）
 */
const props = defineProps({
  article: { type: Object, default: () => ({}) },
  // 紧凑模式：内边距更小、摘要限 2 行、隐藏次级标签（供个人主页 3 列使用）
  compact: { type: Boolean, default: false },
})

const title = computed(() => props.article.title || '无标题')

const link = computed(() => `/article/${props.article.article_id ?? props.article.id ?? ''}`)

const cover = computed(
  () => props.article.cover || props.article.cover_image || props.article.thumbnail || '',
)

const summary = computed(() => {
  if (props.article.summary || props.article.excerpt) {
    return props.article.summary || props.article.excerpt
  }
  return excerpt(props.article.content)
})

const categories = computed(() => {
  const v = props.article.category_names || props.article.categories || []
  return Array.isArray(v) ? v.filter(Boolean) : []
})

const tags = computed(() => {
  const v = props.article.tag_names || props.article.tags || []
  return Array.isArray(v) ? v.filter(Boolean) : []
})

const readingTime = computed(() => props.article.reading_time || props.article.readingTime || '')

const date = computed(() => formatDate(props.article.created_at))

const author = computed(() => props.article.author_name || '')
</script>

<template>
  <article class="group flex flex-col overflow-hidden rounded-card bg-card transition-shadow duration-300 hover:shadow-lg">
    <!-- 封面图（可选） -->
    <RouterLink v-if="cover" :to="link" class="block aspect-[16/9] overflow-hidden bg-line">
      <img
        :src="cover"
        :alt="title"
        loading="lazy"
        class="h-full w-full object-cover transition-transform duration-300 group-hover:scale-105"
      />
    </RouterLink>

    <div class="flex flex-1 flex-col" :class="compact ? 'p-4' : 'p-5 md:p-[25px] xl:p-[30px]'">
      <!-- 分类标签 -->
      <div v-if="categories.length" class="flex flex-wrap gap-2" :class="compact ? 'mb-2' : 'mb-3'">
        <span v-for="c in categories" :key="c" class="tag">{{ c }}</span>
      </div>

      <!-- 标题 -->
      <h2 :class="compact ? 'text-base font-bold leading-snug' : 'text-lg font-bold leading-snug md:text-xl'">
        <RouterLink :to="link" class="text-ink transition-colors group-hover:text-accent">
          {{ title }}
        </RouterLink>
      </h2>

      <!-- 摘要 -->
      <p
        v-if="summary"
        class="mt-2 flex-1 text-sm leading-relaxed text-muted"
        :class="compact ? 'line-clamp-2' : ''"
      >{{ summary }}</p>

      <!-- 次级标签（紧凑模式隐藏） -->
      <div v-if="tags.length && !compact" class="mt-3 flex flex-wrap gap-1.5">
        <span
          v-for="t in tags"
          :key="t"
          class="rounded-tag border border-line px-2 py-0.5 text-xs text-faint"
        >
          {{ t }}
        </span>
      </div>

      <!-- 元信息 -->
      <div class="mt-4 flex items-center justify-between gap-2 border-t border-line pt-3 text-xs text-faint" :class="compact ? 'mt-3 pt-2' : ''">
        <span class="flex min-w-0 items-center gap-2">
          <time class="shrink-0">{{ date }}</time>
          <span v-if="readingTime" class="shrink-0">· {{ readingTime }} 分钟阅读</span>
        </span>
        <span v-if="author" class="shrink-0">{{ author }}</span>
      </div>
    </div>
  </article>
</template>
