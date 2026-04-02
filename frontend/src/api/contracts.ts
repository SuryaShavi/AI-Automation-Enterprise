export type Role = 'ADMIN' | 'EMPLOYEE';

export interface ApiError {
  code: string;
  message: string;
  details: string[];
}

export interface ApiEnvelope<T> {
  timestamp: string;
  traceId: string;
  data: T;
  error: ApiError | null;
}

export interface PageEnvelope<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
  sort: string;
}

export interface UserProfile {
  id: string;
  userCode?: number;
  email: string;
  firstName: string;
  lastName: string;
  roles: Role[];
  preferences: Record<string, string>;
  twoFactorEnabled: boolean;
  lastLoginAt: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}


export interface RegisterRequest {
  email: string;
  firstName: string;
  lastName: string;
  role: 'USER' | 'ADMIN';
  password: string;
}
export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  accessTokenExpiresAt: string;
  refreshTokenExpiresAt: string;
  user: UserProfile;
}

export interface ServiceHealthItem {
  service: string;
  status: string;
}

export interface DashboardMetrics {
  totalTasks: number;
  pendingTasks: number;
  completedTasks: number;
  documentsUploaded: number;
  aiRequestsToday: number;
}

export interface ActivityFeedItem {
  type: string;
  description: string;
  occurredAt: string;
}

export interface NotificationItem {
  id: string;
  type: string;
  title: string;
  message: string;
  read: boolean;
  createdAt: string;
}

export interface EmailItem {
  id: string;
  senderName: string;
  senderEmail: string;
  subject: string;
  aiSummary: string;
  detectedTasks: string[];
  status: string;
  priority: string;
  receivedAt: string;
}

export interface TaskItem {
  id: string;
  title: string;
  description: string;
  assigneeUserId: string;
  priority: string;
  status: string;
  dueAt: string;
  updatedAt: string;
  source: string;
}

export interface BoardColumn {
  status: string;
  tasks: TaskItem[];
}

export interface DocumentItem {
  id: string;
  fileName: string;
  fileType: string | null;
  fileSize: number;
  processingStatus: string;
  summary: string;
  createdAt: string;
}

export interface DocumentChunk {
  id: string;
  chunkIndex: number;
  content: string;
  citationLabel: string;
}

export interface DocumentAnswer {
  question: string;
  answer: string;
  confidence: number;
  citations: DocumentChunk[];
}

export interface ReportItem {
  id: string;
  reportType: string;
  title: string;
  status: string;
  requestedAt: string;
  payload: Record<string, unknown>;
}

export interface ChatMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  createdAt: string;
}

export interface ChatSummary {
  id: string;
  title: string;
  updatedAt: string;
  messageCount: number;
}

export interface ChatReply {
  chatId: string;
  message: ChatMessage;
  guardrails: string[];
}

export interface AttachmentReceipt {
  chatId: string;
  fileName: string;
  contentType: string;
  size: number;
  status: string;
}

export interface ReportAnalyticsPoint {
  [key: string]: string | number;
}

export interface ReportAnalytics {
  weeklyProductivity: ReportAnalyticsPoint[];
  taskCompletionRate: ReportAnalyticsPoint[];
  aiUsageAnalytics: ReportAnalyticsPoint[];
}

export interface WorkflowItem {
  id: string;
  name: string;
  status: string;
  steps: string[];
  createdAt: string;
  triggers: WorkflowTriggerItem[];
}

export interface WorkflowTriggerItem {
  id: string;
  type: string;
  scheduleCron: string | null;
  eventType: string | null;
  provider: string | null;
  enabled: boolean;
  lastFiredAt: string | null;
}

export interface WorkflowStepResult {
  index: number;
  name: string;
  status: string;
  startedAt: string | null;
  completedAt: string | null;
  message: string | null;
}

export interface WorkflowExecutionItem {
  executionId: string;
  status: string;
  triggerType: string;
  triggerLabel: string;
  startedAt: string;
  completedAt: string | null;
  durationMs: number;
  errorMessage: string | null;
  stepResults: WorkflowStepResult[];
}

export interface IntegrationItem {
  provider: string;
  status: string;
  authType: string;
  connectedAt: string | null;
  webhookSecretConfigured: boolean;
}

export interface EmailStats {
  emailsProcessed: number;
  tasksDetected: number;
  pendingReview: number;
}

export interface ExtractedTask {
  id: string;
  title: string;
  confidence: number;
}

export interface ExtractTaskResponse {
  emailId: string;
  tasks: ExtractedTask[];
}
