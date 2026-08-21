"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { cn } from "@/lib/utils";
import { Shield, Circle, Users, Menu, X, Building2, Lock } from "lucide-react";
import { useState, useEffect } from "react";

const navItems = [
  { label: "Visão Geral", href: "overview", icon: Shield },
  { label: "Partidas", href: "matches", icon: Circle },
  { label: "Jogadores", href: "players", icon: Users },
];

export function SidebarNav({ clubId, clubName, restricted = false }: { clubId: string; clubName: string; restricted?: boolean }) {
  const pathname = usePathname();
  const [mobileOpen, setMobileOpen] = useState(false);

  useEffect(() => {
    const handleEscape = (e: KeyboardEvent) => {
      if (e.key === "Escape") setMobileOpen(false);
    };
    document.addEventListener("keydown", handleEscape);
    return () => document.removeEventListener("keydown", handleEscape);
  }, []);

  useEffect(() => {
    setMobileOpen(false);
  }, [pathname]);

  return (
    <>
      {/* Mobile header */}
      <header className="fixed top-0 left-0 right-0 z-50 flex items-center h-16 px-4 bg-surface/80 backdrop-blur-md border-b border-border lg:hidden">
        <button onClick={() => setMobileOpen(true)} className="p-2 text-muted hover:text-text-primary" aria-label="Abrir menu">
          <Menu className="w-5 h-5" />
        </button>
        <div className="flex items-center gap-2 ml-3">
          <BrandMark />
          <span className="font-semibold text-text-primary text-[15px]">EA FC STATS</span>
        </div>
      </header>

      {/* Overlay */}
      {mobileOpen && (
        <div className="fixed inset-0 z-50 bg-black/50 lg:hidden" onClick={() => setMobileOpen(false)} />
      )}

      {/* Sidebar */}
      <aside
        className={cn(
          "fixed top-0 left-0 z-50 h-screen w-[248px] bg-surface border-r border-border flex flex-col transition-transform duration-200",
          "lg:sticky lg:translate-x-0",
          mobileOpen ? "translate-x-0" : "-translate-x-full"
        )}
      >
        <div className="flex items-center justify-between px-5 h-16 border-b border-border">
          <div className="flex items-center gap-2.5">
            <BrandMark />
            <div>
              <span className="font-semibold text-text-primary text-[15px] block leading-tight">EA FC STATS</span>
              <span className="block max-w-[150px] truncate text-[11px] text-muted">{clubName}</span>
            </div>
          </div>
          <button onClick={() => setMobileOpen(false)} className="p-1 text-muted hover:text-text-primary lg:hidden" aria-label="Fechar menu">
            <X className="w-4 h-4" />
          </button>
        </div>

        <nav className="flex-1 px-3 py-4">
          <p className="px-3 mb-2 text-[11px] font-semibold uppercase tracking-wider text-muted">Explorar</p>
          <ul className="space-y-0.5">
            {navItems.map(({ label, href, icon: Icon }) => {
              const fullHref = `/clubs/${clubId}/${href}`;
              const isActive = pathname.startsWith(fullHref);
              return (
                <li key={href}>
                  <Link
                    href={fullHref}
                    className={cn(
                      "flex items-center gap-3 px-3 py-2 rounded-md text-[13px] font-medium transition-colors",
                      isActive
                        ? "shadow-[inset_3px_0_var(--color-accent)] bg-accent/10 text-accent"
                        : "text-text-soft hover:text-text-primary hover:bg-surface-raised"
                    )}
                  >
                    <Icon className="w-[18px] h-[18px]" strokeWidth={1.8} />
                    {label}{restricted && href !== "overview" && <Lock className="ml-auto h-3.5 w-3.5" aria-label="Disponível no plano completo" />}
                  </Link>
                </li>
              );
            })}
          </ul>
        </nav>

        <div className="border-t border-border px-3 py-4">
          <Link href="/admin/clubs" className="flex items-center gap-3 rounded-md px-3 py-2 text-[13px] font-medium text-muted transition-colors hover:bg-surface-raised hover:text-text-primary">
            <Building2 className="h-[18px] w-[18px]" strokeWidth={1.8} />
            Administrar clubes
          </Link>
        </div>
      </aside>
    </>
  );
}

function BrandMark() {
  return (
    <div
      className="w-[38px] h-[38px] rounded-lg flex items-center justify-center text-white font-bold text-sm shrink-0"
      style={{ background: "linear-gradient(135deg, #2f81f7, #3fb950)" }}
    >
      FC
    </div>
  );
}
