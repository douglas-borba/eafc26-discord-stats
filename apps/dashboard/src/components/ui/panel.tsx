import { cn } from "@/lib/utils";

export function Panel({ children, className, style }: { children: React.ReactNode; className?: string; style?: React.CSSProperties }) {
  return (
    <div className={cn("rounded-[10px] border border-border p-5", className)} style={{ background: "linear-gradient(160deg, #1a1f26, #161b22, #12161c)", ...style }}>
      {children}
    </div>
  );
}
