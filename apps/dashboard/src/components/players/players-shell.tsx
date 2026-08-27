"use client";

import { ArrowLeft } from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";

import { PlayerProfileView } from "@/components/players/player-profile-view";
import { PlayerXRaySkeleton } from "@/components/players/player-xray-skeleton";
import type { PlayerProfile, PlayerSummary } from "@/lib/domain/types";

function performanceBarColor(rating: number | null): { width: string; color: string } {
  if (rating == null) return { width: "0%", color: "transparent" };
  const pct = Math.max(0, Math.min(100, ((rating - 4) / 6) * 100));
  const color = rating >= 8.5 ? "#58a6ff" : rating >= 8 ? "#3fb950" : rating >= 7 ? "#7ee787" : rating >= 6 ? "#d29922" : "#f85149";
  return { width: `${pct}%`, color };
}

function playerFromLocation(): string | null {
  return new URLSearchParams(window.location.search).get("player");
}

function playerUrl(playerId: string | null): string {
  const params = new URLSearchParams(window.location.search);
  if (playerId) params.set("player", playerId);
  else params.delete("player");
  const query = params.toString();
  return `${window.location.pathname}${query ? `?${query}` : ""}`;
}

class ProfileLoadError extends Error {}

type PlayerDetailError = {
  playerId: string;
  message: string;
};

function isMobileViewport(): boolean {
  return window.matchMedia("(max-width: 1023px)").matches;
}

export function PlayersShell({
  players,
  selectedPlayerId: initialSelectedPlayerId,
  profile: initialProfile,
  clubId,
  clubName,
  showDetailOnMobile,
}: {
  players: PlayerSummary[];
  selectedPlayerId: string | null;
  profile: PlayerProfile | null;
  clubId: string;
  clubName: string;
  showDetailOnMobile: boolean;
}) {
  const [search, setSearch] = useState("");
  const [selectedPlayerId, setSelectedPlayerId] = useState(initialSelectedPlayerId);
  const [displayedProfile, setDisplayedProfile] = useState(initialProfile);
  const [loadingPlayerId, setLoadingPlayerId] = useState<string | null>(null);
  const [detailError, setDetailError] = useState<PlayerDetailError | null>(null);
  const [mobileShowDetail, setMobileShowDetail] = useState(showDetailOnMobile);
  const profileCache = useRef(new Map<string, PlayerProfile>());
  const requestSequence = useRef(0);
  const listScrollPosition = useRef(0);
  const playerButtonRefs = useRef(new Map<string, HTMLButtonElement>());

  useEffect(() => {
    if (initialSelectedPlayerId && initialProfile) profileCache.current.set(initialSelectedPlayerId, initialProfile);
  }, [initialProfile, initialSelectedPlayerId]);

  const loadProfile = useCallback(async (playerId: string) => {
    setSelectedPlayerId(playerId);
    setDetailError(null);
    const requestId = ++requestSequence.current;

    const cached = profileCache.current.get(playerId);
    if (cached) {
      setDisplayedProfile(cached);
      setLoadingPlayerId(null);
      return;
    }

    setDisplayedProfile(null);
    setLoadingPlayerId(playerId);
    try {
      const response = await fetch(`/api/clubs/${encodeURIComponent(clubId)}/players/${encodeURIComponent(playerId)}`, {
        cache: "no-store",
        headers: { Accept: "application/json" },
      });
      if (response.status === 404) throw new ProfileLoadError("Perfil não encontrado.");
      if (!response.ok) throw new ProfileLoadError("Não foi possível carregar o Raio-X agora.");
      const body = await response.json() as { profile?: PlayerProfile };
      if (!body.profile) throw new ProfileLoadError("Não foi possível carregar o Raio-X agora.");
      if (body.profile.playerId !== playerId) throw new ProfileLoadError("Não foi possível carregar o Raio-X agora.");

      profileCache.current.set(playerId, body.profile);
      if (requestId === requestSequence.current) {
        setDisplayedProfile(body.profile);
        setLoadingPlayerId(null);
      }
    } catch (error) {
      if (requestId === requestSequence.current) {
        setDisplayedProfile(null);
        setLoadingPlayerId(null);
        setDetailError({ playerId, message: error instanceof ProfileLoadError ? error.message : "Não foi possível carregar o Raio-X agora." });
      }
    }
  }, [clubId]);

  useEffect(() => {
    const handleHistoryNavigation = () => {
      const playerIdFromUrl = playerFromLocation();
      const playerId = playerIdFromUrl ?? players[0]?.playerId ?? null;
      if (!playerId) {
        requestSequence.current += 1;
        setDisplayedProfile(null);
        setLoadingPlayerId(null);
        setDetailError(null);
        setMobileShowDetail(false);
        return;
      }
      if (!playerIdFromUrl && isMobileViewport()) {
        requestSequence.current += 1;
        setDisplayedProfile(null);
        setLoadingPlayerId(null);
        setDetailError(null);
        setMobileShowDetail(false);
        return;
      }
      setMobileShowDetail(Boolean(playerIdFromUrl));
      if (playerId !== selectedPlayerId || displayedProfile?.playerId !== playerId) void loadProfile(playerId);
    };
    window.addEventListener("popstate", handleHistoryNavigation);
    return () => window.removeEventListener("popstate", handleHistoryNavigation);
  }, [displayedProfile, loadProfile, players, selectedPlayerId]);

  const selectPlayer = useCallback((playerId: string) => {
    const isMobile = isMobileViewport();
    if (isMobile) listScrollPosition.current = window.scrollY;

    if (playerId === selectedPlayerId) {
      setMobileShowDetail(true);
      if (isMobile) window.requestAnimationFrame(() => window.scrollTo({ top: 0, behavior: "auto" }));
      if (!profileCache.current.has(playerId) && loadingPlayerId !== playerId) void loadProfile(playerId);
      return;
    }
    // Native history keeps the shareable selection without re-running the page Server Component.
    window.history.pushState(null, "", playerUrl(playerId));
    setMobileShowDetail(true);
    if (isMobile) window.requestAnimationFrame(() => window.scrollTo({ top: 0, behavior: "auto" }));
    void loadProfile(playerId);
  }, [loadProfile, loadingPlayerId, selectedPlayerId]);

  const handleBack = useCallback(() => {
    requestSequence.current += 1;
    window.history.replaceState(null, "", playerUrl(null));
    setDisplayedProfile(null);
    setLoadingPlayerId(null);
    setDetailError(null);
    setMobileShowDetail(false);
    window.requestAnimationFrame(() => {
      window.scrollTo({ top: listScrollPosition.current, behavior: "auto" });
      if (selectedPlayerId) playerButtonRefs.current.get(selectedPlayerId)?.focus();
    });
  }, [selectedPlayerId]);

  const filtered = players.filter((player) => {
    if (!search) return true;
    const term = search.toLocaleLowerCase("pt-BR");
    const name = (player.displayName ?? player.platformName ?? "").toLocaleLowerCase("pt-BR");
    return name.includes(term);
  });
  const selectedPlayer = players.find((player) => player.playerId === selectedPlayerId) ?? null;
  const selectedPlayerName = selectedPlayer?.displayName ?? selectedPlayer?.platformName ?? null;
  const detailIsLoading = loadingPlayerId === selectedPlayerId;
  const visibleProfile = displayedProfile?.playerId === selectedPlayerId ? displayedProfile : null;
  const visibleError = detailError?.playerId === selectedPlayerId ? detailError : null;

  return (
    <div>
      <header className="players-page-header" style={{ marginBottom: 24 }}>
        <p style={{ fontSize: 12, textTransform: "uppercase", letterSpacing: "0.08em", color: "var(--color-text-muted)", marginBottom: 4 }}>
          EA FC 26 — {clubName}
        </p>
        <h1 style={{ fontSize: 24, fontWeight: 700, color: "var(--color-text-primary)", margin: 0 }}>Perfis de Jogadores</h1>
      </header>

      <div className="lg:grid lg:grid-cols-[minmax(260px,340px)_minmax(0,1fr)] gap-6">
        <style>{`
          .players-list-scroll { max-height: calc(100vh - 190px); overflow-y: auto; }
          .players-detail-content { transition: opacity 150ms ease; }
          @media (prefers-reduced-motion: reduce) { .players-detail-content { transition: none; } }
          @media (max-width: 1023px) {
            .players-page-header { margin-bottom: 16px !important; }
            .players-list-header { align-items: stretch !important; flex-direction: column; }
            .players-list-search { max-width: none !important; min-height: 40px; width: 100%; }
            .players-list-scroll { max-height: none !important; overflow: visible !important; }
            .players-list-item { min-height: 72px; padding: 14px 16px !important; }
            .players-detail-panel { min-width: 0; padding: 16px !important; }
          }
        `}</style>

        <div className={`${mobileShowDetail ? "hidden" : "block"} lg:block`} style={{ border: "1px solid var(--color-border)", borderRadius: 12, background: "var(--color-surface-panel)", overflow: "hidden" }}>
          <div className="players-list-header" style={{ padding: "14px 16px", borderBottom: "1px solid var(--color-border)", display: "flex", alignItems: "center", justifyContent: "space-between", gap: 12 }}>
            <h2 style={{ fontSize: 16, fontWeight: 700, color: "var(--color-text-primary)", margin: 0 }}>Jogadores</h2>
            <input
              type="search"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Buscar..."
              aria-label="Buscar jogador"
              className="players-list-search"
              style={{ flex: 1, maxWidth: 180, padding: "6px 10px", fontSize: 13, border: "1px solid var(--color-border)", borderRadius: 6, background: "var(--color-surface-raised)", color: "var(--color-text-primary)", outline: "none" }}
            />
          </div>

          <div className="players-list-scroll">
            {filtered.map((player) => {
              const isActive = player.playerId === selectedPlayerId;
              const bar = performanceBarColor(player.averageRating);
              const name = player.displayName ?? player.platformName ?? player.playerId;
              return (
                <button
                  key={player.playerId}
                  type="button"
                  onClick={() => selectPlayer(player.playerId)}
                  aria-pressed={isActive}
                  ref={(node) => {
                    if (node) playerButtonRefs.current.set(player.playerId, node);
                    else playerButtonRefs.current.delete(player.playerId);
                  }}
                  className={`players-list-item ${isActive ? "shadow-[inset_3px_0_var(--color-accent)]" : ""}`}
                  style={{ display: "grid", gap: 7, padding: "11px 16px", width: "100%", textAlign: "left", border: "none", cursor: "pointer", background: isActive ? "var(--color-surface-raised)" : "transparent", transition: "background 0.15s" }}
                  onMouseEnter={(event) => { if (!isActive) event.currentTarget.style.background = "var(--color-surface-raised)"; }}
                  onMouseLeave={(event) => { if (!isActive) event.currentTarget.style.background = "transparent"; }}
                >
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                    <span style={{ fontWeight: 600, fontSize: 14, color: "var(--color-text-primary)", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap", minWidth: 0 }}>{name}</span>
                    {player.averageRating != null && <span style={{ fontSize: 15, fontWeight: 800, fontVariantNumeric: "tabular-nums", color: "var(--color-text-primary)", marginLeft: 8, flexShrink: 0 }}>{player.averageRating.toFixed(2)}</span>}
                  </div>
                  <div style={{ height: 4, borderRadius: 2, background: "var(--color-border)", overflow: "hidden" }}>
                    <div style={{ height: "100%", width: bar.width, background: bar.color, borderRadius: 2, transition: "width 0.3s" }} />
                  </div>
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", fontSize: 12, color: "var(--color-text-muted)" }}>
                    <span style={{ overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap", minWidth: 0 }}>{player.matchesPlayed} partidas</span>
                  </div>
                </button>
              );
            })}
            {filtered.length === 0 && <p style={{ margin: 0, padding: "24px 16px", color: "var(--color-text-muted)", fontSize: 14 }}>Nenhum jogador encontrado.</p>}
          </div>
        </div>

        <section className={`players-detail-panel ${!mobileShowDetail ? "hidden" : "block"} lg:block`} aria-busy={detailIsLoading} style={{ border: "1px solid var(--color-border)", borderRadius: 12, background: "var(--color-surface-panel)", minHeight: 560, padding: 20, position: "relative" }}>
          {mobileShowDetail && <button type="button" onClick={handleBack} className="lg:hidden" style={{ display: "flex", alignItems: "center", gap: 6, fontSize: 14, color: "var(--color-accent)", background: "none", border: "none", cursor: "pointer", marginBottom: 16 }}>
            <ArrowLeft style={{ width: 16, height: 16 }} />
            Todos os jogadores
          </button>}

          <div className="players-detail-content">
            {detailIsLoading ? <PlayerXRaySkeleton playerName={selectedPlayerName} /> : visibleProfile ? <PlayerProfileView profile={visibleProfile} /> : visibleError ? <PlayerDetailFailure playerName={selectedPlayerName} message={visibleError.message} onRetry={() => selectedPlayerId && void loadProfile(selectedPlayerId)} /> : <EmptyDetail />}
          </div>

        </section>
      </div>
    </div>
  );
}

function PlayerDetailFailure({ playerName, message, onRetry }: { playerName: string | null; message: string; onRetry: () => void }) {
  return <div role="alert" style={{ display: "grid", placeItems: "center", minHeight: 500, color: "var(--color-danger)", fontSize: 14 }}>
    <div style={{ maxWidth: 360, textAlign: "center" }}>
      <p style={{ color: "var(--color-text-muted)", fontSize: 12, fontWeight: 800, letterSpacing: "0.08em", margin: "0 0 6px" }}>RAIO-X DO JOGADOR</p>
      {playerName && <h2 style={{ color: "var(--color-text-primary)", fontSize: 24, margin: "0 0 12px", overflowWrap: "anywhere" }}>{playerName}</h2>}
      <p style={{ margin: 0 }}>{message}</p>
      <button type="button" onClick={onRetry} style={{ marginTop: 16, border: "none", background: "none", color: "inherit", cursor: "pointer", fontWeight: 700, textDecoration: "underline" }}>Tentar novamente</button>
    </div>
  </div>;
}

function EmptyDetail() {
  return <div style={{ display: "grid", placeItems: "center", minHeight: 500, color: "var(--color-text-muted)", fontSize: 14 }}>
    <div style={{ textAlign: "center" }}>
      <div style={{ fontSize: 48, marginBottom: 8 }}>👤</div>
      <p>Selecione um jogador.</p>
    </div>
  </div>;
}
