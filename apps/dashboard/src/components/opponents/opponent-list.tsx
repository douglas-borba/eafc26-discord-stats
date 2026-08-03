"use client";

import Link from "next/link";
import { useParams, usePathname } from "next/navigation";
import { useState } from "react";
import { SearchInput } from "@/components/ui/search-input";
import type { OpponentSummary } from "@/lib/domain/types";

export function OpponentList({ opponents }: { opponents: OpponentSummary[] }) {
  const params = useParams();
  const pathname = usePathname();
  const clubId = params.clubId as string;
  const [search, setSearch] = useState("");

  const filtered = opponents.filter((o) => {
    if (!search) return true;
    const term = search.toLocaleLowerCase("pt-BR");
    return (o.clubName ?? "").toLocaleLowerCase("pt-BR").includes(term);
  });

  return (
    <div>
      <SearchInput value={search} onChange={setSearch} placeholder="Buscar adversário..." />
      <div className="mt-3 space-y-1">
        {filtered.map((o) => {
          const isActive = pathname.includes(o.clubId);
          return (
            <Link
              key={o.clubId}
              href={`/clubs/${clubId}/opponents/${o.clubId}`}
              className={`block px-3 py-2.5 rounded-lg transition-colors ${isActive ? "bg-accent/10 border border-accent/30" : "hover:bg-surface-raised border border-transparent"}`}
            >
              <p className="text-sm font-medium text-text-primary truncate">{o.clubName ?? o.clubId}</p>
              <div className="flex gap-2 mt-1 text-xs">
                <span className="text-win font-semibold">{o.wins}V</span>
                <span className="text-draw font-semibold">{o.draws}E</span>
                <span className="text-loss font-semibold">{o.losses}D</span>
                <span className="text-muted">· {o.matchesPlayed} jogos</span>
              </div>
            </Link>
          );
        })}
      </div>
    </div>
  );
}
