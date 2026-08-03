"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { useState, useCallback } from "react";
import { SearchInput } from "@/components/ui/search-input";
import { RatingBar } from "@/components/ui/rating-bar";
import { PlayerProfileView } from "@/components/players/player-profile-view";
import { ArrowLeft } from "lucide-react";
import type { PlayerSummary, PlayerProfile } from "@/lib/domain/types";

export function PlayersShell({
  players,
  selectedPlayerId,
  profile,
  clubId,
}: {
  players: PlayerSummary[];
  selectedPlayerId: string | null;
  profile: PlayerProfile | null;
  clubId: string;
}) {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [search, setSearch] = useState("");
  const [mobileShowDetail, setMobileShowDetail] = useState(!!searchParams.get("player"));

  const filtered = players.filter((p) => {
    if (!search) return true;
    const term = search.toLocaleLowerCase("pt-BR");
    const name = (p.displayName ?? p.platformName ?? "").toLocaleLowerCase("pt-BR");
    return name.includes(term);
  });

  const handleSelect = useCallback(
    (playerId: string) => {
      router.push(`/clubs/${clubId}/players?player=${playerId}`, { scroll: false });
      setMobileShowDetail(true);
    },
    [router, clubId]
  );

  const handleBack = useCallback(() => {
    setMobileShowDetail(false);
  }, []);

  return (
    <div className="lg:grid lg:grid-cols-[minmax(260px,340px)_minmax(0,1fr)] lg:gap-6">
      {/* Left: player list */}
      <div className={`${mobileShowDetail ? "hidden" : "block"} lg:block`}>
        <h1 className="text-xl font-bold text-text-primary mb-4">Jogadores</h1>
        <div className="lg:sticky lg:top-[18px]">
          <SearchInput value={search} onChange={setSearch} placeholder="Buscar jogador..." />
          <div className="mt-3 lg:max-h-[calc(100vh-160px)] lg:overflow-y-auto space-y-0.5 pr-1">
            {filtered.map((p) => {
              const isActive = p.playerId === selectedPlayerId;
              return (
                <button
                  key={p.playerId}
                  onClick={() => handleSelect(p.playerId)}
                  className={`w-full text-left block px-3 py-2.5 rounded-lg transition-colors ${
                    isActive
                      ? "shadow-[inset_3px_0_var(--color-accent)] bg-accent/8"
                      : "hover:bg-surface-raised"
                  }`}
                >
                  <div className="flex items-center justify-between mb-1">
                    <span className="text-sm font-medium text-text-primary truncate">
                      {p.displayName ?? p.platformName ?? p.playerId}
                    </span>
                    <span className="text-xs text-muted ml-2 shrink-0">{p.matchesPlayed} jogos</span>
                  </div>
                  {p.averageRating != null && <RatingBar rating={p.averageRating} />}
                </button>
              );
            })}
          </div>
        </div>
      </div>

      {/* Right: profile */}
      <div className={`${!mobileShowDetail ? "hidden" : "block"} lg:block`}>
        {mobileShowDetail && (
          <button
            onClick={handleBack}
            className="flex items-center gap-1.5 text-sm text-accent mb-4 lg:hidden"
          >
            <ArrowLeft className="w-4 h-4" />
            Todos os jogadores
          </button>
        )}
        {profile ? (
          <PlayerProfileView profile={profile} clubId={clubId} />
        ) : (
          <p className="text-muted text-sm text-center py-12">Nenhum jogador disponível.</p>
        )}
      </div>
    </div>
  );
}
