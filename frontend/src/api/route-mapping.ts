export const frontendRouteMapping = {
  '/login': {
    apis: ['POST /auth/login', 'POST /auth/refresh', 'GET /auth/me'],
    transport: 'request-response',
  },
  '/': {
    apis: ['GET /dashboard/metrics', 'GET /dashboard/activity', 'GET /notifications/recent', 'GET /health/services'],
    transport: 'parallel GET + 30s polling for notifications',
  },
  '/ai-assistant': {
    apis: ['POST /ai/chat', 'GET /ai/chats', 'GET /ai/chats/{id}/messages', 'POST /ai/chats/{id}/attachments'],
    transport: 'request-response, upgrade to SSE/WebSocket later if streaming is enabled',
  },
  '/email-automation': {
    apis: ['GET /emails', 'GET /emails/{id}', 'POST /emails/ingest', 'POST /emails/{id}/extract-tasks', 'GET /emails/stats'],
    transport: 'request-response with manual refresh and background polling every 60s',
  },
  '/tasks': {
    apis: ['GET /tasks', 'POST /tasks', 'PATCH /tasks/{id}', 'DELETE /tasks/{id}', 'GET /tasks/board'],
    transport: 'request-response with optimistic updates on write',
  },
  '/documents': {
    apis: ['POST /documents/upload', 'GET /documents', 'GET /documents/{id}', 'POST /documents/{id}/ask', 'GET /documents/{id}/chunks'],
    transport: 'multipart upload + request-response + 15s polling for processing status',
  },
  '/reports': {
    apis: ['GET /reports', 'POST /reports/generate', 'GET /reports/{id}', 'GET /reports/analytics'],
    transport: 'request-response, poll requested reports every 20s until status is terminal',
  },
  '/notifications': {
    apis: ['GET /notifications', 'PATCH /notifications/{id}/read', 'PATCH /notifications/read-all', 'DELETE /notifications/{id}'],
    transport: 'polling every 30s, SSE recommended for future badge updates',
  },
  '/workflow-automation': {
    apis: ['GET /workflows', 'POST /workflows', 'PATCH /workflows/{id}/status', 'GET /workflows/{id}/executions'],
    transport: 'request-response with manual refresh for execution history',
  },
  '/integrations': {
    apis: ['GET /integrations', 'POST /integrations/{provider}/connect', 'POST /integrations/{provider}/disconnect', 'POST /integrations/webhooks/{provider}'],
    transport: 'request-response for user actions, webhook ingestion from providers',
  },
  '/settings': {
    apis: ['GET /users/me', 'PATCH /users/me', 'PATCH /users/me/password', 'PATCH /users/me/preferences', 'POST /users/me/2fa/enable'],
    transport: 'request-response with inline form validation',
  },
} as const;