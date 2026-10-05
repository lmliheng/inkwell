"""对照用例的公共定义。每个模块一个文件，文件里导出 CASES 列表。

用例字段：
    name    用例名（打印用）
    method  HTTP 方法
    path    路径（含查询串）
    body    请求体（dict / list，缺省不发 body）
    auth    none | user | admin | apikey:read | apikey:write
    ignore  值比对时跳过的 JSON 路径（例如新建资源的自增 id："body.data.id"）
"""

import time

# 每次运行一个时间戳，用来给「新增」类用例生成唯一名字，避免重名冲突
STAMP = str(int(time.time()))


def case(name, method, path, body=None, auth="none", ignore=(), key=None):
    """key：列表响应用主键配对比较，容忍参考库被别人测试写进去的多余行。"""
    return {"name": name, "method": method, "path": path, "body": body, "auth": auth,
            "ignore": ignore, "key": key}
