export const dynamic = "force-dynamic";

import { getOpponents, getOpponent } from "@/lib/services/opponent-service";
import { OpponentList } from "@/components/opponents/opponent-list";
import { OpponentHistoryView } from "@/components/opponents/opponent-history-view";
import { notFound } from "next/navigation";

export default async function OpponentHistoryPage({
  params,
}: {
  params: Promise<{ clubId: string; opponentClubId: string }>;
}) {
  const { clubId, opponentClubId } = await params;
  const [opponentsResult, history] = await Promise.all([
    getOpponents(clubId, 0, 100),
    getOpponent(clubId, opponentClubId),
  ]);

  if (!history) notFound();

  return (
    <div className="lg:grid lg:grid-cols-[minmax(260px,340px)_minmax(0,1fr)] lg:gap-6">
      <div className="hidden lg:block">
        <h1 className="text-xl font-bold text-text-primary mb-4">Adversários</h1>
        <div className="lg:sticky lg:top-[26px] lg:max-h-[calc(100vh-52px)] lg:overflow-y-auto">
          <OpponentList opponents={opponentsResult.content} />
        </div>
      </div>
      <div>
        <OpponentHistoryView history={history} />
      </div>
    </div>
  );
}
