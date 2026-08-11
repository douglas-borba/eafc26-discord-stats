import { clubPath, fetchSports, fetchSportsInternal } from "@/lib/api/sports-client";
import type { ClubSummary } from "@/lib/domain/types";

type Club={clubId:string;displayName:string;monitoringEnabled:boolean};
export async function listClubs():Promise<ClubSummary[]>{
  const clubs=await fetchSportsInternal<Club[]>("/api/admin/clubs");
  return Promise.all(clubs.map(async club=>{
    try {
      const metadata=await fetchSports<{matchCount:number}>(clubPath(club.clubId,"/history/metadata"));
      return {clubId:club.clubId,clubName:club.displayName,totalMatches:metadata.matchCount};
    } catch {
      return {clubId:club.clubId,clubName:club.displayName,totalMatches:0};
    }
  }));
}
export async function getClub(clubId:string):Promise<Club>{return fetchSports<Club>(clubPath(clubId));}
