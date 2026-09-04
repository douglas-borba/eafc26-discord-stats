import { createDefaultLiveFeedbackCounters } from "@/lib/live-feedback-phrases";

export type CollectorDraft = {
  clubId: string;
  matchId: string | null;
  playerId: string | null;
  playerName: string | null;
  /** Local-only reminder. It never participates in match association or import. */
  opponentName: string;
  phrases: Record<string, number>;
  /** Marks drafts created with the collector-owned shortcut collection. */
  phraseCollectionVersion?: 1;
  completeness: "AT_LEAST" | "EXACT";
  startedAt: string;
  /** Proves that match and player were explicitly selected for this draft instance. */
  associationDraftStartedAt: string | null;
};

export type AssociatedCollectorDraft = CollectorDraft & {
  matchId: string;
  playerId: string;
  associationDraftStartedAt: string;
};

export type LiveCollectorObservationInput = {
  matchId: string;
  playerId: string;
  phrase: string;
  observedCount: number;
  completeness: "AT_LEAST" | "EXACT";
};

export function createLiveCollectorDraft(clubId: string, startedAt = new Date().toISOString()): CollectorDraft {
  return {
    clubId,
    matchId: null,
    playerId: null,
    playerName: null,
    opponentName: "",
    phrases: createDefaultLiveFeedbackCounters(),
    phraseCollectionVersion: 1,
    completeness: "AT_LEAST",
    startedAt,
    associationDraftStartedAt: null,
  };
}

export function clearLiveCollectorAssociation(draft: CollectorDraft): CollectorDraft {
  return {
    ...draft,
    matchId: null,
    playerId: null,
    playerName: null,
    associationDraftStartedAt: null,
  };
}

export function selectLiveCollectorMatch(draft: CollectorDraft, matchId: string): CollectorDraft {
  return {
    ...clearLiveCollectorAssociation(draft),
    matchId,
  };
}

export function selectLiveCollectorPlayer(
  draft: CollectorDraft,
  playerId: string,
  playerName: string | null,
): CollectorDraft {
  if (!draft.matchId) return clearLiveCollectorAssociation(draft);

  return {
    ...draft,
    playerId,
    playerName: playerName ?? playerId,
    associationDraftStartedAt: draft.startedAt,
  };
}

export function hasCurrentLiveCollectorAssociation(draft: CollectorDraft | null): draft is AssociatedCollectorDraft {
  return Boolean(
    draft
      && draft.matchId
      && draft.playerId
      && draft.associationDraftStartedAt === draft.startedAt,
  );
}

export function buildLiveCollectorObservationInputs(draft: CollectorDraft): LiveCollectorObservationInput[] {
  if (!hasCurrentLiveCollectorAssociation(draft)) return [];

  return Object.entries(draft.phrases)
    .filter(([, count]) => count > 0)
    .map(([phrase, observedCount]) => ({
      matchId: draft.matchId,
      playerId: draft.playerId,
      phrase,
      observedCount,
      completeness: draft.completeness,
    }));
}
