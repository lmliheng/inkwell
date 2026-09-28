
import { ref, type Ref } from 'vue'
import { defineStore } from 'pinia'
import { type RouteRecordNameGeneric, type RouteMeta } from 'vue-router'


export interface PathTagItem {
    name: RouteRecordNameGeneric
    meta: RouteMeta
    fullPath: string
}

export const usePathTagStore = defineStore('pathTag', () => {

    const pathTagsList: Ref<PathTagItem[]> = ref([])
    const setPathTags = (val: PathTagItem[]) => {
        pathTagsList.value = val
    }
    const addPathTag = (val: PathTagItem) => { // val是一个对象，包含路由信息和标签信息
        if (pathTagsList.value.some(item => item.name === val.name)) {
            return
        }
        pathTagsList.value.push(val)
    }
    const removePathTag = (val: PathTagItem) => {
        pathTagsList.value = pathTagsList.value.filter(item => item.name !== val.name)
    }

    /**
     * @没有使用过
     */
    const removeAllPathTags = (val: any) => { // 当前route
        pathTagsList.value = pathTagsList.value.filter(item => item.name == val.name)
    }
    return { pathTagsList, setPathTags, addPathTag, removePathTag, removeAllPathTags }
}, {
    persist: true,
}
)