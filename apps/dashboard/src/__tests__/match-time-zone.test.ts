import { describe, expect, it } from "vitest";

import { formatDate, formatDateTime, formatMatchDateTime } from "@/lib/utils";

describe("match date formatting", () => {
  it("renders the match instant in America/Sao_Paulo", () => {
    expect(formatMatchDateTime("2026-08-12T22:07:00Z")).toBe("12 ago. 2026 • 19:07");
    expect(formatDateTime("2026-08-12T22:07:00Z")).toContain("19:07");
  });

  it("uses the Brazilian local date across a UTC day boundary", () => {
    expect(formatMatchDateTime("2026-08-13T01:30:00Z")).toBe("12 ago. 2026 • 22:30");
    expect(formatDate("2026-08-13T01:30:00Z")).toBe("12/08/2026");
  });
});
