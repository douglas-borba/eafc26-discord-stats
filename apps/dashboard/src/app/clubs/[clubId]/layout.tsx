export default async function ClubLayout({
  children,
}: {
  children: React.ReactNode;
  params: Promise<{ clubId: string }>;
}) {
  return <>{children}</>;
}
