import React from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { Table, TableHeader, TableBody, TableHead, TableRow, TableCell } from "@/components/ui/table";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/tabs";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/ui/empty-state";
import { CodeBlock } from "@/components/ui/code-block";
import { DiffViewer } from "@/components/ui/diff-viewer";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Palette, Box, Layers, CheckCircle2, ShieldCheck } from "lucide-react";

export function DesignShowcasePage() {
  const [dialogOpen, setDialogOpen] = React.useState(false);

  const colors = [
    { name: "canvas", hex: "#FFFFFF", token: "var(--color-canvas)" },
    { name: "subtle", hex: "#F7F8FA", token: "var(--color-subtle)" },
    { name: "muted", hex: "#EFF1F4", token: "var(--color-muted)" },
    { name: "border", hex: "#E3E6EA", token: "var(--color-border)" },
    { name: "border-strong", hex: "#CDD2D9", token: "var(--color-border-strong)" },
    { name: "text-primary", hex: "#1B1F24", token: "var(--color-text-primary)" },
    { name: "text-secondary", hex: "#5B6470", token: "var(--color-text-secondary)" },
    { name: "text-tertiary", hex: "#8A93A0", token: "var(--color-text-tertiary)" },
    { name: "accent", hex: "#4F5BD5", token: "var(--color-accent)" },
    { name: "accent-hover", hex: "#4350C4", token: "var(--color-accent-hover)" },
    { name: "accent-subtle", hex: "#EEF0FD", token: "var(--color-accent-subtle)" },
    { name: "success", hex: "#1F8A4C", token: "var(--color-success)" },
    { name: "warning", hex: "#B26A00", token: "var(--color-warning)" },
    { name: "danger", hex: "#D0343B", token: "var(--color-danger)" },
    { name: "info", hex: "#1F6FEB", token: "var(--color-info)" },
  ];

  const sampleDiff = {
    artifactId: "sample",
    v1: 1,
    v2: 2,
    v1Hash: "a1b2c3d4e5f6g7h8",
    v2Hash: "z9y8x7w6v5u4t3s2",
    insertions: 2,
    deletions: 1,
    modifications: 0,
    lines: [
      { type: "EQUAL" as const, oldLineNumber: 1, newLineNumber: 1, content: "## Unified Engineering Platform" },
      { type: "DELETE" as const, oldLineNumber: 2, content: "- Architecture: Single node Postgres" },
      { type: "INSERT" as const, newLineNumber: 2, content: "- Architecture: PostgreSQL 16 with pgvector" },
      { type: "INSERT" as const, newLineNumber: 3, content: "- Row-Level Security: Active on all project tables" },
      { type: "EQUAL" as const, oldLineNumber: 3, newLineNumber: 4, content: "- Immutability: Append-only triggers enforced" },
    ],
  };

  return (
    <div className="space-y-10 pb-12 max-w-4xl mx-auto">
      <div>
        <div className="flex items-center gap-2 mb-1">
          <Palette className="h-5 w-5 text-accent" />
          <h1 className="text-2xl font-bold tracking-tight text-text-primary">Design System Showcase</h1>
        </div>
        <p className="text-xs text-text-secondary">
          Living reference of all design tokens, typography, shape parameters, and Radix UI components
          per <code className="font-mono bg-subtle px-1 py-0.5 rounded border border-border">docs/DESIGN_SYSTEM.md</code>.
        </p>
      </div>

      {/* 1. Color Tokens */}
      <section className="space-y-3">
        <h2 className="text-sm font-semibold text-text-primary uppercase tracking-wider pb-1 border-b border-border">
          1. Color Palette Tokens
        </h2>
        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-5 gap-3">
          {colors.map((c) => (
            <div key={c.name} className="rounded-panel border border-border p-2.5 bg-canvas shadow-lift">
              <div
                className="h-10 w-full rounded-control border border-border/40 mb-2"
                style={{ backgroundColor: c.token }}
              />
              <span className="block text-xs font-semibold text-text-primary">{c.name}</span>
              <span className="block font-mono text-[10px] text-text-tertiary">{c.hex}</span>
            </div>
          ))}
        </div>
      </section>

      {/* 2. Status Pills */}
      <section className="space-y-3">
        <h2 className="text-sm font-semibold text-text-primary uppercase tracking-wider pb-1 border-b border-border">
          2. Status Pills (Always Text + Dot)
        </h2>
        <div className="flex flex-wrap gap-2.5 items-center">
          <Badge variant="success">Connected</Badge>
          <Badge variant="neutral">Not connected</Badge>
          <Badge variant="default">Not available yet</Badge>
          <Badge variant="info">Syncing</Badge>
          <Badge variant="warning">Proposed</Badge>
          <Badge variant="success">Approved</Badge>
          <Badge variant="danger">Error</Badge>
          <Badge variant="accent">Implemented</Badge>
        </div>
      </section>

      {/* 3. Button Component */}
      <section className="space-y-3">
        <h2 className="text-sm font-semibold text-text-primary uppercase tracking-wider pb-1 border-b border-border">
          3. Button Variants & Sizes
        </h2>
        <div className="flex flex-wrap gap-3 items-center">
          <Button variant="default">Primary Default</Button>
          <Button variant="secondary">Secondary</Button>
          <Button variant="outline">Outline</Button>
          <Button variant="ghost">Ghost</Button>
          <Button variant="danger">Danger</Button>
          <Button variant="subtle">Accent Subtle</Button>
          <Button size="sm">Small (28px)</Button>
          <Button size="default">Default (32px)</Button>
          <Button size="lg">Large (36px)</Button>
          <Button disabled>Disabled</Button>
        </div>
      </section>

      {/* 4. Inputs & Forms */}
      <section className="space-y-3">
        <h2 className="text-sm font-semibold text-text-primary uppercase tracking-wider pb-1 border-b border-border">
          4. Inputs & Controls
        </h2>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
          <Input placeholder="Standard Input…" />
          <Input placeholder="Input with error…" error />
          <Input placeholder="Disabled Input…" disabled />
        </div>
      </section>

      {/* 5. Dense Table (36px row height) */}
      <section className="space-y-3">
        <h2 className="text-sm font-semibold text-text-primary uppercase tracking-wider pb-1 border-b border-border">
          5. Dense Table (36px row height)
        </h2>
        <div className="rounded-panel border border-border overflow-hidden">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Public ID</TableHead>
                <TableHead>Title</TableHead>
                <TableHead>Lifecycle State</TableHead>
                <TableHead>Actor</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              <TableRow>
                <TableCell className="font-mono">DEC-001</TableCell>
                <TableCell className="font-semibold">Adopt pgvector for RAG embeddings</TableCell>
                <TableCell><Badge variant="success">APPROVED</Badge></TableCell>
                <TableCell className="text-text-secondary">engineer@team.com</TableCell>
              </TableRow>
              <TableRow>
                <TableCell className="font-mono">DEC-002</TableCell>
                <TableCell className="font-semibold">Spring JDBC instead of JPA</TableCell>
                <TableCell><Badge variant="success">APPROVED</Badge></TableCell>
                <TableCell className="text-text-secondary">engineer@team.com</TableCell>
              </TableRow>
              <TableRow>
                <TableCell className="font-mono">DEC-003</TableCell>
                <TableCell className="font-semibold">Argon2id for password hashing</TableCell>
                <TableCell><Badge variant="warning">PROPOSED</Badge></TableCell>
                <TableCell className="text-text-secondary">agent-claude</TableCell>
              </TableRow>
            </TableBody>
          </Table>
        </div>
      </section>

      {/* 6. Tabs */}
      <section className="space-y-3">
        <h2 className="text-sm font-semibold text-text-primary uppercase tracking-wider pb-1 border-b border-border">
          6. Tabs Primitive
        </h2>
        <Tabs defaultValue="architecture">
          <TabsList>
            <TabsTrigger value="architecture">Architecture</TabsTrigger>
            <TabsTrigger value="database">Database Schema</TabsTrigger>
            <TabsTrigger value="security">Security Rules</TabsTrigger>
          </TabsList>
          <TabsContent value="architecture" className="p-4 rounded-panel border border-border bg-subtle/20 text-xs">
            Modular monolith with strict package boundaries: project, auth, artifact, decision, storage, event.
          </TabsContent>
          <TabsContent value="database" className="p-4 rounded-panel border border-border bg-subtle/20 text-xs">
            PostgreSQL 16 with Row-Level Security and Flyway migrations.
          </TabsContent>
          <TabsContent value="security" className="p-4 rounded-panel border border-border bg-subtle/20 text-xs">
            Argon2id password hashing, HTTP-only cookies, and optimistic locking.
          </TabsContent>
        </Tabs>
      </section>

      {/* 7. Dialog */}
      <section className="space-y-3">
        <h2 className="text-sm font-semibold text-text-primary uppercase tracking-wider pb-1 border-b border-border">
          7. Dialog Primitive
        </h2>
        <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
          <DialogTrigger asChild>
            <Button variant="secondary">Open Sample Dialog</Button>
          </DialogTrigger>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>Sample Dialog Component</DialogTitle>
              <DialogDescription>
                Radix UI primitive styled with 8px radius, calm light colors, and popover shadow.
              </DialogDescription>
            </DialogHeader>
            <div className="py-4 text-xs text-text-secondary">
              This dialog meets WCAG AA contrast standards and supports complete keyboard navigation (Escape to close).
            </div>
            <DialogFooter>
              <Button variant="outline" onClick={() => setDialogOpen(false)}>Cancel</Button>
              <Button onClick={() => setDialogOpen(false)}>Confirm</Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      </section>

      {/* 8. Code Block */}
      <section className="space-y-3">
        <h2 className="text-sm font-semibold text-text-primary uppercase tracking-wider pb-1 border-b border-border">
          8. CodeBlock (JetBrains Mono + Hash display)
        </h2>
        <CodeBlock
          title="application.yml"
          hash="4f5bd5aethersecretkeyforphaseonefoundations"
          language="yaml"
          code={`spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/aether
  flyway:
    enabled: true
aether:
  security:
    argon2: true`}
        />
      </section>

      {/* 9. Structured Diff Viewer */}
      <section className="space-y-3">
        <h2 className="text-sm font-semibold text-text-primary uppercase tracking-wider pb-1 border-b border-border">
          9. Structured Diff Viewer
        </h2>
        <DiffViewer diff={sampleDiff} />
      </section>

      {/* 10. Skeletons */}
      <section className="space-y-3">
        <h2 className="text-sm font-semibold text-text-primary uppercase tracking-wider pb-1 border-b border-border">
          10. Skeleton Loader
        </h2>
        <div className="space-y-2 max-w-sm">
          <Skeleton className="h-4 w-3/4" />
          <Skeleton className="h-4 w-1/2" />
          <Skeleton className="h-8 w-1/3" />
        </div>
      </section>

      {/* 11. Empty State */}
      <section className="space-y-3">
        <h2 className="text-sm font-semibold text-text-primary uppercase tracking-wider pb-1 border-b border-border">
          11. Empty State Pattern
        </h2>
        <EmptyState
          icon={Box}
          title="No tasks active"
          description="Every empty state clearly explains what this section is and offers the one real next action."
          actionLabel="Create Task"
          onAction={() => alert("Real action clicked")}
        />
      </section>
    </div>
  );
}
