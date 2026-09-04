import { proxyAdminRequest } from "@/lib/admin/backend";

export const dynamic = "force-dynamic";

export async function POST(request: Request, { params }: { params: Promise<{ clubId: string; matchId: string; playerId: string }> }) {
  const { clubId, matchId, playerId } = await params;
  return proxyAdminRequest(
    `/api/admin/explorer/clubs/${encodeURIComponent(clubId)}/matches/${encodeURIComponent(matchId)}/players/${encodeURIComponent(playerId)}/observations/reconcile`,
    { method: "POST", body: await request.text() },
  );
}
