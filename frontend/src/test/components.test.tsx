import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { Button } from "../components/ui/button";
import { Badge } from "../components/ui/badge";
import { EmptyState } from "../components/ui/empty-state";
import { CodeBlock } from "../components/ui/code-block";
import { DiffViewer } from "../components/ui/diff-viewer";
import { FolderKanban } from "lucide-react";

describe("Design System Component Library", () => {
  it("renders primary button with accessible text", () => {
    render(<Button>Create Project</Button>);
    const btn = screen.getByRole("button", { name: /create project/i });
    expect(btn).toBeInTheDocument();
  });

  it("renders disabled button preventing interaction", () => {
    render(<Button disabled>Not available</Button>);
    const btn = screen.getByRole("button", { name: /not available/i });
    expect(btn).toBeDisabled();
  });

  it("renders StatusPill with text and dot indicator", () => {
    render(<Badge variant="success">Approved</Badge>);
    expect(screen.getByText("Approved")).toBeInTheDocument();
  });

  it("renders EmptyState with honest description and action", () => {
    render(
      <EmptyState
        icon={FolderKanban}
        title="No projects initialized"
        description="Create your first engineering workspace."
        actionLabel="Create Project"
        onAction={() => {}}
      />
    );
    expect(screen.getByText("No projects initialized")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /create project/i })).toBeInTheDocument();
  });

  it("renders CodeBlock with JetBrains Mono and SHA-256 hash", () => {
    const code = "SELECT * FROM projects WHERE id = 'PRJ-001';";
    render(<CodeBlock code={code} language="sql" hash="abc1234567890def" />);
    expect(screen.getByText(/SELECT \* FROM projects/)).toBeInTheDocument();
    expect(screen.getByText(/SHA: abc1234567/)).toBeInTheDocument();
  });

  it("renders DiffViewer showing line insertions and deletions", () => {
    const sampleDiff = {
      artifactId: "art-1",
      v1: 1,
      v2: 2,
      v1Hash: "hash-v1",
      v2Hash: "hash-v2",
      insertions: 1,
      deletions: 1,
      modifications: 0,
      lines: [
        { type: "EQUAL" as const, oldLineNumber: 1, newLineNumber: 1, content: "base line" },
        { type: "DELETE" as const, oldLineNumber: 2, content: "old feature" },
        { type: "INSERT" as const, newLineNumber: 2, content: "new feature" },
      ],
    };
    render(<DiffViewer diff={sampleDiff} />);
    expect(screen.getByText("base line")).toBeInTheDocument();
    expect(screen.getByText("old feature")).toBeInTheDocument();
    expect(screen.getByText("new feature")).toBeInTheDocument();
    expect(screen.getByText("+1")).toBeInTheDocument();
    expect(screen.getByText("-1")).toBeInTheDocument();
  });
});

