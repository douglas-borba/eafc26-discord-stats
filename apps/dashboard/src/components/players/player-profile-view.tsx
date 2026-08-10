import { formatDate } from "@/lib/utils";
import type { PlayerProfile } from "@/lib/domain/types";

const OUTCOME_PILL: Record<"WIN" | "DRAW" | "LOSS", { color: string; bg: string; label: string }> = {
  WIN:  { color: "#3fb950", bg: "rgba(63,185,80,.12)",  label: "Vitória" },
  DRAW: { color: "#d29922", bg: "rgba(210,153,34,.12)", label: "Empate" },
  LOSS: { color: "#f85149", bg: "rgba(248,81,73,.12)",  label: "Derrota" },
};

export function PlayerProfileView({ profile, clubId, clubName }: { profile: PlayerProfile; clubId: string; clubName: string }) {
  const wins = profile.recentMatches.filter((m) => m.outcome === "WIN").length;
  const draws = profile.recentMatches.filter((m) => m.outcome === "DRAW").length;
  const losses = profile.recentMatches.filter((m) => m.outcome === "LOSS").length;

  const ratedCount = profile.recentMatches.filter((m) => m.rating != null).length;

  const stats: { label: string; value: string | number; emoji?: string }[] = [
    { label: "Partidas", value: profile.matchesPlayed },
    { label: "Média", value: profile.averageRating?.toFixed(2) ?? "—" },
    { label: "Gols", value: profile.totalGoals },
    { label: "Assistências", value: profile.totalAssists },
    { label: "Craques", value: profile.manOfTheMatchCount, emoji: "⭐" },
    { label: "Menor Desempenho", value: "—", emoji: "📉" },
    { label: "Xerifes", value: "—", emoji: "🛡️" },
    { label: "Vermelhos", value: profile.redCardCount, emoji: "🟥" },
  ];

  return (
    <div>
      <style>{`
        .profile-header { display: flex; justify-content: space-between; align-items: flex-end; margin-bottom: 24px; }
        .profile-stats { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; margin-bottom: 28px; }
        .match-row { display: grid; grid-template-columns: 130px minmax(0,1fr) auto; align-items: center; gap: 14px; padding: 12px 14px; }
        @media (max-width: 850px) {
          .profile-header { flex-direction: column; align-items: flex-start; }
          .profile-stats { grid-template-columns: repeat(2, 1fr); }
          .match-row { grid-template-columns: 1fr auto; }
          .match-date-col { grid-column: 1 / -1; }
        }
      `}</style>

      {/* Profile header */}
      <div className="profile-header">
        <div>
          <p
            style={{
              fontSize: 12,
              textTransform: "uppercase",
              letterSpacing: "0.08em",
              color: "var(--color-text-muted)",
              marginBottom: 4,
            }}
          >
            Perfil histórico
          </p>
          <h2
            style={{
              fontSize: 28,
              fontWeight: 700,
              color: "var(--color-text-primary)",
              margin: 0,
              lineHeight: 1.2,
            }}
          >
            {profile.displayName ?? profile.platformName ?? profile.playerId}
          </h2>
          <p style={{ fontSize: 13, color: "var(--color-text-muted)", marginTop: 4 }}>
            {ratedCount} partidas com nota
          </p>
        </div>
        <div style={{ display: "flex", gap: 6 }}>
          <RecordBadge outcome="WIN" value={wins} suffix="V" />
          <RecordBadge outcome="DRAW" value={draws} suffix="E" />
          <RecordBadge outcome="LOSS" value={losses} suffix="D" />
        </div>
      </div>

      {/* Stat grid */}
      <div className="profile-stats">
        {stats.map((s) => (
          <div
            key={s.label}
            style={{
              padding: 14,
              border: "1px solid var(--color-border)",
              borderRadius: 9,
              background: "var(--color-surface-raised)",
            }}
          >
            <p style={{ fontSize: 24, fontWeight: 700, color: "var(--color-text-primary)", margin: 0 }}>
              {s.value}
            </p>
            <p style={{ fontSize: 12, color: "var(--color-text-muted)", marginTop: 4 }}>
              {s.emoji ? `${s.emoji} ` : ""}{s.label}
            </p>
          </div>
        ))}
      </div>

      {/* Recent matches */}
      {profile.recentMatches.length > 0 && (
        <div>
          <h3
            style={{
              fontSize: 16,
              fontWeight: 700,
              color: "var(--color-text-primary)",
              marginBottom: 12,
            }}
          >
            Últimas partidas
          </h3>
          <div style={{ display: "grid", gap: 4 }}>
            {profile.recentMatches.map((m) => {
              const pill = OUTCOME_PILL[m.outcome];
              const ourClub = clubName;
              const opponent = m.opponentClubName ?? "Adversário";

              return (
                <a
                  key={m.matchId}
                  href={`/clubs/${clubId}/matches?match=${m.matchId}`}
                  style={{ textDecoration: "none", color: "inherit" }}
                >
                  <div
                    className="match-row"
                    style={{
                      border: "1px solid var(--color-border)",
                      borderRadius: 8,
                      background: "var(--color-surface-panel)",
                      transition: "background 0.15s",
                    }}
                    onMouseEnter={(e) => {
                      e.currentTarget.style.background = "var(--color-surface-raised)";
                    }}
                    onMouseLeave={(e) => {
                      e.currentTarget.style.background = "var(--color-surface-panel)";
                    }}
                  >
                    {/* Date column */}
                    <div className="match-date-col">
                      <span
                        style={{
                          color: pill.color,
                          fontWeight: 700,
                          fontSize: 13,
                        }}
                      >
                        {pill.label}
                      </span>
                      <p style={{ fontSize: 12, color: "var(--color-text-muted)", marginTop: 2 }}>
                        {formatDate(m.playedAt)}
                      </p>
                    </div>

                    {/* Middle: clubs + awards */}
                    <div style={{ minWidth: 0 }}>
                      <p style={{ fontWeight: 700, fontSize: 14, color: "var(--color-text-primary)", margin: 0 }}>
                        {ourClub} &times; {opponent}
                      </p>
                      <p style={{ fontSize: 12, color: "var(--color-text-muted)", marginTop: 2 }}>
                        {m.rating != null ? `Nota ${m.rating.toFixed(1)}` : "Sem nota"}
                        {" · "}{m.goals} G · {m.assists} A
                      </p>
                    </div>

                    {/* Score */}
                    <div
                      style={{
                        fontSize: 17,
                        fontWeight: 800,
                        color: "var(--color-text-primary)",
                        textAlign: "right",
                        whiteSpace: "nowrap",
                      }}
                    >
                      {m.ourScore}&times;{m.opponentScore}
                    </div>
                  </div>
                </a>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}

function RecordBadge({ outcome, value, suffix }: { outcome: "WIN" | "DRAW" | "LOSS"; value: number; suffix: string }) {
  return (
    <span
      style={{
        color: OUTCOME_PILL[outcome].color,
        background: OUTCOME_PILL[outcome].bg,
        padding: "5px 9px",
        borderRadius: 999,
        fontWeight: 700,
        fontSize: 13,
      }}
    >
      {value}{suffix}
    </span>
  );
}
