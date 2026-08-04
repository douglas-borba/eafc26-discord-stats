import { OutcomeBadge } from "@/components/ui/outcome-badge";
import { formatDate, outcomeColor } from "@/lib/utils";
import type { OpponentHistory } from "@/lib/domain/types";

function outcomeLabel(outcome: "WIN" | "DRAW" | "LOSS") {
  return outcome === "WIN" ? "Vitória" : outcome === "DRAW" ? "Empate" : "Derrota";
}

export function OpponentHistoryView({ history, clubId }: { history: OpponentHistory; clubId: string }) {
  const goalDiff = history.goalsFor - history.goalsAgainst;
  const lastMatch = history.matches[0] ?? null;
  const winRate = history.matchesPlayed > 0
    ? Math.round((history.wins / history.matchesPlayed) * 100)
    : 0;

  return (
    <div>
      {/* Versus header */}
      <header
        style={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "flex-start",
          gap: 16,
          flexWrap: "wrap",
          marginBottom: 24,
        }}
      >
        <div>
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
            Histórico do confronto
          </span>
          <h2
            style={{
              fontSize: "clamp(1.7rem, 4vw, 3rem)",
              lineHeight: 1.06,
              letterSpacing: "-.035em",
              fontWeight: 800,
              margin: 0,
            }}
          >
            Associação BF &times; {history.clubName ?? history.clubId}
          </h2>
          <div className="text-muted" style={{ fontSize: 13, marginTop: 6 }}>
            {history.matchesPlayed} confrontos
          </div>
        </div>
        <div style={{ display: "flex", gap: 16, alignItems: "baseline" }}>
          <strong className="text-2xl font-black text-win">
            {history.wins}V
          </strong>
          <strong className="text-2xl font-black text-draw">
            {history.draws}E
          </strong>
          <strong className="text-2xl font-black text-loss">
            {history.losses}D
          </strong>
          <span className="text-muted" style={{ fontSize: 13 }}>
            Aproveitamento: <strong>{winRate}%</strong>
          </span>
        </div>
      </header>

      {/* Summary strip */}
      <div
        className="summary-strip"
        style={{
          display: "grid",
          gridTemplateColumns: "repeat(4, 1fr)",
          borderTop: "1px solid var(--color-border)",
          borderBottom: "1px solid var(--color-border)",
          marginBottom: 28,
        }}
      >
        {[
          { label: "gols marcados", value: history.goalsFor },
          { label: "gols sofridos", value: history.goalsAgainst },
          {
            label: "saldo",
            value: `${goalDiff > 0 ? "+" : ""}${goalDiff}`,
            color: goalDiff > 0 ? "var(--color-win)" : goalDiff < 0 ? "var(--color-loss)" : "var(--color-draw)",
          },
          { label: "confrontos", value: history.matchesPlayed },
        ].map((item) => (
          <div
            key={item.label}
            style={{
              padding: "18px 12px",
              textAlign: "center",
              borderLeft: "1px solid var(--color-border)",
            }}
          >
            <div className="text-muted" style={{ fontSize: 11, textTransform: "uppercase", letterSpacing: ".08em", fontWeight: 700, marginBottom: 4 }}>
              {item.label}
            </div>
            <div style={{ fontSize: "1.45rem", fontWeight: 800, color: item.color }}>
              {item.value}
            </div>
          </div>
        ))}
      </div>

      {/* Section 1: Last match */}
      {lastMatch && (
        <section style={{ marginBottom: 28 }}>
          <div style={{ marginBottom: 12 }}>
            <span className="text-muted" style={{ fontSize: 11, textTransform: "uppercase", letterSpacing: ".08em", fontWeight: 700 }}>
              O encontro mais recente
            </span>
            <h3 style={{ fontSize: "1.1rem", fontWeight: 700, margin: "4px 0 0" }}>
              Último Confronto Registrado
            </h3>
          </div>
          <a
            href={`/clubs/${clubId}/matches?match=${lastMatch.matchId}`}
            style={{
              display: "block",
              padding: "16px 18px",
              background: "var(--color-surface-raised)",
              borderRadius: 10,
              textDecoration: "none",
              color: "inherit",
              transition: "background .15s",
            }}
          >
            <div style={{ display: "flex", alignItems: "center", gap: 14 }}>
              <OutcomeBadge outcome={lastMatch.outcome} className="w-9 h-9 text-sm" />
              <div style={{ flex: 1, minWidth: 0 }}>
                <p style={{ fontSize: 14, margin: 0, fontWeight: 600 }}>
                  {lastMatch.ourClubName ?? "Nós"} vs {lastMatch.opponentClubName ?? "Adversário"}
                </p>
                <p className="text-muted" style={{ fontSize: 12, margin: "2px 0 0" }}>
                  {formatDate(lastMatch.playedAt)} &middot; {lastMatch.matchType ?? "Partida"}
                </p>
              </div>
              <span
                style={{ fontSize: "1.6rem", fontWeight: 900, whiteSpace: "nowrap" }}
                className={outcomeColor(lastMatch.outcome)}
              >
                {lastMatch.ourScore} &times; {lastMatch.opponentScore}
              </span>
            </div>
          </a>
        </section>
      )}

      {/* Section 2: Sequences (data unavailable) */}
      <section style={{ marginBottom: 28 }}>
        <div style={{ marginBottom: 12 }}>
          <span className="text-muted" style={{ fontSize: 11, textTransform: "uppercase", letterSpacing: ".08em", fontWeight: 700 }}>
            Confronto em sequência
          </span>
          <h3 style={{ fontSize: "1.1rem", fontWeight: 700, margin: "4px 0 0" }}>
            Sequências
          </h3>
        </div>
        <p className="text-muted" style={{ fontSize: 13, padding: "12px 0" }}>
          Dados de sequência indisponíveis nesta versão.
        </p>
      </section>

      {/* Section 3: Individual production (data unavailable) */}
      <section style={{ marginBottom: 28 }}>
        <div style={{ marginBottom: 12 }}>
          <span className="text-muted" style={{ fontSize: 11, textTransform: "uppercase", letterSpacing: ".08em", fontWeight: 700 }}>
            Produção individual
          </span>
          <h3 style={{ fontSize: "1.1rem", fontWeight: 700, margin: "4px 0 0" }}>
            Jogadores contra este clube
          </h3>
        </div>
        <p className="text-muted" style={{ fontSize: 13, padding: "12px 0" }}>
          Indisponível nesta versão.
        </p>
      </section>

      {/* Section 4: Full history */}
      <section style={{ marginBottom: 28 }}>
        <div style={{ marginBottom: 12 }}>
          <span className="text-muted" style={{ fontSize: 11, textTransform: "uppercase", letterSpacing: ".08em", fontWeight: 700 }}>
            Partida por partida
          </span>
          <h3 style={{ fontSize: "1.1rem", fontWeight: 700, margin: "4px 0 0" }}>
            Histórico completo
          </h3>
        </div>
        <div>
          {history.matches.map((m) => (
            <a
              key={m.matchId}
              href={`/clubs/${clubId}/matches?match=${m.matchId}`}
              className="history-row"
              style={{
                display: "grid",
                gridTemplateColumns: "auto 1fr auto auto",
                gap: "0 14px",
                alignItems: "center",
                padding: "12px 14px",
                borderBottom: "1px solid var(--color-border)",
                textDecoration: "none",
                color: "inherit",
                transition: "background .12s",
              }}
            >
              <span className="text-muted" style={{ fontSize: 12, whiteSpace: "nowrap" }}>
                {formatDate(m.playedAt)}
              </span>
              <span style={{ display: "flex", alignItems: "center", gap: 8 }}>
                <OutcomeBadge outcome={m.outcome} />
                <span style={{ fontSize: 13, fontWeight: 600 }}>{outcomeLabel(m.outcome)}</span>
              </span>
              <span className="text-muted history-match-type" style={{ fontSize: 12 }}>
                {m.matchType ?? ""}
              </span>
              <span
                style={{ fontSize: "1.05rem", fontWeight: 800, whiteSpace: "nowrap", textAlign: "right" }}
                className={outcomeColor(m.outcome)}
              >
                {m.ourScore} &times; {m.opponentScore}
              </span>
            </a>
          ))}
        </div>
      </section>

      {/* Audit disclosure */}
      <details
        style={{
          marginTop: 24,
          borderTop: "1px solid var(--color-border)",
          paddingTop: 16,
        }}
      >
        <summary
          className="text-muted"
          style={{ fontSize: 12, cursor: "pointer", userSelect: "none" }}
        >
          Ver comprovação do retrospecto
        </summary>
        <pre
          style={{
            fontFamily: "var(--font-mono)",
            fontSize: 11,
            marginTop: 10,
            padding: 12,
            background: "var(--color-bg)",
            borderRadius: 8,
            overflowX: "auto",
            whiteSpace: "pre-wrap",
          }}
          className="text-muted"
        >
{`clubId: ${history.clubId}
confrontos: ${history.matchesPlayed}
critério: partidas registradas na base da Associação BF`}
        </pre>
      </details>

      {/* Responsive styles */}
      <style>{`
        .history-row:hover {
          background: var(--color-surface-raised);
        }
        .summary-strip > div:first-child {
          border-left: none;
        }
        @media (max-width: 700px) {
          .summary-strip {
            grid-template-columns: repeat(2, 1fr) !important;
          }
          .history-row {
            grid-template-columns: 1fr auto !important;
          }
          .history-match-type {
            display: none !important;
          }
        }
      `}</style>
    </div>
  );
}
