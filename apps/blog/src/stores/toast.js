import { defineStore } from 'pinia'

let seq = 0

export const useToastStore = defineStore('toast', {
  state: () => ({
    items: [],
  }),
  actions: {
    push(message, type = 'success') {
      const id = ++seq
      this.items.push({ id, message, type })
      setTimeout(() => this.remove(id), 3200)
    },
    remove(id) {
      this.items = this.items.filter((t) => t.id !== id)
    },
    success(message) {
      this.push(message, 'success')
    },
    error(message) {
      this.push(message, 'error')
    },
    info(message) {
      this.push(message, 'info')
    },
  },
})
