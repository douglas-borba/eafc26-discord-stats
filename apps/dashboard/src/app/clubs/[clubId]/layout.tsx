import { notFound } from "next/navigation";
import { getClub } from "@/lib/repositories/overview-repository";
import { SportsApiNotFound } from "@/lib/api/sports-client";

export default async function ClubLayout({
  children,
  params,
}: {
  children: React.ReactNode;
  params: Promise<{ clubId: string }>;
}) {
  const { clubId } = await params;
  try { await getClub(clubId); } catch (error) { if (error instanceof SportsApiNotFound) notFound(); throw error; }
  return <>{children}</>;
}
