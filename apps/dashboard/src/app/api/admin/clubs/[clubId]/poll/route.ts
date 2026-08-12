import { proxyAdminRequest } from "@/lib/admin/backend";
export async function POST(_: Request, { params }: { params: Promise<{ clubId: string }> }) {
  const { clubId } = await params;
  return proxyAdminRequest(`/api/admin/clubs/${encodeURIComponent(clubId)}/poll`, { method: "POST" });
}
