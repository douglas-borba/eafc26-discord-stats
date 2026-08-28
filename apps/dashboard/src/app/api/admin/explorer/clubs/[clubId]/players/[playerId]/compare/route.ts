import { proxyAdminRequest } from "@/lib/admin/backend";
import { NextRequest } from "next/server";

export const dynamic = "force-dynamic";

export async function GET(request: NextRequest, { params }: { params: Promise<{ clubId: string; playerId: string }> }) {
  const { clubId, playerId } = await params;
  const matchIds = request.nextUrl.searchParams.getAll("matchIds");
  const qs = matchIds.map((id) => `matchIds=${id}`).join("&");
  return proxyAdminRequest(`/api/admin/explorer/clubs/${clubId}/players/${playerId}/compare?${qs}`);
}
