"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { cn } from "@/lib/utils";
import { Shield, Circle, Users, Menu, X } from "lucide-react";
import { useState, useEffect } from "react";

const navItems = [
  { label: "Visão Geral", href: "overview", icon: Shield },
  { label: "Partidas", href: "matches", icon: Circle },
  { label: "Jogadores", href: "players", icon: Users },
];

export function OverviewHeader({ clubId }: { clubId: string }) {
  const pathname = usePathname();
  const [mobileOpen, setMobileOpen] = useState(false);

  useEffect(() => {
    setMobileOpen(false);
  }, [pathname]);

  useEffect(() => {
    const handleEscape = (e: KeyboardEvent) => {
      if (e.key === "Escape") setMobileOpen(false);
    };
    document.addEventListener("keydown", handleEscape);
    return () => document.removeEventListener("keydown", handleEscape);
  }, []);

  return (
    <header className="sticky top-0 z-40 border-b border-border bg-surface/90 backdrop-blur-md">
      <div className="max-w-[1600px] mx-auto px-5 lg:px-8 xl:px-10">
        <div className="flex items-center justify-between h-14">
          {/* Left: brand + title */}
          <div className="flex items-center gap-3">
            <BrandMark />
            <span className="font-semibold text-text-primary text-[15px] hidden sm:block">
              EA FC STATS
            </span>
          </div>

          {/* Center: desktop nav */}
          <nav className="hidden md:flex items-center gap-1">
            {navItems.map(({ label, href, icon: Icon }) => {
              const fullHref = `/clubs/${clubId}/${href}`;
              const isActive = pathname.startsWith(fullHref);
              return (
                <Link
                  key={href}
                  href={fullHref}
                  className={cn(
                    "flex items-center gap-2 px-3 py-1.5 rounded-md text-[13px] font-medium transition-colors",
                    isActive
                      ? "bg-accent/12 text-accent"
                      : "text-text-soft hover:text-text-primary hover:bg-surface-raised"
                  )}
                >
                  <Icon className="w-4 h-4" strokeWidth={1.8} />
                  {label}
                </Link>
              );
            })}
          </nav>

          {/* Right: mobile hamburger */}
          <button
            onClick={() => setMobileOpen(!mobileOpen)}
            className="p-2 text-muted hover:text-text-primary md:hidden"
            aria-label={mobileOpen ? "Fechar menu" : "Abrir menu"}
          >
            {mobileOpen ? <X className="w-5 h-5" /> : <Menu className="w-5 h-5" />}
          </button>
        </div>
      </div>

      {/* Mobile dropdown nav */}
      {mobileOpen && (
        <div className="md:hidden border-t border-border bg-surface px-5 py-3">
          <nav className="flex flex-col gap-0.5">
            {navItems.map(({ label, href, icon: Icon }) => {
              const fullHref = `/clubs/${clubId}/${href}`;
              const isActive = pathname.startsWith(fullHref);
              return (
                <Link
                  key={href}
                  href={fullHref}
                  className={cn(
                    "flex items-center gap-3 px-3 py-2 rounded-md text-[13px] font-medium transition-colors",
                    isActive
                      ? "bg-accent/10 text-accent"
                      : "text-text-soft hover:text-text-primary hover:bg-surface-raised"
                  )}
                >
                  <Icon className="w-[18px] h-[18px]" strokeWidth={1.8} />
                  {label}
                </Link>
              );
            })}
          </nav>
        </div>
      )}
    </header>
  );
}

function BrandMark() {
  return (
    <div
      className="w-8 h-8 rounded-lg flex items-center justify-center text-white font-bold text-xs shrink-0"
      style={{ background: "linear-gradient(135deg, #2f81f7, #3fb950)" }}
    >
      FC
    </div>
  );
}
