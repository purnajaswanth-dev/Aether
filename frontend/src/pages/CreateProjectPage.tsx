import React from "react";
import { useNavigate } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { api } from "@/services/api";
import { ArrowLeft, Plus, X, FolderKanban, ShieldCheck } from "lucide-react";

export function CreateProjectPage() {
  const navigate = useNavigate();

  const [name, setName] = React.useState("");
  const [description, setDescription] = React.useState("");
  const [projectType, setProjectType] = React.useState("Web Application");
  const [directory, setDirectory] = React.useState("");
  const [storagePreference, setStoragePreference] = React.useState<"LOCAL_FIRST" | "CLOUD" | "HYBRID">("LOCAL_FIRST");

  const [techInput, setTechInput] = React.useState("");
  const [techStack, setTechStack] = React.useState<string[]>(["Java 17", "Spring Boot", "React", "PostgreSQL"]);

  const [loading, setLoading] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);

  const handleAddTech = () => {
    const trimmed = techInput.trim();
    if (trimmed && !techStack.includes(trimmed)) {
      setTechStack([...techStack, trimmed]);
      setTechInput("");
    }
  };

  const handleRemoveTech = (tag: string) => {
    setTechStack(techStack.filter((t) => t !== tag));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) {
      setError("Project name is required.");
      return;
    }

    setLoading(true);
    setError(null);

    try {
      const project = await api.createProject({
        name: name.trim(),
        description: description.trim() || undefined,
        projectType,
        techStack,
        directory: directory.trim() || undefined,
        storagePreference,
      });

      navigate(`/projects/${project.id}`);
    } catch (err: any) {
      setError(err.message || "Failed to create project");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="max-w-2xl mx-auto py-6">
      <div className="flex items-center gap-3 mb-6">
        <Button
          variant="ghost"
          size="iconSm"
          onClick={() => navigate("/projects")}
          className="text-text-secondary hover:text-text-primary"
        >
          <ArrowLeft className="h-4 w-4" />
        </Button>
        <div>
          <h1 className="text-xl font-semibold text-text-primary">Create New Engineering Project</h1>
          <p className="text-xs text-text-secondary mt-0.5">
            Initializes isolated persistent memory, append-only repository, and provider catalogue.
          </p>
        </div>
      </div>

      <form onSubmit={handleSubmit} className="space-y-6 rounded-panel border border-border bg-canvas p-6 shadow-lift">
        {error && (
          <div className="rounded-control bg-danger-subtle p-3 text-xs text-danger border border-danger/20">
            {error}
          </div>
        )}

        {/* Project Name */}
        <div>
          <label className="block text-xs font-medium text-text-primary mb-1.5">
            Project Name <span className="text-danger">*</span>
          </label>
          <Input
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="e.g. Aether Engineering Platform"
            required
            autoFocus
          />
        </div>

        {/* Description */}
        <div>
          <label className="block text-xs font-medium text-text-primary mb-1.5">
            Description
          </label>
          <textarea
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="Summary of project purpose and architecture scope…"
            rows={3}
            className="w-full rounded-control border border-border bg-canvas px-3 py-2 text-sm text-text-primary placeholder:text-text-tertiary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:border-transparent transition-colors"
          />
        </div>

        {/* Project Type */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div>
            <label className="block text-xs font-medium text-text-primary mb-1.5">
              Project Type <span className="text-danger">*</span>
            </label>
            <select
              value={projectType}
              onChange={(e) => setProjectType(e.target.value)}
              className="h-8 w-full rounded-control border border-border bg-canvas px-3 text-sm text-text-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent"
            >
              <option value="Web Application">Web Application (Full Stack)</option>
              <option value="Modular Monolith">Modular Monolith Backend</option>
              <option value="Microservices">Microservices Suite</option>
              <option value="CLI Tool">CLI / Developer Tool</option>
              <option value="Data & AI Pipeline">Data & AI Pipeline</option>
              <option value="Library / SDK">Library / SDK</option>
            </select>
          </div>

          {/* Storage Preference */}
          <div>
            <label className="block text-xs font-medium text-text-primary mb-1.5">
              Storage Preference
            </label>
            <select
              value={storagePreference}
              onChange={(e) => setStoragePreference(e.target.value as any)}
              className="h-8 w-full rounded-control border border-border bg-canvas px-3 text-sm text-text-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent"
            >
              <option value="LOCAL_FIRST">Local-First (Disk + PostgreSQL)</option>
              <option value="CLOUD">Cloud Workspace</option>
              <option value="HYBRID">Hybrid (Local cache + Cloud backup)</option>
            </select>
          </div>
        </div>

        {/* Tech Stack Tags */}
        <div>
          <label className="block text-xs font-medium text-text-primary mb-1.5">
            Technology Stack
          </label>
          <div className="flex flex-wrap gap-1.5 mb-2">
            {techStack.map((tech) => (
              <span
                key={tech}
                className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium bg-subtle text-text-secondary border border-border"
              >
                {tech}
                <button
                  type="button"
                  onClick={() => handleRemoveTech(tech)}
                  className="text-text-tertiary hover:text-danger ml-0.5"
                >
                  <X className="h-3 w-3" />
                </button>
              </span>
            ))}
          </div>
          <div className="flex gap-2">
            <Input
              value={techInput}
              onChange={(e) => setTechInput(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter") {
                  e.preventDefault();
                  handleAddTech();
                }
              }}
              placeholder="Add technology (e.g. Docker, TypeScript, pgvector) and press Enter"
            />
            <Button type="button" variant="secondary" onClick={handleAddTech} size="sm" className="h-8">
              <Plus className="h-3.5 w-3.5 mr-1" /> Add
            </Button>
          </div>
        </div>

        {/* Project Directory (Optional) */}
        <div>
          <label className="block text-xs font-medium text-text-primary mb-1.5">
            Project Directory <span className="text-text-tertiary">(Optional local workspace path)</span>
          </label>
          <Input
            value={directory}
            onChange={(e) => setDirectory(e.target.value)}
            placeholder="e.g. /workspace/my-project or C:\workspace\my-project"
          />
        </div>

        {/* Actions */}
        <div className="pt-4 border-t border-border flex items-center justify-between">
          <div className="flex items-center gap-1.5 text-xs text-text-tertiary">
            <ShieldCheck className="h-4 w-4 text-success" />
            <span>Postgres RLS isolation initialized upon creation</span>
          </div>
          <div className="flex items-center gap-2">
            <Button type="button" variant="outline" onClick={() => navigate("/projects")}>
              Cancel
            </Button>
            <Button type="submit" disabled={loading}>
              {loading ? "Creating Project…" : "Create Project"}
            </Button>
          </div>
        </div>
      </form>
    </div>
  );
}
