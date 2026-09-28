-- 首次初始化（数据目录为空、由 docker-entrypoint-initdb.d 执行）后立刻刷新统计信息。
--
-- 为什么需要：01-schema.sql 是 mysqldump 产物（表结构 + 少量种子），导完 InnoDB 只有粗略统计。统计信息不准时
-- 优化器可能给「GROUP BY 含 TEXT 列 + ORDER BY」的查询（如 /blog/feed、/blog/hot）选到
-- 需要更大排序内存的计划，进而报 1038 Out of sort memory。ANALYZE 之后即可恢复。
-- （compose 里同时把 --sort-buffer-size 提到 2M 作为双保险。）
--
-- 注意：这个脚本只在容器第一次初始化时执行。往一个**已有**的库导入 dump 之后，
-- 请手动跑一次：docker exec jscreator-mysql mysql -uroot -p*** <库名> -e "ANALYZE TABLE ..."

SET @sql = (
    SELECT GROUP_CONCAT(CONCAT('ANALYZE TABLE `', table_name, '`') SEPARATOR '; ')
    FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
