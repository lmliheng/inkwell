
import { ref } from 'vue'
import { defineStore } from 'pinia'


export const useLangStore = defineStore('lang', () => {

    const lang = ref('cn')
    const setLang = (val: string) => {
        lang.value = val
    }
    return { lang, setLang }
}, {
    persist: true,
}
)