import { screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { apiClient } from '../../api/client';
import { endpoints } from '../../api/endpoints';
import { renderWithRouter } from '../../test/test-utils';
import Tasks from './Tasks';

vi.mock('../../api/client', () => ({
  apiClient: {
    request: vi.fn(),
  },
}));

const requestMock = vi.mocked(apiClient.request);

const sampleTask = {
  id: 'task-1',
  title: 'Automate onboarding',
  description: 'Create the welcome workflow',
  assigneeUserId: 'user-1',
  priority: 'HIGH',
  status: 'PENDING',
  dueAt: '2026-04-02T10:00:00Z',
  updatedAt: '2026-04-01T10:00:00Z',
  source: 'manual',
};

const envelope = <T,>(data: T) => ({
  timestamp: new Date().toISOString(),
  traceId: 'test-trace',
  data,
  error: null,
});

describe('Tasks page', () => {
  beforeEach(() => {
    requestMock.mockReset();
    requestMock.mockImplementation(async (path: string) => {
      if (path === endpoints.tasks.list) {
        return envelope({ items: [sampleTask] });
      }

      if (path === endpoints.tasks.board) {
        return envelope({ columns: [{ status: 'PENDING', tasks: [sampleTask] }] });
      }

      throw new Error(`Unhandled path: ${path}`);
    });
  });

  it('loads and displays task data from the backend API', async () => {
    renderWithRouter(<Tasks />);

    expect(await screen.findByRole('heading', { name: /task management/i })).toBeInTheDocument();
    expect(await screen.findByText('Automate onboarding')).toBeInTheDocument();
    expect(requestMock).toHaveBeenCalledWith(endpoints.tasks.list);
    expect(requestMock).toHaveBeenCalledWith(endpoints.tasks.board);
  });
});
