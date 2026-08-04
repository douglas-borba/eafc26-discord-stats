import { Panel } from "@/components/ui/panel";
import { OutcomeBadge } from "@/components/ui/outcome-badge";
import { formatDateTime, ratingColor, outcomeColor } from "@/lib/utils";
import type { MatchDetail } from "@/lib/domain/types";

/* ── tone maps ─────────────────────────────────────────────── */

const characterMeta: Record<string, { emoji: string; label: string; color: string; message: string }> = {
  craque: {
    emoji: "⭐",
    label: "Craque da Partida",
    color: "var(--color-highlight)",
    message: "Uma atuação que fez a diferença nesta partida.",
  },
  bagre: {
    emoji: "📉",
    label: "Menor Desempenho",
    color: "var(--color-development)",
    message: "Nem toda partida sai como esperado. A próxima é uma nova oportunidade para responder em campo.",
  },
  xerife: {
    emoji: "🛡️",
    label: "Xerife",
    color: "var(--color-defense)",
    message: "Consistência e segurança para proteger o time.",
  },
};

const storyToneColor: Record<string, string> = {
  DECISIVE: "var(--color-highlight)",
  CONSTANT_THREAT: "var(--color-pressure)",
  NEAR_MISS: "var(--color-near-miss)",
  PASS_PRECISION: "var(--color-precision)",
  RED_CARD: "var(--color-discipline)",
  LOST_MAIL: "var(--color-development)",
  GOALKEEPER: "var(--color-defense)",
};

const storyMeta: Record<string, { emoji: string; title: string; message: string }> = {
  DECISIVE: {
    emoji: "⚡",
    title: "Fez a Diferença",
    message: "Uma atuação que mudou o rumo da partida, sustentada pelos fatos do jogo.",
  },
  CONSTANT_THREAT: {
    emoji: "🎯",
    title: "Perigo Constante",
    message: "Participação ofensiva frequente, pressionando e criando oportunidades.",
  },
  NEAR_MISS: {
    emoji: "⏳",
    title: "Ficou no Quase",
    message: "A presença ofensiva apareceu; faltou transformar mais oportunidades em resultado.",
  },
  RED_CARD: {
    emoji: "🟥",
    title: "Cartão Vermelho",
    message: "Um momento difícil que também faz parte da história deste jogo.",
  },
  PASS_PRECISION: {
    emoji: "🎯",
    title: "Passe de Precisão",
    message: "Consistência com a bola para dar continuidade ao jogo do time.",
  },
  LOST_MAIL: {
    emoji: "📨",
    title: "Correio Extraviado",
    message: "Um aspecto desta atuação que pode encontrar uma resposta diferente no próximo jogo.",
  },
  GOALKEEPER: {
    emoji: "🧤",
    title: "Muralha",
    message: "A presença do goleiro também escreveu parte desta partida.",
  },
};

/* ── component ─────────────────────────────────────────────── */

export function MatchDetailView({ match, clubId }: { match: MatchDetail; clubId: string }) {
  const totalGoals = match.players.reduce((s, p) => s + p.goals, 0);
  const totalAssists = match.players.reduce((s, p) => s + p.assists, 0);
  const avgRating =
    match.players.length > 0
      ? match.players.reduce((s, p) => s + (p.rating ?? 0), 0) / match.players.length
      : 0;

  return (
    <article>
      {/* ── Hero ────────────────────────────────────────── */}
      <section className="pb-8 border-b border-border">
        <p className="text-[11px] font-semibold uppercase tracking-[0.09em] text-muted mb-1">
          O que aconteceu
        </p>
        <h2 style={{ fontSize: "clamp(20px, 2.4vw, 26px)" }} className="font-bold text-text-primary mb-4">
          A Partida
        </h2>

        {/* meta row: date+competition left, outcome right */}
        <div className="flex items-center justify-between mb-6">
          <p className="text-xs text-muted">
            {formatDateTime(match.playedAt)}
            {match.matchType && <> &middot; {match.matchType}</>}
          </p>
          <div className="flex items-center gap-2">
            <OutcomeBadge outcome={match.outcome} className="w-7 h-7 text-xs" />
            <span className={`text-sm font-semibold ${outcomeColor(match.outcome)}`}>
              {match.outcome === "WIN" ? "Vitória" : match.outcome === "DRAW" ? "Empate" : "Derrota"}
            </span>
          </div>
        </div>

        {/* score grid: 3 columns */}
        <div className="grid grid-cols-[1fr_auto_1fr] items-center gap-4 max-w-lg mx-auto">
          <div className="text-right">
            <p className="text-sm text-text-soft mb-1">{match.ourClubName ?? "Nós"}</p>
          </div>
          <div className="text-center">
            <p className="text-5xl font-black leading-none">
              {match.ourScore}
              <span className="text-2xl text-muted font-light mx-2">&times;</span>
              {match.opponentScore}
            </p>
          </div>
          <div className="text-left">
            {match.opponentClubId ? (
              <a
                href={`/clubs/${clubId}/opponents?opponent=${match.opponentClubId}`}
                className="text-sm text-accent hover:underline"
              >
                {match.opponentClubName ?? "Adversário"}
              </a>
            ) : (
              <p className="text-sm text-text-soft mb-1">{match.opponentClubName ?? "Adversário"}</p>
            )}
          </div>
        </div>
      </section>

      {/* ── Section 1: Personagens da Partida ──────────── */}
      {(match.awards.craque || match.awards.bagre || match.awards.xerife) && (
        <EditorialSection>
          <SectionHeading
            kicker="Quem marcou o jogo"
            title="Personagens da Partida"
            subtitle="Quem foram os protagonistas desta atuação?"
          />
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            {(["craque", "bagre", "xerife"] as const).map((key) => {
              const award = match.awards[key];
              const meta = characterMeta[key];
              const awarded = !!award;
              return (
                <Panel
                  key={key}
                  className={`relative overflow-hidden min-h-[180px] ${
                    awarded ? "" : "border-dashed opacity-50"
                  }`}
                  style={
                    awarded
                      ? { background: `color-mix(in srgb, ${meta.color} 5%, var(--color-surface))` }
                      : undefined
                  }
                >
                  {/* accent bar */}
                  <span
                    className="absolute top-0 bottom-0 left-0 w-[3px]"
                    style={{ backgroundColor: meta.color }}
                  />
                  <div className="pl-3">
                    <div className="flex items-center gap-2 mb-1">
                      <span className="text-lg">{meta.emoji}</span>
                      <span className="text-[10px] font-semibold uppercase tracking-wider text-muted">
                        {meta.label}
                      </span>
                    </div>
                    {awarded ? (
                      <>
                        <h3 className="text-[19px] font-bold text-text-primary mt-2">
                          {award.winnerName ?? "---"}
                        </h3>
                        {award.reason && (
                          <p className="text-sm mt-1" style={{ fontWeight: 650 }}>
                            {award.reason}
                          </p>
                        )}
                        <p className="text-xs text-muted mt-3 pt-3 border-t border-border/40 italic leading-relaxed">
                          {meta.message}
                        </p>
                      </>
                    ) : (
                      <p className="text-xs text-muted mt-2 italic">Não concedido nesta partida.</p>
                    )}
                  </div>
                </Panel>
              );
            })}
          </div>

          {/* goals and assists contribution */}
          {(totalGoals > 0 || totalAssists > 0) && (
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 mt-3">
              {totalGoals > 0 && (
                <Panel>
                  <p className="text-[10px] font-semibold uppercase tracking-wider text-muted mb-2">
                    Gols
                  </p>
                  <div className="space-y-1">
                    {match.players
                      .filter((p) => p.goals > 0)
                      .map((p) => (
                        <div key={p.playerId} className="flex justify-between text-sm">
                          <span className="text-text-soft">
                            {p.displayName ?? p.platformName ?? p.playerId}
                          </span>
                          <span className="font-semibold">{p.goals}</span>
                        </div>
                      ))}
                  </div>
                </Panel>
              )}
              {totalAssists > 0 && (
                <Panel>
                  <p className="text-[10px] font-semibold uppercase tracking-wider text-muted mb-2">
                    Assistências
                  </p>
                  <div className="space-y-1">
                    {match.players
                      .filter((p) => p.assists > 0)
                      .map((p) => (
                        <div key={p.playerId} className="flex justify-between text-sm">
                          <span className="text-text-soft">
                            {p.displayName ?? p.platformName ?? p.playerId}
                          </span>
                          <span className="font-semibold">{p.assists}</span>
                        </div>
                      ))}
                  </div>
                </Panel>
              )}
            </div>
          )}
        </EditorialSection>
      )}

      {/* ── Section 2: A História do Jogo ──────────────── */}
      <EditorialSection>
        <SectionHeading
          kicker="Como aconteceu"
          title="A História do Jogo"
          subtitle="Quais decisões ajudam a explicar esta partida?"
        />
        {match.stories.length > 0 ? (
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            {match.stories.map((s, i) => {
              const meta = storyMeta[s.type] ?? {
                emoji: "•",
                title: s.type,
                message: "Mais um fato que ajuda a compreender como o jogo aconteceu.",
              };
              const accent = storyToneColor[s.type] ?? "var(--color-border)";
              return (
                <div
                  key={i}
                  className="rounded-[10px] border border-border p-5"
                  style={{
                    background: `linear-gradient(135deg, color-mix(in srgb, ${accent} 7%, var(--color-surface-raised)), var(--color-surface-raised) 55%)`,
                  }}
                >
                  <p className="text-lg mb-1">{meta.emoji}</p>
                  <p className="text-sm font-semibold text-text-primary">{meta.title}</p>
                  <p className="text-xs text-text-soft mt-1">{s.narrativeKey}</p>
                  {/* involvedPlayers would go here if available in the data model */}
                  <p className="text-xs text-muted mt-3 italic">{meta.message}</p>
                </div>
              );
            })}
          </div>
        ) : (
          <p className="text-sm text-muted italic">
            Nenhuma narrativa especial foi identificada nesta partida.
          </p>
        )}
      </EditorialSection>

      {/* ── Section 3: O Time em Números ───────────────── */}
      <EditorialSection>
        <SectionHeading
          kicker="Os números do coletivo"
          title="O Time em Números"
          subtitle="Quais fatos sustentam a leitura da equipe?"
        />
        {/* metric strip */}
        <div
          className="grid gap-0 border-y border-border"
          style={{
            gridTemplateColumns: "repeat(auto-fit, minmax(140px, 1fr))",
          }}
        >
          <MetricStripItem label="Gols marcados" value={String(match.ourScore)} />
          <MetricStripItem label="Gols sofridos" value={String(match.opponentScore)} />
          <MetricStripItem
            label="Média do time"
            value={avgRating.toFixed(1)}
            valueClassName={ratingColor(avgRating)}
            last
          />
        </div>

        {/* rating highlights */}
        {match.players.some((p) => p.rating !== null && p.rating >= 8.0) && (
          <Panel className="mt-4">
            <p className="text-[10px] font-semibold uppercase tracking-wider text-muted mb-2">
              Destaques por nota
            </p>
            <div className="space-y-1">
              {match.players
                .filter((p) => p.rating !== null && p.rating >= 8.0)
                .sort((a, b) => (b.rating ?? 0) - (a.rating ?? 0))
                .map((p) => (
                  <div key={p.playerId} className="flex justify-between text-sm">
                    <span className="text-text-soft">
                      {p.displayName ?? p.platformName ?? p.playerId}
                    </span>
                    <span className={`font-semibold ${ratingColor(p.rating!)}`}>
                      {p.rating!.toFixed(1)}
                    </span>
                  </div>
                ))}
            </div>
          </Panel>
        )}
      </EditorialSection>

      {/* ── Section 4: Jogador por Jogador ─────────────── */}
      <EditorialSection>
        <SectionHeading
          kicker="Atuação por atuação"
          title="Jogador por Jogador"
          subtitle="Como cada jogador participou deste capítulo?"
        />

        {/* desktop table */}
        <div className="overflow-x-auto rounded-[10px] border border-border hidden sm:block">
          <table className="w-full text-sm min-w-[800px]">
            <thead>
              <tr className="border-b border-border text-left text-[11px] font-semibold uppercase tracking-wider text-muted">
                <th className="px-4 py-3">Jogador</th>
                <th className="px-3 py-3 text-center">Nota</th>
                <th className="px-3 py-3 text-center">G</th>
                <th className="px-3 py-3 text-center">A</th>
                <th className="px-3 py-3 text-center">Fin.</th>
                <th className="px-3 py-3 text-center">Passes</th>
                <th className="px-3 py-3 text-center">Desarmes</th>
                <th className="px-3 py-3 text-center">CV</th>
                <th className="px-3 py-3 text-center">Def.</th>
              </tr>
            </thead>
            <tbody>
              {match.players.map((p) => (
                <tr key={p.playerId} className="border-b border-border/50 hover:bg-surface-raised/50">
                  <td className="px-4 py-2.5">
                    <a
                      href={`/clubs/${clubId}/players?player=${p.playerId}`}
                      className="font-medium hover:text-accent transition-colors"
                    >
                      {p.displayName ?? p.platformName ?? p.playerId}
                    </a>
                    {p.manOfTheMatch && (
                      <span className="ml-1.5 text-highlight" title="Man of the Match">
                        ⭐
                      </span>
                    )}
                  </td>
                  <td className="px-3 py-2.5 text-center" style={{ fontSize: 16, fontWeight: 850 }}>
                    <span className={p.rating ? ratingColor(p.rating) : "text-muted"}>
                      {p.rating?.toFixed(1) ?? "---"}
                    </span>
                  </td>
                  <td className="px-3 py-2.5 text-center">{p.goals || "---"}</td>
                  <td className="px-3 py-2.5 text-center">{p.assists || "---"}</td>
                  <td className="px-3 py-2.5 text-center">{p.shots || "---"}</td>
                  <td className="px-3 py-2.5 text-center">
                    {p.passesCompleted}/{p.passesAttempted}
                  </td>
                  <td className="px-3 py-2.5 text-center">
                    {p.tacklesCompleted}/{p.tacklesAttempted}
                  </td>
                  <td className="px-3 py-2.5 text-center">{p.redCards || "---"}</td>
                  <td className="px-3 py-2.5 text-center text-muted">---</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {/* mobile cards */}
        <div className="sm:hidden rounded-[10px] border border-border divide-y divide-border">
          {match.players.map((p) => (
            <div key={p.playerId} className="p-4">
              <div className="flex items-center justify-between mb-2">
                <a
                  href={`/clubs/${clubId}/players?player=${p.playerId}`}
                  className="font-medium text-text-primary hover:text-accent transition-colors"
                >
                  {p.displayName ?? p.platformName ?? p.playerId}
                  {p.manOfTheMatch && <span className="ml-1 text-highlight">⭐</span>}
                </a>
                <span
                  className={p.rating ? ratingColor(p.rating) : "text-muted"}
                  style={{ fontSize: 16, fontWeight: 850 }}
                >
                  {p.rating?.toFixed(1) ?? "---"}
                </span>
              </div>
              <div className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-muted">
                <span>
                  <span className="font-semibold text-text-soft">{p.goals}</span> G
                </span>
                <span>
                  <span className="font-semibold text-text-soft">{p.assists}</span> A
                </span>
                <span>
                  <span className="font-semibold text-text-soft">{p.shots}</span> Fin
                </span>
                <span>
                  <span className="font-semibold text-text-soft">
                    {p.passesCompleted}/{p.passesAttempted}
                  </span>{" "}
                  Passes
                </span>
                <span>
                  <span className="font-semibold text-text-soft">
                    {p.tacklesCompleted}/{p.tacklesAttempted}
                  </span>{" "}
                  Desarmes
                </span>
              </div>
            </div>
          ))}
        </div>
      </EditorialSection>

      {/* ── Section 5: Critérios e Evidências ──────────── */}
      <EditorialSection>
        <SectionHeading
          kicker="Auditoria sob demanda"
          title="Critérios e Evidências"
          subtitle="Como o sistema chegou a estas conclusões?"
        />
        <div className="space-y-2">
          {/* 1. Premiações */}
          <details className="group rounded-[10px] border border-border">
            <summary className="px-5 py-3 cursor-pointer text-sm font-semibold text-text-primary select-none list-none flex items-center justify-between">
              Premiações
              <span className="text-muted text-xs group-open:rotate-90 transition-transform">&#9654;</span>
            </summary>
            <div className="px-5 pb-4 text-xs text-text-soft space-y-3">
              {(["craque", "bagre", "xerife"] as const).map((key) => {
                const award = match.awards[key];
                const meta = characterMeta[key];
                if (!award) return null;
                return (
                  <div key={key} className="border-t border-border/40 pt-3">
                    <p className="font-semibold text-text-primary">{meta.label}</p>
                    <p>
                      <span className="text-muted">Vencedor:</span> {award.winnerName ?? "---"}
                    </p>
                    {award.reason && (
                      <p>
                        <span className="text-muted">Razão:</span> {award.reason}
                      </p>
                    )}
                    <p className="text-muted italic mt-1">
                      Dados adicionais (voice, facts, rule IDs) disponíveis no servidor Thymeleaf.
                    </p>
                  </div>
                );
              })}
              {!match.awards.craque && !match.awards.bagre && !match.awards.xerife && (
                <p className="text-muted italic">Nenhuma premiação concedida.</p>
              )}
            </div>
          </details>

          {/* 2. Histórias e regras aplicadas */}
          <details className="group rounded-[10px] border border-border">
            <summary className="px-5 py-3 cursor-pointer text-sm font-semibold text-text-primary select-none list-none flex items-center justify-between">
              Histórias e regras aplicadas
              <span className="text-muted text-xs group-open:rotate-90 transition-transform">&#9654;</span>
            </summary>
            <div className="px-5 pb-4 text-xs text-text-soft space-y-3">
              {match.stories.length > 0 ? (
                match.stories.map((s, i) => (
                  <div key={i} className="border-t border-border/40 pt-3">
                    <p className="font-semibold text-text-primary">
                      {storyMeta[s.type]?.title ?? s.type}
                    </p>
                    <p>
                      <span className="text-muted">Prioridade:</span> {s.priority}
                    </p>
                    <p>
                      <span className="text-muted">Narrativa:</span> {s.narrativeKey}
                    </p>
                    <p className="text-muted italic mt-1">
                      Dados adicionais (evidence count, rule IDs) disponíveis no servidor Thymeleaf.
                    </p>
                  </div>
                ))
              ) : (
                <p className="text-muted italic">Nenhuma história registrada.</p>
              )}
            </div>
          </details>

          {/* 3. Elegibilidade dos jogadores */}
          <details className="group rounded-[10px] border border-border">
            <summary className="px-5 py-3 cursor-pointer text-sm font-semibold text-text-primary select-none list-none flex items-center justify-between">
              Elegibilidade dos jogadores
              <span className="text-muted text-xs group-open:rotate-90 transition-transform">&#9654;</span>
            </summary>
            <div className="px-5 pb-4 text-xs text-text-soft space-y-2">
              {match.players.map((p) => (
                <div key={p.playerId} className="flex justify-between border-t border-border/40 pt-2">
                  <span>{p.displayName ?? p.platformName ?? p.playerId}</span>
                  <span className="font-mono text-muted">
                    {p.rating !== null ? "elegível" : "sem nota"}
                  </span>
                </div>
              ))}
              <p className="text-muted italic mt-2">
                Critérios detalhados de elegibilidade disponíveis no servidor Thymeleaf.
              </p>
            </div>
          </details>

          {/* 4. Proveniência canônica */}
          <details className="group rounded-[10px] border border-border">
            <summary className="px-5 py-3 cursor-pointer text-sm font-semibold text-text-primary select-none list-none flex items-center justify-between">
              Proveniência canônica
              <span className="text-muted text-xs group-open:rotate-90 transition-transform">&#9654;</span>
            </summary>
            <div className="px-5 pb-4 text-xs text-text-soft space-y-1">
              <p>
                <span className="text-muted">matchId:</span>{" "}
                <code className="font-mono text-[11px]">{match.matchId}</code>
              </p>
              <p>
                <span className="text-muted">playedAt:</span> {match.playedAt}
              </p>
              <p className="text-muted italic mt-2">
                Schema version, engine version e generatedAt disponíveis no servidor Thymeleaf.
              </p>
            </div>
          </details>
        </div>
      </EditorialSection>
    </article>
  );
}

/* ── editorial section wrapper (gradient divider above) ───── */

function EditorialSection({ children }: { children: React.ReactNode }) {
  return (
    <section
      className="pt-12 relative before:absolute before:top-0 before:left-0 before:right-0 before:h-px"
      style={{
        // gradient line from left to transparent
        // applied via inline since Tailwind can't express this gradient
      }}
    >
      <div
        className="absolute top-0 left-0 right-0 h-px"
        style={{
          background: "linear-gradient(to right, var(--color-border), transparent)",
        }}
      />
      {children}
    </section>
  );
}

/* ── section heading ─────────────────────────────────────── */

function SectionHeading({
  kicker,
  title,
  subtitle,
}: {
  kicker: string;
  title: string;
  subtitle: string;
}) {
  return (
    <div className="mb-6">
      <p className="text-[11px] font-semibold uppercase tracking-[0.09em] text-muted mb-1">
        {kicker}
      </p>
      <h3 style={{ fontSize: "clamp(20px, 2.4vw, 26px)" }} className="font-bold text-text-primary">
        {title}
      </h3>
      <p className="text-xs text-muted mt-0.5">{subtitle}</p>
    </div>
  );
}

/* ── metric strip item ───────────────────────────────────── */

function MetricStripItem({
  label,
  value,
  valueClassName,
  last,
}: {
  label: string;
  value: string;
  valueClassName?: string;
  last?: boolean;
}) {
  return (
    <div
      className={`text-center py-5 px-4 ${last ? "" : "border-r border-border"}`}
    >
      <p className="text-[10px] font-semibold uppercase tracking-wider text-muted mb-1">{label}</p>
      <p
        className={valueClassName ?? ""}
        style={{ fontSize: "clamp(25px, 3vw, 32px)", fontWeight: 900 }}
      >
        {value}
      </p>
    </div>
  );
}
