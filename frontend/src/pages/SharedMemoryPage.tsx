import React from "react";
import { useParams } from "react-router-dom";
import { api } from "@/services/api";
import {
  SearchResultItem,
  ExtractedFact,
  IngestionJob,
  SecretFinding,
  ContextPackageResponse,
} from "@/types";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/tabs";
import { CodeBlock } from "@/components/ui/code-block";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from "@/components/ui/dialog";
import {
  Brain,
  Search,
  Shield,
  ShieldAlert,
  Cpu,
  CheckCircle2,
  XCircle,
  AlertTriangle,
  RefreshCw,
  Maximize2,
  FileText,
  Layers,
  Database,
  Code2,
  Scale,
  Sparkles,
  Sliders,
  Check,
  Ban,
} from "lucide-react";

export function SharedMemoryPage() {
  const { projectId } = useParams<{ projectId: string }>();

  // State
  const [activeTab, setActiveTab] = React.useState("search");

  // Tab 1: Hybrid Retrieval
  const [searchQuery, setSearchQuery] = React.useState("PostgreSQL RLS security");
  const [searchResults, setSearchResults] = React.useState<SearchResultItem[]>([]);
  const [isSearching, setIsSearching] = React.useState(false);
  const [expandedChunkData, setExpandedChunkData] = React.useState<{
    chunk_id: string;
    parent_section_path: string;
    expanded_view: string;
  } | null>(null);
  const [isExpanding, setIsExpanding] = React.useState(false);

  // Tab 2: Extracted Facts
  const [facts, setFacts] = React.useState<ExtractedFact[]>([]);
  const [selectedCategory, setSelectedCategory] = React.useState<string>("ALL");
  const [selectedFactState, setSelectedFactState] = React.useState<string>("ALL");
  const [isLoadingFacts, setIsLoadingFacts] = React.useState(false);
  const [evidenceDrawerFact, setEvidenceDrawerFact] = React.useState<ExtractedFact | null>(null);

  // Tab 3: Context Package Simulator
  const [taskPrompt, setTaskPrompt] = React.useState("Review database architecture and security controls");
  const [targetModel, setTargetModel] = React.useState("claude-3-5-sonnet");
  const [tokenBudget, setTokenBudget] = React.useState(16000);
  const [contextPackage, setContextPackage] = React.useState<ContextPackageResponse | null>(null);
  const [isGeneratingPackage, setIsGeneratingPackage] = React.useState(false);

  // Tab 4: Ingestion & Secrets
  const [jobs, setJobs] = React.useState<IngestionJob[]>([]);
  const [secrets, setSecrets] = React.useState<SecretFinding[]>([]);
  const [isLoadingPipeline, setIsLoadingPipeline] = React.useState(false);
  const [retryingJobId, setRetryingJobId] = React.useState<string | null>(null);

  // Initial load
  React.useEffect(() => {
    if (!projectId) return;
    loadFacts();
    loadPipeline();
  }, [projectId]);

  const handleSearch = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    if (!projectId || !searchQuery.trim()) return;
    setIsSearching(true);
    try {
      const results = await api.searchMemory(projectId, searchQuery.trim(), 15);
      setSearchResults(results);
    } catch (err) {
      console.error("Search error", err);
    } finally {
      setIsSearching(false);
    }
  };

  const handleExpandChunk = async (chunkId: string) => {
    if (!projectId) return;
    setIsExpanding(true);
    try {
      const resp = await api.expandChunk(projectId, chunkId);
      setExpandedChunkData({
        chunk_id: resp.chunk_id,
        parent_section_path: resp.parent_section_path,
        expanded_view: resp.expanded_view,
      });
    } catch (err) {
      console.error("Failed to expand chunk", err);
    } finally {
      setIsExpanding(false);
    }
  };

  const loadFacts = async () => {
    if (!projectId) return;
    setIsLoadingFacts(true);
    try {
      const cat = selectedCategory === "ALL" ? undefined : selectedCategory;
      const st = selectedFactState === "ALL" ? undefined : selectedFactState;
      const data = await api.listFacts(projectId, cat, st, 100);
      setFacts(data);
    } catch (err) {
      console.error("Failed to load facts", err);
    } finally {
      setIsLoadingFacts(false);
    }
  };

  const handleReviewFact = async (factId: string, newState: "VERIFIED" | "REJECTED") => {
    if (!projectId) return;
    try {
      await api.reviewFact(projectId, factId, newState, "Manual human review via Memory Console");
      await loadFacts();
      if (evidenceDrawerFact?.id === factId) {
        setEvidenceDrawerFact((prev) => prev ? { ...prev, validationState: newState } : null);
      }
    } catch (err) {
      console.error("Failed to review fact", err);
    }
  };

  const handleGeneratePackage = async () => {
    if (!projectId || !taskPrompt.trim()) return;
    setIsGeneratingPackage(true);
    try {
      const pkg = await api.previewContextPackage(
        projectId,
        taskPrompt.trim(),
        targetModel,
        tokenBudget
      );
      setContextPackage(pkg);
    } catch (err) {
      console.error("Failed to generate context package", err);
    } finally {
      setIsGeneratingPackage(false);
    }
  };

  const loadPipeline = async () => {
    if (!projectId) return;
    setIsLoadingPipeline(true);
    try {
      const [jobsData, secretsData] = await Promise.all([
        api.listIngestionJobs(projectId),
        api.listSecrets(projectId),
      ]);
      setJobs(jobsData);
      setSecrets(secretsData);
    } catch (err) {
      console.error("Failed to load pipeline data", err);
    } finally {
      setIsLoadingPipeline(false);
    }
  };

  const handleRetryJob = async (jobId: string) => {
    if (!projectId) return;
    setRetryingJobId(jobId);
    try {
      await api.retryIngestionJob(projectId, jobId);
      await loadPipeline();
    } catch (err) {
      console.error("Failed to retry job", err);
    } finally {
      setRetryingJobId(null);
    }
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <Brain className="h-6 w-6 text-accent" />
            <h1 className="text-xl font-semibold text-text-primary tracking-tight">
              Shared Project Memory & Knowledge Hub
            </h1>
          </div>
          <p className="text-xs text-text-secondary mt-1">
            Hybrid RAG retrieval (pgvector + full-text + structured SQL), provenance-verified facts, token-budgeted orchestrator, and real-time ingestion.
          </p>
        </div>
        {secrets.length > 0 && (
          <div className="flex items-center gap-2 bg-amber-500/10 border border-amber-500/30 text-amber-400 px-3 py-1.5 rounded-control text-xs">
            <ShieldAlert className="h-4 w-4" />
            <span>{secrets.length} credential(s) detected & redacted</span>
          </div>
        )}
      </div>

      {/* Main Tabs */}
      <Tabs value={activeTab} onValueChange={setActiveTab} className="space-y-4">
        <TabsList className="bg-subtle border border-border">
          <TabsTrigger value="search" className="flex items-center gap-1.5 text-xs">
            <Search className="h-3.5 w-3.5" />
            Hybrid Retrieval
          </TabsTrigger>
          <TabsTrigger value="facts" className="flex items-center gap-1.5 text-xs">
            <Scale className="h-3.5 w-3.5" />
            Extracted Facts ({facts.length})
          </TabsTrigger>
          <TabsTrigger value="orchestrator" className="flex items-center gap-1.5 text-xs">
            <Cpu className="h-3.5 w-3.5" />
            Context Orchestrator
          </TabsTrigger>
          <TabsTrigger value="pipeline" className="flex items-center gap-1.5 text-xs">
            <Layers className="h-3.5 w-3.5" />
            Ingestion & Secrets ({jobs.length})
          </TabsTrigger>
        </TabsList>

        {/* ========================================================================= */}
        {/* TAB 1: HYBRID RETRIEVAL SEARCH */}
        {/* ========================================================================= */}
        <TabsContent value="search" className="space-y-4">
          <form onSubmit={handleSearch} className="flex gap-2">
            <div className="relative flex-1">
              <Search className="absolute left-3 top-2.5 h-4 w-4 text-text-tertiary" />
              <Input
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="Search across project memory (semantic vectors, full-text keywords, approved decisions, symbol offsets)..."
                className="pl-9 h-10 text-xs bg-canvas"
              />
            </div>
            <Button type="submit" disabled={isSearching} className="h-10 text-xs px-4">
              {isSearching ? <RefreshCw className="h-3.5 w-3.5 animate-spin mr-1.5" /> : <Sparkles className="h-3.5 w-3.5 mr-1.5" />}
              Hybrid Search
            </Button>
          </form>

          {/* Results List */}
          {isSearching ? (
            <div className="space-y-3">
              {[1, 2, 3].map((i) => (
                <Skeleton key={i} className="h-28 rounded-panel" />
              ))}
            </div>
          ) : searchResults.length === 0 ? (
            <div className="border border-dashed border-border rounded-panel p-8 text-center text-text-secondary text-xs">
              <Brain className="h-8 w-8 mx-auto mb-2 text-text-tertiary" />
              <p>Type a query and run Hybrid Search to retrieve multi-ranked artifact chunks.</p>
              <p className="text-[11px] text-text-tertiary mt-1">
                Fuses pgvector cosine distance, Postgres tsvector full-text, and current approved architectural decisions.
              </p>
            </div>
          ) : (
            <div className="space-y-3">
              <div className="flex items-center justify-between text-xs text-text-secondary px-1">
                <span>Top {searchResults.length} multi-fused results (RRF ranked):</span>
                <span className="text-[11px]">Approved state outranks historical versions</span>
              </div>

              {searchResults.map((item, idx) => {
                const methods = item.methodsMatched || [];
                return (
                  <div
                    key={item.chunkId || idx}
                    className="border border-border rounded-panel p-4 bg-canvas space-y-2 hover:border-border-focus transition-colors"
                  >
                    <div className="flex items-center justify-between gap-2 flex-wrap">
                      <div className="flex items-center gap-2">
                        <span className="font-mono text-xs text-text-tertiary">#{idx + 1}</span>
                        <span className="text-xs font-semibold text-text-primary">
                          {item.parentSectionPath || "Root Section"}
                        </span>
                        <Badge variant="neutral" className="text-[10px]">
                          v{item.artifactVersion}
                        </Badge>
                        {item.approvalState === "APPROVED" && (
                          <Badge className="bg-emerald-500/10 text-emerald-400 border border-emerald-500/30 text-[10px]">
                            APPROVED
                          </Badge>
                        )}
                        {item.isSuperseded && (
                          <Badge className="bg-amber-500/10 text-amber-400 border border-amber-500/30 text-[10px]">
                            SUPERSEDED
                          </Badge>
                        )}
                      </div>

                      <div className="flex items-center gap-1.5">
                        {methods.map((m) => (
                          <span
                            key={m}
                            className="px-1.5 py-0.5 rounded text-[9px] font-mono bg-subtle text-text-secondary border border-border"
                          >
                            {m}
                          </span>
                        ))}
                        <span className="font-mono text-[11px] text-accent font-medium ml-1">
                          Score: {item.fusedScore?.toFixed(3)}
                        </span>
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => handleExpandChunk(item.chunkId)}
                          className="h-7 text-xs ml-2"
                        >
                          <Maximize2 className="h-3 w-3 mr-1" />
                          Expand
                        </Button>
                      </div>
                    </div>

                    <p className="text-xs text-text-secondary line-clamp-3 font-mono bg-subtle/50 p-2 rounded border border-border/50">
                      {item.content}
                    </p>

                    <div className="flex items-center justify-between text-[11px] text-text-tertiary pt-1">
                      <span>
                        Lines {item.startLine}–{item.endLine} (offsets {item.startOffset}–{item.endOffset})
                      </span>
                      <span className="font-mono text-[10px]">Hash: {item.contentHash?.substring(0, 12)}…</span>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </TabsContent>

        {/* ========================================================================= */}
        {/* TAB 2: EXTRACTED KNOWLEDGE FACTS */}
        {/* ========================================================================= */}
        <TabsContent value="facts" className="space-y-4">
          {/* Filters */}
          <div className="flex flex-wrap items-center justify-between gap-3 bg-subtle p-3 rounded-panel border border-border">
            <div className="flex items-center gap-2 flex-wrap">
              <span className="text-xs text-text-secondary">Category:</span>
              {["ALL", "REQUIREMENT", "CONSTRAINT", "ARCHITECTURE", "API_CONTRACT", "DATABASE_DESIGN"].map((cat) => (
                <button
                  key={cat}
                  onClick={() => {
                    setSelectedCategory(cat);
                    setTimeout(loadFacts, 0);
                  }}
                  className={`px-2.5 py-1 rounded text-xs transition-colors ${
                    selectedCategory === cat
                      ? "bg-accent text-accent-foreground font-medium"
                      : "bg-canvas text-text-secondary hover:text-text-primary border border-border"
                  }`}
                >
                  {cat}
                </button>
              ))}
            </div>

            <div className="flex items-center gap-2">
              <span className="text-xs text-text-secondary">State:</span>
              {["ALL", "VERIFIED", "UNVERIFIED", "REJECTED"].map((st) => (
                <button
                  key={st}
                  onClick={() => {
                    setSelectedFactState(st);
                    setTimeout(loadFacts, 0);
                  }}
                  className={`px-2 py-0.5 rounded text-[11px] transition-colors ${
                    selectedFactState === st
                      ? "bg-text-primary text-canvas font-medium"
                      : "bg-canvas text-text-secondary hover:text-text-primary border border-border"
                  }`}
                >
                  {st}
                </button>
              ))}
            </div>
          </div>

          {/* Facts List */}
          {isLoadingFacts ? (
            <div className="space-y-3">
              {[1, 2, 3].map((i) => (
                <Skeleton key={i} className="h-24 rounded-panel" />
              ))}
            </div>
          ) : facts.length === 0 ? (
            <div className="border border-dashed border-border rounded-panel p-8 text-center text-text-secondary text-xs">
              No extracted facts match the selected filters.
            </div>
          ) : (
            <div className="space-y-3">
              {facts.map((fact) => (
                <div
                  key={fact.id}
                  className="border border-border rounded-panel p-4 bg-canvas space-y-2 hover:border-border-focus transition-colors"
                >
                  <div className="flex items-center justify-between gap-2 flex-wrap">
                    <div className="flex items-center gap-2">
                      <Badge variant="neutral" className="font-mono text-[10px]">
                        {fact.category}
                      </Badge>
                      <span className="text-xs font-semibold text-text-primary">{fact.factKey}</span>
                      {fact.validationState === "VERIFIED" && (
                        <Badge className="bg-emerald-500/10 text-emerald-400 border border-emerald-500/30 text-[10px] flex items-center gap-1">
                          <CheckCircle2 className="h-3 w-3" /> VERIFIED
                        </Badge>
                      )}
                      {fact.validationState === "UNVERIFIED" && (
                        <Badge className="bg-amber-500/10 text-amber-400 border border-amber-500/30 text-[10px] flex items-center gap-1">
                          <AlertTriangle className="h-3 w-3" /> UNVERIFIED
                        </Badge>
                      )}
                      {fact.validationState === "REJECTED" && (
                        <Badge className="bg-red-500/10 text-red-400 border border-red-500/30 text-[10px] flex items-center gap-1">
                          <XCircle className="h-3 w-3" /> REJECTED
                        </Badge>
                      )}
                    </div>

                    <div className="flex items-center gap-1">
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => setEvidenceDrawerFact(fact)}
                        className="h-7 text-xs"
                      >
                        Evidence Drawer
                      </Button>
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => handleReviewFact(fact.id, "VERIFIED")}
                        disabled={fact.validationState === "VERIFIED"}
                        className="h-7 text-[11px] text-emerald-400 hover:text-emerald-300 hover:bg-emerald-500/10"
                      >
                        <Check className="h-3 w-3 mr-1" />
                        Verify
                      </Button>
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => handleReviewFact(fact.id, "REJECTED")}
                        disabled={fact.validationState === "REJECTED"}
                        className="h-7 text-[11px] text-red-400 hover:text-red-300 hover:bg-red-500/10"
                      >
                        <Ban className="h-3 w-3 mr-1" />
                        Reject
                      </Button>
                    </div>
                  </div>

                  <p className="text-xs text-text-primary">{fact.factStatement}</p>

                  <div className="bg-subtle p-2 rounded border border-border text-[11px] text-text-secondary">
                    <span className="font-semibold text-text-primary">Supporting Quote: </span>
                    <span className="italic font-mono">"{fact.exactQuote}"</span>
                  </div>

                  <div className="flex items-center justify-between text-[11px] text-text-tertiary">
                    <span>
                      Offsets {fact.startOffset}–{fact.endOffset} · Source: {fact.sourcePlatform} ({fact.extractionMethod})
                    </span>
                    <span>Confidence: {(fact.confidenceScore * 100).toFixed(0)}%</span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </TabsContent>

        {/* ========================================================================= */}
        {/* TAB 3: CONTEXT PACKAGE SIMULATOR */}
        {/* ========================================================================= */}
        <TabsContent value="orchestrator" className="space-y-4">
          <div className="border border-border rounded-panel p-4 bg-canvas space-y-4">
            <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
              <div className="md:col-span-2 space-y-1">
                <label className="text-xs font-medium text-text-primary">Task Description</label>
                <Input
                  value={taskPrompt}
                  onChange={(e) => setTaskPrompt(e.target.value)}
                  placeholder="Describe agent task (e.g. Architecture review, Fix database timeout bug)..."
                  className="h-9 text-xs"
                />
              </div>

              <div className="space-y-1">
                <label className="text-xs font-medium text-text-primary">Target Model Profile</label>
                <select
                  value={targetModel}
                  onChange={(e) => setTargetModel(e.target.value)}
                  className="w-full h-9 rounded-control border border-border bg-canvas px-3 text-xs text-text-primary focus:outline-none focus:ring-1 focus:ring-accent"
                >
                  <option value="claude-3-5-sonnet">Claude 3.5 Sonnet (200k max, 16k default)</option>
                  <option value="gpt-4o">ChatGPT / GPT-4o (128k max, 12k default)</option>
                  <option value="gemini-1-5-pro">Google Antigravity / Gemini 1.5 Pro (1M max, 32k default)</option>
                  <option value="default-conservative-4k">Default Conservative Fallback (4k ceiling)</option>
                </select>
              </div>
            </div>

            <div className="flex items-center justify-between pt-1">
              <div className="flex items-center gap-2">
                <span className="text-xs text-text-secondary">Token Budget:</span>
                <input
                  type="number"
                  value={tokenBudget}
                  onChange={(e) => setTokenBudget(Number(e.target.value))}
                  className="h-8 w-28 rounded-control border border-border bg-canvas px-2 text-xs font-mono"
                  step="1000"
                  min="2000"
                  max="64000"
                />
              </div>

              <Button
                onClick={handleGeneratePackage}
                disabled={isGeneratingPackage}
                className="h-9 text-xs px-4"
              >
                {isGeneratingPackage ? (
                  <RefreshCw className="h-3.5 w-3.5 animate-spin mr-1.5" />
                ) : (
                  <Sparkles className="h-3.5 w-3.5 mr-1.5" />
                )}
                Build Context Package
              </Button>
            </div>
          </div>

          {/* Context Package Output */}
          {contextPackage && (
            <div className="space-y-4">
              {/* Token Meter Bar */}
              <div className="border border-border rounded-panel p-4 bg-canvas space-y-2">
                <div className="flex items-center justify-between text-xs">
                  <div className="flex items-center gap-2">
                    <span className="font-semibold text-text-primary">Token Utilization:</span>
                    <span className="font-mono text-accent">
                      {contextPackage.totalTokensUsed} / {contextPackage.maxTokenBudget} tokens ({contextPackage.budgetUtilizationPercent?.toFixed(1)}%)
                    </span>
                  </div>
                  <Badge variant="neutral" className="font-mono text-[10px]">
                    Request: {contextPackage.requestType}
                  </Badge>
                </div>

                <div className="w-full bg-subtle h-2 rounded-full overflow-hidden border border-border">
                  <div
                    className={`h-full transition-all duration-300 ${
                      (contextPackage.budgetUtilizationPercent || 0) > 90
                        ? "bg-amber-500"
                        : "bg-emerald-500"
                    }`}
                    style={{ width: `${Math.min(100, contextPackage.budgetUtilizationPercent || 0)}%` }}
                  />
                </div>
              </div>

              {/* Package Content Preview */}
              <div className="border border-border rounded-panel p-4 bg-canvas space-y-2">
                <div className="flex items-center justify-between text-xs pb-1 border-b border-border">
                  <span className="font-semibold text-text-primary">Package Payload</span>
                  <span className="text-[11px] text-text-tertiary">
                    Delimited with UNTRUSTED_DATA_BOUNDARY to prevent prompt injection
                  </span>
                </div>
                <CodeBlock
                  code={contextPackage.systemPromptEnclosure || ""}
                  language="markdown"
                />
              </div>

              {/* Staged Retrieval Index of Omitted Items */}
              {contextPackage.omittedItems && contextPackage.omittedItems.length > 0 && (
                <div className="border border-border rounded-panel p-4 bg-canvas space-y-3">
                  <div className="flex items-center justify-between text-xs pb-1 border-b border-border">
                    <div className="flex items-center gap-1.5 font-semibold text-text-primary">
                      <Sliders className="h-4 w-4 text-text-tertiary" />
                      Staged Retrieval Index ({contextPackage.omittedItems.length} items omitted to protect token headroom)
                    </div>
                  </div>

                  <div className="space-y-2">
                    {contextPackage.omittedItems.map((item, idx) => (
                      <div
                        key={idx}
                        className="flex items-center justify-between gap-2 p-2 bg-subtle rounded border border-border text-xs"
                      >
                        <div>
                          <span className="font-medium text-text-primary">{item.title}</span>
                          <span className="text-text-tertiary text-[11px] ml-2">({item.tokenCost} tokens)</span>
                          <p className="text-[11px] text-text-secondary">{item.omissionReason}</p>
                        </div>
                        <code className="text-[10px] font-mono bg-canvas px-2 py-1 rounded border border-border text-accent">
                          {item.retrievalHandle}
                        </code>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          )}
        </TabsContent>

        {/* ========================================================================= */}
        {/* TAB 4: INGESTION PIPELINE & SECRETS */}
        {/* ========================================================================= */}
        <TabsContent value="pipeline" className="space-y-4">
          {/* Secret Detection Panel */}
          <div className="border border-border rounded-panel p-4 bg-canvas space-y-3">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Shield className="h-4 w-4 text-accent" />
                <h3 className="text-xs font-semibold text-text-primary">
                  Secret Detection & Redaction Auditing
                </h3>
              </div>
              <Badge variant="neutral" className="text-[11px]">
                Originals immutable · Embeddings sanitized
              </Badge>
            </div>

            {secrets.length === 0 ? (
              <p className="text-xs text-text-secondary">
                No secrets detected. All specification uploads passed Shannon entropy and regex scanners cleanly.
              </p>
            ) : (
              <div className="space-y-2">
                {secrets.map((sec) => (
                  <div
                    key={sec.id}
                    className="p-3 bg-red-500/5 border border-red-500/20 rounded-panel flex items-center justify-between gap-2 text-xs"
                  >
                    <div>
                      <div className="flex items-center gap-2">
                        <Badge className="bg-red-500/10 text-red-400 border border-red-500/30 text-[10px]">
                          {sec.findingType}
                        </Badge>
                        <span className="font-medium text-text-primary">{sec.findingLocation}</span>
                      </div>
                      <p className="text-[11px] font-mono text-text-secondary mt-1">
                        Redacted snippet: {sec.maskedSnippet}
                      </p>
                    </div>
                    <Badge variant="neutral" className="text-[10px] text-emerald-400 border-emerald-500/30">
                      Redacted from RAG
                    </Badge>
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* Ingestion Jobs Queue */}
          <div className="border border-border rounded-panel p-4 bg-canvas space-y-3">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <RefreshCw className="h-4 w-4 text-text-tertiary" />
                <h3 className="text-xs font-semibold text-text-primary">Ingestion Jobs & Dead-Letter Queue</h3>
              </div>
              <Button variant="ghost" size="sm" onClick={loadPipeline} className="h-7 text-xs">
                <RefreshCw className="h-3 w-3 mr-1" />
                Refresh
              </Button>
            </div>

            {jobs.length === 0 ? (
              <p className="text-xs text-text-secondary">No background ingestion jobs recorded.</p>
            ) : (
              <div className="space-y-2">
                {jobs.map((job) => (
                  <div
                    key={job.id}
                    className="p-3 bg-subtle rounded border border-border flex items-center justify-between gap-2 text-xs"
                  >
                    <div>
                      <div className="flex items-center gap-2">
                        <span className="font-mono text-[11px] text-text-secondary">
                          Artifact {job.artifactId?.substring(0, 8)}… v{job.artifactVersion}
                        </span>
                        {job.status === "COMPLETED" && (
                          <Badge className="bg-emerald-500/10 text-emerald-400 border border-emerald-500/30 text-[10px]">
                            COMPLETED
                          </Badge>
                        )}
                        {job.status === "FAILED" && (
                          <Badge className="bg-red-500/10 text-red-400 border border-red-500/30 text-[10px]">
                            FAILED
                          </Badge>
                        )}
                        {job.status === "RUNNING" && (
                          <Badge className="bg-accent/10 text-accent border border-accent/30 text-[10px]">
                            RUNNING
                          </Badge>
                        )}
                      </div>
                      <p className="text-[11px] text-text-tertiary mt-0.5">
                        Step: {job.currentStep} · Retries: {job.retryCount}
                        {job.errorMessage && ` · Error: ${job.errorMessage}`}
                      </p>
                    </div>

                    {job.status === "FAILED" && (
                      <Button
                        variant="outline"
                        size="sm"
                        disabled={retryingJobId === job.id}
                        onClick={() => handleRetryJob(job.id)}
                        className="h-7 text-xs"
                      >
                        <RefreshCw className={`h-3 w-3 mr-1 ${retryingJobId === job.id ? "animate-spin" : ""}`} />
                        Retry Job
                      </Button>
                    )}
                  </div>
                ))}
              </div>
            )}
          </div>
        </TabsContent>
      </Tabs>

      {/* Expand Chunk Modal */}
      <Dialog open={!!expandedChunkData} onOpenChange={(open) => !open && setExpandedChunkData(null)}>
        <DialogContent className="max-w-3xl max-h-[85vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle className="text-sm font-semibold text-text-primary">
              Expanded Chunk Context
            </DialogTitle>
            <DialogDescription className="text-xs text-text-secondary">
              Viewing chunk in full original artifact context with provenance boundaries.
            </DialogDescription>
          </DialogHeader>

          {expandedChunkData && (
            <div className="space-y-3 pt-2">
              <div className="text-xs font-mono text-accent">
                Section: {expandedChunkData.parent_section_path}
              </div>
              <CodeBlock code={expandedChunkData.expanded_view} language="markdown" />
            </div>
          )}
        </DialogContent>
      </Dialog>

      {/* Evidence Drawer Modal */}
      <Dialog open={!!evidenceDrawerFact} onOpenChange={(open) => !open && setEvidenceDrawerFact(null)}>
        <DialogContent className="max-w-2xl max-h-[80vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle className="text-sm font-semibold text-text-primary">
              Fact Evidence & Provenance Drawer
            </DialogTitle>
            <DialogDescription className="text-xs text-text-secondary">
              Programmatic validation checks verbatim quotes against immutable storage.
            </DialogDescription>
          </DialogHeader>

          {evidenceDrawerFact && (
            <div className="space-y-3 pt-2 text-xs">
              <div>
                <span className="font-semibold text-text-primary">Extracted Statement:</span>
                <p className="mt-1 text-text-secondary">{evidenceDrawerFact.factStatement}</p>
              </div>

              <div className="bg-subtle p-3 rounded border border-border">
                <span className="font-semibold text-text-primary">Verbatim Quote in Original Artifact:</span>
                <p className="mt-1 font-mono italic text-accent">"{evidenceDrawerFact.exactQuote}"</p>
                <div className="mt-2 text-[11px] text-text-tertiary flex gap-4">
                  <span>Start Offset: {evidenceDrawerFact.startOffset}</span>
                  <span>End Offset: {evidenceDrawerFact.endOffset}</span>
                  <span>Artifact Version: v{evidenceDrawerFact.artifactVersion}</span>
                </div>
              </div>

              {evidenceDrawerFact.validationNotes && (
                <div>
                  <span className="font-semibold text-text-primary">Validation Notes:</span>
                  <p className="mt-1 font-mono text-[11px] text-text-secondary">
                    {evidenceDrawerFact.validationNotes}
                  </p>
                </div>
              )}

              <div className="flex justify-end gap-2 pt-3 border-t border-border">
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => handleReviewFact(evidenceDrawerFact.id, "REJECTED")}
                  disabled={evidenceDrawerFact.validationState === "REJECTED"}
                  className="text-red-400 hover:text-red-300"
                >
                  <Ban className="h-3.5 w-3.5 mr-1" />
                  Reject Fact
                </Button>
                <Button
                  size="sm"
                  onClick={() => handleReviewFact(evidenceDrawerFact.id, "VERIFIED")}
                  disabled={evidenceDrawerFact.validationState === "VERIFIED"}
                  className="bg-emerald-600 hover:bg-emerald-500 text-white"
                >
                  <Check className="h-3.5 w-3.5 mr-1" />
                  Verify Fact
                </Button>
              </div>
            </div>
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}
