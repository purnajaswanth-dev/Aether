import React from "react";
import { Link, useNavigate } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/ui/empty-state";
import { api } from "@/services/api";
import { Project } from "@/types";
import { FolderPlus, FolderKanban, ArrowRight, HardDrive, Calendar } from "lucide-react";
import { formatDate } from "@/lib/utils";

export function ProjectsListPage() {
  const navigate = useNavigate();
  const [projects, setProjects] = React.useState<Project[]>([]);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);

  React.useEffect(() => {
    api
      .listProjects()
      .then((data) => setProjects(data))
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, []);

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-semibold text-text-primary tracking-tight">Projects</h1>
          <p className="text-xs text-text-secondary mt-0.5">
            Isolated engineering memory workspaces with append-only logs and provider integrations.
          </p>
        </div>
        <Button onClick={() => navigate("/projects/new")} className="gap-1.5">
          <FolderPlus className="h-4 w-4" />
          <span>New Project</span>
        </Button>
      </div>

      {loading && (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {[1, 2, 3].map((i) => (
            <div key={i} className="rounded-panel border border-border p-4 space-y-3">
              <Skeleton className="h-5 w-1/3" />
              <Skeleton className="h-4 w-3/4" />
              <Skeleton className="h-4 w-1/2" />
            </div>
          ))}
        </div>
      )}

      {error && (
        <div className="rounded-control bg-danger-subtle p-4 text-xs text-danger border border-danger/20">
          Failed to load projects: {error}
        </div>
      )}

      {!loading && !error && projects.length === 0 && (
        <EmptyState
          icon={FolderKanban}
          title="No projects initialized yet"
          description="Create your first engineering project to store immutable architecture documents, review technical decisions, and track AI agent context."
          actionLabel="Create Project"
          onAction={() => navigate("/projects/new")}
        />
      )}

      {!loading && !error && projects.length > 0 && (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {projects.map((proj) => (
            <Link
              key={proj.id}
              to={`/projects/${proj.id}`}
              className="group flex flex-col justify-between rounded-panel border border-border bg-canvas p-4 shadow-lift transition-all duration-hover hover:border-accent hover:shadow-popover/10 select-none"
            >
              <div>
                <div className="flex items-center justify-between mb-2">
                  <Badge variant="neutral" size="sm" showDot={false} className="font-mono text-[11px] font-semibold text-text-primary">
                    {proj.publicId}
                  </Badge>
                  <span className="text-[11px] text-text-tertiary uppercase font-medium">
                    {proj.projectType}
                  </span>
                </div>

                <h3 className="text-base font-semibold text-text-primary group-hover:text-accent transition-colors truncate">
                  {proj.name}
                </h3>

                <p className="mt-1 text-xs text-text-secondary line-clamp-2 leading-relaxed">
                  {proj.description || "No description provided."}
                </p>

                {/* Tech Stack Chips */}
                {proj.techStack && proj.techStack.length > 0 && (
                  <div className="mt-3 flex flex-wrap gap-1">
                    {proj.techStack.slice(0, 4).map((tech) => (
                      <span
                        key={tech}
                        className="inline-block px-1.5 py-0.5 rounded text-[10px] bg-subtle text-text-secondary border border-border font-mono"
                      >
                        {tech}
                      </span>
                    ))}
                    {proj.techStack.length > 4 && (
                      <span className="inline-block px-1 py-0.5 text-[10px] text-text-tertiary">
                        +{proj.techStack.length - 4}
                      </span>
                    )}
                  </div>
                )}
              </div>

              <div className="mt-4 pt-3 border-t border-border/60 flex items-center justify-between text-[11px] text-text-tertiary">
                <span className="flex items-center gap-1 font-mono">
                  <HardDrive className="h-3 w-3" />
                  {proj.storagePreference}
                </span>
                <span className="flex items-center gap-1">
                  <Calendar className="h-3 w-3" />
                  {formatDate(proj.createdAt)}
                </span>
                <ArrowRight className="h-3.5 w-3.5 text-text-tertiary group-hover:text-accent group-hover:translate-x-0.5 transition-all" />
              </div>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
