import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";
import {
  normalizeOperationalEvents,
  type OperationalEventsResponse,
} from "@/lib/admin/types";

const root = resolve(process.cwd(), "src");
const timelineSource = readFileSync(resolve(root, "components/admin/club-event-timeline.tsx"), "utf8");

const validEvent = {
  id: 42,
  matchId: "match-42",
  eventType: "ACQUISITION",
  phase: "COMPLETE",
  status: "SUCCESS",
  message: null,
  errorCode: null,
  durationMs: 321,
  createdAt: "2026-08-11T12:00:00Z",
};

describe("club event timeline contract", () => {
  it("accepts the Spring wrapper and stores its events rather than the wrapper itself", () => {
    const payload: OperationalEventsResponse = { events: [validEvent] };

    expect(normalizeOperationalEvents(payload.events)).toEqual([validEvent]);
    expect(timelineSource).toContain("adminRequest<OperationalEventsResponse>");
    expect(timelineSource).toContain("normalizeOperationalEvents(payload?.events)");
  });

  it("keeps an empty Spring events collection empty", () => {
    expect(normalizeOperationalEvents([])).toEqual([]);
    expect(timelineSource).toContain("Nenhum evento registrado.");
  });

  it("normalizes nullable and unexpected persisted fields without throwing", () => {
    expect(normalizeOperationalEvents([{
      id: null,
      matchId: null,
      eventType: "UNKNOWN_EVENT",
      phase: "UNRECOGNIZED_PHASE",
      status: "UNRECOGNIZED_STATUS",
      message: null,
      errorCode: null,
      durationMs: null,
      createdAt: "not-a-date",
    }])).toEqual([{
      id: null,
      matchId: null,
      eventType: "UNKNOWN_EVENT",
      phase: "UNRECOGNIZED_PHASE",
      status: "UNRECOGNIZED_STATUS",
      message: null,
      errorCode: null,
      durationMs: null,
      createdAt: "not-a-date",
    }]);
    expect(timelineSource).toContain('"Data indisponível"');
    expect(timelineSource).toContain('?? "bg-muted"');
  });

  it("contains endpoint failures locally in the timeline states", () => {
    expect(timelineSource).toContain("setError(reason instanceof Error");
    expect(timelineSource).toContain("if (error) return <Panel>");
    expect(timelineSource).toContain("if (loading) return <Panel>");
  });

  it("drops malformed event records instead of passing them to rendering", () => {
    expect(normalizeOperationalEvents([null, "invalid", { eventType: "EA_FETCH" }])).toEqual([{
      id: null,
      matchId: null,
      eventType: "EA_FETCH",
      phase: null,
      status: null,
      message: null,
      errorCode: null,
      durationMs: null,
      createdAt: null,
    }]);
  });
});
