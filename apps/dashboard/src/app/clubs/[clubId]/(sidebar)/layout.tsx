import { SidebarNav } from "@/components/layout/sidebar-nav";
import { getClub } from "@/lib/repositories/overview-repository";

export default async function SidebarLayout({
  children,
  params,
}: {
  children: React.ReactNode;
  params: Promise<{ clubId: string }>;
}) {
  const { clubId } = await params;
  const club = await getClub(clubId);

  return (
    <div className="lg:grid lg:grid-cols-[248px_minmax(0,1fr)] min-h-screen">
      <SidebarNav clubId={clubId} clubName={club.displayName} restricted={club.accessStatus !== "ACTIVE"} />
      <main className="pt-16 lg:pt-0">
        <div className="p-4 lg:p-[26px] max-w-[1480px] mx-auto">
          {children}
        </div>
      </main>
    </div>
  );
}
