import { proxyAdminRequest } from "@/lib/admin/backend";
export async function POST(request: Request, { params }: { params: Promise<{ requestId: string }> }) { const { requestId } = await params; return proxyAdminRequest(`/api/admin/trial-requests/${encodeURIComponent(requestId)}/approve`, { method: "POST", body: await request.text() }); }
