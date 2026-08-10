import { proxyAdminRequest } from "@/lib/admin/backend";

export const dynamic = "force-dynamic";

export async function GET() {
  return proxyAdminRequest("/api/admin/clubs");
}

export async function POST(request: Request) {
  return proxyAdminRequest("/api/admin/clubs", { method: "POST", body: await request.text() });
}
