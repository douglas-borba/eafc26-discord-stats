import { NextResponse } from "next/server";
import { requireAdmin } from "@/lib/supabase/auth-server";

const BACKEND_TIMEOUT_MS = 10_000;
const MUTATING_METHODS = new Set(["POST", "PATCH", "PUT", "DELETE"]);

type ProxyOptions = {
  method?: string;
  body?: string;
};

export async function proxyAdminRequest(path: string, options: ProxyOptions = {}) {
  const auth = await requireAdmin();
  if (auth.kind === "anonymous" || auth.kind === "unavailable") return errorResponse(401, "unauthorized", "Autenticação administrativa necessária.");
  if (auth.kind === "forbidden") return errorResponse(403, "forbidden", "Acesso administrativo não autorizado.");
  const backendUrl = process.env.BACKEND_URL?.trim();
  const internalToken = process.env.ADMIN_INTERNAL_TOKEN?.trim();
  if (!backendUrl || !internalToken) {
    return errorResponse(503, "backend_not_configured", "Backend não configurado.");
  }

  const method = (options.method ?? "GET").toUpperCase();
  const baseUrl = backendUrl.replace(/\/$/, "");

  try {
    const security = MUTATING_METHODS.has(method)
      ? await springCsrf(baseUrl, internalToken)
      : undefined;

    if (MUTATING_METHODS.has(method) && !security) {
      return errorResponse(503, "csrf_unavailable", "Não foi possível iniciar uma operação segura.");
    }

    const headers = new Headers({ Accept: "application/json", Authorization: `Bearer ${internalToken}` });
    if (options.body !== undefined) headers.set("Content-Type", "application/json");
    if (security) {
      headers.set("Cookie", security.cookieHeader);
      headers.set("X-XSRF-TOKEN", security.token);
    }

    const response = await fetch(`${baseUrl}${path}`, {
      method,
      headers,
      body: options.body,
      cache: "no-store",
      signal: AbortSignal.timeout(BACKEND_TIMEOUT_MS),
    });

    return safeBackendResponse(response);
  } catch {
    return errorResponse(503, "backend_unavailable", "Backend indisponível. Tente novamente.");
  }
}

async function springCsrf(baseUrl: string, internalToken: string) {
  const response = await fetch(`${baseUrl}/api/admin/clubs`, {
    method: "GET",
    headers: { Accept: "application/json", Authorization: `Bearer ${internalToken}` },
    cache: "no-store",
    signal: AbortSignal.timeout(BACKEND_TIMEOUT_MS),
  });
  if (!response.ok) return null;

  const cookies = getSetCookies(response.headers);
  const tokenCookie = cookies.find((cookie) => cookie.startsWith("XSRF-TOKEN="));
  if (!tokenCookie) return null;

  const token = decodeURIComponent(tokenCookie.slice("XSRF-TOKEN=".length).split(";", 1)[0]);
  const cookieHeader = cookies.map((cookie) => cookie.split(";", 1)[0]).join("; ");
  return token && cookieHeader ? { token, cookieHeader } : null;
}

function getSetCookies(headers: Headers): string[] {
  const extended = headers as Headers & { getSetCookie?: () => string[] };
  const values = extended.getSetCookie?.();
  if (values?.length) return values;

  const combined = headers.get("set-cookie");
  if (!combined) return [];
  return combined.split(/,(?=\s*[A-Za-z0-9_-]+=)/).map((value) => value.trim());
}

async function safeBackendResponse(response: Response) {
  if (!response.ok) {
    if (response.status === 400) return errorResponse(400, "validation_error", "Verifique os dados informados.");
    if (response.status === 403) return errorResponse(403, "csrf_error", "A operação segura expirou. Tente novamente.");
    if (response.status === 404) return errorResponse(404, "not_found", "Clube não encontrado.");
    if (response.status === 502) return errorResponse(502, "ea_unavailable", "A EA está indisponível no momento.");
    return errorResponse(503, "backend_unavailable", "Backend indisponível. Tente novamente.");
  }

  const contentType = response.headers.get("content-type") ?? "";
  if (!contentType.includes("application/json")) {
    return errorResponse(503, "invalid_backend_response", "Resposta inválida do backend.");
  }

  const payload = sanitize(await response.json());
  return NextResponse.json(payload, { status: response.status });
}

function sanitize(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(sanitize);
  if (!value || typeof value !== "object") return value;

  return Object.fromEntries(
    Object.entries(value).flatMap(([key, item]) => {
      const normalized = key.toLowerCase();
      if (normalized.includes("webhookurl") || normalized.includes("secretreference") || normalized === "token") {
        return [];
      }
      return [[key, sanitize(item)]];
    }),
  );
}

function errorResponse(status: number, error: string, message: string) {
  return NextResponse.json({ error, message }, { status });
}
