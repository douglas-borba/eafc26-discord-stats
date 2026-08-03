export const dynamic = "force-dynamic";

import { getOpponents } from "@/lib/services/opponent-service";
import { OpponentList } from "@/components/opponents/opponent-list";

export default async function OpponentsPage({ params }: { params: Promise<{ clubId: string }> }) {
  const { clubId } = await params;
  const result = await getOpponents(clubId, 0, 100);

  return (
    <div className="lg:grid lg:grid-cols-[minmax(260px,340px)_minmax(0,1fr)] lg:gap-6">
      <div>
        <h1 className="text-xl font-bold text-text-primary mb-4">Adversários</h1>
        <div className="lg:sticky lg:top-[26px] lg:max-h-[calc(100vh-52px)] lg:overflow-y-auto">
          <OpponentList opponents={result.content} />
          {result.content.length === 0 && (
            <p className="text-sm text-muted mt-4 text-center">Nenhum adversário encontrado.</p>
          )}
        </div>
      </div>
      <div className="hidden lg:flex items-center justify-center">
        <p className="text-muted text-sm">Selecione um adversário para ver o histórico.</p>
      </div>
    </div>
  );
}
