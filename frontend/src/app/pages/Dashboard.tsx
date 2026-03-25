import { useEffect, useMemo, useState } from "react";
import { CheckSquare, Upload, Bot, Clock, CheckCircle, FileText, MessageSquare, TrendingUp, AlertCircle } from "lucide-react";
import { apiClient } from "../../api/client";
import { endpoints } from "../../api/endpoints";
import type { ActivityFeedItem, DashboardMetrics, NotificationItem, ServiceHealthItem } from "../../api/contracts";
import { formatRelativeTime } from "../lib/format";
import { useSession } from "../auth/session";

export default function Dashboard() {
  const { user } = useSession();
  const [metrics, setMetrics] = useState<DashboardMetrics | null>(null);
  const [activities, setActivities] = useState<ActivityFeedItem[]>([]);
  const [notifications, setNotifications] = useState<NotificationItem[]>([]);
  const [services, setServices] = useState<ServiceHealthItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const cardMetrics = useMemo(() => {
    if (!metrics) {
      return [];
    }

    return [
      { label: "Total Tasks", value: String(metrics.totalTasks), icon: CheckSquare, color: "bg-blue-500" },
      { label: "Pending Tasks", value: String(metrics.pendingTasks), icon: Clock, color: "bg-orange-500" },
      { label: "Completed Tasks", value: String(metrics.completedTasks), icon: CheckCircle, color: "bg-green-500" },
      { label: "Documents Uploaded", value: String(metrics.documentsUploaded), icon: Upload, color: "bg-purple-500" },
      { label: "AI Requests Today", value: String(metrics.aiRequestsToday), icon: Bot, color: "bg-indigo-500" },
    ];
  }, [metrics]);

  useEffect(() => {
    let active = true;

    async function loadDashboard() {
      try {
        setError(null);
        const [metricsEnvelope, activityEnvelope, notificationsEnvelope, healthEnvelope] = await Promise.all([
          apiClient.request<DashboardMetrics>(endpoints.dashboard.metrics),
          apiClient.request<ActivityFeedItem[]>(endpoints.dashboard.activity),
          apiClient.request<NotificationItem[]>(endpoints.notifications.recent),
          apiClient.request<ServiceHealthItem[]>(endpoints.dashboard.health),
        ]);

        if (!active) {
          return;
        }

        setMetrics(metricsEnvelope.data);
        setActivities(activityEnvelope.data);
        setNotifications(notificationsEnvelope.data);
        setServices(healthEnvelope.data);
      } catch (loadError) {
        if (active) {
          setError(loadError instanceof Error ? loadError.message : "Failed to load dashboard");
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    async function refreshNotifications() {
      try {
        const notificationsEnvelope = await apiClient.request<NotificationItem[]>(endpoints.notifications.recent);
        if (active) {
          setNotifications(notificationsEnvelope.data);
        }
      } catch {
        // Keep current notifications on polling failures.
      }
    }

    void loadDashboard();
    const intervalId = window.setInterval(() => {
      void refreshNotifications();
    }, 30_000);

    return () => {
      active = false;
      window.clearInterval(intervalId);
    };
  }, []);

  const displayName = user ? `${user.firstName}` : "there";
  const allSystemsOperational = services.every((service) => service.status === "UP");

  return (
    <div className="space-y-6">
      <div className="bg-gradient-to-r from-indigo-600 to-purple-600 rounded-2xl p-8 text-white">
        <h1 className="text-3xl font-bold mb-2">Welcome back, {displayName}!</h1>
        <p className="text-indigo-100">Here&apos;s what&apos;s happening with your automation platform today.</p>
      </div>

      {error && (
        <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
          {error}
        </div>
      )}

      {loading ? (
        <div className="bg-white rounded-xl p-6 shadow-sm border border-gray-100">
          <div className="w-8 h-8 border-4 border-indigo-200 border-t-indigo-600 rounded-full animate-spin"></div>
          <p className="text-sm text-gray-600 mt-3">Loading dashboard data...</p>
        </div>
      ) : (
        <>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-5 gap-6">
            {cardMetrics.map((metric) => {
              const Icon = metric.icon;
              return (
                <div key={metric.label} className="bg-white rounded-xl p-6 shadow-sm border border-gray-100">
                  <div className="flex items-start justify-between">
                    <div>
                      <p className="text-gray-600 text-sm mb-1">{metric.label}</p>
                      <p className="text-3xl font-bold text-gray-800">{metric.value}</p>
                    </div>
                    <div className={`${metric.color} p-3 rounded-lg`}>
                      <Icon size={24} className="text-white" />
                    </div>
                  </div>
                </div>
              );
            })}
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            <div className="lg:col-span-2 bg-white rounded-xl p-6 shadow-sm border border-gray-100">
              <h2 className="text-xl font-bold text-gray-800 mb-4">Activity Feed</h2>
              {activities.length === 0 ? (
                <div className="rounded-lg border border-dashed border-gray-200 bg-gray-50 px-4 py-10 text-center text-sm text-gray-500">
                  No recent activity yet.
                </div>
              ) : (
                <div className="space-y-4">
                  {activities.map((activity) => {
                    const Icon = activity.type.includes("task")
                      ? CheckSquare
                      : activity.type.includes("document")
                        ? FileText
                        : activity.type.includes("report")
                          ? TrendingUp
                          : MessageSquare;

                    const color = activity.type.includes("task")
                      ? "text-blue-500"
                      : activity.type.includes("document")
                        ? "text-purple-500"
                        : activity.type.includes("report")
                          ? "text-green-500"
                          : "text-indigo-500";

                    return (
                      <div key={`${activity.type}-${activity.occurredAt}`} className="flex items-start gap-4 p-4 bg-gray-50 rounded-lg hover:bg-gray-100 transition-colors">
                        <Icon size={20} className={color} />
                        <div className="flex-1">
                          <p className="font-medium text-gray-800">{activity.type}</p>
                          <p className="text-sm text-gray-600">{activity.description}</p>
                        </div>
                        <span className="text-xs text-gray-500">{formatRelativeTime(activity.occurredAt)}</span>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>

            <div className="space-y-6">
              <div className="bg-white rounded-xl p-6 shadow-sm border border-gray-100">
                <h2 className="text-xl font-bold text-gray-800 mb-4">AI Insights</h2>
                <div className="bg-gradient-to-br from-indigo-50 to-purple-50 rounded-lg p-4 border border-indigo-200">
                  <div className="flex items-start gap-3">
                    <Bot className="text-indigo-600 flex-shrink-0" size={24} />
                    <div>
                      <p className="font-medium text-gray-800 mb-2">AI handled {metrics?.aiRequestsToday ?? 0} requests today.</p>
                      <p className="text-sm text-gray-600">Pending tasks to review: {metrics?.pendingTasks ?? 0}.</p>
                    </div>
                  </div>
                </div>
              </div>

              <div className="bg-white rounded-xl p-6 shadow-sm border border-gray-100">
                <h2 className="text-xl font-bold text-gray-800 mb-4">Recent Notifications</h2>
                {notifications.length === 0 ? (
                  <div className="rounded-lg border border-dashed border-gray-200 bg-gray-50 px-4 py-6 text-center text-sm text-gray-500">
                    No notifications yet.
                  </div>
                ) : (
                  <div className="space-y-3">
                    {notifications.map((notification) => (
                      <div key={notification.id} className="flex items-start gap-3 p-3 bg-gray-50 rounded-lg">
                        <AlertCircle
                          size={16}
                          className={notification.read ? "text-blue-500" : "text-red-500"}
                        />
                        <div className="flex-1">
                          <p className="text-sm text-gray-800">{notification.message}</p>
                          <p className="text-xs text-gray-500 mt-1">{formatRelativeTime(notification.createdAt)}</p>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>

              <div className="bg-white rounded-xl p-6 shadow-sm border border-gray-100">
                <h2 className="text-xl font-bold text-gray-800 mb-2">System Health</h2>
                <div className="space-y-2">
                  <div className="flex items-center gap-2">
                    <div className={`w-3 h-3 rounded-full ${allSystemsOperational ? "bg-green-500 animate-pulse" : "bg-orange-500"}`}></div>
                    <span className="text-sm text-gray-600">
                      {allSystemsOperational ? "All systems operational" : "Some services need attention"}
                    </span>
                  </div>
                  <div className="space-y-1">
                    {services.map((service) => (
                      <p key={service.service} className="text-xs text-gray-500">
                        {service.service}: {service.status}
                      </p>
                    ))}
                  </div>
                </div>
              </div>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
