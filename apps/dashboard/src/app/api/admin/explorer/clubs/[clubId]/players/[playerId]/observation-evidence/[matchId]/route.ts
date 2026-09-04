import { proxyAdminRequest } from "@/lib/admin/backend";

export const dynamic = "force-dynamic";

/** A lazy, authenticated read of one exact observation/candidate audit record. */
export async function GET(
  request: Request,
  { params }: { params: Promise<{ clubId: string; playerId: string; matchId: string }> },
) {
  const { clubId, playerId, matchId } = await params;
  const query = new URL(request.url).searchParams;
  const phrase = query.get("phrase") ?? "";
  const aggregateIndex = query.get("aggregateIndex") ?? "";
  const code = query.get("code") ?? "";
  return proxyAdminRequest(
    `/api/admin/explorer/clubs/${encodeURIComponent(clubId)}/players/${encodeURIComponent(playerId)}/observation-evidence/${encodeURIComponent(matchId)}?phrase=${encodeURIComponent(phrase)}&aggregateIndex=${encodeURIComponent(aggregateIndex)}&code=${encodeURIComponent(code)}`,
  );
}
