import "server-only";

const TIMEOUT_MS = 10_000;

export class SportsApiNotFound extends Error {}

export async function fetchSports<T>(path: string): Promise<T> {
  const backend = process.env.BACKEND_URL?.trim() || "http://localhost:8080";
  const response = await fetch(`${backend.replace(/\/$/, "")}${path}`, {
    headers: { Accept: "application/json" },
    cache: "no-store",
    signal: AbortSignal.timeout(TIMEOUT_MS),
  });
  if (response.status === 404) throw new SportsApiNotFound(path);
  if (!response.ok) throw new Error(`Sports API failed with status ${response.status}`);
  return response.json() as Promise<T>;
}

export function clubPath(clubId: string, suffix = ""): string {
  return `/api/clubs/${encodeURIComponent(clubId)}${suffix}`;
}
