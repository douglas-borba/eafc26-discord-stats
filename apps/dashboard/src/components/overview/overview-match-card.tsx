/**
 * Overview Match Card
 *
 * Full replica of the Thymeleaf match card with all award sections,
 * editorial phrases, and exact styling. This is the protagonist element
 * of the overview page.
 */

import type { MatchSummaryPresentation } from "@/lib/services/match-card-service";

interface Props {
  presentation: MatchSummaryPresentation | null | undefined;
  simulated?: boolean;
}

const resultBadgeStyles = {
  WIN: {
    background: "rgba(63, 185, 80, 0.25)",
    color: "#3fb950",
  },
  DRAW: {
    background: "rgba(139, 148, 158, 0.25)",
    color: "#8b949e",
  },
  LOSS: {
    background: "rgba(248, 81, 73, 0.25)",
    color: "#f85149",
  },
};

export function OverviewMatchCard({ presentation }: Props) {
  if (!presentation) {
    return (
      <div className="text-center py-12 px-6 text-[#6e7681]">
        <div className="text-[2.5rem] mb-2.5 opacity-50">🎴</div>
        <div className="text-[0.85rem]">Nenhuma partida processada ainda</div>
      </div>
    );
  }

  const badgeStyle = resultBadgeStyles[presentation.outcome.type];

  return (
    <>

      {/* Card Canvas - centered with max-width */}
      <div className="max-w-[420px] mx-auto px-3">
        <div className="match-card match-summary-card">
          {/* Card Header */}
          <div
            className="card-header py-4 px-5 text-center"
            style={{
              background: "linear-gradient(135deg, rgba(88, 166, 255, 0.08), transparent)",
            }}
          >
            <div
              className="inline-block rounded-md text-[0.7rem] font-bold uppercase tracking-[0.05em] mb-2"
              style={{
                padding: "0.2rem 0.7rem",
                ...badgeStyle,
              }}
            >
              {presentation.outcome.label}
            </div>
            <div className="card-score-row flex items-center justify-center gap-3 my-[0.4rem]">
              <div className="card-team-name text-[0.85rem] font-medium text-[#e6edf3] max-w-[110px] text-center leading-[1.2]">
                {presentation.ourName}
              </div>
              <div className="card-score flex items-center gap-1 text-[1.75rem] font-[800] text-white">
                <span>{presentation.ourScore}</span>
                <span className="card-score-sep text-[#6e7681] text-[1.1rem] font-normal">×</span>
                <span>{presentation.oppScore}</span>
              </div>
              <div className="card-team-name text-[0.85rem] font-medium text-[#e6edf3] max-w-[110px] text-center leading-[1.2]">
                {presentation.oppName}
              </div>
            </div>
            <div className="card-date text-[0.7rem] text-muted mt-[0.4rem]">
              📅 {presentation.date}
            </div>
          </div>

          {/* Card Body */}
          <div className="card-body px-5 pb-4">
            {/* Goals & Assists - ONLY section with two columns */}
            {(presentation.goals || presentation.assists) && (
              <>
                <CardDivider />
                {presentation.goals && presentation.assists ? (
                  <div className="card-goals-assists grid grid-cols-[1fr_auto_1fr] gap-0">
                    <div className="pr-3">
                      <PlayerList
                        title="⚽ GOLS"
                        items={presentation.goals.scorers}
                        mapper={(s) => ({ name: s.name, stat: `×${s.count}` })}
                      />
                    </div>
                    <div
                      className="w-px"
                      style={{
                        background:
                          "linear-gradient(180deg, transparent 0%, #30363d 20%, #30363d 80%, transparent 100%)",
                      }}
                    />
                    <div className="pl-3">
                      <PlayerList
                        title="🎯 ASSISTÊNCIAS"
                        items={presentation.assists.assisters}
                        mapper={(a) => ({ name: a.name, stat: `×${a.count}` })}
                      />
                    </div>
                  </div>
                ) : presentation.goals ? (
                  <PlayerList
                    title="⚽ GOLS"
                    items={presentation.goals.scorers}
                    mapper={(s) => ({ name: s.name, stat: `×${s.count}` })}
                  />
                ) : presentation.assists ? (
                  <PlayerList
                    title="🎯 ASSISTÊNCIAS"
                    items={presentation.assists.assisters}
                    mapper={(a) => ({ name: a.name, stat: `×${a.count}` })}
                  />
                ) : null}
              </>
            )}

            {/* Highlights - compact stacked */}
            {presentation.highlights && (
              <>
                <CardDivider />
                <CardSection title="🥇 DESTAQUES">
                  <div className="card-top3 flex flex-col gap-1">
                    {presentation.highlights.top3.map((t) => (
                      <div key={t.name} className="card-top3-item flex items-center gap-2 text-[0.85rem]">
                        <span className="card-top3-medal text-[0.9rem] shrink-0 w-5 text-center">
                          {t.medal}
                        </span>
                        <span className="card-top3-name text-[#e6edf3] font-medium">{t.name}</span>
                        <span className="card-top3-rating text-[#7ee787] font-semibold">{t.rating}</span>
                      </div>
                    ))}
                  </div>
                  {presentation.highlights.teamAverage && (
                    <div className="card-team-avg text-[0.75rem] text-muted mt-[0.35rem]">
                      ⭐ Média: {presentation.highlights.teamAverage}
                    </div>
                  )}
                </CardSection>
              </>
            )}

            {/* Craque - full width stacked */}
            {presentation.craque && (
              <>
                <CardDivider />
                <Highlight
                  title="⭐ CRAQUE DA PARTIDA"
                  name={presentation.craque.name}
                  stats={presentation.craque.reason}
                  phrase={presentation.craque.phrase}
                />
              </>
            )}

            {/* Offensive Narratives - full width stacked */}
            {presentation.offensiveNarratives.map((n) => (
              <div key={n.name}>
                <CardDivider />
                <Highlight
                  title={`${n.emoji} ${n.title}`}
                  name={n.name}
                  stats={`${n.shots} chutes • ${n.goals} ${n.goals === 1 ? "gol" : "gols"}`}
                  phrase={n.message}
                />
              </div>
            ))}

            {/* Bagre - full width with clear structure */}
            {presentation.bagre && (
              <>
                <CardDivider />
                <CardSection title="🍍 BAGRE DA PARTIDA">
                  <div className="card-bagre flex flex-col gap-[0.2rem]">
                    <span className="card-bagre-name text-[0.95rem] font-semibold text-[#e6edf3]">
                      {presentation.bagre.name}
                    </span>
                    <span className="card-bagre-reason text-[0.78rem] text-muted leading-[1.4]">
                      Nota {presentation.bagre.rating} · {presentation.bagre.reason}
                    </span>
                    {(presentation.bagre.tackleStats || presentation.bagre.passStats) && (
                      <span className="card-bagre-stats text-[0.75rem] text-muted leading-[1.4]">
                        {[
                          presentation.bagre.tackleStats && `🛡️ Desarmes: ${presentation.bagre.tackleStats}`,
                          presentation.bagre.passStats && `📉 Passes: ${presentation.bagre.passStats}`,
                        ]
                          .filter(Boolean)
                          .join(" · ")}
                      </span>
                    )}
                    <span className="card-bagre-phrase text-[0.82rem] text-[#f0883e] italic leading-[1.4] mt-[0.2rem]">
                      "{presentation.bagre.phrase}"
                    </span>
                  </div>
                </CardSection>
              </>
            )}

            {/* Red Card */}
            {presentation.redCard && (
              <>
                <CardDivider />
                <Highlight
                  title="🟥 PERDEU A CABEÇA"
                  name={presentation.redCard.name}
                  stats="Cartão vermelho"
                  phrase={presentation.redCard.phrase}
                />
              </>
            )}

            {/* Xerife - full width stacked */}
            {presentation.xerife && (
              <>
                <CardDivider />
                <Highlight
                  title="🚧 XERIFE"
                  name={presentation.xerife.name}
                  stats={`${presentation.xerife.tacklesMade}/${presentation.xerife.tackleAttempts} desarmes (${presentation.xerife.successRate}%)`}
                  phrase={presentation.xerife.phrase}
                />
              </>
            )}

            {/* Passe Precisão */}
            {presentation.passePrecisao && (
              <>
                <CardDivider />
                <Highlight
                  title="🎯 PASSE PRECISÃO"
                  name={presentation.passePrecisao.name}
                  stats={`${presentation.passePrecisao.passesMade}/${presentation.passePrecisao.passAttempts} passes (${presentation.passePrecisao.accuracy}%)`}
                  phrase={presentation.passePrecisao.phrase}
                />
              </>
            )}

            {/* Correio Extraviado */}
            {presentation.correioExtraviado && (
              <>
                <CardDivider />
                <Highlight
                  title="📮 CORREIO EXTRAVIADO"
                  name={presentation.correioExtraviado.name}
                  stats={`📬 ${presentation.correioExtraviado.playerAccuracyPct}% de acerto · Média do time: ${presentation.correioExtraviado.teamAccuracyPct}% (-${presentation.correioExtraviado.deltaPct}%)`}
                  phrase={presentation.correioExtraviado.phrase}
                  variant="warning"
                />
              </>
            )}

            {/* Muralha */}
            {presentation.muralha && (
              <>
                <CardDivider />
                <Highlight
                  title="🧤 MURALHA"
                  name={presentation.muralha.name}
                  stats={`${presentation.muralha.saves} ${presentation.muralha.saves === 1 ? "defesa" : "defesas"} • ${presentation.muralha.goalsConceded} ${presentation.muralha.goalsConceded === 1 ? "gol sofrido" : "gols sofridos"}`}
                  phrase={presentation.muralha.phrase}
                />
              </>
            )}
          </div>

          {/* Card Footer */}
          <div className="card-footer py-[0.6rem] px-3 text-center text-[0.7rem] text-[#6e7681] bg-black/25 border-t border-[#30363d]">
            EA FC 26 — Associação BF
          </div>
        </div>
      </div>
    </>
  );
}

function CardDivider() {
  return (
    <div
      className="h-px my-3"
      style={{
        background: "linear-gradient(90deg, transparent, #30363d 20%, #30363d 80%, transparent)",
      }}
    />
  );
}

interface CardSectionProps {
  title: string;
  children: React.ReactNode;
}

function CardSection({ title, children }: CardSectionProps) {
  return (
    <div className="card-section mb-2">
      <div className="card-section-title text-[0.65rem] font-bold text-muted uppercase tracking-[0.06em] mb-[0.4rem]">
        {title}
      </div>
      {children}
    </div>
  );
}

interface PlayerListProps {
  title: string;
  items: Array<{ name: string; count: number }>;
  mapper: (item: { name: string; count: number }) => { name: string; stat: string };
}

function PlayerList({ title, items, mapper }: PlayerListProps) {
  return (
    <CardSection title={title}>
      <div className="card-player-list flex flex-col gap-[0.2rem]">
        {items.map((item) => {
          const { name, stat } = mapper(item);
          return (
            <div key={name} className="card-player-item flex items-baseline gap-[0.4rem] text-[0.82rem]">
              <span className="card-player-name text-[#e6edf3] font-medium">{name}</span>
              <span className="card-player-stat text-muted text-[0.75rem]">{stat}</span>
            </div>
          );
        })}
      </div>
    </CardSection>
  );
}

interface HighlightProps {
  title: string;
  name: string;
  stats: string;
  phrase: string;
  variant?: "warning";
}

function Highlight({ title, name, stats, phrase, variant }: HighlightProps) {
  return (
    <CardSection title={title}>
      <div className="card-highlight flex flex-col gap-[0.15rem]">
        <span className="card-hl-name text-[0.95rem] font-semibold text-[#e6edf3]">{name}</span>
        <span className="card-hl-stats text-[0.78rem] text-muted">{stats}</span>
        <span
          className="text-[0.82rem] italic leading-[1.4] mt-[0.3rem]"
          style={{
            color: variant === "warning" ? "#f0883e" : "#7ee787",
          }}
        >
          "{phrase}"
        </span>
      </div>
    </CardSection>
  );
}
