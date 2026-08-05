import type { MatchSummaryPresentation } from "@/lib/services/match-card-service";

interface Props {
  presentation: MatchSummaryPresentation | null | undefined;
  simulated?: boolean;
  variant?: "full" | "compact";
  isLatest?: boolean;
}

const ribbonStyles = {
  WIN: { bg: "var(--color-win)", color: "#fff" },
  DRAW: { bg: "var(--color-draw)", color: "#fff" },
  LOSS: { bg: "var(--color-loss)", color: "#fff" },
};

export function OverviewMatchCard({ presentation, variant = "full", isLatest = false }: Props) {
  const c = variant === "compact";

  if (!presentation) {
    return (
      <div className={`text-center ${c ? "py-8 px-4" : "py-12 px-6"} text-[#6e7681]`}>
        <div className={`${c ? "text-[2rem]" : "text-[2.5rem]"} mb-2.5 opacity-50`}>🎴</div>
        <div className={`${c ? "text-[0.78rem]" : "text-[0.85rem]"}`}>Nenhuma partida processada ainda</div>
      </div>
    );
  }

  const ribbon = ribbonStyles[presentation.outcome.type];
  const divMy = c ? "my-[0.45rem]" : "my-3";
  const px = c ? "px-4" : "px-5";
  const sectionMb = c ? "mb-[0.35rem]" : "mb-2";
  const sectionTitleCls = c
    ? "text-[0.6rem] mb-[0.3rem]"
    : "text-[0.65rem] mb-[0.4rem]";
  const textSm = c ? "text-[0.76rem]" : "text-[0.82rem]";
  const textXs = c ? "text-[0.68rem]" : "text-[0.75rem]";
  const nameLg = c ? "text-[0.88rem]" : "text-[0.95rem]";

  return (
    <div className={c ? "" : "max-w-[420px] mx-auto px-3"}>
      <div
        className={`match-card match-summary-card rounded-[10px] match-card-elevated ${isLatest ? "match-card-featured" : ""}`}
      >
        {/* Header with vertical ribbon */}
        <div
          className="card-header grid overflow-hidden"
          style={{
            gridTemplateColumns: c ? "26px 1fr" : "32px 1fr",
            background: "linear-gradient(135deg, rgba(88, 166, 255, 0.08), transparent)",
          }}
        >
          {/* Ribbon */}
          <div
            className="flex items-center justify-center"
            style={{
              background: ribbon.bg,
              color: ribbon.color,
              writingMode: "vertical-rl",
              textOrientation: "mixed",
              fontSize: c ? "0.52rem" : "0.6rem",
              fontWeight: 800,
              letterSpacing: "0.08em",
              textTransform: "uppercase",
            }}
          >
            {presentation.outcome.label}
          </div>

          {/* Score area */}
          <div className={`${c ? "py-3 px-3" : "py-4 px-4"} text-center`}>
            <div className={`card-score-row flex items-center justify-center ${c ? "gap-2" : "gap-3"}`}>
              <div className={`card-team-name ${c ? "text-[0.78rem] max-w-[90px]" : "text-[0.85rem] max-w-[110px]"} font-medium text-[#e6edf3] text-center leading-[1.2]`}>
                {presentation.ourName}
              </div>
              <div className={`card-score flex items-center gap-0.5 ${c ? "text-[1.5rem]" : "text-[1.75rem]"} font-[800] text-white`}>
                <span>{presentation.ourScore}</span>
                <span className={`card-score-sep text-[#6e7681] ${c ? "text-[0.95rem]" : "text-[1.1rem]"} font-normal`}>×</span>
                <span>{presentation.oppScore}</span>
              </div>
              <div className={`card-team-name ${c ? "text-[0.78rem] max-w-[90px]" : "text-[0.85rem] max-w-[110px]"} font-medium text-[#e6edf3] text-center leading-[1.2]`}>
                {presentation.oppName}
              </div>
            </div>
            <div className={`card-date ${c ? "text-[0.62rem]" : "text-[0.7rem]"} text-muted ${c ? "mt-[0.25rem]" : "mt-[0.4rem]"}`}>
              📅 {presentation.date}
            </div>
          </div>
        </div>

        {/* Body */}
        <div className={`card-body ${px} ${c ? "pb-3" : "pb-4"}`}>
          {/* Goals & Assists */}
          {(presentation.goals || presentation.assists) && (
            <>
              <Divider my={divMy} />
              {presentation.goals && presentation.assists ? (
                <div className="card-goals-assists grid grid-cols-[1fr_auto_1fr] gap-0">
                  <div className="pr-2.5">
                    <Section title="⚽ GOLS" mb={sectionMb} titleCls={sectionTitleCls}>
                      <PList items={presentation.goals.scorers} mapper={(s) => ({ name: s.name, stat: `×${s.count}` })} textCls={textSm} statCls={textXs} />
                    </Section>
                  </div>
                  <div className="w-px" style={{ background: "linear-gradient(180deg, transparent 0%, #30363d 20%, #30363d 80%, transparent 100%)" }} />
                  <div className="pl-2.5">
                    <Section title="🎯 ASSISTÊNCIAS" mb={sectionMb} titleCls={sectionTitleCls}>
                      <PList items={presentation.assists.assisters} mapper={(a) => ({ name: a.name, stat: `×${a.count}` })} textCls={textSm} statCls={textXs} />
                    </Section>
                  </div>
                </div>
              ) : presentation.goals ? (
                <Section title="⚽ GOLS" mb={sectionMb} titleCls={sectionTitleCls}>
                  <PList items={presentation.goals.scorers} mapper={(s) => ({ name: s.name, stat: `×${s.count}` })} textCls={textSm} statCls={textXs} />
                </Section>
              ) : presentation.assists ? (
                <Section title="🎯 ASSISTÊNCIAS" mb={sectionMb} titleCls={sectionTitleCls}>
                  <PList items={presentation.assists.assisters} mapper={(a) => ({ name: a.name, stat: `×${a.count}` })} textCls={textSm} statCls={textXs} />
                </Section>
              ) : null}
            </>
          )}

          {/* Highlights */}
          {presentation.highlights && (
            <>
              <Divider my={divMy} />
              <Section title="🥇 DESTAQUES" mb={sectionMb} titleCls={sectionTitleCls}>
                <div className={`card-top3 flex flex-col ${c ? "gap-[0.15rem]" : "gap-1"}`}>
                  {presentation.highlights.top3.map((t) => (
                    <div key={t.name} className={`card-top3-item flex items-center ${c ? "gap-1.5" : "gap-2"} ${textSm}`}>
                      <span className={`card-top3-medal ${c ? "text-[0.82rem] w-[1.1rem]" : "text-[0.9rem] w-5"} shrink-0 text-center`}>{t.medal}</span>
                      <span className="card-top3-name text-[#e6edf3] font-medium">{t.name}</span>
                      <span className="card-top3-rating text-[#7ee787] font-semibold">{t.rating}</span>
                    </div>
                  ))}
                </div>
                {presentation.highlights.teamAverage && (
                  <div className={`card-team-avg ${textXs} text-muted ${c ? "mt-[0.2rem]" : "mt-[0.35rem]"}`}>
                    ⭐ Média: {presentation.highlights.teamAverage}
                  </div>
                )}
              </Section>
            </>
          )}

          {/* Craque */}
          {presentation.craque && (
            <>
              <Divider my={divMy} />
              <HighlightBlock
                title="⭐ CRAQUE DA PARTIDA"
                name={presentation.craque.name}
                stats={presentation.craque.reason}
                phrase={presentation.craque.phrase}
                sectionMb={sectionMb} titleCls={sectionTitleCls} nameCls={nameLg} textCls={textSm} statCls={textXs}
                c={c}
              />
            </>
          )}

          {/* Offensive Narratives */}
          {presentation.offensiveNarratives.map((n) => (
            <div key={n.name}>
              <Divider my={divMy} />
              <HighlightBlock
                title={`${n.emoji} ${n.title}`}
                name={n.name}
                stats={`${n.shots} chutes • ${n.goals} ${n.goals === 1 ? "gol" : "gols"}`}
                phrase={n.message}
                sectionMb={sectionMb} titleCls={sectionTitleCls} nameCls={nameLg} textCls={textSm} statCls={textXs}
                c={c}
              />
            </div>
          ))}

          {/* Bagre */}
          {presentation.bagre && (
            <>
              <Divider my={divMy} />
              <Section title="🍍 BAGRE DA PARTIDA" mb={sectionMb} titleCls={sectionTitleCls}>
                <div className={`card-bagre flex flex-col ${c ? "gap-[0.05rem]" : "gap-[0.2rem]"}`}>
                  <span className={`card-bagre-name ${nameLg} font-semibold text-[#e6edf3]`}>{presentation.bagre.name}</span>
                  <span className={`card-bagre-reason ${textXs} text-muted leading-[1.35]`}>
                    Nota {presentation.bagre.rating} · {presentation.bagre.reason}
                  </span>
                  {(presentation.bagre.tackleStats || presentation.bagre.passStats) && (
                    <span className={`card-bagre-stats ${textXs} text-muted leading-[1.35]`}>
                      {[
                        presentation.bagre.tackleStats && `🛡️ Desarmes: ${presentation.bagre.tackleStats}`,
                        presentation.bagre.passStats && `📉 Passes: ${presentation.bagre.passStats}`,
                      ].filter(Boolean).join(" · ")}
                    </span>
                  )}
                  <span className={`card-bagre-phrase ${textSm} text-[#f0883e] italic leading-[1.35] ${c ? "mt-[0.1rem]" : "mt-[0.2rem]"}`}>
                    &ldquo;{presentation.bagre.phrase}&rdquo;
                  </span>
                </div>
              </Section>
            </>
          )}

          {/* Red Card */}
          {presentation.redCard && (
            <>
              <Divider my={divMy} />
              <HighlightBlock
                title="🟥 PERDEU A CABEÇA"
                name={presentation.redCard.name}
                stats="Cartão vermelho"
                phrase={presentation.redCard.phrase}
                sectionMb={sectionMb} titleCls={sectionTitleCls} nameCls={nameLg} textCls={textSm} statCls={textXs}
                c={c}
              />
            </>
          )}

          {/* Xerife */}
          {presentation.xerife && (
            <>
              <Divider my={divMy} />
              <HighlightBlock
                title="🚧 XERIFE"
                name={presentation.xerife.name}
                stats={`${presentation.xerife.tacklesMade}/${presentation.xerife.tackleAttempts} desarmes (${presentation.xerife.successRate}%)`}
                phrase={presentation.xerife.phrase}
                sectionMb={sectionMb} titleCls={sectionTitleCls} nameCls={nameLg} textCls={textSm} statCls={textXs}
                c={c}
              />
            </>
          )}

          {/* Passe Precisão */}
          {presentation.passePrecisao && (
            <>
              <Divider my={divMy} />
              <HighlightBlock
                title="🎯 PASSE PRECISÃO"
                name={presentation.passePrecisao.name}
                stats={`${presentation.passePrecisao.passesMade}/${presentation.passePrecisao.passAttempts} passes (${presentation.passePrecisao.accuracy}%)`}
                phrase={presentation.passePrecisao.phrase}
                sectionMb={sectionMb} titleCls={sectionTitleCls} nameCls={nameLg} textCls={textSm} statCls={textXs}
                c={c}
              />
            </>
          )}

          {/* Correio Extraviado */}
          {presentation.correioExtraviado && (
            <>
              <Divider my={divMy} />
              <HighlightBlock
                title="📮 CORREIO EXTRAVIADO"
                name={presentation.correioExtraviado.name}
                stats={`📬 ${presentation.correioExtraviado.playerAccuracyPct}% de acerto · Média do time: ${presentation.correioExtraviado.teamAccuracyPct}% (-${presentation.correioExtraviado.deltaPct}%)`}
                phrase={presentation.correioExtraviado.phrase}
                phraseColor="#f0883e"
                sectionMb={sectionMb} titleCls={sectionTitleCls} nameCls={nameLg} textCls={textSm} statCls={textXs}
                c={c}
              />
            </>
          )}

          {/* Muralha */}
          {presentation.muralha && (
            <>
              <Divider my={divMy} />
              <HighlightBlock
                title="🧤 MURALHA"
                name={presentation.muralha.name}
                stats={`${presentation.muralha.saves} ${presentation.muralha.saves === 1 ? "defesa" : "defesas"} • ${presentation.muralha.goalsConceded} ${presentation.muralha.goalsConceded === 1 ? "gol sofrido" : "gols sofridos"}`}
                phrase={presentation.muralha.phrase}
                sectionMb={sectionMb} titleCls={sectionTitleCls} nameCls={nameLg} textCls={textSm} statCls={textXs}
                c={c}
              />
            </>
          )}
        </div>

        {/* Footer */}
        <div className={`card-footer ${c ? "py-[0.35rem] px-3 text-[0.6rem]" : "py-[0.6rem] px-3 text-[0.7rem]"} text-center text-[#6e7681] bg-black/25 border-t border-[#30363d]`}>
          EA FC 26 — Associação BF
        </div>
      </div>
    </div>
  );
}

function Divider({ my }: { my: string }) {
  return (
    <div className={`h-px ${my}`} style={{ background: "linear-gradient(90deg, transparent, #30363d 20%, #30363d 80%, transparent)" }} />
  );
}

function Section({ title, children, mb, titleCls }: { title: string; children: React.ReactNode; mb: string; titleCls: string }) {
  return (
    <div className={`card-section ${mb}`}>
      <div className={`card-section-title ${titleCls} font-bold text-muted uppercase tracking-[0.06em]`}>{title}</div>
      {children}
    </div>
  );
}

function PList({ items, mapper, textCls, statCls }: {
  items: Array<{ name: string; count: number }>;
  mapper: (item: { name: string; count: number }) => { name: string; stat: string };
  textCls: string;
  statCls: string;
}) {
  return (
    <div className="card-player-list flex flex-col gap-[0.1rem]">
      {items.map((item) => {
        const { name, stat } = mapper(item);
        return (
          <div key={name} className={`card-player-item flex items-baseline gap-[0.35rem] ${textCls}`}>
            <span className="card-player-name text-[#e6edf3] font-medium">{name}</span>
            <span className={`card-player-stat text-muted ${statCls}`}>{stat}</span>
          </div>
        );
      })}
    </div>
  );
}

function HighlightBlock({ title, name, stats, phrase, phraseColor, sectionMb, titleCls, nameCls, textCls, statCls, c }: {
  title: string; name: string; stats: string; phrase: string; phraseColor?: string;
  sectionMb: string; titleCls: string; nameCls: string; textCls: string; statCls: string; c: boolean;
}) {
  return (
    <Section title={title} mb={sectionMb} titleCls={titleCls}>
      <div className={`card-highlight flex flex-col ${c ? "gap-[0.05rem]" : "gap-[0.15rem]"}`}>
        <span className={`card-hl-name ${nameCls} font-semibold text-[#e6edf3]`}>{name}</span>
        <span className={`card-hl-stats ${statCls} text-muted`}>{stats}</span>
        <span
          className={`${textCls} italic leading-[1.35] ${c ? "mt-[0.1rem]" : "mt-[0.3rem]"}`}
          style={{ color: phraseColor ?? "#7ee787" }}
        >
          &ldquo;{phrase}&rdquo;
        </span>
      </div>
    </Section>
  );
}
