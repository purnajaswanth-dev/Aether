import React from "react";
import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { AppLayout } from "./components/layout/app-layout";
import { WelcomePage } from "./pages/WelcomePage";
import { ProjectsListPage } from "./pages/ProjectsListPage";
import { CreateProjectPage } from "./pages/CreateProjectPage";
import { ProjectOverviewPage } from "./pages/ProjectOverviewPage";
import { ArtifactsPage } from "./pages/ArtifactsPage";
import { DecisionsPage } from "./pages/DecisionsPage";
import { TimelinePage } from "./pages/TimelinePage";
import { IntegrationsPage } from "./pages/IntegrationsPage";
import { SharedMemoryPage } from "./pages/SharedMemoryPage";
import { SettingsPage } from "./pages/SettingsPage";
import { DesignShowcasePage } from "./pages/DesignShowcasePage";
import { AuthPage } from "./pages/AuthPages";
import { api } from "./services/api";
import { User } from "./types";

const queryClient = new QueryClient();

export function App() {
  const [currentUser, setCurrentUser] = React.useState<User | null>(null);
  const [loading, setLoading] = React.useState(true);

  React.useEffect(() => {
    api
      .me()
      .then((user) => setCurrentUser(user))
      .catch(() => setCurrentUser(null))
      .finally(() => setLoading(false));
  }, []);

  const handleLogout = async () => {
    try {
      await api.logout();
    } finally {
      setCurrentUser(null);
    }
  };

  if (loading) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-canvas">
        <div className="flex h-10 w-10 items-center justify-center rounded-control bg-accent text-white font-bold text-lg animate-pulse">
          Æ
        </div>
      </div>
    );
  }

  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <Routes>
          {/* Public Welcome & Auth */}
          <Route path="/" element={<WelcomePage />} />
          <Route
            path="/login"
            element={<AuthPage onAuthSuccess={(user) => setCurrentUser(user)} />}
          />
          <Route
            path="/register"
            element={<AuthPage onAuthSuccess={(user) => setCurrentUser(user)} isRegister />}
          />

          {/* Main App Layout */}
          <Route
            element={
              <AppLayout
                userEmail={currentUser?.email}
                onLogout={handleLogout}
              />
            }
          >
            <Route path="/projects" element={<ProjectsListPage />} />
            <Route path="/projects/new" element={<CreateProjectPage />} />
            <Route path="/projects/:projectId" element={<ProjectOverviewPage />} />
            <Route path="/projects/:projectId/memory" element={<SharedMemoryPage />} />
            <Route path="/projects/:projectId/artifacts" element={<ArtifactsPage />} />
            <Route path="/projects/:projectId/decisions" element={<DecisionsPage />} />
            <Route path="/projects/:projectId/timeline" element={<TimelinePage />} />
            <Route path="/projects/:projectId/integrations" element={<IntegrationsPage />} />
            <Route path="/projects/:projectId/settings" element={<SettingsPage />} />
            <Route path="/design" element={<DesignShowcasePage />} />
          </Route>

          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </QueryClientProvider>
  );
}

export default App;
