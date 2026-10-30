import React from "react";
import { Link, useNavigate } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { api } from "@/services/api";
import { User } from "@/types";
import { ShieldCheck } from "lucide-react";

interface AuthPageProps {
  onAuthSuccess: (user: User) => void;
  isRegister?: boolean;
}

export function AuthPage({ onAuthSuccess, isRegister = false }: AuthPageProps) {
  const navigate = useNavigate();

  const [email, setEmail] = React.useState("");
  const [password, setPassword] = React.useState("");
  const [displayName, setDisplayName] = React.useState("");

  const [loading, setLoading] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError(null);

    try {
      if (isRegister) {
        if (password.length < 8) {
          setError("Password must be at least 8 characters long.");
          setLoading(false);
          return;
        }
        const res = await api.register({
          email: email.trim(),
          password,
          displayName: displayName.trim(),
        });
        onAuthSuccess(res.user);
        navigate("/projects");
      } else {
        const res = await api.login({
          email: email.trim(),
          password,
        });
        onAuthSuccess(res.user);
        navigate("/projects");
      }
    } catch (err: any) {
      setError(err.message || "Authentication failed");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex min-h-[85vh] items-center justify-center p-4">
      <div className="w-full max-w-sm space-y-6 rounded-panel border border-border bg-canvas p-8 shadow-lift">
        <div className="text-center">
          <div className="mx-auto flex h-10 w-10 items-center justify-center rounded-control bg-accent text-white font-bold text-lg shadow-lift mb-3">
            Æ
          </div>
          <h1 className="text-xl font-bold tracking-tight text-text-primary">
            {isRegister ? "Create Engineer Account" : "Sign in to Aether"}
          </h1>
          <p className="mt-1 text-xs text-text-secondary">
            {isRegister
              ? "Join the unified persistent engineering workspace"
              : "Access your project memory and decision history"}
          </p>
        </div>

        {error && (
          <div className="rounded-control bg-danger-subtle p-3 text-xs text-danger border border-danger/20 leading-relaxed">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4 text-xs">
          {isRegister && (
            <div>
              <label className="block font-medium text-text-primary mb-1">Display Name</label>
              <Input
                value={displayName}
                onChange={(e) => setDisplayName(e.target.value)}
                placeholder="e.g. Alex Chen"
                required
              />
            </div>
          )}

          <div>
            <label className="block font-medium text-text-primary mb-1">Email Address</label>
            <Input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="alex@company.com"
              required
              autoFocus
            />
          </div>

          <div>
            <div className="flex items-center justify-between mb-1">
              <label className="font-medium text-text-primary">Password</label>
              {isRegister && (
                <span className="text-[10px] text-text-tertiary">Min 8 chars</span>
              )}
            </div>
            <Input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="••••••••"
              required
            />
          </div>

          <Button type="submit" className="w-full mt-2" disabled={loading}>
            {loading ? "Processing…" : isRegister ? "Create Account" : "Sign In"}
          </Button>
        </form>

        <div className="pt-4 border-t border-border text-center text-xs text-text-secondary">
          {isRegister ? (
            <span>
              Already have an account?{" "}
              <Link to="/login" className="text-accent hover:underline font-medium">
                Sign In
              </Link>
            </span>
          ) : (
            <span>
              Don't have an account?{" "}
              <Link to="/register" className="text-accent hover:underline font-medium">
                Create one
              </Link>
            </span>
          )}
        </div>

        <div className="flex items-center justify-center gap-1.5 text-[11px] text-text-tertiary">
          <ShieldCheck className="h-3.5 w-3.5 text-success" />
          <span>Argon2id password hashing & HTTP-only sessions</span>
        </div>
      </div>
    </div>
  );
}

