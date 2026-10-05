import { api } from './useAxiosConfig'

interface BaseResponse {
    code: number,
    success: boolean,
    message: string
}

interface BaseSearchRequest {
    page: number
    pageSize: number
    keyword?: string
}


/**
 * @用户
 * 部分接口不会拿一些字段，部分字段保留undefined类型
 */

export interface UserItem {
    id?: number
    username: string
    name?: string
    email: string
    avatar: string
    bio: string
    area: string

    vip: number
    checkinDay: number

    socials?: SocialLink[]
    featured_articles?: number[]   // 精选文章 ID 列表
    github_id?: string | null

    created_at?: string
    updated_at?: string

    role_id?: number
    role_name?: string

    article_count?: number
    comment_count?: number
}

interface LoginResponse extends BaseResponse {
    token: string
    user_info: UserItem
}


export const login = (account: string, password: string): Promise<LoginResponse> => api({
    url: '/sys/login',
    method: 'post',
    data: {
        username: account,
        password: password
    }
})


/**
 * 
 * @注册
 * 暂时用any
 */
export const register = (data: any) => api({
    url: '/sys/register',
    method: 'post',
    data
})


// 发送邮箱验证码
export const sendEmailCode = (email: string) => api({
    url: '/email/send-code',
    method: 'post',
    data: { email }
})

// 邮箱验证码登录
export const emailLogin = (email: string, code: string): Promise<LoginResponse> => api({
    url: '/email/login',
    method: 'post',
    data: { email, code }
})



/**
 * @权限项
 */
export interface Permission {
    permission_name: string
    permission_id: number
}

export interface UserInfo extends Partial<UserItem> {
    user_detail?: UserItem
    user_permission?: Permission[]
    login_time?: string
}

export interface UserInfoResponse extends BaseResponse {
    user_info: UserInfo
}

export const requestUserInfo = (): Promise<UserInfoResponse> => api({
    url: '/sys/profile',
    method: 'get'
})

interface UserListRequest {
    keyword: string
    page: number
    pageSize: number
}

interface UserListResponse {
    code: number
    success: boolean
    message: string
    data: {
        list: UserItem[]
        total: number
        page: number
        pageSize: number
        size: number      // 当前页实际返回的数量
    }
}
export const requestUser = (params: UserListRequest): Promise<UserListResponse> => api({
    url: '/user-manage/list',
    method: 'get',
    params
})


// 社交链接
interface SocialLink {
    url: string
    type: string       // 'github' | 'npm' | 'leetcode'
    label: string      // 'GitHub' | 'npm' | '力扣'
}

export const requestUserDetail = (id: number): Promise<UserItem> => api({
    url: `/user-manage/detail/${id}`,
    method: 'get'
})

/**
 * @角色项
 */
interface Role {
    role_id: number
    role_name: string
}

interface RoleListResponse extends BaseResponse {
    list: Role[]
}
export const requestRoleList = (): Promise<RoleListResponse> => api({
    url: `/role/list`,
    method: 'get'
})

export const requestPermissionList = () => api({
    url: `/permission/list`,
    method: 'get'
})


/**
 * @Admin
 * 管理员-用户管理-添加
 */
export const requestUserAdd = (data: UserItem) => api({
    url: '/user-manage/add',
    method: 'post',
    data
})

/**
 * @Admin
 * 管理员-用户管理-更新
 */
export const requestUserUpdate = (data: UserItem) => api({
    url: '/user-manage/update',
    method: 'put',
    data
})

// 管理员更新用户完整信息（含 vip、area、bio、name、checkinDay 等扩展字段）
export const requestUpdateUserFull = (data: UserItem) => api({
    url: '/user-manage/update',
    method: 'put',
    data
})


// 管理员重置用户密码
export const requestUserResetPassword = (data: { id: number, password: string }) => api({
    url: '/user-manage/reset-password',
    method: 'put',
    data
})

export const requestUserDelete = (id: number) => api({
    url: '/user-manage/delete',
    method: 'delete',
    data: { id }
})

// 批量删除用户
export const requestUserDeleteBatch = (ids: number[]) => api({
    url: '/user-manage/delete-batch',
    method: 'post',
    data: { ids }
})

// 本人更新资料（用户名/邮箱）
export const requestSelfUpdate = (data: UserItem) => api({
    url: '/userInfo',
    method: 'put',
    data
})

/**
 * 
 * @角色管理
 * 暂时不管
 */
export const requestRoleAdd = (role_name: string) => api({
    url: '/role/add',
    method: 'post',
    data: { role_name }
})

export const requestRoleUpdate = (role_id: number, role_name: string) => api({
    url: '/role/update',
    method: 'put',
    data: { role_id, role_name }
})

export const requestRoleDelete = (role_id: number) => api({
    url: '/role/delete',
    method: 'delete',
    data: { role_id }
})

export const requestRoleSetPermission = (role_id: number, permission_id_list: number[]) => api({
    url: '/role/setPermission',
    method: 'post',
    data: { role_id, permission_id_list }
})

export const requestRolePermission = (role_id: number) => api({
    url: `/role/permission/${role_id}`,
    method: 'get'
})

/**
 * 
 * @暂时用any
 */
export const requestPermissionUpdate = (data: any) => api({
    url: '/permission/update',
    method: 'put',
    data
})

interface NotificationForm {
    title: string
    content: string
    target_type: 'all' | 'user' | 'role'  // 发送目标类型
    target_id: number | null               // 目标 ID（all 时为 null）
    type: string                           // 通知类型
    importance: 'low' | 'medium' | 'high'  // 重要性
}

/**
 * 
 * @通知系统
 */
// 管理员发布通知
export const requestNotificationAdd = (data: NotificationForm) => api({
    url: '/notification/add',
    method: 'post',
    data
})

// 当前用户收到的通知列表
export const requestNotificationList = () => api({
    url: '/notification/list',
    method: 'get'
})

// 当前用户未读数
export const requestNotificationUnreadCount = () => api({
    url: '/notification/unread-count',
    method: 'get'
})

// 标记已读
export const requestNotificationRead = (notification_id: number) => api({
    url: '/notification/read',
    method: 'post',
    data: { notification_id }
})

// 管理员更新通知
export const requestNotificationUpdate = (data: any) => api({
    url: '/notification/update',
    method: 'put',
    data
})

// 管理员删除通知
export const requestNotificationDelete = (notification_id: number) => api({
    url: '/notification/delete',
    method: 'delete',
    data: { notification_id }
})


/**
 * 
 * @系统监控
 */

export const requestSystemMonitor = () => api({
    url: '/system-monitor',
    method: 'get'
})
// 接口调用统计
export const requestApiStats = () => api({
    url: '/system-monitor/api-stats',
    method: 'get'
})


/**
 * 
 * @评论管理
 */
interface CommentManageListResponse extends BaseResponse {
    data: {
        list: CommentItem[];
        total: number;
        page: number;
        pageSize: number;
    };
}

export interface CommentItem {
    comment_id: number;
    article_id?: number;
    user_id?: number | null;      // 未登录用户为 null
    nickname: string;            // 评论者昵称
    content: string;             // 评论内容
    parent_id?: number | null;    // 父评论ID，顶级评论为 null
    created_at?: string;          // ISO 时间字符串
    article_title?: string | null; // 文章标题，可能为 null
    display_name?: string | null;  // 显示名称，可能为 null
}

export const requestCommentManageList = (params: BaseSearchRequest): Promise<CommentManageListResponse> => api({
    url: '/comment/manage/list',
    method: 'get',
    params
})

export const requestCommentManageUpdate = (data: CommentItem): Promise<BaseResponse> => api({
    url: '/comment/manage/update',
    method: 'put',
    data
})

export const requestCommentManageDelete = (comment_ids: number[]): Promise<BaseResponse> => api({
    url: '/comment/manage/delete',
    method: 'delete',
    data: { comment_ids }
})

/**
 * 
 * @互动管理
 * 点赞和收藏
 */
interface LikeManageListRequest extends BaseSearchRequest {

}

export const requestLikeManageList = (params: LikeManageListRequest) => api({
    url: '/social/admin/likes',
    method: 'get',
    params
})

export const requestLikeManageDelete = (id: number) => api({
    url: `/social/admin/likes/${id}`,
    method: 'delete'
})

export const requestFavoriteManageList = (params: any) => api({
    url: '/social/admin/favorites',
    method: 'get',
    params
})

export const requestFavoriteManageDelete = (id: number) => api({
    url: `/social/admin/favorites/${id}`,
    method: 'delete'
})


/**
 * 
 * @特殊服务接口-博客服务
 */



/**
 * 
 * @获取某用户已发布的文章列表（主页精选文章选择用，公开接口）
 * { page: 1, pageSize: 100 }
 */
export const requestBlogArticles = (username: string, params: { page: number, pageSize: number }) => api({
    url: `/blog/articles/${username}`,
    method: 'get',
    params
})


/**
 * 
 * @获取某用户的公开主页信息（含 socials / featured_articles）
 * 
 */
export const requestBlogProfile = (username: string) => api({
    url: `/blog/profile/${username}`,
    method: 'get'
})

// 本人重置密码
export const requestSelfResetPassword = (password: string) => api({
    url: '/resetPassword',
    method: 'post',
    data: { password }
})


interface ArticleListRequest {
    page: number,
    pageSize: number,
    keyword?: string,
    author?: string,
    category_id?: number,
    status?: string // 待定
}



/**
 * @
 * 文章内容应该单独请求
 */
export interface ArticleItem {
    article_id?: number
    title: string
    content?: string
    status: 0 | 1 | 2
    user_id?: number
    author_name?: string
    created_at?: string
    updated_at?: string
    like_count?: number
    favorite_count?: number
    category_ids?: number[]
    category_names?: string[]
}

interface ArticleListResponse {
    code: number
    success: boolean
    message: string
    data: {
        list: ArticleItem[]
        total: number
        page: number
        pageSize: number
    }
}


// 公开文章列表（分页 + 按分类 + 关键词，仅 status=1 已发布）
export const requestArticleList = (params: ArticleListRequest): Promise<ArticleListResponse> => api({
    url: '/article/list',
    method: 'get',
    params
})


// 当前用户自己的文章列表（分页，含草稿/仅自己可见）
export const requestArticleMine = (params: ArticleListRequest): Promise<ArticleListResponse> => api({
    url: '/article/mine',
    method: 'get',
    params
})

// 文章详情（含作者、分类、正文）
export const requestArticleDetail = (id: number) => api({
    url: `/article/detail/${id}`,
    method: 'get'
})

// 新增文章
export const requestArticleAdd = (data: ArticleItem) => api({
    url: '/article/add',
    method: 'post',
    data
})

// 更新文章
export const requestArticleUpdate = (id: number, data: ArticleItem) => api({
    url: `/article/update/${id}`,
    method: 'put',
    data
})

// 删除文章
export const requestArticleDelete = (id: number) => api({
    url: `/article/delete/${id}`,
    method: 'delete'
})

/**
 * 
 * @文章分类管理
 */

export interface CategoryItem {
    category_id: number
    category_name: string
    created_at: string
    updated_at: string
    user: number           // 创建者用户 ID
    author_name: string    // 创建者名称
}

interface CategoryListResponse extends BaseResponse {
    data: {
        list: CategoryItem[]
    }
}
// 全部分类
export const requestArticleCategoryList = (): Promise<CategoryListResponse> => api({
    url: '/article/category/list',
    method: 'get'
})

// 新增分类
export const requestArticleCategoryAdd = (category_name: string): Promise<BaseResponse> => api({
    url: '/article/category/add',
    method: 'post',
    data: { category_name }
})

// 更新分类
export const requestArticleCategoryUpdate = (category_id: number, category_name: string): Promise<BaseResponse> => api({
    url: '/article/category/update',
    method: 'put',
    data: { category_id, category_name }
})

// 删除分类
export const requestArticleCategoryDelete = (category_id: number): Promise<BaseResponse> => api({
    url: '/article/category/delete',
    method: 'delete',
    data: { category_id }
})



interface AdListRequest {
    page: number, // 页数
    pageSize: number, // 一页的广告数量
    keyword: string | undefined,  //按关键词查询
    position: string | undefined  //按位置查询
}


export type PositionKey = 'article_top' | 'article_bottom' | 'home_mid'
export interface AdDetail {
    id?: number | null
    title: string
    type: 'image' | 'text'
    image_url: string | null
    text_title: string
    text_desc: string
    link_url: string
    position: PositionKey
    sort_order: number
    status: number

    click_count?: number
    created_at?: string
    updated_at?: string
}
interface AdListRespone extends BaseResponse {
    data: {
        list: AdDetail[]
        total: number
        page: number
        pageSize: number
    }
}
interface AdDateilResponse extends BaseResponse {
    data: AdDetail
}

/**
 * 
 * @广告
 */
export const requestAdList = (params: AdListRequest): Promise<AdListRespone> => api({
    url: '/ad/admin/list',
    method: 'get',
    params
})

export const requestAdDetail = (id: number): Promise<AdDateilResponse> => api({
    url: `/ad/admin/detail/${id}`,
    method: 'get'
})

export const requestAdAdd = (data: AdDetail) => api({
    url: '/ad/admin/add',
    method: 'post',
    data
})

export const requestAdUpdate = (id: number, data: AdDetail) => api({
    url: `/ad/admin/update/${id}`,
    method: 'put',
    data
})

/**
 * 
 * @这个接口是什么作用 
 */
export const requestAdStatus = (id: number, status: number) => api({
    url: `/ad/admin/status/${id}`,
    method: 'put',
    data: { status }
})

export const requestAdDelete = (id: number) => api({
    url: `/ad/admin/delete/${id}`,
    method: 'delete'
})

export interface AnnounceListRequest extends BaseSearchRequest {
    status?: string//按位置查询
}

export interface AnnounceListResponse extends BaseResponse {
    data: {
        list: Announce[],
        total: number
        page: number
        pageSize: number
    }
}

export interface Announce {
    id?: number
    title: string
    content: string
    status?: number
    created_at?: string
    updated_at?: string
}

/**
 * 
 * @公告管理
 */
export const requestAnnounceList = (params: AnnounceListRequest): Promise<AnnounceListResponse> => api({
    url: '/announcement/admin/list',
    method: 'get',
    params
})

export const requestAnnounceAdd = (data: Announce) => api({
    url: '/announcement/admin/add',
    method: 'post',
    data
})

export const requestAnnounceUpdate = (id: number, data: Announce) => api({
    url: `/announcement/admin/update/${id}`,
    method: 'put',
    data
})

export const requestAnnounceStatus = (id: number, status: number) => api({
    url: `/announcement/admin/status/${id}`,
    method: 'put',
    data: { status }
})

export const requestAnnounceDelete = (id: number) => api({
    url: `/announcement/admin/delete/${id}`,
    method: 'delete'
})






/**
 * 
 * @API Key管理
 */

export interface ApiKeyItem {
    id?: number
    name: string
    key_prefix: string
    scopes: string
    status?: 0 | 1     // 0=停用, 1=启用
    last_used_at?: string | null
    created_at?: string
}

export interface ApiKeyListResponse extends BaseResponse {
    data: {
        list: ApiKeyItem[]
    }
}

export const requestApiKeyList = (): Promise<ApiKeyListResponse> => api({
    url: '/api-keys',
    method: 'get'
})


export interface ApiKeyCreateRequest {
    name: string
    scopes: 'write' | 'read'
}

interface ApiKeyCreateResponse extends BaseResponse {
    data: ApiKeyData;
}

// API Key 数据
interface ApiKeyData {
    plain: string;          // 完整的密钥明文
    prefix: string;         // 密钥前缀
    scopes: 'write' | 'read'        // 权限范围
}

export const requestApiKeyCreate = (data: ApiKeyCreateRequest): Promise<ApiKeyCreateResponse> => api({
    url: '/api-keys',
    method: 'post',
    data
})

export const requestApiKeyStatus = (id: number, status: 0 | 1): Promise<BaseResponse> => api({
    url: `/api-keys/${id}/status`,
    method: 'put',
    data: { status }
})

export const requestApiKeyDelete = (id: number): Promise<BaseResponse> => api({
    url: `/api-keys/${id}`,
    method: 'delete'
})



/**
 * 
 * @OAuth应用管理
 * 暂时使用any
 */
export const requestOAuthClientList = () => api({
    url: '/oauth/admin/clients',
    method: 'get'
})

export const requestOAuthClientCreate = (data: any) => api({
    url: '/oauth/admin/clients',
    method: 'post',
    data
})

export const requestOAuthClientUpdate = (id: number, data: any) => api({
    url: `/oauth/admin/clients/${id}`,
    method: 'put',
    data
})

export const requestOAuthClientStatus = (id: number, status: 0 | 1) => api({
    url: `/oauth/admin/clients/${id}/status`,
    method: 'put',
    data: { status }
})

export const requestOAuthClientDelete = (id: number) => api({
    url: `/oauth/admin/clients/${id}`,
    method: 'delete'
})



/**
 * 
 * @数据库备份下载
 * blob响应类型，超时时间放宽
 */

export const requestBackupDownload = () => {
    return api({
        url: '/backup/download',
        method: 'get',
        responseType: 'blob',
        timeout: 120000
    }).then((res) => {
        // 拦截器返回 response.data（Blob）；文件名用时间戳兜底
        let blob: any = res
        if (res instanceof Blob) {
            blob = res
        } else if (res && res.data instanceof Blob) {
            blob = res.data
        }
        const filename = `backup-${Date.now()}.zip`
        return { blob, filename }
    })
}