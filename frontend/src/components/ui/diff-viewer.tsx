import { ArtifactDiffResponse } from "@/types";
import { Badge } from "./badge";
import { cn } from "@/lib/utils";

interface DiffViewerProps {
  diff: ArtifactDiffResponse;
  className?: string;
}

export function DiffViewer({ diff, className }: DiffViewerProps) {
  return (
    <div className={cn("overflow-hidden rounded-panel border border-border bg-canvas text-xs font-mono", className)}>
      <div className="flex items-center justify-between border-b border-border bg-subtle px-3 py-2">
        <div className="flex items-center gap-2">
          <span className="font-semibold text-text-primary">
            v{diff.v1} → v{diff.v2}
          </span>
          <span className="text-text-tertiary">|</span>
          <span className="text-success font-medium">+{diff.insertions}</span>
          <span className="text-danger font-medium">-{diff.deletions}</span>
          {diff.modifications > 0 && (
            <span className="text-warning font-medium">~{diff.modifications}</span>
          )}
        </div>
        <div className="flex items-center gap-2">
          <Badge variant="neutral" size="sm" showDot={false}>
            v1: {diff.v1Hash.substring(0, 8)}…
          </Badge>
          <Badge variant="neutral" size="sm" showDot={false}>
            v2: {diff.v2Hash.substring(0, 8)}…
          </Badge>
        </div>
      </div>

      <div className="max-h-[500px] overflow-auto divide-y divide-border/30">
        {diff.lines.map((line, idx) => {
          const isInsert = line.type === "INSERT";
          const isDelete = line.type === "DELETE";

          return (
            <div
              key={idx}
              className={cn(
                "flex items-stretch font-mono text-[12px] leading-5 select-text",
                isInsert && "bg-success-subtle/40 text-success border-l-2 border-l-success",
                isDelete && "bg-danger-subtle/40 text-danger border-l-2 border-l-danger",
                !isInsert && !isDelete && "hover:bg-subtle/50 text-text-primary"
              )}
            >
              <div className="w-10 shrink-0 select-none px-2 py-0.5 text-right text-text-tertiary bg-subtle/30 border-r border-border/40">
                {line.oldLineNumber || ""}
              </div>
              <div className="w-10 shrink-0 select-none px-2 py-0.5 text-right text-text-tertiary bg-subtle/30 border-r border-border/40">
                {line.newLineNumber || ""}
              </div>
              <div className="w-6 shrink-0 select-none text-center py-0.5 font-bold">
                {isInsert ? "+" : isDelete ? "-" : " "}
              </div>
              <div className="flex-1 px-2 py-0.5 whitespace-pre overflow-x-auto">
                {line.content}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
