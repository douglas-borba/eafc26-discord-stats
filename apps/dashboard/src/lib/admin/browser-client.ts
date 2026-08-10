import type { AdminApiError } from "./types";

export class AdminRequestError extends Error {
  constructor(public readonly code: string, message: string, public readonly status: number) {
    super(message);
  }
}

export async function adminRequest<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response;
  try {
    response = await fetch(path, {
      ...init,
      headers: init?.body ? { "Content-Type": "application/json", ...init.headers } : init?.headers,
    });
  } catch {
    throw new AdminRequestError("network_error", "Não foi possível conectar ao painel.", 0);
  }

  const payload = await response.json().catch(() => null) as T | AdminApiError | null;
  if (!response.ok) {
    const error = payload as AdminApiError | null;
    throw new AdminRequestError(error?.error ?? "unexpected_error", error?.message ?? "Não foi possível concluir a operação.", response.status);
  }
  return payload as T;
}
