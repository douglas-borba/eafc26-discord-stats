import { proxyAdminRequest } from "@/lib/admin/backend";

export const dynamic = "force-dynamic";

export async function GET(_request: Request, { params }: { params: Promise<{ clubId: string; matchId: string; playerId: string }> }) {
  const { clubId, matchId, playerId } = await params;
  return proxyAdminRequest(`/api/admin/explorer/clubs/${clubId}/matches/${matchId}/players/${playerId}`);
}
