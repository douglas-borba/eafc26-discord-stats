import { redirect } from "next/navigation";

export default async function PlayerProfileRedirect({
  params,
}: {
  params: Promise<{ clubId: string; playerId: string }>;
}) {
  const { clubId, playerId } = await params;
  redirect(`/clubs/${clubId}/players?player=${playerId}`);
}
