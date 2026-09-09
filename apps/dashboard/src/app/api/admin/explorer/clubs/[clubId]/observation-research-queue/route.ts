import { proxyAdminRequest } from "@/lib/admin/backend";

export const dynamic = "force-dynamic";

/** Bounded, authenticated research triage. No browser credential reaches Spring. */
export async function GET(_request: Request, { params }: { params: Promise<{ clubId: string }> }) {
  const { clubId } = await params;
  return proxyAdminRequest(
    `/api/admin/explorer/clubs/${encodeURIComponent(clubId)}/observation-research-queue`,
  );
}
