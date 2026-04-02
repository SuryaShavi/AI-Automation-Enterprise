import { useEffect, useRef, useState } from "react";
import { Send, Paperclip, Bot, User } from "lucide-react";
import { apiClient } from "../../api/client";
import { endpoints } from "../../api/endpoints";
import type { AttachmentReceipt, ChatMessage, ChatReply, ChatSummary, DocumentItem } from "../../api/contracts";

function createLocalChatId() {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }
  // Fallback UUID v4 generation (minimal implementation)
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
    var r = Math.random() * 16 | 0, v = c == 'x' ? r : (r & 0x3 | 0x8);
    return v.toString(16);
  });
}

export default function AIAssistant() {
  const [chats, setChats] = useState<ChatSummary[]>([]);
  const [activeChatId, setActiveChatId] = useState<string>(createLocalChatId());
  const [serverChatId, setServerChatId] = useState<string | null>(null);
  const [attachmentRefs, setAttachmentRefs] = useState<string[]>([]);
  const [attachedFiles, setAttachedFiles] = useState<string[]>([]);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [inputMessage, setInputMessage] = useState("");
  const [loading, setLoading] = useState(true);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const attachmentInputRef = useRef<HTMLInputElement>(null);

  const suggestedPrompts = [
    "What tasks are pending?",
    "Summarize today's emails",
    "Generate weekly report",
    "Explain HR policy",
  ];

  useEffect(() => {
    let active = true;

    async function loadChats() {
      try {
        const envelope = await apiClient.request<ChatSummary[]>(endpoints.ai.chats);
        if (!active) {
          return;
        }
        setChats(envelope.data);
        setError(null);
      } catch (loadError) {
        if (active) {
          setError(loadError instanceof Error ? loadError.message : "Failed to load chat history");
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    void loadChats();

    return () => {
      active = false;
    };
  }, []);

  async function loadMessages(chatId: string) {
    try {
      setError(null);
      const envelope = await apiClient.request<ChatMessage[]>(endpoints.ai.messages(chatId));
      setActiveChatId(chatId);
      setServerChatId(chatId);
      setAttachmentRefs([]);
      setAttachedFiles([]);
      setMessages(envelope.data);
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : "Failed to load messages");
    }
  }

  function handleNewChat() {
    const newChatId = createLocalChatId();
    setActiveChatId(newChatId);
    setServerChatId(null);
    setAttachmentRefs([]);
    setAttachedFiles([]);
    setError(null);
    setMessages([]);
  }

  async function handleSend() {
    if (!inputMessage.trim()) {
      return;
    }

    setSending(true);
    setError(null);

    const optimistic: ChatMessage = {
      id: `local-${Date.now()}`,
      role: "user",
      content: inputMessage,
      createdAt: new Date().toISOString(),
    };

    setMessages((previous) => [...previous, optimistic]);
    const prompt = inputMessage;
    setInputMessage("");

    try {
      const envelope = await apiClient.request<ChatReply>(endpoints.ai.chat, {
        method: "POST",
        body: {
          chatId: activeChatId,
          prompt,
          mode: "general",
          attachments: attachmentRefs,
        },
      });

      setActiveChatId(envelope.data.chatId);
      setServerChatId(envelope.data.chatId);
      setMessages((previous) => [...previous.filter((message) => message.id !== optimistic.id), optimistic, envelope.data.message]);

      const chatsEnvelope = await apiClient.request<ChatSummary[]>(endpoints.ai.chats);
      setChats(chatsEnvelope.data);
    } catch (sendError) {
      setMessages((previous) => previous.filter((message) => message.id !== optimistic.id));
      setError(sendError instanceof Error ? sendError.message : "Failed to send chat prompt");
      setInputMessage(prompt);
    } finally {
      setSending(false);
    }
  }

  async function handleAttachment(event: React.ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    if (!file) {
      return;
    }

    let targetChatId = serverChatId;

    try {
      if (!targetChatId) {
        const chatEnvelope = await apiClient.request<ChatSummary>(endpoints.ai.createChat, {
          method: "POST",
        });
        targetChatId = chatEnvelope.data.id;
        setServerChatId(targetChatId);
        setActiveChatId(targetChatId);
        setChats((previous) => [chatEnvelope.data, ...previous.filter((chat) => chat.id !== chatEnvelope.data.id)]);
      }

      const uploadBody = new FormData();
      uploadBody.append("file", file);
      const documentEnvelope = await apiClient.request<DocumentItem>(endpoints.documents.upload, {
        method: "POST",
        body: uploadBody,
      });

      await apiClient.request<AttachmentReceipt>(endpoints.ai.attachments(targetChatId), {
        method: "POST",
        body: {
          fileName: file.name,
          contentType: file.type || "application/octet-stream",
          size: file.size,
          documentId: documentEnvelope.data.id,
          metadata: { source: "ui", storagePath: `document:${documentEnvelope.data.id}` },
        },
      });

      setAttachmentRefs((previous) => {
        if (previous.includes(documentEnvelope.data.id)) {
          return previous;
        }
        return [...previous, documentEnvelope.data.id];
      });
      setAttachedFiles((previous) => [...previous, documentEnvelope.data.fileName]);
      setError(null);
    } catch (attachError) {
      setError(attachError instanceof Error ? attachError.message : "Failed to upload attachment");
    } finally {
      event.target.value = "";
    }
  }

  return (
    <div className="h-[calc(100vh-8rem)] flex gap-6">
      <div className="w-64 bg-white rounded-xl shadow-sm border border-gray-100 p-4">
        <button onClick={handleNewChat} className="w-full bg-indigo-600 text-white py-2 rounded-lg mb-4 hover:bg-indigo-700 transition-colors">
          + New Chat
        </button>
        <h3 className="text-sm font-semibold text-gray-700 mb-3">Chat History</h3>
        <div className="space-y-2">
          {loading && <p className="text-xs text-gray-500">Loading chats...</p>}
          {chats.map((chat) => (
            <button
              key={chat.id}
              onClick={() => void loadMessages(chat.id)}
              className={`w-full text-left p-3 rounded-lg cursor-pointer transition-colors ${
                chat.id === activeChatId ? "bg-indigo-50 text-indigo-700" : "hover:bg-gray-50 text-gray-700"
              }`}
            >
              <p className="text-sm truncate">{chat.title}</p>
            </button>
          ))}
        </div>
      </div>

      <div className="flex-1 bg-white rounded-xl shadow-sm border border-gray-100 flex flex-col">
        <div className="p-6 border-b border-gray-200">
          <h1 className="text-2xl font-bold text-gray-800">AI Assistant</h1>
          <p className="text-gray-600">Ask me anything about your work</p>
          {error && <p className="text-sm text-red-600 mt-2">{error}</p>}
        </div>

        <div className="flex-1 overflow-y-auto p-6 space-y-4">
          {messages.map((message) => (
            <div key={message.id} className={`flex gap-3 ${message.role === "user" ? "justify-end" : "justify-start"}`}>
              {message.role === "assistant" && (
                <div className="w-8 h-8 bg-indigo-600 rounded-full flex items-center justify-center flex-shrink-0">
                  <Bot size={18} className="text-white" />
                </div>
              )}
              <div className={`max-w-2xl ${message.role === "user" ? "bg-indigo-600 text-white" : "bg-gray-100 text-gray-800"} rounded-2xl px-4 py-3`}>
                <p className="whitespace-pre-wrap">{message.content}</p>
                <p className={`text-xs mt-1 ${message.role === "user" ? "text-indigo-200" : "text-gray-500"}`}>
                  {new Date(message.createdAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
                </p>
              </div>
              {message.role === "user" && (
                <div className="w-8 h-8 bg-gray-600 rounded-full flex items-center justify-center flex-shrink-0">
                  <User size={18} className="text-white" />
                </div>
              )}
            </div>
          ))}

          {sending && (
            <div className="flex gap-3">
              <div className="w-8 h-8 bg-indigo-600 rounded-full flex items-center justify-center">
                <Bot size={18} className="text-white" />
              </div>
              <div className="bg-gray-100 rounded-2xl px-4 py-3">
                <div className="flex gap-1">
                  <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce"></div>
                  <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: "0.2s" }}></div>
                  <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: "0.4s" }}></div>
                </div>
              </div>
            </div>
          )}
        </div>

        <div className="p-4 border-t border-gray-200">
          <div className="mb-3">
            <p className="text-sm text-gray-600 mb-2">Suggested prompts:</p>
            <div className="flex flex-wrap gap-2">
              {suggestedPrompts.map((prompt) => (
                <button key={prompt} onClick={() => setInputMessage(prompt)} className="px-3 py-1 bg-gray-100 hover:bg-gray-200 rounded-full text-sm text-gray-700 transition-colors">
                  {prompt}
                </button>
              ))}
            </div>
          </div>

          {attachedFiles.length > 0 && (
            <div className="mb-3">
              <p className="text-sm text-gray-600 mb-2">Attached for analysis:</p>
              <div className="flex flex-wrap gap-2">
                {attachedFiles.map((name, index) => (
                  <span key={`${name}-${index}`} className="px-3 py-1 bg-indigo-50 text-indigo-700 rounded-full text-sm">
                    {name}
                  </span>
                ))}
              </div>
            </div>
          )}

          <div className="flex gap-2">
            <input ref={attachmentInputRef} type="file" className="hidden" onChange={(event) => void handleAttachment(event)} />
            <button
              onClick={() => attachmentInputRef.current?.click()}
              title="Attach a file"
              className="p-3 hover:bg-gray-100 rounded-lg transition-colors"
            >
              <Paperclip size={20} className="text-gray-600" />
            </button>
            <input
              type="text"
              value={inputMessage}
              onChange={(event) => setInputMessage(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === "Enter") {
                  event.preventDefault();
                  void handleSend();
                }
              }}
              placeholder="Type your message..."
              className="flex-1 px-4 py-3 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
            <button onClick={() => void handleSend()} disabled={sending} className="px-6 py-3 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors">
              <Send size={20} />
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
