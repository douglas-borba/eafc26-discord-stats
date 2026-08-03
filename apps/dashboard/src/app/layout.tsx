import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "EA FC STATS",
  description: "Dashboard de estatísticas EA FC",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="pt-BR">
      <body>{children}</body>
    </html>
  );
}
