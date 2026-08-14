import { clubPath, fetchSports, SportsApiNotFound } from "@/lib/api/sports-client";
import type { MatchSummary, MatchDetail, PagedResult, MatchAwards } from "@/lib/domain/types";

type HistorySummary = { matchId:string; playedAt:string; competition:string|null; ourClub:{id:string;name:string;score:number}; opponentClub:{id:string;name:string;score:number}; outcome:{code:"WIN"|"DRAW"|"LOSS"}; completionStatus?:"COMPLETED"|"DNF"|"UNKNOWN"; dnfClubId?:string|null };
type HistoryList = { matches: HistorySummary[] };
type HistoryDetail = { status:string; match?: { summary:HistorySummary; players:Array<Record<string, unknown>>; awards:Array<Record<string, unknown>>; stories:Array<Record<string, unknown>> } };

export async function listMatches(clubId:string, page:number, size:number, _filters?:unknown): Promise<PagedResult<MatchSummary>> {
  const data = await fetchSports<HistoryList>(clubPath(clubId, "/history/matches"));
  const all = data.matches.map(toSummary);
  const content = all.slice(page * size, page * size + size);
  return { content, page, size, totalElements:all.length, totalPages:size ? Math.ceil(all.length / size) : 0 };
}

export async function getMatchDetail(clubId:string, matchId:string): Promise<MatchDetail|null> {
  try {
    const data = await fetchSports<HistoryDetail>(clubPath(clubId, `/history/matches/${encodeURIComponent(matchId)}`));
    if (!data.match) return null;
    const { summary, players, awards, stories } = data.match;
    const award = (type:string) => {
      const item = awards.find((a) => a.type === type && a.awarded === true);
      return item ? { winnerId:(item.winnerId as string|null) ?? null, winnerName:(item.winnerName as string|null) ?? null, reason:(item.reason as string|null) ?? null } : null;
    };
    const mappedAwards: MatchAwards = { craque:award("CRAQUE"), bagre:award("BAGRE"), xerife:award("XERIFE") };
    return {
      ...toSummary(summary),
      players: players.map((p) => ({
        playerId:String(p.id), displayName:String(p.name), platformName:null, proName:String(p.name),
        rating:p.rating == null ? null : Number(p.rating), goals:Number(p.goals ?? 0), assists:Number(p.assists ?? 0), shots:Number(p.shots ?? 0),
        passesCompleted:Number(p.passesCompleted ?? 0), passesAttempted:Number(p.passesAttempted ?? 0), tacklesCompleted:Number(p.tacklesCompleted ?? 0),
        tacklesAttempted:Number(p.tacklesAttempted ?? 0), redCards:Number(p.redCards ?? 0), manOfTheMatch:Boolean(p.manOfTheMatch),
      })),
      awards:mappedAwards,
      stories:stories.map((s) => ({
        type:String(s.type), priority:String(s.priority), narrativeKey:String(s.narrativeKey),
        content:(s.structuredData ?? {}) as Record<string, unknown>, involvedPlayers:(s.involvedPlayerIds ?? []) as string[],
      })),
    };
  } catch (error) { if (error instanceof SportsApiNotFound) return null; throw error; }
}

function toSummary(m:HistorySummary): MatchSummary { return {
  matchId:m.matchId, playedAt:m.playedAt, ourClubId:m.ourClub.id, ourClubName:m.ourClub.name,
  opponentClubId:m.opponentClub.id, opponentClubName:m.opponentClub.name, ourScore:m.ourClub.score,
  opponentScore:m.opponentClub.score, outcome:m.outcome.code, matchType:m.competition, completionStatus:m.completionStatus, dnfClubId:m.dnfClubId,
}; }
