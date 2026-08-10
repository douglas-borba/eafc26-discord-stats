import { NextResponse } from "next/server";
import { createServerClient } from "@supabase/ssr";

export async function GET(request: Request) {
  const url = new URL(request.url); const code = url.searchParams.get("code");
  const response = NextResponse.redirect(new URL(code ? (url.searchParams.get("next") || "/admin/clubs") : "/admin/login", url.origin));
  if (!code) return response;
  const supabase = createServerClient(process.env.NEXT_PUBLIC_SUPABASE_URL!, process.env.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY!, { cookies: { getAll: () => request.headers.get("cookie")?.split("; ").map((part) => { const [name, ...rest] = part.split("="); return { name, value: rest.join("=") }; }) ?? [], setAll: (values) => values.forEach(({ name, value, options }) => response.cookies.set(name, value, options)) } });
  await supabase.auth.exchangeCodeForSession(code);
  return response;
}
