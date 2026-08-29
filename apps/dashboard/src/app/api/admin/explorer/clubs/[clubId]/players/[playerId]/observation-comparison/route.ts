import { proxyAdminRequest } from "@/lib/admin/backend";

export const dynamic = "force-dynamic";

export async function GET(request: Request, { params }: { params: Promise<{ clubId: string; playerId: string }> }) {
  const { clubId, playerId } = await params;
  const input = new URL(request.url);
  const phrase = input.searchParams.get("phrase") ?? "";
  const limit = input.searchParams.get("limit") ?? "20";
  return proxyAdminRequest(
    `/api/admin/explorer/clubs/${encodeURIComponent(clubId)}/players/${encodeURIComponent(playerId)}/observation-comparison?phrase=${encodeURIComponent(phrase)}&limit=${encodeURIComponent(limit)}`,
  );
}
