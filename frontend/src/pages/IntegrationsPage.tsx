import React from "react";
import { useParams, Link } from "react-router-dom";
import { api } from "@/services/api";
import {
  McpToken,
  McpActivityLog,
  McpReliabilityReport,
  McpClientPlatform,
  McpClientConfig,
} from "@/types";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/tabs";
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
  Plug,
  Bot,
  GitBranch,
  Cloud,
  Terminal,
  Container,
  Lock,
  Info,
  CheckCircle2,
  Copy,
  Check,
  Pause,
  Play,
  Trash2,
  ShieldAlert,
  ArrowRight,
  RefreshCw,
  Activity,
  Layers,
  Sparkles,
  ExternalLink,
} from "lucide-react";

interface ProviderDef {
  id: string;
  name: string;
  platform: McpClientPlatform | null;
  category: string;
  description: string;
  isMcpSupported: boolean;
  transportInfo: string;
  plannedCapabilities: string[];
}

const PROVIDERS: ProviderDef[] = [
  {
    id: "antigravity",
    name: "Google Antigravity",
    platform: "ANTIGRAVITY",
    category: "AI Agent",
    description: "AI-first development environment with native Streamable HTTP MCP support and workspace rules.",
    isMcpSupported: true,
    transportInfo: "Streamable HTTP (POST /api/mcp)",
    plannedCapabilities: ["Persistent memory retrieval", "Workspace rules (.agents/rules/aether.md)", "Artifact creation & ADR proposal"],
  },
  {
    id: "claude",
    name: "Anthropic Claude Desktop",
    platform: "CLAUDE",
    category: "AI Assistant",
    description: "Desktop assistant connected via mcp-remote stdio bridge with scoped Bearer authentication.",
    isMcpSupported: true,
    transportInfo: "Stdio via mcp-remote bridge",
    plannedCapabilities: ["Architecture review & ADR proposals", "Full artifact retrieval with SHA-256 provenance", "Review findings recording"],
  },
  {
    id: "chatgpt",
    name: "OpenAI ChatGPT",
    platform: "CHATGPT",
    category: "AI Assistant",
    description: "Custom MCP connector / Actions integration for ChatGPT Plus/Team/Enterprise workspaces.",
    isMcpSupported: true,
    transportInfo: "Remote HTTPS (Streamable HTTP / SSE)",
    plannedCapabilities: ["Project overview & context injection", "Memory full-text search", "Conversation transcript export"],
  },
  {
    id: "other_mcp",
    name: "Cursor / Windsurf / Custom MCP",
    platform: "OTHER",
    category: "Developer Tool",
    description: "Any external IDE or agent supporting Model Context Protocol via Streamable HTTP or legacy SSE.",
    isMcpSupported: true,
    transportInfo: "Streamable HTTP & SSE (Dual transport)",
    plannedCapabilities: ["Full 13 MCP engineering tools", "Resources & Prompts support", "Per-token rate limiting"],
  },
  {
    id: "github",
    name: "GitHub",
    platform: null,
    category: "Version Control",
    description: "Bi-directional commit linkage, PR memory capture, and repository webhook ingestion.",
    isMcpSupported: false,
    transportInfo: "REST API & Webhooks (Phase 4)",
    plannedCapabilities: ["PR summary sync", "Commit SHA provenance", "Repository webhook receiver"],
  },
  {
    id: "render",
    name: "Render",
    platform: null,
    category: "Cloud Deployment",
    description: "Continuous deployment log capture, service health monitoring, and release verification.",
    isMcpSupported: false,
    transportInfo: "Deploy Hooks & Telemetry (Phase 4)",
    plannedCapabilities: ["Deployment status tracking", "Build log ingestion", "Verification evidence capture"],
  },
  {
    id: "postman",
    name: "Postman",
    platform: null,
    category: "API Testing",
    description: "API collection synchronization, contract verification test results, and OpenAPI spec diffs.",
    isMcpSupported: false,
    transportInfo: "Collection Webhooks (Phase 5)",
    plannedCapabilities: ["API contract verification", "Collection test result recording", "Endpoint schema capture"],
  },
  {
    id: "docker",
    name: "Docker",
    platform: null,
    category: "Containerization",
    description: "Container build verification, runtime configuration validation, and image digest hashes.",
    isMcpSupported: false,
    transportInfo: "Daemon API (Phase 5)",
    plannedCapabilities: ["Dockerfile validation", "Image digest integrity", "Runtime environment verification"],
  },
];

const ALL_CONSTITUTIONAL_PERMISSIONS = [
  { key: "READ_PROJECT_CONTEXT", label: "Read Project Context", desc: "Allows inspecting overview, tech stack, and 10 memory domains" },
  { key: "READ_ARTIFACTS", label: "Read Artifacts", desc: "Allows downloading original raw specifications and code artifacts" },
  { key: "SAVE_ARTIFACTS", label: "Save Artifacts", desc: "Allows appending immutable versions and review findings" },
  { key: "RECORD_DECISIONS", label: "Record Decisions", desc: "Allows proposing new Architectural Decision Records (status: PROPOSED)" },
  { key: "READ_DEPLOYMENT_DATA", label: "Read Deployment Data", desc: "Allows reading environment configs and verified release hashes" },
  { key: "EXECUTE_APPROVED_ACTIONS", label: "Execute Approved Actions", desc: "Allows triggering actions explicitly approved by human reviewers" },
];

export function IntegrationsPage() {
  const { projectId } = useParams<{ projectId: string }>();

  // State
  const [tokens, setTokens] = React.useState<McpToken[]>([]);
  const [activityLogs, setActivityLogs] = React.useState<McpActivityLog[]>([]);
  const [reliability, setReliability] = React.useState<McpReliabilityReport | null>(null);
  const [loading, setLoading] = React.useState(true);
  const [activeTab, setActiveTab] = React.useState("catalog");

  // Filters for Activity Log
  const [statusFilter, setStatusFilter] = React.useState("ALL");
  const [toolFilter, setToolFilter] = React.useState("");

  // Connect Stepper Modal State
  const [isConnectOpen, setIsConnectOpen] = React.useState(false);
  const [selectedProvider, setSelectedProvider] = React.useState<ProviderDef | null>(null);
  const [connectStep, setConnectStep] = React.useState<1 | 2 | 3>(1);
  const [tokenName, setTokenName] = React.useState("");
  const [selectedPermissions, setSelectedPermissions] = React.useState<string[]>([
    "READ_PROJECT_CONTEXT",
    "READ_ARTIFACTS",
    "SAVE_ARTIFACTS",
    "RECORD_DECISIONS",
  ]);
  const [rateLimit, setRateLimit] = React.useState(60);
  const [generatedSecretToken, setGeneratedSecretToken] = React.useState<string | null>(null);
  const [clientConfig, setClientConfig] = React.useState<McpClientConfig | null>(null);
  const [copiedToken, setCopiedToken] = React.useState(false);
  const [copiedConfig, setCopiedConfig] = React.useState(false);
  const [copiedRules, setCopiedRules] = React.useState(false);

  const loadData = React.useCallback(async () => {
    if (!projectId) return;
    try {
      const [tokenList, logs, metrics] = await Promise.all([
        api.listMcpTokens(projectId),
        api.getMcpActivity(projectId, { limit: 50, status: statusFilter !== "ALL" ? statusFilter : undefined, tool: toolFilter || undefined }),
        api.getMcpReliability(projectId),
      ]);
      setTokens(tokenList);
      setActivityLogs(logs);
      setReliability(metrics);
    } catch (err) {
      console.error("Failed to load MCP integration data", err);
    } finally {
      setLoading(false);
    }
  }, [projectId, statusFilter, toolFilter]);

  React.useEffect(() => {
    loadData();
  }, [loadData]);

  // Derive honest status for each provider
  const getDerivedStatus = (provider: ProviderDef) => {
    if (!provider.isMcpSupported || !provider.platform) {
      return { label: "Not available yet", variant: "neutral" as const, subtitle: "Planned for later phase" };
    }

    const platformTokens = tokens.filter((t) => t.clientPlatform === provider.platform && !t.isRevoked);
    if (platformTokens.length === 0) {
      return { label: "Not connected", variant: "neutral" as const, subtitle: "No active MCP token configured" };
    }

    const hasPaused = platformTokens.some((t) => t.isPaused);
    if (hasPaused && platformTokens.every((t) => t.isPaused)) {
      return { label: "Paused", variant: "warning" as const, subtitle: "Connection paused by project admin" };
    }

    const lastUsedToken = platformTokens
      .filter((t) => t.lastUsedAt)
      .sort((a, b) => new Date(b.lastUsedAt!).getTime() - new Date(a.lastUsedAt!).getTime())[0];

    if (!lastUsedToken || !lastUsedToken.lastUsedAt) {
      return { label: "Awaiting first call", variant: "info" as const, subtitle: "Token created; waiting for client handshake" };
    }

    const diffMinutes = Math.round((Date.now() - new Date(lastUsedToken.lastUsedAt).getTime()) / 60000);
    const timeAgoStr = diffMinutes < 1 ? "just now" : diffMinutes < 60 ? `${diffMinutes}m ago` : `${Math.round(diffMinutes / 60)}h ago`;

    return { label: `Connected (${timeAgoStr})`, variant: "success" as const, subtitle: `Active token: ${lastUsedToken.tokenPrefix}` };
  };

  const handleOpenConnect = (provider: ProviderDef) => {
    setSelectedProvider(provider);
    setConnectStep(1);
    setTokenName(`${provider.name} Connection`);
    setSelectedPermissions(["READ_PROJECT_CONTEXT", "READ_ARTIFACTS", "SAVE_ARTIFACTS", "RECORD_DECISIONS"]);
    setRateLimit(60);
    setGeneratedSecretToken(null);
    setClientConfig(null);
    setIsConnectOpen(true);
  };

  const handleGenerateToken = async () => {
    if (!projectId || !selectedProvider?.platform) return;

    try {
      const resp = await api.createMcpToken(projectId, {
        tokenName,
        clientPlatform: selectedProvider.platform,
        permissions: selectedPermissions,
        rateLimitPerMinute: rateLimit,
      });

      setGeneratedSecretToken(resp.rawToken);

      // Fetch client specific configuration with the generated token
      const config = await api.getMcpConfig(projectId, selectedProvider.platform, window.location.origin, resp.rawToken);
      setClientConfig(config);

      setConnectStep(2);
      loadData();
    } catch (err) {
      alert("Failed to generate token: " + (err instanceof Error ? err.message : String(err)));
    }
  };

  const handleTogglePause = async (token: McpToken) => {
    if (!projectId) return;
    try {
      await api.pauseMcpToken(projectId, token.id, !token.isPaused);
      loadData();
    } catch (err) {
      alert("Failed to update token state: " + err);
    }
  };

  const handleRevokeToken = async (tokenId: string) => {
    if (!projectId) return;
    if (!confirm("Are you sure you want to revoke this MCP token? Clients using it will be blocked immediately.")) return;

    try {
      await api.revokeMcpToken(projectId, tokenId);
      loadData();
    } catch (err) {
      alert("Failed to revoke token: " + err);
    }
  };

  const copyToClipboard = (text: string, setter: (val: boolean) => void) => {
    navigator.clipboard.writeText(text);
    setter(true);
    setTimeout(() => setter(false), 2000);
  };

  return (
    <div className="space-y-6">
      {/* Top Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
        <div>
          <div className="flex items-center gap-2 mb-1">
            <h1 className="text-xl font-semibold text-text-primary tracking-tight">
              AI & Engineering Integrations
            </h1>
            <Badge variant="neutral" size="sm" showDot={false}>
              MCP Server Protocol 2025-03-26 & 2024-11-05
            </Badge>
          </div>
          <p className="text-xs text-text-secondary">
            Expose persistent project memory to independent AI agents (Claude, Antigravity, ChatGPT, Cursor) through remote Model Context Protocol.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <Button variant="outline" size="sm" onClick={loadData} className="gap-1.5 text-xs">
            <RefreshCw className="h-3.5 w-3.5" /> Refresh
          </Button>
        </div>
      </div>

      {/* Manual Import Fallback Notice (Non-negotiable Rule 9 & Honest Fallback) */}
      <div className="flex items-start gap-3 rounded-panel border border-accent/20 bg-accent-subtle/50 p-4">
        <Info className="h-4 w-4 text-accent shrink-0 mt-0.5" />
        <div className="text-xs text-text-primary space-y-1">
          <span className="font-semibold block">Independent AI Client Fallback Guarantee</span>
          <p className="text-text-secondary leading-relaxed">
            If your target AI assistant does not support direct remote MCP connections, you can always use the first-class{" "}
            <Link to={`/projects/${projectId}/artifacts`} className="text-accent underline font-medium hover:text-accent-hover">
              Save to Project
            </Link>{" "}
            import flow to persist specifications, conversation transcripts, and code architectures with cryptographic SHA-256 verification.
          </p>
        </div>
      </div>

      {/* Main Tabs */}
      <Tabs value={activeTab} onValueChange={setActiveTab}>
        <TabsList>
          <TabsTrigger value="catalog">Catalogue & Connectors</TabsTrigger>
          <TabsTrigger value="tokens" className="gap-1.5">
            Active MCP Connections
            {tokens.filter((t) => !t.isRevoked).length > 0 && (
              <span className="flex h-4 w-4 items-center justify-center rounded-full bg-accent text-[10px] text-canvas font-semibold">
                {tokens.filter((t) => !t.isRevoked).length}
              </span>
            )}
          </TabsTrigger>
          <TabsTrigger value="activity">MCP Activity & Telemetry</TabsTrigger>
        </TabsList>

        {/* TAB 1: CATALOGUE & CONNECTORS */}
        <TabsContent value="catalog" className="space-y-4">
          {loading ? (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
              {[1, 2, 3, 4].map((i) => (
                <Skeleton key={i} className="h-56 rounded-panel" />
              ))}
            </div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
              {PROVIDERS.map((item) => {
                const status = getDerivedStatus(item);
                return (
                  <div
                    key={item.id}
                    className="flex flex-col justify-between rounded-panel border border-border bg-canvas p-4 shadow-lift"
                  >
                    <div>
                      <div className="flex items-start justify-between gap-2 mb-3">
                        <div className="flex items-center gap-2">
                          <div className="flex h-8 w-8 items-center justify-center rounded-control bg-subtle border border-border">
                            {item.platform ? <Bot className="h-4 w-4 text-accent" /> : <Layers className="h-4 w-4 text-text-tertiary" />}
                          </div>
                          <div>
                            <h3 className="text-xs font-semibold text-text-primary">{item.name}</h3>
                            <span className="text-[10px] text-text-tertiary block">{item.category}</span>
                          </div>
                        </div>
                      </div>

                      <div className="mb-2">
                        <Badge variant={status.variant} size="sm">
                          {status.label}
                        </Badge>
                      </div>

                      <p className="text-[11px] text-text-secondary leading-relaxed mb-3">
                        {item.description}
                      </p>

                      <div className="rounded-control bg-subtle/80 p-2 border border-border/60 text-[10px] text-text-secondary mb-3">
                        <span className="font-semibold text-text-primary block mb-0.5">Transport:</span>
                        {item.transportInfo}
                      </div>

                      <div className="space-y-1 mb-3">
                        <span className="text-[10px] font-semibold text-text-tertiary uppercase tracking-wider block">
                          Capabilities
                        </span>
                        {item.plannedCapabilities.slice(0, 2).map((cap, idx) => (
                          <div key={idx} className="text-[10px] text-text-secondary flex items-center gap-1.5">
                            <span className="h-1 w-1 rounded-full bg-text-tertiary shrink-0" />
                            <span className="truncate">{cap}</span>
                          </div>
                        ))}
                      </div>
                    </div>

                    <div className="pt-3 border-t border-border flex items-center justify-between text-xs mt-2">
                      <span className="text-[10px] text-text-tertiary truncate max-w-[120px]" title={status.subtitle}>
                        {status.subtitle}
                      </span>

                      {item.isMcpSupported ? (
                        <Button
                          variant="outline"
                          size="sm"
                          className="gap-1 text-xs font-medium"
                          onClick={() => handleOpenConnect(item)}
                        >
                          <Sparkles className="h-3 w-3 text-accent" /> Connect
                        </Button>
                      ) : (
                        <Button variant="outline" size="sm" disabled className="gap-1 opacity-60 text-xs">
                          <Lock className="h-3 w-3" /> Connect
                        </Button>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </TabsContent>

        {/* TAB 2: ACTIVE MCP CONNECTIONS */}
        <TabsContent value="tokens" className="space-y-4">
          <div className="flex items-center justify-between">
            <h2 className="text-sm font-semibold text-text-primary">
              Project Scoped MCP Connection Tokens
            </h2>
            <Button
              variant="outline"
              size="sm"
              className="gap-1.5 text-xs"
              onClick={() => handleOpenConnect(PROVIDERS[0])}
            >
              <Plug className="h-3.5 w-3.5" /> Create New MCP Connection
            </Button>
          </div>

          {tokens.filter((t) => !t.isRevoked).length === 0 ? (
            <EmptyState
              icon={Plug}
              title="No active MCP connections"
              description="Connect Claude Desktop, Antigravity, or ChatGPT to enable two-way context exchange with project memory."
              actionLabel="Connect First Agent"
              onAction={() => handleOpenConnect(PROVIDERS[0])}
            />
          ) : (
            <div className="rounded-panel border border-border bg-canvas overflow-hidden shadow-lift">
              <table className="w-full text-left border-collapse text-xs">
                <thead>
                  <tr className="border-b border-border bg-subtle text-text-secondary font-medium h-9">
                    <th className="px-4 py-2">Connection Name</th>
                    <th className="px-4 py-2">Client Platform</th>
                    <th className="px-4 py-2">Masked Token</th>
                    <th className="px-4 py-2">Permissions</th>
                    <th className="px-4 py-2">Rate Limit</th>
                    <th className="px-4 py-2">Last Used</th>
                    <th className="px-4 py-2">Status</th>
                    <th className="px-4 py-2 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border">
                  {tokens
                    .filter((t) => !t.isRevoked)
                    .map((token) => (
                      <tr key={token.id} className="h-10 hover:bg-subtle/50 transition-colors">
                        <td className="px-4 py-2 font-medium text-text-primary">{token.tokenName}</td>
                        <td className="px-4 py-2">
                          <Badge variant="neutral" size="sm" showDot={false}>
                            {token.clientPlatform}
                          </Badge>
                        </td>
                        <td className="px-4 py-2 font-mono text-[11px] text-text-secondary">
                          {token.tokenPrefix}
                        </td>
                        <td className="px-4 py-2">
                          <span className="text-[11px] text-text-tertiary">
                            {token.permissions.length} granted
                          </span>
                        </td>
                        <td className="px-4 py-2 text-text-secondary">
                          {token.rateLimitPerMinute}/min
                        </td>
                        <td className="px-4 py-2 text-text-tertiary text-[11px]">
                          {token.lastUsedAt ? new Date(token.lastUsedAt).toLocaleString() : "Never"}
                        </td>
                        <td className="px-4 py-2">
                          <Badge variant={token.isPaused ? "warning" : "success"} size="sm">
                            {token.isPaused ? "Paused" : "Active"}
                          </Badge>
                        </td>
                        <td className="px-4 py-2 text-right space-x-1">
                          <Button
                            variant="ghost"
                            size="sm"
                            className="h-7 px-2 text-xs"
                            onClick={() => handleTogglePause(token)}
                            title={token.isPaused ? "Resume connection" : "Pause connection"}
                          >
                            {token.isPaused ? <Play className="h-3.5 w-3.5 text-success" /> : <Pause className="h-3.5 w-3.5 text-warning" />}
                          </Button>
                          <Button
                            variant="ghost"
                            size="sm"
                            className="h-7 px-2 text-xs text-danger hover:bg-danger-subtle hover:text-danger"
                            onClick={() => handleRevokeToken(token.id)}
                            title="Revoke immediately"
                          >
                            <Trash2 className="h-3.5 w-3.5" />
                          </Button>
                        </td>
                      </tr>
                    ))}
                </tbody>
              </table>
            </div>
          )}
        </TabsContent>

        {/* TAB 3: MCP ACTIVITY & TELEMETRY */}
        <TabsContent value="activity" className="space-y-6">
          {/* Reliability Metric Cards (Item 8) */}
          {reliability && (
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
              <div className="rounded-panel border border-border bg-canvas p-4 shadow-lift">
                <span className="text-[11px] font-medium text-text-tertiary uppercase tracking-wider block mb-1">
                  Total MCP Calls
                </span>
                <div className="flex items-baseline justify-between">
                  <span className="text-2xl font-semibold text-text-primary">{reliability.totalCalls}</span>
                  <span className="text-xs text-success font-medium">{reliability.successfulCalls} success</span>
                </div>
              </div>

              <div className="rounded-panel border border-border bg-canvas p-4 shadow-lift">
                <span className="text-[11px] font-medium text-text-tertiary uppercase tracking-wider block mb-1">
                  Retrieve-Before-Save Rate
                </span>
                <div className="flex items-baseline justify-between">
                  <span className="text-2xl font-semibold text-accent">{reliability.firstRetrievalBeforeSaveRate}%</span>
                  <span className="text-[11px] text-text-tertiary">Disciplined sessions</span>
                </div>
                <p className="text-[10px] text-text-tertiary mt-1">Sessions reading context prior to first save</p>
              </div>

              <div className="rounded-panel border border-border bg-canvas p-4 shadow-lift">
                <span className="text-[11px] font-medium text-text-tertiary uppercase tracking-wider block mb-1">
                  Save Completion Rate
                </span>
                <div className="flex items-baseline justify-between">
                  <span className="text-2xl font-semibold text-success">{reliability.saveCompletionRate}%</span>
                  <span className="text-[11px] text-text-tertiary">Persistent outputs</span>
                </div>
                <p className="text-[10px] text-text-tertiary mt-1">Sessions persisting code/spec evidence</p>
              </div>

              <div className="rounded-panel border border-border bg-canvas p-4 shadow-lift">
                <span className="text-[11px] font-medium text-text-tertiary uppercase tracking-wider block mb-1">
                  Active Client Sessions
                </span>
                <div className="flex items-baseline justify-between">
                  <span className="text-2xl font-semibold text-text-primary">{reliability.sessionsTracked}</span>
                  <span className="text-[11px] text-text-tertiary">Tracked tokens</span>
                </div>
              </div>
            </div>
          )}

          {/* Activity Log Filter Bar */}
          <div className="flex flex-col sm:flex-row items-center justify-between gap-3 bg-subtle p-3 rounded-control border border-border">
            <div className="flex items-center gap-2 w-full sm:w-auto">
              <span className="text-xs text-text-secondary font-medium">Status:</span>
              <select
                value={statusFilter}
                onChange={(e) => setStatusFilter(e.target.value)}
                className="h-8 rounded-control border border-border bg-canvas px-2 text-xs text-text-primary focus:outline-none focus:ring-1 focus:ring-accent"
              >
                <option value="ALL">All Statuses</option>
                <option value="SUCCESS">SUCCESS</option>
                <option value="ERROR">ERROR</option>
                <option value="RATE_LIMITED">RATE_LIMITED</option>
                <option value="UNAUTHORIZED">UNAUTHORIZED</option>
              </select>
            </div>

            <div className="w-full sm:w-64">
              <Input
                placeholder="Filter by tool name..."
                value={toolFilter}
                onChange={(e) => setToolFilter(e.target.value)}
                className="h-8 text-xs"
              />
            </div>
          </div>

          {/* Activity Log Table */}
          {activityLogs.length === 0 ? (
            <EmptyState
              icon={Activity}
              title="No MCP calls recorded yet"
              description="Calls made by Claude, Antigravity, or ChatGPT via Streamable HTTP or SSE will appear here with millisecond latency and audit attribution."
            />
          ) : (
            <div className="rounded-panel border border-border bg-canvas overflow-hidden shadow-lift">
              <table className="w-full text-left border-collapse text-xs">
                <thead>
                  <tr className="border-b border-border bg-subtle text-text-secondary font-medium h-9">
                    <th className="px-4 py-2">Tool / Method</th>
                    <th className="px-4 py-2">Client Platform</th>
                    <th className="px-4 py-2">Latency</th>
                    <th className="px-4 py-2">Status</th>
                    <th className="px-4 py-2">Details / Error</th>
                    <th className="px-4 py-2 text-right">Timestamp</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border">
                  {activityLogs.map((log) => (
                    <tr key={log.id} className="h-9 hover:bg-subtle/50 transition-colors">
                      <td className="px-4 py-2 font-mono text-[11px] font-semibold text-text-primary">
                        {log.toolName}
                      </td>
                      <td className="px-4 py-2">
                        <Badge variant="neutral" size="sm" showDot={false}>
                          {log.clientPlatform}
                        </Badge>
                      </td>
                      <td className="px-4 py-2 font-mono text-[11px] text-text-secondary">
                        {log.durationMs} ms
                      </td>
                      <td className="px-4 py-2">
                        <Badge
                          variant={
                            log.status === "SUCCESS"
                              ? "success"
                              : log.status === "RATE_LIMITED"
                              ? "warning"
                              : "danger"
                          }
                          size="sm"
                        >
                          {log.status}
                        </Badge>
                      </td>
                      <td className="px-4 py-2 text-text-tertiary text-[11px] max-w-xs truncate" title={log.errorMessage || ""}>
                        {log.errorMessage ? log.errorMessage : "—"}
                      </td>
                      <td className="px-4 py-2 text-right text-text-tertiary text-[11px] whitespace-nowrap">
                        {new Date(log.createdAt).toLocaleTimeString()}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </TabsContent>
      </Tabs>

      {/* CONNECT STEPPER DIALOG */}
      <Dialog open={isConnectOpen} onOpenChange={setIsConnectOpen}>
        <DialogContent className="max-w-xl">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2 text-sm font-semibold">
              <Bot className="h-4 w-4 text-accent" /> Connect {selectedProvider?.name} (Step {connectStep} of 3)
            </DialogTitle>
            <DialogDescription className="text-xs text-text-secondary">
              {connectStep === 1 && "Configure connection identity, permissions, and rate limits for this AI assistant."}
              {connectStep === 2 && "Copy your single-use Bearer token. For security, it cannot be recovered after this dialog closes."}
              {connectStep === 3 && "Apply the configuration snippet and workspace rules into your client's environment."}
            </DialogDescription>
          </DialogHeader>

          {/* STEP 1: PERMISSIONS & RATE LIMITS */}
          {connectStep === 1 && (
            <div className="space-y-4 py-2">
              <div>
                <label className="text-xs font-medium text-text-secondary block mb-1">
                  Connection Name
                </label>
                <Input
                  value={tokenName}
                  onChange={(e) => setTokenName(e.target.value)}
                  placeholder="e.g. Claude Desktop Workstation"
                  className="text-xs"
                />
              </div>

              <div>
                <span className="text-xs font-medium text-text-secondary block mb-2">
                  Granted Constitutional Permissions
                </span>
                <div className="space-y-2 rounded-panel border border-border bg-subtle/50 p-3 max-h-48 overflow-y-auto">
                  {ALL_CONSTITUTIONAL_PERMISSIONS.map((perm) => {
                    const isChecked = selectedPermissions.includes(perm.key);
                    return (
                      <label key={perm.key} className="flex items-start gap-2.5 cursor-pointer text-xs">
                        <input
                          type="checkbox"
                          checked={isChecked}
                          onChange={(e) => {
                            if (e.target.checked) {
                              setSelectedPermissions([...selectedPermissions, perm.key]);
                            } else {
                              setSelectedPermissions(selectedPermissions.filter((p) => p !== perm.key));
                            }
                          }}
                          className="mt-0.5 rounded-control text-accent focus:ring-accent"
                        />
                        <div>
                          <span className="font-semibold text-text-primary block">{perm.label}</span>
                          <span className="text-[11px] text-text-tertiary">{perm.desc}</span>
                        </div>
                      </label>
                    );
                  })}
                </div>
              </div>

              <div className="flex items-center justify-between">
                <div>
                  <span className="text-xs font-medium text-text-secondary block">Rate Limit</span>
                  <span className="text-[11px] text-text-tertiary">Maximum tool calls per minute</span>
                </div>
                <Input
                  type="number"
                  value={rateLimit}
                  onChange={(e) => setRateLimit(Math.max(1, parseInt(e.target.value) || 60))}
                  className="w-24 text-xs h-8"
                />
              </div>

              <div className="flex justify-end gap-2 pt-3 border-t border-border">
                <Button variant="ghost" size="sm" onClick={() => setIsConnectOpen(false)}>
                  Cancel
                </Button>
                <Button variant="default" size="sm" onClick={handleGenerateToken} className="gap-1.5">
                  Generate Scoped Token <ArrowRight className="h-3.5 w-3.5" />
                </Button>
              </div>
            </div>
          )}

          {/* STEP 2: TOKEN GENERATION & ONE-TIME COPY */}
          {connectStep === 2 && generatedSecretToken && (
            <div className="space-y-4 py-2">
              <div className="rounded-panel border border-warning/30 bg-warning-subtle p-3 flex items-start gap-2.5">
                <ShieldAlert className="h-4 w-4 text-warning shrink-0 mt-0.5" />
                <div className="text-xs text-text-primary">
                  <span className="font-semibold block text-warning">Copy your Bearer Token now</span>
                  This secret token is stored only as a cryptographic SHA-256 hash in the database and will NEVER be shown again.
                </div>
              </div>

              <div>
                <label className="text-xs font-medium text-text-secondary block mb-1">
                  Bearer Token Secret
                </label>
                <div className="flex items-center gap-2">
                  <Input
                    readOnly
                    value={generatedSecretToken}
                    className="font-mono text-xs bg-canvas"
                  />
                  <Button
                    variant="outline"
                    size="sm"
                    className="gap-1 text-xs shrink-0"
                    onClick={() => copyToClipboard(generatedSecretToken, setCopiedToken)}
                  >
                    {copiedToken ? <Check className="h-3.5 w-3.5 text-success" /> : <Copy className="h-3.5 w-3.5" />}
                    {copiedToken ? "Copied" : "Copy"}
                  </Button>
                </div>
              </div>

              <div className="flex justify-end gap-2 pt-3 border-t border-border">
                <Button variant="default" size="sm" onClick={() => setConnectStep(3)} className="gap-1.5">
                  View Client Setup Instructions <ArrowRight className="h-3.5 w-3.5" />
                </Button>
              </div>
            </div>
          )}

          {/* STEP 3: CLIENT INSTRUCTIONS & RULES */}
          {connectStep === 3 && clientConfig && (
            <div className="space-y-4 py-2 max-h-[70vh] overflow-y-auto">
              <div>
                <div className="flex items-center justify-between mb-1">
                  <span className="text-xs font-semibold text-text-primary">
                    1. Configuration File ({clientConfig.config_file})
                  </span>
                  <Button
                    variant="ghost"
                    size="sm"
                    className="h-6 px-2 text-[11px] gap-1"
                    onClick={() => copyToClipboard(clientConfig.config_snippet, setCopiedConfig)}
                  >
                    {copiedConfig ? <Check className="h-3 w-3 text-success" /> : <Copy className="h-3 w-3" />}
                    {copiedConfig ? "Copied" : "Copy Config"}
                  </Button>
                </div>
                <CodeBlock code={clientConfig.config_snippet} language="json" />
              </div>

              <div>
                <div className="flex items-center justify-between mb-1">
                  <span className="text-xs font-semibold text-text-primary">
                    2. Recommended Rules ({clientConfig.rules_file})
                  </span>
                  <Button
                    variant="ghost"
                    size="sm"
                    className="h-6 px-2 text-[11px] gap-1"
                    onClick={() => copyToClipboard(clientConfig.rules_snippet, setCopiedRules)}
                  >
                    {copiedRules ? <Check className="h-3 w-3 text-success" /> : <Copy className="h-3 w-3" />}
                    {copiedRules ? "Copied" : "Copy Rules"}
                  </Button>
                </div>
                <CodeBlock code={clientConfig.rules_snippet} language="markdown" />
              </div>

              <div className="flex justify-end gap-2 pt-3 border-t border-border">
                <Button variant="default" size="sm" onClick={() => setIsConnectOpen(false)}>
                  Done
                </Button>
              </div>
            </div>
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}
