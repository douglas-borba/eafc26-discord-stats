export const dynamic = "force-dynamic";

import { OverviewLatestMatch } from "@/components/overview/overview-latest-match";
import { OverviewMatchCard } from "@/components/overview/overview-match-card";
import { getLatestMatchCard } from "@/lib/services/match-card-service";

export default async function OverviewPage({ params }: { params: Promise<{ clubId: string }> }) {
  const { clubId } = await params;

  // Fetch match card data from Supabase only - no Spring Boot dependency
  const cardData = await getLatestMatchCard(clubId);
  const presentation = cardData.status === "success" ? cardData.presentation : null;

  return (
    <div className="grid grid-cols-1 lg:grid-cols-[340px_minmax(0,1fr)] gap-4 lg:gap-4 items-start">
      {/* Left Column - Only Latest Match panel (Actions removed until public backend exists) */}
      <div className="flex flex-col gap-[0.6rem]">
        {/* Latest Match Panel */}
        <OverviewLatestMatch presentation={presentation} />
      </div>

      {/* Right Column - Full Match Card (protagonist) */}
      <div className="lg:sticky lg:top-4">
        <OverviewMatchCard presentation={presentation} />
      </div>
    </div>
  );
}
