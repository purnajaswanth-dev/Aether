import React from "react";
import { useParams, useSearchParams } from "react-router-dom";
import { api } from "@/services/api";
import { TechnicalDecision, DecisionComparison } from "@/types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { SplitPane } from "@/components/ui/split-pane";
import { EmptyState } from "@/components/ui/empty-state";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from "@/components/ui/dialog";
import {
  Scale,
  Plus,
  CheckCircle2,
  XCircle,
  Clock,
  Archive,
  CheckCheck,
  FileSearch,
  User,
  Bot,
  AlertCircle,
  GitCompare,
} from "lucide-react";
import { formatDate } from "@/lib/utils";

export function DecisionsPage() {
  const { projectId } = useParams<{ projectId: string }>();
  const [searchParams] = useSearchParams();

  const [decisions, setDecisions] = React.useState<TechnicalDecision[]>([]);
  const [selectedDecision, setSelectedDecision] = React.useState<TechnicalDecision | null>(null);
  const [detailData, setDetailData] = React.useState<{ decision: TechnicalDecision; versions: any[]; evidence: any[] } | null>(null);
  const [comparison, setComparison] = React.useState<DecisionComparison | null>(null);
  const [compareMode, setCompareMode] = React.useState(false);

  const [loading, setLoading] = React.useState(true);
  const [detailLoading, setDetailLoading] = React.useState(false);
  const [stateFilter, setStateFilter] = React.useState<string>("");
  const [categoryFilter, setCategoryFilter] = React.useState<string>("");

  // New Decision Dialog
  const [createDialogOpen, setCreateDialogOpen] = React.useState(searchParams.get("action") === "new");
  const [newTitle, setNewTitle] = React.useState("");
  const [newCategory, setNewCategory] = React.useState("ARCHITECTURE");
  const [newProblem, setNewProblem] = React.useState("");
  const [newSolution, setNewSolution] = React.useState("");
  const [newRationale, setNewRationale] = React.useState("");
  const [newConsequences, setNewConsequences] = React.useState("");
  const [createLoading, setCreateLoading] = React.useState(false);

  // Action Dialog (Review / Approve / Reject / etc)
  const [actionNotes, setActionNotes] = React.useState("");
  const [actionLoading, setActionLoading] = React.useState(false);
  const [actionError, setActionError] = React.useState<string | null>(null);

  const loadDecisions = React.useCallback(async () => {
    if (!projectId) return;
    try {
      const list = await api.listDecisions(projectId, stateFilter || undefined, categoryFilter || undefined);
      setDecisions(list);
      if (list.length > 0 && !selectedDecision) {
        setSelectedDecision(list[0]);
      }
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  }, [projectId, stateFilter, categoryFilter, selectedDecision]);

  React.useEffect(() => {
    loadDecisions();
  }, [loadDecisions]);

  React.useEffect(() => {
    if (!projectId || !selectedDecision) return;

    setDetailLoading(true);
    setCompareMode(false);
    setComparison(null);
    setActionError(null);

    api
      .getDecision(projectId, selectedDecision.id)
      .then((data) => setDetailData(data))
      .catch((err) => console.error(err))
      .finally(() => setDetailLoading(false));
  }, [projectId, selectedDecision]);

  const handleCreateDecision = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!projectId || !newTitle.trim() || !newProblem.trim() || !newSolution.trim()) return;

    setCreateLoading(true);
    try {
      const created = await api.createDecision(projectId, {
        title: newTitle.trim(),
        category: newCategory,
        problemStatement: newProblem.trim(),
        proposedSolution: newSolution.trim(),
        rationale: newRationale.trim() || undefined,
        consequences: newConsequences.trim() || undefined,
      });

      setCreateDialogOpen(false);
      setNewTitle("");
      setNewProblem("");
      setNewSolution("");
      setNewRationale("");
      setNewConsequences("");
      await loadDecisions();
      setSelectedDecision(created);
    } catch (err: any) {
      alert("Failed to create decision: " + err.message);
    } finally {
      setCreateLoading(false);
    }
  };

  const handleAction = async (action: "review" | "approve" | "reject" | "supersede" | "implement" | "verify") => {
    if (!projectId || !selectedDecision) return;

    setActionLoading(true);
    setActionError(null);

    try {
      let updated: TechnicalDecision;
      const lock = selectedDecision.lockVersion;

      if (action === "review") {
        updated = await api.submitDecisionReview(projectId, selectedDecision.id, lock, actionNotes);
      } else if (action === "approve") {
        updated = await api.approveDecision(projectId, selectedDecision.id, lock, actionNotes);
      } else if (action === "reject") {
        updated = await api.rejectDecision(projectId, selectedDecision.id, lock, actionNotes);
      } else if (action === "supersede") {
        updated = await api.supersedeDecision(projectId, selectedDecision.id, lock, undefined, actionNotes);
      } else {
        throw new Error("Action not supported");
      }

      setActionNotes("");
      setSelectedDecision(updated);
      await loadDecisions();
    } catch (err: any) {
      setActionError(err.message || "Action failed");
    } finally {
      setActionLoading(false);
    }
  };

  const handleLoadComparison = async () => {
    if (!projectId || !selectedDecision) return;
    try {
      const res = await api.compareDecision(projectId, selectedDecision.id);
      setComparison(res);
      setCompareMode(true);
    } catch (err: any) {
      alert("Failed to load decision comparison: " + err.message);
    }
  };

  const getStateBadge = (state: string) => {
    switch (state) {
      case "PROPOSED":
        return <Badge variant="warning">PROPOSED</Badge>;
      case "UNDER_REVIEW":
        return <Badge variant="info">UNDER_REVIEW</Badge>;
      case "APPROVED":
        return <Badge variant="success">APPROVED</Badge>;
      case "REJECTED":
        return <Badge variant="danger">REJECTED</Badge>;
      case "SUPERSEDED":
        return <Badge variant="neutral">SUPERSEDED</Badge>;
      case "IMPLEMENTED":
        return <Badge variant="accent">IMPLEMENTED</Badge>;
      case "VERIFIED":
        return <Badge variant="success">VERIFIED</Badge>;
      default:
        return <Badge variant="default">{state}</Badge>;
    }
  };

  return (
    <div className="space-y-4 h-[calc(100vh-140px)] flex flex-col">
      {/* Header */}
      <div className="flex items-center justify-between shrink-0">
        <div>
          <h1 className="text-xl font-semibold text-text-primary tracking-tight">Technical Decisions</h1>
          <p className="text-xs text-text-secondary mt-0.5">
            Explicit lifecycle tracking with optimistic concurrency locking. Human approval strictly required.
          </p>
        </div>
        <Button onClick={() => setCreateDialogOpen(true)} className="gap-1.5">
          <Plus className="h-4 w-4" /> Propose Decision
        </Button>
      </div>

      {loading ? (
        <Skeleton className="h-full w-full rounded-panel" />
      ) : decisions.length === 0 ? (
        <EmptyState
          icon={Scale}
          title="No technical decisions recorded yet"
          description="Record architectural choices, database models, or API conventions. Decisions follow an explicit lifecycle (PROPOSED → UNDER_REVIEW → APPROVED)."
          actionLabel="Propose First Decision"
          onAction={() => setCreateDialogOpen(true)}
        />
      ) : (
        <div className="flex-1 min-h-0">
          <SplitPane
            left={
              <div className="flex flex-col h-full">
                {/* Filters */}
                <div className="p-2 border-b border-border bg-subtle/40 flex gap-2">
                  <select
                    value={stateFilter}
                    onChange={(e) => setStateFilter(e.target.value)}
                    className="h-7 w-1/2 rounded border border-border bg-canvas px-2 text-[11px] text-text-primary"
                  >
                    <option value="">All States</option>
                    <option value="PROPOSED">PROPOSED</option>
                    <option value="UNDER_REVIEW">UNDER_REVIEW</option>
                    <option value="APPROVED">APPROVED</option>
                    <option value="REJECTED">REJECTED</option>
                    <option value="SUPERSEDED">SUPERSEDED</option>
                  </select>
                  <select
                    value={categoryFilter}
                    onChange={(e) => setCategoryFilter(e.target.value)}
                    className="h-7 w-1/2 rounded border border-border bg-canvas px-2 text-[11px] text-text-primary"
                  >
                    <option value="">All Categories</option>
                    <option value="ARCHITECTURE">ARCHITECTURE</option>
                    <option value="DATABASE">DATABASE</option>
                    <option value="API">API</option>
                    <option value="SECURITY">SECURITY</option>
                    <option value="INFRASTRUCTURE">INFRASTRUCTURE</option>
                    <option value="CONVENTION">CONVENTION</option>
                  </select>
                </div>

                {/* List */}
                <div className="flex-1 overflow-y-auto divide-y divide-border/60">
                  {decisions.map((d) => {
                    const isSelected = selectedDecision?.id === d.id;
                    return (
                      <div
                        key={d.id}
                        onClick={() => setSelectedDecision(d)}
                        className={`p-3 cursor-pointer transition-colors select-none text-xs ${
                          isSelected
                            ? "bg-canvas border-l-2 border-l-accent shadow-lift"
                            : "hover:bg-subtle/60"
                        }`}
                      >
                        <div className="flex items-center justify-between mb-1">
                          <span className="font-mono text-[11px] font-semibold text-text-primary">
                            {d.publicId}
                          </span>
                          {getStateBadge(d.state)}
                        </div>
                        <h4 className="font-semibold text-text-primary truncate max-w-[240px] mb-1">
                          {d.title}
                        </h4>
                        <div className="flex items-center justify-between text-[11px] text-text-tertiary">
                          <span>{d.category}</span>
                          <span>v{d.currentVersion}</span>
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>
            }
            right={
              selectedDecision && detailData ? (
                <div className="flex flex-col h-full overflow-hidden">
                  {/* Top Bar for Selected Decision */}
                  <div className="p-4 border-b border-border flex flex-wrap items-center justify-between gap-3 bg-subtle/30 shrink-0">
                    <div>
                      <div className="flex items-center gap-2 mb-1">
                        <span className="font-mono text-xs font-semibold text-text-tertiary">
                          {selectedDecision.publicId}
                        </span>
                        <h2 className="text-base font-semibold text-text-primary">
                          {selectedDecision.title}
                        </h2>
                        {getStateBadge(selectedDecision.state)}
                      </div>
                      <div className="flex items-center gap-3 text-xs text-text-tertiary font-mono">
                        <span>Category: {selectedDecision.category}</span>
                        <span>•</span>
                        <span>v{selectedDecision.currentVersion}</span>
                        <span>•</span>
                        <span>Lock: {selectedDecision.lockVersion}</span>
                      </div>
                    </div>

                    <div className="flex items-center gap-2">
                      <Button
                        variant={compareMode ? "subtle" : "outline"}
                        size="sm"
                        onClick={() => {
                          if (compareMode) {
                            setCompareMode(false);
                          } else {
                            handleLoadComparison();
                          }
                        }}
                      >
                        <GitCompare className="h-3.5 w-3.5 mr-1" />
                        {compareMode ? "Standard View" : "Compare Versions"}
                      </Button>
                    </div>
                  </div>

                  {/* Actions Bar */}
                  <div className="px-4 py-2 border-b border-border bg-subtle/10 flex items-center justify-between gap-2 shrink-0">
                    <div className="flex items-center gap-1.5">
                      {selectedDecision.state === "PROPOSED" && (
                        <Button
                          size="sm"
                          variant="secondary"
                          onClick={() => handleAction("review")}
                          disabled={actionLoading}
                        >
                          <Clock className="h-3.5 w-3.5 mr-1 text-info" />
                          Submit for Review
                        </Button>
                      )}

                      {selectedDecision.state === "UNDER_REVIEW" && (
                        <>
                          <Button
                            size="sm"
                            variant="default"
                            onClick={() => handleAction("approve")}
                            disabled={actionLoading}
                          >
                            <CheckCircle2 className="h-3.5 w-3.5 mr-1 text-white" />
                            Approve Decision
                          </Button>
                          <Button
                            size="sm"
                            variant="dangerOutline"
                            onClick={() => handleAction("reject")}
                            disabled={actionLoading}
                          >
                            <XCircle className="h-3.5 w-3.5 mr-1 text-danger" />
                            Reject
                          </Button>
                        </>
                      )}

                      {selectedDecision.state === "APPROVED" && (
                        <Button
                          size="sm"
                          variant="outline"
                          onClick={() => handleAction("supersede")}
                          disabled={actionLoading}
                        >
                          <Archive className="h-3.5 w-3.5 mr-1 text-text-tertiary" />
                          Supersede
                        </Button>
                      )}
                    </div>

                    <div className="text-[11px] text-text-tertiary flex items-center gap-1">
                      {selectedDecision.latestActorType === "AGENT" ? (
                        <>
                          <Bot className="h-3 w-3 text-info" /> Agent Proposed
                        </>
                      ) : (
                        <>
                          <User className="h-3 w-3 text-accent" /> Human Reviewed
                        </>
                      )}
                    </div>
                  </div>

                  {actionError && (
                    <div className="p-3 bg-danger-subtle text-danger border-b border-danger/20 text-xs flex items-center gap-2">
                      <AlertCircle className="h-4 w-4 shrink-0" />
                      <span>{actionError}</span>
                    </div>
                  )}

                  {/* Content View */}
                  <div className="flex-1 overflow-auto p-6 bg-canvas">
                    {detailLoading ? (
                      <div className="space-y-4">
                        <Skeleton className="h-6 w-1/3" />
                        <Skeleton className="h-24 w-full" />
                        <Skeleton className="h-24 w-full" />
                      </div>
                    ) : compareMode && comparison ? (
                      /* Side by Side Comparison */
                      <div className="space-y-6">
                        <div className="border-b border-border pb-3">
                          <h3 className="text-sm font-semibold text-text-primary">
                            Lifecycle Version Comparison: {comparison.decision.publicId}
                          </h3>
                          <p className="text-xs text-text-secondary mt-0.5">
                            Original Proposal vs Review Findings vs Final Approved State
                          </p>
                        </div>

                        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                          {/* Column 1: Original Proposal */}
                          <div className="rounded-panel border border-border p-4 bg-subtle/30 space-y-3">
                            <div className="flex items-center justify-between pb-2 border-b border-border">
                              <span className="text-xs font-semibold text-text-primary">
                                1. Original Proposal
                              </span>
                              <Badge variant="warning" size="sm">
                                v{comparison.originalProposal.version}
                              </Badge>
                            </div>
                            <div>
                              <span className="text-[11px] font-medium text-text-tertiary block">
                                Problem Statement
                              </span>
                              <p className="text-xs text-text-primary mt-1">
                                {comparison.originalProposal.problemStatement}
                              </p>
                            </div>
                            <div>
                              <span className="text-[11px] font-medium text-text-tertiary block">
                                Proposed Solution
                              </span>
                              <p className="text-xs text-text-primary mt-1">
                                {comparison.originalProposal.proposedSolution}
                              </p>
                            </div>
                          </div>

                          {/* Column 2: Review Findings */}
                          <div className="rounded-panel border border-border p-4 bg-subtle/30 space-y-3">
                            <div className="flex items-center justify-between pb-2 border-b border-border">
                              <span className="text-xs font-semibold text-text-primary">
                                2. Review Findings
                              </span>
                              <Badge variant="info" size="sm">
                                Under Review
                              </Badge>
                            </div>
                            {comparison.reviewFindings.length === 0 ? (
                              <p className="text-xs text-text-tertiary italic">
                                No intermediate review notes recorded.
                              </p>
                            ) : (
                              comparison.reviewFindings.map((rf, idx) => (
                                <div key={idx} className="space-y-1">
                                  <span className="text-[10px] text-text-tertiary font-mono">
                                    Review by {rf.actorId}:
                                  </span>
                                  <p className="text-xs text-text-primary bg-canvas p-2 rounded border border-border">
                                    {rf.reviewNotes || rf.proposedSolution}
                                  </p>
                                </div>
                              ))
                            )}
                          </div>

                          {/* Column 3: Approved Version */}
                          <div className="rounded-panel border border-border p-4 bg-success-subtle/20 space-y-3">
                            <div className="flex items-center justify-between pb-2 border-b border-success/30">
                              <span className="text-xs font-semibold text-text-primary">
                                3. Final Approved State
                              </span>
                              {comparison.approvedVersion ? (
                                <Badge variant="success" size="sm">
                                  v{comparison.approvedVersion.version}
                                </Badge>
                              ) : (
                                <Badge variant="neutral" size="sm">
                                  Not Approved Yet
                                </Badge>
                              )}
                            </div>
                            {comparison.approvedVersion ? (
                              <>
                                <div>
                                  <span className="text-[11px] font-medium text-text-tertiary block">
                                    Approved Solution
                                  </span>
                                  <p className="text-xs text-text-primary font-medium mt-1">
                                    {comparison.approvedVersion.proposedSolution}
                                  </p>
                                </div>
                                <div>
                                  <span className="text-[11px] font-medium text-text-tertiary block">
                                    Approval Rationale
                                  </span>
                                  <p className="text-xs text-text-secondary mt-1">
                                    {comparison.approvedVersion.rationale || "Approved by human reviewer."}
                                  </p>
                                </div>
                              </>
                            ) : (
                              <p className="text-xs text-text-tertiary italic">
                                Decision is currently in {comparison.decision.state} lifecycle state.
                              </p>
                            )}
                          </div>
                        </div>
                      </div>
                    ) : (
                      /* Standard Detail View */
                      <div className="space-y-6 max-w-3xl">
                        <div>
                          <h3 className="text-xs font-semibold text-text-tertiary uppercase tracking-wider mb-2">
                            Problem Statement
                          </h3>
                          <div className="rounded-panel border border-border bg-subtle/20 p-4 text-sm text-text-primary leading-relaxed">
                            {selectedDecision.problemStatement}
                          </div>
                        </div>

                        <div>
                          <h3 className="text-xs font-semibold text-text-tertiary uppercase tracking-wider mb-2">
                            Proposed Solution
                          </h3>
                          <div className="rounded-panel border border-border bg-subtle/20 p-4 text-sm text-text-primary leading-relaxed font-mono">
                            {selectedDecision.proposedSolution}
                          </div>
                        </div>

                        {selectedDecision.rationale && (
                          <div>
                            <h3 className="text-xs font-semibold text-text-tertiary uppercase tracking-wider mb-2">
                              Rationale & Justification
                            </h3>
                            <div className="rounded-panel border border-border bg-subtle/20 p-4 text-xs text-text-secondary leading-relaxed">
                              {selectedDecision.rationale}
                            </div>
                          </div>
                        )}

                        {selectedDecision.consequences && (
                          <div>
                            <h3 className="text-xs font-semibold text-text-tertiary uppercase tracking-wider mb-2">
                              Consequences & Trade-offs
                            </h3>
                            <div className="rounded-panel border border-border bg-subtle/20 p-4 text-xs text-text-secondary leading-relaxed">
                              {selectedDecision.consequences}
                            </div>
                          </div>
                        )}

                        {/* Lifecycle History */}
                        <div className="pt-4 border-t border-border">
                          <h3 className="text-xs font-semibold text-text-primary mb-3">
                            Version Lifecycle Trail
                          </h3>
                          <div className="space-y-2">
                            {detailData.versions.map((ver) => (
                              <div
                                key={ver.id}
                                className="flex items-center justify-between p-2.5 rounded-control border border-border bg-subtle/30 text-xs"
                              >
                                <div className="flex items-center gap-2">
                                  <Badge variant="neutral" size="sm" showDot={false}>
                                    v{ver.version}
                                  </Badge>
                                  {getStateBadge(ver.state)}
                                  <span className="text-text-secondary">{ver.reviewNotes || "Version recorded"}</span>
                                </div>
                                <div className="flex items-center gap-2 text-text-tertiary text-[11px] font-mono">
                                  <span>by {ver.actorId}</span>
                                  <span>•</span>
                                  <span>{formatDate(ver.createdAt)}</span>
                                </div>
                              </div>
                            ))}
                          </div>
                        </div>
                      </div>
                    )}
                  </div>
                </div>
              ) : (
                <div className="flex items-center justify-center h-full text-text-tertiary text-xs">
                  Select a decision to inspect details and lifecycle actions.
                </div>
              )
            }
          />
        </div>
      )}

      {/* Propose Decision Dialog */}
      <Dialog open={createDialogOpen} onOpenChange={setCreateDialogOpen}>
        <DialogContent className="max-w-2xl">
          <form onSubmit={handleCreateDecision}>
            <DialogHeader>
              <DialogTitle>Propose Technical Decision</DialogTitle>
              <DialogDescription>
                Proposes a new architectural choice, library selection, or database schema change.
                Enters lifecycle state PROPOSED. Human approval is strictly required before adoption.
              </DialogDescription>
            </DialogHeader>

            <div className="space-y-4 py-4 text-xs">
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block font-medium text-text-primary mb-1">
                    Decision Title <span className="text-danger">*</span>
                  </label>
                  <Input
                    value={newTitle}
                    onChange={(e) => setNewTitle(e.target.value)}
                    placeholder="e.g. Adopt PostgreSQL with pgvector for Vector RAG"
                    required
                  />
                </div>
                <div>
                  <label className="block font-medium text-text-primary mb-1">Category</label>
                  <select
                    value={newCategory}
                    onChange={(e) => setNewCategory(e.target.value)}
                    className="h-8 w-full rounded-control border border-border bg-canvas px-3 text-xs text-text-primary"
                  >
                    <option value="ARCHITECTURE">ARCHITECTURE</option>
                    <option value="DATABASE">DATABASE</option>
                    <option value="API">API</option>
                    <option value="SECURITY">SECURITY</option>
                    <option value="INFRASTRUCTURE">INFRASTRUCTURE</option>
                    <option value="CONVENTION">CONVENTION</option>
                  </select>
                </div>
              </div>

              <div>
                <label className="block font-medium text-text-primary mb-1">
                  Problem Statement <span className="text-danger">*</span>
                </label>
                <textarea
                  value={newProblem}
                  onChange={(e) => setNewProblem(e.target.value)}
                  placeholder="Describe the architectural challenge, requirement, or constraint…"
                  rows={3}
                  className="w-full text-xs rounded-control border border-border bg-canvas p-2.5 text-text-primary"
                  required
                />
              </div>

              <div>
                <label className="block font-medium text-text-primary mb-1">
                  Proposed Solution <span className="text-danger">*</span>
                </label>
                <textarea
                  value={newSolution}
                  onChange={(e) => setNewSolution(e.target.value)}
                  placeholder="Exact technical decision and solution approach…"
                  rows={3}
                  className="w-full font-mono text-xs rounded-control border border-border bg-canvas p-2.5 text-text-primary"
                  required
                />
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block font-medium text-text-primary mb-1">Rationale</label>
                  <textarea
                    value={newRationale}
                    onChange={(e) => setNewRationale(e.target.value)}
                    placeholder="Why this option over alternatives…"
                    rows={2}
                    className="w-full text-xs rounded-control border border-border bg-canvas p-2 text-text-primary"
                  />
                </div>
                <div>
                  <label className="block font-medium text-text-primary mb-1">Consequences</label>
                  <textarea
                    value={newConsequences}
                    onChange={(e) => setNewConsequences(e.target.value)}
                    placeholder="Trade-offs, performance impact, or dependencies…"
                    rows={2}
                    className="w-full text-xs rounded-control border border-border bg-canvas p-2 text-text-primary"
                  />
                </div>
              </div>
            </div>

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setCreateDialogOpen(false)}>
                Cancel
              </Button>
              <Button type="submit" disabled={createLoading}>
                {createLoading ? "Proposing…" : "Propose Decision"}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  );
}

