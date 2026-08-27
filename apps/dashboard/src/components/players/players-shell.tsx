"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { useState, useCallback } from "react";
import { PlayerProfileView } from "@/components/players/player-profile-view";
import { ArrowLeft } from "lucide-react";
import type { PlayerSummary, PlayerProfile } from "@/lib/domain/types";

function performanceBarColor(rating: number | null): { width: string; color: string } {
  if (rating == null) return { width: "0%", color: "transparent" };
  const pct = Math.max(0, Math.min(100, ((rating - 4) / 6) * 100));
  let color: string;
  if (rating >= 8.5) color = "#58a6ff";
  else if (rating >= 8) color = "#3fb950";
  else if (rating >= 7) color = "#7ee787";
  else if (rating >= 6) color = "#d29922";
  else color = "#f85149";
  return { width: `${pct}%`, color };
}

export function PlayersShell({
  players,
  selectedPlayerId,
  profile,
  clubId,
  clubName,
}: {
  players: PlayerSummary[];
  selectedPlayerId: string | null;
  profile: PlayerProfile | null;
  clubId: string;
  clubName: string;
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
    <div>
      {/* Page header */}
      <header style={{ marginBottom: 24 }}>
        <p
          style={{
            fontSize: 12,
            textTransform: "uppercase",
            letterSpacing: "0.08em",
            color: "var(--color-text-muted)",
            marginBottom: 4,
          }}
        >
          EA FC 26 — {clubName}
        </p>
        <h1 style={{ fontSize: 24, fontWeight: 700, color: "var(--color-text-primary)", margin: 0 }}>
          Perfis de Jogadores
        </h1>
      </header>

      {/* Two-column layout */}
      <div className="lg:grid lg:grid-cols-[minmax(260px,340px)_minmax(0,1fr)] gap-6">
        <style>{`
          .players-list-scroll {
            max-height: calc(100vh - 190px);
            overflow-y: auto;
          }
          @media (max-width: 1024px) {
            .players-list-scroll {
              max-height: 300px !important;
            }
          }
        `}</style>

        {/* Left panel */}
        <div
          className={`${mobileShowDetail ? "hidden" : "block"} lg:block`}
          style={{
            border: "1px solid var(--color-border)",
            borderRadius: 12,
            background: "var(--color-surface-panel)",
            overflow: "hidden",
          }}
        >
          {/* Panel header */}
          <div
            style={{
              padding: "14px 16px",
              borderBottom: "1px solid var(--color-border)",
              display: "flex",
              alignItems: "center",
              justifyContent: "space-between",
              gap: 12,
            }}
          >
            <h2 style={{ fontSize: 16, fontWeight: 700, color: "var(--color-text-primary)", margin: 0 }}>
              Jogadores
            </h2>
            <input
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Buscar..."
              style={{
                flex: 1,
                maxWidth: 180,
                padding: "6px 10px",
                fontSize: 13,
                border: "1px solid var(--color-border)",
                borderRadius: 6,
                background: "var(--color-surface-raised)",
                color: "var(--color-text-primary)",
                outline: "none",
              }}
            />
          </div>

          {/* Player list */}
          <div
            className="players-list-scroll"
            style={{ maxHeight: "calc(100vh - 190px)", overflowY: "auto" }}
          >
            {filtered.map((p) => {
              const isActive = p.playerId === selectedPlayerId;
              const bar = performanceBarColor(p.averageRating);
              const name = p.displayName ?? p.platformName ?? p.playerId;

              return (
                <button
                  key={p.playerId}
                  onClick={() => handleSelect(p.playerId)}
                  className={isActive ? "shadow-[inset_3px_0_var(--color-accent)]" : ""}
                  style={{
                    display: "grid",
                    gap: 7,
                    padding: "11px 16px",
                    width: "100%",
                    textAlign: "left",
                    border: "none",
                    cursor: "pointer",
                    background: isActive ? "var(--color-surface-raised)" : "transparent",
                    transition: "background 0.15s",
                  }}
                  onMouseEnter={(e) => {
                    if (!isActive) e.currentTarget.style.background = "var(--color-surface-raised)";
                  }}
                  onMouseLeave={(e) => {
                    if (!isActive) e.currentTarget.style.background = "transparent";
                  }}
                >
                  {/* Row 1: name + rating */}
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                    <span
                      style={{
                        fontWeight: 600,
                        fontSize: 14,
                        color: "var(--color-text-primary)",
                        overflow: "hidden",
                        textOverflow: "ellipsis",
                        whiteSpace: "nowrap",
                        minWidth: 0,
                      }}
                    >
                      {name}
                    </span>
                    {p.averageRating != null && (
                      <span
                        style={{
                          fontSize: 15,
                          fontWeight: 800,
                          fontVariantNumeric: "tabular-nums",
                          color: "var(--color-text-primary)",
                          marginLeft: 8,
                          flexShrink: 0,
                        }}
                      >
                        {p.averageRating.toFixed(2)}
                      </span>
                    )}
                  </div>

                  {/* Row 2: performance bar */}
                  <div
                    style={{
                      height: 4,
                      borderRadius: 2,
                      background: "var(--color-border)",
                      overflow: "hidden",
                    }}
                  >
                    <div
                      style={{
                        height: "100%",
                        width: bar.width,
                        background: bar.color,
                        borderRadius: 2,
                        transition: "width 0.3s",
                      }}
                    />
                  </div>

                  {/* Row 3: meta */}
                  <div
                    style={{
                      display: "flex",
                      justifyContent: "space-between",
                      alignItems: "center",
                      fontSize: 12,
                      color: "var(--color-text-muted)",
                    }}
                  >
                    <span style={{ overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap", minWidth: 0 }}>
                      {p.matchesPlayed} partidas
                    </span>
                  </div>
                </button>
              );
            })}
          </div>
        </div>

        {/* Right panel */}
        <div
          className={`${!mobileShowDetail ? "hidden" : "block"} lg:block`}
          style={{
            border: "1px solid var(--color-border)",
            borderRadius: 12,
            background: "var(--color-surface-panel)",
            minHeight: 560,
            padding: 20,
          }}
        >
          {mobileShowDetail && (
            <button
              onClick={handleBack}
              className="lg:hidden"
              style={{
                display: "flex",
                alignItems: "center",
                gap: 6,
                fontSize: 14,
                color: "var(--color-accent)",
                background: "none",
                border: "none",
                cursor: "pointer",
                marginBottom: 16,
              }}
            >
              <ArrowLeft style={{ width: 16, height: 16 }} />
              Todos os jogadores
            </button>
          )}
          {profile ? (
            <PlayerProfileView profile={profile} />
          ) : (
            <div
              style={{
                display: "grid",
                placeItems: "center",
                minHeight: 500,
                color: "var(--color-text-muted)",
                fontSize: 14,
              }}
            >
              <div style={{ textAlign: "center" }}>
                <div style={{ fontSize: 48, marginBottom: 8 }}>👤</div>
                <p>Selecione um jogador.</p>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
