import { defineStore } from 'pinia'

/**
 * 当前浏览的「博客作者」上下文。
 * 文章详情页加载后会 setAuthor(...)，Sidebar 据此显示该作者的头像/昵称/简介和作者导航；
 * 离开文章详情页时 clear()，Sidebar 回退到站点默认（登录用户或 JScreator）。
 */
export const useAuthorStore = defineStore('author', {
  state: () => ({
    current: null, // { username, name, avatar, bio }
  }),

  getters: {
    hasAuthor: (state) => !!state.current,
  },

  actions: {
    setAuthor(author) {
      this.current = author || null
    },
    clear() {
      this.current = null
    },
  },
})
