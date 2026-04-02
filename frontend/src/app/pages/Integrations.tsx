import { useEffect, useMemo, useState } from "react";
import { Mail, MessageSquare, Cloud, Code, CheckCircle, XCircle } from "lucide-react";
import { apiClient } from "../../api/client";
import { endpoints } from "../../api/endpoints";
import type { IntegrationItem } from "../../api/contracts";
import { formatCompactDate, formatDateTime } from "../lib/format";

interface IntegrationDraft {
  authType: string;
  webhookSecret: string;
  slackWebhookUrl: string;
  testEventType: string;
}

interface WebhookReceipt {
  provider: string;
  eventType: string | null;
  matchedTriggers: number;
  executionsStarted: number;
  signatureValidated: boolean;
  receivedAt: string;
}

function normalizeProviderKey(provider: string) {
  return provider.toLowerCase();
}

async function signWebhookPayload(secret: string, payload: Record<string, unknown>) {
  if (!secret.trim() || typeof crypto === "undefined" || !crypto.subtle) {
    return undefined;
  }

  const encoder = new TextEncoder();
  const key = await crypto.subtle.importKey("raw", encoder.encode(secret), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  const signature = await crypto.subtle.sign("HMAC", key, encoder.encode(JSON.stringify(payload)));
  const bytes = new Uint8Array(signature);
  const hex = Array.from(bytes).map((value) => value.toString(16).padStart(2, "0")).join("");
  return `sha256=${hex}`;
}

export default function Integrations() {
  const [integrations, setIntegrations] = useState<IntegrationItem[]>([]);
  const [drafts, setDrafts] = useState<Record<string, IntegrationDraft>>({});
  const [receipts, setReceipts] = useState<Record<string, WebhookReceipt>>({});
  const [loading, setLoading] = useState(true);
  const [pendingProvider, setPendingProvider] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;

    async function loadIntegrations(showLoader: boolean) {
      if (showLoader) {
        setLoading(true);
      }
      try {
        const envelope = await apiClient.request<IntegrationItem[]>(endpoints.integrations.list);
        if (!active) {
          return;
        }
        setIntegrations(envelope.data);
        setDrafts((previous) => {
          const nextDrafts = { ...previous };
          for (const integration of envelope.data) {
            const key = normalizeProviderKey(integration.provider);
            nextDrafts[key] = nextDrafts[key] ?? {
              authType: integration.authType,
              webhookSecret: "",
              slackWebhookUrl: "",
              testEventType: `${integration.provider.toLowerCase().replace(/\s+/g, ".")}.test`,
            };
            nextDrafts[key] = { ...nextDrafts[key], authType: integration.authType };
          }
          return nextDrafts;
        });
      } catch (loadError) {
        if (active) {
          setError(loadError instanceof Error ? loadError.message : "Failed to load integrations");
        }
      } finally {
        if (active && showLoader) {
          setLoading(false);
        }
      }
    }

    void loadIntegrations(true);
    const intervalId = window.setInterval(() => {
      void loadIntegrations(false);
    }, 20000);

    return () => {
      active = false;
      window.clearInterval(intervalId);
    };
  }, []);

  function updateDraft(provider: string, patch: Partial<IntegrationDraft>) {
    const key = normalizeProviderKey(provider);
    setDrafts((previous) => ({
      ...previous,
      [key]: {
        authType: previous[key]?.authType ?? "OAuth2",
        webhookSecret: previous[key]?.webhookSecret ?? "",
          slackWebhookUrl: previous[key]?.slackWebhookUrl ?? "",
        testEventType: previous[key]?.testEventType ?? `${provider.toLowerCase().replace(/\s+/g, ".")}.test`,
        ...patch,
      },
    }));
  }

  async function toggleConnection(provider: string, currentlyConnected: boolean) {
    const key = normalizeProviderKey(provider);
    const draft = drafts[key] ?? { authType: "OAuth2", webhookSecret: "", slackWebhookUrl: "", testEventType: `${provider.toLowerCase().replace(/\s+/g, ".")}.test` };
    const snapshot = integrations;
    setPendingProvider(provider);
    setIntegrations((previous) => previous.map((integration) => (
      integration.provider === provider
        ? { ...integration, status: currentlyConnected ? "DISCONNECTED" : "CONNECTED", connectedAt: currentlyConnected ? null : new Date().toISOString(), authType: draft.authType, webhookSecretConfigured: currentlyConnected ? integration.webhookSecretConfigured : Boolean(draft.webhookSecret.trim()) || integration.webhookSecretConfigured }
        : integration
    )));

    try {
      const endpoint = currentlyConnected ? endpoints.integrations.disconnect(provider) : endpoints.integrations.connect(provider);
      const envelope = await apiClient.request<IntegrationItem>(endpoint, {
        method: "POST",
        body: currentlyConnected
          ? {}
          : {
              authType: draft.authType,
              webhookSecret: draft.webhookSecret.trim() || undefined,
              webhookUrl: draft.slackWebhookUrl.trim() || undefined,
            },
      });
      setIntegrations((previous) => previous.map((integration) => (integration.provider === provider ? envelope.data : integration)));
      setError(null);
    } catch (toggleError) {
      setIntegrations(snapshot);
      setError(toggleError instanceof Error ? toggleError.message : "Failed to update integration");
    } finally {
      setPendingProvider(null);
    }
  }

  async function sendTestWebhook(provider: string) {
    const key = normalizeProviderKey(provider);
    const draft = drafts[key] ?? { authType: "OAuth2", webhookSecret: "", slackWebhookUrl: "", testEventType: `${provider.toLowerCase().replace(/\s+/g, ".")}.test` };
    const payload = {
      provider,
      eventType: draft.testEventType.trim() || `${provider.toLowerCase().replace(/\s+/g, ".")}.test`,
      sentAt: new Date().toISOString(),
      sample: true,
    };

    setPendingProvider(provider);
    try {
      const signature = await signWebhookPayload(draft.webhookSecret, payload);
      const envelope = await apiClient.request<WebhookReceipt>(endpoints.integrations.webhook(provider), {
        method: "POST",
        headers: signature ? { "X-Webhook-Signature": signature } : undefined,
        body: payload,
      });
      setReceipts((previous) => ({ ...previous, [key]: envelope.data }));
      setError(null);
    } catch (webhookError) {
      setError(webhookError instanceof Error ? webhookError.message : "Failed to deliver test webhook");
    } finally {
      setPendingProvider(null);
    }
  }

  const summary = useMemo(() => ({
    connected: integrations.filter((integration) => integration.status === "CONNECTED").length,
    total: integrations.length,
  }), [integrations]);

  const iconByProvider = (provider: string) => {
    const normalized = provider.toLowerCase();
    if (normalized.includes("gmail") || normalized.includes("outlook")) {
      return { icon: Mail, color: "bg-red-500" };
    }
    if (normalized.includes("slack")) {
      return { icon: MessageSquare, color: "bg-purple-500" };
    }
    if (normalized.includes("drive") || normalized.includes("dropbox")) {
      return { icon: Cloud, color: "bg-blue-500" };
    }
    return { icon: Code, color: "bg-gray-700" };
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-gray-800">Integrations</h1>
          <p className="mt-1 text-gray-600">Connect providers, set webhook secrets, and emit test payloads into workflow-service.</p>
        </div>
      </div>

      {error && <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}
      {loading && <div className="text-sm text-gray-600">Loading integrations...</div>}

      <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
        <div className="rounded-xl border border-gray-100 bg-white p-6 shadow-sm">
          <div className="flex items-start justify-between">
            <div>
              <p className="mb-1 text-sm text-gray-600">Connected Services</p>
              <p className="text-3xl font-bold text-gray-800">{summary.connected}</p>
            </div>
            <div className="rounded-lg bg-green-500 p-3"><CheckCircle size={24} className="text-white" /></div>
          </div>
        </div>

        <div className="rounded-xl border border-gray-100 bg-white p-6 shadow-sm">
          <div className="flex items-start justify-between">
            <div>
              <p className="mb-1 text-sm text-gray-600">Available Integrations</p>
              <p className="text-3xl font-bold text-gray-800">{summary.total}</p>
            </div>
            <div className="rounded-lg bg-indigo-500 p-3"><Code size={24} className="text-white" /></div>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 gap-6 md:grid-cols-2 xl:grid-cols-3">
        {integrations.map((integration) => {
          const key = normalizeProviderKey(integration.provider);
          const draft = drafts[key] ?? { authType: integration.authType, webhookSecret: "", slackWebhookUrl: "", testEventType: `${integration.provider.toLowerCase().replace(/\s+/g, ".")}.test` };
          const receipt = receipts[key];
          const iconData = iconByProvider(integration.provider);
          const Icon = iconData.icon;
          const isConnected = integration.status === "CONNECTED";

          return (
            <div key={integration.provider} className="rounded-xl border border-gray-100 bg-white p-6 shadow-sm transition-shadow hover:shadow-md">
              <div className="mb-4 flex items-start justify-between">
                <div className={`${iconData.color} rounded-lg p-3`}><Icon size={24} className="text-white" /></div>
                <div className="flex items-center gap-2">
                  {isConnected ? <CheckCircle size={20} className="text-green-500" /> : <XCircle size={20} className="text-gray-400" />}
                  <span className={`text-xs font-medium ${isConnected ? "text-green-700" : "text-gray-500"}`}>{isConnected ? "Connected" : "Not Connected"}</span>
                </div>
              </div>

              <h3 className="mb-2 text-lg font-bold text-gray-800">{integration.provider}</h3>
              <p className="mb-2 text-sm text-gray-600">Connected on {integration.connectedAt ? formatCompactDate(integration.connectedAt) : "Not yet connected"}</p>
              <p className="mb-4 text-xs text-gray-500">Webhook secret {integration.webhookSecretConfigured ? "configured on the server" : "not configured yet"}</p>

              <div className="space-y-3">
                <label className="block text-sm text-gray-700">
                  <span className="mb-1 block font-medium">Auth type</span>
                  <input value={draft.authType} onChange={(event) => updateDraft(integration.provider, { authType: event.target.value })} className="w-full rounded-lg border border-gray-300 px-3 py-2" />
                </label>

                <label className="block text-sm text-gray-700">
                  <span className="mb-1 block font-medium">Webhook secret</span>
                  <input value={draft.webhookSecret} onChange={(event) => updateDraft(integration.provider, { webhookSecret: event.target.value })} placeholder={integration.webhookSecretConfigured ? "Configured server-side; enter again to rotate" : "Optional HMAC secret"} className="w-full rounded-lg border border-gray-300 px-3 py-2" />
                </label>

                {integration.provider.toLowerCase().includes("slack") && (
                  <label className="block text-sm text-gray-700">
                    <span className="mb-1 block font-medium">Slack webhook URL</span>
                    <input value={draft.slackWebhookUrl} onChange={(event) => updateDraft(integration.provider, { slackWebhookUrl: event.target.value })} placeholder="https://hooks.slack.com/services/..." className="w-full rounded-lg border border-gray-300 px-3 py-2" />
                  </label>
                )}

                <label className="block text-sm text-gray-700">
                  <span className="mb-1 block font-medium">Test event type</span>
                  <input value={draft.testEventType} onChange={(event) => updateDraft(integration.provider, { testEventType: event.target.value })} className="w-full rounded-lg border border-gray-300 px-3 py-2" />
                </label>
              </div>

              <div className="mt-4 flex gap-2">
                <button onClick={() => void toggleConnection(integration.provider, isConnected)} className={`flex-1 rounded-lg py-2 font-medium transition-colors ${isConnected ? "bg-red-100 text-red-700 hover:bg-red-200" : "bg-indigo-600 text-white hover:bg-indigo-700"}`} disabled={pendingProvider === integration.provider}>
                  {isConnected ? "Disconnect" : "Connect"}
                </button>
                <button onClick={() => void sendTestWebhook(integration.provider)} className="flex-1 rounded-lg bg-slate-900 py-2 font-medium text-white transition-colors hover:bg-slate-800" disabled={pendingProvider === integration.provider}>
                  Test Webhook
                </button>
              </div>

              {receipt && (
                <div className="mt-4 rounded-lg border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-900">
                  <div className="font-medium">Webhook receipt</div>
                  <div>{receipt.executionsStarted} executions started from {receipt.matchedTriggers} matching triggers.</div>
                  <div className="text-xs text-emerald-700">Signature {receipt.signatureValidated ? "validated" : "not required or not matched"} · {formatDateTime(receipt.receivedAt)}</div>
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}
