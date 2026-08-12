import { proxyAdminRequest } from "@/lib/admin/backend";
export async function POST(_request: Request, { params }: { params: Promise<{ requestId: string }> }) { const { requestId } = await params; return proxyAdminRequest(`/api/admin/trial-requests/${encodeURIComponent(requestId)}/reject`, { method: "POST" }); }
