import React from "react";
import { useParams, Link } from "react-router-dom";
import { api } from "@/services/api";
import { TimelineEvent } from "@/types";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/ui/empty-state";
import {
  History,
  FileCode2,
  Scale,
  Sparkles,
  Bot,
  User,
  ShieldCheck,
  Filter,
} from "lucide-react";
import { formatDate } from "@/lib/utils";

export function TimelinePage() {
  const { projectId } = useParams<{ projectId: string }>();

  const [events, setEvents] = React.useState<TimelineEvent[]>([]);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);

  const [eventTypeFilter, setEventTypeFilter] = React.useState<string>("");
  const [platformFilter, setPlatformFilter] = React.useState<string>("");

  const loadTimeline = React.useCallback(async () => {
    if (!projectId) return;
    try {
      const data = await api.getTimeline(
        projectId,
        eventTypeFilter || undefined,
        platformFilter || undefined
      );
      setEvents(data);
    } catch (err: any) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }, [projectId, eventTypeFilter, platformFilter]);

  React.useEffect(() => {
    loadTimeline();
  }, [loadTimeline]);

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h1 className="text-xl font-semibold text-text-primary tracking-tight">Project Timeline</h1>
          <p className="text-xs text-text-secondary mt-0.5">
            Immutable, append-only chronological record of every artifact, decision, and system event.
          </p>
        </div>

        {/* Filters */}
        <div className="flex items-center gap-2">
          <select
            value={eventTypeFilter}
            onChange={(e) => setEventTypeFilter(e.target.value)}
            className="h-8 rounded border border-border bg-canvas px-3 text-xs text-text-primary"
          >
            <option value="">All Event Types</option>
            <option value="PROJECT_CREATED">PROJECT_CREATED</option>
            <option value="ARTIFACT_SAVED">ARTIFACT_SAVED</option>
            <option value="ARTIFACT_VERSION_CREATED">ARTIFACT_VERSION_CREATED</option>
            <option value="DECISION_PROPOSED">DECISION_PROPOSED</option>
            <option value="DECISION_REVIEW_REQUESTED">DECISION_REVIEW_REQUESTED</option>
            <option value="DECISION_APPROVED">DECISION_APPROVED</option>
            <option value="DECISION_REJECTED">DECISION_REJECTED</option>
            <option value="DECISION_SUPERSEDED">DECISION_SUPERSEDED</option>
          </select>

          <select
            value={platformFilter}
            onChange={(e) => setPlatformFilter(e.target.value)}
            className="h-8 rounded border border-border bg-canvas px-3 text-xs text-text-primary"
          >
            <option value="">All Platforms</option>
            <option value="PLATFORM">PLATFORM</option>
            <option value="MANUAL">MANUAL</option>
            <option value="AGENT">AGENT</option>
            <option value="CLAUDE">CLAUDE</option>
            <option value="CHATGPT">CHATGPT</option>
            <option value="ANTIGRAVITY">ANTIGRAVITY</option>
          </select>
        </div>
      </div>

      {loading ? (
        <div className="space-y-3">
          {[1, 2, 3, 4, 5].map((i) => (
            <Skeleton key={i} className="h-16 w-full rounded-panel" />
          ))}
        </div>
      ) : error ? (
        <div className="rounded-control bg-danger-subtle p-4 text-xs text-danger border border-danger/20">
          Failed to load timeline: {error}
        </div>
      ) : events.length === 0 ? (
        <EmptyState
          icon={History}
          title="No timeline events match the filter"
          description="Every state change automatically appends an immutable event to this audit trail."
        />
      ) : (
        <div className="relative border-l border-border ml-3 space-y-6 pb-6">
          {events.map((event) => {
            const isDecision = event.eventType.startsWith("DECISION");
            const isArtifact = event.eventType.startsWith("ARTIFACT");
            const isProject = event.eventType.startsWith("PROJECT");

            return (
              <div key={event.id} className="relative pl-6 select-none group">
                {/* Node icon on line */}
                <div className="absolute -left-2.5 top-1.5 flex h-5 w-5 items-center justify-center rounded-full border border-border bg-canvas shadow-lift">
                  {isDecision && <Scale className="h-3 w-3 text-warning" />}
                  {isArtifact && <FileCode2 className="h-3 w-3 text-info" />}
                  {isProject && <ShieldCheck className="h-3 w-3 text-success" />}
                  {!isDecision && !isArtifact && !isProject && (
                    <History className="h-3 w-3 text-text-secondary" />
                  )}
                </div>

                {/* Event Card */}
                <div className="rounded-panel border border-border bg-canvas p-4 shadow-lift transition-colors hover:border-accent/40">
                  <div className="flex flex-wrap items-center justify-between gap-2 mb-1.5">
                    <div className="flex items-center gap-2">
                      <span className="font-mono text-xs font-semibold text-text-primary">
                        {event.eventType}
                      </span>
                      <Badge variant="neutral" size="sm" showDot={false} className="font-mono text-[10px]">
                        {event.sourcePlatform}
                      </Badge>
                      <Badge variant={event.status === "SUCCESS" ? "success" : "danger"} size="sm">
                        {event.status}
                      </Badge>
                    </div>
                    <span className="text-[11px] font-mono text-text-tertiary">
                      {formatDate(event.timestamp)}
                    </span>
                  </div>

                  <p className="text-xs text-text-primary leading-relaxed font-normal">
                    {event.summary}
                  </p>

                  <div className="mt-3 pt-2 border-t border-border/40 flex flex-wrap items-center justify-between text-[11px] text-text-tertiary font-mono">
                    <div className="flex items-center gap-3">
                      <span className="flex items-center gap-1">
                        {event.actorType === "AGENT" ? (
                          <Bot className="h-3 w-3 text-info" />
                        ) : (
                          <User className="h-3 w-3 text-accent" />
                        )}
                        {event.actorId}
                      </span>
                      {event.associatedArtifactId && (
                        <Link
                          to={`/projects/${projectId}/artifacts`}
                          className="text-accent hover:underline flex items-center gap-1"
                        >
                          <FileCode2 className="h-3 w-3" /> View Artifact
                        </Link>
                      )}
                      {event.associatedDecisionId && (
                        <Link
                          to={`/projects/${projectId}/decisions`}
                          className="text-accent hover:underline flex items-center gap-1"
                        >
                          <Scale className="h-3 w-3" /> View Decision
                        </Link>
                      )}
                    </div>

                    <span className="text-[10px] text-text-tertiary">
                      ID: {event.id.substring(0, 8)}…
                    </span>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
