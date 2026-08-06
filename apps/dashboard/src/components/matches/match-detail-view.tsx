import { Panel } from "@/components/ui/panel";
import { OutcomeBadge } from "@/components/ui/outcome-badge";
import { formatDateTime, ratingColor, outcomeColor } from "@/lib/utils";
import type { MatchDetail, Story, MatchPlayer } from "@/lib/domain/types";

/* ── reason labels ────────────────────────────────────────── */

const reasonLabels: Record<string, string> = {
  EA_MAN_OF_THE_MATCH: "Eleito craque pela EA",
  HIGHEST_RATING: "Maior nota da partida",
  LOWEST_ELIGIBLE_RATING: "Menor nota entre os elegíveis",
  HIGHEST_DEFENSIVE_IMPACT: "Maior impacto defensivo",
  NO_ELIGIBLE_CANDIDATE: "Nenhum candidato elegível",
};

function localizeReason(reason: string | null): string | null {
  if (!reason) return null;
  return reasonLabels[reason] ?? reason;
}

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

interface StoryPresentation {
  emoji: string;
  title: string;
  color: string;
  message: string;
}

const storyRegistry: Record<string, StoryPresentation> = {
  MATCH_OUTCOME: {
    emoji: "📋",
    title: "Resultado",
    color: "var(--color-accent)",
    message: "O desfecho desta partida e o que os números contam.",
  },
  AWARD: {
    emoji: "🏆",
    title: "Premiação",
    color: "var(--color-highlight)",
    message: "Um reconhecimento individual que marcou esta partida.",
  },
  GOALS: {
    emoji: "⚽",
    title: "Gols",
    color: "var(--color-win)",
    message: "As finalizações que balançaram a rede neste jogo.",
  },
  ASSISTS: {
    emoji: "👟",
    title: "Assistências",
    color: "var(--color-precision)",
    message: "Passes decisivos que criaram oportunidades de gol.",
  },
  HIGHLIGHTS: {
    emoji: "⭐",
    title: "Destaques",
    color: "var(--color-highlight)",
    message: "Atuações que se destacaram nesta partida.",
  },
  BAGRE_PERFORMANCE: {
    emoji: "📉",
    title: "Menor Desempenho",
    color: "var(--color-development)",
    message: "Um aspecto desta atuação que pode encontrar uma resposta diferente no próximo jogo.",
  },
  OFFENSIVE_NARRATIVE: {
    emoji: "⚡",
    title: "Narrativa Ofensiva",
    color: "var(--color-pressure)",
    message: "A participação ofensiva que ajuda a explicar o jogo.",
  },
  RED_CARD: {
    emoji: "🟥",
    title: "Cartão Vermelho",
    color: "var(--color-discipline)",
    message: "Um momento difícil que também faz parte da história deste jogo.",
  },
  PASS_PRECISION: {
    emoji: "🎯",
    title: "Passe de Precisão",
    color: "var(--color-precision)",
    message: "Consistência com a bola para dar continuidade ao jogo do time.",
  },
  LOST_MAIL: {
    emoji: "📨",
    title: "Correio Extraviado",
    color: "var(--color-development)",
    message: "Um aspecto desta atuação que pode encontrar uma resposta diferente no próximo jogo.",
  },
  GOALKEEPER: {
    emoji: "🧤",
    title: "Muralha",
    color: "var(--color-defense)",
    message: "A presença do goleiro também escreveu parte desta partida.",
  },
  EA_RECOGNIZED_MVP: {
    emoji: "🌟",
    title: "Craque EA",
    color: "var(--color-highlight)",
    message: "Reconhecido pela EA como o melhor em campo.",
  },
};

const defaultStory: StoryPresentation = {
  emoji: "📝",
  title: "Acontecimento",
  color: "var(--color-border)",
  message: "Mais um fato que ajuda a compreender como o jogo aconteceu.",
};

function getStoryPresentation(type: string): StoryPresentation {
  return storyRegistry[type] ?? defaultStory;
}

/* ── editorial narrative system ───────────────────────────── */

const REDUNDANT_STORY_TYPES = new Set([
  "MATCH_OUTCOME",
  "AWARD",
  "GOALS",
  "ASSISTS",
  "HIGHLIGHTS",
  "BAGRE_PERFORMANCE",
  "EA_RECOGNIZED_MVP",
]);

interface EditorialCard {
  emoji: string;
  title: string;
  color: string;
  playerName: string | null;
  headline: string;
  editorial: string;
  priority: number;
}

function resolvePlayerName(id: unknown, players: MatchPlayer[]): string | null {
  if (typeof id !== "string") return null;
  const p = players.find((pl) => pl.playerId === id);
  return p?.displayName ?? p?.platformName ?? null;
}

function buildEditorialCards(stories: Story[], players: MatchPlayer[]): EditorialCard[] {
  const cards: EditorialCard[] = [];

  for (const s of stories) {
    if (REDUNDANT_STORY_TYPES.has(s.type)) continue;
    const card = buildEditorialCard(s, players);
    if (card) cards.push(card);
  }

  cards.sort((a, b) => b.priority - a.priority);
  return cards;
}

function buildEditorialCard(story: Story, players: MatchPlayer[]): EditorialCard | null {
  const c = story.content;
  const name = resolvePlayerName(c.playerId, players);
  const isPrimary = story.priority === "PRIMARY";
  const priorityBoost = isPrimary ? 15 : 0;

  switch (story.type) {
    case "OFFENSIVE_NARRATIVE": {
      const goals = c.goals as number | undefined ?? 0;
      const shots = c.shots as number | undefined ?? 0;
      const hatTrickBoost = goals >= 3 ? 25 : 0;
      const headline = name
        ? `${name} finalizou ${shots} ${shots === 1 ? "vez" : "vezes"} e marcou ${goals} ${goals === 1 ? "gol" : "gols"}.`
        : `${goals} ${goals === 1 ? "gol" : "gols"} em ${shots} ${shots === 1 ? "finalização" : "finalizações"}.`;
      return {
        emoji: "⚽",
        title: "Poder de Fogo",
        color: "var(--color-win)",
        playerName: name,
        headline,
        editorial: "A participação ofensiva que ajuda a explicar o jogo.",
        priority: 70 + hatTrickBoost + priorityBoost,
      };
    }
    case "RED_CARD": {
      const count = c.redCards as number | undefined ?? 1;
      const headline = name
        ? `${name} recebeu cartão vermelho${count > 1 ? ` (${count} cartões)` : ""}.`
        : `Cartão vermelho${count > 1 ? ` (${count} cartões)` : ""} alterou o rumo da partida.`;
      return {
        emoji: "🟥",
        title: "Momento Disciplinar",
        color: "var(--color-discipline)",
        playerName: name,
        headline,
        editorial: "Um momento difícil que também faz parte da história deste jogo.",
        priority: 100 + priorityBoost,
      };
    }
    case "GOALKEEPER": {
      const saves = c.saves as number | undefined ?? 0;
      const conceded = c.goalsConceded as number | undefined ?? 0;
      const highSavesBoost = saves >= 5 ? 20 : 0;
      const headline = name
        ? `${name} realizou ${saves} ${saves === 1 ? "defesa" : "defesas"} e sofreu ${conceded} ${conceded === 1 ? "gol" : "gols"}.`
        : `${saves} ${saves === 1 ? "defesa" : "defesas"} durante a partida.`;
      return {
        emoji: "🧤",
        title: "Muralha",
        color: "var(--color-defense)",
        playerName: name,
        headline,
        editorial: "Quando o time precisou, a segurança veio do gol.",
        priority: 65 + highSavesBoost + priorityBoost,
      };
    }
    case "PASS_PRECISION": {
      const pct = c.accuracyPercent as number | undefined;
      const completed = c.completed as number | undefined;
      const attempted = c.attempted as number | undefined;
      const eliteBoost = pct != null && pct >= 90 ? 10 : 0;
      let headline: string;
      if (name && pct != null && completed != null && attempted != null) {
        headline = `${name} terminou com ${pct}% de precisão (${completed}/${attempted}).`;
      } else if (name && pct != null) {
        headline = `${name} terminou com ${pct}% de precisão nos passes.`;
      } else if (name) {
        headline = `${name} se destacou na construção das jogadas.`;
      } else {
        headline = "A construção de jogadas foi um ponto forte nesta partida.";
      }
      return {
        emoji: "🎯",
        title: "Construção das Jogadas",
        color: "var(--color-precision)",
        playerName: name,
        headline,
        editorial: "Foi quem deu ritmo à circulação da bola.",
        priority: 50 + eliteBoost + priorityBoost,
      };
    }
    case "LOST_MAIL": {
      const playerPct = c.playerAccuracyPercent as number | undefined;
      const teamPct = c.teamAccuracyPercent as number | undefined;
      const delta = c.deltaPercent as number | undefined;
      const bigDeltaBoost = delta != null && delta >= 20 ? 10 : 0;
      let headline: string;
      if (name && playerPct != null && teamPct != null) {
        headline = `${name} acertou ${playerPct}% dos passes — média do time foi ${teamPct}%.`;
      } else if (name && playerPct != null) {
        headline = `${name} ficou com ${playerPct}% de precisão nos passes.`;
      } else if (name) {
        headline = `${name} ficou abaixo da média do time na distribuição de bola.`;
      } else {
        headline = "A precisão nos passes ficou abaixo do esperado.";
      }
      return {
        emoji: "📉",
        title: "Ponto de Atenção",
        color: "var(--color-development)",
        playerName: name,
        headline,
        editorial: "Nem tudo saiu como planejado — e reconhecer isso também faz parte.",
        priority: 40 + bigDeltaBoost + priorityBoost,
      };
    }
    default:
      return null;
  }
}

/* ── player sorting ────────────────────────────────────────── */

function sortPlayersByRating(players: MatchPlayer[]): MatchPlayer[] {
  const disconnectedPlayers = players.filter(p => p.rating === 3.0);
  const activePlayers = players.filter(p => p.rating !== 3.0);

  activePlayers.sort((a, b) => {
    const ratingA = a.rating ?? 0;
    const ratingB = b.rating ?? 0;
    return ratingB - ratingA; // descending order
  });

  return [...activePlayers, ...disconnectedPlayers];
}

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
              const player = awarded
                ? match.players.find((p) => p.playerId === award.winnerId)
                : null;
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
                        {player && (
                          <div className="flex flex-wrap gap-x-3 gap-y-0.5 text-xs text-text-soft mt-1">
                            {player.rating != null && (
                              <span className={`font-bold ${ratingColor(player.rating)}`}>
                                {player.rating.toFixed(1)}
                              </span>
                            )}
                            {player.goals > 0 && <span>{player.goals} gol{player.goals > 1 ? "s" : ""}</span>}
                            {player.assists > 0 && <span>{player.assists} assist.</span>}
                            {player.redCards > 0 && <span className="text-loss">{player.redCards} CV</span>}
                          </div>
                        )}
                        {award.reason && (
                          <p className="text-xs text-muted mt-2" style={{ fontWeight: 600 }}>
                            {localizeReason(award.reason)}
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
          subtitle="Os acontecimentos que ajudam a explicar esta partida."
        />
        {(() => {
          const editorialCards = buildEditorialCards(match.stories, match.players);
          if (editorialCards.length === 0) {
            return (
              <div className="rounded-[10px] border border-dashed border-border p-8 text-center">
                <p className="text-sm text-muted italic">
                  Nenhum acontecimento editorial se destacou nesta partida.
                </p>
              </div>
            );
          }
          return (
            <div className="flex flex-col gap-3">
              {editorialCards.map((card, i) => (
                <div
                  key={i}
                  className="relative rounded-[10px] border border-border overflow-hidden"
                  style={{
                    background: `linear-gradient(135deg, color-mix(in srgb, ${card.color} 5%, var(--color-surface-raised)), var(--color-surface-raised) 40%)`,
                  }}
                >
                  <span
                    className="absolute top-0 bottom-0 left-0 w-[3px]"
                    style={{ backgroundColor: card.color }}
                  />
                  <div className="pl-5 pr-5 py-5">
                    <div className="flex items-center gap-2 mb-3">
                      <span className="text-lg">{card.emoji}</span>
                      <span className="text-[10px] font-semibold uppercase tracking-wider text-muted">
                        {card.title}
                      </span>
                    </div>
                    <p className="text-[15px] font-semibold text-text-primary leading-snug">
                      {card.headline}
                    </p>
                    <p className="text-xs text-muted mt-2 italic leading-relaxed">
                      {card.editorial}
                    </p>
                  </div>
                </div>
              ))}
            </div>
          );
        })()}
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
              {sortPlayersByRating(match.players).map((p) => {
                const isDisconnected = p.rating === 3.0;
                return (
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
                    {isDisconnected && (
                      <span className="ml-2 text-xs text-muted italic">
                        (Saiu da partida)
                      </span>
                    )}
                  </td>
                  <td className="px-3 py-2.5 text-center" style={{ fontSize: 16, fontWeight: 850 }}>
                    <span className={p.rating ? (isDisconnected ? "text-muted" : ratingColor(p.rating)) : "text-muted"}>
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
                );
              })}
            </tbody>
          </table>
        </div>

        {/* mobile cards */}
        <div className="sm:hidden rounded-[10px] border border-border divide-y divide-border">
          {sortPlayersByRating(match.players).map((p) => {
            const isDisconnected = p.rating === 3.0;
            return (
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
                  className={p.rating ? (isDisconnected ? "text-muted" : ratingColor(p.rating)) : "text-muted"}
                  style={{ fontSize: 16, fontWeight: 850 }}
                >
                  {p.rating?.toFixed(1) ?? "---"}
                </span>
              </div>
              {isDisconnected && (
                <div className="text-xs text-muted italic mb-2">
                  Saiu da partida
                </div>
              )}
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
            );
          })}
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
                        <span className="text-muted">Razão:</span> {localizeReason(award.reason)}
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
                      {getStoryPresentation(s.type).title}
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
