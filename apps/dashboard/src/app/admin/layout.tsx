import Link from "next/link";
import { Building2, ChevronLeft } from "lucide-react";

export default function AdminLayout({ children }: { children: React.ReactNode }) {
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
