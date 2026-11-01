import React, { useState, useEffect, useCallback } from "react";
import { useParams, Link } from "react-router-dom";
import { api } from "@/services/api";
import {
  ActionRequest,
  Task,
  TechnicalDecision,
  IntegrationHealth,
} from "@/types";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from "@/components/ui/dialog";
import { CodeBlock } from "@/components/ui/code-block";
import { EmptyState } from "@/components/ui/empty-state";
import {
  Inbox,
  ShieldCheck,
  ShieldAlert,
  AlertTriangle,
  CheckCircle2,
  XCircle,
  Clock,
  Sparkles,
  Bot,
  UserCheck,
  CheckSquare,
  Scale,
  RefreshCw,
  Terminal,
  Activity,
  Layers,
  ArrowRight,
  ExternalLink,
} from "lucide-react";

export function ApprovalInboxPage() {
  const { projectId } = useParams<{ projectId: string }>();

  // State
  const [loading, setLoading] = useState(true);
  const [actionRequests, setActionRequests] = useState<ActionRequest[]>([]);
  const [proposedTasks, setProposedTasks] = useState<Task[]>([]);
  const [proposedDecisions, setProposedDecisions] = useState<TechnicalDecision[]>([]);
  const [healthStatus, setHealthStatus] = useState<IntegrationHealth[]>([]);
  const [reviewNotes, setReviewNotes] = useState<Record<string, string>>({});
  const [activeTab, setActiveTab] = useState<"actions" | "tasks" | "decisions" | "health">("actions");

  // Destructive Confirmation Modal
  const [destructiveModalOpen, setDestructiveModalOpen] = useState(false);
  const [targetAction, setTargetAction] = useState<ActionRequest | null>(null);
  const [confirmInput, setConfirmInput] = useState("");
  const [submitting, setSubmitting] = useState(false);

  // Load Inbox Data
  const loadInboxData = useCallback(async () => {
    if (!projectId) return;
    try {
      setLoading(true);
      const [actions, allTasks, allDecisions, health] = await Promise.all([
        api.listActionRequests(projectId).catch(() => []),
        api.listTasks(projectId).catch(() => []),
        api.listDecisions(projectId).catch(() => []),
        api.getIntegrationHealth(projectId).catch(() => []),
      ]);

      setActionRequests(actions);
      setProposedTasks(allTasks.filter((t) => t.isProposedByAgent && t.status === "BACKLOG"));
      setProposedDecisions(allDecisions.filter((d) => d.state === "PROPOSED"));
      setHealthStatus(health);
    } catch (err) {
      console.error("Failed to load approval inbox", err);
    } finally {
      setLoading(false);
    }
  }, [projectId]);

  useEffect(() => {
    loadInboxData();
  }, [loadInboxData]);

  // Review Action
  const handleReviewAction = async (action: ActionRequest, approved: boolean) => {
    if (!projectId) return;

    if (approved && action.isDestructive) {
      setTargetAction(action);
      setConfirmInput("");
      setDestructiveModalOpen(true);
      return;
    }

    try {
      setSubmitting(true);
      const notes = reviewNotes[action.id] || (approved ? "Approved by human reviewer" : "Rejected by human reviewer");
      await api.reviewActionRequest(projectId, action.id, {
        approved,
        reviewNotes: notes,
      });
      await loadInboxData();
    } catch (err: any) {
      alert("Failed to review action: " + (err.message || err));
    } finally {
      setSubmitting(false);
    }
  };

  // Confirm Destructive Action
  const handleConfirmDestructive = async () => {
    if (!projectId || !targetAction) return;
    if (confirmInput.trim().toUpperCase() !== "CONFIRM") {
      alert("Please type CONFIRM exactly to proceed.");
      return;
    }

    try {
      setSubmitting(true);
      const notes = reviewNotes[targetAction.id] || "Confirmed and approved destructive action";
      await api.reviewActionRequest(projectId, targetAction.id, {
        approved: true,
        reviewNotes: notes,
      });
      setDestructiveModalOpen(false);
      setTargetAction(null);
      await loadInboxData();
    } catch (err: any) {
      alert("Failed to execute destructive action: " + (err.message || err));
    } finally {
      setSubmitting(false);
    }
  };

  // Accept Task
  const handleAcceptTask = async (task: Task) => {
    if (!projectId) return;
    try {
      await api.acceptProposedTask(projectId, task.id);
      await loadInboxData();
    } catch (err: any) {
      alert("Failed to accept task: " + (err.message || err));
    }
  };

  const pendingActions = actionRequests.filter((a) => a.status === "PENDING");
  const reviewedActions = actionRequests.filter((a) => a.status !== "PENDING");
  const totalPending = pendingActions.length + proposedTasks.length + proposedDecisions.length;

  return (
    <div className="flex flex-col h-full bg-canvas text-text-primary">
      {/* Top Header Bar */}
      <header className="flex items-center justify-between px-6 py-4 border-b border-border bg-subtle/30 shrink-0">
        <div className="flex items-center gap-3">
          <div className="flex h-9 w-9 items-center justify-center rounded-control bg-accent-subtle text-accent border border-accent/20">
            <Inbox className="h-5 w-5" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h1 className="text-lg font-semibold tracking-tight text-text-primary">Approval Inbox</h1>
              {totalPending > 0 && (
                <span className="text-xs px-2 py-0.5 rounded-full bg-warning-subtle border border-warning/30 text-warning font-mono font-medium">
                  {totalPending} pending
                </span>
              )}
            </div>
            <p className="text-xs text-text-tertiary">
              Human-in-the-loop gatekeeper: Approve agent action executions, task proposals, and ADRs
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <Button variant="outline" size="sm" onClick={loadInboxData} disabled={loading}>
            <RefreshCw className={`h-3.5 w-3.5 mr-1 ${loading ? "animate-spin" : ""}`} />
            Refresh
          </Button>
        </div>
      </header>

      {/* Tabs Bar */}
      <div className="flex border-b border-border bg-canvas px-6 text-xs shrink-0">
        <button
          onClick={() => setActiveTab("actions")}
          className={`py-2.5 px-3 border-b-2 font-medium transition-colors flex items-center gap-1.5 ${
            activeTab === "actions"
              ? "border-accent text-accent"
              : "border-transparent text-text-tertiary hover:text-text-secondary"
          }`}
        >
          <span>Action Requests</span>
          {pendingActions.length > 0 && (
            <span className="px-1.5 py-0.2 rounded-full text-[10px] bg-accent text-white font-mono">
              {pendingActions.length}
            </span>
          )}
        </button>

        <button
          onClick={() => setActiveTab("tasks")}
          className={`py-2.5 px-3 border-b-2 font-medium transition-colors flex items-center gap-1.5 ${
            activeTab === "tasks"
              ? "border-accent text-accent"
              : "border-transparent text-text-tertiary hover:text-text-secondary"
          }`}
        >
          <span>Proposed Tasks</span>
          {proposedTasks.length > 0 && (
            <span className="px-1.5 py-0.2 rounded-full text-[10px] bg-accent-subtle text-accent font-mono border border-accent/30">
              {proposedTasks.length}
            </span>
          )}
        </button>

        <button
          onClick={() => setActiveTab("decisions")}
          className={`py-2.5 px-3 border-b-2 font-medium transition-colors flex items-center gap-1.5 ${
            activeTab === "decisions"
              ? "border-accent text-accent"
              : "border-transparent text-text-tertiary hover:text-text-secondary"
          }`}
        >
          <span>Proposed Decisions</span>
          {proposedDecisions.length > 0 && (
            <span className="px-1.5 py-0.2 rounded-full text-[10px] bg-warning-subtle text-warning font-mono border border-warning/30">
              {proposedDecisions.length}
            </span>
          )}
        </button>

        <button
          onClick={() => setActiveTab("health")}
          className={`py-2.5 px-3 border-b-2 font-medium transition-colors flex items-center gap-1.5 ${
            activeTab === "health"
              ? "border-accent text-accent"
              : "border-transparent text-text-tertiary hover:text-text-secondary"
          }`}
        >
          <span>Integration Health</span>
          <span className="px-1.5 py-0.2 rounded-full text-[10px] bg-subtle text-text-tertiary font-mono">
            {healthStatus.length}
          </span>
        </button>
      </div>

      {/* Main Tab Content */}
      <div className="flex-1 overflow-y-auto p-6">
        {loading ? (
          <div className="space-y-4 max-w-4xl">
            <Skeleton className="h-24 w-full" />
            <Skeleton className="h-24 w-full" />
          </div>
        ) : activeTab === "actions" ? (
          /* ACTION REQUESTS TAB */
          <div className="max-w-4xl space-y-6">
            <div>
              <h2 className="text-sm font-semibold text-text-secondary uppercase tracking-wider mb-3 flex items-center gap-2">
                <Clock className="h-4 w-4 text-warning" />
                Pending Actions Awaiting Human Execution ({pendingActions.length})
              </h2>

              {pendingActions.length === 0 ? (
                <EmptyState
                  icon={ShieldCheck}
                  title="Approval inbox clear"
                  description="No pending actions requested by agents. Antigravity and Claude agent actions will appear here for human authorization."
                />
              ) : (
                <div className="space-y-4">
                  {pendingActions.map((action) => (
                    <div
                      key={action.id}
                      className={`p-4 rounded-panel border ${
                        action.isDestructive
                          ? "border-danger/40 bg-danger-subtle/10"
                          : "border-border bg-subtle/20"
                      } space-y-3 shadow-xs`}
                    >
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-2">
                          <Terminal className="h-4 w-4 text-accent" />
                          <span className="font-mono font-semibold text-sm text-text-primary">
                            {action.actionType}
                          </span>
                          {action.isDestructive ? (
                            <Badge variant="danger" size="sm">
                              <AlertTriangle className="h-3 w-3 mr-1" />
                              Destructive Action
                            </Badge>
                          ) : (
                            <Badge variant="info" size="sm">Standard Action</Badge>
                          )}
                        </div>
                        <span className="text-xs text-text-tertiary font-mono">
                          Requested {new Date(action.createdAt).toLocaleTimeString()}
                        </span>
                      </div>

                      {/* Parameters view */}
                      <div>
                        <span className="text-[11px] font-semibold text-text-tertiary uppercase tracking-wider block mb-1">
                          Action Payload
                        </span>
                        <CodeBlock
                          code={JSON.stringify(action.parameters, null, 2)}
                          language="json"
                        />
                      </div>

                      {/* Review Notes Input */}
                      <div>
                        <label className="text-[11px] font-semibold text-text-tertiary uppercase tracking-wider block mb-1">
                          Reviewer Audit Notes (Optional)
                        </label>
                        <input
                          type="text"
                          placeholder="e.g. Verified target environment and release notes"
                          value={reviewNotes[action.id] || ""}
                          onChange={(e) =>
                            setReviewNotes({ ...reviewNotes, [action.id]: e.target.value })
                          }
                          className="w-full px-3 py-1.5 bg-canvas border border-border rounded-control text-xs text-text-primary focus:outline-none focus:ring-1 focus:ring-accent"
                        />
                      </div>

                      {/* Action Buttons */}
                      <div className="flex items-center justify-end gap-2 pt-2 border-t border-border/60">
                        <Button
                          variant="outline"
                          size="sm"
                          disabled={submitting}
                          onClick={() => handleReviewAction(action, false)}
                          className="text-danger hover:bg-danger-subtle hover:text-danger"
                        >
                          <XCircle className="h-3.5 w-3.5 mr-1" />
                          Reject Action
                        </Button>
                        <Button
                          variant="default"
                          size="sm"
                          disabled={submitting}
                          onClick={() => handleReviewAction(action, true)}
                          className={action.isDestructive ? "bg-danger text-white hover:bg-danger/90" : ""}
                        >
                          <CheckCircle2 className="h-3.5 w-3.5 mr-1" />
                          {action.isDestructive ? "Authorize Destructive Execution..." : "Approve & Execute"}
                        </Button>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* Reviewed Actions History */}
            {reviewedActions.length > 0 && (
              <div className="pt-6 border-t border-border">
                <h3 className="text-xs font-semibold text-text-tertiary uppercase tracking-wider mb-3">
                  Previously Reviewed Actions ({reviewedActions.length})
                </h3>
                <div className="space-y-2">
                  {reviewedActions.slice(0, 10).map((action) => (
                    <div
                      key={action.id}
                      className="p-3 rounded bg-subtle/30 border border-border flex items-center justify-between text-xs"
                    >
                      <div className="flex items-center gap-2">
                        {action.status === "EXECUTED" || action.status === "APPROVED" ? (
                          <CheckCircle2 className="h-4 w-4 text-success" />
                        ) : (
                          <XCircle className="h-4 w-4 text-danger" />
                        )}
                        <span className="font-mono font-medium">{action.actionType}</span>
                        <Badge
                          variant={action.status === "EXECUTED" ? "success" : "danger"}
                          size="sm"
                        >
                          {action.status}
                        </Badge>
                      </div>
                      <div className="flex items-center gap-3 text-text-tertiary">
                        {action.reviewNotes && <span>"{action.reviewNotes}"</span>}
                        <span className="font-mono text-[10px]">
                          {action.reviewedAt ? new Date(action.reviewedAt).toLocaleDateString() : ""}
                        </span>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        ) : activeTab === "tasks" ? (
          /* PROPOSED TASKS TAB */
          <div className="max-w-4xl space-y-4">
            <h2 className="text-sm font-semibold text-text-secondary uppercase tracking-wider mb-3 flex items-center gap-2">
              <Bot className="h-4 w-4 text-accent" />
              Agent-Proposed Tasks Awaiting Human Acceptance ({proposedTasks.length})
            </h2>

            {proposedTasks.length === 0 ? (
              <EmptyState
                icon={CheckSquare}
                title="No proposed tasks"
                description="When agents identify missing work or deployment remediations, they propose tasks into the backlog."
              />
            ) : (
              <div className="space-y-3">
                {proposedTasks.map((task) => (
                  <div key={task.id} className="p-4 rounded-panel border border-accent/30 bg-accent-subtle/10 space-y-2">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2">
                        <span className="font-mono text-xs text-accent font-bold">{task.key}</span>
                        <h4 className="font-semibold text-text-primary text-sm">{task.title}</h4>
                      </div>
                      <Button
                        variant="default"
                        size="sm"
                        onClick={() => handleAcceptTask(task)}
                        className="h-7 text-xs"
                      >
                        Accept into Ready
                      </Button>
                    </div>

                    {task.description && (
                      <p className="text-xs text-text-secondary">{task.description}</p>
                    )}

                    {task.agentProposalReason && (
                      <div className="p-2 rounded bg-subtle/60 text-xs border border-border">
                        <span className="text-[11px] font-semibold text-accent block">Agent Proposal Rationale:</span>
                        <p className="text-text-primary">{task.agentProposalReason}</p>
                      </div>
                    )}
                  </div>
                ))}
              </div>
            )}
          </div>
        ) : activeTab === "decisions" ? (
          /* PROPOSED DECISIONS (ADRs) TAB */
          <div className="max-w-4xl space-y-4">
            <h2 className="text-sm font-semibold text-text-secondary uppercase tracking-wider mb-3 flex items-center gap-2">
              <Scale className="h-4 w-4 text-warning" />
              Technical Decisions Awaiting Review ({proposedDecisions.length})
            </h2>

            {proposedDecisions.length === 0 ? (
              <EmptyState
                icon={Scale}
                title="No proposed decisions"
                description="Constitutional Rule: AI agents cannot auto-approve decisions. Any agent-proposed ADR requires human review."
              />
            ) : (
              <div className="space-y-3">
                {proposedDecisions.map((decision) => (
                  <div key={decision.id} className="p-4 rounded-panel border border-border bg-subtle/20 space-y-2">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2">
                        <span className="font-mono text-xs text-warning font-bold">{decision.publicId}</span>
                        <h4 className="font-semibold text-text-primary text-sm">{decision.title}</h4>
                      </div>
                      <Link
                        to={`/projects/${projectId}/decisions`}
                        className="inline-flex items-center gap-1 text-xs text-accent hover:underline font-medium"
                      >
                        <span>Open Decision Review</span>
                        <ArrowRight className="h-3 w-3" />
                      </Link>
                    </div>

                    <p className="text-xs text-text-secondary line-clamp-2">
                      {decision.problemStatement}
                    </p>
                  </div>
                ))}
              </div>
            )}
          </div>
        ) : (
          /* INTEGRATION HEALTH TAB */
          <div className="max-w-4xl space-y-4">
            <h2 className="text-sm font-semibold text-text-secondary uppercase tracking-wider mb-3 flex items-center gap-2">
              <Activity className="h-4 w-4 text-accent" />
              Connected Provider Sync & Health Telemetry
            </h2>

            {healthStatus.length === 0 ? (
              <EmptyState
                icon={Activity}
                title="No health telemetry"
                description="Connect integrations to view synchronization lag and health metrics."
              />
            ) : (
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {healthStatus.map((h) => (
                  <div key={h.provider} className="p-4 rounded-panel border border-border bg-subtle/20 space-y-3">
                    <div className="flex items-center justify-between">
                      <span className="font-mono font-semibold text-sm text-text-primary uppercase">
                        {h.provider}
                      </span>
                      <Badge
                        variant={h.status === "HEALTHY" ? "success" : h.status === "DEGRADED" ? "warning" : "danger"}
                        size="sm"
                      >
                        {h.status}
                      </Badge>
                    </div>

                    <div className="grid grid-cols-2 gap-2 text-xs">
                      <div className="p-2 rounded bg-subtle">
                        <span className="text-[10px] text-text-tertiary block">Sync Lag</span>
                        <span className="font-mono font-medium">{h.currentLagSeconds}s</span>
                      </div>
                      <div className="p-2 rounded bg-subtle">
                        <span className="text-[10px] text-text-tertiary block">Error Rate</span>
                        <span className="font-mono font-medium">{h.recentErrorRatePercent.toFixed(1)}%</span>
                      </div>
                      <div className="p-2 rounded bg-subtle">
                        <span className="text-[10px] text-text-tertiary block">Rate Limit</span>
                        <span className="font-mono font-medium">{h.rateLimitRemaining ?? "N/A"}</span>
                      </div>
                      <div className="p-2 rounded bg-subtle">
                        <span className="text-[10px] text-text-tertiary block">DLQ Pending</span>
                        <span className="font-mono font-medium text-danger">{h.dlqPendingCount}</span>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </div>

      {/* Confirmation Modal for Destructive Operations */}
      <Dialog open={destructiveModalOpen} onOpenChange={setDestructiveModalOpen}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2 text-danger">
              <ShieldAlert className="h-5 w-5" />
              Destructive Operation Confirmation
            </DialogTitle>
            <DialogDescription>
              This agent action is marked as destructive (e.g. database schema change, service rollback, cloud resource provisioning).
            </DialogDescription>
          </DialogHeader>

          {targetAction && (
            <div className="space-y-4 pt-2">
              <div className="p-3 rounded bg-danger-subtle/20 border border-danger/30 text-xs">
                <span className="font-bold text-danger block mb-1">
                  Action: {targetAction.actionType}
                </span>
                <CodeBlock
                  code={JSON.stringify(targetAction.parameters, null, 2)}
                  language="json"
                />
              </div>

              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-text-primary block">
                  To confirm execution, type <span className="font-mono font-bold text-danger">CONFIRM</span> below:
                </label>
                <input
                  type="text"
                  placeholder="CONFIRM"
                  value={confirmInput}
                  onChange={(e) => setConfirmInput(e.target.value)}
                  className="w-full px-3 py-2 bg-subtle border border-border rounded-control font-mono text-sm text-text-primary focus:outline-none focus:ring-2 focus:ring-danger"
                  autoFocus
                />
              </div>

              <div className="flex justify-end gap-2 pt-2 border-t border-border">
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => setDestructiveModalOpen(false)}
                >
                  Cancel
                </Button>
                <Button
                  type="button"
                  variant="default"
                  size="sm"
                  disabled={confirmInput.trim().toUpperCase() !== "CONFIRM" || submitting}
                  onClick={handleConfirmDestructive}
                  className="bg-danger text-white hover:bg-danger/90"
                >
                  {submitting ? "Executing..." : "Confirm & Authorize"}
                </Button>
              </div>
            </div>
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}

export default ApprovalInboxPage;
