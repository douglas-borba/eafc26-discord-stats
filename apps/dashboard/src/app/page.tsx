import { redirect } from "next/navigation";
import { listClubs } from "@/lib/repositories/overview-repository";
import { SportsApiUnavailable } from "@/lib/api/sports-client";

export const dynamic = "force-dynamic";

export default async function Home() {
  try {
    const clubs = await listClubs();
    if (clubs.length > 0) {
      redirect(`/${clubs[0].clubId}`);
    }
    return (
      <div className="flex items-center justify-center min-h-screen">
        <p className="text-muted">Nenhum clube cadastrado.</p>
      </div>
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
