import { proxyAdminRequest } from "@/lib/admin/backend";
import { NextRequest } from "next/server";

export const dynamic = "force-dynamic";

export async function GET(request: NextRequest, { params }: { params: Promise<{ clubId: string }> }) {
  const { clubId } = await params;
  const limit = request.nextUrl.searchParams.get("limit") ?? "20";
  return proxyAdminRequest(`/api/admin/explorer/clubs/${clubId}/matches?limit=${limit}`);
}
