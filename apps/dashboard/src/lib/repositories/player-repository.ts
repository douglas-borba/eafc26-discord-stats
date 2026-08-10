import { clubPath, fetchSports, SportsApiNotFound } from "@/lib/api/sports-client";
import type { PlayerSummary, PlayerProfile, PagedResult } from "@/lib/domain/types";

type ApiProfile = { playerId:string; name:string; matchCount:number; averageRating:number|null; goals:number; assists:number; craques:number; redCards:number; shots:number; passesCompleted:number; passesAttempted:number; tacklesCompleted:number; tacklesAttempted:number; recentMatches:Array<{matchId:string;playedAt:string;opponentClubName:string;ourScore:number;opponentScore:number;outcomeCode:"WIN"|"DRAW"|"LOSS";rating:number|null;goals:number;assists:number}> };

export async function listPlayers(clubId:string,page:number,size:number,_name?:string,sortBy?:string,sortDir?:string):Promise<PagedResult<PlayerSummary>> {
  const data = await fetchSports<{players:ApiProfile[]}>(clubPath(clubId,"/players"));
  const players = data.players.map(summary);
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
  try { const data=await fetchSports<{profile?:ApiProfile}>(clubPath(clubId,`/players/${encodeURIComponent(playerId)}`)); return data.profile ? profile(data.profile) : null; }
  catch(error){if(error instanceof SportsApiNotFound)return null;throw error;}
}
function summary(p:ApiProfile):PlayerSummary{return {playerId:p.playerId,displayName:p.name,platformName:null,proName:p.name,matchesPlayed:p.matchCount,totalGoals:p.goals,totalAssists:p.assists,averageRating:p.averageRating,manOfTheMatchCount:p.craques,redCardCount:p.redCards};}
function profile(p:ApiProfile):PlayerProfile{return {...summary(p),totalShots:p.shots,totalPassesCompleted:p.passesCompleted,totalPassesAttempted:p.passesAttempted,totalTacklesCompleted:p.tacklesCompleted,totalTacklesAttempted:p.tacklesAttempted,recentMatches:p.recentMatches.map(m=>({matchId:m.matchId,playedAt:m.playedAt,opponentClubName:m.opponentClubName,ourScore:m.ourScore,opponentScore:m.opponentScore,outcome:m.outcomeCode,rating:m.rating,goals:m.goals??0,assists:m.assists??0}))};}
