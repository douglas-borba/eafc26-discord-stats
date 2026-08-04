import { redirect } from "next/navigation";

export default async function OpponentHistoryRedirect({
  params,
}: {
  params: Promise<{ clubId: string; opponentClubId: string }>;
}) {
  const { clubId, opponentClubId } = await params;
  redirect(`/clubs/${clubId}/opponents?opponent=${opponentClubId}`);
}
