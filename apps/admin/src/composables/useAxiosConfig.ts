import axios from 'axios'
import type { AxiosInstance } from 'axios'

// 扩展 AxiosResponse 类型，支持后端返回的自定义字段
declare module 'axios' {
    interface AxiosResponse<T = any> {
        code?: number
        success?: boolean
        message?: string
        token?: string
    }
}

const api: AxiosInstance = axios.create({
    baseURL: import.meta.env.API_BASE || 'http://127.0.0.1:7000',
    timeout: 10000,
    headers: {
        'Content-Type': 'application/json',
    }
})

api.interceptors.request.use(
    config => {
        // FormData 上传：删除 Content-Type，让浏览器自动生成带 boundary 的 multipart 头。
        // 否则全局默认 application/json 会让 multer 收不到文件（报「请选择图片文件」）。
        if (config.data instanceof FormData) {
            delete config.headers['Content-Type']
        }
        if (localStorage.getItem('auth')) {
            let auth = localStorage.getItem('auth')
            config.headers.Authorization = 'Bearer ' + JSON.parse(auth!).token
        }
        return config
    },
    error => {
        return Promise.reject(error)
    }
)



api.interceptors.response.use(
    response => {
        return response.data
    },
    error => {
        return Promise.reject(error)
    }
)

export { api }