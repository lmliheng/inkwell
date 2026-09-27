"""M4 系统域用例（system 服务）：GET /system-monitor、/system-monitor/api-stats、GET /、/backup/download。

系统监控返回的是**运行时实时值**，两侧必然不同，所以下面把这类字段按路径 ignore
（键的存在性与嵌套结构仍逐层严格比对，只有值不比）：

    data.system.hostname       容器/宿主机名，部署形态不同就不同
    data.system.arch           Node 给 x64、JVM 给 amd64（刻意的差异，见 README）
    data.system.uptime         系统运行秒数，两侧采样时刻不同
    data.cpu.usage             两次 /proc/stat 采样求差，实时值（首次为 null）
    data.cpu.loadavg           实时负载
    data.memory.free/used/usagePercent   实时内存
    data.process.*             pid / 版本号 / 进程运行时长 / 堆内存，进程不同必然不同
    data.timestamp             采样时刻

**严格比对**的是同一台机器上两侧取值应当相同的字段：platform、osType、osRelease、cpuModel、
cpuCores、memory.total、db.connected、db.version —— 它们能通过才说明字段取自同一语义。

api-stats 的 data.list 整段 ignore：原版是单体进程，登记的是全部 111 个接口的调用计数；
Java 版按域拆服务，统计口径天然不同（详见 README）。列表外的信封、键名、message 仍严格比对。
字段名、排序、count=0 时 avgTime 为 null 这些细节由 scratchpad 里的 check_apistats.py 另行核对。

/backup/download 只有鉴权分支能进这套 JSON 对照（成功分支返回 zip 二进制）：
无 token → 401、非管理员 → 403。成功分支的产物（zip 内文件名、SQL 表清单与行数、
能否恢复成同构同量的库）由 scratchpad 里的 verify_backup.sh 实测。
"""

from _spec import case

REALTIME = (
    "data.system.hostname",
    "data.system.arch",
    "data.system.uptime",
    "data.cpu.usage",
    "data.cpu.loadavg",
    "data.memory.free",
    "data.memory.used",
    "data.memory.usagePercent",
    "data.process.pid",
    "data.process.nodeVersion",
    "data.process.uptime",
    "data.process.memoryRss",
    "data.process.heapUsed",
    "data.process.heapTotal",
    "data.timestamp",
)

CASES = [
    # ---------- GET /：公开健康检查。原版这条响应**没有 success 字段**，必须照抄 ----------
    case("根路径 公开健康检查", "GET", "/"),

    # ---------- GET /system-monitor ----------
    case("system-monitor 无 token → 401", "GET", "/system-monitor"),
    case("system-monitor 非管理员 → 403（文案是 systemmon 自己那套）", "GET", "/system-monitor", auth="user"),
    case("system-monitor 管理员 → 200（实时值除外逐字段比对）", "GET", "/system-monitor",
         auth="admin", ignore=REALTIME),

    # ---------- GET /system-monitor/api-stats ----------
    case("api-stats 无 token → 401", "GET", "/system-monitor/api-stats"),
    case("api-stats 非管理员 → 403", "GET", "/system-monitor/api-stats", auth="user"),
    case("api-stats 管理员 → 200（列表口径不同，只比信封与结构）", "GET", "/system-monitor/api-stats",
         auth="admin", ignore=("data.list",)),

    # ---------- GET /backup/download（成功分支是 zip，见文件头说明）----------
    case("backup 无 token → 401", "GET", "/backup/download"),
    case("backup 非管理员 → 403", "GET", "/backup/download", auth="user"),
]
