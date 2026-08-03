import { Panel } from "@/components/ui/panel";
import { OutcomeBadge } from "@/components/ui/outcome-badge";
import { formatDateTime, ratingColor, outcomeColor } from "@/lib/utils";
import type { MatchDetail } from "@/lib/domain/types";

const characterMeta: Record<string, { emoji: string; label: string; accent: string; tone: string; message: string }> = {
  craque: {
    emoji: "⭐",
    label: "Craque da Partida",
    accent: "border-l-highlight",
    tone: "bg-highlight/5",
    message: "Uma atuação que fez a diferença nesta partida.",
  },
  bagre: {
    emoji: "📉",
    label: "Menor Desempenho",
    accent: "border-l-development",
    tone: "bg-development/5",
    message: "Nem toda partida sai como esperado. A próxima é uma nova oportunidade para responder em campo.",
  },
  xerife: {
    emoji: "🛡️",
    label: "Xerife",
    accent: "border-l-defense",
    tone: "bg-defense/5",
    message: "Consistência e segurança para proteger o time.",
  },
};

const storyMeta: Record<string, { emoji: string; title: string; accent: string; tone: string; message: string }> = {
  DECISIVE: {
    emoji: "⚡",
    title: "Fez a Diferença",
    accent: "border-l-win",
    tone: "bg-win/5",
    message: "Uma atuação que mudou o rumo da partida, sustentada pelos fatos do jogo.",
  },
  CONSTANT_THREAT: {
    emoji: "🎯",
    title: "Perigo Constante",
    accent: "border-l-pressure",
    tone: "bg-pressure/5",
    message: "Participação ofensiva frequente, pressionando e criando oportunidades.",
  },
  NEAR_MISS: {
    emoji: "⏳",
    title: "Ficou no Quase",
    accent: "border-l-near-miss",
    tone: "bg-near-miss/5",
    message: "A presença ofensiva apareceu; faltou transformar mais oportunidades em resultado.",
  },
  RED_CARD: {
    emoji: "🟥",
    title: "Cartão Vermelho",
    accent: "border-l-discipline",
    tone: "bg-discipline/5",
    message: "Um momento difícil que também faz parte da história deste jogo.",
  },
  PASS_PRECISION: {
    emoji: "🎯",
    title: "Passe de Precisão",
    accent: "border-l-precision",
    tone: "bg-precision/5",
    message: "Consistência com a bola para dar continuidade ao jogo do time.",
  },
  LOST_MAIL: {
    emoji: "📨",
    title: "Correio Extraviado",
    accent: "border-l-development",
    tone: "bg-development/5",
    message: "Um aspecto desta atuação que pode encontrar uma resposta diferente no próximo jogo.",
  },
  GOALKEEPER: {
    emoji: "🧤",
    title: "Muralha",
    accent: "border-l-defense",
    tone: "bg-defense/5",
    message: "A presença do goleiro também escreveu parte desta partida.",
  },
};

export function MatchDetailView({ match, clubId }: { match: MatchDetail; clubId: string }) {
  const totalGoals = match.players.reduce((s, p) => s + p.goals, 0);
  const totalAssists = match.players.reduce((s, p) => s + p.assists, 0);
  const avgRating = match.players.length > 0
    ? match.players.reduce((s, p) => s + (p.rating ?? 0), 0) / match.players.length
    : 0;

  return (
    <article className="space-y-8">
      {/* Section 1: Hero */}
      <section>
        <Panel className="text-center py-8">
          <div className="flex items-center justify-center gap-2 mb-4">
            <OutcomeBadge outcome={match.outcome} className="w-7 h-7 text-xs" />
            <span className={`text-sm font-semibold ${outcomeColor(match.outcome)}`}>
              {match.outcome === "WIN" ? "Vitória" : match.outcome === "DRAW" ? "Empate" : "Derrota"}
            </span>
          </div>
          <p className="text-xs text-muted mb-5">
            {match.matchType ?? "Partida"} &middot; {formatDateTime(match.playedAt)}
          </p>
          <div className="flex items-center justify-center gap-6 sm:gap-10">
            <div className="text-right flex-1">
              <p className="text-sm text-text-soft mb-1">{match.ourClubName ?? "Nós"}</p>
              <p className="text-5xl font-black">{match.ourScore}</p>
            </div>
            <span className="text-2xl text-muted font-light">&times;</span>
            <div className="text-left flex-1">
              <p className="text-sm text-text-soft mb-1">{match.opponentClubName ?? "Adversário"}</p>
              <p className="text-5xl font-black">{match.opponentScore}</p>
            </div>
          </div>
          {match.opponentClubId && (
            <a
              href={`/clubs/${clubId}/opponents?opponent=${match.opponentClubId}`}
              className="inline-block mt-4 text-xs text-accent hover:underline"
            >
              Ver retrospecto contra {match.opponentClubName ?? "este adversário"}
            </a>
          )}
        </Panel>
      </section>

      {/* Section 2: Personagens da Partida */}
      {(match.awards.craque || match.awards.bagre || match.awards.xerife) && (
        <section>
          <SectionHeading
            kicker="Personagens"
            title="Personagens da Partida"
            subtitle="Quem se destacou nesta história?"
          />
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            {(["craque", "bagre", "xerife"] as const).map((key) => {
              const award = match.awards[key];
              const meta = characterMeta[key];
              const awarded = !!award;
              return (
                <Panel
                  key={key}
                  className={`border-l-[3px] ${meta.accent} ${awarded ? meta.tone : "opacity-50 border-dashed"} min-h-[180px]`}
                >
                  <p className="text-2xl mb-2">{meta.emoji}</p>
                  <p className="text-[10px] font-semibold uppercase tracking-wider text-muted">
                    {meta.label}
                  </p>
                  {awarded ? (
                    <>
                      <p className="text-base font-bold text-text-primary mt-2">
                        {award.winnerName ?? "—"}
                      </p>
                      {award.reason && (
                        <p className="text-xs text-text-soft mt-1">{award.reason}</p>
                      )}
                      <p className="text-xs text-muted mt-2 italic">{meta.message}</p>
                    </>
                  ) : (
                    <p className="text-xs text-muted mt-2 italic">Não concedido nesta partida.</p>
                  )}
                </Panel>
              );
            })}
          </div>

          {/* Goals and assists contribution */}
          {(totalGoals > 0 || totalAssists > 0) && (
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 mt-3">
              {totalGoals > 0 && (
                <Panel>
                  <p className="text-[10px] font-semibold uppercase tracking-wider text-muted mb-2">Gols</p>
                  <div className="space-y-1">
                    {match.players.filter((p) => p.goals > 0).map((p) => (
                      <div key={p.playerId} className="flex justify-between text-sm">
                        <span className="text-text-soft">{p.displayName ?? p.platformName ?? p.playerId}</span>
                        <span className="font-semibold">{p.goals}</span>
                      </div>
                    ))}
                  </div>
                </Panel>
              )}
              {totalAssists > 0 && (
                <Panel>
                  <p className="text-[10px] font-semibold uppercase tracking-wider text-muted mb-2">Assistências</p>
                  <div className="space-y-1">
                    {match.players.filter((p) => p.assists > 0).map((p) => (
                      <div key={p.playerId} className="flex justify-between text-sm">
                        <span className="text-text-soft">{p.displayName ?? p.platformName ?? p.playerId}</span>
                        <span className="font-semibold">{p.assists}</span>
                      </div>
                    ))}
                  </div>
                </Panel>
              )}
            </div>
          )}
        </section>
      )}

      {/* Section 3: A História do Jogo */}
      {match.stories.length > 0 && (
        <section>
          <SectionHeading
            kicker="Narrativas"
            title="A História do Jogo"
            subtitle="O que a engine identificou nesta partida?"
          />
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            {match.stories.map((s, i) => {
              const meta = storyMeta[s.type] ?? {
                emoji: "•",
                title: s.type,
                accent: "border-l-border",
                tone: "",
                message: "Mais um fato que ajuda a compreender como o jogo aconteceu.",
              };
              return (
                <Panel key={i} className={`border-l-[3px] ${meta.accent} ${meta.tone} min-h-[180px]`}>
                  <p className="text-2xl mb-2">{meta.emoji}</p>
                  <p className="text-sm font-semibold text-text-primary">{meta.title}</p>
                  <p className="text-xs text-text-soft mt-1">{s.narrativeKey}</p>
                  <p className="text-xs text-muted mt-2 italic">{meta.message}</p>
                </Panel>
              );
            })}
          </div>
        </section>
      )}

      {/* Section 4: O Time em Números */}
      <section>
        <SectionHeading
          kicker="Estatísticas"
          title="O Time em Números"
          subtitle="Os números coletivos desta partida."
        />
        <div className="grid grid-cols-3 gap-3">
          <Panel className="text-center">
            <p className="text-[10px] font-semibold uppercase tracking-wider text-muted mb-1">Gols marcados</p>
            <p className="text-3xl font-black">{match.ourScore}</p>
          </Panel>
          <Panel className="text-center">
            <p className="text-[10px] font-semibold uppercase tracking-wider text-muted mb-1">Gols sofridos</p>
            <p className="text-3xl font-black">{match.opponentScore}</p>
          </Panel>
          <Panel className="text-center">
            <p className="text-[10px] font-semibold uppercase tracking-wider text-muted mb-1">Média do time</p>
            <p className={`text-3xl font-black ${ratingColor(avgRating)}`}>{avgRating.toFixed(1)}</p>
          </Panel>
        </div>
      </section>

      {/* Section 5: Jogador por Jogador */}
      <section>
        <SectionHeading
          kicker="Desempenho individual"
          title="Jogador por Jogador"
          subtitle="As estatísticas individuais registradas nesta partida."
        />
        <Panel className="overflow-x-auto p-0">
          {/* Desktop table */}
          <table className="w-full text-sm hidden sm:table min-w-[760px]">
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
              </tr>
            </thead>
            <tbody>
              {match.players.map((p) => (
                <tr key={p.playerId} className="border-b border-border/50 hover:bg-surface-raised/50">
                  <td className="px-4 py-2.5 font-medium">
                    <a
                      href={`/clubs/${clubId}/players?player=${p.playerId}`}
                      className="hover:text-accent transition-colors"
                    >
                      {p.displayName ?? p.platformName ?? p.playerId}
                    </a>
                    {p.manOfTheMatch && (
                      <span className="ml-1.5 text-highlight" title="Man of the Match">⭐</span>
                    )}
                  </td>
                  <td className={`px-3 py-2.5 text-center font-semibold ${p.rating ? ratingColor(p.rating) : "text-muted"}`}>
                    {p.rating?.toFixed(1) ?? "—"}
                  </td>
                  <td className="px-3 py-2.5 text-center">{p.goals || "—"}</td>
                  <td className="px-3 py-2.5 text-center">{p.assists || "—"}</td>
                  <td className="px-3 py-2.5 text-center">{p.shots || "—"}</td>
                  <td className="px-3 py-2.5 text-center">{p.passesCompleted}/{p.passesAttempted}</td>
                  <td className="px-3 py-2.5 text-center">{p.tacklesCompleted}/{p.tacklesAttempted}</td>
                  <td className="px-3 py-2.5 text-center">{p.redCards || "—"}</td>
                </tr>
              ))}
            </tbody>
          </table>

          {/* Mobile cards */}
          <div className="sm:hidden divide-y divide-border">
            {match.players.map((p) => (
              <div key={p.playerId} className="p-4">
                <div className="flex items-center justify-between mb-2">
                  <span className="font-medium text-text-primary">
                    {p.displayName ?? p.platformName ?? p.playerId}
                    {p.manOfTheMatch && <span className="ml-1 text-highlight">⭐</span>}
                  </span>
                  <span className={`font-semibold ${p.rating ? ratingColor(p.rating) : "text-muted"}`}>
                    {p.rating?.toFixed(1) ?? "—"}
                  </span>
                </div>
                <div className="grid grid-cols-4 gap-2 text-xs text-center text-muted">
                  <div><span className="block font-semibold text-text-soft">{p.goals}</span>Gols</div>
                  <div><span className="block font-semibold text-text-soft">{p.assists}</span>Assist.</div>
                  <div><span className="block font-semibold text-text-soft">{p.shots}</span>Fin.</div>
                  <div><span className="block font-semibold text-text-soft">{p.passesCompleted}/{p.passesAttempted}</span>Passes</div>
                </div>
              </div>
            ))}
          </div>
        </Panel>
      </section>
    </article>
  );
}

function SectionHeading({ kicker, title, subtitle }: { kicker: string; title: string; subtitle: string }) {
  return (
    <div className="mb-4 pb-4 border-b border-border/50">
      <p className="text-[11px] font-semibold uppercase tracking-wider text-muted mb-1">{kicker}</p>
      <h3 className="text-lg font-bold text-text-primary">{title}</h3>
      <p className="text-xs text-muted mt-0.5">{subtitle}</p>
    </div>
  );
}
