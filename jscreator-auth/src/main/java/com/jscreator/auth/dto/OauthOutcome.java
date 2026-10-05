package com.jscreator.auth.dto;

import java.util.Map;

/**
 * oauth service 的返回值形状，对齐 Express 版 modules/oauth/oauth.service：
 * {@code {status, body}}（JSON）/ {@code {status, text}}（纯文本）/ {@code {redirect}}（302）。
 */
public sealed interface OauthOutcome {

    /** {@code res.status(status).json(body)} */
    record Json(int status, Map<String, Object> body) implements OauthOutcome {
    }

    /** {@code res.status(status).send(text)}，原版是 text/html（400 的文本错误走这条）。 */
    record Text(int status, String text) implements OauthOutcome {
    }

    /** {@code res.redirect(location)}（302）。 */
    record Redirect(String location) implements OauthOutcome {
    }
}
