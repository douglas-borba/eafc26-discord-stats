import { clubPath, fetchSports, SportsApiNotFound } from "@/lib/api/sports-client";
import type { PlayerSummary, PlayerProfile, PagedResult, PlayerXRay } from "@/lib/domain/types";

type ApiPlayerListItem = { playerId:string; name:string; matchCount:number; averageRating:number|null; ratedMatchCount:number };
export type ApiProfile = {
  playerId:string; name:string; matchCount:number; averageRating:number|null; ratedMatchCount:number;
  wins:number; draws:number; losses:number; goals:number; assists:number; craques:number; bagres:number; xerifes:number;
  redCards:number; shots:number; passesCompleted:number; passesAttempted:number; tacklesCompleted:number; tacklesAttempted:number;
  recentMatches:Array<{matchId:string;playedAt:string;opponentClubName:string;ourScore:number;opponentScore:number;outcomeCode:"WIN"|"DRAW"|"LOSS";rating:number|null;goals:number;assists:number}>;
  /** `xray` was emitted by the API before the contract was stabilized. */
  xRay?:PlayerXRay | null;
  xray?:PlayerXRay | null;
};

export async function listPlayers(clubId:string,page:number,size:number,_name?:string,sortBy?:string,sortDir?:string):Promise<PagedResult<PlayerSummary>> {
  const data = await fetchSports<{players:ApiPlayerListItem[]}>(clubPath(clubId,"/players"));
  const players = data.players.map(selectorSummary);
  const direction = sortDir === "ASC" ? 1 : -1;
  players.sort((a,b) => {
    const nameA = (a.displayName ?? a.playerId).toLocaleLowerCase("pt-BR");
    const nameB = (b.displayName ?? b.playerId).toLocaleLowerCase("pt-BR");
    return sortBy === "averageRating"
      ? ((a.averageRating ?? -1) - (b.averageRating ?? -1)) * direction || b.matchesPlayed - a.matchesPlayed || nameA.localeCompare(nameB, "pt-BR")
      : b.matchesPlayed - a.matchesPlayed || nameA.localeCompare(nameB, "pt-BR");
  });
  return {content:players.slice(page*size,page*size+size),page,size,totalElements:players.length,totalPages:size?Math.ceil(players.length/size):0};
}
export async function getPlayerProfile(clubId:string,playerId:string):Promise<PlayerProfile|null>{
  try { const data=await fetchSports<{profile?:ApiProfile}>(clubPath(clubId,`/players/${encodeURIComponent(playerId)}`)); return data.profile ? toPlayerProfile(data.profile) : null; }
  catch(error){if(error instanceof SportsApiNotFound)return null;throw error;}
}
function selectorSummary(p:ApiPlayerListItem):PlayerSummary{return {playerId:p.playerId,displayName:p.name,platformName:null,proName:p.name,matchesPlayed:p.matchCount,totalGoals:0,totalAssists:0,averageRating:p.averageRating,manOfTheMatchCount:0,redCardCount:0};}
function summary(p:ApiProfile):PlayerSummary{return {playerId:p.playerId,displayName:p.name,platformName:null,proName:p.name,matchesPlayed:p.matchCount,totalGoals:p.goals,totalAssists:p.assists,averageRating:p.averageRating,manOfTheMatchCount:p.craques,redCardCount:p.redCards};}
export function toPlayerProfile(p:ApiProfile):PlayerProfile{return {...summary(p),totalShots:p.shots,totalPassesCompleted:p.passesCompleted,totalPassesAttempted:p.passesAttempted,totalTacklesCompleted:p.tacklesCompleted,totalTacklesAttempted:p.tacklesAttempted,wins:p.wins,draws:p.draws,losses:p.losses,ratedMatchCount:p.ratedMatchCount,bagreCount:p.bagres,xerifeCount:p.xerifes,xRay:p.xRay ?? p.xray ?? null,recentMatches:p.recentMatches.map(m=>({matchId:m.matchId,playedAt:m.playedAt,opponentClubName:m.opponentClubName,ourScore:m.ourScore,opponentScore:m.opponentScore,outcome:m.outcomeCode,rating:m.rating,goals:m.goals??0,assists:m.assists??0}))};}
