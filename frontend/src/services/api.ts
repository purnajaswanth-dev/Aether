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
  SearchResultItem,
  ExtractedFact,
  IngestionJob,
  SecretFinding,
  ContextPackageResponse,
  CapabilityDescriptor,
  IntegrationSummary,
  Deployment,
  AgentDevice,
  DeadLetterEvent,
  Task,
  TaskStatus,
  TaskEvent,
  ImplementationRecord,
  TestExecutionRecord,
  DebuggingRecord,
  TaskLink,
  TaskContext,
  ActionRequest,
  AgentHandoff,
  ChangeImpactResult,
  IntegrationHealth,
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
      if (res.status === 401) {
        this.setToken(null);
        if (typeof window !== 'undefined' && !window.location.pathname.startsWith('/login') && !window.location.pathname.startsWith('/register')) {
          window.location.href = '/login?expired=true';
        }
      }
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

  async exportProject(projectId: string): Promise<any> {
    return this.request<any>(`/projects/${projectId}/export`);
  }

  async deleteProject(projectId: string, confirmationCode: string): Promise<{ message: string; projectId: string }> {
    return this.request<{ message: string; projectId: string }>(`/projects/${projectId}`, {
      method: 'DELETE',
      body: JSON.stringify({ confirmationCode }),
    });
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

  // ==========================================
  // Phase 3: Extraction, RAG & Orchestrator API
  // ==========================================

  async searchMemory(projectId: string, query: string, limit: number = 15): Promise<SearchResultItem[]> {
    const params = new URLSearchParams({ query, limit: limit.toString() });
    return this.request<SearchResultItem[]>(`/projects/${projectId}/memory/search?${params.toString()}`);
  }

  async listFacts(
    projectId: string,
    category?: string,
    state?: string,
    limit: number = 50
  ): Promise<ExtractedFact[]> {
    const params = new URLSearchParams({ limit: limit.toString() });
    if (category) params.append('category', category);
    if (state) params.append('state', state);
    return this.request<ExtractedFact[]>(`/projects/${projectId}/memory/facts?${params.toString()}`);
  }

  async reviewFact(
    projectId: string,
    factId: string,
    validationState: 'VERIFIED' | 'REJECTED',
    notes?: string
  ): Promise<ExtractedFact> {
    return this.request<ExtractedFact>(`/projects/${projectId}/memory/facts/${factId}/review`, {
      method: 'POST',
      body: JSON.stringify({ validation_state: validationState, notes }),
    });
  }

  async expandChunk(projectId: string, chunkId: string): Promise<{
    chunk_id: string;
    artifact_id: string;
    version: number;
    parent_section_path: string;
    chunk_type: string;
    start_line: number;
    end_line: number;
    start_offset: number;
    end_offset: number;
    content: string;
    expanded_view: string;
  }> {
    return this.request(`/projects/${projectId}/memory/chunks/${chunkId}/expand`);
  }

  async listIngestionJobs(projectId: string, limit: number = 20): Promise<IngestionJob[]> {
    const params = new URLSearchParams({ limit: limit.toString() });
    return this.request<IngestionJob[]>(`/projects/${projectId}/memory/ingestion-jobs?${params.toString()}`);
  }

  async retryIngestionJob(projectId: string, jobId: string): Promise<IngestionJob> {
    return this.request<IngestionJob>(`/projects/${projectId}/memory/ingestion-jobs/${jobId}/retry`, {
      method: 'POST',
    });
  }

  async listSecrets(projectId: string): Promise<SecretFinding[]> {
    return this.request<SecretFinding[]>(`/projects/${projectId}/memory/secrets`);
  }

  async previewContextPackage(
    projectId: string,
    task: string,
    targetModel?: string,
    tokenBudget?: number
  ): Promise<ContextPackageResponse> {
    return this.request<ContextPackageResponse>(`/projects/${projectId}/orchestrator/preview`, {
      method: 'POST',
      body: JSON.stringify({
        task,
        target_model: targetModel,
        token_budget: tokenBudget,
      }),
    });
  }
  // ==========================================
  // Phase 4: Integrations, Deployments & Local Agent
  // ==========================================

  async getIntegrationCapabilities(projectId: string): Promise<CapabilityDescriptor[]> {
    return this.request<CapabilityDescriptor[]>(`/projects/${projectId}/integrations/capabilities`);
  }

  async listProjectIntegrations(projectId: string): Promise<IntegrationSummary[]> {
    return this.request<IntegrationSummary[]>(`/projects/${projectId}/integrations`);
  }

  async connectIntegration(
    projectId: string,
    data: { provider: string; config: Record<string, any>; credential?: string }
  ): Promise<any> {
    return this.request(`/projects/${projectId}/integrations/connect`, {
      method: 'POST',
      body: JSON.stringify(data),
    });
  }

  async disconnectIntegration(projectId: string, provider: string): Promise<void> {
    return this.request(`/projects/${projectId}/integrations/${provider}/disconnect`, {
      method: 'POST',
    });
  }

  async pauseIntegration(projectId: string, provider: string): Promise<void> {
    return this.request(`/projects/${projectId}/integrations/${provider}/pause`, {
      method: 'POST',
    });
  }

  async resumeIntegration(projectId: string, provider: string): Promise<void> {
    return this.request(`/projects/${projectId}/integrations/${provider}/resume`, {
      method: 'POST',
    });
  }

  async syncIntegration(projectId: string, provider: string): Promise<any> {
    return this.request(`/projects/${projectId}/integrations/${provider}/sync`, {
      method: 'POST',
    });
  }

  async listDeadLetterEvents(projectId: string): Promise<DeadLetterEvent[]> {
    return this.request<DeadLetterEvent[]>(`/projects/${projectId}/integrations/dead-letter`);
  }

  async retryDeadLetterEvent(projectId: string, dlqId: string): Promise<void> {
    return this.request(`/projects/${projectId}/integrations/dead-letter/${dlqId}/retry`, {
      method: 'POST',
    });
  }

  async discardDeadLetterEvent(projectId: string, dlqId: string): Promise<void> {
    return this.request(`/projects/${projectId}/integrations/dead-letter/${dlqId}`, {
      method: 'DELETE',
    });
  }

  async listDeployments(projectId: string, limit: number = 50): Promise<Deployment[]> {
    return this.request<Deployment[]>(`/projects/${projectId}/integrations/deployments?limit=${limit}`);
  }

  async getDeploymentLogs(projectId: string, externalDeployId: string, tailLines: number = 200): Promise<{ deployId: string; logs: string }> {
    return this.request(`/projects/${projectId}/integrations/deployments/${externalDeployId}/logs?tailLines=${tailLines}`);
  }

  async initAgentPair(projectId: string): Promise<{ deviceCode: string; userCode: string; verificationUri: string; expiresIn: number }> {
    return this.request(`/agent/pair/init?projectId=${projectId}`, {
      method: 'POST',
    });
  }

  async confirmAgentPair(projectId: string, userCode: string, hostname?: string): Promise<{ status: string; deviceToken: string }> {
    return this.request(`/projects/${projectId}/agent/confirm`, {
      method: 'POST',
      body: JSON.stringify({ userCode, hostname: hostname || 'local-workstation', os: navigator.platform, localPath: '' }),
    });
  }

  async listAgentDevices(projectId: string): Promise<AgentDevice[]> {
    return this.request<AgentDevice[]>(`/projects/${projectId}/agent/devices`);
  }

  async disconnectAgentDevice(projectId: string, deviceId: string): Promise<void> {
    return this.request(`/projects/${projectId}/agent/devices/${deviceId}`, {
      method: 'DELETE',
    });
  }

  // ==========================================
  // Phase 5: Workflow Coordination
  // ==========================================

  async listTasks(projectId: string, status?: TaskStatus, limit: number = 50, offset: number = 0): Promise<Task[]> {
    const query = new URLSearchParams();
    if (status) query.append('status', status);
    query.append('limit', limit.toString());
    query.append('offset', offset.toString());
    return this.request<Task[]>(`/projects/${projectId}/tasks?${query.toString()}`);
  }

  async createTask(projectId: string, data: { title: string; description?: string; priority?: string; assigneeId?: string; isProposedByAgent?: boolean; agentProposalReason?: string }): Promise<Task> {
    return this.request<Task>(`/projects/${projectId}/tasks`, {
      method: 'POST',
      body: JSON.stringify(data),
    });
  }

  async getTask(projectId: string, taskId: string): Promise<Task> {
    return this.request<Task>(`/projects/${projectId}/tasks/${taskId}`);
  }

  async updateTaskStatus(projectId: string, taskId: string, data: { status: TaskStatus; expectedLockVersion: number; reason?: string; humanOverrideReason?: string; actorType?: string; actorId?: string }): Promise<Task> {
    return this.request<Task>(`/projects/${projectId}/tasks/${taskId}/status`, {
      method: 'PATCH',
      body: JSON.stringify(data),
    });
  }

  async acceptProposedTask(projectId: string, taskId: string): Promise<Task> {
    return this.request<Task>(`/projects/${projectId}/tasks/${taskId}/accept`, {
      method: 'POST',
    });
  }

  async getTaskContext(projectId: string, taskId: string): Promise<TaskContext> {
    return this.request<TaskContext>(`/projects/${projectId}/tasks/${taskId}/context`);
  }

  async listTaskEvents(projectId: string, taskId: string): Promise<TaskEvent[]> {
    return this.request<TaskEvent[]>(`/projects/${projectId}/tasks/${taskId}/events`);
  }

  async listTaskLinks(projectId: string, taskId: string): Promise<TaskLink[]> {
    return this.request<TaskLink[]>(`/projects/${projectId}/tasks/${taskId}/links`);
  }

  async linkTask(projectId: string, taskId: string, data: { linkType: string; targetId: string; targetLabel: string }): Promise<void> {
    return this.request(`/projects/${projectId}/tasks/${taskId}/links`, {
      method: 'POST',
      body: JSON.stringify(data),
    });
  }

  async recordImplementation(projectId: string, taskId: string, data: { summary: string; modifiedFiles?: string[]; unresolvedIssues?: string[]; sessionId?: string; agentPlatform?: string }): Promise<ImplementationRecord> {
    return this.request<ImplementationRecord>(`/projects/${projectId}/tasks/${taskId}/implementations`, {
      method: 'POST',
      body: JSON.stringify(data),
    });
  }

  async recordTestExecution(projectId: string, taskId: string, data: { testType?: string; framework?: string; passed: boolean; totalTests: number; passedTests: number; failedTests: number; durationMs: number; errorSummary?: string; rawLogs?: string; sessionId?: string; recordedBy?: string }): Promise<TestExecutionRecord> {
    return this.request<TestExecutionRecord>(`/projects/${projectId}/tasks/${taskId}/tests`, {
      method: 'POST',
      body: JSON.stringify(data),
    });
  }

  async listDebuggingRecords(projectId: string, taskId: string): Promise<DebuggingRecord[]> {
    return this.request<DebuggingRecord[]>(`/projects/${projectId}/tasks/${taskId}/debugging`);
  }

  async getDebuggingPackage(projectId: string, taskId: string, debuggingId: string): Promise<any> {
    return this.request(`/projects/${projectId}/tasks/${taskId}/debugging/${debuggingId}/package`);
  }

  async listActionRequests(projectId: string, status?: string): Promise<ActionRequest[]> {
    const query = status ? `?status=${status}` : '';
    return this.request<ActionRequest[]>(`/projects/${projectId}/actions${query}`);
  }

  async submitActionRequest(projectId: string, data: { actionType: string; parameters: Record<string, any>; isDestructive?: boolean }): Promise<ActionRequest> {
    return this.request<ActionRequest>(`/projects/${projectId}/actions`, {
      method: 'POST',
      body: JSON.stringify(data),
    });
  }

  async reviewActionRequest(projectId: string, actionId: string, data: { approved: boolean; reviewNotes?: string }): Promise<ActionRequest> {
    return this.request<ActionRequest>(`/projects/${projectId}/actions/${actionId}/review`, {
      method: 'POST',
      body: JSON.stringify(data),
    });
  }

  async getWorkflowOverview(projectId: string): Promise<any> {
    return this.request(`/projects/${projectId}/workflow/overview`);
  }

  async getIntegrationHealth(projectId: string): Promise<IntegrationHealth[]> {
    return this.request<IntegrationHealth[]>(`/projects/${projectId}/workflow/health`);
  }

  async analyzeChangeImpact(projectId: string, files: string[]): Promise<ChangeImpactResult> {
    return this.request<ChangeImpactResult>(`/projects/${projectId}/code-intel/impact`, {
      method: 'POST',
      body: JSON.stringify(files),
    });
  }

  async getRelevantTests(projectId: string, files: string[]): Promise<string[]> {
    const query = files.map(f => `file=${encodeURIComponent(f)}`).join('&');
    return this.request<string[]>(`/projects/${projectId}/code-intel/relevant-tests?${query}`);
  }
}

export const api = new ApiClient();

