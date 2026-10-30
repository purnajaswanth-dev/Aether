import React from "react";
import { useParams, Link, useNavigate } from "react-router-dom";
import { api } from "@/services/api";
import { Project, TimelineEvent } from "@/types";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import {
  FileCode2,
  Scale,
  History,
  Plug,
  Plus,
  FolderOpen,
  ArrowRight,
  ShieldCheck,
  CheckCircle2,
  Clock,
  Sparkles,
} from "lucide-react";
import { formatDate } from "@/lib/utils";

export function ProjectOverviewPage() {
  const { projectId } = useParams<{ projectId: string }>();
  const navigate = useNavigate();

  const [project, setProject] = React.useState<Project | null>(null);
  const [timeline, setTimeline] = React.useState<TimelineEvent[]>([]);
  const [artifactCount, setArtifactCount] = React.useState(0);
  const [decisionCount, setDecisionCount] = React.useState(0);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);

  React.useEffect(() => {
    if (!projectId) return;

    Promise.all([
      api.getProject(projectId),
      api.getTimeline(projectId),
      api.listArtifacts(projectId),
      api.listDecisions(projectId),
    ])
      .then(([proj, tl, arts, decs]) => {
        setProject(proj);
        setTimeline(tl);
        setArtifactCount(arts.length);
        setDecisionCount(decs.length);
      })
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, [projectId]);

  if (loading) {
    return (
      <div className="space-y-6">
        <Skeleton className="h-8 w-1/3" />
        <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
          {[1, 2, 3, 4].map((i) => (
            <Skeleton key={i} className="h-24 rounded-panel" />
          ))}
        </div>
        <Skeleton className="h-64 rounded-panel" />
      </div>
    );
  }

  if (error || !project) {
    return (
      <div className="rounded-control bg-danger-subtle p-4 text-xs text-danger border border-danger/20">
        Failed to load project: {error || "Project not found"}
      </div>
    );
  }

  return (
    <div className="space-y-8">
      {/* Project Header Banner */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 pb-6 border-b border-border">
        <div>
          <div className="flex items-center gap-2 mb-1.5">
            <Badge variant="neutral" size="sm" showDot={false} className="font-mono text-xs font-semibold">
              {project.publicId}
            </Badge>
            <span className="text-xs text-text-tertiary uppercase font-medium">{project.projectType}</span>
            <Badge variant="success" size="sm">Active</Badge>
          </div>
          <h1 className="text-2xl font-bold tracking-tight text-text-primary">{project.name}</h1>
          <p className="mt-1 text-sm text-text-secondary max-w-2xl leading-relaxed">
            {project.description || "No project description provided."}
          </p>
        </div>

        <div className="flex items-center gap-2 shrink-0">
          <Button variant="outline" onClick={() => navigate(`/projects/${projectId}/artifacts?action=new`)}>
            <FileCode2 className="mr-1.5 h-4 w-4" /> Save Artifact
          </Button>
          <Button onClick={() => navigate(`/projects/${projectId}/decisions?action=new`)}>
            <Scale className="mr-1.5 h-4 w-4" /> Propose Decision
          </Button>
        </div>
      </div>

      {/* Honest Capability Notice */}
      <div className="rounded-panel border border-border bg-subtle/50 p-3.5 flex items-start gap-3">
        <Sparkles className="h-4 w-4 text-accent shrink-0 mt-0.5" />
        <div className="text-xs leading-relaxed text-text-secondary">
          <span className="font-semibold text-text-primary">Phase 1 Foundation Active:</span> PostgreSQL 16 schema with
          pgvector, append-only triggers, Row-Level Security isolation, and SHA-256 verified artifact storage are operational.
          AI client connectivity (Claude, ChatGPT, Antigravity) will become connectable in Phase 2 via remote Model Context Protocol (MCP).
        </div>
      </div>

      {/* Metric Cards */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <Link
          to={`/projects/${projectId}/artifacts`}
          className="group rounded-panel border border-border bg-canvas p-4 shadow-lift hover:border-accent transition-colors"
        >
          <div className="flex items-center justify-between">
            <span className="text-xs font-medium text-text-secondary">Stored Artifacts</span>
            <FileCode2 className="h-4 w-4 text-info" />
          </div>
          <div className="mt-2 text-2xl font-bold text-text-primary">{artifactCount}</div>
          <div className="mt-1 text-[11px] text-text-tertiary flex items-center gap-1">
            <ShieldCheck className="h-3 w-3 text-success" /> Immutable versions
          </div>
        </Link>

        <Link
          to={`/projects/${projectId}/decisions`}
          className="group rounded-panel border border-border bg-canvas p-4 shadow-lift hover:border-accent transition-colors"
        >
          <div className="flex items-center justify-between">
            <span className="text-xs font-medium text-text-secondary">Technical Decisions</span>
            <Scale className="h-4 w-4 text-warning" />
          </div>
          <div className="mt-2 text-2xl font-bold text-text-primary">{decisionCount}</div>
          <div className="mt-1 text-[11px] text-text-tertiary">
            Human-approved lifecycle
          </div>
        </Link>

        <Link
          to={`/projects/${projectId}/timeline`}
          className="group rounded-panel border border-border bg-canvas p-4 shadow-lift hover:border-accent transition-colors"
        >
          <div className="flex items-center justify-between">
            <span className="text-xs font-medium text-text-secondary">Timeline Events</span>
            <History className="h-4 w-4 text-accent" />
          </div>
          <div className="mt-2 text-2xl font-bold text-text-primary">{timeline.length}</div>
          <div className="mt-1 text-[11px] text-text-tertiary">
            Append-only audit trail
          </div>
        </Link>

        <Link
          to={`/projects/${projectId}/integrations`}
          className="group rounded-panel border border-border bg-canvas p-4 shadow-lift hover:border-accent transition-colors"
        >
          <div className="flex items-center justify-between">
            <span className="text-xs font-medium text-text-secondary">Connected Providers</span>
            <Plug className="h-4 w-4 text-text-tertiary" />
          </div>
          <div className="mt-2 text-2xl font-bold text-text-primary">0 / 7</div>
          <div className="mt-1 text-[11px] text-text-tertiary">
            Planned for Phase 2–4
          </div>
        </Link>
      </div>

      {/* Tech Stack & Workspace Info */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        <div className="md:col-span-2 space-y-4">
          <div className="flex items-center justify-between">
            <h2 className="text-sm font-semibold text-text-primary">Recent Project Activity</h2>
            <Link to={`/projects/${projectId}/timeline`} className="text-xs text-accent hover:underline flex items-center gap-1">
              View all <ArrowRight className="h-3 w-3" />
            </Link>
          </div>

          <div className="rounded-panel border border-border bg-canvas overflow-hidden divide-y divide-border/60">
            {timeline.slice(0, 6).map((event) => (
              <div key={event.id} className="p-3 text-xs flex items-start gap-3 hover:bg-subtle/40 transition-colors">
                <div className="mt-0.5 h-2 w-2 rounded-full bg-accent shrink-0" />
                <div className="flex-1 min-w-0">
                  <div className="flex items-center justify-between gap-2">
                    <span className="font-semibold text-text-primary truncate">{event.eventType}</span>
                    <span className="text-[10px] text-text-tertiary shrink-0">{formatDate(event.timestamp)}</span>
                  </div>
                  <p className="mt-0.5 text-text-secondary leading-relaxed">{event.summary}</p>
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Sidebar Info */}
        <div className="space-y-4">
          <h2 className="text-sm font-semibold text-text-primary">Environment & Storage</h2>
          <div className="rounded-panel border border-border bg-canvas p-4 space-y-3 text-xs">
            <div>
              <span className="text-text-tertiary block">Storage Preference</span>
              <span className="font-mono text-text-primary font-medium">{project.storagePreference}</span>
            </div>
            <div>
              <span className="text-text-tertiary block">Directory Path</span>
              <span className="font-mono text-text-primary truncate block">
                {project.directory || "Not configured (Cloud/Local-first memory)"}
              </span>
            </div>
            <div>
              <span className="text-text-tertiary block mb-1">Technology Stack</span>
              <div className="flex flex-wrap gap-1">
                {project.techStack?.map((t) => (
                  <span key={t} className="px-1.5 py-0.5 rounded bg-subtle text-text-secondary border border-border font-mono text-[10px]">
                    {t}
                  </span>
                ))}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

