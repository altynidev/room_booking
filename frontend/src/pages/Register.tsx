import { useState, type FormEvent } from 'react';
import { Link, Navigate, useNavigate } from 'react-router';
import { api } from '../api';
import { useAuth } from '../auth';
import { ErrorBanner, errorMessage } from '../components/ui';

export default function Register() {
  const { user, login } = useAuth();
  const navigate = useNavigate();

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  if (user) return <Navigate to="/" replace />;

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (password !== confirm) {
      setError('Passwords do not match');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await api.register(username, password);
      await login(username, password);
      navigate('/', { replace: true });
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="auth-page">
      <form className="card auth-card" onSubmit={submit}>
        <img src="/favicon.svg" alt="" width={40} height={40} />
        <h1>Create account</h1>
        <ErrorBanner error={error} />
        <label>
          Username
          <input
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            autoComplete="username"
            minLength={3}
            maxLength={50}
            pattern="[A-Za-z0-9._\-]+"
            title="Letters, digits, '.', '_' and '-'"
            required
            autoFocus
          />
        </label>
        <label>
          Password
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="new-password" minLength={6} maxLength={72} required />
          <span className="hint">At least 6 characters</span>
        </label>
        <label>
          Confirm password
          <input type="password" value={confirm} onChange={(e) => setConfirm(e.target.value)} autoComplete="new-password" required />
        </label>
        <button className="btn btn-primary" disabled={busy}>{busy ? 'Creating…' : 'Create account'}</button>
        <p className="muted small">
          Already registered? <Link to="/login">Sign in</Link>
        </p>
      </form>
    </div>
  );
}
