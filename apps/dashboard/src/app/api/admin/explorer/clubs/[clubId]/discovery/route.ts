import { proxyAdminRequest } from "@/lib/admin/backend";

export const dynamic = "force-dynamic";

export async function GET(request: Request, { params }: { params: Promise<{ clubId: string }> }) {
  const { clubId } = await params;
  const query = new URL(request.url).searchParams;
  const limit = query.get("limit") ?? "10";
  const aggregate = query.get("aggregate") ?? "all";
  const minimumMatches = query.get("minimumMatches") ?? "0";
  const minimumObservations = query.get("minimumObservations") ?? "0";
  const hideKnownRelationships = query.get("hideKnownRelationships") ?? "true";
  return proxyAdminRequest(
    `/api/admin/explorer/clubs/${clubId}/discovery?limit=${limit}&aggregate=${aggregate}` +
      `&minimumMatches=${minimumMatches}&minimumObservations=${minimumObservations}` +
      `&hideKnownRelationships=${hideKnownRelationships}`,
  );
}
