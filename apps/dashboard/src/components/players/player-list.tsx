"use client";

import Link from "next/link";
import { useParams, usePathname } from "next/navigation";
import { useState } from "react";
import { RatingBar } from "@/components/ui/rating-bar";
import { SearchInput } from "@/components/ui/search-input";
import type { PlayerSummary } from "@/lib/domain/types";

export function PlayerList({ players }: { players: PlayerSummary[] }) {
  const params = useParams();
  const pathname = usePathname();
  const clubId = params.clubId as string;
  const [search, setSearch] = useState("");

  const filtered = players.filter((p) => {
    if (!search) return true;
    const term = search.toLocaleLowerCase("pt-BR");
    const name = (p.displayName ?? p.platformName ?? "").toLocaleLowerCase("pt-BR");
    return name.includes(term);
  });

  return (
    <div>
      <SearchInput value={search} onChange={setSearch} placeholder="Buscar jogador..." />
      <div className="mt-3 space-y-1">
        {filtered.map((p) => {
          const isActive = pathname.includes(p.playerId);
          return (
            <Link
              key={p.playerId}
              href={`/clubs/${clubId}/players/${p.playerId}`}
              className={`block px-3 py-2.5 rounded-lg transition-colors ${isActive ? "bg-accent/10 border border-accent/30" : "hover:bg-surface-raised border border-transparent"}`}
            >
              <div className="flex items-center justify-between mb-1">
                <span className="text-sm font-medium text-text-primary truncate">
                  {p.displayName ?? p.platformName ?? p.playerId}
                </span>
                <span className="text-xs text-muted ml-2 shrink-0">{p.matchesPlayed} jogos</span>
              </div>
              {p.averageRating != null && <RatingBar rating={p.averageRating} />}
            </Link>
          );
        })}
      </div>
    </div>
  );
}
