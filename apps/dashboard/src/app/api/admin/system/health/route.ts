import { proxyAdminRequest } from "@/lib/admin/backend";

export async function GET() {
  return proxyAdminRequest("/api/admin/system/health");
}
