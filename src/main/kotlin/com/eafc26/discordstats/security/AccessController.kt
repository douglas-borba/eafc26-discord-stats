package com.eafc26.discordstats.security

import org.springframework.http.MediaType
import org.springframework.security.core.Authentication
import org.springframework.security.web.server.csrf.CsrfToken
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

@RestController
class AccessController {
    @GetMapping("/login", produces = [MediaType.TEXT_HTML_VALUE])
    fun viewerLogin(
        exchange: ServerWebExchange,
    ): Mono<String> = csrf(exchange).map {
        loginPage(it, false, exchange.request.queryParams.containsKey("error"), exchange.request.queryParams.containsKey("logout"))
    }

    @GetMapping("/admin/login", produces = [MediaType.TEXT_HTML_VALUE])
    fun adminLogin(
        exchange: ServerWebExchange,
    ): Mono<String> = csrf(exchange).map {
        loginPage(it, true, exchange.request.queryParams.containsKey("error"), false)
    }

    @GetMapping("/access-denied", produces = [MediaType.TEXT_HTML_VALUE])
    fun denied(): String = messagePage("Acesso restrito", "Esta área exige acesso administrativo.")

    @GetMapping("/session-expired", produces = [MediaType.TEXT_HTML_VALUE])
    fun expired(): String = messagePage("Sessão encerrada", "Entre novamente para continuar explorando o clube.")

    @GetMapping("/api/auth/session", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun session(authentication: Authentication, exchange: ServerWebExchange): Mono<Map<String, Any>> =
        csrf(exchange).map { token ->
            mapOf(
                "authenticated" to true,
                "role" to if (authentication.authorities.any { authority -> authority.authority == "ROLE_ADMIN" }) "ADMIN" else "VIEWER",
                "csrfToken" to token.token,
            )
        }

    private fun csrf(exchange: ServerWebExchange): Mono<CsrfToken> =
        exchange.getAttribute<Mono<CsrfToken>>(CsrfToken::class.java.name)
            ?: Mono.error(IllegalStateException("CSRF token is unavailable"))

    private fun loginPage(token: CsrfToken, admin: Boolean, failed: Boolean, loggedOut: Boolean): String {
        val title = if (admin) "Acesso administrativo" else "Acesse os dados e a trajetória do clube"
        val identity = if (admin) "admin" else "viewer"
        val alternate = if (admin) "<a href=\"/login\">Voltar ao acesso do grupo</a>" else "<a href=\"/admin/login\">Acesso administrativo</a>"
        val feedback = when {
            failed -> "<p class=\"notice\">Palavra-passe inválida. Tente novamente.</p>"
            loggedOut -> "<p class=\"success\">Sessão encerrada com segurança.</p>"
            else -> ""
        }
        return """<!doctype html><html lang="pt-BR"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>EA FC STATS — Acesso</title><style>
        *{box-sizing:border-box}body{margin:0;min-height:100vh;display:grid;place-items:center;padding:24px;background:#0d1117;color:#f0f6fc;font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif}.login{width:min(100%,390px);padding:32px;border:1px solid #30363d;border-radius:16px;background:#121820;box-shadow:0 24px 70px #0008}.mark{display:grid;width:52px;height:52px;place-items:center;border:1px solid #58a6ff77;border-radius:14px;background:#388bfd28;font-weight:900}h1{margin:22px 0 4px;font-size:24px}.club{color:#58a6ff;font-weight:750}.intro{margin:22px 0;color:#b1bac4;line-height:1.5}label{display:block;margin-bottom:8px;font-size:13px;font-weight:700}input[type=password]{width:100%;padding:13px 14px;border:1px solid #484f58;border-radius:9px;background:#0d1117;color:#fff;font-size:16px}button{width:100%;margin-top:16px;padding:13px;border:0;border-radius:9px;background:#238636;color:#fff;font-weight:800;font-size:15px;cursor:pointer}.alternate{margin:24px 0 0;text-align:center;font-size:12px}.alternate a{color:#8b949e}.notice,.success{padding:10px;border-radius:8px;font-size:13px}.notice{background:#f8514920;color:#ffb3ad}.success{background:#23863622;color:#7ee787}</style></head><body><main class="login"><span class="mark">FC</span><h1>EA FC STATS</h1><div class="club">Associação BF</div><p class="intro">$title</p>$feedback${if (admin) "<p class=\"notice\">Área reservada às operações do clube.</p>" else ""}<form method="post" action="/login"><input type="hidden" name="username" value="$identity"><input type="hidden" name="${token.parameterName}" value="${token.token}"><label for="password">Palavra-passe</label><input id="password" name="password" type="password" autocomplete="current-password" required autofocus><button type="submit">Entrar</button></form><p class="alternate">$alternate</p></main></body></html>"""
    }

    private fun messagePage(title: String, message: String): String =
        """<!doctype html><html lang="pt-BR"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>$title</title><style>body{margin:0;min-height:100vh;display:grid;place-items:center;background:#0d1117;color:#f0f6fc;font-family:sans-serif}.box{max-width:420px;padding:32px;text-align:center}a{color:#58a6ff}</style></head><body><main class="box"><h1>$title</h1><p>$message</p><a href="/">Voltar à Visão Geral</a></main></body></html>"""
}
