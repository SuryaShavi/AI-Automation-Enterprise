import { useEffect, useMemo, useState } from "react";
import { User, Lock, Bell, Save } from "lucide-react";
import { apiClient } from "../../api/client";
import { endpoints } from "../../api/endpoints";
import type { UserProfile } from "../../api/contracts";
import { useSession } from "../auth/session";

type SettingsTab = "Profile" | "Security" | "Notifications";

interface ProfileForm {
  firstName: string;
  lastName: string;
  email: string;
  role: string;
}

interface SecurityForm {
  currentPassword: string;
  newPassword: string;
  confirmPassword: string;
}

const DEFAULT_SECURITY_FORM: SecurityForm = {
  currentPassword: "",
  newPassword: "",
  confirmPassword: "",
};

export default function Settings() {
  const { setUser } = useSession();
  const [activeTab, setActiveTab] = useState<SettingsTab>("Profile");
  const [userProfile, setUserProfile] = useState<UserProfile | null>(null);
  const [profileForm, setProfileForm] = useState<ProfileForm>({ firstName: "", lastName: "", email: "", role: "" });
  const [securityForm, setSecurityForm] = useState<SecurityForm>(DEFAULT_SECURITY_FORM);
  const [saving, setSaving] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  useEffect(() => {
    let active = true;

    async function loadProfile() {
      try {
        const envelope = await apiClient.request<UserProfile>(endpoints.auth.user);
        if (!active) {
          return;
        }

        setUserProfile(envelope.data);
        setProfileForm({
          firstName: envelope.data.firstName,
          lastName: envelope.data.lastName,
          email: envelope.data.email,
          role: envelope.data.roles[0] ?? "EMPLOYEE",
        });
      } catch (loadError) {
        if (active) {
          setError(loadError instanceof Error ? loadError.message : "Failed to load profile");
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    void loadProfile();

    return () => {
      active = false;
    };
  }, []);

  const notificationPreferences = useMemo(() => {
    const preferences = userProfile?.preferences ?? {};
    return [
      { key: "notifications.email", label: "Email notifications", sublabel: "Receive email alerts for important updates" },
      { key: "notifications.tasks", label: "Task assignments", sublabel: "Get notified when tasks are assigned to you" },
      { key: "notifications.ai", label: "AI insights", sublabel: "Receive AI-generated insights and suggestions" },
      { key: "notifications.weekly", label: "Weekly reports", sublabel: "Get weekly productivity and analytics reports" },
      { key: "notifications.system", label: "System alerts", sublabel: "Critical system notifications and updates" },
    ].map((item) => ({
      ...item,
      checked: preferences[item.key] === "true",
    }));
  }, [userProfile]);

  async function refreshProfile() {
    const envelope = await apiClient.request<UserProfile>(endpoints.auth.user);
    setUserProfile(envelope.data);
    setUser(envelope.data);
    setProfileForm({
      firstName: envelope.data.firstName,
      lastName: envelope.data.lastName,
      email: envelope.data.email,
      role: envelope.data.roles[0] ?? "EMPLOYEE",
    });
  }

  async function handleSaveProfile() {
    setSaving(true);
    setError(null);
    setSuccess(null);

    try {
      const envelope = await apiClient.request<UserProfile>(endpoints.auth.user, {
        method: "PATCH",
        body: {
          firstName: profileForm.firstName,
          lastName: profileForm.lastName,
        },
      });

      setUserProfile(envelope.data);
      setUser(envelope.data);
      setSuccess("Profile updated");
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : "Failed to update profile");
    } finally {
      setSaving(false);
    }
  }

  async function handleSavePassword() {
    if (securityForm.newPassword !== securityForm.confirmPassword) {
      setError("New password and confirmation do not match");
      return;
    }

    setSaving(true);
    setError(null);
    setSuccess(null);

    try {
      await apiClient.request<{ status: string }>(endpoints.auth.userPassword, {
        method: "PATCH",
        body: {
          currentPassword: securityForm.currentPassword,
          newPassword: securityForm.newPassword,
        },
      });

      setSecurityForm(DEFAULT_SECURITY_FORM);
      setSuccess("Password updated");
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : "Failed to update password");
    } finally {
      setSaving(false);
    }
  }

  async function handleEnableTwoFactor() {
    try {
      await apiClient.request<{ status: string; method: string }>(endpoints.auth.enableTwoFactor, {
        method: "POST",
        body: { method: "TOTP" },
      });
      await refreshProfile();
      setSuccess("Two-factor authentication enabled");
    } catch (enableError) {
      setError(enableError instanceof Error ? enableError.message : "Failed to enable 2FA");
    }
  }

  async function handlePreferenceToggle(key: string, value: boolean) {
    if (!userProfile) {
      return;
    }

    const nextPreferences = {
      ...userProfile.preferences,
      [key]: String(value),
    };

    setUserProfile({ ...userProfile, preferences: nextPreferences });

    try {
      const envelope = await apiClient.request<UserProfile>(endpoints.auth.userPreferences, {
        method: "PATCH",
        body: { preferences: { [key]: String(value) } },
      });
      setUserProfile(envelope.data);
      setUser(envelope.data);
    } catch (toggleError) {
      setError(toggleError instanceof Error ? toggleError.message : "Failed to update preferences");
      setUserProfile(userProfile);
    }
  }

  function handleCancel() {
    if (!userProfile) {
      return;
    }

    setProfileForm({
      firstName: userProfile.firstName,
      lastName: userProfile.lastName,
      email: userProfile.email,
      role: userProfile.roles[0] ?? "EMPLOYEE",
    });
    setSecurityForm(DEFAULT_SECURITY_FORM);
  }

  if (loading) {
    return (
      <div className="bg-white rounded-xl p-6 shadow-sm border border-gray-100">
        <div className="w-8 h-8 border-4 border-indigo-200 border-t-indigo-600 rounded-full animate-spin"></div>
        <p className="text-sm text-gray-600 mt-3">Loading settings...</p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold text-gray-800">Settings</h1>
        <p className="text-gray-600 mt-1">Manage your account and preferences</p>
      </div>

      {error && <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}
      {success && <div className="rounded-lg border border-green-200 bg-green-50 px-4 py-3 text-sm text-green-700">{success}</div>}

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-4">
          <nav className="space-y-2">
            {[
              { icon: User, label: "Profile" as const },
              { icon: Lock, label: "Security" as const },
              { icon: Bell, label: "Notifications" as const },
            ].map((item) => {
              const Icon = item.icon;
              return (
                <button
                  key={item.label}
                  onClick={() => setActiveTab(item.label)}
                  className={`w-full flex items-center gap-3 px-4 py-3 rounded-lg transition-colors ${activeTab === item.label ? "bg-indigo-50 text-indigo-700 font-medium" : "text-gray-700 hover:bg-gray-50"}`}
                >
                  <Icon size={20} />
                  <span>{item.label}</span>
                </button>
              );
            })}
          </nav>
        </div>

        <div className="lg:col-span-2 space-y-6">
          {activeTab === "Profile" && (
            <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
              <div className="flex items-center gap-3 mb-6"><User className="text-indigo-600" size={24} /><h2 className="text-xl font-bold text-gray-800">Profile Information</h2></div>
              <div className="space-y-4">
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-2">First Name</label>
                    <input type="text" value={profileForm.firstName} onChange={(event) => setProfileForm((previous) => ({ ...previous, firstName: event.target.value }))} className="w-full px-4 py-2 border border-gray-300 rounded-lg" />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-2">Last Name</label>
                    <input type="text" value={profileForm.lastName} onChange={(event) => setProfileForm((previous) => ({ ...previous, lastName: event.target.value }))} className="w-full px-4 py-2 border border-gray-300 rounded-lg" />
                  </div>
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">Email Address</label>
                  <input type="email" value={profileForm.email} disabled className="w-full px-4 py-2 border border-gray-300 rounded-lg bg-gray-50 text-gray-500" />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">Role</label>
                  <input type="text" value={profileForm.role} disabled className="w-full px-4 py-2 border border-gray-300 rounded-lg bg-gray-50 text-gray-500" />
                </div>
              </div>
            </div>
          )}

          {activeTab === "Security" && (
            <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
              <div className="flex items-center gap-3 mb-6"><Lock className="text-indigo-600" size={24} /><h2 className="text-xl font-bold text-gray-800">Security</h2></div>
              <div className="space-y-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">Current Password</label>
                  <input type="password" value={securityForm.currentPassword} onChange={(event) => setSecurityForm((previous) => ({ ...previous, currentPassword: event.target.value }))} className="w-full px-4 py-2 border border-gray-300 rounded-lg" />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">New Password</label>
                  <input type="password" value={securityForm.newPassword} onChange={(event) => setSecurityForm((previous) => ({ ...previous, newPassword: event.target.value }))} className="w-full px-4 py-2 border border-gray-300 rounded-lg" />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">Confirm New Password</label>
                  <input type="password" value={securityForm.confirmPassword} onChange={(event) => setSecurityForm((previous) => ({ ...previous, confirmPassword: event.target.value }))} className="w-full px-4 py-2 border border-gray-300 rounded-lg" />
                </div>
                <div className="pt-4 border-t border-gray-200">
                  <div className="flex items-center justify-between">
                    <div>
                      <p className="font-medium text-gray-800">Two-Factor Authentication</p>
                      <p className="text-sm text-gray-600">Add an extra layer of security to your account</p>
                    </div>
                    <button onClick={() => void handleEnableTwoFactor()} className="px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors text-sm">Enable 2FA</button>
                  </div>
                </div>
              </div>
            </div>
          )}

          {activeTab === "Notifications" && (
            <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
              <div className="flex items-center gap-3 mb-6"><Bell className="text-indigo-600" size={24} /><h2 className="text-xl font-bold text-gray-800">Notification Preferences</h2></div>
              <div className="space-y-4">
                {notificationPreferences.map((item) => (
                  <div key={item.key} className="flex items-center justify-between py-3 border-b border-gray-200 last:border-0">
                    <div>
                      <p className="font-medium text-gray-800">{item.label}</p>
                      <p className="text-sm text-gray-600">{item.sublabel}</p>
                    </div>
                    <label className="relative inline-flex items-center cursor-pointer">
                      <input type="checkbox" checked={item.checked} onChange={(event) => void handlePreferenceToggle(item.key, event.target.checked)} className="sr-only peer" />
                      <div className="w-11 h-6 bg-gray-200 peer-focus:outline-none peer-focus:ring-4 peer-focus:ring-indigo-300 rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-indigo-600"></div>
                    </label>
                  </div>
                ))}
              </div>
            </div>
          )}

          <div className="flex justify-end gap-3">
            <button onClick={handleCancel} className="px-6 py-2 border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50 transition-colors">Cancel</button>
            {activeTab === "Security" ? (
              <button onClick={() => void handleSavePassword()} disabled={saving} className="px-6 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors flex items-center gap-2"><Save size={18} />Save Changes</button>
            ) : activeTab === "Profile" ? (
              <button onClick={() => void handleSaveProfile()} disabled={saving} className="px-6 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors flex items-center gap-2"><Save size={18} />Save Changes</button>
            ) : null}
          </div>
        </div>
      </div>
    </div>
  );
}
