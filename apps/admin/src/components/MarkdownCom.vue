<script setup lang="ts">
    import { ref, onMounted, onBeforeUnmount, watch } from 'vue'

    // @ts-ignore - toast-ui editor types not properly exported
    import Editor from '@toast-ui/editor';
    import '@toast-ui/editor/dist/toastui-editor.css';
    import '@toast-ui/editor/dist/i18n/zh-cn';
    import { api } from '@/composables/useAxiosConfig'
    import { ElMessage } from 'element-plus'

    const props = defineProps({
        modelValue: {
            type: String,
            default: ''
        },
        height: {
            type: String,
            default: '650px'
        },
        placeholder: {
            type: String,
            default: '请输入文章内容…'
        }
    })
    const emit = defineEmits<{
        (e: 'update:modelValue', val: string): void
        (e: 'fullscreen-change', val: boolean): void
    }>()

    const editorEl = ref<HTMLDivElement | null>(null)
    const isFullscreen = ref(false)
    let editor: any = null

    onMounted(() => {
        editor = new Editor({
            el: editorEl.value,
            language: 'zh-CN',
            height: props.height,
            initialEditType: 'markdown',
            previewStyle: 'vertical',
            placeholder: props.placeholder,
            initialValue: props.modelValue || '',
            hooks: {
                addImageBlobHook: async (blob: Blob, callback: (url: string, alt: string) => void) => {
                    const formData = new FormData()
                    formData.append('image', blob)
                    try {
                        const res = await api.post('/upload/image', formData) as any
                        if (res && res.data && res.data.url) {
                            callback(res.data.url, (blob as File).name || 'image')
                        } else {
                            ElMessage.error((res && res.message) || '图片上传失败')
                        }
                    } catch (e: any) {
                        ElMessage.error(e?.response?.data?.message || '图片上传失败')
                    }
                },
            },
        })
        editor.on('change', () => {
            emit('update:modelValue', editor.getMarkdown())
        })
        window.addEventListener('keydown', handleEsc)
    })

    watch(() => props.modelValue, (val) => {
        if (editor && val !== editor.getMarkdown()) {
            editor.setMarkdown(val || '')
        }
    })

    const handleEsc = (e: KeyboardEvent) => {
        if (e.key === 'Escape' && isFullscreen.value) {
            setFullscreen(false)
        }
    }

    const setFullscreen = (v: boolean) => {
        isFullscreen.value = v
        emit('fullscreen-change', v)
        if (editor) {
            editor.setHeight(v ? '100%' : props.height)
        }
        if (v) {
            requestAnimationFrame(() => {
                const el = editorEl.value
                if (el && el.querySelector('.toastui-editor-md-container')) {
                    const container = el.querySelector('.toastui-editor-md-container') as HTMLElement
                    if (container) container.style.height = '100%'
                }
            })
        }
    }

    const toggleFullscreen = () => setFullscreen(!isFullscreen.value)

    const getMarkdown = () => (editor ? editor.getMarkdown() : props.modelValue)
    const getHTML = () => (editor ? editor.getHTML() : '')

    defineExpose({ getMarkdown, getHTML, toggleFullscreen, setFullscreen })

    onBeforeUnmount(() => {
        window.removeEventListener('keydown', handleEsc)
        if (editor) {
            editor.destroy()
            editor = null
        }
    })
</script>
<template>
    <div class="editor-shell" :class="{ 'editor-fullscreen': isFullscreen }">
        <div ref="editorEl" class="editor-body"></div>
        <button
            class="editor-fs-btn"
            type="button"
            :title="isFullscreen ? '退出全屏 (Esc)' : '全屏书写'"
            @click="toggleFullscreen"
        >
            <svg v-if="!isFullscreen" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <path d="M8 3H5a2 2 0 0 0-2 2v3m18 0V5a2 2 0 0 0-2-2h-3m0 18h3a2 2 0 0 0 2-2v-3M3 16v3a2 2 0 0 0 2 2h3" />
            </svg>
            <svg v-else class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <path d="M8 3v3a2 2 0 0 1-2 2H3m18 0h-3a2 2 0 0 1-2-2V3m0 18v-3a2 2 0 0 1 2-2h3M3 16h3a2 2 0 0 1 2 2v3" />
            </svg>
        </button>
    </div>
</template>

<style scoped>
.editor-shell {
    position: relative;
    width: 100%;
}
.editor-fullscreen {
    height: 100%;
}

.editor-body :deep(.toastui-editor-defaultUI) {
    border: 1px solid #e4e7ed;
    border-radius: 8px;
    overflow: hidden;
}
.editor-body :deep(.toastui-editor-defaultUI-toolbar) {
    background: #fafafa;
    border-bottom: 1px solid #ebeef5;
    padding: 6px 10px;
}
.editor-body :deep(.toastui-editor-toolbar-group) {
    border-right: 1px solid #ebeef5;
}
.editor-body :deep(.toastui-editor-toolbar-icons) {
    width: 30px;
    height: 30px;
    border-radius: 6px;
    transition: background-color 0.15s ease;
}
.editor-body :deep(.toastui-editor-toolbar-icons:hover) {
    background: #ecf5ff;
}
.editor-body :deep(.toastui-editor-toolbar-icons.active) {
    background: #d9ecff;
}
.editor-body :deep(.toastui-editor-md-container) {
    background: #fff;
}
.editor-body :deep(.toastui-editor-contents) {
    font-size: 15px;
    line-height: 1.8;
}
.editor-body :deep(.toastui-editor-md-splitter) {
    border-left: 1px solid #ebeef5;
}
.editor-body :deep(.toastui-editor-md-preview) {
    background: #fbfbfd;
}

.editor-fs-btn {
    position: absolute;
    top: 48px;
    right: 12px;
    z-index: 10;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 32px;
    height: 32px;
    border: 1px solid #e4e7ed;
    border-radius: 8px;
    background: #fff;
    color: #606266;
    cursor: pointer;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
    transition: color 0.15s ease, border-color 0.15s ease;
}
.editor-fs-btn:hover {
    color: #409eff;
    border-color: #409eff;
}
</style>
