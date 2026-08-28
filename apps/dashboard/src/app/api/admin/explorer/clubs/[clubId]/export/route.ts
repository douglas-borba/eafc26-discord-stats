import { proxyAdminRequest } from "@/lib/admin/backend";
import { NextRequest } from "next/server";

export const dynamic = "force-dynamic";

export async function GET(request: NextRequest, { params }: { params: Promise<{ clubId: string }> }) {
  const { clubId } = await params;
  const limit = request.nextUrl.searchParams.get("limit") ?? "20";
  const format = request.nextUrl.searchParams.get("format") ?? "json";
  return proxyAdminRequest(`/api/admin/explorer/clubs/${clubId}/export?limit=${limit}&format=${format}`);
}
