import { clubPath, fetchSports, SportsApiNotFound } from "@/lib/api/sports-client";
import type { OpponentSummary, OpponentHistory, PagedResult, MatchSummary } from "@/lib/domain/types";

type ApiMatch={matchId:string;playedAt:string;competition:string|null;ourClubName:string;opponentName:string;ourScore:number;opponentScore:number;outcomeCode:"WIN"|"DRAW"|"LOSS"};
type ApiOpponent={clubId:string;name:string;meetings:number;wins:number;draws:number;losses:number;goalsFor:number;goalsAgainst:number;latestMatch:ApiMatch};
type ApiHistory=ApiOpponent&{matches:ApiMatch[];currentRun:{type:string;label:string;count:number;matchIds:string[];tiedRuns:number}|null;runRecords:Array<{type:string;label:string;count:number;matchIds:string[];tiedRuns:number}>;leaders:Array<{type:string;label:string;value:number;players:Array<{playerId:string;name:string}>}>};

export async function listOpponents(clubId:string,page:number,size:number):Promise<PagedResult<OpponentSummary>>{
  const data=await fetchSports<{opponents:ApiOpponent[]}>(clubPath(clubId,"/opponents"));
  const all=data.opponents.map(o=>({clubId:o.clubId,clubName:o.name,matchesPlayed:o.meetings,wins:o.wins,draws:o.draws,losses:o.losses,goalsFor:o.goalsFor,goalsAgainst:o.goalsAgainst,lastPlayedAt:o.latestMatch.playedAt}));
  return {content:all.slice(page*size,page*size+size),page,size,totalElements:all.length,totalPages:size?Math.ceil(all.length/size):0};
}
export async function getOpponentHistory(clubId:string,opponentId:string):Promise<OpponentHistory|null>{
  try{const data=await fetchSports<{opponent?:ApiHistory}>(clubPath(clubId,`/opponents/${encodeURIComponent(opponentId)}`));if(!data.opponent)return null;const o=data.opponent;return {clubId:o.clubId,clubName:o.name,matchesPlayed:o.meetings,wins:o.wins,draws:o.draws,losses:o.losses,goalsFor:o.goalsFor,goalsAgainst:o.goalsAgainst,matches:o.matches.map(m=>match(clubId,o.clubId,m)),currentRun:o.currentRun,runRecords:o.runRecords,playerLeaders:o.leaders};}
  catch(error){if(error instanceof SportsApiNotFound)return null;throw error;}
}
function match(clubId:string,opponentId:string,m:ApiMatch):MatchSummary{return {matchId:m.matchId,playedAt:m.playedAt,ourClubId:clubId,ourClubName:m.ourClubName,opponentClubId:opponentId,opponentClubName:m.opponentName,ourScore:m.ourScore,opponentScore:m.opponentScore,outcome:m.outcomeCode,matchType:m.competition};}
