import { useState } from "react";
import { Send, Paperclip, Bot, User } from "lucide-react";

interface Message {
  id: number;
  sender: "user" | "ai";
  content: string;
  time: string;
}

export default function AIAssistant() {
  const [messages, setMessages] = useState<Message[]>([
    {
      id: 1,
      sender: "ai",
      content: "Hello! I'm your AI assistant. I can help you with tasks, summarize emails, generate reports, and answer questions about your documents. How can I assist you today?",
      time: "10:00 AM",
    },
    {
      id: 2,
      sender: "user",
      content: "What tasks are pending for today?",
      time: "10:01 AM",
    },
    {
      id: 3,
      sender: "ai",
      content: "You have 5 pending tasks for today:\n\n1. Review Q1 financial report (High Priority)\n2. Approve marketing budget proposal\n3. Schedule team meeting for project kickoff\n4. Review and respond to client emails\n5. Update project documentation\n\nWould you like me to provide more details on any of these tasks?",
      time: "10:01 AM",
    },
  ]);

  const [inputMessage, setInputMessage] = useState("");
  const [isTyping, setIsTyping] = useState(false);

  const suggestedPrompts = [
    "What tasks are pending?",
    "Summarize today's emails",
    "Generate weekly report",
    "Explain HR policy",
  ];

  const chatHistory = [
    "New conversation",
    "Weekly report discussion",
    "Email automation setup",
    "Document analysis",
    "Task management help",
  ];

  const handleSend = () => {
    if (!inputMessage.trim()) return;

    const newMessage: Message = {
      id: messages.length + 1,
      sender: "user",
      content: inputMessage,
      time: new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" }),
    };

    setMessages([...messages, newMessage]);
    setInputMessage("");
    setIsTyping(true);

    setTimeout(() => {
      const aiResponse: Message = {
        id: messages.length + 2,
        sender: "ai",
        content: "I understand your request. Let me process that for you...",
        time: new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" }),
      };
      setMessages((prev) => [...prev, aiResponse]);
      setIsTyping(false);
    }, 1000);
  };

  return (
    <div className="h-[calc(100vh-8rem)] flex gap-6">
      <div className="w-64 bg-white rounded-xl shadow-sm border border-gray-100 p-4">
        <button className="w-full bg-indigo-600 text-white py-2 rounded-lg mb-4 hover:bg-indigo-700 transition-colors">
          + New Chat
        </button>
        <h3 className="text-sm font-semibold text-gray-700 mb-3">Chat History</h3>
        <div className="space-y-2">
          {chatHistory.map((chat, index) => (
            <div
              key={index}
              className={`p-3 rounded-lg cursor-pointer transition-colors ${
                index === 0 ? "bg-indigo-50 text-indigo-700" : "hover:bg-gray-50 text-gray-700"
              }`}
            >
              <p className="text-sm">{chat}</p>
            </div>
          ))}
        </div>
      </div>

      <div className="flex-1 bg-white rounded-xl shadow-sm border border-gray-100 flex flex-col">
        <div className="p-6 border-b border-gray-200">
          <h1 className="text-2xl font-bold text-gray-800">AI Assistant</h1>
          <p className="text-gray-600">Ask me anything about your work</p>
        </div>

        <div className="flex-1 overflow-y-auto p-6 space-y-4">
          {messages.map((message) => (
            <div
              key={message.id}
              className={`flex gap-3 ${message.sender === "user" ? "justify-end" : "justify-start"}`}
            >
              {message.sender === "ai" && (
                <div className="w-8 h-8 bg-indigo-600 rounded-full flex items-center justify-center flex-shrink-0">
                  <Bot size={18} className="text-white" />
                </div>
              )}
              <div
                className={`max-w-2xl ${
                  message.sender === "user"
                    ? "bg-indigo-600 text-white"
                    : "bg-gray-100 text-gray-800"
                } rounded-2xl px-4 py-3`}
              >
                <p className="whitespace-pre-wrap">{message.content}</p>
                <p className={`text-xs mt-1 ${message.sender === "user" ? "text-indigo-200" : "text-gray-500"}`}>
                  {message.time}
                </p>
              </div>
              {message.sender === "user" && (
                <div className="w-8 h-8 bg-gray-600 rounded-full flex items-center justify-center flex-shrink-0">
                  <User size={18} className="text-white" />
                </div>
              )}
            </div>
          ))}

          {isTyping && (
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
              {suggestedPrompts.map((prompt, index) => (
                <button
                  key={index}
                  onClick={() => setInputMessage(prompt)}
                  className="px-3 py-1 bg-gray-100 hover:bg-gray-200 rounded-full text-sm text-gray-700 transition-colors"
                >
                  {prompt}
                </button>
              ))}
            </div>
          </div>

          <div className="flex gap-2">
            <button className="p-3 hover:bg-gray-100 rounded-lg transition-colors">
              <Paperclip size={20} className="text-gray-600" />
            </button>
            <input
              type="text"
              value={inputMessage}
              onChange={(e) => setInputMessage(e.target.value)}
              onKeyPress={(e) => e.key === "Enter" && handleSend()}
              placeholder="Type your message..."
              className="flex-1 px-4 py-3 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
            <button
              onClick={handleSend}
              className="px-6 py-3 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors"
            >
              <Send size={20} />
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
