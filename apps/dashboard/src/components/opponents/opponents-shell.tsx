"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { useState, useCallback } from "react";
import { SearchInput } from "@/components/ui/search-input";
import { OpponentHistoryView } from "@/components/opponents/opponent-history-view";
import { ArrowLeft } from "lucide-react";
import type { OpponentSummary, OpponentHistory } from "@/lib/domain/types";

export function OpponentsShell({
  opponents,
  selectedOpponentId,
  history,
  clubId,
}: {
  opponents: OpponentSummary[];
  selectedOpponentId: string | null;
  history: OpponentHistory | null;
  clubId: string;
}) {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [search, setSearch] = useState("");
  const [mobileShowDetail, setMobileShowDetail] = useState(!!searchParams.get("opponent"));

  const filtered = opponents.filter((o) => {
    if (!search) return true;
    const term = search.toLocaleLowerCase("pt-BR");
    return (o.clubName ?? "").toLocaleLowerCase("pt-BR").includes(term);
  });

  const handleSelect = useCallback(
    (opponentId: string) => {
      router.push(`/clubs/${clubId}/opponents?opponent=${opponentId}`, { scroll: false });
      setMobileShowDetail(true);
    },
    [router, clubId]
  );

  const handleBack = useCallback(() => {
    setMobileShowDetail(false);
  }, []);

  return (
    <div className="lg:grid lg:grid-cols-[minmax(280px,350px)_minmax(0,1fr)] lg:gap-6">
      {/* Left: opponent index */}
      <div className={`${mobileShowDetail ? "hidden" : "block"} lg:block`}>
        <h1 className="text-xl font-bold text-text-primary mb-4">Adversários</h1>
        <div className="lg:sticky lg:top-[18px]">
          <SearchInput value={search} onChange={setSearch} placeholder="Nome do clube" />
          <div className="mt-3 lg:max-h-[calc(100vh-160px)] lg:overflow-y-auto space-y-0.5 pr-1">
            {filtered.map((o) => {
              const isActive = o.clubId === selectedOpponentId;
              return (
                <button
                  key={o.clubId}
                  onClick={() => handleSelect(o.clubId)}
                  className={`w-full text-left block px-3 py-2.5 rounded-lg transition-colors ${
                    isActive
                      ? "shadow-[inset_3px_0_var(--color-accent)] bg-accent/8"
                      : "hover:bg-surface-raised"
                  }`}
                >
                  <p className="text-sm font-medium text-text-primary truncate">
                    {o.clubName ?? o.clubId}
                  </p>
                  <p className="text-xs text-muted mt-0.5">
                    {o.matchesPlayed} confrontos &mdash;{" "}
                    <span className="text-win font-semibold">{o.wins}V</span>{" "}
                    <span className="text-draw font-semibold">{o.draws}E</span>{" "}
                    <span className="text-loss font-semibold">{o.losses}D</span>
                  </p>
                </button>
              );
            })}
            {filtered.length === 0 && (
              <p className="text-sm text-muted text-center py-8">Nenhum adversário encontrado.</p>
            )}
          </div>
        </div>
      </div>

      {/* Right: history */}
      <div className={`${!mobileShowDetail ? "hidden" : "block"} lg:block`}>
        {mobileShowDetail && (
          <button
            onClick={handleBack}
            className="flex items-center gap-1.5 text-sm text-accent mb-4 lg:hidden"
          >
            <ArrowLeft className="w-4 h-4" />
            Todos os adversários
          </button>
        )}
        {history ? (
          <OpponentHistoryView history={history} clubId={clubId} />
        ) : (
          <p className="text-muted text-sm text-center py-12">Nenhum adversário disponível.</p>
        )}
      </div>
    </div>
  );
}
