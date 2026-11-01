export interface User {
  id: string;
  email: string;
  displayName: string;
  createdAt: string;
}

export interface Project {
  id: string;
  publicId: string;
  name: string;
  description: string;
  projectType: string;
  techStack: string[];
  directory?: string;
  storagePreference: 'LOCAL_FIRST' | 'CLOUD' | 'HYBRID';
  archived: boolean;
  currentUserRole: 'OWNER' | 'MEMBER' | 'VIEWER';
  createdAt: string;
  updatedAt: string;
}

export interface IntegrationStatus {
  id: string;
  providerId: 'claude' | 'chatgpt' | 'antigravity' | 'github' | 'render' | 'postman' | 'docker';
  name: string;
  category: string;
  description: string;
  status: 'CONNECTED' | 'NOT_CONNECTED' | 'NOT_AVAILABLE' | 'SYNCING' | 'ERROR';
  plannedCapabilities: string[];
  statusReason: string;
}

export interface Artifact {
  id: string;
  projectId: string;
  title: string;
  sourcePlatform: string;
  sourceReference?: string;
  artifactType: string;
  relatedTaskId?: string;
  currentVersion: number;
  contentHash: string;
  byteSize: number;
  content?: string;
  createdAt: string;
}

export interface ArtifactVersion {
  id: string;
  artifactId: string;
  version: number;
  contentHash: string;
  byteSize: number;
  createdBy: string;
  actorType: 'HUMAN_USER' | 'AGENT';
  changeSummary?: string;
  content?: string;
  createdAt: string;
}

export interface ArtifactDiffResponse {
  artifactId: string;
  v1: number;
  v2: number;
  v1Hash: string;
  v2Hash: string;
  insertions: number;
  deletions: number;
  modifications: number;
  lines: {
    type: 'EQUAL' | 'INSERT' | 'DELETE' | 'CHANGE';
    oldLineNumber?: number;
    newLineNumber?: number;
    content: string;
  }[];
}

export interface TechnicalDecision {
  id: string;
  projectId: string;
  publicId: string;
  title: string;
  state: 'PROPOSED' | 'UNDER_REVIEW' | 'APPROVED' | 'REJECTED' | 'SUPERSEDED' | 'IMPLEMENTED' | 'VERIFIED';
  category: string;
  currentVersion: number;
  lockVersion: number;
  problemStatement: string;
  proposedSolution: string;
  rationale?: string;
  consequences?: string;
  latestActorId: string;
  latestActorType: 'HUMAN_USER' | 'AGENT';
  createdAt: string;
  updatedAt: string;
}

export interface DecisionVersion {
  id: string;
  decisionId: string;
  projectId: string;
  version: number;
  state: string;
  problemStatement: string;
  proposedSolution: string;
  rationale?: string;
  consequences?: string;
  sourceArtifactVersionId?: string;
  actorId: string;
  actorType: string;
  reviewNotes?: string;
  createdAt: string;
}

export interface DecisionComparison {
  decision: TechnicalDecision;
  originalProposal: DecisionVersion;
  reviewFindings: DecisionVersion[];
  modifiedProposals: DecisionVersion[];
  approvedVersion?: DecisionVersion;
}

export interface TimelineEvent {
  id: string;
  projectId: string;
  taskId?: string;
  sourcePlatform: string;
  timestamp: string;
  eventType: string;
  associatedArtifactId?: string;
  associatedDecisionId?: string;
  decisionVersion?: number;
  status: 'SUCCESS' | 'FAILED' | 'PENDING';
  actorId: string;
  actorType: string;
  summary: string;
  metadata?: string;
  createdAt: string;
}

export type McpClientPlatform = 'CLAUDE' | 'CHATGPT' | 'ANTIGRAVITY' | 'OTHER';

export interface McpToken {
  id: string;
  projectId: string;
  tokenName: string;
  clientPlatform: McpClientPlatform;
  tokenPrefix: string;
  permissions: string[];
  isRevoked: boolean;
  isPaused: boolean;
  rateLimitPerMinute: number;
  expiresAt?: string;
  lastUsedAt?: string;
  createdAt: string;
}

export interface CreateMcpTokenRequest {
  tokenName: string;
  clientPlatform: McpClientPlatform;
  permissions: string[];
  rateLimitPerMinute?: number;
  expiresAt?: string;
}

export interface CreateMcpTokenResponse {
  id: string;
  projectId: string;
  tokenName: string;
  clientPlatform: McpClientPlatform;
  rawToken: string;
  tokenPrefix: string;
  permissions: string[];
  rateLimitPerMinute: number;
  expiresAt?: string;
  createdAt: string;
}

export interface McpActivityLog {
  id: string;
  projectId: string;
  tokenId?: string;
  toolName: string;
  clientPlatform: McpClientPlatform;
  durationMs: number;
  status: 'SUCCESS' | 'ERROR' | 'RATE_LIMITED' | 'UNAUTHORIZED';
  errorMessage?: string;
  createdAt: string;
}

export interface McpReliabilityReport {
  totalCalls: number;
  successfulCalls: number;
  failedCalls: number;
  sessionsTracked: number;
  firstRetrievalBeforeSaveRate: number;
  saveCompletionRate: number;
  topToolsCalled: Record<string, number>;
  platformDistribution: Record<string, number>;
}

export interface McpClientConfig {
  platform: string;
  transport: string;
  config_file: string;
  config_snippet: string;
  rules_file: string;
  rules_snippet: string;
}

// ==========================================
// Phase 3: Extraction, RAG & Orchestrator Types
// ==========================================

export interface SearchResultItem {
  chunkId: string;
  artifactId: string;
  artifactVersion: number;
  parentSectionPath: string;
  chunkType: string;
  content: string;
  contentHash: string;
  startOffset: number;
  endOffset: number;
  startLine: number;
  endLine: number;
  methodsMatched: string[];
  fusedScore: number;
  approvalState: string;
  isSuperseded: boolean;
}

export interface ExtractedFact {
  id: string;
  projectId: string;
  artifactId: string;
  artifactVersion: number;
  category: 'REQUIREMENT' | 'DECISION' | 'ARCHITECTURE' | 'API_CONTRACT' | 'DATABASE_DESIGN' | 'CONSTRAINT';
  factKey: string;
  factStatement: string;
  exactQuote: string;
  startOffset: number;
  endOffset: number;
  sourcePlatform: string;
  extractionMethod: string;
  validationState: 'VERIFIED' | 'UNVERIFIED' | 'REJECTED';
  validationNotes?: string;
  approvalState: string;
  confidenceScore: number;
  createdAt: string;
}

export interface IngestionJob {
  id: string;
  projectId: string;
  artifactId: string;
  artifactVersion: number;
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED';
  currentStep: string;
  retryCount: number;
  errorMessage?: string;
  createdAt: string;
  updatedAt: string;
}

export interface SecretFinding {
  id: string;
  projectId: string;
  artifactId: string;
  artifactVersion: number;
  findingType: 'AWS_KEY' | 'GITHUB_TOKEN' | 'OPENAI_KEY' | 'GENERIC_HIGH_ENTROPY' | 'PRIVATE_KEY' | 'BEARER_TOKEN';
  findingLocation: string;
  maskedSnippet: string;
  reviewed: boolean;
  falsePositive: boolean;
  createdAt: string;
}

export interface OmittedItem {
  id: string;
  itemType: string;
  title: string;
  tokenCost: number;
  omissionReason: string;
  retrievalHandle: string;
}

export interface ContextPackageResponse {
  requestId: string;
  projectId: string;
  requestType: string;
  targetModel: string;
  maxTokenBudget: number;
  totalTokensUsed: number;
  budgetUtilizationPercent: number;
  systemPromptEnclosure: string;
  l1ExecutiveSummary: string;
  l2StructuredDecisions: string;
  l3KeyArtifactsAndChunks: string;
  l4ApiAndDbContracts: string;
  l5RawSurroundingLogs: string;
  omittedItems: OmittedItem[];
  generatedAt: string;
}

export interface CapabilityDescriptor {
  provider: 'GITHUB' | 'RENDER' | 'POSTMAN' | 'DOCKER';
  displayName: string;
  description: string;
  primarySyncMethod: 'WEBHOOK' | 'POLLING' | 'MCP' | 'MANUAL' | 'UNSUPPORTED';
  supportsWebhooks: boolean;
  supportsPolling: boolean;
  supportsLogs: boolean;
  supportsDeployments: boolean;
  supportsArtifactSync: boolean;
  authModel: string;
  eventTypes: string[];
  rateLimits: string;
  limitations: string;
}

export interface IntegrationSummary {
  id: string | null;
  projectId: string;
  provider: 'GITHUB' | 'RENDER' | 'POSTMAN' | 'DOCKER';
  displayName: string;
  status: 'CONNECTED' | 'DISCONNECTED' | 'PAUSED' | 'ERROR';
  primarySyncMethod: 'WEBHOOK' | 'POLLING' | 'MCP' | 'MANUAL' | 'UNSUPPORTED';
  configMetadata: Record<string, any>;
  maskedCredential?: string;
  lastSyncAt?: string;
  createdAt?: string;
}

export interface Deployment {
  id: string;
  projectId: string;
  provider: string;
  serviceId: string;
  externalDeployId: string;
  environment: string;
  commitSha?: string;
  status: string;
  failureClassification?: string;
  buildLogsArtifactId?: string;
  runtimeLogsArtifactId?: string;
  extractedErrors: string[];
  linkedTaskId?: string;
  deployStartedAt?: string;
  deployFinishedAt?: string;
  createdAt: string;
}

export interface AgentDevice {
  id: string;
  projectId: string;
  deviceCode: string;
  userCode: string;
  hostname?: string;
  os?: string;
  localPath?: string;
  status: string;
  expiresAt: string;
  lastHeartbeatAt?: string;
  createdAt: string;
}

export interface DeadLetterEvent {
  id: string;
  projectId: string;
  provider: string;
  eventType: string;
  payload: string;
  headers: Record<string, any>;
  errorMessage: string;
  attempts: number;
  status: string;
  createdAt: string;
  lastRetryAt?: string;
}

// ==========================================
// Phase 5: Workflow Coordination Types
// ==========================================

export type TaskStatus =
  | 'BACKLOG'
  | 'READY'
  | 'IN_PROGRESS'
  | 'IN_REVIEW'
  | 'BLOCKED'
  | 'DONE_UNVERIFIED'
  | 'VERIFIED'
  | 'CANCELLED';

export type TaskPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

export type ActorType = 'HUMAN' | 'AGENT' | 'SYSTEM';

export type TaskLinkType = 'DECISION' | 'ARTIFACT' | 'COMMIT' | 'DEPLOYMENT';

export interface Task {
  id: string;
  projectId: string;
  key: string;
  title: string;
  description?: string;
  status: TaskStatus;
  priority: TaskPriority;
  assigneeId?: string;
  isProposedByAgent: boolean;
  agentProposalReason?: string;
  humanOverrideReason?: string;
  lockVersion: number;
  createdBy?: string;
  createdAt: string;
  updatedAt: string;
}

export interface TaskEvent {
  id: string;
  projectId: string;
  taskId: string;
  eventType: string;
  fromStatus?: string;
  toStatus?: string;
  actorType: ActorType;
  actorId: string;
  reason?: string;
  metadata?: Record<string, any>;
  createdAt: string;
}

export interface ImplementationRecord {
  id: string;
  projectId: string;
  taskId: string;
  sessionId?: string;
  agentPlatform: string;
  summary: string;
  modifiedFiles: string[];
  unresolvedIssues: string[];
  candidateStatus: string;
  recordedAt: string;
}

export interface TestExecutionRecord {
  id: string;
  projectId: string;
  taskId: string;
  sessionId?: string;
  testType: string;
  framework: string;
  passed: boolean;
  totalTests: number;
  passedTests: number;
  failedTests: number;
  durationMs: number;
  errorSummary?: string;
  rawLogsArtifactId?: string;
  recordedBy: string;
  recordedAt: string;
}

export interface DebuggingRecord {
  id: string;
  projectId: string;
  taskId: string;
  deploymentId: string;
  status: 'INVESTIGATING' | 'ROOT_CAUSE_IDENTIFIED' | 'FIX_PROPOSED' | 'RESOLVED' | 'ABANDONED';
  failureClassification: string;
  errorLine?: string;
  rootCauseHypothesis?: string;
  resolvedByCommit?: string;
  resolvedAt?: string;
  createdAt: string;
}

export interface TaskLink {
  id: string;
  projectId: string;
  taskId: string;
  linkType: TaskLinkType;
  targetId: string;
  targetLabel: string;
  createdAt: string;
}

export interface TaskContext {
  task: Task;
  linkedDecisions: any[];
  relevantArtifacts: any[];
  priorAttempts: DebuggingRecord[];
  latestImplementation?: ImplementationRecord;
  recentHandoffs: AgentHandoff[];
  apiContracts: any[];
}

export interface ActionRequest {
  id: string;
  projectId: string;
  tokenId?: string;
  actionType: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'EXECUTED';
  parameters: Record<string, any>;
  isDestructive: boolean;
  reviewedBy?: string;
  reviewedAt?: string;
  reviewNotes?: string;
  executionResult?: Record<string, any>;
  createdAt: string;
}

export interface AgentHandoff {
  id: string;
  projectId: string;
  taskId: string;
  fromSessionId?: string;
  toSessionId?: string;
  fromPlatform: string;
  toPlatform: string;
  intent: string;
  summary: string;
  unresolvedQuestions: string[];
  handoffAt: string;
}

export interface ChangeImpactResult {
  modifiedFiles: string[];
  impactedFiles: string[];
  affectedApis: string[];
  suggestedTestFiles: string[];
  confidence: 'HIGH' | 'MEDIUM' | 'HEURISTIC';
  heuristicNotes?: string;
}

export interface IntegrationHealth {
  provider: string;
  status: string;
  lastSyncAt?: string;
  currentLagSeconds: number;
  recentErrorRatePercent: number;
  rateLimitRemaining?: number;
  rateLimitResetSeconds?: number;
  dlqPendingCount: number;
}



