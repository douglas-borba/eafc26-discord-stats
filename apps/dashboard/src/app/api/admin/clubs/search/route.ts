import { proxyAdminRequest } from "@/lib/admin/backend";

export async function GET(request: Request) {
  const query = new URL(request.url).searchParams.get("query") ?? "";
  return proxyAdminRequest(`/api/admin/clubs/search?query=${encodeURIComponent(query)}`);
}
