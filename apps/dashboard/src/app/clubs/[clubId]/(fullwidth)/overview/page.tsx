export const dynamic = "force-dynamic";

import { OverviewClubPanel } from "@/components/overview/overview-club-panel";
import { OverviewMatchCarousel } from "@/components/overview/overview-match-carousel";
import { getRecentMatchCards } from "@/lib/services/match-card-service";
import { buildSequenceEditorial } from "@/lib/services/sequence-editorial-service";
import { fetchAiPanorama } from "@/lib/api/panorama-client";

export default async function OverviewPage({ params }: { params: Promise<{ clubId: string }> }) {
  const { clubId } = await params;

  const [presentations, aiNarrative] = await Promise.all([
    getRecentMatchCards(clubId, 10), // 10 for both match cards and editorial analysis
    fetchAiPanorama(),                // Fetch from REST API (validates contextKey in backend)
  ]);
  const editorial = buildSequenceEditorial(presentations);
  editorial.aiNarrative = aiNarrative;

  if (presentations.length === 0) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <div className="text-center text-[#6e7681]">
          <div className="text-[3rem] mb-4 opacity-50">⚽</div>
          <div className="text-[1rem] font-medium text-[#c9d1d9]">Nenhuma partida processada ainda</div>
          <div className="text-[0.8rem] mt-1.5">Aguardando os primeiros resultados.</div>
        </div>
      </div>
    );
  }

  return (
    <div className="grid grid-cols-1 lg:grid-cols-[300px_minmax(0,1fr)] min-h-screen">
      {/* Column 1: Club panel */}
      <aside className="lg:sticky lg:top-0 lg:h-screen border-r border-[#21262d] bg-[#0d1117] px-5 py-6 overflow-y-auto">
        <OverviewClubPanel
          clubId={clubId}
          editorial={editorial}
          presentations={presentations}
        />
      </aside>

      {/* Columns 2-4: Match Cards with Carousel */}
      <OverviewMatchCarousel presentations={presentations} />
    </div>
  );
}
