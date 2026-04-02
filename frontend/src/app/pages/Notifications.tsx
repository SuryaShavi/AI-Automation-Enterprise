import { useEffect, useMemo, useState } from "react";
import { CheckCircle, FileText, Upload, Bot, Bell, Trash2 } from "lucide-react";
import { useNavigate } from "react-router";
import { apiClient } from "../../api/client";
import { endpoints } from "../../api/endpoints";
import type { NotificationItem } from "../../api/contracts";
import { API_BASE_URL, AUTH_STORAGE_KEYS } from "../../config/api";
import { formatRelativeTime } from "../lib/format";

type NotificationFilter = "All" | "Unread" | "Tasks" | "AI Alerts";

export default function Notifications() {
  const navigate = useNavigate();
  const [notifications, setNotifications] = useState<NotificationItem[]>([]);
  const [filter, setFilter] = useState<NotificationFilter>("All");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    let stream: EventSource | null = null;

    function upsertNotification(incoming: NotificationItem) {
      setNotifications((previous) => {
        const withoutExisting = previous.filter((item) => item.id !== incoming.id);
        return [incoming, ...withoutExisting].sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
      });
    }

    async function loadNotifications() {
      try {
        const envelope = await apiClient.request<{ items: NotificationItem[] }>(endpoints.notifications.list);
        if (!active) {
          return;
        }
        setNotifications(envelope.data.items);
      } catch (loadError) {
        if (active) {
          setError(loadError instanceof Error ? loadError.message : "Failed to load notifications");
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    void loadNotifications();

    const accessToken = localStorage.getItem(AUTH_STORAGE_KEYS.accessToken);
    if (accessToken) {
      const streamUrl = `${API_BASE_URL}${endpoints.notifications.stream}?access_token=${encodeURIComponent(accessToken)}`;
      stream = new EventSource(streamUrl);
      stream.addEventListener("notification", (event) => {
        if (!active) {
          return;
        }
        try {
          const payload = JSON.parse(event.data) as NotificationItem;
          upsertNotification(payload);
          setError(null);
        } catch {
          // Ignore malformed stream payloads and rely on periodic sync.
        }
      });
      stream.onerror = () => {
        if (active) {
          setError((previous) => previous ?? "Live notification stream disconnected. Falling back to periodic refresh.");
        }
      };
    }

    const pollId = window.setInterval(() => {
      void loadNotifications();
    }, 30_000);

    return () => {
      active = false;
      if (stream) {
        stream.close();
      }
      window.clearInterval(pollId);
    };
  }, []);

  const filteredNotifications = useMemo(() => notifications.filter((notification) => {
    if (filter === "Unread") {
      return !notification.read;
    }
    if (filter === "Tasks") {
      return notification.type.toLowerCase().includes("task");
    }
    if (filter === "AI Alerts") {
      return notification.type.toLowerCase().includes("ai");
    }
    return true;
  }), [filter, notifications]);

  function iconForType(type: string) {
    const normalized = type.toLowerCase();
    if (normalized.includes("task")) {
      return { icon: CheckCircle, color: "text-green-500", bgColor: "bg-green-50" };
    }
    if (normalized.includes("report")) {
      return { icon: FileText, color: "text-blue-500", bgColor: "bg-blue-50" };
    }
    if (normalized.includes("document")) {
      return { icon: Upload, color: "text-purple-500", bgColor: "bg-purple-50" };
    }
    if (normalized.includes("ai")) {
      return { icon: Bot, color: "text-indigo-500", bgColor: "bg-indigo-50" };
    }
    return { icon: Bell, color: "text-gray-500", bgColor: "bg-gray-50" };
  }

  async function refreshNotifications() {
    const envelope = await apiClient.request<{ items: NotificationItem[] }>(endpoints.notifications.list);
    setNotifications(envelope.data.items);
  }

  async function handleMarkAllRead() {
    try {
      await apiClient.request<{ status: string }>(endpoints.notifications.readAll, { method: "PATCH" });
      await refreshNotifications();
    } catch (markError) {
      setError(markError instanceof Error ? markError.message : "Failed to mark all as read");
    }
  }

  async function handleClearAll() {
    try {
      await Promise.all(notifications.map((notification) => apiClient.request<{ status: string; id: string }>(endpoints.notifications.remove(notification.id), { method: "DELETE" })));
      await refreshNotifications();
    } catch (clearError) {
      setError(clearError instanceof Error ? clearError.message : "Failed to clear notifications");
    }
  }

  async function handleDelete(id: string) {
    const snapshot = notifications;
    setNotifications((previous) => previous.filter((item) => item.id !== id));

    try {
      await apiClient.request<{ status: string; id: string }>(endpoints.notifications.remove(id), { method: "DELETE" });
    } catch (deleteError) {
      setNotifications(snapshot);
      setError(deleteError instanceof Error ? deleteError.message : "Failed to delete notification");
    }
  }

  async function handleMarkRead(id: string, read: boolean) {
    if (read) {
      return;
    }

    try {
      const envelope = await apiClient.request<NotificationItem>(endpoints.notifications.read(id), { method: "PATCH" });
      setNotifications((previous) => previous.map((item) => (item.id === id ? envelope.data : item)));
    } catch (readError) {
      setError(readError instanceof Error ? readError.message : "Failed to mark notification as read");
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-gray-800">Notifications</h1>
          <p className="text-gray-600 mt-1">Stay updated with all system activities</p>
        </div>
        <div className="flex items-center gap-3">
          <button onClick={() => void handleMarkAllRead()} className="px-4 py-2 bg-gray-100 text-gray-700 rounded-lg hover:bg-gray-200 transition-colors">Mark all as read</button>
          <button onClick={() => void handleClearAll()} className="px-4 py-2 bg-red-100 text-red-700 rounded-lg hover:bg-red-200 transition-colors flex items-center gap-2">
            <Trash2 size={18} />
            Clear all
          </button>
        </div>
      </div>

      {error && <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}
      {loading && <div className="text-sm text-gray-600">Loading notifications...</div>}

      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        {[
          { label: "All", count: notifications.length },
          { label: "Unread", count: notifications.filter((n) => !n.read).length },
          { label: "Tasks", count: notifications.filter((n) => n.type.toLowerCase().includes("task")).length },
          { label: "AI Alerts", count: notifications.filter((n) => n.type.toLowerCase().includes("ai")).length },
        ].map((option) => (
          <button
            key={option.label}
            onClick={() => setFilter(option.label as NotificationFilter)}
            className={`p-4 rounded-lg border-2 transition-all ${filter === option.label ? "border-indigo-500 bg-indigo-50" : "border-gray-200 bg-white hover:border-gray-300"}`}
          >
            <p className="text-2xl font-bold text-gray-800">{option.count}</p>
            <p className="text-sm text-gray-600">{option.label}</p>
          </button>
        ))}
      </div>

      <div className="bg-white rounded-xl shadow-sm border border-gray-100">
        <div className="p-6 border-b border-gray-200">
          <h2 className="text-xl font-bold text-gray-800">Recent Notifications</h2>
        </div>

        <div className="divide-y divide-gray-200">
          {filteredNotifications.map((notification) => {
            const iconData = iconForType(notification.type);
            const Icon = iconData.icon;

            return (
              <div key={notification.id} className={`p-6 transition-colors ${!notification.read ? "bg-blue-50/50" : "bg-white hover:bg-gray-50"}`}>
                <div className="flex items-start gap-4">
                  <div className={`${iconData.bgColor} p-3 rounded-lg`}>
                    <Icon size={24} className={iconData.color} />
                  </div>

                  <button onClick={() => void handleMarkRead(notification.id, notification.read)} className="flex-1 min-w-0 text-left">
                    <div className="flex items-start justify-between gap-4">
                      <div>
                        <div className="flex items-center gap-2">
                          <h3 className="font-semibold text-gray-800">{notification.title}</h3>
                          {!notification.read && <span className="w-2 h-2 bg-blue-500 rounded-full"></span>}
                        </div>
                        <p className="text-sm text-gray-600 mt-1">{notification.message}</p>
                        <p className="text-xs text-gray-500 mt-2">{formatRelativeTime(notification.createdAt)}</p>
                      </div>
                    </div>
                  </button>

                  <button onClick={() => void handleDelete(notification.id)} className="p-2 hover:bg-gray-100 rounded-lg transition-colors flex-shrink-0">
                    <Trash2 size={16} className="text-gray-400" />
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      </div>

      <div className="bg-gradient-to-br from-indigo-50 to-purple-50 rounded-xl p-6 border border-indigo-200">
        <div className="flex items-start gap-4">
          <Bell className="text-indigo-600" size={24} />
          <div>
            <h3 className="font-semibold text-gray-800 mb-2">Notification Settings</h3>
            <p className="text-sm text-gray-600 mb-4">Customize which notifications you receive and how you want to be alerted.</p>
            <button onClick={() => navigate("/settings")} className="px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors text-sm">
              Configure Settings
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
