import { redirect } from "next/navigation";

export default async function MatchDetailRedirect({
  params,
}: {
  params: Promise<{ clubId: string; matchId: string }>;
}) {
  const { clubId, matchId } = await params;
  redirect(`/clubs/${clubId}/matches?match=${matchId}`);
}
