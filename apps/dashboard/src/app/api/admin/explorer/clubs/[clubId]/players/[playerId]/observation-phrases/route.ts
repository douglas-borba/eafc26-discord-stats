import { proxyAdminRequest } from "@/lib/admin/backend";

export const dynamic = "force-dynamic";

export async function GET(_request: Request, { params }: { params: Promise<{ clubId: string; playerId: string }> }) {
  const { clubId, playerId } = await params;
  return proxyAdminRequest(`/api/admin/explorer/clubs/${encodeURIComponent(clubId)}/players/${encodeURIComponent(playerId)}/observation-phrases`);
}
