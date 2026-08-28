import { proxyAdminRequest } from "@/lib/admin/backend";

export const dynamic = "force-dynamic";

export async function GET(request: Request, { params }: { params: Promise<{ clubId: string }> }) {
  const { clubId } = await params;
  const query = new URL(request.url).searchParams;
  const qs = new URLSearchParams();
  for (const key of ["limit", "anchorType", "aggregateIndex", "code", "metricName"]) {
    const val = query.get(key);
    if (val !== null) qs.set(key, val);
  }
  return proxyAdminRequest(`/api/admin/explorer/clubs/${clubId}/anchor?${qs.toString()}`);
}
