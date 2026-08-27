import type { CSSProperties, ReactNode } from "react";
import type { PlayerMetricPeriod, PlayerProfile, PlayerSingleMatchRecord } from "@/lib/domain/types";

const sectionTitle: CSSProperties = { fontSize: 12, fontWeight: 800, letterSpacing: "0.08em", color: "var(--color-text-muted)", margin: "28px 0 10px" };
const surfaceStyle: CSSProperties = { border: "1px solid var(--color-border)", borderRadius: 9, padding: 14, background: "var(--color-surface-raised)" };
const analysisTextStyle: CSSProperties = { fontSize: 14, lineHeight: 1.55, color: "var(--color-text-primary)", margin: "0 0 7px" };

export function PlayerProfileView({ profile }: { profile: PlayerProfile }) {
  const xRay = profile.xRay;
  const name = profile.displayName ?? profile.platformName ?? profile.playerId;
  const directContributions = profile.totalGoals + profile.totalAssists;

  return (
    <div>
      <header style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-end", gap: 16, marginBottom: 20 }}>
        <div>
          <p style={{ ...sectionTitle, margin: "0 0 5px" }}>RAIO-X DO JOGADOR</p>
          <h2 style={{ fontSize: 29, lineHeight: 1.15, margin: 0, color: "var(--color-text-primary)" }}>{name}</h2>
          <p style={{ color: "var(--color-text-muted)", fontSize: 13, margin: "6px 0 0" }}>{profile.ratedMatchCount} partidas com nota no histórico elegível</p>
        </div>
        <div style={{ display: "flex", gap: 6, flexWrap: "wrap", justifyContent: "flex-end" }}>
          <RecordBadge color="#3fb950" value={profile.wins} suffix="V" />
          <RecordBadge color="#d29922" value={profile.draws} suffix="E" />
          <RecordBadge color="#f85149" value={profile.losses} suffix="D" />
        </div>
      </header>

      <div className="xray-primary-metrics">
        <Metric value={profile.matchesPlayed} label="Partidas" />
        <Metric value={profile.averageRating == null ? "—" : profile.averageRating.toFixed(2)} label="Nota média" />
        <Metric value={profile.totalGoals} label="Gols" />
        <Metric value={profile.totalAssists} label="Assistências" />
        <Metric value={directContributions} label="Participações diretas" />
      </div>

      {xRay ? <XRayContent xRay={xRay} profile={profile} /> : <p style={{ color: "var(--color-text-muted)", fontSize: 14 }}>Ainda não há dados básicos suficientes para gerar o Raio-X deste perfil.</p>}

      <style>{`
        .xray-primary-metrics { display:grid; grid-template-columns:repeat(5,minmax(0,1fr)); gap:8px; }
        .xray-stat-grid { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:8px; }
        .xray-record-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:8px; }
        @media (max-width: 800px) { .xray-primary-metrics { grid-template-columns:repeat(2,minmax(0,1fr)); } .xray-stat-grid { grid-template-columns:repeat(2,minmax(0,1fr)); } }
        @media (max-width: 480px) { .xray-stat-grid,.xray-record-grid { grid-template-columns:1fr; } }
      `}</style>
    </div>
  );
}

function XRayContent({ profile, xRay }: { profile: PlayerProfile; xRay: NonNullable<PlayerProfile["xRay"]> }) {
  const analysisLines = [xRay.analysis.summary, ...xRay.analysis.strengths, xRay.analysis.currentForm].filter(Boolean) as string[];
  return <>
    <section>
      <h3 style={sectionTitle}>ANÁLISE DO JOGADOR</h3>
      <div style={surfaceStyle}>
        {analysisLines.map((line) => <p key={line} style={analysisTextStyle}>{line}</p>)}
        {xRay.analysis.opportunity && <div style={{ borderTop: "1px solid var(--color-border)", marginTop: 12, paddingTop: 12 }}>
          <p style={{ ...sectionTitle, margin: "0 0 5px", color: "#d29922" }}>OPORTUNIDADE DE EVOLUÇÃO</p>
          <p style={analysisTextStyle}>{xRay.analysis.opportunity.message}</p>
        </div>}
      </div>
    </section>

    <section>
      <h3 style={sectionTitle}>MOMENTO ATUAL</h3>
      <CurrentForm form={xRay.currentForm} />
    </section>

    <div className="xray-stat-grid">
      <MetricSection title="ATAQUE" values={[
        ["Gols", xRay.attack.goals], ["Gols por jogo", decimal(xRay.attack.goalsPerMatch)],
        ["Finalizações", xRay.attack.shots], ["Finalizações por jogo", decimal(xRay.attack.shotsPerMatch)],
        ["Conversão de finalizações", percentOrDash(xRay.attack.finishingConversion)],
      ]} />
      <MetricSection title="CRIAÇÃO" values={[
        ["Assistências", xRay.creation.assists], ["Assistências por jogo", decimal(xRay.creation.assistsPerMatch)],
        ["Passes tentados", xRay.creation.passesAttempted], ["Passes completos", xRay.creation.passesCompleted],
        ["Precisão de passe", percentOrDash(xRay.creation.passAccuracy)], ["Participações diretas", xRay.creation.directContributions],
        ["Participações por jogo", decimal(xRay.creation.directContributionsPerMatch)],
      ]} />
      <MetricSection title="DEFESA" values={[
        ["Desarmes tentados", xRay.defense.tacklesAttempted], ["Desarmes certos", xRay.defense.tacklesCompleted],
        ["Eficiência de desarme", percentOrDash(xRay.defense.tackleEfficiency)], ["Desarmes certos por jogo", decimal(xRay.defense.tacklesCompletedPerMatch)],
      ]} />
    </div>

    {xRay.oneOnOne && <section>
      <h3 style={sectionTitle}>1 CONTRA 1</h3>
      <div style={surfaceStyle}>
        <p style={{ ...analysisTextStyle, color: "var(--color-text-muted)", marginBottom: 10 }}>Dados avançados em {xRay.oneOnOne.coveredAppearances} de {xRay.advancedCoverage.eligibleAppearances} partidas elegíveis.</p>
        <div style={{ display: "flex", gap: 30, flexWrap: "wrap" }}><Fact label="Dribles completos" value={xRay.oneOnOne.dribblesCompleted} /><Fact label="Adversários superados" value={xRay.oneOnOne.opponentsBeaten} /></div>
      </div>
    </section>}

    <section>
      <h3 style={sectionTitle}>RECONHECIMENTOS</h3>
      <div style={{ display: "flex", gap: 18, flexWrap: "wrap", ...surfaceStyle }}>
        <Fact label="⭐ Craques" value={xRay.recognitions.craques} /><Fact label="📉 Menor Desempenho" value={xRay.recognitions.bagres} />
        <Fact label="🛡️ Xerifes" value={xRay.recognitions.xerifes} /><Fact label="🟥 Cartões vermelhos" value={profile.redCardCount} />
      </div>
    </section>

    <section>
      <h3 style={sectionTitle}>RECORDES PESSOAIS</h3>
      <div className="xray-record-grid">
        <Record label="Mais gols em uma partida" record={xRay.records.mostGoalsInMatch} />
        <Record label="Mais assistências em uma partida" record={xRay.records.mostAssistsInMatch} />
        <Record label="Mais participações diretas" record={xRay.records.mostDirectContributionsInMatch} />
        <MetricSection title="SEQUÊNCIAS" values={[
          ["Marcando", `${xRay.records.scoringStreak} partidas`], ["Dando assistência", `${xRay.records.assistStreak} partidas`],
          ["Participando de gol", `${xRay.records.directContributionStreak} partidas`], ["Partidas com nota 10", xRay.records.ratingTenMatches],
        ]} />
      </div>
    </section>
  </>;
}

function CurrentForm({ form }: { form: NonNullable<PlayerProfile["xRay"]>["currentForm"] }) {
  if (form.state === "FORMING") return <p style={{ ...analysisTextStyle, color: "var(--color-text-muted)" }}>Histórico em formação.</p>;
  const rows: [string, keyof PlayerMetricPeriod][] = [["Nota média", "averageRating"], ["Gols / jogo", "goalsPerMatch"], ["Assistências / jogo", "assistsPerMatch"], ["Participações / jogo", "directContributionsPerMatch"]];
  return <div style={surfaceStyle}>
    <div style={{ display: "grid", gridTemplateColumns: form.previous ? "minmax(120px,1fr) repeat(2,minmax(0,1fr))" : "minmax(120px,1fr) minmax(0,1fr)", gap: 8, alignItems: "center" }}>
      <span /><HeaderMetric>Últimas 5</HeaderMetric>{form.previous && <HeaderMetric>Histórico anterior</HeaderMetric>}
      {rows.map(([label, key]) => <MetricComparison key={label} label={label} recent={form.recent?.[key] ?? null} previous={form.previous?.[key] ?? null} />)}
    </div>
    {form.state === "RECENT_ONLY" && <p style={{ ...analysisTextStyle, color: "var(--color-text-muted)", marginTop: 12 }}>Ainda não há histórico anterior suficiente para comparar tendências.</p>}
  </div>;
}

function MetricComparison({ label, recent, previous }: { label:string; recent:number|null; previous:number|null }) { return <><span style={{ color:"var(--color-text-muted)",fontSize:13 }}>{label}</span><span style={{ color:"var(--color-text-primary)",fontWeight:700,fontVariantNumeric:"tabular-nums" }}>{metricValue(recent)}</span>{previous !== null && <span style={{ color:"var(--color-text-primary)",fontWeight:700,fontVariantNumeric:"tabular-nums" }}>{metricValue(previous)}</span>}</>; }
function HeaderMetric({ children }: { children: ReactNode }) { return <span style={{ color:"var(--color-text-muted)",fontSize:11,textTransform:"uppercase",letterSpacing:"0.05em" }}>{children}</span>; }
function MetricSection({ title, values }: { title:string; values:[string,string|number][] }) { return <section style={surfaceStyle}><h3 style={{ ...sectionTitle, margin:"0 0 10px" }}>{title}</h3><div style={{ display:"grid",gap:7 }}>{values.map(([label,value])=><div key={label} style={{display:"flex",justifyContent:"space-between",gap:10,fontSize:13}}><span style={{color:"var(--color-text-muted)"}}>{label}</span><strong style={{color:"var(--color-text-primary)",fontVariantNumeric:"tabular-nums"}}>{value}</strong></div>)}</div></section>; }
function Metric({ label, value }: { label:string; value:string|number }) { return <div style={{ padding:"11px 12px",border:"1px solid var(--color-border)",borderRadius:8,background:"var(--color-surface-raised)" }}><strong style={{display:"block",color:"var(--color-text-primary)",fontSize:20,fontVariantNumeric:"tabular-nums"}}>{value}</strong><span style={{color:"var(--color-text-muted)",fontSize:11}}>{label}</span></div>; }
function Fact({ label, value }: { label:string; value:string|number }) { return <div><strong style={{display:"block",fontSize:18,color:"var(--color-text-primary)",fontVariantNumeric:"tabular-nums"}}>{value}</strong><span style={{color:"var(--color-text-muted)",fontSize:12}}>{label}</span></div>; }
function Record({ label, record }: { label:string; record:PlayerSingleMatchRecord|null }) { return <div style={surfaceStyle}><p style={{ ...sectionTitle,margin:"0 0 7px" }}>{label}</p>{record ? <><strong style={{fontSize:21,color:"var(--color-text-primary)"}}>{record.value}</strong><p style={{ ...analysisTextStyle,color:"var(--color-text-muted)",marginTop:5 }}>{record.opponentClubName ?? "Adversário"} · {new Intl.DateTimeFormat("pt-BR").format(new Date(record.playedAt))}</p></> : <p style={{ ...analysisTextStyle,color:"var(--color-text-muted)" }}>Ainda não registrado.</p>}</div>; }
function RecordBadge({ color, value, suffix }: { color:string; value:number; suffix:string }) { return <span style={{color,background:`color-mix(in srgb, ${color} 12%, transparent)`,padding:"5px 9px",borderRadius:999,fontWeight:700,fontSize:13}}>{value}{suffix}</span>; }
function decimal(value:number):string { return value.toFixed(2); }
function percentOrDash(value:number|null):string { return value === null ? "—" : `${value.toFixed(1)}%`; }
function metricValue(value:number|null):string { return value === null ? "—" : value.toFixed(2); }
