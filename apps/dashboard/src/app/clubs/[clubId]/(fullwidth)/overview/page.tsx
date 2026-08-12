export const dynamic = "force-dynamic";

import { OverviewClubPanel } from "@/components/overview/overview-club-panel";
import { OverviewMatchCarousel } from "@/components/overview/overview-match-carousel";
import { getOverviewCards } from "@/lib/services/match-card-service";
import { buildSequenceEditorial } from "@/lib/services/sequence-editorial-service";
import { fetchAiPanorama } from "@/lib/api/panorama-client";
import { getClub } from "@/lib/repositories/overview-repository";
import { SportsApiUnavailable } from "@/lib/api/sports-client";
import { TrialNotice } from "@/components/club/trial-notice";

export default async function OverviewPage({ params }: { params: Promise<{ clubId: string }> }) {
  const { clubId } = await params;

  try {
    const [club, presentations, aiNarrative] = await Promise.all([
      getClub(clubId),
      getOverviewCards(clubId, 10),
      fetchAiPanorama(clubId),
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
      <><TrialNotice status={club.accessStatus} count={club.trialMatchesCount} limit={club.trialLimit} /><div className="grid grid-cols-1 lg:grid-cols-[300px_minmax(0,1fr)] min-h-screen">
        <aside className="lg:sticky lg:top-0 lg:h-screen border-r border-[#21262d] bg-[#0d1117] px-5 py-6 overflow-y-auto">
          <OverviewClubPanel
            clubId={clubId}
            clubName={club.displayName}
            editorial={editorial}
            presentations={presentations}
          />
        </aside>
        <OverviewMatchCarousel presentations={presentations} />
      </div></>
    );
  } catch (error) {
    if (error instanceof SportsApiUnavailable) {
      return (
        <div className="flex items-center justify-center min-h-screen">
          <div className="text-center text-[#6e7681]">
            <div className="text-[3rem] mb-4 opacity-50">⚠️</div>
            <div className="text-[1rem] font-medium text-[#c9d1d9]">Não foi possível carregar os dados agora</div>
            <div className="text-[0.8rem] mt-1.5">Tente novamente em instantes.</div>
          </div>
        </div>
      );
    }
    throw error;
  }
}
