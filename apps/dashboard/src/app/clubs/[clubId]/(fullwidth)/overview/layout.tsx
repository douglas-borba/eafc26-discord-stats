export default async function OverviewLayout({
  children,
}: {
  children: React.ReactNode;
  params: Promise<{ clubId: string }>;
}) {
  return (
    <div className="min-h-screen">
      {children}
    </div>
  );
}
