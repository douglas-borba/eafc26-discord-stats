import { describe, expect, it } from "vitest";
import {
  buildLiveCollectorObservationInputs,
  clearLiveCollectorAssociation,
  createLiveCollectorDraft,
  hasCurrentLiveCollectorAssociation,
  selectLiveCollectorMatch,
  selectLiveCollectorPlayer,
} from "@/lib/live-collector-draft";

describe("live collector draft lifecycle", () => {
  it("starts Collection B without inheriting Collection A's match or player after A was associated", () => {
    const collectionA = selectLiveCollectorPlayer(
      selectLiveCollectorMatch(createLiveCollectorDraft("1104972", "2026-09-04T20:00:00.000Z"), "match-a"),
      "player-a",
      "Player A",
    );
    const collectionB = createLiveCollectorDraft("1104972", "2026-09-04T21:00:00.000Z");

    expect(hasCurrentLiveCollectorAssociation(collectionA)).toBe(true);
    expect(collectionB).toMatchObject({
      matchId: null,
      playerId: null,
      playerName: null,
      associationDraftStartedAt: null,
    });
    expect(hasCurrentLiveCollectorAssociation(collectionB)).toBe(false);
  });

  it("preserves a legitimately associated active draft through serialization", () => {
    const activeDraft = selectLiveCollectorPlayer(
      selectLiveCollectorMatch(createLiveCollectorDraft("1104972", "2026-09-04T20:00:00.000Z"), "match-a"),
      "player-a",
      "Player A",
    );
    activeDraft.phrases["Bela cavadinha"] = 3;
    activeDraft.opponentName = "Clube adversário";
    activeDraft.completeness = "EXACT";

    const restoredDraft = JSON.parse(JSON.stringify(activeDraft));

    expect(hasCurrentLiveCollectorAssociation(restoredDraft)).toBe(true);
    expect(restoredDraft).toMatchObject({
      matchId: "match-a",
      playerId: "player-a",
      opponentName: "Clube adversário",
      completeness: "EXACT",
      phrases: { "Bela cavadinha": 3 },
    });
  });

  it("treats old drafts without explicit association provenance as unsafe for Preview or Import", () => {
    const legacyDraft = {
      ...createLiveCollectorDraft("1104972", "2026-09-04T20:00:00.000Z"),
      matchId: "stale-match",
      playerId: "stale-player",
      playerName: "Old player",
      associationDraftStartedAt: null,
    };

    expect(hasCurrentLiveCollectorAssociation(legacyDraft)).toBe(false);
  });

  it("clears only canonical association while preserving active evidence for safe re-association", () => {
    const draft = selectLiveCollectorPlayer(
      selectLiveCollectorMatch(createLiveCollectorDraft("1104972", "2026-09-04T20:00:00.000Z"), "old-match"),
      "old-player",
      "Old player",
    );
    draft.phrases["Bela cavadinha"] = 4;
    draft.phrases["Frase manual"] = 2;
    draft.opponentName = "Adversário novo";
    draft.completeness = "EXACT";

    const cleared = clearLiveCollectorAssociation(draft);
    const reassociated = selectLiveCollectorPlayer(
      selectLiveCollectorMatch(cleared, "new-match"),
      "new-player",
      "New player",
    );

    expect(cleared).toMatchObject({
      matchId: null,
      playerId: null,
      playerName: null,
      associationDraftStartedAt: null,
      opponentName: "Adversário novo",
      completeness: "EXACT",
      phrases: { "Bela cavadinha": 4, "Frase manual": 2 },
    });
    expect(reassociated).toMatchObject({ matchId: "new-match", playerId: "new-player", playerName: "New player" });
    expect(hasCurrentLiveCollectorAssociation(reassociated)).toBe(true);
    expect(buildLiveCollectorObservationInputs(reassociated)).toEqual([
      {
        matchId: "new-match",
        playerId: "new-player",
        phrase: "Bela cavadinha",
        observedCount: 4,
        completeness: "EXACT",
      },
      {
        matchId: "new-match",
        playerId: "new-player",
        phrase: "Frase manual",
        observedCount: 2,
        completeness: "EXACT",
      },
    ]);
  });

  it("requires both a selected match and selected player from the current draft", () => {
    const empty = createLiveCollectorDraft("1104972", "2026-09-04T20:00:00.000Z");
    const matchOnly = selectLiveCollectorMatch(empty, "match-a");
    const playerOnly = { ...empty, playerId: "player-a", associationDraftStartedAt: empty.startedAt };
    const complete = selectLiveCollectorPlayer(matchOnly, "player-a", "Player A");

    expect(hasCurrentLiveCollectorAssociation(empty)).toBe(false);
    expect(hasCurrentLiveCollectorAssociation(matchOnly)).toBe(false);
    expect(hasCurrentLiveCollectorAssociation(playerOnly)).toBe(false);
    expect(hasCurrentLiveCollectorAssociation(complete)).toBe(true);
    expect(buildLiveCollectorObservationInputs(empty)).toEqual([]);
    expect(buildLiveCollectorObservationInputs(matchOnly)).toEqual([]);
    expect(buildLiveCollectorObservationInputs(playerOnly)).toEqual([]);
  });
});
