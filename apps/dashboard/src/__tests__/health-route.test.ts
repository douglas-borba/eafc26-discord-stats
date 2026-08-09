import { afterEach, describe, expect, it, vi } from "vitest";

import { GET } from "@/app/api/health/route";

const originalBackendUrl = process.env.BACKEND_URL;

afterEach(() => {
  vi.unstubAllGlobals();
  if (originalBackendUrl === undefined) {
    delete process.env.BACKEND_URL;
  } else {
    process.env.BACKEND_URL = originalBackendUrl;
  }
});

describe("GET /api/health", () => {
  it("returns UP when the configured backend is healthy", async () => {
    process.env.BACKEND_URL = "https://spring.example.test/";
    const fetchMock = vi.fn().mockResolvedValue(new Response('{"status":"UP"}', { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    const response = await GET();

    expect(response.status).toBe(200);
    await expect(response.json()).resolves.toEqual({ status: "UP" });
    expect(fetchMock).toHaveBeenCalledOnce();
    expect(fetchMock).toHaveBeenCalledWith(
      "https://spring.example.test/api/health",
      expect.objectContaining({
        method: "GET",
        cache: "no-store",
        signal: expect.any(AbortSignal),
      }),
    );
    expect(fetchMock.mock.calls[0][1]).not.toHaveProperty("headers");
    expect(fetchMock.mock.calls[0][1]).not.toHaveProperty("credentials");
  });

  it("returns DOWN when the backend is unavailable", async () => {
    process.env.BACKEND_URL = "https://spring.example.test";
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new TypeError("network unavailable")));

    const response = await GET();

    expect(response.status).toBe(503);
    await expect(response.json()).resolves.toEqual({ status: "DOWN" });
  });

  it("returns a safe reason when BACKEND_URL is absent", async () => {
    delete process.env.BACKEND_URL;
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);

    const response = await GET();

    expect(response.status).toBe(503);
    await expect(response.json()).resolves.toEqual({
      status: "DOWN",
      reason: "backend_not_configured",
    });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("returns DOWN when the backend responds with an error", async () => {
    process.env.BACKEND_URL = "https://spring.example.test";
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response("internal details", { status: 500 })));

    const response = await GET();

    expect(response.status).toBe(503);
    await expect(response.json()).resolves.toEqual({ status: "DOWN" });
  });
});
