export const dynamic = "force-dynamic";

import { getOverview } from "@/lib/repositories/overview-repository";
import { OverviewCards, RecordStrip } from "@/components/overview/overview-cards";
import { formatDateTime } from "@/lib/utils";
import { notFound } from "next/navigation";

export default async function OverviewPage({ params }: { params: Promise<{ clubId: string }> }) {
  const { clubId } = await params;
  const overview = await getOverview(clubId);
  if (!overview) notFound();

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-xl font-bold text-text-primary">Visão Geral</h1>
        {overview.clubName && <p className="text-sm text-muted mt-0.5">{overview.clubName}</p>}
      </div>

      <OverviewCards overview={overview} />
      <RecordStrip overview={overview} />

      {overview.lastMatchAt && (
        <p className="text-xs text-muted mt-4">
          Última partida: {formatDateTime(overview.lastMatchAt)}
        </p>
      )}
    </div>
  );
}
