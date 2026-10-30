import { Link, useNavigate } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { FolderPlus, FolderOpen, BookOpen, Plug, Settings, ShieldCheck } from "lucide-react";

export function WelcomePage() {
  const navigate = useNavigate();

  return (
    <div className="flex min-h-[80vh] flex-col items-center justify-center text-center max-w-2xl mx-auto px-4 select-none">
      <div className="flex h-14 w-14 items-center justify-center rounded-panel bg-accent text-white font-bold text-2xl shadow-lift mb-6">
        Æ
      </div>

      <h1 className="text-2xl md:text-3xl font-semibold tracking-tight text-text-primary">
        Welcome to your unified engineering workspace
      </h1>

      <p className="mt-3 text-sm md:text-base text-text-secondary leading-relaxed">
        One project, one persistent memory, many independent AI agents and engineering tools
        (ChatGPT, Claude, Google Antigravity, GitHub, Render, Postman, Docker). The project, not the
        AI conversation, is the central unit.
      </p>

      {/* Main Call to Actions */}
      <div className="mt-8 flex flex-wrap items-center justify-center gap-3">
        <Button size="lg" onClick={() => navigate("/projects/new")} className="gap-2">
          <FolderPlus className="h-4 w-4" />
          Create Project
        </Button>
        <Button
          size="lg"
          variant="secondary"
          onClick={() => navigate("/projects/new?mode=local")}
          className="gap-2"
        >
          <FolderOpen className="h-4 w-4" />
          Connect Existing Project
        </Button>
      </div>

      {/* Trust & Transparency Links */}
      <div className="mt-12 pt-8 border-t border-border w-full flex flex-wrap items-center justify-center gap-6 text-xs text-text-secondary">
        <Link
          to="/design"
          className="flex items-center gap-1.5 hover:text-text-primary transition-colors"
        >
          <BookOpen className="h-3.5 w-3.5 text-text-tertiary" />
          Documentation & Design
        </Link>
        <Link
          to="/projects"
          className="flex items-center gap-1.5 hover:text-text-primary transition-colors"
        >
          <Plug className="h-3.5 w-3.5 text-text-tertiary" />
          Integrations Catalogue
        </Link>
        <Link
          to="/projects"
          className="flex items-center gap-1.5 hover:text-text-primary transition-colors"
        >
          <Settings className="h-3.5 w-3.5 text-text-tertiary" />
          Workspace Settings
        </Link>
        <div className="flex items-center gap-1.5 text-text-tertiary">
          <ShieldCheck className="h-3.5 w-3.5 text-success" />
          <span>RLS Isolated & Append-Only</span>
        </div>
      </div>
    </div>
  );
}
