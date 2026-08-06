"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState } from "react";
import { cn } from "@/lib/utils";
import { Shield, Circle, Users } from "lucide-react";
import type { SequenceEditorial } from "@/lib/services/sequence-editorial-service";
import type { MatchSummaryPresentation } from "@/lib/services/match-card-service";

const navItems = [
  { label: "Visão Geral", href: "overview", icon: Shield },
  { label: "Partidas", href: "matches", icon: Circle },
  { label: "Jogadores", href: "players", icon: Users },
  // { label: "Adversários", href: "opponents", icon: Swords }, // Temporariamente desabilitado
];

const resultDotColors = {
  WIN: "#3fb950",
  DRAW: "#d29922",
  LOSS: "#f85149",
} as const;

const outcomeLabels = { wins: "V", draws: "E", losses: "D" } as const;
const outcomeColors = { wins: "#3fb950", draws: "#8b949e", losses: "#f85149" } as const;

interface Props {
  clubId: string;
  editorial: SequenceEditorial;
  presentations: MatchSummaryPresentation[];
}

function PanelDivider() {
  return <div className="h-px bg-[#21262d] mx-[-20px] px-0" />;
}

export function OverviewClubPanel({ clubId, editorial, presentations }: Props) {
  const pathname = usePathname();
  const { stats } = editorial;
  const [hoveredMatch, setHoveredMatch] = useState<number | null>(null);

  return (
    <div className="flex flex-col h-full">
      {/* ── Bloco 1: Marca ── */}
      <div className="pb-5 mb-1">
        <div className="flex items-center gap-3">
          <div
            className="w-10 h-10 rounded-lg flex items-center justify-center text-white font-bold text-sm shrink-0"
            style={{ background: "linear-gradient(135deg, #2f81f7, #3fb950)" }}
          >
            FC
          </div>
          <div className="flex flex-col">
            <span className="font-semibold text-[#e6edf3] text-[15px] leading-tight">
              EA FC STATS
            </span>
            <span className="text-[0.72rem] text-[#6e7681] leading-tight mt-0.5">
              Associação BF
            </span>
          </div>
        </div>
      </div>

      <PanelDivider />

      {/* ── Bloco 2: Últimas 10 partidas ── */}
      <div className="py-5">
        <p className="text-[0.6rem] font-bold uppercase tracking-[0.1em] text-[#8b949e] mb-3">
          Últimas {editorial.matchDetails.length} partida{editorial.matchDetails.length !== 1 ? "s" : ""}
        </p>

        <div className="flex items-center gap-[9px] mb-2.5 flex-wrap relative">
          {editorial.matchDetails.map((match, idx) => (
            <div
              key={match.matchId}
              className="relative cursor-pointer transition-transform hover:scale-125 active:scale-110"
              onMouseEnter={() => setHoveredMatch(idx)}
              onMouseLeave={() => setHoveredMatch(null)}
              onClick={() => setHoveredMatch(hoveredMatch === idx ? null : idx)}
            >
              <div
                className="w-[14px] h-[14px] rounded-full"
                style={{ backgroundColor: resultDotColors[match.outcome] }}
              />
              {hoveredMatch === idx && (
                <div className="absolute z-50 left-1/2 -translate-x-1/2 bottom-[calc(100%+8px)] bg-[#161b22] border border-[#30363d] rounded-md px-2.5 py-1.5 text-[0.7rem] whitespace-nowrap shadow-lg pointer-events-none">
                  <div className="text-[#c9d1d9] font-medium mb-0.5">{match.date}</div>
                  <div className="text-[#8b949e]">{match.opponent}</div>
                  <div className="text-[#c9d1d9] font-semibold">{match.ourScore}×{match.oppScore}</div>
                  <div
                    className="absolute left-1/2 -translate-x-1/2 top-full w-0 h-0 border-l-4 border-r-4 border-t-4 border-transparent"
                    style={{ borderTopColor: "#30363d" }}
                  />
                </div>
              )}
            </div>
          ))}
        </div>

        {stats.matchCount > 0 && (
          <div className="flex items-center gap-1 text-[0.78rem]">
            {(["wins", "draws", "losses"] as const).map((key, i) => (
              <span key={key} className="flex items-center gap-1">
                {i > 0 && <span className="text-[#30363d] text-[0.7rem]">•</span>}
                <span className="font-semibold tabular-nums" style={{ color: outcomeColors[key] }}>
                  {stats[key]}
                </span>
                <span className="text-[#6e7681] text-[0.7rem]">
                  {outcomeLabels[key]}
                </span>
              </span>
            ))}
          </div>
        )}
      </div>

      <PanelDivider />

      {/* ── Bloco 3: Momento do Clube ── */}
      <div className="py-5 flex-1">
        <p className="text-[0.6rem] font-bold uppercase tracking-[0.1em] text-[#58a6ff] mb-3">
          Momento do Clube
        </p>

        <h2 className="text-[0.95rem] font-bold text-[#e6edf3] leading-tight mb-2.5">
          {editorial.title}
        </h2>

        <p className="text-[0.78rem] text-[#9da5b0] leading-[1.65] mb-5 max-w-[260px]">
          {editorial.aiNarrative ?? editorial.narrative}
        </p>

        {/* Estatísticas do Período */}
        {stats.matchCount > 0 && (
          <>
            <div className="flex flex-col gap-[5px] text-[0.73rem] mb-5">
              <StatRow label="Gols marcados" value={String(stats.goalsScored)} />
              <StatRow label="Gols sofridos" value={String(stats.goalsConceded)} />
              <StatRow
                label="Saldo"
                value={`${stats.goalDifference > 0 ? "+" : ""}${stats.goalDifference}`}
                color={stats.goalDifference > 0 ? "#3fb950" : stats.goalDifference < 0 ? "#f85149" : "#6e7681"}
              />
              <StatRow label="Média de gols" value={stats.avgGoalsScored} />
              <StatRow
                label="Aproveitamento"
                value={`${stats.pointsPercentage}%`}
                color={parseFloat(stats.pointsPercentage) >= 60 ? "#3fb950" : parseFloat(stats.pointsPercentage) >= 40 ? "#d29922" : "#f85149"}
              />
              {editorial.currentStreak && (
                <StatRow label="Sequência atual" value={editorial.currentStreak.label} />
              )}
            </div>
          </>
        )}

        {/* Destaques do Período */}
        {(editorial.topScorer || editorial.topAssister || editorial.topHighlight || editorial.topRatedPlayer) && (
          <>
            <div className="h-px bg-[#21262d] mb-4" />
            <div className="flex flex-col gap-2 text-[0.73rem]">
              {editorial.topScorer && (
                <div className="flex items-center gap-2">
                  <span className="text-[0.82rem] w-5 text-center shrink-0">⚽</span>
                  <span className="text-[#9da5b0]">
                    <span className="font-medium text-[#c9d1d9]">{editorial.topScorer.name}</span>
                    <span className="text-[#6e7681]"> — </span>
                    {editorial.topScorer.goals} {editorial.topScorer.goals === 1 ? "gol" : "gols"}
                  </span>
                </div>
              )}
              {editorial.topAssister && (
                <div className="flex items-center gap-2">
                  <span className="text-[0.82rem] w-5 text-center shrink-0">🎯</span>
                  <span className="text-[#9da5b0]">
                    <span className="font-medium text-[#c9d1d9]">{editorial.topAssister.name}</span>
                    <span className="text-[#6e7681]"> — </span>
                    {editorial.topAssister.assists} {editorial.topAssister.assists === 1 ? "assistência" : "assistências"}
                  </span>
                </div>
              )}
              {editorial.topHighlight && (
                <div className="flex items-center gap-2">
                  <span className="text-[0.82rem] w-5 text-center shrink-0">🥇</span>
                  <span className="text-[#9da5b0]">
                    <span className="font-medium text-[#c9d1d9]">{editorial.topHighlight.name}</span>
                    <span className="text-[#6e7681]"> — </span>
                    {editorial.topHighlight.appearances}× MVP
                  </span>
                </div>
              )}
              {editorial.topRatedPlayer && (
                <div className="flex items-center gap-2">
                  <span className="text-[0.82rem] w-5 text-center shrink-0">⭐</span>
                  <span className="text-[#9da5b0]">
                    <span className="font-medium text-[#c9d1d9]">{editorial.topRatedPlayer.name}</span>
                    <span className="text-[#6e7681]"> — </span>
                    média {editorial.topRatedPlayer.avgRating}
                  </span>
                </div>
              )}
            </div>
          </>
        )}
      </div>

      {/* ── Bloco 4: Navegação ── */}
      <div className="mt-auto pt-1">
        <PanelDivider />
        <nav className="flex flex-col gap-0.5 pt-5 pb-2">
          {navItems.map(({ label, href, icon: Icon }) => {
            const fullHref = `/clubs/${clubId}/${href}`;
            const isActive = pathname.startsWith(fullHref);
            return (
              <Link
                key={href}
                href={fullHref}
                className={cn(
                  "flex items-center gap-2.5 px-3 py-[0.45rem] rounded-md text-[0.78rem] font-medium transition-colors",
                  isActive
                    ? "bg-[#1c2128] text-[#58a6ff] shadow-[inset_3px_0_var(--color-accent)]"
                    : "text-[#8b949e] hover:text-[#e6edf3] hover:bg-[#161b22]"
                )}
              >
                <Icon className="w-4 h-4" strokeWidth={1.8} />
                {label}
              </Link>
            );
          })}
        </nav>
      </div>
    </div>
  );
}

function StatRow({ label, value, color }: { label: string; value: string; color?: string }) {
  return (
    <div className="flex justify-between items-baseline">
      <span className="text-[#6e7681]">{label}</span>
      <span className="font-semibold tabular-nums" style={{ color: color ?? "#c9d1d9" }}>{value}</span>
    </div>
  );
}
