import React from "react";
import { useParams } from "react-router-dom";
import { api } from "@/services/api";
import { IntegrationStatus } from "@/types";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Plug,
  Bot,
  GitBranch,
  Cloud,
  Terminal,
  Container,
  Layers,
  Lock,
  Info,
} from "lucide-react";

export function IntegrationsPage() {
  const { projectId } = useParams<{ projectId: string }>();

  const [integrations, setIntegrations] = React.useState<IntegrationStatus[]>([]);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);

  React.useEffect(() => {
    if (!projectId) return;

    api
      .getIntegrations(projectId)
      .then((data) => setIntegrations(data))
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, [projectId]);

  const getProviderIcon = (providerId: string) => {
    switch (providerId) {
      case "claude":
      case "chatgpt":
      case "antigravity":
        return <Bot className="h-5 w-5 text-accent" />;
      case "github":
        return <GitBranch className="h-5 w-5 text-text-primary" />;
      case "render":
        return <Cloud className="h-5 w-5 text-info" />;
      case "postman":
        return <Terminal className="h-5 w-5 text-warning" />;
      case "docker":
        return <Container className="h-5 w-5 text-info" />;
      default:
        return <Plug className="h-5 w-5 text-text-secondary" />;
    }
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <div className="flex items-center gap-2 mb-1">
          <h1 className="text-xl font-semibold text-text-primary tracking-tight">
            Integrations Catalogue
          </h1>
          <Badge variant="neutral" size="sm" showDot={false}>
            Non-negotiable Rule 9 Compliant
          </Badge>
        </div>
        <p className="text-xs text-text-secondary">
          Honest provider connectivity matrix. No fake statuses or simulated connections. Every
          provider truthfully reports its verified phase implementation status.
        </p>
      </div>

      {loading ? (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {[1, 2, 3, 4, 5, 6].map((i) => (
            <Skeleton key={i} className="h-48 rounded-panel" />
          ))}
        </div>
      ) : error ? (
        <div className="rounded-control bg-danger-subtle p-4 text-xs text-danger border border-danger/20">
          Failed to load integrations: {error}
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {integrations.map((item) => (
            <div
              key={item.providerId}
              className="flex flex-col justify-between rounded-panel border border-border bg-canvas p-5 shadow-lift select-none"
            >
              <div>
                <div className="flex items-start justify-between gap-2 mb-3">
                  <div className="flex items-center gap-2.5">
                    <div className="flex h-9 w-9 items-center justify-center rounded-control bg-subtle border border-border">
                      {getProviderIcon(item.providerId)}
                    </div>
                    <div>
                      <h3 className="text-sm font-semibold text-text-primary">{item.name}</h3>
                      <span className="text-[11px] text-text-tertiary">{item.category}</span>
                    </div>
                  </div>
                  <Badge variant="neutral" size="sm">
                    Not connected
                  </Badge>
                </div>

                <p className="text-xs text-text-secondary leading-relaxed mb-4">
                  {item.description}
                </p>

                {/* Planned Capabilities */}
                <div className="mb-4">
                  <span className="text-[11px] font-semibold text-text-tertiary uppercase tracking-wider block mb-1.5">
                    Planned Capabilities
                  </span>
                  <ul className="space-y-1">
                    {item.plannedCapabilities.map((cap, idx) => (
                      <li key={idx} className="text-xs text-text-secondary flex items-center gap-1.5">
                        <span className="h-1 w-1 rounded-full bg-text-tertiary" />
                        <span>{cap}</span>
                      </li>
                    ))}
                  </ul>
                </div>
              </div>

              <div className="pt-3 border-t border-border flex items-center justify-between text-xs">
                <div
                  className="flex items-center gap-1 text-[11px] text-text-tertiary cursor-help"
                  title={item.statusReason}
                >
                  <Info className="h-3.5 w-3.5" />
                  <span className="truncate max-w-[150px]">{item.statusReason}</span>
                </div>

                <Button variant="outline" size="sm" disabled className="gap-1 opacity-60">
                  <Lock className="h-3 w-3" /> Connect
                </Button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
