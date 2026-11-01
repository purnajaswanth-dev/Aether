import React, { useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from "@/components/ui/dialog";
import { CodeBlock } from "@/components/ui/code-block";
import { Terminal, Laptop, CheckCircle2, Copy, Check, ShieldCheck, AlertCircle } from "lucide-react";
import { api } from "@/services/api";

interface LocalProjectWizardProps {
  projectId: string;
  isOpen: boolean;
  onClose: () => void;
  onPaired: () => void;
  initialUserCode?: string;
}

export function LocalProjectWizard({
  projectId,
  isOpen,
  onClose,
  onPaired,
  initialUserCode = "",
}: LocalProjectWizardProps) {
  const [userCode, setUserCode] = useState(initialUserCode);
  const [hostname, setHostname] = useState("my-macbook-pro");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [successToken, setSuccessToken] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  const pairCommand = `cd /path/to/repo\naether-agent pair -project ${projectId}`;

  const handleAuthorize = async () => {
    if (!userCode.trim()) {
      setError("Please enter the 8-character pairing code shown in your terminal.");
      return;
    }

    try {
      setLoading(true);
      setError(null);
      const res = await api.confirmAgentPair(projectId, userCode.trim().toUpperCase(), hostname);
      setSuccessToken(res.deviceToken);
      onPaired();
    } catch (err: any) {
      setError(err.message || "Failed to authorize device. Please check the pairing code.");
    } finally {
      setLoading(false);
    }
  };

  const handleCopyCommand = () => {
    navigator.clipboard.writeText(pairCommand);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <Dialog open={isOpen} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-w-xl">
        <DialogHeader>
          <div className="flex items-center gap-2 mb-1">
            <div className="h-8 w-8 rounded-control bg-accent/10 flex items-center justify-center text-accent">
              <Terminal className="h-4 w-4" />
            </div>
            <div>
              <DialogTitle className="text-base font-semibold">Pair Local Project Agent</DialogTitle>
              <DialogDescription className="text-xs">
                Synchronize local files, workspace symbols, and run commands with direct cryptographic verification.
              </DialogDescription>
            </div>
          </div>
        </DialogHeader>

        {successToken ? (
          <div className="py-4 space-y-4">
            <div className="rounded-panel border border-success/30 bg-success/10 p-4 text-center space-y-2">
              <CheckCircle2 className="h-8 w-8 text-success mx-auto" />
              <h3 className="text-sm font-semibold text-text-primary">Device Paired Successfully!</h3>
              <p className="text-xs text-text-secondary max-w-md mx-auto">
                Your local agent in terminal is now active. You can start background synchronization with:
              </p>
              <div className="pt-2">
                <CodeBlock code="aether-agent start" language="bash" />
              </div>
            </div>

            <div className="flex justify-end pt-2">
              <Button size="sm" onClick={onClose}>
                Done
              </Button>
            </div>
          </div>
        ) : (
          <div className="space-y-4 py-2">
            {/* Step 1 */}
            <div className="space-y-1.5">
              <div className="flex items-center gap-2 text-xs font-semibold text-text-primary">
                <span className="flex h-5 w-5 items-center justify-center rounded-full bg-accent text-canvas text-[11px] font-bold">
                  1
                </span>
                <span>Run pairing command in your project terminal</span>
              </div>
              <div className="relative group">
                <CodeBlock code={pairCommand} language="bash" />
                <Button
                  variant="outline"
                  size="sm"
                  className="absolute top-2 right-2 h-7 px-2 text-xs opacity-80 hover:opacity-100"
                  onClick={handleCopyCommand}
                >
                  {copied ? <Check className="h-3 w-3 text-success" /> : <Copy className="h-3 w-3" />}
                </Button>
              </div>
            </div>

            {/* Step 2 */}
            <div className="space-y-2 pt-2 border-t border-border">
              <div className="flex items-center gap-2 text-xs font-semibold text-text-primary">
                <span className="flex h-5 w-5 items-center justify-center rounded-full bg-accent text-canvas text-[11px] font-bold">
                  2
                </span>
                <span>Enter User Code shown in terminal</span>
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <label className="text-[11px] font-medium text-text-secondary block mb-1">
                    Pairing Code (e.g. ABCD-1234)
                  </label>
                  <Input
                    placeholder="ABCD-1234"
                    value={userCode}
                    onChange={(e) => setUserCode(e.target.value.toUpperCase())}
                    className="font-mono text-center tracking-wider font-semibold uppercase text-sm"
                    maxLength={9}
                  />
                </div>
                <div>
                  <label className="text-[11px] font-medium text-text-secondary block mb-1">
                    Device Hostname / Label
                  </label>
                  <Input
                    placeholder="dev-laptop"
                    value={hostname}
                    onChange={(e) => setHostname(e.target.value)}
                    className="text-xs"
                  />
                </div>
              </div>
            </div>

            {error && (
              <div className="flex items-center gap-2 rounded-control border border-destructive/30 bg-destructive/10 p-2.5 text-xs text-destructive">
                <AlertCircle className="h-4 w-4 shrink-0" />
                <span>{error}</span>
              </div>
            )}

            {/* Security note */}
            <div className="flex items-start gap-2 rounded-control bg-subtle p-2.5 text-[11px] text-text-secondary">
              <ShieldCheck className="h-4 w-4 text-accent shrink-0 mt-0.5" />
              <span>
                <strong>Non-negotiable Rule 8:</strong> The local agent automatically excludes <code>.env</code>, private keys, certificates, and build caches.
              </span>
            </div>

            <div className="flex justify-end gap-2 pt-2 border-t border-border">
              <Button variant="outline" size="sm" onClick={onClose} disabled={loading}>
                Cancel
              </Button>
              <Button size="sm" onClick={handleAuthorize} disabled={loading || !userCode.trim()}>
                {loading ? "Authorizing..." : "Authorize Device"}
              </Button>
            </div>
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}

