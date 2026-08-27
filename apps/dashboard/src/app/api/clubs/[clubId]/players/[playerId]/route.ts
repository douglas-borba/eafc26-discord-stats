import { NextResponse } from "next/server";

import { SportsApiNotFound, SportsApiUnavailable } from "@/lib/api/sports-client";
import { getPlayer } from "@/lib/services/player-service";

export const dynamic = "force-dynamic";

/**
 * Public, same-origin detail bridge used by the client-side Players master/detail shell.
 * The browser receives a single selected profile; list loading remains server-side and light.
 */
export async function GET(_request: Request, context: { params: Promise<{ clubId: string; playerId: string }> }) {
  const { clubId, playerId } = await context.params;

  try {
    const profile = await getPlayer(clubId, playerId);
    if (!profile) return NextResponse.json({ error: "not_found" }, { status: 404 });

    return NextResponse.json({ profile }, { headers: { "Cache-Control": "no-store" } });
  } catch (error) {
    if (error instanceof SportsApiNotFound) return NextResponse.json({ error: "not_found" }, { status: 404 });
    if (error instanceof SportsApiUnavailable) return NextResponse.json({ error: "profile_unavailable" }, { status: 503 });
    return NextResponse.json({ error: "profile_unavailable" }, { status: 503 });
  }
}
