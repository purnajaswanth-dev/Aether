import React from "react";
import { cn } from "@/lib/utils";

interface SplitPaneProps {
  left: React.ReactNode;
  right: React.ReactNode;
  leftWidth?: string;
  className?: string;
}

export function SplitPane({
  left,
  right,
  leftWidth = "w-1/3 min-w-[320px] max-w-[420px]",
  className,
}: SplitPaneProps) {
  return (
    <div className={cn("flex h-full w-full overflow-hidden border border-border rounded-panel bg-canvas", className)}>
      <div className={cn("border-r border-border overflow-y-auto bg-subtle/30", leftWidth)}>
        {left}
      </div>
      <div className="flex-1 overflow-y-auto bg-canvas">
        {right}
      </div>
    </div>
  );
}
