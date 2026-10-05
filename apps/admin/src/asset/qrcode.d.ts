declare module 'qrcode' {
    export function toDataURL(text: string, options?: { width?: number; margin?: number }): Promise<string>
    export function toString(text: string, options?: object): Promise<string>
    export function toCanvas(canvas: HTMLCanvasElement, text: string, options?: object): Promise<void>
}
