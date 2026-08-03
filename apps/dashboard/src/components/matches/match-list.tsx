"use client";

import Link from "next/link";
import { useParams, usePathname } from "next/navigation";
import { OutcomeBadge } from "@/components/ui/outcome-badge";
import { formatDate } from "@/lib/utils";
import type { MatchSummary } from "@/lib/domain/types";

export function MatchList({ matches }: { matches: MatchSummary[] }) {
  const params = useParams();
  const pathname = usePathname();
  const clubId = params.clubId as string;

  return (
    <div className="space-y-1">
      {matches.map((m) => {
        const isActive = pathname.includes(m.matchId);
        return (
          <Link
            key={m.matchId}
            href={`/clubs/${clubId}/matches/${m.matchId}`}
            className={`flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm transition-colors ${isActive ? "bg-accent/10 border border-accent/30" : "hover:bg-surface-raised border border-transparent"}`}
          >
            <OutcomeBadge outcome={m.outcome} />
            <div className="flex-1 min-w-0">
              <p className="text-text-primary font-medium truncate">
                {m.ourClubName ?? "Nós"} {m.ourScore} × {m.opponentScore} {m.opponentClubName ?? "Adversário"}
              </p>
              <p className="text-xs text-muted">{formatDate(m.playedAt)}</p>
            </div>
          </Link>
        );
      })}
    </div>
  );
}
