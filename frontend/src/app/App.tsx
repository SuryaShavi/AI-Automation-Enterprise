import { RouterProvider } from 'react-router';
import { router } from './routes';
import { SessionProvider } from './auth/session';

export default function App() {
  return (
    <SessionProvider>
      <RouterProvider router={router} />
    </SessionProvider>
  );
}