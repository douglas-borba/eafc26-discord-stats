import { proxyAdminRequest } from "@/lib/admin/backend";

export async function POST() {
  return proxyAdminRequest("/api/admin/system/canonical-read-diagnostics/reset", { method: "POST" });
}
