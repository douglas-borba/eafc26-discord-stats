import { proxyAdminRequest } from "@/lib/admin/backend";

export async function PATCH(request: Request, { params }: { params: Promise<{ clubId: string }> }) {
  const { clubId } = await params;
  return proxyAdminRequest(`/api/admin/clubs/${encodeURIComponent(clubId)}/game-version`, {
    method: "PATCH",
    body: await request.text(),
  });
}
