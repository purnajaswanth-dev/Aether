import React, { useState, useEffect, useCallback, useRef } from "react";
import { useParams, Link } from "react-router-dom";
import { api } from "@/services/api";
import {
  Task,
  TaskStatus,
  TaskPriority,
  TaskEvent,
  ImplementationRecord,
  TestExecutionRecord,
  DebuggingRecord,
  TaskLink,
  TaskContext,
  AgentHandoff,
} from "@/types";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
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
  CheckSquare,
  Plus,
  Search,
  Filter,
  CheckCircle2,
  Clock,
  AlertTriangle,
  Flame,
  Bot,
  User as UserIcon,
  Cpu,
  ArrowRight,
  GitCommit,
  Rocket,
  Scale,
  FileCode,
  ShieldCheck,
  ShieldAlert,
  X,
  ExternalLink,
  ChevronRight,
  Kanban,
  List as ListIcon,
  RefreshCw,
  Terminal,
  Bug,
  HelpCircle,
  Sparkles,
} from "lucide-react";

const TASK_STATUSES: { key: TaskStatus; label: string; variant: "default" | "success" | "warning" | "danger" | "info" | "accent" | "neutral" }[] = [
  { key: "BACKLOG", label: "Backlog", variant: "neutral" },
  { key: "READY", label: "Ready", variant: "info" },
  { key: "IN_PROGRESS", label: "In Progress", variant: "accent" },
  { key: "IN_REVIEW", label: "In Review", variant: "warning" },
  { key: "BLOCKED", label: "Blocked", variant: "danger" },
  { key: "DONE_UNVERIFIED", label: "Done (Unverified)", variant: "warning" },
  { key: "VERIFIED", label: "Verified", variant: "success" },
  { key: "CANCELLED", label: "Cancelled", variant: "neutral" },
];

export function TasksPage() {
  const { projectId } = useParams<{ projectId: string }>();

  // State
  const [tasks, setTasks] = useState<Task[]>([]);
  const [loading, setLoading] = useState(true);
  const [viewMode, setViewMode] = useState<"list" | "board">("list");
  const [filterStatus, setFilterStatus] = useState<string>("ALL");
  const [searchQuery, setSearchQuery] = useState("");
  const [selectedIndex, setSelectedIndex] = useState<number>(0);

  // Selected Task & Drawer
  const [selectedTask, setSelectedTask] = useState<Task | null>(null);
  const [taskContext, setTaskContext] = useState<TaskContext | null>(null);
  const [taskEvents, setTaskEvents] = useState<TaskEvent[]>([]);
  const [taskLinks, setTaskLinks] = useState<TaskLink[]>([]);
  const [debuggingRecords, setDebuggingRecords] = useState<DebuggingRecord[]>([]);
  const [drawerLoading, setDrawerLoading] = useState(false);

  // Modals
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [newTitle, setNewTitle] = useState("");
  const [newDescription, setNewDescription] = useState("");
  const [newPriority, setNewPriority] = useState<TaskPriority>("MEDIUM");
  const [submitting, setSubmitting] = useState(false);

  // Override Modal
  const [overrideModalOpen, setOverrideModalOpen] = useState(false);
  const [overrideReason, setOverrideReason] = useState("");
  const [pendingStatusTarget, setPendingStatusTarget] = useState<TaskStatus | null>(null);

  // Active Tab inside Drawer
  const [drawerTab, setDrawerTab] = useState<"overview" | "claims_evidence" | "handoffs" | "debugging" | "activity">("overview");

  // Load tasks
  const loadTasks = useCallback(async () => {
    if (!projectId) return;
    try {
      setLoading(true);
      const data = await api.listTasks(projectId);
      setTasks(data);
    } catch (err) {
      console.error("Failed to load tasks", err);
    } finally {
      setLoading(false);
    }
  }, [projectId]);

  useEffect(() => {
    loadTasks();
  }, [loadTasks]);

  // Load Task Context when selected
  const loadTaskDetails = useCallback(async (task: Task) => {
    if (!projectId) return;
    setSelectedTask(task);
    setDrawerLoading(true);
    try {
      const [ctx, evts, lnks, dbg] = await Promise.all([
        api.getTaskContext(projectId, task.id).catch(() => null),
        api.listTaskEvents(projectId, task.id).catch(() => []),
        api.listTaskLinks(projectId, task.id).catch(() => []),
        api.listDebuggingRecords(projectId, task.id).catch(() => []),
      ]);
      setTaskContext(ctx);
      setTaskEvents(evts);
      setTaskLinks(lnks);
      setDebuggingRecords(dbg);
    } catch (err) {
      console.error("Failed to load task details", err);
    } finally {
      setDrawerLoading(false);
    }
  }, [projectId]);

  // Filter tasks
  const filteredTasks = tasks.filter((t) => {
    const matchesStatus = filterStatus === "ALL" || t.status === filterStatus;
    const matchesSearch =
      t.title.toLowerCase().includes(searchQuery.toLowerCase()) ||
      t.key.toLowerCase().includes(searchQuery.toLowerCase()) ||
      (t.description && t.description.toLowerCase().includes(searchQuery.toLowerCase()));
    return matchesStatus && matchesSearch;
  });

  // Keyboard navigation: C for create, J/K for list, Enter to open, Esc to close
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      // Ignore if typing in an input or textarea
      if (
        document.activeElement?.tagName === "INPUT" ||
        document.activeElement?.tagName === "TEXTAREA"
      ) {
        return;
      }

      if (e.key === "c" || e.key === "C") {
        e.preventDefault();
        setCreateModalOpen(true);
      } else if (e.key === "Escape") {
        if (createModalOpen) setCreateModalOpen(false);
        else if (overrideModalOpen) setOverrideModalOpen(false);
        else if (selectedTask) setSelectedTask(null);
      } else if (e.key === "j" || e.key === "J") {
        e.preventDefault();
        setSelectedIndex((prev) => Math.min(prev + 1, Math.max(0, filteredTasks.length - 1)));
      } else if (e.key === "k" || e.key === "K") {
        e.preventDefault();
        setSelectedIndex((prev) => Math.max(prev - 1, 0));
      } else if (e.key === "Enter") {
        if (!selectedTask && filteredTasks[selectedIndex]) {
          e.preventDefault();
          loadTaskDetails(filteredTasks[selectedIndex]);
        }
      }
    };

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [createModalOpen, overrideModalOpen, selectedTask, filteredTasks, selectedIndex, loadTaskDetails]);

  // Create Task
  const handleCreateTask = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!projectId || !newTitle.trim()) return;
    try {
      setSubmitting(true);
      const created = await api.createTask(projectId, {
        title: newTitle.trim(),
        description: newDescription.trim() || undefined,
        priority: newPriority,
      });
      setTasks((prev) => [created, ...prev]);
      setCreateModalOpen(false);
      setNewTitle("");
      setNewDescription("");
      loadTaskDetails(created);
    } catch (err: any) {
      alert("Failed to create task: " + (err.message || err));
    } finally {
      setSubmitting(false);
    }
  };

  // Accept Agent Proposal
  const handleAcceptProposal = async (task: Task) => {
    if (!projectId) return;
    try {
      const accepted = await api.acceptProposedTask(projectId, task.id);
      setTasks((prev) => prev.map((t) => (t.id === accepted.id ? accepted : t)));
      setSelectedTask(accepted);
      loadTaskDetails(accepted);
    } catch (err: any) {
      alert("Failed to accept proposal: " + (err.message || err));
    }
  };

  // Transition Task Status
  const handleUpdateStatus = async (targetStatus: TaskStatus, reason?: string, override?: string) => {
    if (!projectId || !selectedTask) return;

    if (targetStatus === "VERIFIED" && !override) {
      // Check if task already has passing test executions
      const hasPassingTest = taskContext?.priorAttempts?.length || false; // Or prompt for override if human verification
      setPendingStatusTarget(targetStatus);
      setOverrideModalOpen(true);
      return;
    }

    try {
      const updated = await api.updateTaskStatus(projectId, selectedTask.id, {
        status: targetStatus,
        expectedLockVersion: selectedTask.lockVersion,
        reason: reason || "Manual status change by user",
        humanOverrideReason: override,
        actorType: "HUMAN",
      });
      setTasks((prev) => prev.map((t) => (t.id === updated.id ? updated : t)));
      setSelectedTask(updated);
      setOverrideModalOpen(false);
      setOverrideReason("");
      loadTaskDetails(updated);
    } catch (err: any) {
      alert("Transition failed: " + (err.message || err));
    }
  };

  const getStatusBadge = (status: TaskStatus) => {
    const config = TASK_STATUSES.find((s) => s.key === status) || { label: status, variant: "default" as const };
    return (
      <Badge variant={config.variant} size="sm">
        {config.label}
      </Badge>
    );
  };

  const getPriorityBadge = (priority: TaskPriority) => {
    switch (priority) {
      case "CRITICAL":
        return <Badge variant="danger" size="sm"><Flame className="w-3 h-3 mr-1" />Critical</Badge>;
      case "HIGH":
        return <Badge variant="warning" size="sm">High</Badge>;
      case "MEDIUM":
        return <Badge variant="info" size="sm">Medium</Badge>;
      case "LOW":
        return <Badge variant="neutral" size="sm">Low</Badge>;
    }
  };

  return (
    <div className="flex flex-col h-full bg-canvas text-text-primary">
      {/* Top Header Bar */}
      <header className="flex items-center justify-between px-6 py-4 border-b border-border bg-subtle/30 shrink-0">
        <div className="flex items-center gap-3">
          <div className="flex h-9 w-9 items-center justify-center rounded-control bg-accent-subtle text-accent border border-accent/20">
            <CheckSquare className="h-5 w-5" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h1 className="text-lg font-semibold tracking-tight text-text-primary">Workflow Tasks</h1>
              <span className="text-xs px-2 py-0.5 rounded-full bg-subtle border border-border text-text-secondary font-mono">
                {tasks.length}
              </span>
            </div>
            <p className="text-xs text-text-tertiary">
              Linear-density task tracking, agent handoffs, and verification evidence
            </p>
          </div>
        </div>

        {/* View Mode & Actions */}
        <div className="flex items-center gap-2">
          {/* Quick shortcuts helper */}
          <div className="hidden lg:flex items-center gap-1.5 text-[11px] text-text-tertiary mr-2 select-none">
            <kbd className="px-1.5 py-0.5 rounded bg-subtle border border-border font-mono">C</kbd>
            <span>new</span>
            <span className="mx-1">•</span>
            <kbd className="px-1.5 py-0.5 rounded bg-subtle border border-border font-mono">J</kbd>
            <kbd className="px-1.5 py-0.5 rounded bg-subtle border border-border font-mono">K</kbd>
            <span>navigate</span>
            <span className="mx-1">•</span>
            <kbd className="px-1.5 py-0.5 rounded bg-subtle border border-border font-mono">↵</kbd>
            <span>open</span>
          </div>

          <div className="flex items-center rounded-control border border-border bg-subtle p-0.5">
            <button
              onClick={() => setViewMode("list")}
              className={`p-1.5 rounded text-xs transition-colors ${viewMode === "list" ? "bg-canvas text-text-primary shadow-xs" : "text-text-tertiary hover:text-text-secondary"}`}
              title="List View"
            >
              <ListIcon className="h-4 w-4" />
            </button>
            <button
              onClick={() => setViewMode("board")}
              className={`p-1.5 rounded text-xs transition-colors ${viewMode === "board" ? "bg-canvas text-text-primary shadow-xs" : "text-text-tertiary hover:text-text-secondary"}`}
              title="Kanban Board View"
            >
              <Kanban className="h-4 w-4" />
            </button>
          </div>

          <Button variant="outline" size="sm" onClick={loadTasks} disabled={loading}>
            <RefreshCw className={`h-3.5 w-3.5 mr-1 ${loading ? "animate-spin" : ""}`} />
            Sync
          </Button>

          <Button variant="default" size="sm" onClick={() => setCreateModalOpen(true)}>
            <Plus className="h-4 w-4 mr-1" />
            New Task
          </Button>
        </div>
      </header>

      {/* Filter and Search Bar */}
      <div className="flex items-center gap-3 px-6 py-2.5 border-b border-border bg-canvas text-xs shrink-0">
        <div className="relative flex-1 max-w-sm">
          <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-text-tertiary" />
          <input
            type="text"
            placeholder="Search by title, key, or description... (press /)"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full pl-8 pr-3 py-1.5 bg-subtle border border-border rounded-control text-xs text-text-primary placeholder:text-text-tertiary focus:outline-none focus:ring-1 focus:ring-accent"
          />
        </div>

        <div className="flex items-center gap-1.5 overflow-x-auto py-1">
          <button
            onClick={() => setFilterStatus("ALL")}
            className={`px-2.5 py-1 rounded-full text-[11px] font-medium transition-colors ${
              filterStatus === "ALL" ? "bg-accent text-white" : "bg-subtle text-text-secondary hover:bg-subtle/80"
            }`}
          >
            All ({tasks.length})
          </button>
          {TASK_STATUSES.map((s) => {
            const count = tasks.filter((t) => t.status === s.key).length;
            if (count === 0 && filterStatus !== s.key) return null;
            return (
              <button
                key={s.key}
                onClick={() => setFilterStatus(s.key)}
                className={`px-2.5 py-1 rounded-full text-[11px] font-medium transition-colors flex items-center gap-1.5 ${
                  filterStatus === s.key ? "bg-accent text-white" : "bg-subtle text-text-secondary hover:bg-subtle/80"
                }`}
              >
                <span>{s.label}</span>
                <span className="text-[10px] opacity-80 font-mono">({count})</span>
              </button>
            );
          })}
        </div>
      </div>

      {/* Main Content Area */}
      <div className="flex-1 overflow-hidden relative flex">
        {loading && tasks.length === 0 ? (
          <div className="p-6 space-y-3 w-full">
            <Skeleton className="h-8 w-1/3" />
            <Skeleton className="h-10 w-full" />
            <Skeleton className="h-10 w-full" />
            <Skeleton className="h-10 w-full" />
          </div>
        ) : filteredTasks.length === 0 ? (
          <div className="flex-1 flex items-center justify-center p-8">
            <EmptyState
              icon={CheckSquare}
              title="No tasks found"
              description={searchQuery || filterStatus !== "ALL" ? "No tasks match your active filters." : "Create your first task or have an AI agent propose one."}
              actionLabel="Create Task (C)"
              onAction={() => setCreateModalOpen(true)}
            />
          </div>
        ) : viewMode === "list" ? (
          /* List View (Linear Density) */
          <div className="flex-1 overflow-y-auto divide-y divide-border/60">
            {filteredTasks.map((task, idx) => {
              const isSelected = selectedTask?.id === task.id || selectedIndex === idx;
              return (
                <div
                  key={task.id}
                  onClick={() => {
                    setSelectedIndex(idx);
                    loadTaskDetails(task);
                  }}
                  className={`group flex items-center justify-between px-6 py-2.5 cursor-pointer transition-colors duration-100 select-none ${
                    selectedTask?.id === task.id
                      ? "bg-accent/10 border-l-2 border-accent"
                      : isSelected
                      ? "bg-subtle/60"
                      : "hover:bg-subtle/40"
                  }`}
                >
                  <div className="flex items-center gap-3.5 min-w-0 flex-1">
                    <span className="text-xs font-mono font-medium text-text-tertiary w-16 shrink-0">
                      {task.key}
                    </span>
                    <div className="shrink-0">{getStatusBadge(task.status)}</div>
                    <span className="text-xs font-medium text-text-primary truncate">
                      {task.title}
                    </span>
                    {task.isProposedByAgent && (
                      <Badge variant="accent" size="sm" className="shrink-0 bg-accent/15 text-accent border-accent/30">
                        <Bot className="h-3 w-3 mr-1" />
                        Proposed by Agent
                      </Badge>
                    )}
                  </div>

                  <div className="flex items-center gap-4 shrink-0 text-xs text-text-tertiary">
                    {getPriorityBadge(task.priority)}
                    <span className="text-[11px] font-mono">v{task.lockVersion}</span>
                    <span className="text-[11px]">
                      {new Date(task.updatedAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
                    </span>
                    <ChevronRight className="h-4 w-4 text-text-tertiary opacity-0 group-hover:opacity-100 transition-opacity" />
                  </div>
                </div>
              );
            })}
          </div>
        ) : (
          /* Kanban Board View */
          <div className="flex-1 overflow-x-auto p-4 flex gap-3">
            {TASK_STATUSES.filter(s => s.key !== "CANCELLED").map((col) => {
              const colTasks = filteredTasks.filter((t) => t.status === col.key);
              return (
                <div key={col.key} className="w-72 shrink-0 flex flex-col bg-subtle/30 rounded-panel border border-border p-2">
                  <div className="flex items-center justify-between px-2 py-1.5 mb-2">
                    <span className="text-xs font-semibold text-text-secondary">{col.label}</span>
                    <span className="text-xs font-mono text-text-tertiary px-1.5 py-0.5 rounded bg-subtle">
                      {colTasks.length}
                    </span>
                  </div>

                  <div className="flex-1 overflow-y-auto space-y-2 pr-1">
                    {colTasks.map((task) => (
                      <div
                        key={task.id}
                        onClick={() => loadTaskDetails(task)}
                        className={`p-3 rounded-control border bg-canvas text-xs cursor-pointer shadow-xs hover:border-accent transition-all ${
                          selectedTask?.id === task.id ? "border-accent ring-1 ring-accent" : "border-border"
                        }`}
                      >
                        <div className="flex items-center justify-between text-[11px] text-text-tertiary mb-1">
                          <span className="font-mono">{task.key}</span>
                          {getPriorityBadge(task.priority)}
                        </div>
                        <h4 className="font-medium text-text-primary line-clamp-2 mb-2">{task.title}</h4>
                        {task.isProposedByAgent && (
                          <div className="mb-2">
                            <Badge variant="accent" size="sm">
                              <Bot className="h-2.5 w-2.5 mr-1" />
                              Proposed
                            </Badge>
                          </div>
                        )}
                        <div className="flex items-center justify-between text-[10px] text-text-tertiary pt-1 border-t border-border/50">
                          <span>lock v{task.lockVersion}</span>
                          <span>{new Date(task.updatedAt).toLocaleDateString()}</span>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              );
            })}
          </div>
        )}

        {/* Task Detail Side Panel Drawer (180ms ease-out) */}
        {selectedTask && (
          <aside
            className="w-[480px] lg:w-[560px] border-l border-border bg-canvas flex flex-col h-full shadow-popover z-20 transition-all duration-[180ms] ease-out shrink-0"
          >
            {/* Drawer Header */}
            <div className="p-4 border-b border-border flex items-center justify-between bg-subtle/20 shrink-0">
              <div className="flex items-center gap-2">
                <span className="text-xs font-mono font-bold text-accent">{selectedTask.key}</span>
                {getStatusBadge(selectedTask.status)}
                {selectedTask.isProposedByAgent && (
                  <Badge variant="accent" size="sm">Proposed by Agent</Badge>
                )}
              </div>
              <Button
                variant="ghost"
                size="iconSm"
                onClick={() => setSelectedTask(null)}
                className="text-text-tertiary hover:text-text-primary"
              >
                <X className="h-4 w-4" />
              </Button>
            </div>

            {/* Agent Proposal Callout */}
            {selectedTask.isProposedByAgent && (
              <div className="mx-4 mt-3 p-3 bg-accent-subtle/40 border border-accent/20 rounded-panel text-xs">
                <div className="flex items-center justify-between mb-1">
                  <span className="font-semibold text-accent flex items-center gap-1.5">
                    <Sparkles className="h-3.5 w-3.5" />
                    Proposed by Autonomous Agent
                  </span>
                  <Button
                    variant="default"
                    size="sm"
                    onClick={() => handleAcceptProposal(selectedTask)}
                    className="text-xs h-7 px-2.5"
                  >
                    Accept Proposal
                  </Button>
                </div>
                {selectedTask.agentProposalReason && (
                  <p className="text-text-secondary mt-1">{selectedTask.agentProposalReason}</p>
                )}
              </div>
            )}

            {/* Task Title & Status Controls */}
            <div className="p-4 border-b border-border shrink-0">
              <h2 className="text-base font-semibold text-text-primary leading-snug mb-2">
                {selectedTask.title}
              </h2>
              {selectedTask.description && (
                <p className="text-xs text-text-secondary whitespace-pre-wrap mb-3">
                  {selectedTask.description}
                </p>
              )}

              {/* Status Action Transitions */}
              <div className="pt-2 border-t border-border/60">
                <span className="text-[11px] font-semibold text-text-tertiary uppercase tracking-wider block mb-1.5">
                  Update Lifecycle State
                </span>
                <div className="flex flex-wrap gap-1.5">
                  {selectedTask.status === "BACKLOG" && (
                    <Button variant="outline" size="sm" onClick={() => handleUpdateStatus("READY")}>
                      Mark Ready
                    </Button>
                  )}
                  {selectedTask.status === "READY" && (
                    <Button variant="outline" size="sm" onClick={() => handleUpdateStatus("IN_PROGRESS")}>
                      Start Work (In Progress)
                    </Button>
                  )}
                  {selectedTask.status === "IN_PROGRESS" && (
                    <>
                      <Button variant="outline" size="sm" onClick={() => handleUpdateStatus("IN_REVIEW")}>
                        Submit for Review
                      </Button>
                      <Button variant="outline" size="sm" onClick={() => handleUpdateStatus("DONE_UNVERIFIED")}>
                        Done (Unverified)
                      </Button>
                      <Button variant="outline" size="sm" onClick={() => handleUpdateStatus("BLOCKED")}>
                        Block
                      </Button>
                    </>
                  )}
                  {selectedTask.status === "DONE_UNVERIFIED" && (
                    <>
                      <Button
                        variant="default"
                        size="sm"
                        onClick={() => handleUpdateStatus("VERIFIED")}
                        className="bg-success text-white hover:bg-success/90"
                      >
                        <ShieldCheck className="h-3.5 w-3.5 mr-1" />
                        Verify Task
                      </Button>
                      <Button variant="outline" size="sm" onClick={() => handleUpdateStatus("IN_PROGRESS")}>
                        Reopen to In Progress
                      </Button>
                    </>
                  )}
                  {selectedTask.status === "IN_REVIEW" && (
                    <>
                      <Button
                        variant="default"
                        size="sm"
                        onClick={() => handleUpdateStatus("VERIFIED")}
                        className="bg-success text-white hover:bg-success/90"
                      >
                        <ShieldCheck className="h-3.5 w-3.5 mr-1" />
                        Verify Task
                      </Button>
                      <Button variant="outline" size="sm" onClick={() => handleUpdateStatus("IN_PROGRESS")}>
                        Request Changes
                      </Button>
                    </>
                  )}
                  {selectedTask.status === "BLOCKED" && (
                    <Button variant="outline" size="sm" onClick={() => handleUpdateStatus("IN_PROGRESS")}>
                      Unblock
                    </Button>
                  )}
                </div>
              </div>
            </div>

            {/* Tab Navigation */}
            <div className="flex border-b border-border bg-subtle/30 px-3 text-xs shrink-0">
              <button
                onClick={() => setDrawerTab("overview")}
                className={`px-3 py-2 border-b-2 font-medium transition-colors ${
                  drawerTab === "overview" ? "border-accent text-accent" : "border-transparent text-text-tertiary hover:text-text-secondary"
                }`}
              >
                Overview & Links
              </button>
              <button
                onClick={() => setDrawerTab("claims_evidence")}
                className={`px-3 py-2 border-b-2 font-medium transition-colors flex items-center gap-1 ${
                  drawerTab === "claims_evidence" ? "border-accent text-accent" : "border-transparent text-text-tertiary hover:text-text-secondary"
                }`}
              >
                <span>Claims vs Evidence</span>
                {taskContext?.latestImplementation && (
                  <span className="w-1.5 h-1.5 rounded-full bg-accent" />
                )}
              </button>
              <button
                onClick={() => setDrawerTab("handoffs")}
                className={`px-3 py-2 border-b-2 font-medium transition-colors ${
                  drawerTab === "handoffs" ? "border-accent text-accent" : "border-transparent text-text-tertiary hover:text-text-secondary"
                }`}
              >
                Handoffs ({taskContext?.recentHandoffs?.length || 0})
              </button>
              <button
                onClick={() => setDrawerTab("debugging")}
                className={`px-3 py-2 border-b-2 font-medium transition-colors flex items-center gap-1 ${
                  drawerTab === "debugging" ? "border-accent text-accent" : "border-transparent text-text-tertiary hover:text-text-secondary"
                }`}
              >
                <span>Debugging</span>
                {debuggingRecords.length > 0 && (
                  <span className="text-[10px] font-mono px-1 rounded bg-danger-subtle text-danger">
                    {debuggingRecords.length}
                  </span>
                )}
              </button>
              <button
                onClick={() => setDrawerTab("activity")}
                className={`px-3 py-2 border-b-2 font-medium transition-colors ${
                  drawerTab === "activity" ? "border-accent text-accent" : "border-transparent text-text-tertiary hover:text-text-secondary"
                }`}
              >
                Trail ({taskEvents.length})
              </button>
            </div>

            {/* Tab Contents */}
            <div className="flex-1 overflow-y-auto p-4 space-y-4">
              {drawerLoading ? (
                <div className="space-y-3">
                  <Skeleton className="h-6 w-1/2" />
                  <Skeleton className="h-20 w-full" />
                  <Skeleton className="h-20 w-full" />
                </div>
              ) : drawerTab === "overview" ? (
                <div className="space-y-4 text-xs">
                  {/* Metadata Card */}
                  <div className="p-3 bg-subtle/30 rounded-panel border border-border grid grid-cols-2 gap-3">
                    <div>
                      <span className="text-text-tertiary block text-[11px]">Priority</span>
                      <span className="mt-0.5 inline-block">{getPriorityBadge(selectedTask.priority)}</span>
                    </div>
                    <div>
                      <span className="text-text-tertiary block text-[11px]">Optimistic Lock</span>
                      <span className="font-mono text-text-secondary">Version {selectedTask.lockVersion}</span>
                    </div>
                    <div>
                      <span className="text-text-tertiary block text-[11px]">Created</span>
                      <span className="text-text-secondary">{new Date(selectedTask.createdAt).toLocaleString()}</span>
                    </div>
                    <div>
                      <span className="text-text-tertiary block text-[11px]">Last Updated</span>
                      <span className="text-text-secondary">{new Date(selectedTask.updatedAt).toLocaleString()}</span>
                    </div>
                  </div>

                  {/* Human Override Banner if present */}
                  {selectedTask.humanOverrideReason && (
                    <div className="p-3 bg-warning-subtle/30 border border-warning/30 rounded-panel">
                      <span className="font-semibold text-warning flex items-center gap-1.5 mb-1">
                        <ShieldAlert className="h-3.5 w-3.5" />
                        Human Override Recorded
                      </span>
                      <p className="text-text-primary text-xs">{selectedTask.humanOverrideReason}</p>
                    </div>
                  )}

                  {/* Linked Decisions (ADRs) */}
                  <div>
                    <h3 className="text-xs font-semibold text-text-secondary uppercase tracking-wider mb-2 flex items-center gap-1.5">
                      <Scale className="h-3.5 w-3.5 text-accent" />
                      Linked Decisions ({taskLinks.filter(l => l.linkType === "DECISION").length})
                    </h3>
                    {taskLinks.filter(l => l.linkType === "DECISION").length === 0 ? (
                      <p className="text-text-tertiary italic">No technical decisions linked to this task.</p>
                    ) : (
                      <div className="space-y-1.5">
                        {taskLinks.filter(l => l.linkType === "DECISION").map((link) => (
                          <div key={link.id} className="p-2.5 rounded bg-subtle/40 border border-border flex items-center justify-between">
                            <span className="font-medium text-text-primary">{link.targetLabel}</span>
                            <Link
                              to={`/projects/${projectId}/decisions`}
                              className="text-accent hover:underline flex items-center gap-1 text-[11px]"
                            >
                              <span>View ADR</span>
                              <ExternalLink className="h-3 w-3" />
                            </Link>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>

                  {/* Linked Artifacts */}
                  <div>
                    <h3 className="text-xs font-semibold text-text-secondary uppercase tracking-wider mb-2 flex items-center gap-1.5">
                      <FileCode className="h-3.5 w-3.5 text-accent" />
                      Linked Artifacts & Logs ({taskLinks.filter(l => l.linkType === "ARTIFACT").length})
                    </h3>
                    {taskLinks.filter(l => l.linkType === "ARTIFACT").length === 0 ? (
                      <p className="text-text-tertiary italic">No artifacts linked.</p>
                    ) : (
                      <div className="space-y-1.5">
                        {taskLinks.filter(l => l.linkType === "ARTIFACT").map((link) => (
                          <div key={link.id} className="p-2.5 rounded bg-subtle/40 border border-border flex items-center justify-between">
                            <span className="font-medium text-text-primary">{link.targetLabel}</span>
                            <span className="font-mono text-[10px] text-text-tertiary">{link.targetId.substring(0, 8)}</span>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                </div>
              ) : drawerTab === "claims_evidence" ? (
                /* Constitutional Separation: Claims vs Evidence */
                <div className="space-y-5 text-xs">
                  {/* 1. AGENT CLAIMS SECTION */}
                  <div className="p-3.5 bg-subtle/30 rounded-panel border border-border">
                    <div className="flex items-center gap-2 mb-2 pb-2 border-b border-border">
                      <Bot className="h-4 w-4 text-accent" />
                      <h3 className="font-semibold text-text-primary">Agent Implementation Claims</h3>
                      <Badge variant="warning" size="sm" className="ml-auto">Unverified Claim</Badge>
                    </div>

                    {taskContext?.latestImplementation ? (
                      <div className="space-y-2.5">
                        <div>
                          <span className="text-text-tertiary text-[11px] block">Agent Summary</span>
                          <p className="text-text-primary font-medium mt-0.5">
                            {taskContext.latestImplementation.summary}
                          </p>
                        </div>

                        <div>
                          <span className="text-text-tertiary text-[11px] block">Platform / Session</span>
                          <span className="font-mono text-accent">
                            {taskContext.latestImplementation.agentPlatform}
                          </span>
                        </div>

                        {taskContext.latestImplementation.modifiedFiles?.length > 0 && (
                          <div>
                            <span className="text-text-tertiary text-[11px] block mb-1">Claimed Modified Files</span>
                            <div className="flex flex-wrap gap-1">
                              {taskContext.latestImplementation.modifiedFiles.map((file, i) => (
                                <span key={i} className="font-mono text-[10px] px-2 py-0.5 rounded bg-subtle border border-border">
                                  {file}
                                </span>
                              ))}
                            </div>
                          </div>
                        )}

                        {taskContext.latestImplementation.unresolvedIssues?.length > 0 && (
                          <div className="p-2 rounded bg-warning-subtle/30 border border-warning/20">
                            <span className="text-[11px] font-semibold text-warning block mb-1">
                              Self-Reported Unresolved Issues
                            </span>
                            <ul className="list-disc list-inside space-y-0.5 text-text-secondary">
                              {taskContext.latestImplementation.unresolvedIssues.map((issue, i) => (
                                <li key={i}>{issue}</li>
                              ))}
                            </ul>
                          </div>
                        )}
                      </div>
                    ) : (
                      <p className="text-text-tertiary italic">No implementation records reported yet.</p>
                    )}
                  </div>

                  {/* 2. VERIFIED EVIDENCE SECTION */}
                  <div className="p-3.5 bg-success-subtle/15 rounded-panel border border-success/30">
                    <div className="flex items-center gap-2 mb-2 pb-2 border-b border-success/20">
                      <ShieldCheck className="h-4 w-4 text-success" />
                      <h3 className="font-semibold text-success">Verified Evidence</h3>
                      <span className="text-[11px] text-text-tertiary ml-auto font-mono">
                        Mandatory for VERIFIED status
                      </span>
                    </div>

                    {/* Linked passing test executions */}
                    <div className="space-y-3">
                      <div>
                        <span className="text-text-tertiary text-[11px] font-semibold uppercase tracking-wider block mb-1.5">
                          Automated Test Runs
                        </span>
                        {taskLinks.filter(l => l.linkType === "ARTIFACT" && l.targetLabel.includes("Test Execution")).length > 0 ? (
                          <div className="space-y-1.5">
                            {taskLinks
                              .filter(l => l.linkType === "ARTIFACT" && l.targetLabel.includes("Test Execution"))
                              .map(lnk => (
                                <div key={lnk.id} className="p-2 rounded bg-canvas border border-success/30 flex items-center justify-between">
                                  <div className="flex items-center gap-2">
                                    <CheckCircle2 className="h-4 w-4 text-success" />
                                    <span className="font-medium text-text-primary">{lnk.targetLabel}</span>
                                  </div>
                                  <span className="text-[10px] font-mono text-text-tertiary">{lnk.targetId.substring(0, 8)}</span>
                                </div>
                              ))}
                          </div>
                        ) : (
                          <div className="p-3 bg-canvas/60 rounded border border-border text-center text-text-tertiary">
                            <p>No passing automated test executions linked.</p>
                            <p className="text-[11px] mt-0.5">Evidence from MCP or CI test runners will appear here.</p>
                          </div>
                        )}
                      </div>

                      {/* Linked Commits */}
                      <div>
                        <span className="text-text-tertiary text-[11px] font-semibold uppercase tracking-wider block mb-1.5">
                          Linked Git Commits
                        </span>
                        {taskLinks.filter(l => l.linkType === "COMMIT").length > 0 ? (
                          <div className="space-y-1.5">
                            {taskLinks.filter(l => l.linkType === "COMMIT").map(lnk => (
                              <div key={lnk.id} className="p-2 rounded bg-canvas border border-border flex items-center justify-between">
                                <div className="flex items-center gap-2">
                                  <GitCommit className="h-3.5 w-3.5 text-accent" />
                                  <span className="font-mono text-[11px] text-accent">{lnk.targetId.substring(0, 8)}</span>
                                  <span className="text-text-secondary truncate">{lnk.targetLabel}</span>
                                </div>
                              </div>
                            ))}
                          </div>
                        ) : (
                          <p className="text-text-tertiary italic">No commits linked yet.</p>
                        )}
                      </div>
                    </div>
                  </div>
                </div>
              ) : drawerTab === "handoffs" ? (
                /* Handoff Trail Visualizer */
                <div className="space-y-3 text-xs">
                  <h3 className="font-semibold text-text-primary">Agent Collaboration & Handoff Trail</h3>
                  {(!taskContext?.recentHandoffs || taskContext.recentHandoffs.length === 0) ? (
                    <p className="text-text-tertiary italic">No agent handoffs recorded for this task yet.</p>
                  ) : (
                    <div className="relative pl-6 space-y-4 before:absolute before:left-2 before:top-2 before:bottom-2 before:w-0.5 before:bg-border">
                      {taskContext.recentHandoffs.map((handoff, i) => (
                        <div key={handoff.id || i} className="relative p-3 bg-subtle/30 rounded-panel border border-border">
                          <span className="absolute -left-6 top-3 w-2.5 h-2.5 rounded-full bg-accent ring-4 ring-canvas" />
                          <div className="flex items-center justify-between mb-1.5">
                            <div className="flex items-center gap-2 font-mono text-xs">
                              <span className="font-semibold text-accent">{handoff.fromPlatform}</span>
                              <ArrowRight className="h-3 w-3 text-text-tertiary" />
                              <span className="font-semibold text-accent">{handoff.toPlatform}</span>
                            </div>
                            <span className="text-[10px] text-text-tertiary">
                              {new Date(handoff.handoffAt).toLocaleTimeString()}
                            </span>
                          </div>
                          <p className="font-medium text-text-primary mb-1">{handoff.intent}</p>
                          <p className="text-text-secondary">{handoff.summary}</p>
                          {handoff.unresolvedQuestions?.length > 0 && (
                            <div className="mt-2 pt-2 border-t border-border/60">
                              <span className="text-[11px] font-medium text-warning block mb-1">Open Questions</span>
                              <ul className="list-disc list-inside text-text-secondary space-y-0.5">
                                {handoff.unresolvedQuestions.map((q, idx) => (
                                  <li key={idx}>{q}</li>
                                ))}
                              </ul>
                            </div>
                          )}
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              ) : drawerTab === "debugging" ? (
                /* Debugging Records & Isolated Logs */
                <div className="space-y-3 text-xs">
                  <h3 className="font-semibold text-text-primary">Isolated Deployment Failures</h3>
                  {debuggingRecords.length === 0 ? (
                    <p className="text-text-tertiary italic">No failures or active debugging records associated with this task.</p>
                  ) : (
                    <div className="space-y-3">
                      {debuggingRecords.map((dbg) => (
                        <div key={dbg.id} className="p-3 rounded-panel border border-danger/30 bg-danger-subtle/10 space-y-2">
                          <div className="flex items-center justify-between">
                            <Badge variant="danger" size="sm">
                              <Bug className="h-3 w-3 mr-1" />
                              {dbg.failureClassification}
                            </Badge>
                            <span className="font-mono text-[10px] text-text-tertiary">{dbg.status}</span>
                          </div>

                          {dbg.errorLine && (
                            <div>
                              <span className="text-[11px] text-text-tertiary block mb-0.5">Root Error Line</span>
                              <code className="block p-2 rounded bg-subtle font-mono text-[11px] text-danger overflow-x-auto">
                                {dbg.errorLine}
                              </code>
                            </div>
                          )}

                          {dbg.rootCauseHypothesis && (
                            <div>
                              <span className="text-[11px] text-text-tertiary block mb-0.5">Root Cause Hypothesis</span>
                              <p className="text-text-secondary">{dbg.rootCauseHypothesis}</p>
                            </div>
                          )}

                          <div className="text-[10px] text-text-tertiary pt-1 border-t border-border/40 flex items-center justify-between">
                            <span>Deploy: {dbg.deploymentId.substring(0, 10)}</span>
                            {dbg.resolvedAt && (
                              <span className="text-success font-semibold">Resolved at {new Date(dbg.resolvedAt).toLocaleTimeString()}</span>
                            )}
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              ) : (
                /* Task Activity Trail */
                <div className="space-y-3 text-xs">
                  <h3 className="font-semibold text-text-primary">Audit & Status History</h3>
                  <div className="relative pl-6 space-y-3 before:absolute before:left-2 before:top-2 before:bottom-2 before:w-0.5 before:bg-border">
                    {taskEvents.map((evt) => (
                      <div key={evt.id} className="relative p-2.5 rounded bg-subtle/30 border border-border">
                        <span className="absolute -left-6 top-3 w-2 h-2 rounded-full bg-accent ring-2 ring-canvas" />
                        <div className="flex items-center justify-between mb-1">
                          <span className="font-medium text-text-primary">{evt.reason || evt.eventType}</span>
                          <span className="text-[10px] text-text-tertiary font-mono">
                            {new Date(evt.createdAt).toLocaleTimeString()}
                          </span>
                        </div>
                        <div className="flex items-center gap-2 text-[10px] text-text-tertiary">
                          <Badge variant="neutral" size="sm">{evt.actorType}</Badge>
                          {evt.fromStatus && evt.toStatus && (
                            <span className="font-mono">
                              {evt.fromStatus} → {evt.toStatus}
                            </span>
                          )}
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          </aside>
        )}
      </div>

      {/* Create Task Modal */}
      <Dialog open={createModalOpen} onOpenChange={setCreateModalOpen}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>Create Task</DialogTitle>
            <DialogDescription>
              Add a new workflow coordination task to project memory.
            </DialogDescription>
          </DialogHeader>

          <form onSubmit={handleCreateTask} className="space-y-4 pt-2">
            <div>
              <label className="text-xs font-semibold text-text-secondary block mb-1">
                Task Title *
              </label>
              <Input
                type="text"
                placeholder="e.g. Implement AES-256 Token Encryption"
                value={newTitle}
                onChange={(e) => setNewTitle(e.target.value)}
                required
                autoFocus
              />
            </div>

            <div>
              <label className="text-xs font-semibold text-text-secondary block mb-1">
                Description / Context
              </label>
              <textarea
                placeholder="Details, acceptance criteria, or reference files..."
                value={newDescription}
                onChange={(e) => setNewDescription(e.target.value)}
                rows={3}
                className="w-full p-2.5 bg-subtle border border-border rounded-control text-xs text-text-primary focus:outline-none focus:ring-1 focus:ring-accent"
              />
            </div>

            <div>
              <label className="text-xs font-semibold text-text-secondary block mb-1">
                Priority
              </label>
              <select
                value={newPriority}
                onChange={(e) => setNewPriority(e.target.value as TaskPriority)}
                className="w-full p-2 bg-subtle border border-border rounded-control text-xs text-text-primary focus:outline-none focus:ring-1 focus:ring-accent"
              >
                <option value="LOW">Low</option>
                <option value="MEDIUM">Medium</option>
                <option value="HIGH">High</option>
                <option value="CRITICAL">Critical</option>
              </select>
            </div>

            <div className="flex justify-end gap-2 pt-2 border-t border-border">
              <Button type="button" variant="outline" size="sm" onClick={() => setCreateModalOpen(false)}>
                Cancel
              </Button>
              <Button type="submit" variant="default" size="sm" disabled={submitting || !newTitle.trim()}>
                {submitting ? "Creating..." : "Create Task"}
              </Button>
            </div>
          </form>
        </DialogContent>
      </Dialog>

      {/* Human Override Reason Modal (Required for VERIFIED without passing test evidence) */}
      <Dialog open={overrideModalOpen} onOpenChange={setOverrideModalOpen}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2 text-warning">
              <ShieldAlert className="h-5 w-5" />
              Human Override Required
            </DialogTitle>
            <DialogDescription>
              Constitutional Rule: Moving a task to VERIFIED without passing test execution evidence requires a recorded human justification.
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-3 pt-2">
            <p className="text-xs text-text-secondary">
              Please enter the audit justification for overriding automated test verification (e.g. manual verification in staging, non-code architectural change, external sign-off):
            </p>

            <textarea
              placeholder="e.g. Verified end-to-end payment gateway in staging sandbox environment with test card."
              value={overrideReason}
              onChange={(e) => setOverrideReason(e.target.value)}
              rows={3}
              className="w-full p-2.5 bg-subtle border border-border rounded-control text-xs text-text-primary focus:outline-none focus:ring-1 focus:ring-accent"
              autoFocus
            />

            <div className="flex justify-end gap-2 pt-2 border-t border-border">
              <Button type="button" variant="outline" size="sm" onClick={() => setOverrideModalOpen(false)}>
                Cancel
              </Button>
              <Button
                type="button"
                variant="default"
                size="sm"
                disabled={!overrideReason.trim()}
                onClick={() => {
                  if (pendingStatusTarget) {
                    handleUpdateStatus(pendingStatusTarget, "Human override to VERIFIED", overrideReason.trim());
                  }
                }}
                className="bg-warning text-white hover:bg-warning/90"
              >
                Confirm Human Override
              </Button>
            </div>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  );
}

export default TasksPage;
