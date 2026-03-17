import { useEffect, useMemo, useState } from "react";
import { Mail, MessageSquare, Cloud, Code, CheckCircle, XCircle } from "lucide-react";
import { apiClient } from "../../api/client";
import { endpoints } from "../../api/endpoints";
import type { IntegrationItem } from "../../api/contracts";
import { formatCompactDate } from "../lib/format";

export default function Integrations() {
  const [integrations, setIntegrations] = useState<IntegrationItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;

    async function loadIntegrations() {
      try {
        const envelope = await apiClient.request<IntegrationItem[]>(endpoints.integrations.list);
        if (!active) {
          return;
        }
        setIntegrations(envelope.data);
      } catch (loadError) {
        if (active) {
          setError(loadError instanceof Error ? loadError.message : "Failed to load integrations");
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    void loadIntegrations();

    return () => {
      active = false;
    };
  }, []);

  async function toggleConnection(provider: string, currentlyConnected: boolean) {
    const snapshot = integrations;
    setIntegrations((previous) => previous.map((integration) => (
      integration.provider === provider
        ? { ...integration, status: currentlyConnected ? "DISCONNECTED" : "CONNECTED", connectedAt: currentlyConnected ? null : new Date().toISOString() }
        : integration
    )));

    try {
      const endpoint = currentlyConnected ? endpoints.integrations.disconnect(provider) : endpoints.integrations.connect(provider);
      const envelope = await apiClient.request<IntegrationItem>(endpoint, { method: "POST", body: {} });
      setIntegrations((previous) => previous.map((integration) => (integration.provider === provider ? envelope.data : integration)));
    } catch (toggleError) {
      setIntegrations(snapshot);
      setError(toggleError instanceof Error ? toggleError.message : "Failed to update integration");
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
          <p className="text-gray-600 mt-1">Connect your favorite tools and services</p>
        </div>
      </div>

      {error && <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}
      {loading && <div className="text-sm text-gray-600">Loading integrations...</div>}

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        <div className="bg-white rounded-xl p-6 shadow-sm border border-gray-100">
          <div className="flex items-start justify-between">
            <div>
              <p className="text-gray-600 text-sm mb-1">Connected Services</p>
              <p className="text-3xl font-bold text-gray-800">{summary.connected}</p>
            </div>
            <div className="bg-green-500 p-3 rounded-lg"><CheckCircle size={24} className="text-white" /></div>
          </div>
        </div>

        <div className="bg-white rounded-xl p-6 shadow-sm border border-gray-100">
          <div className="flex items-start justify-between">
            <div>
              <p className="text-gray-600 text-sm mb-1">Available Integrations</p>
              <p className="text-3xl font-bold text-gray-800">{summary.total}</p>
            </div>
            <div className="bg-indigo-500 p-3 rounded-lg"><Code size={24} className="text-white" /></div>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
        {integrations.map((integration) => {
          const iconData = iconByProvider(integration.provider);
          const Icon = iconData.icon;
          const isConnected = integration.status === "CONNECTED";

          return (
            <div key={integration.provider} className="bg-white rounded-xl shadow-sm border border-gray-100 p-6 hover:shadow-md transition-shadow">
              <div className="flex items-start justify-between mb-4">
                <div className={`${iconData.color} p-3 rounded-lg`}><Icon size={24} className="text-white" /></div>
                <div className="flex items-center gap-2">
                  {isConnected ? <CheckCircle size={20} className="text-green-500" /> : <XCircle size={20} className="text-gray-400" />}
                  <span className={`text-xs font-medium ${isConnected ? "text-green-700" : "text-gray-500"}`}>{isConnected ? "Connected" : "Not Connected"}</span>
                </div>
              </div>

              <h3 className="text-lg font-bold text-gray-800 mb-2">{integration.provider}</h3>
              <p className="text-sm text-gray-600 mb-2">Auth Type: {integration.authType}</p>
              {integration.connectedAt && <p className="text-xs text-gray-500 mb-4">Connected on {formatCompactDate(integration.connectedAt)}</p>}

              <button onClick={() => void toggleConnection(integration.provider, isConnected)} className={`w-full py-2 rounded-lg font-medium transition-colors ${isConnected ? "bg-red-100 text-red-700 hover:bg-red-200" : "bg-indigo-600 text-white hover:bg-indigo-700"}`}>
                {isConnected ? "Disconnect" : "Connect"}
              </button>
            </div>
          );
        })}
      </div>
    </div>
  );
}
