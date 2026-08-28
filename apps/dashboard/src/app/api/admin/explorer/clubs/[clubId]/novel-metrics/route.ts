import { proxyAdminRequest } from "@/lib/admin/backend";

export const dynamic = "force-dynamic";

export async function GET(request: Request, { params }: { params: Promise<{ clubId: string }> }) {
  const { clubId } = await params;
  const query = new URL(request.url).searchParams;
  const qs = new URLSearchParams();
  for (const key of ["limit", "aggregateIndex", "code"]) {
    const value = query.get(key);
    if (value !== null) qs.set(key, value);
  }
  return proxyAdminRequest(`/api/admin/explorer/clubs/${encodeURIComponent(clubId)}/novel-metrics?${qs}`);
}
