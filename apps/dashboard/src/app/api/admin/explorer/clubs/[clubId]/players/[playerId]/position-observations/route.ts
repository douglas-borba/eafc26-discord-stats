import { proxyAdminRequest } from "@/lib/admin/backend";

export const dynamic = "force-dynamic";

export async function GET(request: Request, { params }: { params: Promise<{ clubId: string; playerId: string }> }) {
  const { clubId, playerId } = await params;
  const limit = new URL(request.url).searchParams.get("limit") ?? "20";
  return proxyAdminRequest(`/api/admin/explorer/clubs/${encodeURIComponent(clubId)}/players/${encodeURIComponent(playerId)}/position-observations?limit=${encodeURIComponent(limit)}`);
}
