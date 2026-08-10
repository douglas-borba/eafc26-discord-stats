"use client";
import { useState } from "react";
import { useRouter } from "next/navigation";
import { createAuthBrowserClient } from "@/lib/supabase/auth-browser";

export default function AdminLogin() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const router = useRouter();

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setError("");
    setLoading(true);
    const { error } = await createAuthBrowserClient().auth.signInWithPassword({ email, password });
    setLoading(false);
    if (error) {
      setError("E-mail ou senha incorretos.");
      return;
    }
    router.push("/admin/clubs");
  }

  return (
    <main className="mx-auto flex min-h-screen max-w-md items-center px-6">
      <form onSubmit={submit} className="w-full space-y-4 rounded-xl border border-border bg-surface p-6">
        <h1 className="text-xl font-semibold">Administração</h1>
        <div className="space-y-1.5">
          <label htmlFor="email" className="text-sm text-muted">E-mail</label>
          <input id="email" type="email" required value={email} onChange={(e) => setEmail(e.target.value)} className="w-full rounded border border-border bg-transparent p-2" placeholder="admin@exemplo.com" />
        </div>
        <div className="space-y-1.5">
          <label htmlFor="password" className="text-sm text-muted">Senha</label>
          <input id="password" type="password" required value={password} onChange={(e) => setPassword(e.target.value)} className="w-full rounded border border-border bg-transparent p-2" />
        </div>
        <button disabled={loading} className="w-full rounded bg-accent p-2 font-semibold text-white disabled:opacity-50">
          {loading ? "Entrando…" : "Entrar"}
        </button>
        {error && <p className="text-sm text-red-500">{error}</p>}
      </form>
    </main>
  );
}
