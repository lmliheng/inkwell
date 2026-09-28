import { createRouter, createWebHashHistory, createWebHistory } from 'vue-router'

import HomeView from '../views/HomeView.vue'
import AuthView from '../views/AuthView.vue'
import ForgotPassword from '../views/ForgotPassword.vue'
import UserProfile from '../views/privateViews/UserProfile.vue'
import UserInfo from '../views/UserInfo.vue'
import ArticleManage from '../views/privateViews/ArticleManage.vue'
import PermissionManage from '../views/privateViews/PermissionManage.vue'
import RoleManage from '../views/privateViews/RoleManage.vue'
import ArticleCreate from '../views/privateViews/ArticleCreate.vue'
import UserManage from '../views/privateViews/UserManage.vue'
import NotificationCenter from '../views/privateViews/NotificationCenter.vue'
import NotificationManage from '../views/privateViews/NotificationManage.vue'
import SystemMonitor from '../views/privateViews/SystemMonitor.vue'

/**
 * @后续路由要改成以身份划分
 */
const Routes = [
    {
        path: '/error',
        name: 'error_page',
        redirect: '/error',
        component: () => import('@/views/ErrorPage.vue'),
    },
    {
        path: '/',
        name: 'home',
        redirect: '/user-profile',
        component: HomeView,
        meta: {
            title: '首页',
            icon: 'home',
            private: false,
        },
        children: [

            {
                path: '/user-profile',
                name: 'user-profile',
                component: UserProfile,
                meta: {
                    title: '用户配置',
                    icon: 'user-profile',
                    private: false,
                }
            },
            {
                path: '/user',
                name: 'user',
                meta: {
                    title: '用户',
                    icon: 'user',
                    private: true,
                },
                children: [
                    {
                        path: '/user/user-info/:id',
                        props: true,
                        name: 'user-info',
                        component: UserInfo,
                        meta: {
                            title: '用户信息',
                            icon: 'user-info',
                            private: false,
                        }
                    },
                    {
                        path: '/user/user-manage',
                        name: 'user-manage',
                        component: UserManage,
                        meta: {
                            title: '用户管理',
                            icon: 'user-manage',
                            private: true,
                            roles: [1], // 仅 admin
                        },

                    },
                    {
                        path: '/user/home-setting',
                        name: 'home-setting',
                        component: () => import('../views/privateViews/HomeSetting.vue'),
                        meta: {
                            title: '主页设置',
                            icon: 'home-setting',
                            private: true,
                            roles: [1, 2, 3], // admin 可设置所有人；普通用户设置自己
                        },
                    },
                    {
                        path: '/user/role-manage',
                        name: 'role-manage',
                        component: RoleManage,
                        meta: {
                            title: '角色管理',
                            icon: 'role-manage',
                            private: true,
                            roles: [1], // 仅 admin
                        }
                    },
                    {
                        path: '/user/permission-manage',
                        name: 'permission-manage',
                        component: PermissionManage,
                        meta: {
                            title: '权限管理',
                            icon: 'permission-manage',
                            private: true,
                            roles: [1], // 仅 admin
                        }
                    }

                ]

            },
            {
                path: '/notification',
                name: 'notification',
                meta: {
                    title: '通知',
                    icon: 'notification',
                    private: false,
                },
                children: [
                    {
                        path: '/notification/notification-center',
                        name: 'notification-center',
                        component: NotificationCenter,
                        meta: {
                            title: '通知中心',
                            icon: 'notification-center',
                            private: false,
                        }
                    },
                    {
                        path: '/notification/notification-manage',
                        name: 'notification-manage',
                        component: NotificationManage,
                        meta: {
                            title: '通知管理',
                            icon: 'notification-manage',
                            private: true,
                            roles: [1], // 仅 admin
                        }
                    }
                ]
            },
            {
                path: '/article',
                name: 'article',
                meta: {
                    title: '文章',
                    icon: 'article',
                    private: false,
                },
                children: [
                    {
                        path: '/article/article-manage',
                        name: 'article-manage',
                        component: ArticleManage,
                        meta: {
                            title: '文章管理',
                            icon: 'article-manage',
                            private: true,
                            roles: [1, 2, 3], // admin + editor + 普通用户（自己的文章）
                        }
                    },
                    {
                        path: '/article/article-create',
                        name: 'article-create',
                        component: ArticleCreate,
                        meta: {
                            title: '文章创建',
                            icon: 'article-create',
                            private: true,
                            roles: [1, 2, 3], // admin + editor + 普通用户（自己的文章）
                        }
                    },
                    {
                        path: '/article/interaction-manage',
                        name: 'interaction-manage',
                        component: () => import('@/views/privateViews/InteractionManage.vue'),
                        meta: {
                            title: '互动管理',
                            icon: 'interaction-manage',
                            private: true,
                            roles: [1], // 仅 admin（点赞/收藏记录）
                        }
                    },
                ]
            },
            {
                path: '/system/system-monitor',
                name: 'system-monitor',
                component: SystemMonitor,
                meta: {
                    title: '系统监控',
                    icon: 'system-monitor',
                    private: true,
                    roles: [1], // 仅 admin
                }
            },
            {
                path: '/system/ad-manage',
                name: 'ad-manage',
                component: () => import('../views/privateViews/AdManage.vue'),
                meta: {
                    title: '广告管理',
                    icon: 'ad-manage',
                    private: true,
                    roles: [1], // 仅 admin
                }
            },
            {
                path: '/system/announcement-manage',
                name: 'announcement-manage',
                component: () => import('../views/privateViews/AnnouncementManage.vue'),
                meta: {
                    title: '公告管理',
                    icon: 'announcement-manage',
                    private: true,
                    roles: [1], // 仅 admin
                }
            },
            {
                path: '/system/api-key-manage',
                name: 'api-key-manage',
                component: () => import('../views/privateViews/ApiKeyManage.vue'),
                meta: {
                    title: 'API Keys',
                    icon: 'api-key-manage',
                    private: true,
                    roles: [1, 2, 3], // 所有登录用户可管理自己的 key
                }
            },
            {
                path: '/system/oauth-manage',
                name: 'oauth-manage',
                component: () => import('@/views/privateViews/OAuthClientManage.vue'),
                meta: {
                    title: 'OAuth 应用',
                    icon: 'oauth-manage',
                    private: true,
                    roles: [1], // 仅 admin
                }
            }
        ]

    },
    {
        path: '/auth',
        name: 'auth',
        component: AuthView,
        meta: {
            title: 'auth',
            icon: 'auth',
            private: false,
        }
    },
    {
        path: '/forgot-password',
        name: 'forgot-password',
        component: ForgotPassword,
        meta: {
            title: '忘记密码',
            private: false,
        }
    },
    {
        path: '/:pathMatch(.*)*',
        name: 'NotFound',
        component: () => import('@/views/404.vue')
    }
]


const router = createRouter({
    history: createWebHistory(import.meta.env.BASE_URL),
    routes: [...Routes]
})

export default router