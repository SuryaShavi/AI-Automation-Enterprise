import type { ReactElement } from 'react';
import { cleanup, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { apiClient } from '../../api/client';
import { endpoints } from '../../api/endpoints';
import { renderWithRouter } from '../../test/test-utils';
import AIAssistant from './AIAssistant';
import Dashboard from './Dashboard';
import Documents from './Documents';
import EmailAutomation from './EmailAutomation';
import Integrations from './Integrations';
import Notifications from './Notifications';
import Reports from './Reports';
import Settings from './Settings';
import SignupPage from './SignupPage';
import WorkflowAutomation from './WorkflowAutomation';

vi.mock('../../api/client', () => ({
  apiClient: {
    request: vi.fn(),
  },
}));

const sessionState = {
  isAuthenticated: true,
};

vi.mock('../auth/session', () => ({
  useSession: () => ({
    user: sessionState.isAuthenticated
      ? {
          id: 'user-1',
          email: 'user@company.com',
          firstName: 'Suraj',
          lastName: 'Tester',
          roles: ['EMPLOYEE'],
          preferences: { theme: 'light' },
          twoFactorEnabled: false,
          lastLoginAt: '2026-04-01T10:00:00Z',
        }
      : null,
    isLoading: false,
    isAuthenticated: sessionState.isAuthenticated,
    login: vi.fn(),
    register: vi.fn(),
    logout: vi.fn(),
    refreshUser: vi.fn(),
    setUser: vi.fn(),
  }),
}));

const requestMock = vi.mocked(apiClient.request);

const envelope = <T,>(data: T) => ({
  timestamp: new Date().toISOString(),
  traceId: 'test-trace',
  data,
  error: null,
});

const sampleTask = {
  id: 'task-1',
  title: 'Review automation rules',
  description: 'Check the nightly workflow',
  assigneeUserId: 'user-1',
  priority: 'HIGH',
  status: 'PENDING',
  dueAt: '2026-04-02T09:00:00Z',
  updatedAt: '2026-04-01T09:00:00Z',
  source: 'manual',
};

const sampleDocument = {
  id: 'doc-1',
  fileName: 'Quarterly Plan.pdf',
  fileType: 'application/pdf',
  fileSize: 20480,
  processingStatus: 'READY',
  summary: 'Quarterly plan and milestones.',
  createdAt: '2026-04-01T09:00:00Z',
};

const sampleNotification = {
  id: 'note-1',
  type: 'INFO',
  title: 'Workflow deployed',
  message: 'A new workflow was activated successfully.',
  read: false,
  createdAt: '2026-04-01T08:30:00Z',
};

const sampleReport = {
  id: 'report-1',
  reportType: 'PRODUCTIVITY',
  title: 'Weekly Productivity',
  status: 'GENERATED',
  requestedAt: '2026-04-01T08:00:00Z',
  payload: {},
};

const sampleWorkflow = {
  id: 'workflow-1',
  name: 'Invoice Approval',
  status: 'ACTIVE',
  steps: ['Receive email', 'Create task'],
  createdAt: '2026-04-01T08:00:00Z',
  triggers: [],
};

const sampleIntegration = {
  provider: 'Slack',
  status: 'CONNECTED',
  authType: 'OAuth2',
  connectedAt: '2026-04-01T08:00:00Z',
  webhookSecretConfigured: true,
};

function defaultApiResponse(path: string) {
  if (path === endpoints.dashboard.metrics) {
    return envelope({ totalTasks: 14, pendingTasks: 4, completedTasks: 10, documentsUploaded: 6, aiRequestsToday: 8 });
  }

  if (path === endpoints.dashboard.activity) {
    return envelope([{ type: 'task.created', description: 'New task created', occurredAt: '2026-04-01T08:00:00Z' }]);
  }

  if (path === endpoints.dashboard.health) {
    return envelope([{ service: 'auth-service', status: 'UP' }, { service: 'task-service', status: 'UP' }]);
  }

  if (path === endpoints.notifications.list) {
    return envelope({ items: [sampleNotification] });
  }

  if (path === endpoints.notifications.recent) {
    return envelope([sampleNotification]);
  }

  if (path === endpoints.documents.list) {
    return envelope({ items: [sampleDocument] });
  }

  if (path.startsWith('/documents/') && path.endsWith('/ask')) {
    return envelope({ question: 'What is this document about?', answer: 'It describes the quarterly plan.', confidence: 0.93, citations: [] });
  }

  if (path.startsWith('/documents/')) {
    return envelope(sampleDocument);
  }

  if (path === endpoints.emails.list) {
    return envelope({ items: [] });
  }

  if (path === endpoints.emails.stats) {
    return envelope({ emailsProcessed: 5, tasksDetected: 2, pendingReview: 1 });
  }

  if (path === endpoints.reports.list) {
    return envelope({ items: [sampleReport] });
  }

  if (path === endpoints.reports.analytics) {
    return envelope({
      weeklyProductivity: [{ day: 'Mon', tasks: 5 }],
      taskCompletionRate: [{ month: 'Apr', rate: 91 }],
      aiUsageAnalytics: [{ name: 'Document Analysis', value: 40 }],
    });
  }

  if (path === endpoints.tasks.list) {
    return envelope({ items: [sampleTask] });
  }

  if (path === endpoints.tasks.board) {
    return envelope({ columns: [{ status: 'PENDING', tasks: [sampleTask] }] });
  }

  if (path === endpoints.workflows.list) {
    return envelope([sampleWorkflow]);
  }

  if (path.includes('/executions')) {
    return envelope([]);
  }

  if (path === endpoints.integrations.list) {
    return envelope([sampleIntegration]);
  }

  if (path === endpoints.ai.chats) {
    return envelope([]);
  }

  return envelope({ items: [] });
}

describe('application page smoke tests', () => {
  const smokeCases: Array<{ name: string; page: ReactElement; heading: RegExp; authenticated?: boolean }> = [
    { name: 'Dashboard', page: <Dashboard />, heading: /welcome back/i, authenticated: true },
    { name: 'Documents', page: <Documents />, heading: /^document intelligence$/i, authenticated: true },
    { name: 'Email Automation', page: <EmailAutomation />, heading: /^email automation$/i, authenticated: true },
    { name: 'Workflow Automation', page: <WorkflowAutomation />, heading: /^workflow automation$/i, authenticated: true },
    { name: 'Reports', page: <Reports />, heading: /^reports & analytics$/i, authenticated: true },
    { name: 'Notifications', page: <Notifications />, heading: /^notifications$/i, authenticated: true },
    { name: 'Integrations', page: <Integrations />, heading: /^integrations$/i, authenticated: true },
    { name: 'AI Assistant', page: <AIAssistant />, heading: /^ai assistant$/i, authenticated: true },
    { name: 'Settings', page: <Settings />, heading: /^settings$/i, authenticated: true },
    { name: 'Signup', page: <SignupPage />, heading: /^sign up$/i, authenticated: false },
  ];

  beforeEach(() => {
    requestMock.mockReset();
    requestMock.mockImplementation(async (path: string) => defaultApiResponse(path));
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it.each(smokeCases)('$name renders without crashing', async ({ page, heading, authenticated = true }) => {
    sessionState.isAuthenticated = authenticated;
    renderWithRouter(page);
    expect(await screen.findByRole('heading', { name: heading })).toBeInTheDocument();
  });
});
