import React, { useState, useRef, useEffect } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { oneDark } from 'react-syntax-highlighter/dist/esm/styles/prism';
import "./App.css";

function App() {
  const [input, setInput] = useState("");
  const [model, setModel] = useState("openai/gpt-3.5-turbo");
  const [messages, setMessages] = useState([]);
  const messagesRef = useRef(null);

  useEffect(() => {
    // scroll to bottom when messages change
    if (messagesRef.current) {
      messagesRef.current.scrollTop = messagesRef.current.scrollHeight;
    }
  }, [messages]);

  const sendMessage = () => {
    if (!input.trim()) return;

    const userMessage = input.trim();
    setMessages(prev => [...prev, { role: "user", content: userMessage, ts: Date.now() }]);
    setInput("");

    const sessionId = "default";
    const url = `http://localhost:8080/api/chat/stream?sessionId=${sessionId}&message=${encodeURIComponent(userMessage)}&model=${encodeURIComponent(model)}`;

    const eventSource = new EventSource(url);

    eventSource.addEventListener("chat", (event) => {
      if (event.data === "[DONE]") {
        eventSource.close();
        return;
      }
      try {
        const data = JSON.parse(event.data);
        const content = data.choices?.[0]?.delta?.content ?? "";
        if (content && content !== "<s>" && content !== "</s>") {
          setMessages(prev => {
            const copy = [...prev];
            // find last assistant index
            const lastAssistantIndex = copy.map(m => m.role).lastIndexOf("assistant");
            if (lastAssistantIndex === -1) {
              copy.push({ role: "assistant", content, ts: Date.now() });
            } else {
              copy[lastAssistantIndex] = { ...copy[lastAssistantIndex], content: (copy[lastAssistantIndex].content || "") + content };
            }
            return copy;
          });
        }
      } catch (err) {
        console.error("Erreur JSON parse :", err, event.data);
      }
    });

    eventSource.addEventListener("done", () => eventSource.close());
    eventSource.onerror = err => {
      console.error("SSE error:", err);
      eventSource.close();
    };

    // add an empty assistant message immediately so UI shows streaming
    setMessages(prev => [...prev, { role: "assistant", content: "", ts: Date.now() }]);
  };

  // Custom code renderer for ReactMarkdown to enable syntax highlighting
  const CodeBlock = ({ node, inline, className, children, ...props }) => {
    const match = /language-(\w+)/.exec(className || '');
    const language = match ? match[1] : null;
    if (inline) {
      return (
        <code className={className} {...props}>
          {children}
        </code>
      );
    }

    // For fenced code blocks (not inline), always render SyntaxHighlighter.
    // If no language is specified, fall back to plain text to still get styled block.
    return (
      <SyntaxHighlighter style={oneDark} language={language || 'text'} PreTag="div" {...props}>
        {String(children).replace(/\n$/, '')}
      </SyntaxHighlighter>
    );
  };

  return (
    <div className="app-root">
      <div className="chat-card">
        <header className="chat-header">
          <div>
            <h2>Dark Chat</h2>
            <p className="sub">Chater comme jamais</p>
          </div>
          <div className="model-select">
            <label>Modèle</label>
            <select value={model} onChange={e => setModel(e.target.value)}>
              <option value="openai/gpt-3.5-turbo">gpt-3.5-turbo</option>
              <option value="mistralai/mixtral-8x7b-instruct">mixtral-8x7b-instruct</option>
              <option value="meta-llama/llama-3-8b-instruct">meta-llama/llama-3-8b-instruct</option>
              <option value="openrouter/auto">openrouter/auto</option>
            </select>
          </div>
        </header>

        <main className="messages" ref={messagesRef}>
          {messages.map((m, i) => (
            <div key={i} className={`message-row ${m.role === 'user' ? 'from-user' : 'from-bot'}`}>
              <div className="avatar" aria-hidden>
                {m.role === 'user' ? 'U' : 'B'}
              </div>
              <div className="message-bubble">
                <div className="message-content">
                  <ReactMarkdown remarkPlugins={[remarkGfm]} components={{ code: CodeBlock }}>{m.content || ""}</ReactMarkdown>
                </div>
                <div className="message-meta">{m.ts ? new Date(m.ts).toLocaleTimeString() : ''}</div>
              </div>
            </div>
          ))}
        </main>

        <form className="composer" onSubmit={(e) => { e.preventDefault(); sendMessage(); }}>
          <input
            value={input}
            onChange={e => setInput(e.target.value)}
            placeholder="Écris un message... (Shift+Enter pour saut)"
            onKeyDown={e => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); sendMessage(); } }}
          />
          <button type="submit" className="send">Envoyer</button>
        </form>
      </div>
    </div>
  );
}

export default App;
