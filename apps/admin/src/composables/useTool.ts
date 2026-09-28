export const formatTime = (v: string | Date | number | null | undefined): string => {
    if (!v) return ''
    // 如果是 Date 对象，转成 ISO 字符串
    const str = v instanceof Date ? v.toISOString() : String(v)
    return str.replace('T', ' ').slice(0, 19)
}
