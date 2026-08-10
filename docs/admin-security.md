# Administração segura

| Superfície | Acesso |
| --- | --- |
| `/clubs/**` e APIs esportivas de leitura | Público |
| `/api/health` | Público |
| `/admin/**` no Dashboard | Supabase Auth + `ADMIN_ALLOWED_EMAIL` |
| `/api/admin/**` no Dashboard | Supabase Auth + `ADMIN_ALLOWED_EMAIL` |
| `/api/admin/**` no Spring | BFF interno com `ADMIN_INTERNAL_TOKEN` |
| operações legadas mutáveis no Spring | credencial interna + CSRF |
| `/api/dev/**` | Negado |
| EA Gateway | Interno com `EA_GATEWAY_INTERNAL_TOKEN` |

O navegador nunca recebe `ADMIN_INTERNAL_TOKEN`. O Dashboard usa Supabase Auth
com sessão por cookie e magic link; o BFF valida o usuário com Supabase antes de
enviar a credencial interna ao Spring. A ausência de `ADMIN_ALLOWED_EMAIL` ou
`ADMIN_INTERNAL_TOKEN` deixa a administração indisponível.

## Configuração

No Vercel, configure `NEXT_PUBLIC_SUPABASE_URL`,
`NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY`, `ADMIN_ALLOWED_EMAIL`,
`ADMIN_INTERNAL_TOKEN` e `BACKEND_URL`. Configure a URL de redirecionamento
`https://<dashboard>/auth/callback` no Supabase Auth.

No Railway Spring, configure o mesmo `ADMIN_INTERNAL_TOKEN`. Nunca registre ou
versione tokens, cookies, webhooks ou URLs internas.
