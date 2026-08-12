import { proxyAdminRequest } from "@/lib/admin/backend";

/** Admin-only read bridge for selecting one already-persisted canonical match. */
export async function GET(_request: Request, context: { params: Promise<{ clubId: string }> }) {
  const { clubId } = await context.params;
  return proxyAdminRequest(`/api/clubs/${encodeURIComponent(clubId)}/history/matches`);
}
