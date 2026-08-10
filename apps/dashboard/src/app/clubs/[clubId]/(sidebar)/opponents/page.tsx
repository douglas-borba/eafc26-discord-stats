export const dynamic = "force-dynamic";

import { getOpponents, getOpponent } from "@/lib/services/opponent-service";
import { OpponentsShell } from "@/components/opponents/opponents-shell";
import { getClub } from "@/lib/repositories/overview-repository";

export default async function OpponentsPage({
  params,
  searchParams,
}: {
  params: Promise<{ clubId: string }>;
  searchParams: Promise<{ opponent?: string }>;
}) {
  const { clubId } = await params;
  const { opponent: selectedOpponentId } = await searchParams;
  const [club, result] = await Promise.all([getClub(clubId), getOpponents(clubId, 0, 100)]);

  const effectiveOpponentId = selectedOpponentId ?? result.content[0]?.clubId ?? null;
  const history = effectiveOpponentId ? await getOpponent(clubId, effectiveOpponentId) : null;

  return (
    <OpponentsShell
      opponents={result.content}
      selectedOpponentId={effectiveOpponentId}
      history={history}
      clubId={clubId}
      clubName={club.displayName}
    />
  );
}
