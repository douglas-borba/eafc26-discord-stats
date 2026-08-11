import "server-only";

const TIMEOUT_MS = 10_000;

export class SportsApiNotFound extends Error {}
export class SportsApiUnavailable extends Error {}

function backendOrigin(): string {
  return (process.env.BACKEND_URL?.trim() || "http://localhost:8080").replace(/\/$/, "");
}

export async function fetchSports<T>(path: string): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`${backendOrigin()}${path}`, {
      headers: { Accept: "application/json" },
      cache: "no-store",
      signal: AbortSignal.timeout(TIMEOUT_MS),
    });
  } catch {
    throw new SportsApiUnavailable(path);
  }
  if (response.status === 404) throw new SportsApiNotFound(path);
  if (!response.ok) throw new SportsApiUnavailable(`${path} status=${response.status}`);
  return response.json() as Promise<T>;
}

export async function fetchSportsInternal<T>(path: string): Promise<T> {
  const token = process.env.ADMIN_INTERNAL_TOKEN?.trim();
  const headers: Record<string, string> = { Accept: "application/json" };
  if (token) headers.Authorization = `Bearer ${token}`;
  let response: Response;
  try {
    response = await fetch(`${backendOrigin()}${path}`, {
      headers,
      cache: "no-store",
      signal: AbortSignal.timeout(TIMEOUT_MS),
    });
  } catch {
    throw new SportsApiUnavailable(path);
  }
  if (response.status === 404) throw new SportsApiNotFound(path);
  if (!response.ok) throw new SportsApiUnavailable(`${path} status=${response.status}`);
  return response.json() as Promise<T>;
}

export function clubPath(clubId: string, suffix = ""): string {
  return `/api/clubs/${encodeURIComponent(clubId)}${suffix}`;
}
