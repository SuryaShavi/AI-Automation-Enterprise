import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import LoginPage from './LoginPage';
import { renderWithRouter } from '../../test/test-utils';

const loginMock = vi.fn();

vi.mock('../auth/session', () => ({
  useSession: () => ({
    user: null,
    isLoading: false,
    isAuthenticated: false,
    login: loginMock,
    register: vi.fn(),
    logout: vi.fn(),
    refreshUser: vi.fn(),
    setUser: vi.fn(),
  }),
}));

describe('LoginPage', () => {
  beforeEach(() => {
    loginMock.mockReset();
    loginMock.mockResolvedValue(undefined);
  });

  it('submits credentials through the session hook', async () => {
    renderWithRouter(<LoginPage />);
    const user = userEvent.setup();

    await user.type(screen.getByPlaceholderText(/you@company.com/i), 'user@company.com');
    await user.type(screen.getByPlaceholderText(/••••••••/i), 'Secret123!');
    await user.click(screen.getByRole('button', { name: /sign in/i }));

    await waitFor(() => {
      expect(loginMock).toHaveBeenCalledWith({
        email: 'user@company.com',
        password: 'Secret123!',
      });
    });
  });
});
