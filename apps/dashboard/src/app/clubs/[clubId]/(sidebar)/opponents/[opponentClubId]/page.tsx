import { redirect } from "next/navigation";

export default async function OpponentHistoryRedirect({
  params,
}: {
  params: Promise<{ clubId: string; opponentClubId: string }>;
}) {
  const { clubId } = await params;
  redirect(`/clubs/${clubId}/matches`);
}
