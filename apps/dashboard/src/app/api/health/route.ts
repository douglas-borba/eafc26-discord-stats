import { NextResponse } from "next/server";

const BACKEND_HEALTH_TIMEOUT_MS = 5_000;

const down = (reason?: "backend_not_configured") =>
  NextResponse.json(
    reason ? { status: "DOWN", reason } : { status: "DOWN" },
    { status: 503 },
  );

export async function GET() {
  const backendUrl = process.env.BACKEND_URL?.trim();
  if (!backendUrl) {
    return down("backend_not_configured");
  }

  try {
    const response = await fetch(`${backendUrl.replace(/\/$/, "")}/api/health`, {
      method: "GET",
      cache: "no-store",
      signal: AbortSignal.timeout(BACKEND_HEALTH_TIMEOUT_MS),
    });

    if (!response.ok) {
      return down();
    }

    return NextResponse.json({ status: "UP" });
  } catch {
    return down();
  }
}
