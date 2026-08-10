import "server-only";
import { createServerClient } from "@supabase/ssr";
import { cookies } from "next/headers";

function credentials() {
  const url = process.env.NEXT_PUBLIC_SUPABASE_URL;
  const key = process.env.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY;
  if (!url || !key) throw new Error("Supabase Auth is not configured");
  return { url, key };
}

export async function createAuthServerClient() {
  const cookieStore = await cookies();
  const { url, key } = credentials();
  return createServerClient(url, key, {
    cookies: {
      getAll: () => cookieStore.getAll(),
      setAll: (values) => {
        try { values.forEach(({ name, value, options }) => cookieStore.set(name, value, options)); } catch { /* server component */ }
      },
    },
  });
}

export async function requireAdmin() {
  const allowedEmail = process.env.ADMIN_ALLOWED_EMAIL?.trim().toLowerCase();
  if (!allowedEmail) return { kind: "unavailable" as const };
  try {
    const client = await createAuthServerClient();
    const { data: { user } } = await client.auth.getUser();
    if (!user) return { kind: "anonymous" as const };
    return user.email?.toLowerCase() === allowedEmail ? { kind: "allowed" as const, client } : { kind: "forbidden" as const, client };
  } catch { return { kind: "unavailable" as const }; }
}
