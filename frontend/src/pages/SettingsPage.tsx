import React from "react";
import { useParams } from "react-router-dom";
import { api } from "@/services/api";
import { Project } from "@/types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { ShieldCheck } from "lucide-react";

export function SettingsPage() {
  const { projectId } = useParams<{ projectId: string }>();

  const [project, setProject] = React.useState<Project | null>(null);
  const [permissions, setPermissions] = React.useState<any[]>([]);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);
  const [saved, setSaved] = React.useState(false);

  React.useEffect(() => {
    if (!projectId) return;

    Promise.all([api.getProject(projectId), api.getPermissions(projectId)])
      .then(([proj, perms]) => {
        setProject(proj);
        setPermissions(perms);
      })
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, [projectId]);

  const constitutionalPermissions = [
    { key: "READ_PROJECT_CONTEXT", label: "Read Project Context", desc: "Allow reading high-level project summary and tech stack" },
    { key: "READ_ARTIFACTS", label: "Read Artifacts", desc: "Allow reading verified architecture documents and schemas" },
    { key: "SAVE_ARTIFACTS", label: "Save Artifacts", desc: "Allow creating new immutable artifact versions" },
    { key: "RECORD_DECISIONS", label: "Record Decisions", desc: "Allow proposing technical decisions (state PROPOSED only)" },
    { key: "READ_DEPLOYMENT_DATA", label: "Read Deployment Data", desc: "Allow inspecting deployment statuses and error logs" },
    { key: "EXECUTE_APPROVED_ACTIONS", label: "Execute Approved Actions", desc: "Allow requesting actions for human review" },
  ];

  if (loading) {
    return <Skeleton className="h-96 w-full rounded-panel" />;
  }

  if (error || !project) {
    return (
      <div className="rounded-control bg-danger-subtle p-4 text-xs text-danger border border-danger/20">
        Failed to load settings: {error || "Project not found"}
      </div>
    );
  }

  return (
    <div className="space-y-8 max-w-3xl">
      <div>
        <h1 className="text-xl font-semibold text-text-primary tracking-tight">Project Settings</h1>
        <p className="text-xs text-text-secondary mt-0.5">
          Workspace metadata, storage preferences, and integration permissions matrix.
        </p>
      </div>

      {/* Project Metadata */}
      <div className="rounded-panel border border-border bg-canvas p-6 shadow-lift space-y-4">
        <h2 className="text-sm font-semibold text-text-primary pb-2 border-b border-border">
          Project Information
        </h2>

        <div className="grid grid-cols-2 gap-4 text-xs">
          <div>
            <label className="block text-text-tertiary mb-1 font-medium">Public ID</label>
            <span className="font-mono font-semibold text-text-primary">{project.publicId}</span>
          </div>
          <div>
            <label className="block text-text-tertiary mb-1 font-medium">Project Name</label>
            <span className="font-medium text-text-primary">{project.name}</span>
          </div>
        </div>

        <div className="text-xs">
          <label className="block text-text-tertiary mb-1 font-medium">Description</label>
          <p className="text-text-secondary leading-relaxed">{project.description || "None"}</p>
        </div>

        <div className="grid grid-cols-2 gap-4 text-xs">
          <div>
            <label className="block text-text-tertiary mb-1 font-medium">Storage Preference</label>
            <span className="font-mono font-medium text-text-primary">{project.storagePreference}</span>
          </div>
          <div>
            <label className="block text-text-tertiary mb-1 font-medium">Directory</label>
            <span className="font-mono text-text-secondary truncate block">
              {project.directory || "Not specified"}
            </span>
          </div>
        </div>
      </div>

      {/* Permissions Matrix */}
      <div className="rounded-panel border border-border bg-canvas p-6 shadow-lift space-y-4">
        <div>
          <div className="flex items-center gap-2 mb-1">
            <h2 className="text-sm font-semibold text-text-primary">
              Constitutional Permissions Matrix
            </h2>
            <Badge variant="neutral" size="sm" showDot={false}>
              Per Integration Scope
            </Badge>
          </div>
          <p className="text-xs text-text-secondary">
            These six permissions govern every remote AI agent, CLI connector, and external webhook.
            Agents can never elevate their own privileges.
          </p>
        </div>

        <div className="divide-y divide-border/60">
          {constitutionalPermissions.map((perm) => (
            <div key={perm.key} className="py-3 flex items-center justify-between text-xs">
              <div>
                <span className="font-mono font-semibold text-text-primary block">{perm.label}</span>
                <span className="text-text-secondary text-[11px]">{perm.desc}</span>
              </div>
              <Badge variant="neutral" size="sm">
                Default Off (Revocable)
              </Badge>
            </div>
          ))}
        </div>
      </div>

      {/* Danger Zone Placeholder (Hidden until Phase 6) */}
      <div className="hidden" aria-hidden="true">
        {/* Built fully in Phase 6: Audited tombstone deletion workflow */}
      </div>
    </div>
  );
}
