"""email 模块（/email/send-code、/email/login）的对照用例。

没有 SMTP 授权码：原版 nodemailer 发送失败时是**内部吞错**（legacy-utils/email-sender.ts 的
sendMail 里 catch 掉），所以 /email/send-code 依然返回 200「验证码已发送，请查收邮件」——
Java 侧照抄这个行为（EmailAuthSender 捕获发送异常），对照在这里就能验证。

验证码只存在原版/Java 各自的进程内存里、也拿不到，所以「验证码正确 → 登录成功」这一条
没法用对照验证（用例只能覆盖错误分支）。Java 侧的成功分支另用本地 SMTP 假服务自证（见结论）。

用例不写任何库，重复执行无副作用。
"""

from _spec import case

MAIL = "m1d-mail-test@example.com"

CASES = [
    case("send-code 缺邮箱 → 400", "POST", "/email/send-code", {}),
    case("send-code 邮箱为空白 → 400", "POST", "/email/send-code", {"email": "   "}),
    case("send-code 空字符串邮箱 → 400", "POST", "/email/send-code", {"email": ""}),
    case("send-code 正常（SMTP 失败被吞，仍 200）", "POST", "/email/send-code", {"email": MAIL}),
    case("send-code 邮箱字段是数字（JS String 化）", "POST", "/email/send-code", {"email": 12345}),

    case("login 缺参 → 400", "POST", "/email/login", {}),
    case("login 只有邮箱 → 400", "POST", "/email/login", {"email": MAIL}),
    case("login 只有验证码 → 400", "POST", "/email/login", {"code": "123456"}),
    case("login 空字符串邮箱 → 400", "POST", "/email/login", {"email": "", "code": "123456"}),
    case("login 从未发过码的邮箱 → 400", "POST", "/email/login",
         {"email": "m1d-never-sent@example.com", "code": "123456"}),
    case("login 发过码但码不对 → 400", "POST", "/email/login", {"email": MAIL, "code": "abc"}),
]
