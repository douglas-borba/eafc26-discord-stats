import { ClubAdminDetail } from "@/components/admin/club-admin-detail";

export default async function AdminClubDetailPage({ params }: { params: Promise<{ clubId: string }> }) {
  const { clubId } = await params;
  return <ClubAdminDetail clubId={clubId} />;
}
