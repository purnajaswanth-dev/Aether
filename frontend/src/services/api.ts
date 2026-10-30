import {
  User,
  Project,
  IntegrationStatus,
  Artifact,
  ArtifactVersion,
  ArtifactDiffResponse,
  McpToken,
  CreateMcpTokenRequest,
  CreateMcpTokenResponse,
  McpActivityLog,
  McpReliabilityReport,
  McpClientConfig,
  McpClientPlatform,
  TechnicalDecision,
  DecisionComparison,
  TimelineEvent,
} from '@/types';

const BASE_URL = '/api/v1';

class ApiClient {
  private getToken(): string | null {
    return localStorage.getItem('aether_token');
  }

  public setToken(token: string | null) {
    if (token) {
      localStorage.setItem('aether_token', token);
    } else {
      localStorage.removeItem('aether_token');
    }
  }

  private async request<T>(endpoint: string, options: RequestInit = {}): Promise<T> {
    const token = this.getToken();
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      Accept: 'application/json',
      ...(options.headers as Record<string, string>),
    };

    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }

    const res = await fetch(`${BASE_URL}${endpoint}`, {
      ...options,
      headers,
    });

    if (!res.ok) {
      let errorMessage = `API Error ${res.status}`;
      try {
        const errorJson = await res.json();
        errorMessage = errorJson.message || errorJson.error || errorMessage;
      } catch {
        // ignore parse errors
      }
      throw new Error(errorMessage);
    }

    if (res.status === 204) {
      return {} as T;
    }

    return res.json();
  }

  // Auth
  async register(data: { email: string; password: string; displayName: string }) {
    const res = await this.request<{ user: User; token: string }>('/auth/register', {
      method: 'POST',
      body: JSON.stringify(data),
    });
    this.setToken(res.token);
    return res;
  }

  async login(data: { email: string; password: string }) {
    const res = await this.request<{ user: User; token: string }>('/auth/login', {
      method: 'POST',
      body: JSON.stringify(data),
    });
    this.setToken(res.token);
    return res;
  }

  async me(): Promise<User> {
    return this.request<User>('/auth/me');
  }

  async logout() {
    await this.request('/auth/logout', { method: 'POST' });
    this.setToken(null);
  }

  // Projects
  async listProjects(): Promise<Project[]> {
    return this.request<Project[]>('/projects');
  }

  async getProject(projectId: string): Promise<Project> {
    return this.request<Project>(`/projects/${projectId}`);
  }

  async createProject(data: {
    name: string;
    description?: string;
    projectType: string;
    techStack: string[];
    directory?: string;
    storagePreference: string;
  }): Promise<Project> {
    return this.request<Project>('/projects', {
      method: 'POST',
      body: JSON.stringify(data),
    });
  }

  async updateProject(projectId: string, data: Partial<Project>): Promise<Project> {
    return this.request<Project>(`/projects/${projectId}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    });
  }

  async archiveProject(projectId: string): Promise<void> {
    return this.request<void>(`/projects/${projectId}/archive`, {
      method: 'POST',
    });
  }

  async getIntegrations(projectId: string): Promise<IntegrationStatus[]> {
    return this.request<IntegrationStatus[]>(`/projects/${projectId}/integrations`);
  }

  async getPermissions(projectId: string): Promise<any[]> {
    return this.request<any[]>(`/projects/${projectId}/permissions`);
  }

  // Artifacts
  async listArtifacts(projectId: string, filters?: { type?: string; platform?: string; search?: string }): Promise<Artifact[]> {
    const params = new URLSearchParams();
    if (filters?.type) params.append('type', filters.type);
    if (filters?.platform) params.append('platform', filters.platform);
    if (filters?.search) params.append('search', filters.search);
    return this.request<Artifact[]>(`/projects/${projectId}/artifacts?${params.toString()}`);
  }

  async getLatestArtifact(projectId: string, artifactId: string): Promise<Artifact> {
    return this.request<Artifact>(`/projects/${projectId}/artifacts/${artifactId}/latest`);
  }

  async getArtifactVersion(projectId: string, artifactId: string, version: number): Promise<ArtifactVersion> {
    return this.request<ArtifactVersion>(`/projects/${projectId}/artifacts/${artifactId}/versions/${version}`);
  }

  async listArtifactVersions(projectId: string, artifactId: string): Promise<ArtifactVersion[]> {
    return this.request<ArtifactVersion[]>(`/projects/${projectId}/artifacts/${artifactId}/versions`);
  }

  async createArtifact(projectId: string, data: {
    title: string;
    artifactType: string;
    sourcePlatform: string;
    sourceReference?: string;
    content: string;
    changeSummary?: string;
  }): Promise<Artifact> {
    return this.request<Artifact>(`/projects/${projectId}/artifacts`, {
      method: 'POST',
      body: JSON.stringify(data),
    });
  }

  async createArtifactVersion(projectId: string, artifactId: string, data: {
    content: string;
    changeSummary?: string;
  }): Promise<ArtifactVersion> {
    return this.request<ArtifactVersion>(`/projects/${projectId}/artifacts/${artifactId}/versions`, {
      method: 'POST',
      body: JSON.stringify(data),
    });
  }

  async compareArtifactVersions(projectId: string, artifactId: string, v1: number, v2: number): Promise<ArtifactDiffResponse> {
    return this.request<ArtifactDiffResponse>(`/projects/${projectId}/artifacts/${artifactId}/compare?v1=${v1}&v2=${v2}`);
  }

  // Decisions
  async listDecisions(projectId: string, state?: string, category?: string): Promise<TechnicalDecision[]> {
    const params = new URLSearchParams();
    if (state) params.append('state', state);
    if (category) params.append('category', category);
    return this.request<TechnicalDecision[]>(`/projects/${projectId}/decisions?${params.toString()}`);
  }

  async getDecision(projectId: string, decisionId: string): Promise<{ decision: TechnicalDecision; versions: any[]; evidence: any[] }> {
    return this.request<{ decision: TechnicalDecision; versions: any[]; evidence: any[] }>(`/projects/${projectId}/decisions/${decisionId}`);
  }

  async createDecision(projectId: string, data: {
    title: string;
    category: string;
    problemStatement: string;
    proposedSolution: string;
    rationale?: string;
    consequences?: string;
    sourceArtifactVersionId?: string;
  }): Promise<TechnicalDecision> {
    return this.request<TechnicalDecision>(`/projects/${projectId}/decisions`, {
      method: 'POST',
      body: JSON.stringify(data),
    });
  }

  async submitDecisionReview(projectId: string, decisionId: string, expectedLockVersion: number, notes?: string): Promise<TechnicalDecision> {
    return this.request<TechnicalDecision>(`/projects/${projectId}/decisions/${decisionId}/review`, {
      method: 'POST',
      body: JSON.stringify({ expectedLockVersion, notes }),
    });
  }

  async approveDecision(projectId: string, decisionId: string, expectedLockVersion: number, notes?: string): Promise<TechnicalDecision> {
    return this.request<TechnicalDecision>(`/projects/${projectId}/decisions/${decisionId}/approve`, {
      method: 'POST',
      body: JSON.stringify({ expectedLockVersion, notes }),
    });
  }

  async rejectDecision(projectId: string, decisionId: string, expectedLockVersion: number, notes?: string): Promise<TechnicalDecision> {
    return this.request<TechnicalDecision>(`/projects/${projectId}/decisions/${decisionId}/reject`, {
      method: 'POST',
      body: JSON.stringify({ expectedLockVersion, notes }),
    });
  }

  async supersedeDecision(projectId: string, decisionId: string, expectedLockVersion: number, linkedDecisionId?: string, notes?: string): Promise<TechnicalDecision> {
    return this.request<TechnicalDecision>(`/projects/${projectId}/decisions/${decisionId}/supersede`, {
      method: 'POST',
      body: JSON.stringify({ expectedLockVersion, linkedDecisionId, notes }),
    });
  }

  async compareDecision(projectId: string, decisionId: string): Promise<DecisionComparison> {
    return this.request<DecisionComparison>(`/projects/${projectId}/decisions/${decisionId}/compare`);
  }

  // Timeline
  async getTimeline(projectId: string, eventType?: string, platform?: string): Promise<TimelineEvent[]> {
    const params = new URLSearchParams();
    if (eventType) params.append('eventType', eventType);
    if (platform) params.append('platform', platform);
    return this.request<TimelineEvent[]>(`/projects/${projectId}/timeline?${params.toString()}`);
  }

  // Model Context Protocol (MCP) Management
  async listMcpTokens(projectId: string): Promise<McpToken[]> {
    return this.request<McpToken[]>(`/projects/${projectId}/integrations/mcp/tokens`);
  }

  async createMcpToken(projectId: string, data: CreateMcpTokenRequest): Promise<CreateMcpTokenResponse> {
    return this.request<CreateMcpTokenResponse>(`/projects/${projectId}/integrations/mcp/tokens`, {
      method: 'POST',
      body: JSON.stringify(data),
    });
  }

  async revokeMcpToken(projectId: string, tokenId: string): Promise<void> {
    return this.request<void>(`/projects/${projectId}/integrations/mcp/tokens/${tokenId}/revoke`, {
      method: 'POST',
    });
  }

  async pauseMcpToken(projectId: string, tokenId: string, paused: boolean): Promise<void> {
    return this.request<void>(`/projects/${projectId}/integrations/mcp/tokens/${tokenId}/pause?paused=${paused}`, {
      method: 'POST',
    });
  }

  async getMcpActivity(projectId: string, filters?: { limit?: number; offset?: number; status?: string; tool?: string }): Promise<McpActivityLog[]> {
    const params = new URLSearchParams();
    if (filters?.limit) params.append('limit', filters.limit.toString());
    if (filters?.offset) params.append('offset', filters.offset.toString());
    if (filters?.status) params.append('status', filters.status);
    if (filters?.tool) params.append('tool', filters.tool);
    return this.request<McpActivityLog[]>(`/projects/${projectId}/integrations/mcp/activity?${params.toString()}`);
  }

  async getMcpReliability(projectId: string): Promise<McpReliabilityReport> {
    return this.request<McpReliabilityReport>(`/projects/${projectId}/integrations/mcp/reliability`);
  }

  async getMcpConfig(projectId: string, platform: McpClientPlatform, baseUrl?: string, token?: string): Promise<McpClientConfig> {
    const params = new URLSearchParams({ platform });
    if (baseUrl) params.append('baseUrl', baseUrl);
    if (token) params.append('token', token);
    return this.request<McpClientConfig>(`/projects/${projectId}/integrations/mcp/config?${params.toString()}`);
  }
}

export const api = new ApiClient();

