"use client";
import { useState } from "react";
import { createAuthBrowserClient } from "@/lib/supabase/auth-browser";

export default function AdminLogin() {
  const [email, setEmail] = useState("");
  const [sent, setSent] = useState(false);
  const [error, setError] = useState("");
  async function submit(event: React.FormEvent) {
    event.preventDefault(); setError("");
    const { error } = await createAuthBrowserClient().auth.signInWithOtp({ email, options: { emailRedirectTo: `${window.location.origin}/auth/callback?next=/admin/clubs` } });
    if (error) setError("Não foi possível enviar o link de acesso."); else setSent(true);
  }
  return <main className="mx-auto flex min-h-screen max-w-md items-center px-6"><form onSubmit={submit} className="w-full space-y-4 rounded-xl border border-border bg-surface p-6"><h1 className="text-xl font-semibold">Administração</h1><p className="text-sm text-muted">Receba um link seguro no seu e-mail autorizado.</p><input type="email" required value={email} onChange={(e) => setEmail(e.target.value)} className="w-full rounded border border-border bg-transparent p-2" placeholder="admin@exemplo.com" /><button className="w-full rounded bg-accent p-2 font-semibold text-white">Enviar link de acesso</button>{sent && <p className="text-sm">Verifique seu e-mail para continuar.</p>}{error && <p className="text-sm text-red-500">{error}</p>}</form></main>;
}
