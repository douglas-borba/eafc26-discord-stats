import { proxyAdminRequest } from "@/lib/admin/backend";

export async function POST(_request: Request, context: { params: Promise<{ clubId: string; matchId: string }> }) {
  const { clubId, matchId } = await context.params;
  return proxyAdminRequest(
    `/api/admin/clubs/${encodeURIComponent(clubId)}/publication/${encodeURIComponent(matchId)}/force-publish`,
    { method: "POST" },
  );
}
