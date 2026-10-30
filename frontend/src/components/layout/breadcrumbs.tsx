import { Link, useLocation } from "react-router-dom";
import { ChevronRight } from "lucide-react";

export function Breadcrumbs({ projectName }: { projectName?: string }) {
  const location = useLocation();
  const segments = location.pathname.split("/").filter(Boolean);

  if (segments.length === 0) return null;

  return (
    <nav className="flex items-center space-x-1.5 text-xs text-text-secondary select-none">
      <Link to="/projects" className="hover:text-text-primary transition-colors">
        Projects
      </Link>
      {segments[0] === "projects" && segments[1] && (
        <>
          <ChevronRight className="h-3 w-3 text-text-tertiary" />
          <Link
            to={`/projects/${segments[1]}`}
            className="hover:text-text-primary font-medium text-text-primary transition-colors truncate max-w-[160px]"
          >
            {projectName || segments[1]}
          </Link>
        </>
      )}
      {segments[2] && (
        <>
          <ChevronRight className="h-3 w-3 text-text-tertiary" />
          <span className="capitalize text-text-primary font-medium">
            {segments[2]}
          </span>
        </>
      )}
      {segments[0] === "design" && (
        <>
          <ChevronRight className="h-3 w-3 text-text-tertiary" />
          <span className="text-text-primary font-medium">Design System</span>
        </>
      )}
    </nav>
  );
}
