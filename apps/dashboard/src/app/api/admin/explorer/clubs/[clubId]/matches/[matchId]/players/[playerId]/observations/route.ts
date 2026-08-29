import { proxyAdminRequest } from "@/lib/admin/backend";

export const dynamic = "force-dynamic";

export async function GET(_request: Request, { params }: { params: Promise<{ clubId: string; matchId: string; playerId: string }> }) {
  const { clubId, matchId, playerId } = await params;
  return proxyAdminRequest(`/api/admin/explorer/clubs/${encodeURIComponent(clubId)}/matches/${encodeURIComponent(matchId)}/players/${encodeURIComponent(playerId)}/observations`);
}

export async function POST(request: Request, { params }: { params: Promise<{ clubId: string; matchId: string; playerId: string }> }) {
  const { clubId, matchId, playerId } = await params;
  return proxyAdminRequest(
    `/api/admin/explorer/clubs/${encodeURIComponent(clubId)}/matches/${encodeURIComponent(matchId)}/players/${encodeURIComponent(playerId)}/observations`,
    { method: "POST", body: await request.text() },
  );
}
