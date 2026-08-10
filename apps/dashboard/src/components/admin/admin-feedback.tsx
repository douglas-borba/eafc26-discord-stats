export function AdminFeedback({ message, tone = "error" }: { message: string; tone?: "error" | "success" }) {
  return (
    <div
      role={tone === "error" ? "alert" : "status"}
      className={tone === "error"
        ? "rounded-lg border border-loss/40 bg-loss/10 px-4 py-3 text-sm text-loss"
        : "rounded-lg border border-win/40 bg-win/10 px-4 py-3 text-sm text-win"}
    >
      {message}
    </div>
  );
}
