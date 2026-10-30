import React from "react";
import { Link, useLocation, useParams } from "react-router-dom";
import {
  FolderKanban,
  Brain,
  History,
  FileCode2,
  Plug,
  Settings,
  Scale,
  LayoutDashboard,
  CheckSquare,
  ChevronLeft,
  ChevronRight,
  LogOut,
  Palette,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";

interface SidebarProps {
  collapsed: boolean;
  onToggleCollapse: () => void;
  userEmail?: string;
  onLogout: () => void;
}

export function Sidebar({ collapsed, onToggleCollapse, userEmail, onLogout }: SidebarProps) {
  const location = useLocation();
  const { projectId } = useParams<{ projectId: string }>();

  const isProjectRoute = Boolean(projectId);

  const globalNav = [
    { label: "Projects", path: "/projects", icon: FolderKanban },
    { label: "Design System", path: "/design", icon: Palette },
  ];

  const projectNav = [
    { label: "Overview", path: `/projects/${projectId}`, icon: LayoutDashboard },
    { label: "Shared Memory", path: `/projects/${projectId}/memory`, icon: Brain },
    { label: "Decisions", path: `/projects/${projectId}/decisions`, icon: Scale },
    { label: "Tasks", path: `/projects/${projectId}/tasks`, icon: CheckSquare },
    { label: "Timeline", path: `/projects/${projectId}/timeline`, icon: History },
    { label: "Artifacts", path: `/projects/${projectId}/artifacts`, icon: FileCode2 },
    { label: "Integrations", path: `/projects/${projectId}/integrations`, icon: Plug },
    { label: "Settings", path: `/projects/${projectId}/settings`, icon: Settings },
  ];

  return (
    <aside
      className={cn(
        "flex flex-col border-r border-border bg-subtle/50 transition-all duration-panel ease-standard select-none shrink-0 h-screen sticky top-0",
        collapsed ? "w-16" : "w-60"
      )}
    >
      {/* Brand Header */}
      <div className="flex h-12 items-center justify-between border-b border-border px-3">
        <Link to="/projects" className="flex items-center gap-2 overflow-hidden">
          <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-control bg-accent text-white font-bold text-xs shadow-lift">
            Æ
          </div>
          {!collapsed && (
            <div className="flex flex-col overflow-hidden">
              <span className="font-semibold text-sm leading-tight text-text-primary tracking-tight">
                Aether
              </span>
              <span className="text-[10px] text-text-tertiary leading-none truncate">
                Context Platform
              </span>
            </div>
          )}
        </Link>
        <Button
          variant="ghost"
          size="iconSm"
          onClick={onToggleCollapse}
          className="text-text-tertiary hover:text-text-primary"
          title={collapsed ? "Expand sidebar" : "Collapse sidebar"}
        >
          {collapsed ? <ChevronRight className="h-4 w-4" /> : <ChevronLeft className="h-4 w-4" />}
        </Button>
      </div>

      {/* Nav Items */}
      <div className="flex-1 overflow-y-auto px-2 py-3 space-y-4">
        {isProjectRoute && (
          <div>
            {!collapsed && (
              <div className="px-2 pb-1.5 text-[11px] font-semibold text-text-tertiary uppercase tracking-wider">
                Project
              </div>
            )}
            <nav className="space-y-0.5">
              {projectNav.map((item) => {
                const isActive = location.pathname === item.path;
                const Icon = item.icon;
                return (
                  <Link
                    key={item.path}
                    to={item.path}
                    className={cn(
                      "flex items-center gap-2.5 rounded-control px-2.5 py-1.5 text-xs font-medium transition-colors duration-hover",
                      isActive
                        ? "bg-canvas text-accent font-semibold shadow-lift border border-border/80"
                        : "text-text-secondary hover:bg-canvas/60 hover:text-text-primary"
                    )}
                    title={collapsed ? item.label : undefined}
                  >
                    <Icon className={cn("h-4 w-4 shrink-0", isActive ? "text-accent" : "text-text-secondary")} />
                    {!collapsed && <span className="truncate">{item.label}</span>}
                  </Link>
                );
              })}
            </nav>
          </div>
        )}

        <div>
          {!collapsed && (
            <div className="px-2 pb-1.5 text-[11px] font-semibold text-text-tertiary uppercase tracking-wider">
              Platform
            </div>
          )}
          <nav className="space-y-0.5">
            {globalNav.map((item) => {
              const isActive = location.pathname === item.path;
              const Icon = item.icon;
              return (
                <Link
                  key={item.path}
                  to={item.path}
                  className={cn(
                    "flex items-center gap-2.5 rounded-control px-2.5 py-1.5 text-xs font-medium transition-colors duration-hover",
                    isActive
                      ? "bg-canvas text-accent font-semibold shadow-lift border border-border/80"
                      : "text-text-secondary hover:bg-canvas/60 hover:text-text-primary"
                  )}
                  title={collapsed ? item.label : undefined}
                >
                  <Icon className={cn("h-4 w-4 shrink-0", isActive ? "text-accent" : "text-text-secondary")} />
                  {!collapsed && <span className="truncate">{item.label}</span>}
                </Link>
              );
            })}
          </nav>
        </div>
      </div>

      {/* Footer / User Profile */}
      <div className="border-t border-border p-2">
        <div className="flex items-center justify-between rounded-control p-1.5 bg-subtle/80">
          {!collapsed && (
            <div className="flex flex-col truncate pr-2">
              <span className="text-xs font-medium text-text-primary truncate">
                {userEmail || "Developer"}
              </span>
              <span className="text-[10px] text-text-tertiary">Authenticated</span>
            </div>
          )}
          <Button
            variant="ghost"
            size="iconSm"
            onClick={onLogout}
            title="Log out"
            className="text-text-tertiary hover:text-danger shrink-0"
          >
            <LogOut className="h-4 w-4" />
          </Button>
        </div>
      </div>
    </aside>
  );
}
