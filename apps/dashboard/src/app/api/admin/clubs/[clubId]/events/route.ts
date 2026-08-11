import { proxyAdminRequest } from "@/lib/admin/backend";

export async function GET(_request: Request, context: { params: Promise<{ clubId: string }> }) {
  const { clubId } = await context.params;
  return proxyAdminRequest(`/api/admin/clubs/${encodeURIComponent(clubId)}/events?limit=50`);
}
