import { proxyAdminRequest } from "@/lib/admin/backend";

export const dynamic = "force-dynamic";

export async function POST(request: Request, { params }: { params: Promise<{ clubId: string }> }) {
  const { clubId } = await params;
  return proxyAdminRequest(
    `/api/admin/explorer/clubs/${encodeURIComponent(clubId)}/observations/import`,
    { method: "POST", body: await request.text() },
  );
}
