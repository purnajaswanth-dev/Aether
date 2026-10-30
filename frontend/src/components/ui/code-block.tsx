import * as React from "react";
import { Check, Copy } from "lucide-react";
import { Button } from "./button";
import { Badge } from "./badge";
import { cn } from "@/lib/utils";

interface CodeBlockProps {
  code: string;
  language?: string;
  hash?: string;
  title?: string;
  className?: string;
  maxHeight?: string;
}

export function CodeBlock({
  code,
  language = "markdown",
  hash,
  title,
  className,
  maxHeight = "500px",
}: CodeBlockProps) {
  const [copied, setCopied] = React.useState(false);

  const handleCopy = () => {
    navigator.clipboard.writeText(code);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className={cn("overflow-hidden rounded-panel border border-border bg-subtle/50 text-xs", className)}>
      <div className="flex items-center justify-between border-b border-border bg-subtle px-3 py-1.5">
        <div className="flex items-center gap-2">
          {title && <span className="font-medium text-text-primary">{title}</span>}
          <span className="font-mono text-text-tertiary uppercase text-[11px]">{language}</span>
          {hash && (
            <Badge variant="neutral" size="sm" showDot={false} className="font-mono text-[10px]">
              SHA: {hash.substring(0, 10)}…
            </Badge>
          )}
        </div>
        <Button variant="ghost" size="sm" onClick={handleCopy} className="h-6 px-2 text-xs">
          {copied ? (
            <>
              <Check className="mr-1 h-3 w-3 text-success" />
              <span className="text-success">Copied</span>
            </>
          ) : (
            <>
              <Copy className="mr-1 h-3 w-3 text-text-secondary" />
              <span>Copy</span>
            </>
          )}
        </Button>
      </div>
      <div
        className="overflow-auto p-3 font-mono text-text-primary bg-canvas leading-relaxed whitespace-pre font-normal selection:bg-accent-subtle"
        style={{ maxHeight }}
      >
        <code>{code}</code>
      </div>
    </div>
  );
}

