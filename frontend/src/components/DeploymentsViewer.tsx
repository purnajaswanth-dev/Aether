import React, { useState, useEffect } from "react";
import { Deployment } from "@/types";
import { api } from "@/services/api";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription } from "@/components/ui/dialog";
import { EmptyState } from "@/components/ui/empty-state";
import {
  Cloud,
  CheckCircle2,
  AlertTriangle,
  XCircle,
  Clock,
  Terminal,
  Search,
  Copy,
  Check,
  RefreshCw,
  GitCommit,
  ExternalLink,
} from "lucide-react";

interface DeploymentsViewerProps {
  projectId: string;
}

export function DeploymentsViewer({ projectId }: DeploymentsViewerProps) {
  const [deployments, setDeployments] = useState<Deployment[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedDeploy, setSelectedDeploy] = useState<Deployment | null>(null);
  const [logs, setLogs] = useState<string>("");
  const [loadingLogs, setLoadingLogs] = useState(false);
  const [logSearch, setLogSearch] = useState("");
  const [copied, setCopied] = useState(false);

  const loadDeployments = async () => {
    try {
      setLoading(true);
      const data = await api.listDeployments(projectId, 50);
      setDeployments(data);
    } catch (err) {
      console.error("Failed to load deployments", err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadDeployments();
  }, [projectId]);

  const handleOpenLogs = async (deploy: Deployment) => {
    setSelectedDeploy(deploy);
    setLoadingLogs(true);
    setLogs("");
    setLogSearch("");
    try {
      const res = await api.getDeploymentLogs(projectId, deploy.externalDeployId, 250);
      setLogs(res.logs);
    } catch (err: any) {
      setLogs("Failed to retrieve deployment logs: " + (err.message || "Unknown error"));
    } finally {
      setLoadingLogs(false);
    }
  };

  const handleCopyLogs = () => {
    navigator.clipboard.writeText(logs);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const getStatusBadge = (status: string) => {
    const s = status.toLowerCase();
    if (s === "live" || s === "succeeded") {
      return (
        <Badge variant="success" size="sm" showDot>
          Live
        </Badge>
      );
    }
    if (s.includes("fail") || s.includes("error")) {
      return (
        <Badge variant="danger" size="sm" showDot>
          {status}
        </Badge>
      );
    }
    if (s.includes("progress") || s.includes("building")) {
      return (
        <Badge variant="warning" size="sm" showDot>
          In Progress
        </Badge>
      );
    }
    return (
      <Badge variant="neutral" size="sm">
        {status}
      </Badge>
    );
  };

  const getFailureBadge = (failureClass?: string) => {
    if (!failureClass || failureClass === "NONE") return null;
    return (
      <span className="inline-flex items-center px-2 py-0.5 rounded text-[10px] font-mono font-semibold uppercase bg-danger-subtle text-danger border border-danger/30">
        {failureClass}
      </span>
    );
  };

  const filteredLogLines = logs.split("\n").filter((line) => {
    if (!logSearch.trim()) return true;
    return line.toLowerCase().includes(logSearch.toLowerCase());
  });

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-sm font-semibold text-text-primary">Deployment Verification & Log History</h2>
          <p className="text-xs text-text-secondary">
            Continuous deployment status tracking, failure classification, and build/runtime log artifacts.
          </p>
        </div>
        <Button variant="outline" size="sm" onClick={loadDeployments} className="gap-1.5 text-xs">
          <RefreshCw className="h-3.5 w-3.5" /> Refresh
        </Button>
      </div>

      {loading ? (
        <div className="space-y-3">
          {[1, 2, 3].map((i) => (
            <div key={i} className="h-20 rounded-panel bg-subtle animate-pulse border border-border" />
          ))}
        </div>
      ) : deployments.length === 0 ? (
        <EmptyState
          title="No deployments recorded yet"
          description="Deployments from Render or GitHub Actions will appear here automatically when synchronized or triggered via webhook."
          icon={Cloud}
        />
      ) : (
        <div className="divide-y divide-border rounded-panel border border-border bg-canvas overflow-hidden">
          {deployments.map((deploy) => (
            <div
              key={deploy.id}
              className="p-4 hover:bg-subtle/50 transition-colors flex flex-col sm:flex-row sm:items-center justify-between gap-3"
            >
              <div className="space-y-1.5 min-w-0">
                <div className="flex items-center gap-2 flex-wrap">
                  {getStatusBadge(deploy.status)}
                  {getFailureBadge(deploy.failureClassification)}
                  <span className="text-xs font-mono font-medium text-text-primary">
                    {deploy.externalDeployId}
                  </span>
                  <span className="text-[10px] uppercase font-mono px-1.5 py-0.5 rounded bg-subtle text-text-secondary border border-border">
                    {deploy.provider}
                  </span>
                  <span className="text-[10px] text-text-tertiary">
                    env: <strong className="text-text-secondary">{deploy.environment}</strong>
                  </span>
                </div>

                <div className="flex items-center gap-3 text-xs text-text-secondary">
                  {deploy.commitSha && (
                    <span className="flex items-center gap-1 font-mono text-[11px]">
                      <GitCommit className="h-3.5 w-3.5 text-text-tertiary" />
                      {deploy.commitSha.substring(0, 7)}
                    </span>
                  )}
                  <span className="flex items-center gap-1 text-[11px] text-text-tertiary">
                    <Clock className="h-3 w-3" />
                    {new Date(deploy.createdAt).toLocaleString()}
                  </span>
                </div>

                {deploy.extractedErrors && deploy.extractedErrors.length > 0 && (
                  <div className="rounded bg-destructive/5 border border-destructive/20 p-2 text-[11px] font-mono text-destructive space-y-0.5 max-w-2xl">
                    <div className="font-semibold text-[10px] uppercase tracking-wider text-destructive/80 mb-1">
                      Extracted Failure Highlights
                    </div>
                    {deploy.extractedErrors.slice(0, 2).map((err, idx) => (
                      <div key={idx} className="truncate">
                        &bull; {err}
                      </div>
                    ))}
                  </div>
                )}
              </div>

              <div className="flex items-center gap-2 shrink-0">
                <Button
                  variant="outline"
                  size="sm"
                  className="gap-1.5 text-xs font-medium"
                  onClick={() => handleOpenLogs(deploy)}
                >
                  <Terminal className="h-3.5 w-3.5 text-text-secondary" />
                  View Logs
                </Button>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Log Viewer Dialog */}
      <Dialog open={Boolean(selectedDeploy)} onOpenChange={(open) => !open && setSelectedDeploy(null)}>
        <DialogContent className="max-w-4xl max-h-[85vh] flex flex-col p-6">
          <DialogHeader>
            <div className="flex items-center justify-between pr-6">
              <div>
                <DialogTitle className="text-base font-semibold flex items-center gap-2">
                  <Terminal className="h-4 w-4 text-accent" />
                  Deployment Logs: {selectedDeploy?.externalDeployId}
                </DialogTitle>
                <DialogDescription className="text-xs text-text-secondary">
                  Immutable build and runtime logs preserved with untrusted data boundary wrapper.
                </DialogDescription>
              </div>
              <div className="flex items-center gap-2">
                <Button variant="outline" size="sm" onClick={handleCopyLogs} className="gap-1.5 text-xs">
                  {copied ? <Check className="h-3.5 w-3.5 text-success" /> : <Copy className="h-3.5 w-3.5" />}
                  {copied ? "Copied" : "Copy"}
                </Button>
              </div>
            </div>
          </DialogHeader>

          {/* Search bar */}
          <div className="relative my-2">
            <Search className="h-3.5 w-3.5 absolute left-3 top-2.5 text-text-tertiary" />
            <Input
              placeholder="Search in log lines (e.g. error, exit, panic)..."
              value={logSearch}
              onChange={(e) => setLogSearch(e.target.value)}
              className="pl-8 text-xs font-mono h-8"
            />
          </div>

          {/* Log terminal console */}
          <div className="flex-1 overflow-auto rounded border border-border bg-[#0d1117] p-3 text-[11px] font-mono text-[#c9d1d9] leading-relaxed select-text min-h-[300px]">
            {loadingLogs ? (
              <div className="flex items-center justify-center h-48 text-text-tertiary">
                <RefreshCw className="h-5 w-5 animate-spin mr-2" />
                Loading logs...
              </div>
            ) : filteredLogLines.length === 0 ? (
              <div className="text-text-tertiary text-center py-12">
                {logSearch ? `No log lines matching "${logSearch}"` : "Log content empty."}
              </div>
            ) : (
              filteredLogLines.map((line, idx) => {
                const isError =
                  line.toLowerCase().includes("error") ||
                  line.toLowerCase().includes("err!") ||
                  line.toLowerCase().includes("failed") ||
                  line.toLowerCase().includes("panic:");
                const isWarning = line.toLowerCase().includes("warn");

                return (
                  <div
                    key={idx}
                    className={`flex items-start gap-3 py-0.5 px-1 rounded ${
                      isError
                        ? "bg-destructive/15 text-red-300 font-semibold"
                        : isWarning
                        ? "bg-warning/10 text-amber-300"
                        : "hover:bg-white/5"
                    }`}
                  >
                    <span className="text-gray-500 select-none text-[10px] w-8 shrink-0 text-right">
                      {idx + 1}
                    </span>
                    <span className="break-all whitespace-pre-wrap">{line}</span>
                  </div>
                );
              })
            )}
          </div>
        </DialogContent>
      </Dialog>
    </div>
  );
}
