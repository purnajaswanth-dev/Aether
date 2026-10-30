import React from "react";
import { Outlet, useNavigate, useParams } from "react-router-dom";
import { Sidebar } from "./sidebar";
import { Breadcrumbs } from "./breadcrumbs";
import { Button } from "@/components/ui/button";
import {
  CommandDialog,
  CommandInput,
  CommandList,
  CommandEmpty,
  CommandGroup,
  CommandItem,
} from "@/components/ui/command-palette";
import { Search, Plus, FileCode2, Scale, Plug, FolderKanban, Palette } from "lucide-react";
import { api } from "@/services/api";

interface AppLayoutProps {
  userEmail?: string;
  onLogout: () => void;
  projectName?: string;
}

export function AppLayout({ userEmail, onLogout, projectName }: AppLayoutProps) {
  const [collapsed, setCollapsed] = React.useState(false);
  const [cmdOpen, setCmdOpen] = React.useState(false);
  const navigate = useNavigate();
  const { projectId } = useParams<{ projectId: string }>();

  return (
    <div className="flex min-h-screen bg-canvas text-text-primary antialiased font-sans">
      <Sidebar
        collapsed={collapsed}
        onToggleCollapse={() => setCollapsed(!collapsed)}
        userEmail={userEmail}
        onLogout={onLogout}
      />

      <div className="flex flex-1 flex-col overflow-x-hidden min-w-0">
        {/* Slim Top Bar */}
        <header className="flex h-12 items-center justify-between border-b border-border bg-canvas/80 backdrop-blur-xs px-6 sticky top-0 z-40">
          <Breadcrumbs projectName={projectName} />

          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              onClick={() => setCmdOpen(true)}
              className="text-xs text-text-tertiary hover:text-text-primary h-7 px-2.5 font-normal flex items-center gap-2 border-border/80"
            >
              <Search className="h-3.5 w-3.5" />
              <span>Search or jump to…</span>
              <kbd className="rounded bg-muted px-1.5 py-0.5 text-[10px] font-mono text-text-secondary border border-border">
                ⌘K
              </kbd>
            </Button>
          </div>
        </header>

        {/* Main Content Area (max-width 1200px) */}
        <main className="flex-1 p-6 md:p-8 max-w-[1200px] w-full mx-auto">
          <Outlet />
        </main>
      </div>

      {/* Cmd/Ctrl+K Command Palette */}
      <CommandDialog open={cmdOpen} onOpenChange={setCmdOpen}>
        <CommandInput placeholder="Type a command, project, or section to navigate…" />
        <CommandList>
          <CommandEmpty>No matching items found.</CommandEmpty>
          <CommandGroup heading="Quick Actions">
            <CommandItem
              onSelect={() => {
                setCmdOpen(false);
                navigate("/projects/new");
              }}
            >
              <Plus className="mr-2 h-4 w-4 text-accent" />
              Create New Project
            </CommandItem>
            {projectId && (
              <>
                <CommandItem
                  onSelect={() => {
                    setCmdOpen(false);
                    navigate(`/projects/${projectId}/artifacts`);
                  }}
                >
                  <FileCode2 className="mr-2 h-4 w-4 text-info" />
                  Save or Browse Artifacts
                </CommandItem>
                <CommandItem
                  onSelect={() => {
                    setCmdOpen(false);
                    navigate(`/projects/${projectId}/decisions`);
                  }}
                >
                  <Scale className="mr-2 h-4 w-4 text-warning" />
                  Review Technical Decisions
                </CommandItem>
                <CommandItem
                  onSelect={() => {
                    setCmdOpen(false);
                    navigate(`/projects/${projectId}/integrations`);
                  }}
                >
                  <Plug className="mr-2 h-4 w-4 text-success" />
                  View Integrations Catalogue
                </CommandItem>
              </>
            )}
          </CommandGroup>
          <CommandGroup heading="Navigation">
            <CommandItem
              onSelect={() => {
                setCmdOpen(false);
                navigate("/projects");
              }}
            >
              <FolderKanban className="mr-2 h-4 w-4" />
              All Projects
            </CommandItem>
            <CommandItem
              onSelect={() => {
                setCmdOpen(false);
                navigate("/design");
              }}
            >
              <Palette className="mr-2 h-4 w-4" />
              Design System Showcase (/design)
            </CommandItem>
          </CommandGroup>
        </CommandList>
      </CommandDialog>
    </div>
  );
}
