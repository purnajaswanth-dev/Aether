import React, { useState, useEffect, useCallback } from "react";
import { useParams, Link } from "react-router-dom";
import { api } from "@/services/api";
import {
  McpToken,
  McpActivityLog,
  McpReliabilityReport,
  McpClientPlatform,
  McpClientConfig,
  IntegrationSummary,
  CapabilityDescriptor,
  AgentDevice,
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
import { DeploymentsViewer } from "@/components/DeploymentsViewer";
import { LocalProjectWizard } from "@/components/LocalProjectWizard";
import { DeadLetterQueueDrawer } from "@/components/DeadLetterQueueDrawer";
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
  Laptop,
  AlertTriangle,
  Clock,
  ShieldCheck,
  FileCode,
} from "lucide-react";

interface AiProviderDef {
  id: string;
  name: string;
  platform: McpClientPlatform;
  category: string;
  description: string;
  transportInfo: string;
  plannedCapabilities: string[];
}

const AI_PROVIDERS: AiProviderDef[] = [
  {
    id: "antigravity",
    name: "Google Antigravity",
    platform: "ANTIGRAVITY",
    category: "AI Agent",
    description: "AI-first development environment with native Streamable HTTP MCP support and workspace rules.",
    transportInfo: "Streamable HTTP (POST /api/mcp)",
    plannedCapabilities: ["Persistent memory retrieval", "Workspace rules (.agents/rules/aether.md)", "Artifact creation & ADR proposal"],
  },
  {
    id: "claude",
    name: "Anthropic Claude Desktop",
    platform: "CLAUDE",
    category: "AI Assistant",
    description: "Desktop assistant connected via mcp-remote stdio bridge with scoped Bearer authentication.",
    transportInfo: "Stdio via mcp-remote bridge",
    plannedCapabilities: ["Architecture review & ADR proposals", "Full artifact retrieval with SHA-256 provenance", "Review findings recording"],
  },
  {
    id: "chatgpt",
    name: "OpenAI ChatGPT",
    platform: "CHATGPT",
    category: "AI Assistant",
    description: "Custom MCP connector / Actions integration for ChatGPT Plus/Team/Enterprise workspaces.",
    transportInfo: "Remote HTTPS (Streamable HTTP / SSE)",
    plannedCapabilities: ["Project overview & context injection", "Memory full-text search", "Conversation transcript export"],
  },
  {
    id: "other_mcp",
    name: "Cursor / Windsurf / Custom MCP",
    platform: "OTHER",
    category: "Developer Tool",
    description: "Any external IDE or agent supporting Model Context Protocol via Streamable HTTP or legacy SSE.",
    transportInfo: "Streamable HTTP & SSE (Dual transport)",
    plannedCapabilities: ["Full 20 MCP engineering tools", "Resources & Prompts support", "Per-token rate limiting"],
  },
];

const ALL_CONSTITUTIONAL_PERMISSIONS = [
  { key: "READ_PROJECT_CONTEXT", label: "Read Project Context", desc: "Allows inspecting overview, tech stack, and 10 memory domains" },
  { key: "READ_ARTIFACTS", label: "Read Artifacts", desc: "Allows downloading original raw specifications and code artifacts" },
  { key: "SAVE_ARTIFACTS", label: "Save Artifacts", desc: "Allows appending immutable versions and review findings" },
  { key: "RECORD_DECISIONS", label: "Record Decisions", desc: "Allows proposing new Architectural Decision Records (status: PROPOSED)" },
  { key: "READ_DEPLOYMENT_DATA", label: "Read Deployment Data", desc: "Allows reading environment configs, build logs, and release hashes" },
  { key: "EXECUTE_APPROVED_ACTIONS", label: "Execute Approved Actions", desc: "Allows triggering actions explicitly approved by human reviewers" },
];

export function IntegrationsPage() {
  const { projectId } = useParams<{ projectId: string }>();

  // Navigation tab
  const [activeTab, setActiveTab] = useState("platforms");

  // Phase 4 Engineering Integrations state
  const [capabilities, setCapabilities] = useState<CapabilityDescriptor[]>([]);
  const [integrations, setIntegrations] = useState<IntegrationSummary[]>([]);
  const [devices, setDevices] = useState<AgentDevice[]>([]);
  const [isWizardOpen, setIsWizardOpen] = useState(false);
  const [connectModalProvider, setConnectModalProvider] = useState<string | null>(null);
  const [configFields, setConfigFields] = useState<Record<string, string>>({});
  const [credentialSecret, setCredentialSecret] = useState<string>("");
  const [connecting, setConnecting] = useState(false);
  const [syncingProvider, setSyncingProvider] = useState<string | null>(null);

  // Phase 2 MCP state
  const [tokens, setTokens] = useState<McpToken[]>([]);
  const [logs, setLogs] = useState<McpActivityLog[]>([]);
  const [reliability, setReliability] = useState<McpReliabilityReport | null>(null);
  const [loading, setLoading] = useState(true);

  // Filters for MCP activity logs
  const [statusFilter, setStatusFilter] = useState<string>("ALL");
  const [toolFilter, setToolFilter] = useState<string>("ALL");

  // Token Creation Modal State
  const [selectedAiProvider, setSelectedAiProvider] = useState<AiProviderDef | null>(null);
  const [isAiConnectOpen, setIsAiConnectOpen] = useState(false);
  const [tokenName, setTokenName] = useState("");
  const [selectedPermissions, setSelectedPermissions] = useState<string[]>([
    "READ_PROJECT_CONTEXT",
    "READ_ARTIFACTS",
    "SAVE_ARTIFACTS",
    "RECORD_DECISIONS",
  ]);
  const [rateLimit, setRateLimit] = useState(60);
  const [generatedSecretToken, setGeneratedSecretToken] = useState<string | null>(null);
  const [clientConfig, setClientConfig] = useState<McpClientConfig | null>(null);
  const [aiConnectStep, setAiConnectStep] = useState<1 | 2>(1);
  const [copiedToken, setCopiedToken] = useState(false);
  const [copiedSnippet, setCopiedSnippet] = useState(false);

  const loadData = useCallback(async () => {
    if (!projectId) return;
    try {
      setLoading(true);
      const [caps, ints, devs, tokenList, reliabilityRep, logList] = await Promise.all([
        api.getIntegrationCapabilities(projectId).catch(() => []),
        api.listProjectIntegrations(projectId).catch(() => []),
        api.listAgentDevices(projectId).catch(() => []),
        api.listMcpTokens(projectId).catch(() => []),
        api.getMcpReliability(projectId).catch(() => null),
        api.getMcpActivity(projectId, {
          status: statusFilter === "ALL" ? undefined : statusFilter,
          tool: toolFilter === "ALL" ? undefined : toolFilter,
          limit: 100,
        }).catch(() => []),
      ]);

      setCapabilities(caps);
      setIntegrations(ints);
      setDevices(devs);
      setTokens(tokenList);
      setReliability(reliabilityRep);
      setLogs(logList);
    } catch (err) {
      console.error("Failed to load integrations", err);
    } finally {
      setLoading(false);
    }
  }, [projectId, statusFilter, toolFilter]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  // Handle Engineering Platform Connect
  const openPlatformConnect = (provider: string) => {
    setConnectModalProvider(provider);
    setConfigFields({});
    setCredentialSecret("");
  };

  const handleConnectPlatform = async () => {
    if (!projectId || !connectModalProvider) return;
    try {
      setConnecting(true);
      await api.connectIntegration(projectId, {
        provider: connectModalProvider,
        config: configFields,
        credential: credentialSecret || undefined,
      });
      setConnectModalProvider(null);
      await loadData();
    } catch (err: any) {
      alert("Failed to connect platform: " + (err.message || String(err)));
    } finally {
      setConnecting(false);
    }
  };

  const handleDisconnectPlatform = async (provider: string) => {
    if (!projectId) return;
    if (!confirm(`Are you sure you want to disconnect ${provider}? Stored credentials will be purged.`)) return;
    try {
      await api.disconnectIntegration(projectId, provider);
      await loadData();
    } catch (err: any) {
      alert("Failed to disconnect: " + err.message);
    }
  };

  const handleSyncPlatform = async (provider: string) => {
    if (!projectId) return;
    try {
      setSyncingProvider(provider);
      await api.syncIntegration(projectId, provider);
      await loadData();
    } catch (err: any) {
      alert("Sync failed: " + err.message);
    } finally {
      setSyncingProvider(null);
    }
  };

  // MCP helpers
  const handleOpenAiConnect = (provider: AiProviderDef) => {
    setSelectedAiProvider(provider);
    setAiConnectStep(1);
    setTokenName(`${provider.name} Connection`);
    setSelectedPermissions(["READ_PROJECT_CONTEXT", "READ_ARTIFACTS", "SAVE_ARTIFACTS", "RECORD_DECISIONS", "READ_DEPLOYMENT_DATA"]);
    setRateLimit(60);
    setGeneratedSecretToken(null);
    setClientConfig(null);
    setIsAiConnectOpen(true);
  };

  const handleGenerateToken = async () => {
    if (!projectId || !selectedAiProvider?.platform) return;
    try {
      const resp = await api.createMcpToken(projectId, {
        tokenName,
        clientPlatform: selectedAiProvider.platform,
        permissions: selectedPermissions,
        rateLimitPerMinute: rateLimit,
      });

      setGeneratedSecretToken(resp.rawToken);
      const config = await api.getMcpConfig(projectId, selectedAiProvider.platform, window.location.origin, resp.rawToken);
      setClientConfig(config);
      setAiConnectStep(2);
      loadData();
    } catch (err: any) {
      alert("Failed to generate token: " + (err.message || String(err)));
    }
  };

  const handleTogglePause = async (token: McpToken) => {
    if (!projectId) return;
    try {
      await api.pauseMcpToken(projectId, token.id, !token.isPaused);
      loadData();
    } catch (err: any) {
      alert("Failed to update token state: " + err);
    }
  };

  const handleRevokeToken = async (tokenId: string) => {
    if (!projectId) return;
    if (!confirm("Are you sure you want to revoke this MCP token? Clients using it will be blocked immediately.")) return;
    try {
      await api.revokeMcpToken(projectId, tokenId);
      loadData();
    } catch (err: any) {
      alert("Failed to revoke token: " + err);
    }
  };

  const copyToClipboard = (text: string, setter: (val: boolean) => void) => {
    navigator.clipboard.writeText(text);
    setter(true);
    setTimeout(() => setter(false), 2000);
  };

  const getPlatformIcon = (provider: string) => {
    switch (provider.toUpperCase()) {
      case "GITHUB":
        return <GitBranch className="h-5 w-5 text-accent" />;
      case "RENDER":
        return <Cloud className="h-5 w-5 text-accent" />;
      case "POSTMAN":
        return <FileCode className="h-5 w-5 text-accent" />;
      case "DOCKER":
        return <Container className="h-5 w-5 text-accent" />;
      default:
        return <Plug className="h-5 w-5 text-accent" />;
    }
  };

  return (
    <div className="space-y-6">
      {/* Top Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
        <div>
          <div className="flex items-center gap-2 mb-1">
            <h1 className="text-xl font-semibold text-text-primary tracking-tight">
              Integrations & Connected Agents
            </h1>
            <Badge variant="neutral" size="sm" showDot={false}>
              Phase 4 External Ecosystem
            </Badge>
          </div>
          <p className="text-xs text-text-secondary">
            Connect CI/CD platforms, external engineering tools, and local workstations with cryptographic verification and zero data loss.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <Button variant="outline" size="sm" onClick={loadData} className="gap-1.5 text-xs">
            <RefreshCw className="h-3.5 w-3.5" /> Refresh
          </Button>
          <Button variant="default" size="sm" onClick={() => setIsWizardOpen(true)} className="gap-1.5 text-xs">
            <Laptop className="h-3.5 w-3.5" /> Pair Workstation
          </Button>
        </div>
      </div>

      {/* Honest Capability & Boundary Banner */}
      <div className="flex items-start gap-3 rounded-panel border border-accent/20 bg-accent-subtle/40 p-4">
        <Info className="h-4 w-4 text-accent shrink-0 mt-0.5" />
        <div className="text-xs text-text-primary space-y-1">
          <span className="font-semibold block">Constitutional Reliability & Security Guarantee (Rule 8 & 9)</span>
          <p className="text-text-secondary leading-relaxed">
            All credentials are encrypted with AES-256-GCM and never logged. Render deploy hooks are inbound-only, so status is synchronized via REST polling. Postman environment sync is strictly excluded. External data boundaries are enforced with cryptographic HMAC signatures and dead-letter replay.
          </p>
        </div>
      </div>

      {/* Main Tabs */}
      <Tabs value={activeTab} onValueChange={setActiveTab}>
        <TabsList className="grid grid-cols-2 sm:grid-cols-6 h-auto p-1">
          <TabsTrigger value="platforms" className="gap-1 text-xs py-1.5">
            <Plug className="h-3.5 w-3.5" /> Platforms
          </TabsTrigger>
          <TabsTrigger value="deployments" className="gap-1 text-xs py-1.5">
            <Cloud className="h-3.5 w-3.5" /> Deployments
          </TabsTrigger>
          <TabsTrigger value="agent" className="gap-1 text-xs py-1.5">
            <Laptop className="h-3.5 w-3.5" /> Workstation Agent
            {devices.filter((d) => d.status === "AUTHORIZED").length > 0 && (
              <span className="ml-1 h-4 w-4 rounded-full bg-success text-[10px] text-canvas font-bold flex items-center justify-center">
                {devices.filter((d) => d.status === "AUTHORIZED").length}
              </span>
            )}
          </TabsTrigger>
          <TabsTrigger value="ai_connectors" className="gap-1 text-xs py-1.5">
            <Bot className="h-3.5 w-3.5" /> AI MCP Clients
          </TabsTrigger>
          <TabsTrigger value="tokens" className="gap-1 text-xs py-1.5">
            <Lock className="h-3.5 w-3.5" /> MCP Tokens
          </TabsTrigger>
          <TabsTrigger value="dlq" className="gap-1 text-xs py-1.5">
            <ShieldAlert className="h-3.5 w-3.5 text-danger" /> Dead-Letter
          </TabsTrigger>
        </TabsList>

        {/* TAB 1: ENGINEERING PLATFORMS */}
        <TabsContent value="platforms" className="space-y-6 pt-4">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {capabilities.map((cap) => {
              const activeInt = integrations.find((i) => i.provider === cap.provider);
              const isConnected = activeInt?.status === "CONNECTED";
              const isSyncing = syncingProvider === cap.provider;

              return (
                <div
                  key={cap.provider}
                  className="rounded-panel border border-border bg-surface p-5 flex flex-col justify-between hover:border-border-focus transition-colors"
                >
                  <div className="space-y-3">
                    <div className="flex items-start justify-between">
                      <div className="flex items-center gap-3">
                        <div className="p-2 rounded bg-surface-raised border border-border">
                          {getPlatformIcon(cap.provider)}
                        </div>
                        <div>
                          <h3 className="text-sm font-semibold text-text-primary">{cap.displayName}</h3>
                          <Badge
                            variant={isConnected ? "success" : "neutral"}
                            size="sm"
                            className="mt-0.5"
                          >
                            {isConnected ? "Connected" : "Not Configured"}
                          </Badge>
                        </div>
                      </div>

                      <Badge variant="neutral" size="sm" showDot={false} className="text-[10px] font-mono">
                        {cap.primarySyncMethod}
                      </Badge>
                    </div>

                    <p className="text-xs text-text-secondary leading-relaxed">{cap.description}</p>

                    <div className="space-y-1.5 pt-2 border-t border-border/50 text-[11px]">
                      <div className="flex justify-between text-text-secondary">
                        <span>Auth Model:</span>
                        <span className="font-mono text-text-primary">{cap.authModel}</span>
                      </div>
                      <div className="flex justify-between text-text-secondary">
                        <span>Rate Limits:</span>
                        <span className="font-mono text-text-primary">{cap.rateLimits}</span>
                      </div>
                      <div className="text-text-secondary">
                        <span>Limitations: </span>
                        <span className="text-amber-500/90">{cap.limitations}</span>
                      </div>
                    </div>

                    {activeInt && activeInt.maskedCredential && (
                      <div className="bg-surface-raised/50 p-2 rounded border border-border/60 text-[11px] flex justify-between items-center">
                        <span className="text-text-secondary">Credential:</span>
                        <span className="font-mono text-text-primary">{activeInt.maskedCredential}</span>
                      </div>
                    )}
                  </div>

                  <div className="flex items-center justify-between gap-2 pt-4 mt-4 border-t border-border">
                    {isConnected ? (
                      <>
                        <div className="flex items-center gap-2">
                          <Button
                            variant="outline"
                            size="sm"
                            disabled={isSyncing}
                            onClick={() => handleSyncPlatform(cap.provider)}
                            className="gap-1.5 text-xs"
                          >
                            <RefreshCw className={`h-3 w-3 ${isSyncing ? "animate-spin" : ""}`} />
                            {isSyncing ? "Syncing..." : "Sync Now"}
                          </Button>
                        </div>
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => handleDisconnectPlatform(cap.provider)}
                          className="text-xs text-danger hover:text-danger hover:bg-danger-subtle"
                        >
                          Disconnect
                        </Button>
                      </>
                    ) : (
                      <Button
                        variant="default"
                        size="sm"
                        onClick={() => openPlatformConnect(cap.provider)}
                        className="w-full text-xs gap-1.5"
                      >
                        <Plug className="h-3.5 w-3.5" /> Configure {cap.displayName}
                      </Button>
                    )}
                  </div>
                </div>
              );
            })}
          </div>

          {/* Webhook Endpoint Reference Card */}
          <div className="rounded-panel border border-border bg-surface-raised p-4">
            <h4 className="text-xs font-semibold text-text-primary mb-2 flex items-center gap-2">
              <ShieldCheck className="h-4 w-4 text-accent" />
              Incoming Ingestion Webhook Endpoints
            </h4>
            <p className="text-xs text-text-secondary mb-3">
              Configure your remote repository or service to send events to Aether. Every payload is verified against your secret using constant-time HMAC-SHA256 signature checks.
            </p>
            <div className="space-y-2 font-mono text-xs">
              <div className="bg-canvas p-2.5 rounded border border-border flex justify-between items-center">
                <span className="text-accent">POST /api/webhooks/github/{projectId}</span>
                <Badge variant="neutral" size="sm" showDot={false}>Header: X-Hub-Signature-256</Badge>
              </div>
            </div>
          </div>
        </TabsContent>

        {/* TAB 2: DEPLOYMENTS & VERIFICATION */}
        <TabsContent value="deployments" className="pt-4">
          {projectId && <DeploymentsViewer projectId={projectId} />}
        </TabsContent>

        {/* TAB 3: WORKSTATION AGENT */}
        <TabsContent value="agent" className="space-y-6 pt-4">
          <div className="flex items-center justify-between">
            <div>
              <h3 className="text-sm font-semibold text-text-primary">Paired Workstations</h3>
              <p className="text-xs text-text-secondary">
                Direct local project agent running on developer machines. Synchronizes project code, symbols, and test runs without uploading secrets.
              </p>
            </div>
            <Button variant="default" size="sm" onClick={() => setIsWizardOpen(true)} className="gap-1.5 text-xs">
              <Laptop className="h-3.5 w-3.5" /> Pair New Workstation
            </Button>
          </div>

          {devices.length === 0 ? (
            <EmptyState
              icon={Laptop}
              title="No workstations paired yet"
              description="Pair your local development environment using the aether-agent CLI to track local test executions and symbol changes."
              actionLabel="Pair Workstation"
              onAction={() => setIsWizardOpen(true)}
            />
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {devices.map((device) => (
                <div
                  key={device.id}
                  className="rounded-panel border border-border bg-surface p-4 space-y-3"
                >
                  <div className="flex items-start justify-between">
                    <div className="flex items-center gap-2.5">
                      <div className="p-2 rounded bg-surface-raised border border-border">
                        <Terminal className="h-4 w-4 text-accent" />
                      </div>
                      <div>
                        <h4 className="text-xs font-semibold text-text-primary">
                          {device.hostname || "Workstation"}
                        </h4>
                        <span className="text-[10px] text-text-secondary font-mono">{device.os || "Unknown OS"}</span>
                      </div>
                    </div>
                    <Badge variant={device.status === "AUTHORIZED" ? "success" : "neutral"} size="sm">
                      {device.status}
                    </Badge>
                  </div>

                  <div className="space-y-1 text-xs text-text-secondary">
                    <div className="flex justify-between">
                      <span>Device Code:</span>
                      <span className="font-mono text-text-primary">{device.deviceCode}</span>
                    </div>
                    {device.localPath && (
                      <div className="flex justify-between">
                        <span>Directory:</span>
                        <span className="font-mono text-text-primary truncate max-w-[200px]">{device.localPath}</span>
                      </div>
                    )}
                    <div className="flex justify-between">
                      <span>Paired At:</span>
                      <span>{new Date(device.createdAt).toLocaleDateString()}</span>
                    </div>
                  </div>

                  <div className="pt-2 border-t border-border flex justify-end">
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={async () => {
                        if (!projectId || !confirm("Revoke this workstation pair?")) return;
                        await api.disconnectAgentDevice(projectId, device.id);
                        loadData();
                      }}
                      className="text-xs text-danger hover:text-danger hover:bg-danger-subtle"
                    >
                      Revoke Device
                    </Button>
                  </div>
                </div>
              ))}
            </div>
          )}

          {/* Local CLI Info Box */}
          <div className="rounded-panel border border-border bg-surface-raised p-4 space-y-2">
            <h4 className="text-xs font-semibold text-text-primary flex items-center gap-2">
              <Terminal className="h-4 w-4 text-accent" />
              Local-Only Air-Gapped Mode
            </h4>
            <p className="text-xs text-text-secondary">
              Need strict compliance or offline operation? Run the local agent with zero external network connectivity:
            </p>
            <CodeBlock code="aether-agent run --local-only --listen 127.0.0.1:8124" language="bash" />
          </div>
        </TabsContent>

        {/* TAB 4: AI MCP CLIENTS */}
        <TabsContent value="ai_connectors" className="space-y-6 pt-4">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {AI_PROVIDERS.map((provider) => (
              <div
                key={provider.id}
                className="rounded-panel border border-border bg-surface p-5 flex flex-col justify-between hover:border-border-focus transition-colors"
              >
                <div className="space-y-3">
                  <div className="flex items-start justify-between">
                    <div className="flex items-center gap-3">
                      <div className="p-2 rounded bg-surface-raised border border-border">
                        <Bot className="h-5 w-5 text-accent" />
                      </div>
                      <div>
                        <h3 className="text-sm font-semibold text-text-primary">{provider.name}</h3>
                        <span className="text-[10px] text-text-secondary">{provider.category}</span>
                      </div>
                    </div>
                    <Badge variant="neutral" size="sm" showDot={false}>
                      MCP Server
                    </Badge>
                  </div>

                  <p className="text-xs text-text-secondary leading-relaxed">{provider.description}</p>

                  <div className="bg-surface-raised/60 p-2.5 rounded border border-border/50 text-[11px] font-mono text-text-secondary">
                    <span className="text-text-primary font-semibold">Transport: </span>
                    {provider.transportInfo}
                  </div>
                </div>

                <div className="pt-4 mt-4 border-t border-border">
                  <Button
                    variant="default"
                    size="sm"
                    onClick={() => handleOpenAiConnect(provider)}
                    className="w-full text-xs gap-1.5"
                  >
                    <Plug className="h-3.5 w-3.5" /> Generate Connection Token
                  </Button>
                </div>
              </div>
            ))}
          </div>
        </TabsContent>

        {/* TAB 5: MCP TOKENS TABLE */}
        <TabsContent value="tokens" className="space-y-4 pt-4">
          <div className="flex items-center justify-between">
            <h3 className="text-sm font-semibold text-text-primary">Project Scoped MCP Access Tokens</h3>
            <span className="text-xs text-text-secondary">
              {tokens.filter((t) => !t.isRevoked).length} active tokens
            </span>
          </div>

          {tokens.length === 0 ? (
            <EmptyState
              icon={Lock}
              title="No MCP tokens created yet"
              description="Generate a scoped token for Claude, Antigravity, or ChatGPT in the AI MCP Clients tab."
            />
          ) : (
            <div className="rounded-panel border border-border overflow-hidden bg-surface">
              <table className="w-full text-left border-collapse text-xs">
                <thead>
                  <tr className="border-b border-border bg-surface-raised text-text-secondary font-medium">
                    <th className="py-2.5 px-3">Name & Client</th>
                    <th className="py-2.5 px-3">Prefix</th>
                    <th className="py-2.5 px-3">Permissions</th>
                    <th className="py-2.5 px-3">Rate Limit</th>
                    <th className="py-2.5 px-3">Status</th>
                    <th className="py-2.5 px-3 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border">
                  {tokens.map((token) => (
                    <tr key={token.id} className="hover:bg-surface-raised/40 transition-colors">
                      <td className="py-2.5 px-3 font-medium text-text-primary">
                        {token.tokenName}
                        <div className="text-[10px] text-text-secondary font-mono">{token.clientPlatform}</div>
                      </td>
                      <td className="py-2.5 px-3 font-mono text-text-secondary">{token.tokenPrefix}...</td>
                      <td className="py-2.5 px-3">
                        <div className="flex flex-wrap gap-1">
                          {token.permissions.map((p) => (
                            <Badge key={p} variant="neutral" size="sm" showDot={false} className="text-[9px]">
                              {p}
                            </Badge>
                          ))}
                        </div>
                      </td>
                      <td className="py-2.5 px-3 font-mono text-text-secondary">
                        {token.rateLimitPerMinute} req/min
                      </td>
                      <td className="py-2.5 px-3">
                        {token.isRevoked ? (
                          <Badge variant="neutral" size="sm">Revoked</Badge>
                        ) : token.isPaused ? (
                          <Badge variant="warning" size="sm">Paused</Badge>
                        ) : (
                          <Badge variant="success" size="sm">Active</Badge>
                        )}
                      </td>
                      <td className="py-2.5 px-3 text-right space-x-2">
                        {!token.isRevoked && (
                          <>
                            <Button
                              variant="ghost"
                              size="sm"
                              onClick={() => handleTogglePause(token)}
                              className="text-xs"
                            >
                              {token.isPaused ? <Play className="h-3 w-3" /> : <Pause className="h-3 w-3" />}
                            </Button>
                            <Button
                              variant="ghost"
                              size="sm"
                              onClick={() => handleRevokeToken(token.id)}
                              className="text-xs text-danger hover:text-danger hover:bg-danger-subtle"
                            >
                              <Trash2 className="h-3 w-3" />
                            </Button>
                          </>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </TabsContent>

        {/* TAB 6: DEAD-LETTER QUEUE */}
        <TabsContent value="dlq" className="pt-4">
          {projectId && <DeadLetterQueueDrawer projectId={projectId} />}
        </TabsContent>
      </Tabs>

      {/* PLATFORM CONFIGURATION MODAL */}
      <Dialog open={!!connectModalProvider} onOpenChange={(open) => !open && setConnectModalProvider(null)}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>Configure {connectModalProvider}</DialogTitle>
            <DialogDescription>
              Enter platform connection credentials. Secrets are stored encrypted with AES-256-GCM.
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4 py-2">
            {connectModalProvider === "GITHUB" && (
              <>
                <div className="space-y-1">
                  <label className="text-xs font-medium text-text-primary">Repository (owner/repo)</label>
                  <Input
                    placeholder="e.g. acme-corp/core-service"
                    value={configFields.repository || ""}
                    onChange={(e) => setConfigFields({ ...configFields, repository: e.target.value })}
                  />
                </div>
                <div className="space-y-1">
                  <label className="text-xs font-medium text-text-primary">Webhook Secret</label>
                  <Input
                    type="password"
                    placeholder="HMAC SHA-256 shared secret"
                    value={configFields.webhook_secret || ""}
                    onChange={(e) => setConfigFields({ ...configFields, webhook_secret: e.target.value })}
                  />
                </div>
                <div className="space-y-1">
                  <label className="text-xs font-medium text-text-primary">GitHub Personal Access Token (PAT)</label>
                  <Input
                    type="password"
                    placeholder="ghp_..."
                    value={credentialSecret}
                    onChange={(e) => setCredentialSecret(e.target.value)}
                  />
                </div>
              </>
            )}

            {connectModalProvider === "RENDER" && (
              <>
                <div className="space-y-1">
                  <label className="text-xs font-medium text-text-primary">Service ID</label>
                  <Input
                    placeholder="e.g. srv-c0123456789"
                    value={configFields.service_id || ""}
                    onChange={(e) => setConfigFields({ ...configFields, service_id: e.target.value })}
                  />
                </div>
                <div className="space-y-1">
                  <label className="text-xs font-medium text-text-primary">Render API Key</label>
                  <Input
                    type="password"
                    placeholder="rnd_..."
                    value={credentialSecret}
                    onChange={(e) => setCredentialSecret(e.target.value)}
                  />
                </div>
                <div className="bg-surface-raised p-2.5 rounded text-[11px] text-text-secondary border border-border">
                  <span className="font-semibold text-text-primary">Verified Architecture: </span>
                  Render deploy hooks do not emit outbound webhooks. Aether uses REST polling (`GET /v1/services/{'{serviceId}'}/deploys`) to sync deploy status and logs.
                </div>
              </>
            )}

            {connectModalProvider === "POSTMAN" && (
              <>
                <div className="space-y-1">
                  <label className="text-xs font-medium text-text-primary">Collection ID</label>
                  <Input
                    placeholder="Postman collection UID"
                    value={configFields.collection_id || ""}
                    onChange={(e) => setConfigFields({ ...configFields, collection_id: e.target.value })}
                  />
                </div>
                <div className="space-y-1">
                  <label className="text-xs font-medium text-text-primary">Postman API Key</label>
                  <Input
                    type="password"
                    placeholder="PMAK-..."
                    value={credentialSecret}
                    onChange={(e) => setCredentialSecret(e.target.value)}
                  />
                </div>
                <div className="bg-surface-raised p-2.5 rounded text-[11px] text-text-secondary border border-border">
                  <span className="font-semibold text-text-primary">Rule 8 Enforced: </span>
                  Postman `/environments` endpoint is strictly excluded. Only collections, schemas, and contract tests are ingested.
                </div>
              </>
            )}

            {connectModalProvider === "DOCKER" && (
              <>
                <div className="space-y-1">
                  <label className="text-xs font-medium text-text-primary">Dockerfile / Compose Path</label>
                  <Input
                    placeholder="e.g. docker-compose.yml or Dockerfile"
                    value={configFields.compose_path || "docker-compose.yml"}
                    onChange={(e) => setConfigFields({ ...configFields, compose_path: e.target.value })}
                  />
                </div>
                <div className="bg-surface-raised p-2.5 rounded text-[11px] text-text-secondary border border-border">
                  <span className="font-semibold text-text-primary">File-Based Analysis: </span>
                  Analyzes Dockerfile syntax, multi-stage builds, base image digests, and compose services from files. Direct daemon socket connections are unsupported.
                </div>
              </>
            )}
          </div>

          <div className="flex justify-end gap-2 pt-2">
            <Button variant="outline" size="sm" onClick={() => setConnectModalProvider(null)}>
              Cancel
            </Button>
            <Button
              variant="default"
              size="sm"
              disabled={connecting}
              onClick={handleConnectPlatform}
              className="gap-1.5"
            >
              {connecting ? "Saving..." : "Save Configuration"}
            </Button>
          </div>
        </DialogContent>
      </Dialog>

      {/* AI MCP TOKEN MODAL */}
      <Dialog open={isAiConnectOpen} onOpenChange={setIsAiConnectOpen}>
        <DialogContent className="max-w-xl">
          <DialogHeader>
            <DialogTitle>Connect {selectedAiProvider?.name}</DialogTitle>
            <DialogDescription>
              Generate a project-scoped Bearer token with specific constitutional permissions.
            </DialogDescription>
          </DialogHeader>

          {aiConnectStep === 1 ? (
            <div className="space-y-4 py-2">
              <div className="space-y-1">
                <label className="text-xs font-medium text-text-primary">Connection Name</label>
                <Input
                  value={tokenName}
                  onChange={(e) => setTokenName(e.target.value)}
                  placeholder="e.g. Antigravity Agent Local"
                />
              </div>

              <div className="space-y-2">
                <label className="text-xs font-medium text-text-primary">Constitutional Permissions</label>
                <div className="grid grid-cols-1 gap-2 max-h-48 overflow-y-auto p-1">
                  {ALL_CONSTITUTIONAL_PERMISSIONS.map((p) => {
                    const isChecked = selectedPermissions.includes(p.key);
                    return (
                      <label
                        key={p.key}
                        className={`flex items-start gap-2.5 p-2 rounded border cursor-pointer transition-colors ${
                          isChecked ? "bg-accent-subtle/30 border-accent/40" : "bg-surface-raised border-border"
                        }`}
                      >
                        <input
                          type="checkbox"
                          className="mt-0.5"
                          checked={isChecked}
                          onChange={(e) => {
                            if (e.target.checked) {
                              setSelectedPermissions([...selectedPermissions, p.key]);
                            } else {
                              setSelectedPermissions(selectedPermissions.filter((k) => k !== p.key));
                            }
                          }}
                        />
                        <div className="text-xs">
                          <span className="font-medium text-text-primary block">{p.label}</span>
                          <span className="text-[11px] text-text-secondary">{p.desc}</span>
                        </div>
                      </label>
                    );
                  })}
                </div>
              </div>

              <div className="flex justify-end gap-2 pt-2">
                <Button variant="outline" size="sm" onClick={() => setIsAiConnectOpen(false)}>
                  Cancel
                </Button>
                <Button variant="default" size="sm" onClick={handleGenerateToken}>
                  Generate Token
                </Button>
              </div>
            </div>
          ) : (
            <div className="space-y-4 py-2">
              <div className="bg-success-subtle/40 border border-success/30 rounded-panel p-3">
                <span className="text-xs font-semibold text-success block mb-1">
                  Token Generated Successfully
                </span>
                <p className="text-xs text-text-secondary">
                  Copy this token now. It will never be shown again.
                </p>
                <div className="mt-2 flex items-center gap-2">
                  <Input value={generatedSecretToken || ""} readOnly className="font-mono text-xs" />
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => copyToClipboard(generatedSecretToken || "", setCopiedToken)}
                  >
                    {copiedToken ? <Check className="h-3.5 w-3.5 text-success" /> : <Copy className="h-3.5 w-3.5" />}
                  </Button>
                </div>
              </div>

              {clientConfig && (
                <div className="space-y-2">
                  <label className="text-xs font-medium text-text-primary">Client Configuration</label>
                  <CodeBlock code={clientConfig.config_snippet} language="json" />
                  <div className="flex justify-end">
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => copyToClipboard(clientConfig.config_snippet, setCopiedSnippet)}
                      className="gap-1.5 text-xs"
                    >
                      {copiedSnippet ? <Check className="h-3 w-3 text-success" /> : <Copy className="h-3 w-3" />}
                      Copy Snippet
                    </Button>
                  </div>
                </div>
              )}

              <div className="flex justify-end pt-2">
                <Button variant="default" size="sm" onClick={() => setIsAiConnectOpen(false)}>
                  Done
                </Button>
              </div>
            </div>
          )}
        </DialogContent>
      </Dialog>

      {/* LOCAL PROJECT AGENT PAIRING WIZARD */}
      {projectId && (
        <LocalProjectWizard
          projectId={projectId}
          isOpen={isWizardOpen}
          onClose={() => setIsWizardOpen(false)}
          onPaired={() => {
            setIsWizardOpen(false);
            loadData();
          }}
        />
      )}
    </div>
  );
}
