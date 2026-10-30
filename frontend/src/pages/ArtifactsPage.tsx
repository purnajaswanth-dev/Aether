import React from "react";
import { useParams, useSearchParams } from "react-router-dom";
import { api } from "@/services/api";
import { Artifact, ArtifactVersion, ArtifactDiffResponse } from "@/types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { SplitPane } from "@/components/ui/split-pane";
import { CodeBlock } from "@/components/ui/code-block";
import { DiffViewer } from "@/components/ui/diff-viewer";
import { EmptyState } from "@/components/ui/empty-state";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from "@/components/ui/dialog";
import { Plus, FileCode2, History, GitCompare, Upload, ShieldCheck, Search } from "lucide-react";
import { formatBytes, formatDate } from "@/lib/utils";

export function ArtifactsPage() {
  const { projectId } = useParams<{ projectId: string }>();
  const [searchParams] = useSearchParams();

  const [artifacts, setArtifacts] = React.useState<Artifact[]>([]);
  const [selectedArtifact, setSelectedArtifact] = React.useState<Artifact | null>(null);
  const [versions, setVersions] = React.useState<ArtifactVersion[]>([]);
  const [selectedVersionNum, setSelectedVersionNum] = React.useState<number>(1);
  const [currentContent, setCurrentContent] = React.useState<string>("");
  const [currentHash, setCurrentHash] = React.useState<string>("");

  const [loading, setLoading] = React.useState(true);
  const [contentLoading, setContentLoading] = React.useState(false);
  const [search, setSearch] = React.useState("");

  // Compare mode
  const [compareMode, setCompareMode] = React.useState(false);
  const [compareV1, setCompareV1] = React.useState<number>(1);
  const [compareV2, setCompareV2] = React.useState<number>(2);
  const [diffResult, setDiffResult] = React.useState<ArtifactDiffResponse | null>(null);
  const [diffLoading, setDiffLoading] = React.useState(false);

  // New Artifact Dialog
  const [saveDialogOpen, setSaveDialogOpen] = React.useState(searchParams.get("action") === "new");
  const [newTitle, setNewTitle] = React.useState("");
  const [newType, setNewType] = React.useState("ARCHITECTURE");
  const [newContent, setNewContent] = React.useState("");
  const [saveLoading, setSaveLoading] = React.useState(false);

  // New Version Dialog
  const [newVersionDialogOpen, setNewVersionDialogOpen] = React.useState(false);
  const [versionContent, setVersionContent] = React.useState("");
  const [versionSummary, setVersionSummary] = React.useState("");

  const loadArtifacts = React.useCallback(async () => {
    if (!projectId) return;
    try {
      const data = await api.listArtifacts(projectId, { search });
      setArtifacts(data);
      if (data.length > 0 && !selectedArtifact) {
        setSelectedArtifact(data[0]);
      }
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  }, [projectId, search, selectedArtifact]);

  React.useEffect(() => {
    loadArtifacts();
  }, [loadArtifacts]);

  // Load content of selected artifact / version
  React.useEffect(() => {
    if (!projectId || !selectedArtifact) return;

    setContentLoading(true);
    setCompareMode(false);
    setDiffResult(null);

    Promise.all([
      api.listArtifactVersions(projectId, selectedArtifact.id),
      api.getLatestArtifact(projectId, selectedArtifact.id),
    ])
      .then(([vList, latest]) => {
        setVersions(vList);
        setSelectedVersionNum(latest.currentVersion);
        setCurrentContent(latest.content || "");
        setCurrentHash(latest.contentHash);
        if (vList.length >= 2) {
          setCompareV1(vList[vList.length - 1].version);
          setCompareV2(vList[0].version);
        }
      })
      .catch((err) => console.error(err))
      .finally(() => setContentLoading(false));
  }, [projectId, selectedArtifact]);

  const handleSelectVersion = async (vNum: number) => {
    if (!projectId || !selectedArtifact) return;
    setContentLoading(true);
    try {
      const ver = await api.getArtifactVersion(projectId, selectedArtifact.id, vNum);
      setSelectedVersionNum(vNum);
      setCurrentContent(ver.content || "");
      setCurrentHash(ver.contentHash);
    } catch (err) {
      console.error(err);
    } finally {
      setContentLoading(false);
    }
  };

  const handleRunDiff = async () => {
    if (!projectId || !selectedArtifact) return;
    setDiffLoading(true);
    try {
      const res = await api.compareArtifactVersions(projectId, selectedArtifact.id, compareV1, compareV2);
      setDiffResult(res);
      setCompareMode(true);
    } catch (err) {
      console.error(err);
    } finally {
      setDiffLoading(false);
    }
  };

  const handleSaveNewArtifact = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!projectId || !newTitle.trim() || !newContent.trim()) return;

    setSaveLoading(true);
    try {
      const created = await api.createArtifact(projectId, {
        title: newTitle.trim(),
        artifactType: newType,
        sourcePlatform: "MANUAL",
        content: newContent,
        changeSummary: "Initial import",
      });

      setSaveDialogOpen(false);
      setNewTitle("");
      setNewContent("");
      await loadArtifacts();
      setSelectedArtifact(created);
    } catch (err) {
      alert("Failed to save artifact: " + err);
    } finally {
      setSaveLoading(false);
    }
  };

  const handleCreateNewVersion = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!projectId || !selectedArtifact || !versionContent.trim()) return;

    setSaveLoading(true);
    try {
      await api.createArtifactVersion(projectId, selectedArtifact.id, {
        content: versionContent,
        changeSummary: versionSummary.trim() || undefined,
      });

      setNewVersionDialogOpen(false);
      setVersionContent("");
      setVersionSummary("");
      // Reload current artifact
      const latest = await api.getLatestArtifact(projectId, selectedArtifact.id);
      setSelectedArtifact(latest);
    } catch (err) {
      alert("Failed to create version: " + err);
    } finally {
      setSaveLoading(false);
    }
  };

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>, isNewVersion = false) => {
    const file = e.target.files?.[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = (event) => {
      const text = event.target?.result as string;
      if (isNewVersion) {
        setVersionContent(text);
      } else {
        setNewContent(text);
        if (!newTitle) {
          setNewTitle(file.name.replace(/\.[^/.]+$/, ""));
        }
      }
    };
    reader.readAsText(file);
  };

  return (
    <div className="space-y-4 h-[calc(100vh-140px)] flex flex-col">
      {/* Header Bar */}
      <div className="flex items-center justify-between shrink-0">
        <div>
          <h1 className="text-xl font-semibold text-text-primary tracking-tight">Original Artifacts</h1>
          <p className="text-xs text-text-secondary mt-0.5">
            Append-only, SHA-256 verified architecture documents, design specifications, and logs.
          </p>
        </div>
        <Button onClick={() => setSaveDialogOpen(true)} className="gap-1.5">
          <Plus className="h-4 w-4" /> Save to Project
        </Button>
      </div>

      {loading ? (
        <Skeleton className="h-full w-full rounded-panel" />
      ) : artifacts.length === 0 ? (
        <EmptyState
          icon={FileCode2}
          title="No artifacts stored yet"
          description="Save an architecture document, requirements specification, or API schema. Every version is immutable and protected by database triggers."
          actionLabel="Save Document"
          onAction={() => setSaveDialogOpen(true)}
        />
      ) : (
        <div className="flex-1 min-h-0">
          <SplitPane
            left={
              <div className="flex flex-col h-full">
                {/* Search in left pane */}
                <div className="p-2 border-b border-border bg-subtle/40">
                  <div className="relative">
                    <Search className="absolute left-2.5 top-2.5 h-3.5 w-3.5 text-text-tertiary" />
                    <Input
                      value={search}
                      onChange={(e) => setSearch(e.target.value)}
                      placeholder="Search artifacts…"
                      className="pl-8 h-8 text-xs bg-canvas"
                    />
                  </div>
                </div>

                {/* List */}
                <div className="flex-1 overflow-y-auto divide-y divide-border/60">
                  {artifacts.map((a) => {
                    const isSelected = selectedArtifact?.id === a.id;
                    return (
                      <div
                        key={a.id}
                        onClick={() => setSelectedArtifact(a)}
                        className={`p-3 cursor-pointer transition-colors select-none text-xs ${
                          isSelected
                            ? "bg-canvas border-l-2 border-l-accent shadow-lift"
                            : "hover:bg-subtle/60"
                        }`}
                      >
                        <div className="flex items-center justify-between mb-1">
                          <span className="font-semibold text-text-primary truncate max-w-[200px]">
                            {a.title}
                          </span>
                          <Badge variant="neutral" size="sm" showDot={false} className="font-mono text-[10px]">
                            v{a.currentVersion}
                          </Badge>
                        </div>
                        <div className="flex items-center justify-between text-text-tertiary text-[11px]">
                          <span>{a.artifactType}</span>
                          <span>{formatBytes(a.byteSize)}</span>
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>
            }
            right={
              selectedArtifact ? (
                <div className="flex flex-col h-full overflow-hidden">
                  {/* Top Bar for Selected Artifact */}
                  <div className="p-4 border-b border-border flex flex-wrap items-center justify-between gap-3 bg-subtle/30 shrink-0">
                    <div>
                      <div className="flex items-center gap-2 mb-1">
                        <span className="text-sm font-semibold text-text-primary">
                          {selectedArtifact.title}
                        </span>
                        <Badge variant="neutral" size="sm" showDot={false}>
                          {selectedArtifact.artifactType}
                        </Badge>
                        <Badge variant="success" size="sm">
                          Hash Verified
                        </Badge>
                      </div>
                      <div className="flex items-center gap-3 text-xs text-text-tertiary font-mono">
                        <span>SHA: {currentHash.substring(0, 16)}…</span>
                        <span>•</span>
                        <span>Created: {formatDate(selectedArtifact.createdAt)}</span>
                      </div>
                    </div>

                    <div className="flex items-center gap-2">
                      {/* Compare toggle */}
                      {versions.length >= 2 && (
                        <div className="flex items-center gap-1.5 bg-canvas border border-border rounded-control p-1 text-xs">
                          <select
                            value={compareV1}
                            onChange={(e) => setCompareV1(Number(e.target.value))}
                            className="text-xs bg-transparent font-mono"
                          >
                            {versions.map((v) => (
                              <option key={v.version} value={v.version}>
                                v{v.version}
                              </option>
                            ))}
                          </select>
                          <span className="text-text-tertiary font-mono">vs</span>
                          <select
                            value={compareV2}
                            onChange={(e) => setCompareV2(Number(e.target.value))}
                            className="text-xs bg-transparent font-mono"
                          >
                            {versions.map((v) => (
                              <option key={v.version} value={v.version}>
                                v{v.version}
                              </option>
                            ))}
                          </select>
                          <Button
                            variant={compareMode ? "subtle" : "secondary"}
                            size="sm"
                            onClick={() => {
                              if (compareMode) {
                                setCompareMode(false);
                              } else {
                                handleRunDiff();
                              }
                            }}
                            className="h-6 px-2 text-xs"
                          >
                            <GitCompare className="h-3 w-3 mr-1" />
                            {compareMode ? "Exit Diff" : "Compare"}
                          </Button>
                        </div>
                      )}

                      <Button
                        variant="secondary"
                        size="sm"
                        onClick={() => {
                          setVersionContent(currentContent);
                          setNewVersionDialogOpen(true);
                        }}
                      >
                        <Plus className="h-3.5 w-3.5 mr-1" /> New Version
                      </Button>
                    </div>
                  </div>

                  {/* Version Pills Bar */}
                  <div className="px-4 py-2 border-b border-border bg-subtle/10 flex items-center gap-1 overflow-x-auto shrink-0">
                    <span className="text-[11px] font-medium text-text-tertiary mr-2 flex items-center gap-1">
                      <History className="h-3 w-3" /> Versions:
                    </span>
                    {versions.map((v) => (
                      <button
                        key={v.version}
                        onClick={() => handleSelectVersion(v.version)}
                        className={`px-2 py-0.5 rounded text-xs font-mono transition-colors ${
                          selectedVersionNum === v.version && !compareMode
                            ? "bg-accent text-white font-semibold shadow-lift"
                            : "bg-subtle text-text-secondary hover:bg-muted border border-border"
                        }`}
                      >
                        v{v.version} ({formatBytes(v.byteSize)})
                      </button>
                    ))}
                  </div>

                  {/* Content or Diff Body */}
                  <div className="flex-1 overflow-auto p-4 bg-canvas">
                    {contentLoading || diffLoading ? (
                      <div className="space-y-3">
                        <Skeleton className="h-6 w-1/4" />
                        <Skeleton className="h-40 w-full" />
                      </div>
                    ) : compareMode && diffResult ? (
                      <DiffViewer diff={diffResult} />
                    ) : (
                      <CodeBlock
                        code={currentContent}
                        title={`${selectedArtifact.title} (version ${selectedVersionNum})`}
                        hash={currentHash}
                        language={selectedArtifact.artifactType.toLowerCase()}
                        maxHeight="calc(100vh - 320px)"
                      />
                    )}
                  </div>
                </div>
              ) : (
                <div className="flex items-center justify-center h-full text-text-tertiary text-xs">
                  Select an artifact to inspect its original verified contents.
                </div>
              )
            }
          />
        </div>
      )}

      {/* Save to Project Dialog */}
      <Dialog open={saveDialogOpen} onOpenChange={setSaveDialogOpen}>
        <DialogContent className="max-w-2xl">
          <form onSubmit={handleSaveNewArtifact}>
            <DialogHeader>
              <DialogTitle>Save Document to Project Memory</DialogTitle>
              <DialogDescription>
                Store architecture documents, design decisions, or API schemas. The original is stored
                immutably with a SHA-256 cryptographic hash.
              </DialogDescription>
            </DialogHeader>

            <div className="space-y-4 py-4 text-xs">
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block font-medium text-text-primary mb-1">
                    Document Title <span className="text-danger">*</span>
                  </label>
                  <Input
                    value={newTitle}
                    onChange={(e) => setNewTitle(e.target.value)}
                    placeholder="e.g. System Architecture Specification v1"
                    required
                  />
                </div>
                <div>
                  <label className="block font-medium text-text-primary mb-1">Artifact Type</label>
                  <select
                    value={newType}
                    onChange={(e) => setNewType(e.target.value)}
                    className="h-8 w-full rounded-control border border-border bg-canvas px-3 text-xs text-text-primary"
                  >
                    <option value="ARCHITECTURE">ARCHITECTURE</option>
                    <option value="REQUIREMENTS">REQUIREMENTS</option>
                    <option value="DECISION_DOC">DECISION_DOC</option>
                    <option value="CODE">CODE / SCRIPT</option>
                    <option value="DESIGN">DESIGN / UI</option>
                    <option value="GENERAL">GENERAL</option>
                  </select>
                </div>
              </div>

              <div>
                <div className="flex items-center justify-between mb-1">
                  <label className="font-medium text-text-primary">
                    Content (Markdown or Plain Text) <span className="text-danger">*</span>
                  </label>
                  <label className="cursor-pointer text-accent hover:underline flex items-center gap-1">
                    <Upload className="h-3 w-3" /> Upload File
                    <input
                      type="file"
                      className="hidden"
                      onChange={(e) => handleFileUpload(e, false)}
                    />
                  </label>
                </div>
                <textarea
                  value={newContent}
                  onChange={(e) => setNewContent(e.target.value)}
                  placeholder="Paste complete original architecture document or text here…"
                  rows={12}
                  className="w-full font-mono text-xs rounded-control border border-border bg-subtle/30 p-3 leading-relaxed text-text-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent"
                  required
                />
              </div>

              <div className="flex items-center gap-1.5 text-text-tertiary">
                <ShieldCheck className="h-3.5 w-3.5 text-success" />
                <span>SHA-256 computed on write and verified on every read. Append-only trigger enforced.</span>
              </div>
            </div>

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setSaveDialogOpen(false)}>
                Cancel
              </Button>
              <Button type="submit" disabled={saveLoading}>
                {saveLoading ? "Saving…" : "Save Artifact"}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {/* New Version Dialog */}
      <Dialog open={newVersionDialogOpen} onOpenChange={setNewVersionDialogOpen}>
        <DialogContent className="max-w-2xl">
          <form onSubmit={handleCreateNewVersion}>
            <DialogHeader>
              <DialogTitle>Append New Version to '{selectedArtifact?.title}'</DialogTitle>
              <DialogDescription>
                Creates version {(selectedArtifact?.currentVersion || 1) + 1}. Previous versions remain
                untouched and byte-identical.
              </DialogDescription>
            </DialogHeader>

            <div className="space-y-4 py-4 text-xs">
              <div>
                <label className="block font-medium text-text-primary mb-1">Version Summary / Notes</label>
                <Input
                  value={versionSummary}
                  onChange={(e) => setVersionSummary(e.target.value)}
                  placeholder="e.g. Added database schema section and security boundaries"
                />
              </div>

              <div>
                <div className="flex items-center justify-between mb-1">
                  <label className="font-medium text-text-primary">Content</label>
                  <label className="cursor-pointer text-accent hover:underline flex items-center gap-1">
                    <Upload className="h-3 w-3" /> Upload Updated File
                    <input
                      type="file"
                      className="hidden"
                      onChange={(e) => handleFileUpload(e, true)}
                    />
                  </label>
                </div>
                <textarea
                  value={versionContent}
                  onChange={(e) => setVersionContent(e.target.value)}
                  rows={12}
                  className="w-full font-mono text-xs rounded-control border border-border bg-subtle/30 p-3 leading-relaxed text-text-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent"
                  required
                />
              </div>
            </div>

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setNewVersionDialogOpen(false)}>
                Cancel
              </Button>
              <Button type="submit" disabled={saveLoading}>
                {saveLoading ? "Appending…" : "Append Version"}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  );
}

