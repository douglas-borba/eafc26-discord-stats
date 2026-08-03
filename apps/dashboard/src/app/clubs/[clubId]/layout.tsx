import { SidebarNav } from "@/components/layout/sidebar-nav";

export default async function ClubLayout({
  children,
  params,
}: {
  children: React.ReactNode;
  params: Promise<{ clubId: string }>;
}) {
  const { clubId } = await params;

  return (
    <div className="lg:grid lg:grid-cols-[248px_minmax(0,1fr)] min-h-screen">
      <SidebarNav clubId={clubId} />
      <main className="pt-16 lg:pt-0">
        <div className="p-4 lg:p-[26px] max-w-[1480px] mx-auto">
          {children}
        </div>
      </main>
    </div>
  );
}
