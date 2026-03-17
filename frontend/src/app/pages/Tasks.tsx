import { useEffect, useMemo, useState } from "react";
import { Plus, List, LayoutGrid, Edit, Trash2 } from "lucide-react";
import { apiClient } from "../../api/client";
import { endpoints } from "../../api/endpoints";
import type { BoardColumn, TaskItem } from "../../api/contracts";
import { formatCompactDate, makeIdempotencyKey } from "../lib/format";

interface TaskFormState {
  title: string;
  description: string;
  assigneeUserId: string;
  priority: string;
  dueAt: string;
  status: string;
}

const DEFAULT_TASK_FORM: TaskFormState = {
  title: "",
  description: "",
  assigneeUserId: "",
  priority: "MEDIUM",
  dueAt: "",
  status: "PENDING",
};

export default function Tasks() {
  const [viewMode, setViewMode] = useState<"table" | "kanban">("table");
  const [tasks, setTasks] = useState<TaskItem[]>([]);
  const [boardColumns, setBoardColumns] = useState<BoardColumn[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showModal, setShowModal] = useState(false);
  const [editingTaskId, setEditingTaskId] = useState<string | null>(null);
  const [form, setForm] = useState<TaskFormState>(DEFAULT_TASK_FORM);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let active = true;

    async function loadTasks() {
      try {
        setError(null);
        const [listEnvelope, boardEnvelope] = await Promise.all([
          apiClient.request<{ items: TaskItem[] }>(endpoints.tasks.list),
          apiClient.request<{ columns: BoardColumn[] }>(endpoints.tasks.board),
        ]);

        if (!active) {
          return;
        }

        setTasks(listEnvelope.data.items);
        setBoardColumns(boardEnvelope.data.columns);
      } catch (loadError) {
        if (active) {
          setError(loadError instanceof Error ? loadError.message : "Failed to load tasks");
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    void loadTasks();

    return () => {
      active = false;
    };
  }, []);

  async function refreshBoard() {
    const boardEnvelope = await apiClient.request<{ columns: BoardColumn[] }>(endpoints.tasks.board);
    setBoardColumns(boardEnvelope.data.columns);
  }

  function openCreate() {
    setEditingTaskId(null);
    setForm(DEFAULT_TASK_FORM);
    setShowModal(true);
  }

  function openEdit(task: TaskItem) {
    setEditingTaskId(task.id);
    setForm({
      title: task.title,
      description: task.description,
      assigneeUserId: task.assigneeUserId,
      priority: task.priority,
      dueAt: task.dueAt?.slice(0, 16) ?? "",
      status: task.status,
    });
    setShowModal(true);
  }

  async function handleSave() {
    if (!form.title.trim()) {
      return;
    }

    setSaving(true);
    setError(null);

    if (!editingTaskId) {
      const tempId = `temp-${Date.now()}`;
      const optimistic: TaskItem = {
        id: tempId,
        title: form.title,
        description: form.description,
        assigneeUserId: form.assigneeUserId,
        priority: form.priority,
        status: "PENDING",
        dueAt: form.dueAt ? new Date(form.dueAt).toISOString() : new Date().toISOString(),
        updatedAt: new Date().toISOString(),
        source: "manual",
      };

      setTasks((previous) => [optimistic, ...previous]);
      setShowModal(false);

      try {
        const envelope = await apiClient.request<TaskItem>(endpoints.tasks.create, {
          method: "POST",
          idempotencyKey: makeIdempotencyKey("task"),
          body: {
            title: form.title,
            description: form.description,
            assigneeUserId: form.assigneeUserId || undefined,
            priority: form.priority,
            dueAt: form.dueAt ? new Date(form.dueAt).toISOString() : undefined,
            source: "manual",
          },
        });

        setTasks((previous) => previous.map((task) => (task.id === tempId ? envelope.data : task)));
        await refreshBoard();
      } catch (saveError) {
        setTasks((previous) => previous.filter((task) => task.id !== tempId));
        setError(saveError instanceof Error ? saveError.message : "Failed to create task");
      } finally {
        setSaving(false);
      }

      return;
    }

    const snapshot = tasks;
    setTasks((previous) => previous.map((task) => (
      task.id === editingTaskId
        ? {
            ...task,
            title: form.title,
            description: form.description,
            assigneeUserId: form.assigneeUserId,
            priority: form.priority,
            status: form.status,
            dueAt: form.dueAt ? new Date(form.dueAt).toISOString() : task.dueAt,
          }
        : task
    )));
    setShowModal(false);

    try {
      await apiClient.request<TaskItem>(endpoints.tasks.update(editingTaskId), {
        method: "PATCH",
        body: {
          title: form.title,
          description: form.description,
          assigneeUserId: form.assigneeUserId || undefined,
          priority: form.priority,
          status: form.status,
          dueAt: form.dueAt ? new Date(form.dueAt).toISOString() : undefined,
        },
      });
      await refreshBoard();
    } catch (saveError) {
      setTasks(snapshot);
      setError(saveError instanceof Error ? saveError.message : "Failed to update task");
    } finally {
      setSaving(false);
    }
  }

  async function handleDelete(taskId: string) {
    if (!window.confirm("Delete this task?")) {
      return;
    }

    const snapshot = tasks;
    setTasks((previous) => previous.filter((task) => task.id !== taskId));

    try {
      await apiClient.request<{ status: string; id: string }>(endpoints.tasks.remove(taskId), { method: "DELETE" });
      await refreshBoard();
    } catch (deleteError) {
      setTasks(snapshot);
      setError(deleteError instanceof Error ? deleteError.message : "Failed to delete task");
    }
  }

  const groupedBoard = useMemo(() => {
    if (boardColumns.length > 0) {
      return boardColumns;
    }

    return ["PENDING", "IN_PROGRESS", "COMPLETED"].map((status) => ({
      status,
      tasks: tasks.filter((task) => task.status === status),
    }));
  }, [boardColumns, tasks]);

  const getPriorityColor = (priority: string) => {
    switch (priority) {
      case "URGENT":
        return "bg-red-100 text-red-700";
      case "HIGH":
        return "bg-orange-100 text-orange-700";
      case "MEDIUM":
        return "bg-blue-100 text-blue-700";
      default:
        return "bg-gray-100 text-gray-700";
    }
  };

  const getStatusColor = (status: string) => {
    switch (status) {
      case "COMPLETED":
        return "bg-green-100 text-green-700";
      case "IN_PROGRESS":
        return "bg-blue-100 text-blue-700";
      default:
        return "bg-orange-100 text-orange-700";
    }
  };

  const getStatusLabel = (status: string) => status.replace("_", " ");

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-gray-800">Task Management</h1>
          <p className="text-gray-600 mt-1">Organize and track your team&apos;s tasks</p>
        </div>
        <div className="flex items-center gap-3">
          <div className="flex bg-white rounded-lg border border-gray-200 p-1">
            <button
              onClick={() => setViewMode("table")}
              className={`px-3 py-2 rounded-md transition-colors ${
                viewMode === "table" ? "bg-indigo-600 text-white" : "text-gray-600 hover:bg-gray-100"
              }`}
            >
              <List size={18} />
            </button>
            <button
              onClick={() => setViewMode("kanban")}
              className={`px-3 py-2 rounded-md transition-colors ${
                viewMode === "kanban" ? "bg-indigo-600 text-white" : "text-gray-600 hover:bg-gray-100"
              }`}
            >
              <LayoutGrid size={18} />
            </button>
          </div>
          <button onClick={openCreate} className="px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors flex items-center gap-2">
            <Plus size={18} />
            Create Task
          </button>
        </div>
      </div>

      {error && <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}

      {loading ? (
        <div className="bg-white rounded-xl p-6 shadow-sm border border-gray-100">
          <div className="w-8 h-8 border-4 border-indigo-200 border-t-indigo-600 rounded-full animate-spin"></div>
          <p className="text-sm text-gray-600 mt-3">Loading tasks...</p>
        </div>
      ) : viewMode === "table" ? (
        <div className="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead className="bg-gray-50 border-b border-gray-200">
                <tr>
                  <th className="px-6 py-3 text-left text-xs font-semibold text-gray-600 uppercase tracking-wider">Task Name</th>
                  <th className="px-6 py-3 text-left text-xs font-semibold text-gray-600 uppercase tracking-wider">Description</th>
                  <th className="px-6 py-3 text-left text-xs font-semibold text-gray-600 uppercase tracking-wider">Assigned User</th>
                  <th className="px-6 py-3 text-left text-xs font-semibold text-gray-600 uppercase tracking-wider">Priority</th>
                  <th className="px-6 py-3 text-left text-xs font-semibold text-gray-600 uppercase tracking-wider">Deadline</th>
                  <th className="px-6 py-3 text-left text-xs font-semibold text-gray-600 uppercase tracking-wider">Status</th>
                  <th className="px-6 py-3 text-left text-xs font-semibold text-gray-600 uppercase tracking-wider">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-200">
                {tasks.map((task) => (
                  <tr key={task.id} className="hover:bg-gray-50 transition-colors">
                    <td className="px-6 py-4"><p className="font-medium text-gray-800">{task.title}</p></td>
                    <td className="px-6 py-4"><p className="text-sm text-gray-600 max-w-xs">{task.description}</p></td>
                    <td className="px-6 py-4"><p className="text-sm text-gray-800">{task.assigneeUserId || "Unassigned"}</p></td>
                    <td className="px-6 py-4"><span className={`px-3 py-1 rounded-full text-xs font-medium ${getPriorityColor(task.priority)}`}>{task.priority}</span></td>
                    <td className="px-6 py-4"><p className="text-sm text-gray-600">{formatCompactDate(task.dueAt)}</p></td>
                    <td className="px-6 py-4"><span className={`px-3 py-1 rounded-full text-xs font-medium ${getStatusColor(task.status)}`}>{getStatusLabel(task.status)}</span></td>
                    <td className="px-6 py-4">
                      <div className="flex items-center gap-2">
                        <button onClick={() => openEdit(task)} className="p-2 hover:bg-gray-100 rounded-lg transition-colors"><Edit size={16} className="text-gray-600" /></button>
                        <button onClick={() => void handleDelete(task.id)} className="p-2 hover:bg-red-50 rounded-lg transition-colors"><Trash2 size={16} className="text-red-600" /></button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          {groupedBoard.map((column) => (
            <div key={column.status} className="bg-white rounded-xl shadow-sm border border-gray-100 p-4">
              <div className="flex items-center justify-between mb-4">
                <h3 className="font-semibold text-gray-800">{getStatusLabel(column.status)}</h3>
                <span className="px-2 py-1 bg-gray-100 text-gray-600 rounded-full text-xs">{column.tasks.length}</span>
              </div>
              <div className="space-y-3">
                {column.tasks.map((task) => (
                  <div key={task.id} className="bg-gray-50 rounded-lg p-4 border border-gray-200">
                    <h4 className="font-medium text-gray-800 mb-2">{task.title}</h4>
                    <p className="text-sm text-gray-600 mb-3">{task.description}</p>
                    <div className="flex items-center justify-between">
                      <span className={`px-2 py-1 rounded-full text-xs font-medium ${getPriorityColor(task.priority)}`}>{task.priority}</span>
                      <p className="text-xs text-gray-500">{formatCompactDate(task.dueAt)}</p>
                    </div>
                    <div className="mt-3 pt-3 border-t border-gray-200">
                      <p className="text-xs text-gray-600">{task.assigneeUserId || "Unassigned"}</p>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          ))}
        </div>
      )}

      {showModal && (
        <div className="fixed inset-0 bg-black/30 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-xl shadow-lg w-full max-w-xl p-6 space-y-4">
            <h2 className="text-xl font-bold text-gray-800">{editingTaskId ? "Edit Task" : "Create Task"}</h2>
            <input value={form.title} onChange={(event) => setForm((previous) => ({ ...previous, title: event.target.value }))} placeholder="Title" className="w-full px-4 py-2 border border-gray-300 rounded-lg" />
            <textarea value={form.description} onChange={(event) => setForm((previous) => ({ ...previous, description: event.target.value }))} placeholder="Description" className="w-full px-4 py-2 border border-gray-300 rounded-lg" rows={3} />
            <input value={form.assigneeUserId} onChange={(event) => setForm((previous) => ({ ...previous, assigneeUserId: event.target.value }))} placeholder="Assignee User Id" className="w-full px-4 py-2 border border-gray-300 rounded-lg" />
            <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
              <select value={form.priority} onChange={(event) => setForm((previous) => ({ ...previous, priority: event.target.value }))} className="px-3 py-2 border border-gray-300 rounded-lg">
                <option value="LOW">LOW</option>
                <option value="MEDIUM">MEDIUM</option>
                <option value="HIGH">HIGH</option>
                <option value="URGENT">URGENT</option>
              </select>
              <select value={form.status} onChange={(event) => setForm((previous) => ({ ...previous, status: event.target.value }))} className="px-3 py-2 border border-gray-300 rounded-lg" disabled={!editingTaskId}>
                <option value="PENDING">PENDING</option>
                <option value="IN_PROGRESS">IN_PROGRESS</option>
                <option value="COMPLETED">COMPLETED</option>
              </select>
              <input type="datetime-local" value={form.dueAt} onChange={(event) => setForm((previous) => ({ ...previous, dueAt: event.target.value }))} className="px-3 py-2 border border-gray-300 rounded-lg" />
            </div>
            <div className="flex justify-end gap-2">
              <button onClick={() => setShowModal(false)} className="px-4 py-2 border border-gray-300 rounded-lg">Cancel</button>
              <button onClick={() => void handleSave()} disabled={saving} className="px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700">{saving ? "Saving..." : "Save"}</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
