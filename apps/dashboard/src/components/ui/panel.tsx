import { cn } from "@/lib/utils";

export function Panel({ children, className }: { children: React.ReactNode; className?: string }) {
  return (
    <div className={cn("rounded-[10px] border border-border p-5", className)} style={{ background: "linear-gradient(160deg, #1a1f26, #161b22, #12161c)" }}>
      {children}
    </div>
  );
}
