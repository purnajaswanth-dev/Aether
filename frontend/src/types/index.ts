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

