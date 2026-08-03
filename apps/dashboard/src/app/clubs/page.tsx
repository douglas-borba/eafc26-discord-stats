export const dynamic = "force-dynamic";

import Link from "next/link";
import { listClubs } from "@/lib/repositories/overview-repository";
import { Panel } from "@/components/ui/panel";

export default async function ClubsPage() {
  const clubs = await listClubs();

  if (clubs.length === 1) {
    const { redirect } = await import("next/navigation");
    redirect(`/clubs/${clubs[0].clubId}/overview`);
  }

  return (
    <div className="min-h-screen flex items-center justify-center p-6">
      <div className="w-full max-w-md">
        <div className="flex items-center gap-3 mb-8 justify-center">
          <div
            className="w-[48px] h-[48px] rounded-xl flex items-center justify-center text-white font-bold text-lg"
            style={{ background: "linear-gradient(135deg, #2f81f7, #3fb950)" }}
          >
            FC
          </div>
          <h1 className="text-2xl font-bold text-text-primary">EA FC STATS</h1>
        </div>

        <div className="space-y-3">
          {clubs.map((club) => (
            <Link key={club.clubId} href={`/clubs/${club.clubId}/overview`}>
              <Panel className="hover:border-accent/50 transition-colors cursor-pointer">
                <p className="text-base font-semibold text-text-primary">{club.clubName ?? club.clubId}</p>
                <p className="text-sm text-muted mt-1">{club.totalMatches} partidas</p>
              </Panel>
            </Link>
          ))}
        </div>
      </div>
    </div>
  );
}
