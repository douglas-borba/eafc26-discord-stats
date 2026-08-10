import { proxyAdminRequest } from "@/lib/admin/backend";

export async function PUT(request: Request, context: { params: Promise<{ clubId: string }> }) {
  const { clubId } = await context.params;
  return proxyAdminRequest(`/api/admin/clubs/${encodeURIComponent(clubId)}/discord`, {
    method: "PUT",
    body: await request.text(),
  });
}

export async function DELETE(_request: Request, context: { params: Promise<{ clubId: string }> }) {
  const { clubId } = await context.params;
  return proxyAdminRequest(`/api/admin/clubs/${encodeURIComponent(clubId)}/discord`, { method: "DELETE" });
}
