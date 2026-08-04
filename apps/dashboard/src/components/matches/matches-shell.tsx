"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { useState, useCallback } from "react";
import { MatchDetailView } from "@/components/matches/match-detail-view";
import { formatDate } from "@/lib/utils";
import { ArrowLeft } from "lucide-react";
import type { MatchSummary, MatchDetail } from "@/lib/domain/types";

/* ------------------------------------------------------------------ */
/*  ListOutcomeBadge – pill-shaped badge for the match list            */
/* ------------------------------------------------------------------ */

const outcomeConfig = {
  WIN:  { label: "Vitória", className: "text-outcome-win border-outcome-win" },
  DRAW: { label: "Empate",  className: "text-outcome-draw border-outcome-draw" },
  LOSS: { label: "Derrota", className: "text-outcome-loss border-outcome-loss" },
} as const;

function ListOutcomeBadge({ outcome }: { outcome: "WIN" | "DRAW" | "LOSS" }) {
  const cfg = outcomeConfig[outcome];
  return (
    <span
      className={`inline-flex items-center gap-[5px] px-[9px] py-[5px] border rounded-full text-[11px] font-[800] tracking-[0.04em] uppercase leading-none ${cfg.className}`}
    >
      {cfg.label}
    </span>
  );
}

/* ------------------------------------------------------------------ */
/*  MatchesShell                                                       */
/* ------------------------------------------------------------------ */

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
    <div>
      {/* Page header */}
      <header className="mb-6">
        <div className="text-[11px] uppercase tracking-[0.09em] text-muted">
          A trajetória do clube, jogo a jogo
        </div>
        <h1 className="text-[clamp(26px,3vw,36px)] font-bold text-text-primary leading-tight">
          Partidas
        </h1>
        <p className="text-muted mt-[7px]">
          Cada resultado guarda personagens, histórias e evidências.
        </p>
      </header>

      {/* Two-column layout */}
      <div className="lg:grid lg:grid-cols-[minmax(280px,360px)_minmax(0,1fr)] gap-[18px]">
        {/* Left: match archive panel */}
        <aside className={`${mobileShowDetail ? "hidden" : "block"} lg:block`}>
          <div className="border border-border rounded-[13px] bg-surface overflow-hidden lg:sticky lg:top-[18px]">
            {/* Panel header */}
            <div className="px-[16px] py-[15px] border-b border-border">
              <h2 className="text-[15px] font-semibold text-text-primary">Arquivo de partidas</h2>
              <div className="text-[12px] text-muted">{matches.length} partidas registradas</div>
            </div>

            {/* Match list */}
            <div className="max-h-[calc(100vh-174px)] overflow-y-auto">
              {matches.map((m) => {
                const isActive = m.matchId === selectedMatchId;
                return (
                  <button
                    key={m.matchId}
                    onClick={() => handleSelect(m.matchId)}
                    className={`match-list-item w-full text-left px-[16px] py-[10px] transition-colors ${
                      isActive
                        ? "bg-surface-raised shadow-[inset_3px_0_var(--color-accent)]"
                        : "hover:bg-surface-raised"
                    }`}
                  >
                    {/* Row 1: date + outcome badge */}
                    <div className="flex items-center justify-between mb-[4px]">
                      <span className="text-[12px] text-muted">{formatDate(m.playedAt)}</span>
                      <ListOutcomeBadge outcome={m.outcome} />
                    </div>

                    {/* Row 2: clubs + score */}
                    <div className="flex items-center justify-between">
                      <div className="min-w-0 flex-1">
                        <p className="text-[13px] text-text-primary font-medium truncate">
                          {m.ourClubName ?? "Nós"}
                        </p>
                        <p className="text-[13px] text-text-primary truncate">
                          {m.opponentClubName ?? "Adversário"}
                        </p>
                      </div>
                      <span className="text-[19px] font-[850] text-text-primary tabular-nums ml-3">
                        {m.ourScore} - {m.opponentScore}
                      </span>
                    </div>

                    {/* Row 3: competition (optional) */}
                    {m.matchType && (
                      <p className="text-[12px] text-muted mt-[2px]">{m.matchType}</p>
                    )}
                  </button>
                );
              })}
              {matches.length === 0 && (
                <p className="text-sm text-muted text-center py-8">Nenhuma partida encontrada.</p>
              )}
            </div>
          </div>
        </aside>

        {/* Right: detail panel */}
        <section className={`${!mobileShowDetail ? "hidden" : "block"} lg:block`}>
          {/* Mobile back button */}
          {mobileShowDetail && (
            <button
              onClick={handleBack}
              className="flex items-center gap-1.5 text-sm text-accent mb-4 lg:hidden"
            >
              <ArrowLeft className="w-4 h-4" />
              Todas as partidas
            </button>
          )}

          <div className="border border-border rounded-[13px] bg-surface overflow-hidden min-h-[620px]">
            {detail ? (
              <div className="p-[clamp(18px,3vw,30px)]">
                <MatchDetailView match={detail} clubId={clubId} />
              </div>
            ) : (
              <p className="text-muted text-sm text-center py-12">Nenhuma partida disponível.</p>
            )}
          </div>
        </section>
      </div>
    </div>
  );
}
