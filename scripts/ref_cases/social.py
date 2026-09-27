"""social 域（M3）的对照用例：/social/* 15 个 + /dm/* 4 个 + /notification/* 6 个 = 25 个接口。

覆盖：正常路径 + 无 token(401) / 非管理员(403) / 缺参(400) / 不存在的 id 或用户名(404) /
原版自身的怪癖（NaN 进 SQL → 500）、toggle 的重复分支、分页参数的回落。

跑之前请先让两个对照库处于同一份 dump 状态（两库都由 deploy/mysql/init/01-schema.sql 建成），
用例里有三处用了「确定的自增 id」：DELETE /social/admin/favorites/5、/social/notifications/read {id:6}、
/notification/update|delete {notification_id:6}。未复位时这些 id 可能落空，此时两侧都只是空操作，
对照仍然一致，但会留下几行（下面列了清单）。

写用例的顺序与善后：
  * 列表/只读用例全部排在写用例之前，看的是种子数据（follow 0 行、article_like 3 行、
    article_favorite 1 行、message 3 行、notification 2 行、user_notification 0 行）；
  * 关注：admin 关注 user1 → 读回 user1 的互动通知/未读数 → 标记已读 → admin 取关（follow 行自清）；
  * 点赞：先 like 再 unlike（article_like 行自清），前导零的 08 也走一遍；
  * 收藏：收藏 9 → 用后台 DELETE 接口真删一行 → 再收藏并取消（article_favorite 行自清）；
  * 广播通知：add 出来的行最后用 DELETE /notification/delete 删掉，标题带 STAMP 便于人工核对；
  * 原版没有「删互动通知」的接口：关注/点赞/收藏产生的 user_notification 行、
    /notification/read 插的 notification_read 行会留在库里，跑完由 SQL 复位（见报告）。

几个刻意的 ignore：
  * favorited_at / follow_time：写用例刚插进去的行，值取 NOW()，两次请求（ref 先、java 后）
    可能跨秒 → 只跳过时间，其余字段照比；
  * body.data.notification_id（/notification/add）：自增主键，两库靠同样的插入序列保持一致，
    但真删时用的是确定值，这里跳过值比对；
  * body.data.list[0].id（新建行出现在后台列表里）：同上，自增主键。
"""

from _spec import STAMP, case

ADMIN_ID = 1
USER1_ID = 3
LMLIHENG_ID = 1778237622056           # 种子文章 8/9/15 的作者
ARTICLE_UNLIKED = 8                   # 无人点赞的种子文章（作者 lmliheng）
ARTICLE_UNFAVORITED = 9               # 无人收藏的种子文章（作者 lmliheng）
GONE_USER = f"m3-nouser-{STAMP}"
NOTIF_TITLE = f"m3-social-{STAMP}"
# 清理过 user_notification 后，下一个自增 id 是 6（两库一致，见报告里的 SQL 复位）
NOTIF_ROW_ID = 6
# 广播通知的自增：种子 notification_id 是 2、5，下一个是 6
BROADCAST_ID = 6

CASES = [
    # ================= 公开：关注/粉丝/统计 =================
    case("following 公开可读（空列表）", "GET", "/social/following/lmliheng"),
    case("following 用户不存在 → 404", "GET", f"/social/following/{GONE_USER}"),
    case("following 用户名大小写不敏感", "GET", "/social/following/ADMIN"),
    case("followers 公开可读（空列表）", "GET", "/social/followers/user1"),
    case("followers 用户不存在 → 404", "GET", f"/social/followers/{GONE_USER}"),
    case("stats 公开可读（无 token）", "GET", "/social/stats/lmliheng"),
    case("stats 带 token（未关注 isFollowing=false）", "GET", "/social/stats/lmliheng", auth="user"),
    case("stats 本人（liked 计入种子点赞）", "GET", "/social/stats/admin", auth="admin"),
    case("stats 用户不存在 → 404", "GET", f"/social/stats/{GONE_USER}", auth="user"),

    # ================= 需登录的只读 =================
    case("my-favorites 无 token → 401", "GET", "/social/my-favorites"),
    case("my-favorites 空列表", "GET", "/social/my-favorites", auth="user"),
    case("my-favorites 种子行（含 status / 分类数组）", "GET", "/social/my-favorites", auth="admin"),

    case("notifications 无 token → 401", "GET", "/social/notifications"),
    case("notifications 空列表与默认分页", "GET", "/social/notifications", auth="user"),
    case("notifications 分页参数非法回落 1/20", "GET",
         "/social/notifications?page=abc&pageSize=0", auth="user"),
    case("notifications page=-1 回落第 1 页", "GET", "/social/notifications?page=-1", auth="user"),
    case("notifications/unread-count 无 token → 401", "GET", "/social/notifications/unread-count"),
    case("notifications/unread-count 无数据 → 0", "GET", "/social/notifications/unread-count", auth="user"),

    case("status 无 token → 401", "GET", "/social/status"),
    case("status 不带 ids → 空数组", "GET", "/social/status", auth="user"),
    case("status ids 为空串 → 空数组", "GET", "/social/status?ids=", auth="user"),
    case("status ids 非数字被过滤 → 空数组", "GET", "/social/status?ids=abc,xyz", auth="user"),
    case("status 命中种子的点赞与收藏", "GET", "/social/status?ids=33,43", auth="admin"),
    case("status 重复 ids 参数（Express 数组分支）", "GET", "/social/status?ids=33&ids=43", auth="admin"),
    case("status 别的用户 → 空数组", "GET", "/social/status?ids=33", auth="user"),

    case("admin/likes 无 token → 401", "GET", "/social/admin/likes"),
    case("admin/likes 非管理员 → 403", "GET", "/social/admin/likes", auth="user"),
    case("admin/likes 种子列表", "GET", "/social/admin/likes", auth="admin"),
    case("admin/likes 第 2 页", "GET", "/social/admin/likes?page=2&pageSize=2", auth="admin"),
    case("admin/likes 关键词命中", "GET", "/social/admin/likes?keyword=LangChain", auth="admin"),
    case("admin/likes 关键词无命中", "GET", "/social/admin/likes?keyword=zzz-nope", auth="admin"),
    case("admin/likes 分页参数非法回落 1/10", "GET",
         "/social/admin/likes?page=abc&pageSize=abc", auth="admin"),
    case("admin/likes 重复 keyword（拼成 a,b 后无命中）", "GET",
         "/social/admin/likes?keyword=LangChain&keyword=%E5%BF%AB%E9%80%9F%E5%85%A5%E9%97%A8", auth="admin"),

    case("admin/favorites 无 token → 401", "GET", "/social/admin/favorites"),
    case("admin/favorites 非管理员 → 403", "GET", "/social/admin/favorites", auth="user"),
    case("admin/favorites 种子列表", "GET", "/social/admin/favorites", auth="admin"),
    case("admin/favorites 关键词命中", "GET",
         "/social/admin/favorites?keyword=%E5%BF%AB%E9%80%9F%E5%85%A5%E9%97%A8", auth="admin"),
    case("admin/favorites 关键词无命中", "GET", "/social/admin/favorites?keyword=zzz-nope", auth="admin"),

    case("admin/likes 删除 无 token → 401", "DELETE", "/social/admin/likes/999999"),
    case("admin/likes 删除 非管理员 → 403", "DELETE", "/social/admin/likes/999999", auth="user"),
    case("admin/likes 删除 不存在的 id → 200", "DELETE", "/social/admin/likes/999999", auth="admin"),
    case("admin/likes 删除 非数字 id → 200", "DELETE", "/social/admin/likes/abc", auth="admin"),
    case("admin/favorites 删除 不存在的 id → 200", "DELETE", "/social/admin/favorites/999999", auth="admin"),
    case("admin/favorites 删除 非管理员 → 403", "DELETE", "/social/admin/favorites/999999", auth="user"),

    # ================= dm =================
    case("dm/conversations 无 token → 401", "GET", "/dm/conversations"),
    case("dm/conversations 无会话 → 空", "GET", "/dm/conversations", auth="user"),
    case("dm/conversations 种子会话（含未读数）", "GET", "/dm/conversations", auth="admin"),
    case("dm/messages 无 token → 401", "GET", "/dm/messages/2"),
    case("dm/messages 与 editor 的两条（时间正序）", "GET", "/dm/messages/2", auth="admin"),
    case("dm/messages pageSize=1 第 1 页（最新一条）", "GET",
         "/dm/messages/2?page=1&pageSize=1", auth="admin"),
    case("dm/messages pageSize=1 第 2 页", "GET", "/dm/messages/2?page=2&pageSize=1", auth="admin"),
    case("dm/messages 与 lmliheng 的一条", "GET", f"/dm/messages/{LMLIHENG_ID}", auth="admin"),
    case("dm/messages 不存在的用户 → 空", "GET", "/dm/messages/999999999", auth="admin"),
    case("dm/messages 非数字 otherId → 500（NaN 进 SQL）", "GET", "/dm/messages/abc", auth="admin"),
    case("dm/messages 负 pageSize → 500（LIMIT 为负）", "GET",
         "/dm/messages/2?page=0&pageSize=-5", auth="admin"),
    case("dm/unread-count 无 token → 401", "GET", "/dm/unread-count"),
    case("dm/unread-count 无未读 → 0", "GET", "/dm/unread-count", auth="admin"),

    # ================= notification（平台广播通知） =================
    case("notification/list 无 token → 401", "GET", "/notification/list"),
    case("notification/list 管理员（种子两条均读过）", "GET", "/notification/list", auth="admin"),
    case("notification/list 普通用户（种子两条未读）", "GET", "/notification/list", auth="user"),
    case("notification/unread-count 无 token → 401", "GET", "/notification/unread-count"),
    case("notification/unread-count 管理员 → 0", "GET", "/notification/unread-count", auth="admin"),
    case("notification/unread-count 普通用户 → 2", "GET", "/notification/unread-count", auth="user"),
    case("notification/update 无 token → 401", "PUT", "/notification/update", {"notification_id": 2}),
    case("notification/update 非管理员 → 403", "PUT", "/notification/update",
         {"notification_id": 2, "title": "m3-nope"}, auth="user"),
    case("notification/delete 无 token → 401", "DELETE", "/notification/delete", {"notification_id": 2}),
    case("notification/delete 非管理员 → 403", "DELETE", "/notification/delete",
         {"notification_id": 2}, auth="user"),
    case("notification/add 非管理员 → 403", "POST", "/notification/add",
         {"title": "m3-nope", "content": "x", "target_type": "all"}, auth="user"),

    # ================= 写：关注 =================
    case("follow 无 token → 401", "POST", "/social/follow/lmliheng"),
    case("follow 用户不存在 → 404", "POST", f"/social/follow/{GONE_USER}", auth="user"),
    case("follow 自己 → 400", "POST", "/social/follow/user1", auth="user"),
    case("follow 成功（admin 关注 user1）", "POST", "/social/follow/user1", auth="admin"),
    case("followers 读回新粉丝", "GET", "/social/followers/user1",
         ignore=("follow_time",)),
    case("following 读回新关注", "GET", "/social/following/admin", ignore=("follow_time",)),
    case("stats 读回关注关系", "GET", "/social/stats/user1", auth="admin"),
    case("notifications 读回关注通知", "GET", "/social/notifications", auth="user"),
    case("notifications/unread-count 读回 1", "GET",
         "/social/notifications/unread-count", auth="user"),
    case("notifications/read 缺参数 → 400", "POST", "/social/notifications/read", {}, auth="user"),
    case("notifications/read id 非数字 → 500", "POST", "/social/notifications/read",
         {"id": "abc"}, auth="user"),
    case("notifications/read 指定 id", "POST", "/social/notifications/read",
         {"id": NOTIF_ROW_ID}, auth="user"),
    case("notifications/unread-count 读回 0", "GET",
         "/social/notifications/unread-count", auth="user"),
    case("notifications/read all=true（幂等）", "POST", "/social/notifications/read",
         {"all": True}, auth="user"),
    case("follow 取关（admin 取关 user1）", "POST", "/social/follow/user1", auth="admin"),
    case("followers 读回空列表", "GET", "/social/followers/user1"),
    case("stats 读回无关注关系", "GET", "/social/stats/user1", auth="admin"),

    # ================= 写：点赞 =================
    case("like 文章不存在 → 404", "POST", "/social/like/999999", auth="user"),
    case("like 非数字文章 id → 404", "POST", "/social/like/abc", auth="user"),
    case("like 无 token → 401", "POST", f"/social/like/{ARTICLE_UNLIKED}"),
    case("status 点赞前为空", "GET", f"/social/status?ids={ARTICLE_UNLIKED}", auth="user"),
    case("like 成功", "POST", f"/social/like/{ARTICLE_UNLIKED}", auth="user"),
    case("status 读回点赞", "GET", f"/social/status?ids={ARTICLE_UNLIKED}", auth="user"),
    case("admin/likes 读回新点赞行", "GET",
         "/social/admin/likes?keyword=%E5%AE%89%E8%A3%85", auth="admin",
         ignore=("body.data.list[0].id",)),
    case("like 重复操作 → 取消点赞", "POST", f"/social/like/{ARTICLE_UNLIKED}", auth="user"),
    case("status 读回取消点赞", "GET", f"/social/status?ids={ARTICLE_UNLIKED}", auth="user"),
    # 前导零：SQL 里按 08 匹配到文章 8，通知内容用的是路径原串
    case("like 前导零的文章 id", "POST", "/social/like/08", auth="admin"),
    case("status 读回前导零点赞", "GET", "/social/status?ids=08", auth="admin"),
    case("like 前导零取消", "POST", "/social/like/08", auth="admin"),
    case("admin/likes 回到种子列表", "GET", "/social/admin/likes", auth="admin"),

    # ================= 写：收藏 =================
    case("favorite 文章不存在 → 404", "POST", "/social/favorite/999999", auth="user"),
    case("favorite 无 token → 401", "POST", f"/social/favorite/{ARTICLE_UNFAVORITED}"),
    case("favorite 成功", "POST", f"/social/favorite/{ARTICLE_UNFAVORITED}", auth="user"),
    case("my-favorites 读回新收藏", "GET", "/social/my-favorites", auth="user",
         ignore=("favorited_at",)),
    case("admin/favorites 读回新收藏行", "GET",
         "/social/admin/favorites?keyword=%E5%BF%AB%E9%80%9F%E5%85%A5%E9%97%A8", auth="admin",
         ignore=("body.data.list[0].id",)),
    # 用后台删除接口真删一行（自增 id 两库一致：清理后 article_favorite 的下一个是 5）
    case("admin/favorites 删除新建行", "DELETE", "/social/admin/favorites/5", auth="admin"),
    case("status 读回删除后的收藏", "GET", f"/social/status?ids={ARTICLE_UNFAVORITED}", auth="user"),
    case("favorite 再收藏一次", "POST", f"/social/favorite/{ARTICLE_UNFAVORITED}", auth="user"),
    case("favorite 重复操作 → 取消收藏", "POST", f"/social/favorite/{ARTICLE_UNFAVORITED}", auth="user"),
    # 这一步在「跑前先复位两库」的前提下是空列表；若没复位（自增值漂移、后台删除那一行
    # 没命中），两边会同样留下一行，这里容错地只跳过它刚写入的 favorited_at。
    case("my-favorites 读回已清空", "GET", "/social/my-favorites", auth="user",
         ignore=("favorited_at",)),
    case("admin/favorites 回到种子列表", "GET", "/social/admin/favorites", auth="admin"),

    # ================= 写：平台广播通知 =================
    case("notification/add 缺标题内容 → 400", "POST", "/notification/add", {}, auth="admin"),
    case("notification/add 只有标题 → 400", "POST", "/notification/add",
         {"title": NOTIF_TITLE}, auth="admin"),
    case("notification/add target_type 非法 → 400", "POST", "/notification/add",
         {"title": NOTIF_TITLE, "content": "x"}, auth="admin"),
    case("notification/add target_type=user 缺 target_id → 400", "POST", "/notification/add",
         {"title": NOTIF_TITLE, "content": "x", "target_type": "user"}, auth="admin"),
    # 成功这条必须是本次运行里对 notification 的第一次插入：复位后自增值是 6，所以它就是 6，
    # 后面 update/delete 才敢用确定的 id。
    case("notification/add 成功（type/importance 非法回落默认）", "POST", "/notification/add",
         {"title": NOTIF_TITLE, "content": "m3 内容", "target_type": "user",
          "target_id": USER1_ID, "type": "bogus", "importance": "huge"},
         auth="admin", ignore=("body.data.notification_id",)),
    # notification.target_id 是 int，lmliheng 的 id（1778237622056）超范围 → 原版直接 500
    # （失败的自增插入仍会吃掉一个 id，所以这条要排在成功那条之后）
    case("notification/add target_id 超出 int 范围 → 500", "POST", "/notification/add",
         {"title": NOTIF_TITLE, "content": "m3 内容", "target_type": "user",
          "target_id": LMLIHENG_ID}, auth="admin"),
    case("notification/list 定向通知对目标用户可见", "GET", "/notification/list", auth="user"),
    case("notification/unread-count 目标用户 +1", "GET", "/notification/unread-count", auth="user"),
    case("notification/update 缺 notification_id → 400", "PUT", "/notification/update", {}, auth="admin"),
    case("notification/update 无可更新字段 → 400", "PUT", "/notification/update",
         {"notification_id": BROADCAST_ID}, auth="admin"),
    case("notification/update 不存在的 id → 200", "PUT", "/notification/update",
         {"notification_id": 999999, "title": "m3-ghost"}, auth="admin"),
    case("notification/update 成功", "PUT", "/notification/update",
         {"notification_id": BROADCAST_ID, "title": NOTIF_TITLE + "-2", "content": "m3 内容2",
          "type": "system", "importance": "high", "target_id": LMLIHENG_ID}, auth="admin"),
    case("notification/update title 为 null → 500（NOT NULL 约束）", "PUT", "/notification/update",
         {"notification_id": BROADCAST_ID, "title": None}, auth="admin"),
    case("notification/delete 缺 notification_id → 400", "DELETE", "/notification/delete", {}, auth="admin"),
    case("notification/delete 不存在的 id → 200", "DELETE", "/notification/delete",
         {"notification_id": 999999}, auth="admin"),
    case("notification/list 新通知只发给目标用户", "GET", "/notification/list", auth="admin"),
    case("notification/read 缺 notification_id → 400", "POST", "/notification/read", {}, auth="admin"),
    case("notification/read 种子通知（幂等，不新增行）", "POST", "/notification/read",
         {"notification_id": 5}, auth="admin"),
    case("notification/read 不存在的通知 id", "POST", "/notification/read",
         {"notification_id": 999999}, auth="admin"),
    case("notification/delete 清掉新建的广播通知", "DELETE", "/notification/delete",
         {"notification_id": BROADCAST_ID}, auth="admin"),
    case("notification/list 回到种子两条", "GET", "/notification/list", auth="admin"),
    case("notification/list 目标用户也回到两条", "GET", "/notification/list", auth="user"),
    case("notification/unread-count 目标用户回到 2", "GET", "/notification/unread-count", auth="user"),

    # ================= 写：私信已读 =================
    case("dm/read 无 token → 401", "POST", "/dm/read", {"other_id": 2}),
    case("dm/read 缺 other_id → 400", "POST", "/dm/read", {}, auth="admin"),
    case("dm/read other_id=0 → 400", "POST", "/dm/read", {"other_id": 0}, auth="admin"),
    case("dm/read other_id 非数字 → 500", "POST", "/dm/read", {"other_id": "abc"}, auth="admin"),
    case("dm/read 数字 other_id", "POST", "/dm/read", {"other_id": 2}, auth="admin"),
    case("dm/read 字符串 other_id", "POST", "/dm/read", {"other_id": "2"}, auth="admin"),
    case("dm/unread-count 仍为 0", "GET", "/dm/unread-count", auth="admin"),
]
