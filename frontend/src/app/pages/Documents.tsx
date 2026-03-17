import { Upload, FileText, File, Image, FilePlus, Search, Bot } from "lucide-react";
import { useEffect, useMemo, useRef, useState } from "react";
import { apiClient } from "../../api/client";
import { endpoints } from "../../api/endpoints";
import type { DocumentAnswer, DocumentItem } from "../../api/contracts";
import { formatCompactDate } from "../lib/format";

export default function Documents() {
  const [documents, setDocuments] = useState<DocumentItem[]>([]);
  const [selectedDocId, setSelectedDocId] = useState<string | null>(null);
  const [selectedDoc, setSelectedDoc] = useState<DocumentItem | null>(null);
  const [question, setQuestion] = useState("");
  const [aiAnswer, setAiAnswer] = useState<DocumentAnswer | null>(null);
  const [search, setSearch] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [asking, setAsking] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    let active = true;

    async function loadDocuments() {
      try {
        const envelope = await apiClient.request<{ items: DocumentItem[] }>(endpoints.documents.list);
        if (!active) {
          return;
        }
        setDocuments(envelope.data.items);
      } catch (loadError) {
        if (active) {
          setError(loadError instanceof Error ? loadError.message : "Failed to load documents");
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    async function pollProcessingDocuments() {
      try {
        if (!active) {
          return;
        }

        const envelope = await apiClient.request<{ items: DocumentItem[] }>(endpoints.documents.list);
        if (active) {
          const items = envelope.data.items;
          if (items.some((doc) => doc.processingStatus !== "READY" && doc.processingStatus !== "FAILED")) {
            setDocuments(items);
          }
        }
      } catch {
        // Ignore polling failures to avoid disruptive alerts.
      }
    }

    void loadDocuments();
    const pollId = window.setInterval(() => {
      void pollProcessingDocuments();
    }, 15_000);

    return () => {
      active = false;
      window.clearInterval(pollId);
    };
  }, []);

  async function handleSelectDocument(id: string) {
    setSelectedDocId(id);
    setAiAnswer(null);

    try {
      const envelope = await apiClient.request<DocumentItem>(endpoints.documents.detail(id));
      setSelectedDoc(envelope.data);
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : "Failed to load document details");
    }
  }

  async function handleUpload(event: React.ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    if (!file) {
      return;
    }

    const body = new FormData();
    body.append("file", file);

    try {
      const envelope = await apiClient.request<DocumentItem>(endpoints.documents.upload, {
        method: "POST",
        body,
      });
      setDocuments((previous) => [envelope.data, ...previous]);
    } catch (uploadError) {
      setError(uploadError instanceof Error ? uploadError.message : "Failed to upload document");
    } finally {
      event.target.value = "";
    }
  }

  async function askDocument(questionText: string) {
    if (!selectedDocId || !questionText.trim()) {
      return;
    }

    setAsking(true);
    setError(null);

    try {
      const envelope = await apiClient.request<DocumentAnswer>(endpoints.documents.ask(selectedDocId), {
        method: "POST",
        body: { question: questionText },
      });
      setAiAnswer(envelope.data);
    } catch (askError) {
      setError(askError instanceof Error ? askError.message : "Failed to query document");
    } finally {
      setAsking(false);
    }
  }

  const filteredDocuments = useMemo(() => documents.filter((document) => (
    document.fileName.toLowerCase().includes(search.toLowerCase())
    || (document.summary ?? "").toLowerCase().includes(search.toLowerCase())
  )), [documents, search]);

  const resolveIcon = (type: string | null) => {
    if (!type) {
      return File;
    }
    if (type.toLowerCase().includes("image")) {
      return Image;
    }
    if (type.toLowerCase().includes("pdf")) {
      return FileText;
    }
    return File;
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-gray-800">Document Intelligence</h1>
          <p className="text-gray-600 mt-1">Upload, manage, and analyze documents with AI</p>
        </div>
        <div>
          <input ref={fileInputRef} type="file" className="hidden" onChange={(event) => void handleUpload(event)} />
          <button onClick={() => fileInputRef.current?.click()} className="px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors flex items-center gap-2">
            <Upload size={18} />
            Upload Document
          </button>
        </div>
      </div>

      {error && <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}

      {loading ? (
        <div className="bg-white rounded-xl p-6 shadow-sm border border-gray-100">
          <div className="w-8 h-8 border-4 border-indigo-200 border-t-indigo-600 rounded-full animate-spin"></div>
          <p className="text-sm text-gray-600 mt-3">Loading documents...</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          <div className="lg:col-span-2 space-y-6">
            <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
              <div className="flex items-center justify-between mb-4">
                <h2 className="text-xl font-bold text-gray-800">Documents</h2>
                <div className="relative">
                  <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" size={18} />
                  <input
                    type="text"
                    value={search}
                    onChange={(event) => setSearch(event.target.value)}
                    placeholder="Search documents..."
                    className="pl-10 pr-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500"
                  />
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                {filteredDocuments.map((doc) => {
                  const Icon = resolveIcon(doc.fileType);
                  return (
                    <div
                      key={doc.id}
                      onClick={() => void handleSelectDocument(doc.id)}
                      className={`p-4 border-2 rounded-lg cursor-pointer transition-all ${
                        selectedDocId === doc.id
                          ? "border-indigo-500 bg-indigo-50"
                          : "border-gray-200 hover:border-gray-300 bg-white"
                      }`}
                    >
                      <div className="flex items-start gap-3">
                        <div className="p-3 bg-gray-100 rounded-lg">
                          <Icon size={24} className="text-gray-600" />
                        </div>
                        <div className="flex-1 min-w-0">
                          <p className="font-medium text-gray-800 truncate">{doc.fileName}</p>
                          <div className="flex items-center gap-2 mt-1">
                            <span className="text-xs text-gray-500">{doc.fileType || "Unknown"}</span>
                            <span className="text-xs text-gray-400">�</span>
                            <span className="text-xs text-gray-500">{Math.round(doc.fileSize / 1024)} KB</span>
                          </div>
                          <p className="text-xs text-gray-400 mt-1">{formatCompactDate(doc.createdAt)}</p>
                          <p className="text-xs text-indigo-600 mt-1">Status: {doc.processingStatus}</p>
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>

            {selectedDoc && (
              <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
                <h2 className="text-xl font-bold text-gray-800 mb-4">Document Viewer</h2>
                <div className="bg-gray-50 rounded-lg p-8 border-2 border-dashed border-gray-300 text-center">
                  <FilePlus size={48} className="text-gray-400 mx-auto mb-3" />
                  <p className="text-gray-600">Selected document:</p>
                  <p className="font-medium text-gray-800 mt-1">{selectedDoc.fileName}</p>
                  <p className="text-sm text-gray-600 mt-2">{selectedDoc.summary}</p>
                </div>
              </div>
            )}
          </div>

          <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
            <div className="flex items-center gap-2 mb-4">
              <Bot size={24} className="text-indigo-600" />
              <h2 className="text-xl font-bold text-gray-800">Ask AI About Document</h2>
            </div>

            {!selectedDoc ? (
              <div className="text-center py-8">
                <p className="text-gray-500">Select a document to ask questions</p>
              </div>
            ) : (
              <div className="space-y-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">Selected Document:</label>
                  <p className="text-sm text-indigo-600 font-medium">{selectedDoc.fileName}</p>
                </div>

                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">Your Question:</label>
                  <textarea
                    value={question}
                    onChange={(event) => setQuestion(event.target.value)}
                    placeholder="What is this document about?"
                    className="w-full px-4 py-3 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500 resize-none"
                    rows={4}
                  />
                </div>

                <button
                  onClick={() => void askDocument(question)}
                  disabled={asking}
                  className="w-full px-4 py-3 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors"
                >
                  {asking ? "Asking..." : "Ask AI"}
                </button>

                {aiAnswer && (
                  <div className="bg-gradient-to-br from-indigo-50 to-purple-50 rounded-lg p-4 border border-indigo-200">
                    <div className="flex items-start gap-3">
                      <Bot className="text-indigo-600 flex-shrink-0" size={20} />
                      <div>
                        <p className="font-medium text-gray-800 mb-1">AI Answer:</p>
                        <p className="text-sm text-gray-700">{aiAnswer.answer}</p>
                      </div>
                    </div>
                  </div>
                )}

                <div className="border-t border-gray-200 pt-4">
                  <p className="text-sm font-medium text-gray-700 mb-2">Suggested Questions:</p>
                  <div className="space-y-2">
                    {["Summarize this document", "What are the key points?", "Extract important dates"].map((suggestion) => (
                      <button
                        key={suggestion}
                        onClick={() => {
                          setQuestion(suggestion);
                          void askDocument(suggestion);
                        }}
                        className="w-full text-left px-3 py-2 bg-gray-50 hover:bg-gray-100 rounded-lg text-sm text-gray-700 transition-colors"
                      >
                        {suggestion}
                      </button>
                    ))}
                  </div>
                </div>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
