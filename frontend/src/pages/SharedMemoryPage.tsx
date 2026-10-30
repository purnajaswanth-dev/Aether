import React from "react";
import { useParams, useNavigate } from "react-router-dom";
import { api } from "@/services/api";
import { Artifact, TechnicalDecision } from "@/types";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Brain,
  Search,
  BookOpen,
  Database,
  Code2,
  Scale,
  CheckSquare,
  History,
  Shield,
  Layers,
  ArrowRight,
} from "lucide-react";

export function SharedMemoryPage() {
  const { projectId } = useParams<{ projectId: string }>();
  const navigate = useNavigate();

  const [artifacts, setArtifacts] = React.useState<Artifact[]>([]);
  const [decisions, setDecisions] = React.useState<TechnicalDecision[]>([]);
  const [loading, setLoading] = React.useState(true);
  const [search, setSearch] = React.useState("");

  React.useEffect(() => {
    if (!projectId) return;

    Promise.all([api.listArtifacts(projectId), api.listDecisions(projectId)])
      .then(([arts, decs]) => {
        setArtifacts(arts);
        setDecisions(decs);
      })
      .finally(() => setLoading(false));
  }, [projectId]);

  const categories = [
    { id: "Requirements", label: "Requirements", icon: BookOpen, type: "REQUIREMENTS" },
    { id: "Architecture", label: "Architecture", icon: Layers, type: "ARCHITECTURE" },
    { id: "Database", label: "Database", icon: Database, category: "DATABASE" },
    { id: "API", label: "API Contracts", icon: Code2, category: "API" },
    { id: "Decisions", label: "Decisions", icon: Scale, isDecisions: true },
    { id: "Conventions", label: "Conventions", icon: Shield, category: "CONVENTION" },
    { id: "Implementation", label: "Implementation", icon: Code2, type: "CODE" },
    { id: "Testing", label: "Testing", icon: CheckSquare, category: "TESTING" },
    { id: "Deployment", label: "Deployment", icon: Layers, category: "INFRASTRUCTURE" },
    { id: "History", label: "History", icon: History, isTimeline: true },
  ];

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h1 className="text-xl font-semibold text-text-primary tracking-tight">Shared Project Memory</h1>
          <p className="text-xs text-text-secondary mt-0.5">
            Structured knowledge namespace organized across the 10 constitutional domains.
          </p>
        </div>

        <div className="w-full md:w-72 relative">
          <Search className="absolute left-2.5 top-2.5 h-3.5 w-3.5 text-text-tertiary" />
          <Input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search memory namespace…"
            className="pl-8 h-8 text-xs bg-canvas"
          />
        </div>
      </div>

      {loading ? (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {[1, 2, 3, 4, 5, 6].map((i) => (
            <Skeleton key={i} className="h-32 rounded-panel" />
          ))}
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {categories.map((cat) => {
            const Icon = cat.icon;

            // Compute items in this category
            const matchingArtifacts = artifacts.filter(
              (a) => cat.type && a.artifactType.toUpperCase() === cat.type
            );
            const matchingDecisions = decisions.filter(
              (d) => cat.category && d.category.toUpperCase() === cat.category
            );

            let count = matchingArtifacts.length + matchingDecisions.length;
            if (cat.isDecisions) count = decisions.length;
            if (cat.isTimeline) count = 1; // indicates timeline link

            return (
              <div
                key={cat.id}
                onClick={() => {
                  if (cat.isDecisions) navigate(`/projects/${projectId}/decisions`);
                  else if (cat.isTimeline) navigate(`/projects/${projectId}/timeline`);
                  else navigate(`/projects/${projectId}/artifacts?type=${cat.type || ""}`);
                }}
                className="group flex flex-col justify-between rounded-panel border border-border bg-canvas p-4 shadow-lift hover:border-accent cursor-pointer transition-all duration-hover select-none"
              >
                <div>
                  <div className="flex items-center justify-between mb-2">
                    <div className="flex h-8 w-8 items-center justify-center rounded-control bg-subtle border border-border group-hover:border-accent group-hover:bg-accent-subtle transition-colors">
                      <Icon className="h-4 w-4 text-text-secondary group-hover:text-accent" />
                    </div>
                    <Badge variant="neutral" size="sm" showDot={false} className="font-mono text-[10px]">
                      {count} items
                    </Badge>
                  </div>

                  <h3 className="text-sm font-semibold text-text-primary group-hover:text-accent transition-colors">
                    {cat.label}
                  </h3>

                  <p className="mt-1 text-xs text-text-secondary line-clamp-2 leading-relaxed">
                    Persistent specifications, schemas, verified decisions, and structured context.
                  </p>
                </div>

                <div className="mt-4 pt-2 border-t border-border/40 flex items-center justify-between text-[11px] text-text-tertiary">
                  <span>Explore category</span>
                  <ArrowRight className="h-3.5 w-3.5 group-hover:translate-x-0.5 group-hover:text-accent transition-all" />
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

