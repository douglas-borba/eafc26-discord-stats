import Link from "next/link";
import { Building2, ChevronLeft } from "lucide-react";
import { redirect } from "next/navigation";
import { requireAdmin } from "@/lib/supabase/auth-server";

export default async function AdminLayout({ children }: { children: React.ReactNode }) {
  const auth = await requireAdmin();
  if (auth.kind === "anonymous") redirect("/admin/login");
  if (auth.kind === "forbidden") return <main className="p-8">Acesso negado.</main>;
  if (auth.kind === "unavailable") return <main className="p-8">Administração indisponível.</main>;
  return (
    <div className="min-h-screen">
      <header className="border-b border-border bg-surface">
        <div className="mx-auto flex h-16 max-w-[1180px] items-center justify-between px-4 lg:px-6">
          <Link href="/admin/clubs" className="flex items-center gap-3 font-semibold text-text-primary">
            <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-accent/15 text-accent">
              <Building2 className="h-5 w-5" />
            </span>
            Administração de clubes
          </Link>
          <Link href="/clubs" className="flex items-center gap-1.5 text-sm text-muted hover:text-text-primary">
            <ChevronLeft className="h-4 w-4" /> Voltar ao produto
          </Link>
        </div>
      </header>
      <main className="mx-auto max-w-[1180px] px-4 py-6 lg:px-6 lg:py-8">{children}</main>
    </div>
  );
}
