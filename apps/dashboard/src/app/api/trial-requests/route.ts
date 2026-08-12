import { NextResponse } from "next/server";

export async function POST(request: Request) {
  const backend = process.env.BACKEND_URL?.trim();
  if (!backend) return NextResponse.json({ error: "backend_unavailable" }, { status: 503 });
  try {
    const response = await fetch(`${backend.replace(/\/$/, "")}/api/trial-requests`, {
      method: "POST", headers: { "Content-Type": "application/json", Accept: "application/json" }, body: await request.text(),
      cache: "no-store", signal: AbortSignal.timeout(10_000),
    });
    return NextResponse.json(await response.json().catch(() => ({ error: "invalid_response" })), { status: response.status });
  } catch { return NextResponse.json({ error: "backend_unavailable" }, { status: 503 }); }
}
