type PlayerXRaySkeletonProps = {
  playerName: string | null;
};

export function PlayerXRaySkeleton({ playerName }: PlayerXRaySkeletonProps) {
  return (
    <div className="player-xray-skeleton" role="status" aria-live="polite" aria-label="Carregando Raio-X do jogador">
      <header className="player-xray-skeleton-header">
        <div>
          <p>RAIO-X DO JOGADOR</p>
          {playerName ? <h2>{playerName}</h2> : <SkeletonLine width="42%" height={32} />}
          <span>Carregando Raio-X...</span>
        </div>
        <div className="player-xray-skeleton-records"><SkeletonLine width={38} height={28} /><SkeletonLine width={38} height={28} /><SkeletonLine width={38} height={28} /></div>
      </header>

      <div className="player-xray-skeleton-kpis">{Array.from({ length: 5 }, (_, index) => <SkeletonBlock key={index} height={62} />)}</div>
      <SkeletonSection titleWidth="30%" lines={3} />
      <SkeletonSection titleWidth="24%" lines={4} />
      <div className="player-xray-skeleton-stat-grid">
        <SkeletonSection titleWidth="45%" lines={5} />
        <SkeletonSection titleWidth="45%" lines={6} />
        <SkeletonSection titleWidth="45%" lines={4} />
      </div>
      <SkeletonSection titleWidth="36%" lines={3} />

      <style>{`
        .player-xray-skeleton { min-width:0; }
        .player-xray-skeleton-header { display:flex; justify-content:space-between; align-items:flex-end; gap:16px; margin-bottom:20px; }
        .player-xray-skeleton-header p { color:var(--color-text-muted); font-size:12px; font-weight:800; letter-spacing:.08em; margin:0 0 5px; }
        .player-xray-skeleton-header h2 { color:var(--color-text-primary); font-size:29px; line-height:1.15; margin:0; overflow-wrap:anywhere; }
        .player-xray-skeleton-header span { color:var(--color-text-muted); display:block; font-size:13px; margin-top:6px; }
        .player-xray-skeleton-records { display:flex; flex-wrap:wrap; gap:6px; justify-content:flex-end; }
        .player-xray-skeleton-kpis { display:grid; grid-template-columns:repeat(5,minmax(0,1fr)); gap:8px; }
        .player-xray-skeleton-stat-grid { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:8px; margin-top:28px; }
        .player-xray-skeleton-section { margin-top:28px; }
        .player-xray-skeleton-surface { background:var(--color-surface-raised); border:1px solid var(--color-border); border-radius:9px; padding:14px; }
        .player-xray-skeleton-title { margin-bottom:10px; }
        .player-xray-skeleton-lines { display:grid; gap:10px; }
        .player-xray-skeleton-line { animation:player-xray-skeleton-pulse 1.3s ease-in-out infinite; background:color-mix(in srgb, var(--color-text-muted) 17%, transparent); border-radius:999px; }
        @keyframes player-xray-skeleton-pulse { 0%,100% { opacity:.52; } 50% { opacity:1; } }
        @media (max-width:800px) { .player-xray-skeleton-header { align-items:flex-start; flex-direction:column; } .player-xray-skeleton-kpis { grid-template-columns:repeat(2,minmax(0,1fr)); } .player-xray-skeleton-stat-grid { grid-template-columns:repeat(2,minmax(0,1fr)); } }
        @media (max-width:480px) { .player-xray-skeleton-stat-grid { grid-template-columns:1fr; } }
        @media (prefers-reduced-motion:reduce) { .player-xray-skeleton-line { animation:none; } }
      `}</style>
    </div>
  );
}

function SkeletonSection({ titleWidth, lines }: { titleWidth: string; lines: number }) {
  return <section className="player-xray-skeleton-section"><SkeletonLine className="player-xray-skeleton-title" width={titleWidth} height={12} /><div className="player-xray-skeleton-surface player-xray-skeleton-lines">{Array.from({ length: lines }, (_, index) => <SkeletonLine key={index} width={`${88 - (index % 3) * 16}%`} height={12} />)}</div></section>;
}

function SkeletonBlock({ height }: { height: number }) {
  return <div className="player-xray-skeleton-surface" style={{ minHeight: height }}><SkeletonLine width="44%" height={20} /><SkeletonLine width="70%" height={10} /></div>;
}

function SkeletonLine({ width, height, className = "" }: { width: string | number; height: number; className?: string }) {
  return <span className={`player-xray-skeleton-line ${className}`} style={{ display: "block", width, height }} />;
}
