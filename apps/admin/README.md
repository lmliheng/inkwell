## 后续

 后续改成：后台管理接口，公共服务接口，特定前端服务
 接口带/system /admin /public /blog 等路由前缀

 定义基础查询接口

 -注册逻辑(amdin快速注册接口，普通用户注册接口)
 -后台面包屑，导航栏样式太丑了
 -应用设置(语言切换放应用设置里)
 -首页-权限改成聚类图


签到逻辑

## 调试

调试阶段，Token是根据ID生成的，系统重启不会影响token

#### 系统接口

/sys/login
```
curl -X POST http://localhost:7000/sys/login -H "Content-Type: application/json" -d "{\"username\":\"admin\",\"password\":\"13551458597a\"}"
```

/sys/profile
```
curl -X GET http://localhost:7000/sys/profile -H "Content-Type: application/json" -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpZCI6MSwicm9sZV9pZCI6MSwiaWF0IjoxNzg5NDc3OTUzLCJleHAiOjE3OTAwODI3NTN9.ldzNRBFsIHtqfax0tIuYprDxO6A1RoseRs7Q6NwOa3I"
```

/user-manage/list
param:{ keyword: 'ad', page: 1, pageSize: 20 }
```
curl -X GET "http://localhost:7000/user-manage/list?keyword=ad&page=1&pageSize=20" -H "Content-Type: application/json" -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpZCI6MSwicm9sZV9pZCI6MSwiaWF0IjoxNzg5NDc3OTUzLCJleHAiOjE3OTAwODI3NTN9.ldzNRBFsIHtqfax0tIuYprDxO6A1RoseRs7Q6NwOa3I"
```


/ad/admin/list
```
curl -X GET http://localhost:7000/ad/admin/list -H "Content-Type: application/json" -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpZCI6MSwicm9sZV9pZCI6MSwiaWF0IjoxNzg5NDc3OTUzLCJleHAiOjE3OTAwODI3NTN9.ldzNRBFsIHtqfax0tIuYprDxO6A1RoseRs7Q6NwOa3I"
```

/ad/admin/detail/{id}
```
curl -X GET http://localhost:7000/ad/admin/detail/3 -H "Content-Type: application/json" -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpZCI6MSwicm9sZV9pZCI6MSwiaWF0IjoxNzg5NDc3OTUzLCJleHAiOjE3OTAwODI3NTN9.ldzNRBFsIHtqfax0tIuYprDxO6A1RoseRs7Q6NwOa3I"
```

/announcement/admin/list
```
curl -X GET http://localhost:7000/announcement/admin/list -H "Content-Type: application/json" -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpZCI6MSwicm9sZV9pZCI6MSwiaWF0IjoxNzg5NDc3OTUzLCJleHAiOjE3OTAwODI3NTN9.ldzNRBFsIHtqfax0tIuYprDxO6A1RoseRs7Q6NwOa3I"
```


/user-manage/detail/${id}
```
curl -X GET http://localhost:7000/user-manage/detail/1 -H "Content-Type: application/json" -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpZCI6MSwicm9sZV9pZCI6MSwiaWF0IjoxNzg5NDc3OTUzLCJleHAiOjE3OTAwODI3NTN9.ldzNRBFsIHtqfax0tIuYprDxO6A1RoseRs7Q6NwOa3I"
```

/role/list

```
curl -X GET http://localhost:7000/role/list -H "Content-Type: application/json" -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpZCI6MSwicm9sZV9pZCI6MSwiaWF0IjoxNzg5NDc3OTUzLCJleHAiOjE3OTAwODI3NTN9.ldzNRBFsIHtqfax0tIuYprDxO6A1RoseRs7Q6NwOa3I"
```

/api-keys
```
curl -X GET http://localhost:7000/api-keys -H "Content-Type: application/json" -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpZCI6MSwicm9sZV9pZCI6MSwiaWF0IjoxNzg5NDc3OTUzLCJleHAiOjE3OTAwODI3NTN9.ldzNRBFsIHtqfax0tIuYprDxO6A1RoseRs7Q6NwOa3I"
```

/api-keys 创建
```
curl -X POST http://localhost:7000/api-keys -H "Content-Type: application/json" -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpZCI6MSwicm9sZV9pZCI6MSwiaWF0IjoxNzg5NDc3OTUzLCJleHAiOjE3OTAwODI3NTN9.ldzNRBFsIHtqfax0tIuYprDxO6A1RoseRs7Q6NwOa3I" -d "{\"name\":\"测试\",\"scopes\":\"read\"}"
```

/api-keys/${id}/status
```
curl -X PUT http://localhost:7000/api-keys/9/status -H "Content-Type: application/json" -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpZCI6MSwicm9sZV9pZCI6MSwiaWF0IjoxNzg5NDc3OTUzLCJleHAiOjE3OTAwODI3NTN9.ldzNRBFsIHtqfax0tIuYprDxO6A1RoseRs7Q6NwOa3I" -d "{\"status\":\"0\"}"
```

/api-keys/${id} 删除DELETE
```
curl -X DELETE http://localhost:7000/api-keys/9 -H "Content-Type: application/json" -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpZCI6MSwicm9sZV9pZCI6MSwiaWF0IjoxNzg5NDc3OTUzLCJleHAiOjE3OTAwODI3NTN9.ldzNRBFsIHtqfax0tIuYprDxO6A1RoseRs7Q6NwOa3I"
```


/oauth/admin/clients
```
curl -X GET http://localhost:7000/oauth/admin/clients -H "Content-Type: application/json" -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpZCI6MSwicm9sZV9pZCI6MSwiaWF0IjoxNzg5NDc3OTUzLCJleHAiOjE3OTAwODI3NTN9.ldzNRBFsIHtqfax0tIuYprDxO6A1RoseRs7Q6NwOa3I"
```

/article/list
```
curl -X GET http://localhost:7000/article/list -H "Content-Type: application/json" -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpZCI6MSwicm9sZV9pZCI6MSwiaWF0IjoxNzg5NDc3OTUzLCJleHAiOjE3OTAwODI3NTN9.ldzNRBFsIHtqfax0tIuYprDxO6A1RoseRs7Q6NwOa3I"
```

/comment/manage/list
```
curl -X GET http://localhost:7000/comment/manage/list -H "Content-Type: application/json" -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpZCI6MSwicm9sZV9pZCI6MSwiaWF0IjoxNzg5NDc3OTUzLCJleHAiOjE3OTAwODI3NTN9.ldzNRBFsIHtqfax0tIuYprDxO6A1RoseRs7Q6NwOa3I"
```


#### 博客服务接口

/blog/profile/${username} 接口需要限速
```
curl -X GET "http://localhost:7000/blog/profile/lmliheng" -H "Content-Type: application/json"
```

/article/mine
```
curl -X GET "http://localhost:7000/article/mine" -H "Content-Type: application/json" -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpZCI6MSwicm9sZV9pZCI6MSwiaWF0IjoxNzg5NDc3OTUzLCJleHAiOjE3OTAwODI3NTN9.ldzNRBFsIHtqfax0tIuYprDxO6A1RoseRs7Q6NwOa3I"
```

/article/category/list
```
curl -X GET "http://localhost:7000/article/category/list" -H "Content-Type: application/json"
```