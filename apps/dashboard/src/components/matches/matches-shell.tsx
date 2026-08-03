"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { useState, useCallback } from "react";
import { OutcomeBadge } from "@/components/ui/outcome-badge";
import { MatchDetailView } from "@/components/matches/match-detail-view";
import { formatDate } from "@/lib/utils";
import { ArrowLeft } from "lucide-react";
import type { MatchSummary, MatchDetail } from "@/lib/domain/types";

export function MatchesShell({
  matches,
  selectedMatchId,
  detail,
  clubId,
}: {
  matches: MatchSummary[];
  selectedMatchId: string | null;
  detail: MatchDetail | null;
  clubId: string;
}) {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [mobileShowDetail, setMobileShowDetail] = useState(!!searchParams.get("match"));

  const handleSelect = useCallback(
    (matchId: string) => {
      router.push(`/clubs/${clubId}/matches?match=${matchId}`, { scroll: false });
      setMobileShowDetail(true);
    },
    [router, clubId]
  );

  const handleBack = useCallback(() => {
    setMobileShowDetail(false);
  }, []);

  return (
    <div className="lg:grid lg:grid-cols-[minmax(280px,360px)_minmax(0,1fr)] lg:gap-6">
      {/* Left: match archive */}
      <div className={`${mobileShowDetail ? "hidden" : "block"} lg:block`}>
        <div className="flex items-baseline justify-between mb-4">
          <h1 className="text-xl font-bold text-text-primary">Arquivo de Partidas</h1>
          <span className="text-xs text-muted">{matches.length} partidas</span>
        </div>
        <div className="lg:sticky lg:top-[18px] lg:max-h-[calc(100vh-100px)] lg:overflow-y-auto space-y-0.5 pr-1">
          {matches.map((m) => {
            const isActive = m.matchId === selectedMatchId;
            return (
              <button
                key={m.matchId}
                onClick={() => handleSelect(m.matchId)}
                className={`w-full text-left flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm transition-colors ${
                  isActive
                    ? "shadow-[inset_3px_0_var(--color-accent)] bg-accent/8"
                    : "hover:bg-surface-raised"
                }`}
              >
                <OutcomeBadge outcome={m.outcome} />
                <div className="flex-1 min-w-0">
                  <p className="text-text-primary font-medium truncate">
                    {m.ourClubName ?? "Nós"} {m.ourScore} × {m.opponentScore}{" "}
                    {m.opponentClubName ?? "Adversário"}
                  </p>
                  <p className="text-xs text-muted">{formatDate(m.playedAt)}</p>
                </div>
              </button>
            );
          })}
          {matches.length === 0 && (
            <p className="text-sm text-muted text-center py-8">Nenhuma partida encontrada.</p>
          )}
        </div>
      </div>

      {/* Right: detail */}
      <div className={`${!mobileShowDetail ? "hidden" : "block"} lg:block`}>
        {mobileShowDetail && (
          <button
            onClick={handleBack}
            className="flex items-center gap-1.5 text-sm text-accent mb-4 lg:hidden"
          >
            <ArrowLeft className="w-4 h-4" />
            Todas as partidas
          </button>
        )}
        {detail ? (
          <MatchDetailView match={detail} clubId={clubId} />
        ) : (
          <p className="text-muted text-sm text-center py-12">Nenhuma partida disponível.</p>
        )}
      </div>
    </div>
  );
}
