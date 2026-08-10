import { proxyAdminRequest } from "@/lib/admin/backend";

export async function PATCH(request: Request, context: { params: Promise<{ clubId: string }> }) {
  const { clubId } = await context.params;
  return proxyAdminRequest(`/api/admin/clubs/${encodeURIComponent(clubId)}/monitoring`, {
    method: "PATCH",
    body: await request.text(),
  });
}
