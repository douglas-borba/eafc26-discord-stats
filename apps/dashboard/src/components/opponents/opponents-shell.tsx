"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { useState, useCallback } from "react";
import { OpponentHistoryView } from "@/components/opponents/opponent-history-view";
import { ArrowLeft } from "lucide-react";
import type { OpponentSummary, OpponentHistory } from "@/lib/domain/types";

function outcomeLabel(outcome: "WIN" | "DRAW" | "LOSS") {
  return outcome === "WIN" ? "Vitória" : outcome === "DRAW" ? "Empate" : "Derrota";
}

export function OpponentsShell({
  opponents,
  selectedOpponentId,
  history,
  clubId,
}: {
  opponents: OpponentSummary[];
  selectedOpponentId: string | null;
  history: OpponentHistory | null;
  clubId: string;
}) {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [search, setSearch] = useState("");
  const [mobileShowDetail, setMobileShowDetail] = useState(!!searchParams.get("opponent"));

  const filtered = opponents.filter((o) => {
    if (!search) return true;
    const term = search.toLocaleLowerCase("pt-BR");
    return (o.clubName ?? "").toLocaleLowerCase("pt-BR").includes(term);
  });

  const handleSelect = useCallback(
    (opponentId: string) => {
      router.push(`/clubs/${clubId}/opponents?opponent=${opponentId}`, { scroll: false });
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
      <header style={{ marginBottom: 28 }}>
        <span
          style={{
            color: "#79c0ff",
            fontSize: ".72rem",
            fontWeight: 800,
            letterSpacing: ".1em",
            textTransform: "uppercase",
            display: "block",
            marginBottom: 4,
          }}
        >
          Confronto por confronto
        </span>
        <h1
          style={{
            fontSize: "clamp(2rem, 4vw, 3rem)",
            letterSpacing: "-.04em",
            fontWeight: 800,
            lineHeight: 1.1,
            margin: 0,
          }}
        >
          Adversários
        </h1>
        <p className="text-muted" style={{ marginTop: 6, fontSize: "0.92rem" }}>
          O retrospecto da Associação BF contra cada clube que encontrou em campo.
        </p>
      </header>

      {/* Grid layout */}
      <div
        className="lg:grid lg:grid-cols-[minmax(280px,350px)_minmax(0,1fr)] gap-[18px]"
      >
        {/* Left: opponent index */}
        <div
          className={`${mobileShowDetail ? "opponents-panel-hidden" : ""}`}
          style={{
            border: "1px solid var(--color-border)",
            borderRadius: 14,
            background: "var(--color-surface)",
            overflow: "hidden",
          }}
        >
          {/* Search */}
          <div style={{ padding: "14px 16px", borderBottom: "1px solid var(--color-border)" }}>
            <label
              htmlFor="opponent-search"
              style={{
                display: "block",
                fontSize: 12,
                marginBottom: 6,
              }}
              className="text-muted"
            >
              Buscar adversário
            </label>
            <input
              id="opponent-search"
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Nome do clube"
              style={{
                width: "100%",
                minHeight: 42,
                background: "var(--color-bg)",
                border: "1px solid var(--color-border)",
                borderRadius: 8,
                padding: "8px 12px",
                color: "var(--color-text-primary)",
                fontSize: 14,
                outline: "none",
              }}
            />
          </div>

          {/* Opponent list */}
          <div style={{ maxHeight: "calc(100vh - 280px)", overflowY: "auto" }}>
            {filtered.map((o) => {
              const isActive = o.clubId === selectedOpponentId;
              const isSingle = o.matchesPlayed === 1;

              return (
                <button
                  key={o.clubId}
                  onClick={() => handleSelect(o.clubId)}
                  className={isActive ? "shadow-[inset_3px_0_var(--color-accent)]" : ""}
                  style={{
                    width: "100%",
                    textAlign: "left",
                    display: "block",
                    padding: 17,
                    borderBottom: "1px solid var(--color-border)",
                    background: isActive ? "rgba(56,139,253,.09)" : "transparent",
                    cursor: "pointer",
                    border: "none",
                    borderBlockEnd: "1px solid var(--color-border)",
                    color: "inherit",
                    transition: "background .15s",
                  }}
                >
                  <h2 style={{ fontSize: "1rem", fontWeight: 600, margin: 0, overflowWrap: "anywhere" }}>
                    {o.clubName ?? o.clubId}
                  </h2>
                  {isSingle ? (
                    <div style={{ marginTop: 4 }}>
                      <p className="text-muted" style={{ fontSize: 12, margin: 0 }}>
                        Primeiro confronto registrado
                      </p>
                      {o.lastPlayedAt && (
                        <p className="text-muted" style={{ fontSize: 12, margin: 0, marginTop: 2 }}>
                          {outcomeLabel(o.wins > 0 ? "WIN" : o.draws > 0 ? "DRAW" : "LOSS")} &middot;{" "}
                          {new Date(o.lastPlayedAt).toLocaleDateString("pt-BR")}
                        </p>
                      )}
                    </div>
                  ) : (
                    <div style={{ marginTop: 4 }}>
                      <p style={{ fontSize: 12, margin: 0, fontWeight: 750 }}>
                        {o.matchesPlayed} confrontos &middot;{" "}
                        <span className="text-win">{o.wins}V</span> &middot;{" "}
                        <span className="text-draw">{o.draws}E</span> &middot;{" "}
                        <span className="text-loss">{o.losses}D</span>
                      </p>
                      <p className="text-muted" style={{ fontSize: 12, margin: 0, marginTop: 2 }}>
                        {o.goalsFor} gols marcados &middot; {o.goalsAgainst} sofridos
                      </p>
                      {o.lastPlayedAt && (
                        <p className="text-muted" style={{ fontSize: 12, margin: 0, marginTop: 2 }}>
                          Último: {outcomeLabel(o.wins > 0 ? "WIN" : o.draws > 0 ? "DRAW" : "LOSS")}
                        </p>
                      )}
                    </div>
                  )}
                </button>
              );
            })}
            {filtered.length === 0 && (
              <p className="text-muted" style={{ textAlign: "center", padding: "32px 16px", fontSize: 14 }}>
                Nenhum adversário encontrado.
              </p>
            )}
          </div>
        </div>

        {/* Right: history */}
        <div
          className={`${!mobileShowDetail ? "opponents-detail-hidden" : ""}`}
          style={{
            border: "1px solid var(--color-border)",
            borderRadius: 14,
            background: "var(--color-surface)",
            overflow: "hidden",
          }}
        >
          {mobileShowDetail && (
            <button
              onClick={handleBack}
              className="opponents-back-btn"
              style={{
                display: "none",
                alignItems: "center",
                gap: 6,
                fontSize: 14,
                color: "var(--color-accent)",
                background: "none",
                border: "none",
                cursor: "pointer",
                padding: "12px 16px",
              }}
            >
              <ArrowLeft style={{ width: 16, height: 16 }} />
              Todos os adversários
            </button>
          )}
          <div style={{ padding: "24px 20px" }}>
            {history ? (
              <OpponentHistoryView history={history} clubId={clubId} />
            ) : (
              <p className="text-muted" style={{ textAlign: "center", padding: "48px 0", fontSize: 14 }}>
                Selecione um adversário para ver o histórico.
              </p>
            )}
          </div>
        </div>
      </div>

      {/* Responsive styles */}
      <style>{`
        @media (max-width: 700px) {
          .opponents-layout {
            display: block !important;
          }
          .opponents-panel-hidden {
            display: none !important;
          }
          .opponents-detail-hidden {
            display: none !important;
          }
          .opponents-back-btn {
            display: flex !important;
          }
        }
      `}</style>
    </div>
  );
}
