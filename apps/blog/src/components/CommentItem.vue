<script setup>
import { ref, computed } from 'vue'
import { formatDate } from '@/utils/format'

const props = defineProps({
  comment: { type: Object, required: true },
  isLoggedIn: { type: Boolean, default: false },
  // 匿名时回复框默认继承的昵称（来自顶层评论表单）
  nicknameHint: { type: String, default: '' },
  // 父评论显示名（子评论用于显示"回复 @xxx"）
  parentName: { type: String, default: '' },
  // 提交评论的异步函数，返回 true 表示成功
  submit: { type: Function, required: true },
})

const replying = ref(false)
const content = ref('')
const nickname = ref('')
const submitting = ref(false)

const avatarText = computed(() => (props.comment.nickname || '匿')[0].toUpperCase())
const isAnonymous = computed(() => !props.comment.user_id)

function startReply() {
  replying.value = true
  nickname.value = props.isLoggedIn ? '' : props.nicknameHint || ''
}

async function submitReply() {
  const text = content.value.trim()
  if (!text) return
  if (!props.isLoggedIn && !nickname.value.trim()) {
    return
  }
  submitting.value = true
  const ok = await props.submit({
    parent_id: props.comment.comment_id,
    content: text,
    nickname: props.isLoggedIn ? undefined : nickname.value.trim(),
  })
  submitting.value = false
  if (ok) {
    content.value = ''
    nickname.value = ''
    replying.value = false
  }
}
</script>

<template>
  <div class="comment-item">
    <div class="flex gap-3">
      <div
        class="flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-sm font-semibold"
        :class="isAnonymous ? 'bg-accent/10 text-muted' : 'bg-accent text-on-accent'"
      >
        {{ avatarText }}
      </div>
      <div class="min-w-0 flex-1">
        <div class="flex items-center gap-2 text-sm">
          <span class="font-medium text-ink">{{ comment.nickname || '匿名' }}</span>
          <span v-if="parentName" class="text-xs text-faint">回复 @{{ parentName }}</span>
          <span v-if="comment.is_author" class="author-tag">作者</span>
          <span v-if="isAnonymous" class="tag">匿名</span>
          <time class="text-xs text-faint">{{ formatDate(comment.created_at) }}</time>
        </div>
        <p class="mt-1 whitespace-pre-wrap text-sm leading-relaxed text-body">{{ comment.content }}</p>

        <button class="mt-2 text-xs text-faint hover:text-accent" @click="replying ? (replying = false) : startReply()">
          {{ replying ? '取消回复' : '回复' }}
        </button>

        <!-- 楼中楼回复框 -->
        <div v-if="replying" class="mt-2">
          <input
            v-if="!isLoggedIn"
            v-model="nickname"
            type="text"
            placeholder="昵称"
            maxlength="50"
            class="input mb-2"
          />
          <textarea
            v-model="content"
            rows="2"
            class="input resize-none"
            :placeholder="`回复 @${comment.nickname || '匿名'}`"
          />
          <div class="mt-2 flex gap-2">
            <button class="primary-btn-sm" :disabled="submitting" @click="submitReply">
              {{ submitting ? '提交中…' : '提交' }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 作者徽标 */
.author-tag {
  display: inline-flex;
  align-items: center;
  border-radius: var(--radius-tag);
  padding: 0.125rem 0.5rem;
  font-size: 0.75rem;
  line-height: 1.25rem;
  background-color: var(--color-accent);
  color: var(--color-on-accent);
  font-weight: 600;
}

.primary-btn-sm {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-tag);
  background-color: var(--color-accent);
  color: var(--color-on-accent);
  padding: 0.375rem 0.75rem;
  font-size: 0.875rem;
  cursor: pointer;
  transition: background-color 0.2s ease;
}
.primary-btn-sm:hover {
  background-color: var(--color-accent-strong);
}
.primary-btn-sm:disabled {
  opacity: 0.6;
}
</style>
