export const dynamic = "force-dynamic";

import { getMatches, getMatch } from "@/lib/services/match-service";
import { MatchesShell } from "@/components/matches/matches-shell";

export default async function MatchesPage({
  params,
  searchParams,
}: {
  params: Promise<{ clubId: string }>;
  searchParams: Promise<{ match?: string }>;
}) {
  const { clubId } = await params;
  const { match: selectedMatchId } = await searchParams;
  const result = await getMatches(clubId, 0, 50);

  const effectiveMatchId = selectedMatchId ?? result.content[0]?.matchId ?? null;
  const detail = effectiveMatchId ? await getMatch(clubId, effectiveMatchId) : null;

  return (
    <MatchesShell
      matches={result.content}
      selectedMatchId={effectiveMatchId}
      detail={detail}
      clubId={clubId}
    />
  );
}
