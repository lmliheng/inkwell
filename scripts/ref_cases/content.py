"""content 模块（M2：article / blog / comment / ad / announcement / upload）的对照用例。

覆盖 37 个接口的正常路径与边界分支（无 token / 非管理员 / 缺参 / 不存在的 id / 非法分页 /
非法数字（原版会拼出 NaN 让 SQL 报错 → 500）/ 越权 / 空串参数），全部走 HTTP，写用例自带善后。

运行前提（很重要）：
  * 两边的库都要**刚从 dump 重建**：`scripts/ref_env.sh db fastweb_m2ref` 与 `scripts/ref_env.sh db fastweb_m2dev`。
    新建资源（文章 / 评论 / 分类 / 广告 / 公告）的自增 id 是写死的：文章 86 / 87 / 88、评论 22 / 24、
    分类 12（11 被「category_name: 0」那条占掉，用完即删）、广告 9（8 被「title: 0」那条占掉，用完即删）、
    公告 3，这正是 dump 里这几张表的 AUTO_INCREMENT；库如果不是干净的，后续「按 id 读回」的用例会双双 404。
  * 建完库要 ANALYZE 一次（两个库都要）：
      ANALYZE TABLE user, article, article_and_category 相关表, comment, ad, announcement;
    否则 dev 库的 InnoDB 统计信息是空的，优化器会给 user 表估 1 行、换一个 JOIN 顺序，
    blog/feed、blog/hot 这类「按 created_at 排序 + longtext 内容」的查询会直接
    `ERROR 1038 Out of sort memory`，而 ref 库（被写操作触发过统计重算）不会 —— 那是环境差异，不是实现差异。
  * 列表类用例全部排在写操作之前；写操作按「文章 → 分类 → 评论 → 广告 → 公告」顺序，各自用完即删。
  * 顺序敏感的列表用 key=("body.data.list", "<主键>") 配对比较（新建行的 created_at 只精确到秒，
    同秒插入的两行在 ORDER BY created_at DESC 下先后可能不同）。
  * ORDER BY created_at / comment_count 都没有 tiebreaker，而种子数据里有大并列组（37 篇已发布
    文章的 created_at 全是 2026-08-25 07:13:17；评论数 0 的文章成堆）。凡是 LIMIT/OFFSET 切进
    并列组的位置，两侧各自取并列组里的任意行，连「集合」都不一致 —— 这是原版 SQL 自身的非确定性，
    不是实现差异。这种位置用 ignore=（只保证行数一致），并列组内「次序不同但集合相同」的用 key=；
    每个被动过手脚的接口都另配一条并列组之外、次序完全确定的用例（如 /blog/feed?limit=4、
    /blog/articles/admin?keyword=M）来覆盖排序语义本身。

已知的两处「原版怪癖」（用例里已标注，Java 侧照抄）：
  * POST /ad/click/:id（源码是 POST，不是 GET）。
  * 非法数字参数（article/list 的 status、update 的 status、comment 的 article_id / comment_id）原版
    会把 NaN 直接拼进 SQL 导致 500，而不是 400。
"""

from _spec import STAMP, case

# ---- 种子固定装置（dump 里的数据）----
ADMIN_DRAFT = 1             # user=1(status=0)：草稿，只有作者/admin 可见
PUBLISHED_LANGCHAIN = 7     # user=1778237622056(status=1)：已发布
PUBLISHED_VIP = 83          # user=1(status=1)，带 ai_summary(JSON)
COMMENT_ARTICLE = 56        # 已有评论 20（匿名）+ 21（admin 回复）
ORPHAN_COMMENT_ARTICLE = 5  # 文章已被删（只剩评论 7），作者为 null
CATEGORY_ADMIN = 1          # 分类「技术博客」，user=1
CATEGORY_USER1 = 4          # 分类「学习笔记」，user=3(user1)
CATEGORY_EDITOR = 3         # 分类「项目案例」，user=2(editor)
AD_ARTICLE_TOP = 4          # 广告 id=4 position=article_top
AD_ARTICLE_BOTTOM = 5       # 广告 id=5 position=article_bottom sort_order=0
AUTH_HEADER_MISSING_ID = 999999999

# ---- 本用例新建、并且写死 id 的资源 ----
NEW_ARTICLE = 86            # 第一篇文章：user1 发布，带分类 [1, 2]
NEW_ARTICLE_DRAFT = 87      # 第二篇：user1，status=0 草稿，带分类 [3]（0 与 'abc' 会被丢掉）
NEW_COMMENT_HOST = 88       # 第三篇：承载评论流程
NEW_COMMENT = 22            # 第一条评论（匿名）
NEW_COMMENT_REPLY = 24      # 第一条评论的楼中楼回复
NEW_CATEGORY_ZERO = 11      # 「category_name: 0」那条建的分类（原版 String(0)='0' 校验放行，真的建行）
NEW_CATEGORY = 12           # 新建的分类（id 11 被上面那条占掉）
NEW_AD_ZERO = 8             # 「title: 0」那条建的广告（同上，原版真的建行，用完即删）
NEW_AD = 9                  # 新建的广告（id 8 被上面那条占掉）
NEW_ANNOUNCEMENT = 3        # 新建的公告

LONG_CONTENT = "啊" * 501   # > 500 字 → 400

TAG = f"m2-{STAMP}"

CASES = [
    # ==================== 读：article ====================
    case("article/list 默认（已发布第一页）", "GET", "/article/list"),
    case("article/list 第二页 pageSize=3", "GET", "/article/list?page=2&pageSize=3"),
    case("article/list 关键词命中", "GET", "/article/list?keyword=LangChain&pageSize=2"),
    case("article/list 分类过滤", "GET", "/article/list?category_id=1&pageSize=3"),
    case("article/list 作者过滤", "GET", "/article/list?author=admin&pageSize=3"),
    case("article/list 作者无匹配 → 空列表", "GET", "/article/list?author=nobody-xyz"),
    case("article/list status=all（含草稿）", "GET", "/article/list?status=all&pageSize=3"),
    case("article/list status=0（只看草稿）", "GET", "/article/list?status=0&pageSize=3"),
    case("article/list status 空串（等同不传）", "GET", "/article/list?status=&pageSize=2"),
    case("article/list 分页非法回落默认", "GET", "/article/list?page=abc&pageSize=abc"),
    case("article/list page=-1 pageSize=0 回落", "GET", "/article/list?page=-1&pageSize=0&pageSize=3"),
    case("article/list status=abc → 500（原版拼出 NaN）", "GET", "/article/list?status=abc"),
    case("article/list category_id=abc → MySQL 按 0 处理", "GET", "/article/list?category_id=abc"),

    case("article/archive 全部", "GET", "/article/archive?username=admin"),
    case("article/archive 全站（不带 username）", "GET", "/article/archive"),
    case("article/archive username 无匹配 → 空", "GET", "/article/archive?username=nobody-xyz"),
    case("article/archive username 空串 → 不过滤", "GET", "/article/archive?username="),

    case("article/category/list", "GET", "/article/category/list"),

    case("article/detail 已发布", "GET", f"/article/detail/{PUBLISHED_LANGCHAIN}"),
    case("article/detail 带 ai_summary(JSON 列)", "GET", f"/article/detail/{PUBLISHED_VIP}"),
    case("article/detail 草稿未登录 → 404", "GET", f"/article/detail/{ADMIN_DRAFT}"),
    case("article/detail 草稿+作者(admin) → 200", "GET", f"/article/detail/{ADMIN_DRAFT}", auth="admin"),
    case("article/detail 草稿+他人(user1) → 404", "GET", f"/article/detail/{ADMIN_DRAFT}", auth="user"),
    case("article/detail 不存在 → 404", "GET", f"/article/detail/{AUTH_HEADER_MISSING_ID}"),
    case("article/detail 非数字 → 400", "GET", "/article/detail/abc"),
    case("article/detail id=0 → 400", "GET", "/article/detail/0"),

    case("article/mine 无 token → 401", "GET", "/article/mine"),
    case("article/mine user1（暂无文章）", "GET", "/article/mine", auth="user"),
    case("article/mine admin", "GET", "/article/mine", auth="admin"),
    case("article/mine 分页非法回落", "GET", "/article/mine?page=abc&pageSize=abc", auth="admin"),
    case("article/mine page=0 回落第 1 页", "GET", "/article/mine?page=0", auth="admin"),

    # ==================== 读：blog ====================
    case("blog/users 默认", "GET", "/blog/users"),
    case("blog/users pageSize=2", "GET", "/blog/users?page=1&pageSize=2"),
    case("blog/users 分页非法回落", "GET", "/blog/users?page=abc&pageSize=abc"),
    case("blog/users 越界页 → 空列表", "GET", "/blog/users?page=99&pageSize=2"),
    # ORDER BY a.created_at DESC 没有 tiebreaker：种子里有 37 篇已发布文章的 created_at 完全相同
    # （2026-08-25 07:13:17）。LIMIT 一旦切进这个并列组，两侧取到的是并列组里的任意两行 —— 这是
    # 原版 SQL 自身的非确定性（同一份数据、同一条 SQL 在两库上都会各自挑行），不是实现差异，
    # 所以这几条用 ignore 跳过并列组内的位置（行数仍要比对）；确定性部分由「limit=4」（4 行
    # created_at 互不相同）完整比对，两边都还得是 DESC。
    case("blog/feed 默认（6 条，末 2 行落在 37 行并列组内）", "GET", "/blog/feed",
         ignore=("body.data.list[4]", "body.data.list[5]")),
    case("blog/feed limit=4（并列组之前，次序完全确定）", "GET", "/blog/feed?limit=4"),
    case("blog/feed limit=3", "GET", "/blog/feed?limit=3"),
    case("blog/feed limit=0 回落 6", "GET", "/blog/feed?limit=0",
         ignore=("body.data.list[4]", "body.data.list[5]")),
    case("blog/feed limit=abc 回落 6", "GET", "/blog/feed?limit=abc",
         ignore=("body.data.list[4]", "body.data.list[5]")),
    # blog/hot 按 comment_count DESC：只有榜首（56，count=2）唯一，第 2/3 名并列（69/33，count=1），
    # 第 4 名起是 count=0 的大并列组 —— 并列组内的先后与「切在组内的那几行取谁」都由 MySQL 定。
    case("blog/hot 默认（榜首确定，并列组内位置跳过）", "GET", "/blog/hot",
         ignore=("body.data.list[1]", "body.data.list[2]", "body.data.list[4]", "body.data.list[5]")),
    case("blog/hot limit=1（榜首唯一，次序确定）", "GET", "/blog/hot?limit=1"),
    case("blog/hot limit=3（集合确定，并列组内按主键配对）", "GET", "/blog/hot?limit=3",
         key=("body.data.list", "article_id")),
    case("blog/hot limit=2（第二名并列，只比榜首与行数）", "GET", "/blog/hot?limit=2",
         ignore=("body.data.list[1]",)),
    case("blog/profile admin", "GET", "/blog/profile/admin"),
    case("blog/profile user1", "GET", "/blog/profile/user1"),
    case("blog/profile 不存在 → 404", "GET", "/blog/profile/nobody-xyz"),
    case("blog/articles admin", "GET", "/blog/articles/admin?pageSize=3"),
    case("blog/articles 关键词", "GET", "/blog/articles/admin?keyword=LangChain"),
    case("blog/articles 分类", "GET", "/blog/articles/admin?category_id=1&pageSize=3"),
    # sort=asc 的第一页整页都在 37 行并列组内（并列组是 created_at 最早的一批），此时连「取到哪
    # 3 行」都不确定，只能比对分页回显；排序方向的语义由下面两条 keyword=M 的用例确定性地覆盖
    # （命中 70「MCP server 全解~长期更新」/71「MEX」两篇，两者 created_at 不同）。
    case("blog/articles sort=asc（整页落在并列组内，仅比对分页回显）", "GET",
         "/blog/articles/admin?sort=asc&pageSize=3", ignore=("body.data.list",)),
    case("blog/articles sort=asc 关键词（并列组外，次序确定）", "GET",
         "/blog/articles/admin?sort=asc&keyword=M&pageSize=2"),
    case("blog/articles sort=desc 关键词（并列组外，次序确定）", "GET",
         "/blog/articles/admin?sort=desc&keyword=M&pageSize=2"),
    case("blog/articles 分页非法回落", "GET", "/blog/articles/admin?page=abc&pageSize=abc",
         ignore=("body.data.list[4]", "body.data.list[5]", "body.data.list[6]",
                 "body.data.list[7]", "body.data.list[8]", "body.data.list[9]")),
    case("blog/articles 用户不存在 → 404", "GET", "/blog/articles/nobody-xyz"),

    # ==================== 读：comment ====================
    case("comment/list 楼中楼（文章 56）", "GET", f"/comment/list/{COMMENT_ARTICLE}"),
    case("comment/list 分页 pageSize=1", "GET", f"/comment/list/{COMMENT_ARTICLE}?page=1&pageSize=1"),
    case("comment/list 第二页", "GET", f"/comment/list/{COMMENT_ARTICLE}?page=2&pageSize=1"),
    case("comment/list 文章已删（孤儿评论，作者为 null）", "GET", f"/comment/list/{ORPHAN_COMMENT_ARTICLE}"),
    case("comment/list 文章不存在 → 空树", "GET", f"/comment/list/{AUTH_HEADER_MISSING_ID}"),
    case("comment/list 非数字 → 400", "GET", "/comment/list/abc"),
    case("comment/list 分页非法回落", "GET", f"/comment/list/{COMMENT_ARTICLE}?page=0&pageSize=abc"),

    case("comment/manage/list 无 token → 401", "GET", "/comment/manage/list"),
    case("comment/manage/list 非管理员 → 403", "GET", "/comment/manage/list", auth="user"),
    case("comment/manage/list admin", "GET", "/comment/manage/list?pageSize=2", auth="admin"),
    case("comment/manage/list 按文章过滤", "GET", f"/comment/manage/list?article_id={COMMENT_ARTICLE}", auth="admin"),
    case("comment/manage/list 关键词", "GET", "/comment/manage/list?keyword=你好", auth="admin"),
    case("comment/manage/list 分页非法回落", "GET", "/comment/manage/list?page=abc&pageSize=abc", auth="admin"),

    # ==================== 读：ad / announcement ====================
    case("ad/slots article_top", "GET", "/ad/slots?position=article_top"),
    case("ad/slots article_bottom", "GET", "/ad/slots?position=article_bottom"),
    case("ad/slots home_mid", "GET", "/ad/slots?position=home_mid"),
    case("ad/slots 缺 position → 400", "GET", "/ad/slots"),
    case("ad/slots 非法 position → 400", "GET", "/ad/slots?position=xxx"),

    case("ad/admin/list 无 token → 401", "GET", "/ad/admin/list"),
    case("ad/admin/list 非管理员 → 403", "GET", "/ad/admin/list", auth="user"),
    case("ad/admin/list admin", "GET", "/ad/admin/list", auth="admin"),
    case("ad/admin/list 关键词", "GET", "/ad/admin/list?keyword=广告", auth="admin"),
    case("ad/admin/list 按 position", "GET", "/ad/admin/list?position=home_mid", auth="admin"),
    case("ad/admin/list 分页非法回落", "GET", "/ad/admin/list?page=abc&pageSize=abc", auth="admin"),
    case("ad/admin/detail 种子广告", "GET", f"/ad/admin/detail/{AD_ARTICLE_TOP}", auth="admin"),
    case("ad/admin/detail 不存在 → 404", "GET", f"/ad/admin/detail/{AUTH_HEADER_MISSING_ID}", auth="admin"),
    case("ad/admin/detail 非数字 → 404", "GET", "/ad/admin/detail/abc", auth="admin"),

    case("announcement/latest", "GET", "/announcement/latest"),
    case("announcement/admin/list 无 token → 401", "GET", "/announcement/admin/list"),
    case("announcement/admin/list 非管理员 → 403", "GET", "/announcement/admin/list", auth="user"),
    case("announcement/admin/list admin", "GET", "/announcement/admin/list", auth="admin"),
    case("announcement/admin/list status=1", "GET", "/announcement/admin/list?status=1", auth="admin"),
    case("announcement/admin/list status=0", "GET", "/announcement/admin/list?status=0", auth="admin"),
    case("announcement/admin/list status=abc（不参与过滤）", "GET", "/announcement/admin/list?status=abc", auth="admin"),
    case("announcement/admin/list 关键词", "GET", "/announcement/admin/list?keyword=JScreator", auth="admin"),

    # ==================== 读：upload / ai-summary ====================
    case("upload/image 无 token → 401", "POST", "/upload/image"),
    case("upload/image 无 body（已登录，非 multipart）→ 400", "POST", "/upload/image", auth="user"),
    case("upload/image JSON body → 400 请选择图片文件", "POST", "/upload/image", {}, auth="user"),

    case("ai-summary/regenerate 无 token → 401", "POST", f"/article/ai-summary/regenerate/{PUBLISHED_LANGCHAIN}"),
    case("ai-summary/regenerate 非作者 → 403", "POST", f"/article/ai-summary/regenerate/{PUBLISHED_LANGCHAIN}", auth="user"),
    case("ai-summary/regenerate 不存在 → 404", "POST", f"/article/ai-summary/regenerate/{AUTH_HEADER_MISSING_ID}", auth="admin"),
    case("ai-summary/regenerate 非数字 → 400", "POST", "/article/ai-summary/regenerate/abc", auth="admin"),
    case("ai-summary/regenerate 本人（无 LLM key）→ 500", "POST", f"/article/ai-summary/regenerate/{ADMIN_DRAFT}", auth="admin"),

    # ==================== 写：article ====================
    case("article/add 无 token → 401", "POST", "/article/add", {"title": f"{TAG}-x", "content": "内容"}),
    case("article/add 缺 title → 400", "POST", "/article/add", {"content": "内容"}, auth="user"),
    case("article/add 缺 content → 400", "POST", "/article/add", {"title": f"{TAG}-x"}, auth="user"),
    case("article/add title=0（falsy）→ 400", "POST", "/article/add", {"title": 0, "content": "内容"}, auth="user"),
    case("article/add 成功（发布+分类）→ id 86", "POST", "/article/add",
         {"title": f"{TAG}-published", "content": "m2 content published", "category_ids": [1, 2]}, auth="user"),
    case("article/add 成功（草稿 status=0，分类含 0/非法值）→ id 87", "POST", "/article/add",
         {"title": f"{TAG}-draft", "content": "m2 content draft", "status": 0, "category_ids": [3, 0, "abc"]},
         auth="user"),

    case("article/detail 新建文章（作者）", "GET", f"/article/detail/{NEW_ARTICLE}", auth="user"),
    case("article/detail 新建草稿（作者）", "GET", f"/article/detail/{NEW_ARTICLE_DRAFT}", auth="user"),
    case("article/detail 新建草稿（未登录）→ 404", "GET", f"/article/detail/{NEW_ARTICLE_DRAFT}"),
    case("article/detail 新建草稿（admin）→ 200", "GET", f"/article/detail/{NEW_ARTICLE_DRAFT}", auth="admin"),
    case("article/mine 读到新建两篇", "GET", "/article/mine", auth="user",
         key=("body.data.list", "article_id")),
    case("article/list 关键词命中新建文章", "GET", f"/article/list?keyword={TAG}-published",
         key=("body.data.list", "article_id")),
    case("article/list 新建草稿不在默认列表里", "GET", f"/article/list?keyword={TAG}-draft"),
    case("article/archive user1 读到新建已发布", "GET", "/article/archive?username=user1"),
    case("blog/articles user1 读到新建已发布", "GET", "/blog/articles/user1?pageSize=5", key=("body.data.list", "article_id")),
    case("blog/feed 含新建文章", "GET", "/blog/feed?limit=3", key=("body.data.list", "article_id")),

    case("article/update 无 token → 401", "PUT", f"/article/update/{NEW_ARTICLE}", {"title": "x"}),
    case("article/update 非作者改他人文章 → 403", "PUT", f"/article/update/{ADMIN_DRAFT}", {"title": "x"}, auth="user"),
    case("article/update 不存在 → 404", "PUT", f"/article/update/{AUTH_HEADER_MISSING_ID}", {"title": "x"}, auth="admin"),
    case("article/update 非数字 id → 400", "PUT", "/article/update/abc", {"title": "x"}, auth="user"),
    case("article/update id=0 → 400", "PUT", "/article/update/0", {"title": "x"}, auth="user"),
    case("article/update status 非法 → 500（原版拼出 NaN）", "PUT", f"/article/update/{NEW_ARTICLE}",
         {"status": "abc"}, auth="user"),
    case("article/update 空 body → 200", "PUT", f"/article/update/{NEW_ARTICLE}", {}, auth="user"),
    case("article/update 改字段+分类 → 200", "PUT", f"/article/update/{NEW_ARTICLE}",
         {"title": f"{TAG}-published2", "content": "m2 content updated", "status": 1, "category_ids": [4]},
         auth="user"),
    case("article/detail 读回更新后的文章", "GET", f"/article/detail/{NEW_ARTICLE}", auth="user"),
    case("article/update 分类 id 不存在 → 500（外键）", "PUT", f"/article/update/{NEW_ARTICLE}",
         {"category_ids": [AUTH_HEADER_MISSING_ID]}, auth="user"),
    case("article/detail 读回（事务回滚，分类未变）", "GET", f"/article/detail/{NEW_ARTICLE}", auth="user"),
    case("article/update 清空分类 → 200", "PUT", f"/article/update/{NEW_ARTICLE}", {"category_ids": []}, auth="user"),
    case("article/detail 读回（分类为空）", "GET", f"/article/detail/{NEW_ARTICLE}", auth="user"),
    case("article/update 发布草稿（status=1）→ 200", "PUT", f"/article/update/{NEW_ARTICLE_DRAFT}", {"status": 1},
         auth="user"),
    case("article/list 读到刚发布的草稿", "GET", f"/article/list?keyword={TAG}-draft"),

    # ==================== 写：article/category ====================
    case("category/add 无 token → 401", "POST", "/article/category/add", {"category_name": f"{TAG}-cat"}),
    case("category/add 非管理员非编辑 → 403", "POST", "/article/category/add", {"category_name": f"{TAG}-cat"},
         auth="user"),
    case("category/add 缺名 → 400", "POST", "/article/category/add", {}, auth="admin"),
    # 与广告那条同理：category_name 传数字 0 时原版 String(0) = '0' 是 truthy，校验放行 → 200 且真的建了一行；
    # 所以它占掉自增 11（下面「成功」那条才是 12），这一行必须马上删掉，否则它会顶掉后面所有按 id 读回的用例。
    case("category/add 名为 0（String(0)='0'，falsy 不生效）→ 200 且建了 id 11", "POST", "/article/category/add",
         {"category_name": 0}, auth="admin"),
    case("category/delete 清掉上面那条（id 11）→ 200", "DELETE", "/article/category/delete",
         {"category_id": NEW_CATEGORY_ZERO}, auth="admin"),
    case("category/add 成功 → id 12", "POST", "/article/category/add", {"category_name": f"{TAG}-cat"}, auth="admin"),
    case("category/list 含新增分类", "GET", "/article/category/list"),
    case("category/update 无 token → 401", "PUT", "/article/category/update",
         {"category_id": NEW_CATEGORY, "category_name": "x"}),
    case("category/update 缺 id → 400", "PUT", "/article/category/update", {"category_name": "x"}, auth="admin"),
    case("category/update 缺名 → 400", "PUT", "/article/category/update", {"category_id": NEW_CATEGORY}, auth="admin"),
    case("category/update 不存在 → 404", "PUT", "/article/category/update",
         {"category_id": AUTH_HEADER_MISSING_ID, "category_name": "x"}, auth="admin"),
    case("category/update 非本人非管理员 → 403", "PUT", "/article/category/update",
         {"category_id": CATEGORY_ADMIN, "category_name": "x"}, auth="user"),
    case("category/update 本人改自己的分类 → 200", "PUT", "/article/category/update",
         {"category_id": CATEGORY_USER1, "category_name": f"{TAG}-renamed"}, auth="user"),
    case("category/list 读回改名", "GET", "/article/category/list"),
    case("category/update 管理员改任意分类 → 200", "PUT", "/article/category/update",
         {"category_id": NEW_CATEGORY, "category_name": f"{TAG}-cat2"}, auth="admin"),
    case("category/update 还原 user1 的分类名", "PUT", "/article/category/update",
         {"category_id": CATEGORY_USER1, "category_name": "学习笔记"}, auth="user"),
    case("category/delete 无 token → 401", "DELETE", "/article/category/delete", {"category_id": NEW_CATEGORY}),
    case("category/delete 缺 id → 400", "DELETE", "/article/category/delete", {}, auth="admin"),
    case("category/delete 不存在 → 404", "DELETE", "/article/category/delete",
         {"category_id": AUTH_HEADER_MISSING_ID}, auth="admin"),
    case("category/delete 非本人 → 403", "DELETE", "/article/category/delete",
         {"category_id": CATEGORY_ADMIN}, auth="user"),
    case("category/delete 成功（管理员删新增分类）", "DELETE", "/article/category/delete",
         {"category_id": NEW_CATEGORY}, auth="admin"),
    case("category/list 读回（新增分类已删）", "GET", "/article/category/list"),

    # ==================== 写：article/delete ====================
    case("article/delete 无 token → 401", "DELETE", f"/article/delete/{NEW_ARTICLE}"),
    case("article/delete 不存在 → 404", "DELETE", f"/article/delete/{AUTH_HEADER_MISSING_ID}", auth="admin"),
    case("article/delete 非作者 → 403", "DELETE", f"/article/delete/{ADMIN_DRAFT}", auth="user"),
    case("article/delete 非数字 id → 400", "DELETE", "/article/delete/abc", auth="user"),
    case("article/delete 成功（作者删自己的文章）", "DELETE", f"/article/delete/{NEW_ARTICLE}", auth="user"),
    case("article/detail 读回（已删）→ 404", "GET", f"/article/detail/{NEW_ARTICLE}", auth="user"),
    case("article/delete 重复删 → 404", "DELETE", f"/article/delete/{NEW_ARTICLE}", auth="user"),
    case("article/delete 成功（删草稿）", "DELETE", f"/article/delete/{NEW_ARTICLE_DRAFT}", auth="user"),

    # ==================== 写：comment ====================
    case("comment/add 承载文章（user1 发布）→ id 88", "POST", "/article/add",
         {"title": f"{TAG}-comment-host", "content": "m2 comment host"}, auth="user"),
    case("comment/add 缺参 → 400", "POST", "/comment/add", {}),
    case("comment/add 文章不存在 → 404", "POST", "/comment/add",
         {"article_id": AUTH_HEADER_MISSING_ID, "content": "x"}),
    case("comment/add 匿名缺昵称 → 400", "POST", "/comment/add", {"article_id": NEW_COMMENT_HOST, "content": "x"}),
    case("comment/add 匿名单字昵称 → 400 昵称需 2-20 个字符", "POST", "/comment/add",
         {"article_id": NEW_COMMENT_HOST, "content": "x", "nickname": "甲"}),
    case("comment/add 匿名纯数字昵称 → 400 昵称不能是纯数字", "POST", "/comment/add",
         {"article_id": NEW_COMMENT_HOST, "content": "x", "nickname": "12"}),
    case("comment/add 纯数字内容 → 400 过于简单", "POST", "/comment/add",
         {"article_id": NEW_COMMENT_HOST, "content": "123456", "nickname": "游客"}),
    case("comment/add 超长内容 → 400 最多 500 字", "POST", "/comment/add",
         {"article_id": NEW_COMMENT_HOST, "content": LONG_CONTENT, "nickname": "游客"}),
    case("comment/add 匿名成功 → id 22", "POST", "/comment/add",
         {"article_id": NEW_COMMENT_HOST, "content": "m2 匿名评论", "nickname": "游客甲"}),
    case("comment/add 登录用户成功（昵称取 username）→ id 23", "POST", "/comment/add",
         {"article_id": NEW_COMMENT_HOST, "content": "m2 登录评论"}, auth="user"),
    case("comment/add 回复（parent_id=22）→ id 24", "POST", "/comment/add",
         {"article_id": NEW_COMMENT_HOST, "content": "m2 楼中楼", "nickname": "游客乙", "parent_id": NEW_COMMENT}),
    case("comment/add parent 不属于该文章 → 400", "POST", "/comment/add",
         {"article_id": NEW_COMMENT_HOST, "content": "x", "parent_id": 9}),
    case("comment/add parent 不存在 → 400", "POST", "/comment/add",
         {"article_id": NEW_COMMENT_HOST, "content": "x", "parent_id": AUTH_HEADER_MISSING_ID}),
    case("comment/list 新建文章的评论树", "GET", f"/comment/list/{NEW_COMMENT_HOST}"),
    case("comment/list 新建文章 分页 pageSize=1", "GET", f"/comment/list/{NEW_COMMENT_HOST}?page=1&pageSize=1"),
    case("comment/list 新建文章 第二页", "GET", f"/comment/list/{NEW_COMMENT_HOST}?page=2&pageSize=1"),
    case("comment/manage/list 按新建文章过滤", "GET", f"/comment/manage/list?article_id={NEW_COMMENT_HOST}",
         auth="admin"),
    case("comment/manage/update 无 token → 401", "PUT", "/comment/manage/update",
         {"comment_id": NEW_COMMENT, "content": "x"}),
    case("comment/manage/update 非管理员 → 403", "PUT", "/comment/manage/update",
         {"comment_id": NEW_COMMENT, "content": "x"}, auth="user"),
    case("comment/manage/update 缺 comment_id → 400", "PUT", "/comment/manage/update", {"content": "x"},
         auth="admin"),
    case("comment/manage/update 无字段 → 400", "PUT", "/comment/manage/update", {"comment_id": NEW_COMMENT},
         auth="admin"),
    case("comment/manage/update comment_id 非数字 → 500（原版拼出 NaN）", "PUT", "/comment/manage/update",
         {"comment_id": "abc", "content": "x"}, auth="admin"),
    case("comment/manage/update 不存在的 id → 200（更新 0 行）", "PUT", "/comment/manage/update",
         {"comment_id": AUTH_HEADER_MISSING_ID, "content": "x"}, auth="admin"),
    case("comment/manage/update 成功（改内容+昵称）", "PUT", "/comment/manage/update",
         {"comment_id": NEW_COMMENT, "content": "m2 改过的评论", "nickname": "甲"}, auth="admin"),
    case("comment/list 读回改过的评论", "GET", f"/comment/list/{NEW_COMMENT_HOST}"),
    case("comment/manage/delete 无 token → 401", "DELETE", "/comment/manage/delete",
         {"comment_ids": [NEW_COMMENT]}),
    case("comment/manage/delete 非管理员 → 403", "DELETE", "/comment/manage/delete",
         {"comment_ids": [NEW_COMMENT]}, auth="user"),
    case("comment/manage/delete 非数组 → 400", "DELETE", "/comment/manage/delete", {"comment_ids": "x"},
         auth="admin"),
    case("comment/manage/delete 空数组 → 400", "DELETE", "/comment/manage/delete", {"comment_ids": []},
         auth="admin"),
    case("comment/manage/delete 含非数字 → 500（原版拼出 NaN）", "DELETE", "/comment/manage/delete",
         {"comment_ids": ["abc"]}, auth="admin"),
    case("comment/manage/delete 级联删父评论（含楼中楼）→ 2 条", "DELETE", "/comment/manage/delete",
         {"comment_ids": [NEW_COMMENT]}, auth="admin"),
    case("comment/list 读回（只剩登录用户的评论）", "GET", f"/comment/list/{NEW_COMMENT_HOST}"),
    case("article/delete 删承载文章（连带评论）", "DELETE", f"/article/delete/{NEW_COMMENT_HOST}", auth="user"),
    case("comment/list 读回（空树）", "GET", f"/comment/list/{NEW_COMMENT_HOST}"),

    # ==================== 写：ad ====================
    case("ad/admin/add 无 token → 401", "POST", "/ad/admin/add", {"title": f"{TAG}-ad"}),
    case("ad/admin/add 非管理员 → 403", "POST", "/ad/admin/add", {"title": f"{TAG}-ad"}, auth="user"),
    case("ad/admin/add 缺标题 → 400", "POST", "/ad/admin/add", {}, auth="admin"),
    case("ad/admin/add 标题为 0（String(0)='0'，通过）→ id 8", "POST", "/ad/admin/add", {"title": 0}, auth="admin"),
    case("ad/admin/delete 清掉上面这条（id 8）→ 200", "DELETE", f"/ad/admin/delete/{NEW_AD_ZERO}", auth="admin"),
    case("ad/admin/detail 读回（id 8 已删）→ 404", "GET", f"/ad/admin/detail/{NEW_AD_ZERO}", auth="admin"),
    case("ad/admin/add 成功 → id 9", "POST", "/ad/admin/add",
         {"title": f"{TAG}-ad", "type": "text", "text_title": "T", "text_desc": "D",
          "link_url": "https://example.com", "position": "article_bottom", "sort_order": -1, "status": 1},
         auth="admin"),
    case("ad/admin/list 关键词命中新增", "GET", f"/ad/admin/list?keyword={TAG}-ad", auth="admin"),
    case("ad/admin/detail 新增广告", "GET", f"/ad/admin/detail/{NEW_AD}", auth="admin"),
    case("ad/slots article_bottom（新广告排最前）", "GET", "/ad/slots?position=article_bottom"),
    case("ad/click 新增广告 → 200 ok", "POST", f"/ad/click/{NEW_AD}"),
    case("ad/admin/detail 读回 click_count", "GET", f"/ad/admin/detail/{NEW_AD}", auth="admin"),
    case("ad/click 非数字 → 400", "POST", "/ad/click/abc"),
    case("ad/click 不存在的 id → 200 ok", "POST", f"/ad/click/{AUTH_HEADER_MISSING_ID}"),
    case("ad/admin/update 无 token → 401", "PUT", f"/ad/admin/update/{NEW_AD}", {"title": "x"}),
    case("ad/admin/update 缺 title → 500（NOT NULL 报错）", "PUT", f"/ad/admin/update/{NEW_AD}", {}, auth="admin"),
    case("ad/admin/update 非数字 id → 400", "PUT", "/ad/admin/update/abc", {"title": "x"}, auth="admin"),
    case("ad/admin/update 成功", "PUT", f"/ad/admin/update/{NEW_AD}",
         {"title": f"{TAG}-ad2", "type": "image", "image_url": "https://example.com/a.png",
          "position": "home_mid", "sort_order": 5, "status": 1}, auth="admin"),
    case("ad/admin/detail 读回更新", "GET", f"/ad/admin/detail/{NEW_AD}", auth="admin"),
    case("ad/admin/status 无 token → 401", "PUT", f"/ad/admin/status/{NEW_AD}", {"status": 0}),
    case("ad/admin/status 缺参 → 400", "PUT", f"/ad/admin/status/{NEW_AD}", {}, auth="admin"),
    case("ad/admin/status 非数字 id → 400", "PUT", "/ad/admin/status/abc", {"status": 0}, auth="admin"),
    case("ad/admin/status status=null → 200（Number(null)=0）", "PUT", f"/ad/admin/status/{NEW_AD}",
         {"status": None}, auth="admin"),
    case("ad/admin/detail 读回停用", "GET", f"/ad/admin/detail/{NEW_AD}", auth="admin"),
    case("ad/admin/status 启用回来 → 200", "PUT", f"/ad/admin/status/{NEW_AD}", {"status": 1}, auth="admin"),
    case("ad/admin/delete 无 token → 401", "DELETE", f"/ad/admin/delete/{NEW_AD}"),
    case("ad/admin/delete 非管理员 → 403", "DELETE", f"/ad/admin/delete/{NEW_AD}", auth="user"),
    case("ad/admin/delete 非数字 → 400", "DELETE", "/ad/admin/delete/abc", auth="admin"),
    case("ad/admin/delete 不存在的 id → 200（删 0 行也成功）", "DELETE",
         f"/ad/admin/delete/{AUTH_HEADER_MISSING_ID}", auth="admin"),
    case("ad/admin/delete 成功", "DELETE", f"/ad/admin/delete/{NEW_AD}", auth="admin"),
    case("ad/admin/detail 读回（已删）→ 404", "GET", f"/ad/admin/detail/{NEW_AD}", auth="admin"),
    case("ad/slots article_bottom（回到种子广告）", "GET", "/ad/slots?position=article_bottom"),

    # ==================== 写：announcement ====================
    case("announcement/add 无 token → 401", "POST", "/announcement/admin/add",
         {"title": f"{TAG}-ann", "content": "m2 公告"}),
    case("announcement/add 非管理员 → 403", "POST", "/announcement/admin/add",
         {"title": f"{TAG}-ann", "content": "m2 公告"}, auth="user"),
    case("announcement/add 缺标题 → 400", "POST", "/announcement/admin/add", {"content": "x"}, auth="admin"),
    case("announcement/add 标题空白 → 400", "POST", "/announcement/admin/add",
         {"title": "   ", "content": "x"}, auth="admin"),
    case("announcement/add 缺内容 → 400", "POST", "/announcement/admin/add", {"title": "x"}, auth="admin"),
    case("announcement/add 内容空白 → 400", "POST", "/announcement/admin/add",
         {"title": "x", "content": "  "}, auth="admin"),
    case("announcement/add 成功 → id 3", "POST", "/announcement/admin/add",
         {"title": f"{TAG}-ann", "content": "m2 公告内容"}, auth="admin"),
    case("announcement/admin/list 关键词命中新增", "GET", f"/announcement/admin/list?keyword={TAG}-ann",
         auth="admin"),
    case("announcement/latest（新增已启用，排最前）", "GET", "/announcement/latest"),
    case("announcement/update 无 token → 401", "PUT", f"/announcement/admin/update/{NEW_ANNOUNCEMENT}",
         {"title": "x", "content": "y"}),
    case("announcement/update 缺参 → 400", "PUT", f"/announcement/admin/update/{NEW_ANNOUNCEMENT}",
         {"title": "x"}, auth="admin"),
    case("announcement/update 非数字 id → 400", "PUT", "/announcement/admin/update/abc",
         {"title": "x", "content": "y"}, auth="admin"),
    case("announcement/update 成功", "PUT", f"/announcement/admin/update/{NEW_ANNOUNCEMENT}",
         {"title": f"{TAG}-ann2", "content": "m2 公告内容2"}, auth="admin"),
    case("announcement/admin/list 读回更新", "GET", f"/announcement/admin/list?keyword={TAG}-ann2", auth="admin"),
    case("announcement/status 无 token → 401", "PUT", f"/announcement/admin/status/{NEW_ANNOUNCEMENT}",
         {"status": 0}),
    case("announcement/status 缺参 → 400", "PUT", f"/announcement/admin/status/{NEW_ANNOUNCEMENT}", {},
         auth="admin"),
    case("announcement/status 非数字 id → 400", "PUT", "/announcement/admin/status/abc", {"status": 0},
         auth="admin"),
    case("announcement/status 停用 → 200", "PUT", f"/announcement/admin/status/{NEW_ANNOUNCEMENT}",
         {"status": 0}, auth="admin"),
    case("announcement/latest（停用后回到种子公告）", "GET", "/announcement/latest"),
    case("announcement/delete 无 token → 401", "DELETE", f"/announcement/admin/delete/{NEW_ANNOUNCEMENT}"),
    case("announcement/delete 非管理员 → 403", "DELETE", f"/announcement/admin/delete/{NEW_ANNOUNCEMENT}",
         auth="user"),
    case("announcement/delete 非数字 → 400", "DELETE", "/announcement/admin/delete/abc", auth="admin"),
    case("announcement/delete 成功", "DELETE", f"/announcement/admin/delete/{NEW_ANNOUNCEMENT}", auth="admin"),
    case("announcement/delete 不存在的 id → 200", "DELETE",
         f"/announcement/admin/delete/{AUTH_HEADER_MISSING_ID}", auth="admin"),
    case("announcement/admin/list 读回（回到种子 2 条）", "GET", "/announcement/admin/list", auth="admin"),
    case("announcement/latest 读回", "GET", "/announcement/latest"),
]
