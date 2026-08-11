import { notFound } from "next/navigation";
import { getClub } from "@/lib/repositories/overview-repository";
import { SportsApiNotFound, SportsApiUnavailable } from "@/lib/api/sports-client";
import OverviewPage from "@/app/clubs/[clubId]/(fullwidth)/overview/page";

export const dynamic = "force-dynamic";

export default async function PublicClubPage({ params }: { params: Promise<{ clubId: string }> }) {
  const { clubId } = await params;
  if (!/^\d+$/.test(clubId)) notFound();
  try {
    await getClub(clubId);
  } catch (error) {
    if (error instanceof SportsApiNotFound) notFound();
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
  return <OverviewPage params={params} />;
}
