/**
 * LastLab Mobile — Application Engine
 * Optimized for Mobile WebView & Responsive Handhelds
 * Exclusively powered by @model-aggregator (Relay Gateway)
 */

(function () {
  'use strict';

  // ---------------------------------------------------------------------------
  // Baseline Initial Models & Providers Catalog
  // Pre-bundled so the app is 100% responsive instantly offline and on startup
  // ---------------------------------------------------------------------------
  const INITIAL_MODELS = [
    {
      id: "deepseek-v4.1-flash",
      name: "DeepSeek V4.1 Flash",
      owned_by: "workbuddy",
      reasoning: true,
      relay: { modality: "chat", reasoning: true, vision: false, context: 128000, max_output: 8192, free: true }
    },
    {
      id: "hy4-preview",
      name: "Hunyuan 4 Preview",
      owned_by: "workbuddy",
      reasoning: false,
      relay: { modality: "chat", reasoning: false, vision: false, context: 64000, max_output: 4096, free: true }
    },
    {
      id: "kimi-k3-1",
      name: "Kimi-K3",
      owned_by: "workbuddy",
      reasoning: true,
      relay: { modality: "chat", reasoning: true, vision: false, context: 128000, max_output: 8192, free: true }
    },
    {
      id: "glm-5.2",
      name: "GLM-5.2",
      owned_by: "workbuddy",
      reasoning: false,
      relay: { modality: "chat", reasoning: false, vision: false, context: 128000, max_output: 8192, free: true }
    },
    {
      id: "glm-5.1",
      name: "GLM-5.1",
      owned_by: "workbuddy",
      reasoning: false,
      relay: { modality: "chat", reasoning: false, vision: false, context: 128000, max_output: 8192, free: true }
    },
    {
      id: "deepseek-v4-pro",
      name: "DeepSeek V4 Pro",
      owned_by: "workbuddy",
      reasoning: false,
      relay: { modality: "chat", reasoning: false, vision: false, context: 128000, max_output: 8192, free: true }
    },
    {
      id: "deepseek-v4-flash",
      name: "DeepSeek V4 Flash",
      owned_by: "workbuddy",
      reasoning: false,
      relay: { modality: "chat", reasoning: false, vision: false, context: 128000, max_output: 4096, free: true }
    },
    {
      id: "kimi-k2.7",
      name: "Kimi-K2.7-Code",
      owned_by: "workbuddy",
      reasoning: false,
      relay: { modality: "chat", reasoning: false, vision: false, context: 64000, max_output: 8192, free: true }
    },
    {
      id: "kimi-k2.6",
      name: "Kimi-K2.6",
      owned_by: "workbuddy",
      reasoning: false,
      relay: { modality: "chat", reasoning: false, vision: false, context: 64000, max_output: 4096, free: true }
    },
    {
      id: "hy3",
      name: "Hunyuan 3",
      owned_by: "workbuddy",
      reasoning: false,
      relay: { modality: "chat", reasoning: false, vision: false, context: 32000, max_output: 4096, free: true }
    },
    {
      id: "minimax-m3",
      name: "MiniMax-M3",
      owned_by: "workbuddy",
      reasoning: false,
      relay: { modality: "chat", reasoning: false, vision: false, context: 64000, max_output: 4096, free: true }
    },
    {
      id: "hunyuan-video",
      name: "Hunyuan Video",
      owned_by: "workbuddy",
      reasoning: false,
      relay: { modality: "video", reasoning: false, vision: true, context: null, max_output: null, free: true }
    },
    {
      id: "gpt-6-astra",
      name: "GPT Astra",
      owned_by: "experientiallabs",
      reasoning: false,
      relay: { modality: "chat", reasoning: false, vision: true, context: 128000, max_output: 4096, free: true }
    },
    {
      id: "agnes-image-2-5-flash",
      name: "Agnes Image 2.5 Flash",
      owned_by: "yjs",
      reasoning: false,
      relay: { modality: "image", reasoning: false, vision: true, context: null, max_output: null, free: true }
    },
    {
      id: "auto",
      name: "Auto Route (Smart Failover)",
      owned_by: "relay",
      reasoning: false,
      relay: { modality: "chat", reasoning: false, vision: false, context: 128000, max_output: 8192, free: true }
    }
  ];

  const INITIAL_PROVIDERS = [
    { id: "workbuddy", name: "workbuddy", has_key: true, live: true, models: 12 },
    { id: "yjs", name: "yjs", has_key: true, live: true, models: 18 },
    { id: "tokenforge", name: "tokenforge", has_key: true, live: true, models: 5 },
    { id: "bai", name: "bai", has_key: true, live: true, models: 59 },
    { id: "experientiallabs", name: "experientiallabs", has_key: true, live: true, models: 299 },
    { id: "crax", name: "crax", has_key: true, live: true, models: 17 },
    { id: "openrouter", name: "openrouter", has_key: true, live: true, models: 464 },
    { id: "vsllm", name: "vsllm", has_key: true, live: true, models: 85 },
    { id: "kilgore", name: "kilgore", has_key: true, live: true, models: 25 },
    { id: "hybra", name: "hybra", has_key: true, live: true, models: 67 },
    { id: "hcnsec", name: "hcnsec", has_key: true, live: true, models: 21 },
    { id: "nextrouter", name: "nextrouter", has_key: true, live: true, models: 68 },
    { id: "nvidia", name: "nvidia", has_key: true, live: true, models: 81 },
    { id: "zenmux", name: "zenmux", has_key: true, live: true, models: 201 }
  ];

  const DEFAULT_PINNED_MODELS = [
    'deepseek-v4.1-flash',
    'kimi-k3-1',
    'glm-5.2',
    'agnes-image-2-5-flash',
    'auto'
  ];

  // ---------------------------------------------------------------------------
  // State Management & Local Storage Keys
  // ---------------------------------------------------------------------------
  const STORAGE_KEYS = {
    SESSIONS: 'lastlab_sessions',
    CURRENT_SESSION: 'lastlab_current_session_id',
    TUNING: 'lastlab_tuning_settings',
    ADMIN: 'lastlab_admin_config',
    CURRENT_MODEL: 'lastlab_selected_model',
    PINNED_MODELS: 'lastlab_pinned_models',
  };

  const DEFAULT_TUNING = {
    systemPrompt: 'You are LastLab Assistant, an expert, thoughtful, and highly capable AI. Provide thorough, precise, and well-structured answers.',
    temperature: 0.70,
    topP: 1.00,
    maxTokens: 4096,
    reasoningEffort: 'medium',
    frequencyPenalty: 0.00,
    presencePenalty: 0.00,
    stream: true,
  };

  const SYSTEM_PRESETS = {
    default: 'You are LastLab Assistant, an expert, thoughtful, and highly capable AI. Provide thorough, precise, and well-structured answers.',
    architect: 'You are a Principal Software Architect and Senior Systems Engineer. Write clean, robust, idiomatic, and highly optimized code with comprehensive design explanations.',
    reasoner: 'You are a meticulous step-by-step reasoning AI. Break down complex problems into clear, logical steps and verify each deduction before reaching conclusions.',
    concise: 'Provide direct, concise, and high-density responses. Omit conversational filler, polite preambles, and redundant summaries.',
    creative: 'You are an imaginative, expressive, and compelling creative collaborator. Craft engaging narratives, evocative prose, and innovative concepts.',
  };

  const state = {
    sessions: [],
    currentSessionId: null,
    models: [...INITIAL_MODELS],
    providers: [...INITIAL_PROVIDERS],
    pinnedModels: [...DEFAULT_PINNED_MODELS],
    currentModel: 'deepseek-v4.1-flash',
    tuning: { ...DEFAULT_TUNING },
    admin: {
      baseUrl: 'https://relay-gw.pages.dev',
      adminToken: '',
    },
    activeFilter: 'all',
    providerFilter: 'all',
    searchQuery: '',
    isGenerating: false,
    abortController: null,
    lastRequest: {
      curl: '// No request made yet',
      requestJson: '{}',
      responseJson: '// No response yet',
    },
    activeView: 'chat-view',
    activeInspectorTab: 'curl',
    modelProbes: {}, // modelId -> { latency, status }
  };

  // ---------------------------------------------------------------------------
  // DOM Element Selectors
  // ---------------------------------------------------------------------------
  const dom = {
    // Top Bar
    modelTriggerBtn: document.getElementById('model-trigger-btn'),
    currentModelName: document.getElementById('current-model-name'),
    modelStatusDot: document.getElementById('model-status-dot'),
    newChatTopBtn: document.getElementById('new-chat-top-btn'),
    inspectRawBtn: document.getElementById('inspect-raw-btn'),

    // Telemetry
    telemetryProvider: document.getElementById('telemetry-provider'),
    telemetryLatency: document.getElementById('telemetry-latency'),
    telemetryTokens: document.getElementById('telemetry-tokens'),
    gatewayStatusPill: document.getElementById('gateway-status-pill'),

    // Chat View
    chatScroll: document.getElementById('chat-scroll'),
    emptyState: document.getElementById('empty-state'),
    userInput: document.getElementById('user-input'),
    sendBtn: document.getElementById('send-btn'),
    sendIcon: document.getElementById('send-icon'),
    stopIcon: document.getElementById('stop-icon'),
    quickParamsBtn: document.getElementById('quick-params-btn'),
    quickTempLabel: document.getElementById('quick-temp-label'),
    quickReasoningBtn: document.getElementById('quick-reasoning-btn'),
    quickReasoningLabel: document.getElementById('quick-reasoning-label'),
    clearChatBtn: document.getElementById('clear-chat-btn'),
    exportChatBtn: document.getElementById('export-chat-btn'),

    // Tuning View
    systemPromptInput: document.getElementById('system-prompt-input'),
    paramTemp: document.getElementById('param-temperature'),
    valTemp: document.getElementById('val-temperature'),
    paramTopP: document.getElementById('param-top-p'),
    valTopP: document.getElementById('val-top-p'),
    paramMaxTokens: document.getElementById('param-max-tokens'),
    valMaxTokens: document.getElementById('val-max-tokens'),
    paramFreq: document.getElementById('param-freq-penalty'),
    valFreq: document.getElementById('val-freq-penalty'),
    paramPres: document.getElementById('param-pres-penalty'),
    valPres: document.getElementById('val-pres-penalty'),
    paramStream: document.getElementById('param-stream'),
    reasoningEffortCtrl: document.getElementById('reasoning-effort-ctrl'),
    resetParamsBtn: document.getElementById('reset-params-btn'),

    // Sessions View
    sessionsList: document.getElementById('sessions-list'),
    createSessionBtn: document.getElementById('create-session-btn'),

    // Admin View
    adminBaseUrl: document.getElementById('admin-base-url'),
    adminTokenInput: document.getElementById('admin-token-input'),
    saveAdminBtn: document.getElementById('save-admin-btn'),
    pingGatewayBtn: document.getElementById('ping-gateway-btn'),
    pingResult: document.getElementById('ping-result'),
    providersGrid: document.getElementById('providers-grid'),
    adminLogBox: document.getElementById('admin-log-box'),
    adminConnStatus: document.getElementById('admin-conn-status'),

    // Modals
    modelPickerModal: document.getElementById('model-picker-modal'),
    closeModelPickerBtn: document.getElementById('close-model-picker-btn'),
    modelQuickShelf: document.getElementById('model-quick-shelf'),
    modelSearchInput: document.getElementById('model-search-input'),
    clearSearchBtn: document.getElementById('clear-search-btn'),
    providerFilterChips: document.getElementById('provider-filter-chips'),
    modalModelsList: document.getElementById('modal-models-list'),
    modelCountBadge: document.getElementById('model-count-badge'),
    customModelInput: document.getElementById('custom-model-input'),
    setCustomModelBtn: document.getElementById('set-custom-model-btn'),

    // Inspector Modal
    inspectorModal: document.getElementById('inspector-modal'),
    closeInspectorBtn: document.getElementById('close-inspector-btn'),
    inspectorCode: document.getElementById('inspector-code'),
    copyInspectorBtn: document.getElementById('copy-inspector-btn'),

    // Toast
    toastContainer: document.getElementById('toast-container'),
  };

  // ---------------------------------------------------------------------------
  // Helper Functions
  // ---------------------------------------------------------------------------
  function logAdmin(msg) {
    const time = new Date().toLocaleTimeString();
    const line = `[${time}] ${msg}\n`;
    if (dom.adminLogBox) {
      dom.adminLogBox.textContent += line;
      dom.adminLogBox.scrollTop = dom.adminLogBox.scrollHeight;
    }
    console.log(`[Admin] ${msg}`);
  }

  function showToast(text, duration = 2500) {
    if (!dom.toastContainer) return;
    const toast = document.createElement('div');
    toast.className = 'toast';
    toast.textContent = text;
    dom.toastContainer.appendChild(toast);
    setTimeout(() => {
      toast.style.opacity = '0';
      toast.style.transition = 'opacity 0.3s ease';
      setTimeout(() => toast.remove(), 300);
    }, duration);
  }

  function escapeHtml(str) {
    return String(str || '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  // Lightweight robust Markdown parser
  function renderMarkdown(md) {
    if (!md) return '';
    let text = escapeHtml(md);

    // Code blocks with syntax badge and copy button
    text = text.replace(/```([a-zA-Z0-9_\-\.]*)\r?\n([\s\S]*?)```/g, (match, lang, code) => {
      const language = lang.trim() || 'code';
      return `<pre><div class="code-header"><span class="code-lang">${language}</span><button class="copy-code-btn" onclick="navigator.clipboard.writeText(this.closest('pre').querySelector('code').innerText); this.innerText='Copied!'; setTimeout(()=>this.innerText='Copy', 1500);">Copy</button></div><code>${code.trim()}</code></pre>`;
    });

    // Inline code
    text = text.replace(/`([^`\n]+)`/g, '<code>$1</code>');

    // Bold & Italics
    text = text.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');
    text = text.replace(/\*([^*]+)\*/g, '<em>$1</em>');

    // Blockquotes
    text = text.replace(/^>\s+(.+)$/gm, '<blockquote>$1</blockquote>');

    // Headers
    text = text.replace(/^### (.*$)/gim, '<h3>$1</h3>');
    text = text.replace(/^## (.*$)/gim, '<h2>$1</h2>');
    text = text.replace(/^# (.*$)/gim, '<h1>$1</h1>');

    // Unordered lists
    text = text.replace(/^\s*[-*]\s+(.+)$/gm, '<li>$1</li>');
    text = text.replace(/(<li>.*<\/li>)/gms, '<ul>$1</ul>');

    // Tables
    text = text.replace(/^\|(.+)\|$/gm, (match, content) => {
      const cells = content.split('|').map(c => `<td>${c.trim()}</td>`).join('');
      return `<tr>${cells}</tr>`;
    });
    text = text.replace(/(<tr>.*<\/tr>)/gms, '<table>$1</table>');

    // Paragraphs
    const paras = text.split(/\n\n+/);
    text = paras.map(p => {
      p = p.trim();
      if (!p) return '';
      if (/^<(h[1-6]|ul|ol|pre|blockquote|table)/i.test(p)) return p;
      return `<p>${p.replace(/\n/g, '<br>')}</p>`;
    }).join('\n');

    return text;
  }

  // ---------------------------------------------------------------------------
  // Storage & Initialization
  // ---------------------------------------------------------------------------
  function loadSavedData() {
    function getStored(key, legacyKey) {
      return localStorage.getItem(key) || (legacyKey ? localStorage.getItem(legacyKey) : null);
    }
    try {
      const savedAdmin = getStored(STORAGE_KEYS.ADMIN, 'lastchat_admin_config');
      if (savedAdmin) {
        state.admin = { ...state.admin, ...JSON.parse(savedAdmin) };
      }
      const savedTuning = getStored(STORAGE_KEYS.TUNING, 'lastchat_tuning_settings');
      if (savedTuning) {
        state.tuning = { ...state.tuning, ...JSON.parse(savedTuning) };
      }
      const savedModel = getStored(STORAGE_KEYS.CURRENT_MODEL, 'lastchat_selected_model');
      if (savedModel) {
        state.currentModel = savedModel;
      }
      const savedPinned = getStored(STORAGE_KEYS.PINNED_MODELS, 'lastchat_pinned_models');
      if (savedPinned) {
        state.pinnedModels = JSON.parse(savedPinned);
      }
      const savedSessions = getStored(STORAGE_KEYS.SESSIONS, 'lastchat_playground_sessions');
      if (savedSessions) {
        state.sessions = JSON.parse(savedSessions);
      }
      const savedCurrId = getStored(STORAGE_KEYS.CURRENT_SESSION, 'lastchat_current_session_id');
      if (savedCurrId && state.sessions.some(s => s.id === savedCurrId)) {
        state.currentSessionId = savedCurrId;
      }
    } catch (e) {
      console.error('Failed to load local storage state:', e);
    }

    if (!state.sessions.length) {
      createNewSession('Default Session');
    } else if (!state.currentSessionId) {
      state.currentSessionId = state.sessions[0].id;
    }
  }

  function saveSessions() {
    try {
      localStorage.setItem(STORAGE_KEYS.SESSIONS, JSON.stringify(state.sessions));
      localStorage.setItem(STORAGE_KEYS.CURRENT_SESSION, state.currentSessionId);
    } catch (e) {
      console.warn('Storage quota exceeded:', e);
    }
  }

  function saveTuning() {
    try {
      localStorage.setItem(STORAGE_KEYS.TUNING, JSON.stringify(state.tuning));
    } catch (e) {}
  }

  function saveAdmin() {
    try {
      localStorage.setItem(STORAGE_KEYS.ADMIN, JSON.stringify(state.admin));
    } catch (e) {}
  }

  function savePinned() {
    try {
      localStorage.setItem(STORAGE_KEYS.PINNED_MODELS, JSON.stringify(state.pinnedModels));
    } catch (e) {}
  }

  // ---------------------------------------------------------------------------
  // Session Management
  // ---------------------------------------------------------------------------
  function createNewSession(title = 'New Playground') {
    const id = 'sess_' + Date.now() + '_' + Math.random().toString(36).substring(2, 7);
    const newSession = {
      id,
      title,
      model: state.currentModel,
      createdAt: Date.now(),
      updatedAt: Date.now(),
      messages: [],
    };
    state.sessions.unshift(newSession);
    state.currentSessionId = id;
    saveSessions();
    renderChatMessages();
    renderSessionsList();
    showToast('New session started');
    switchToView('chat-view');
  }

  function getCurrentSession() {
    return state.sessions.find(s => s.id === state.currentSessionId) || state.sessions[0];
  }

  function deleteSession(id, event) {
    if (event) event.stopPropagation();
    if (state.sessions.length <= 1) {
      showToast('Cannot delete the only session');
      return;
    }
    state.sessions = state.sessions.filter(s => s.id !== id);
    if (state.currentSessionId === id) {
      state.currentSessionId = state.sessions[0].id;
    }
    saveSessions();
    renderSessionsList();
    renderChatMessages();
    showToast('Session deleted');
  }

  function renameSession(id, event) {
    if (event) event.stopPropagation();
    const session = state.sessions.find(s => s.id === id);
    if (!session) return;
    const newTitle = prompt('Rename Session Title:', session.title);
    if (newTitle && newTitle.trim()) {
      session.title = newTitle.trim();
      saveSessions();
      renderSessionsList();
      showToast('Session renamed');
    }
  }

  function exportCurrentSession() {
    const session = getCurrentSession();
    if (!session || !session.messages.length) {
      showToast('No messages to export');
      return;
    }
    const dataStr = "data:text/json;charset=utf-8," + encodeURIComponent(JSON.stringify(session, null, 2));
    const dlAnchor = document.createElement('a');
    dlAnchor.setAttribute("href", dataStr);
    dlAnchor.setAttribute("download", `lastlab-${session.id}.json`);
    document.body.appendChild(dlAnchor);
    dlAnchor.click();
    dlAnchor.remove();
    showToast('Session exported (JSON)');
  }

  function renderSessionsList() {
    dom.sessionsList.innerHTML = '';
    state.sessions.forEach(sess => {
      const card = document.createElement('div');
      card.className = `session-card ${sess.id === state.currentSessionId ? 'active' : ''}`;
      card.onclick = () => {
        state.currentSessionId = sess.id;
        saveSessions();
        renderSessionsList();
        renderChatMessages();
        switchToView('chat-view');
      };

      const dateStr = new Date(sess.updatedAt || sess.createdAt).toLocaleDateString([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
      card.innerHTML = `
        <div class="session-info">
          <div class="session-title">${escapeHtml(sess.title)}</div>
          <div class="session-meta">
            <span>${escapeHtml(sess.model || 'auto')}</span>
            <span>•</span>
            <span>${sess.messages ? sess.messages.length : 0} messages</span>
            <span>•</span>
            <span>${dateStr}</span>
          </div>
        </div>
        <div style="display: flex; gap: 4px;">
          <button class="session-delete-btn" title="Rename Session" onclick="(window.LastLabApp || window.LastChatApp).renameSession('${sess.id}', event)">
            <svg width="15" height="15" viewBox="0 0 24 24" fill="currentColor"><path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z"/></svg>
          </button>
          <button class="session-delete-btn" title="Delete Session" onclick="(window.LastLabApp || window.LastChatApp).deleteSession('${sess.id}', event)">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor"><path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"/></svg>
          </button>
        </div>
      `;
      dom.sessionsList.appendChild(card);
    });
  }

  // ---------------------------------------------------------------------------
  // Relay API Integration (@model-aggregator)
  // ---------------------------------------------------------------------------
  function getAuthHeaders() {
    const headers = {
      'Content-Type': 'application/json',
    };
    if (state.admin.adminToken) {
      headers['x-admin-token'] = state.admin.adminToken.trim();
      headers['Authorization'] = `Bearer ${state.admin.adminToken.trim()}`;
    }
    return headers;
  }

  async function fetchModels() {
    logAdmin(`Fetching live models from ${state.admin.baseUrl}/v1/models...`);
    dom.modelStatusDot.className = 'model-dot loading';
    try {
      const res = await fetch(`${state.admin.baseUrl}/v1/models`, {
        headers: getAuthHeaders(),
      });
      if (!res.ok) throw new Error(`HTTP ${res.status} ${res.statusText}`);
      const data = await res.json();
      if (Array.isArray(data.data) && data.data.length > 0) {
        state.models = data.data;
        logAdmin(`Loaded ${state.models.length} models from Relay Gateway.`);
      }
      dom.modelStatusDot.className = 'model-dot';
      dom.adminConnStatus.textContent = '● Live';
      dom.adminConnStatus.className = 'status-badge online';
      renderQuickShelf();
      renderProviderFilterChips();
      renderModelRegistry();
      updateCurrentModelDisplay();
    } catch (err) {
      logAdmin(`Note on live models fetch: ${err.message} (using cached catalog)`);
      dom.modelStatusDot.className = 'model-dot';
      renderQuickShelf();
      renderProviderFilterChips();
      renderModelRegistry();
    }
  }

  async function fetchProviders() {
    logAdmin(`Probing upstream providers from ${state.admin.baseUrl}/v1/providers...`);
    try {
      const res = await fetch(`${state.admin.baseUrl}/v1/providers`, {
        headers: getAuthHeaders(),
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data = await res.json();
      if (Array.isArray(data.providers) && data.providers.length > 0) {
        state.providers = data.providers;
        logAdmin(`Discovered ${state.providers.length} upstream providers.`);
      }
      renderProvidersGrid();
      renderProviderFilterChips();
    } catch (err) {
      logAdmin(`Note on providers probe: ${err.message} (displaying cached providers)`);
      renderProvidersGrid();
    }
  }

  async function pingGateway() {
    dom.pingResult.textContent = 'Pinging upstream gateway...';
    const start = performance.now();
    try {
      const res = await fetch(`${state.admin.baseUrl}/v1/models`, {
        method: 'GET',
        headers: getAuthHeaders(),
      });
      const latency = Math.round(performance.now() - start);
      state.admin.lastLatency = latency;
      dom.pingResult.textContent = `✓ Upstream Online — Ping: ${latency}ms (HTTP ${res.status})`;
      dom.telemetryLatency.textContent = `${latency} ms`;
      showToast(`Ping: ${latency}ms`);
      logAdmin(`Gateway Ping: ${latency}ms (Status: ${res.status})`);
    } catch (e) {
      dom.pingResult.textContent = `✗ Gateway Unreachable: ${e.message}`;
      showToast('Gateway Unreachable');
      logAdmin(`Gateway Ping Error: ${e.message}`);
    }
  }

  async function probeModel(modelId, btnEl) {
    if (btnEl) btnEl.textContent = '⏳';
    const start = performance.now();
    try {
      // Test ping via endpoint
      const res = await fetch(`${state.admin.baseUrl}/v1/models`, {
        headers: getAuthHeaders(),
      });
      const lat = Math.round(performance.now() - start);
      state.modelProbes[modelId] = { latency: lat, status: res.ok ? 'ok' : 'err' };
      if (btnEl) btnEl.textContent = `${lat}ms`;
      showToast(`${modelId}: ${lat}ms`);
    } catch (e) {
      state.modelProbes[modelId] = { latency: null, status: 'fail' };
      if (btnEl) btnEl.textContent = 'Err';
    }
  }

  // ---------------------------------------------------------------------------
  // Model Registry & Model Picker (Admin-Only Experience)
  // ---------------------------------------------------------------------------
  function updateCurrentModelDisplay() {
    const found = state.models.find(m => m.id === state.currentModel);
    const name = found ? (found.name || found.id) : state.currentModel;
    dom.currentModelName.textContent = name;
    dom.currentModelName.title = state.currentModel;

    // Upstream provider
    const prov = found?.owned_by || found?.relay?.sources?.[0]?.provider || 'relay';
    dom.telemetryProvider.textContent = prov;

    renderQuickShelf();
  }

  function renderQuickShelf() {
    if (!dom.modelQuickShelf) return;
    dom.modelQuickShelf.innerHTML = '';
    state.pinnedModels.forEach(mId => {
      const model = state.models.find(m => m.id === mId) || { id: mId, name: mId };
      const chip = document.createElement('button');
      chip.className = `shelf-chip ${mId === state.currentModel ? 'active' : ''}`;
      chip.title = mId;
      chip.innerHTML = `
        <span class="model-dot"></span>
        <span>${escapeHtml(model.name || model.id)}</span>
      `;
      chip.onclick = () => selectModel(mId);
      dom.modelQuickShelf.appendChild(chip);
    });
  }

  function renderProviderFilterChips() {
    if (!dom.providerFilterChips) return;
    dom.providerFilterChips.innerHTML = '';

    // "All" chip
    const allChip = document.createElement('button');
    allChip.className = `prov-filter-chip ${state.providerFilter === 'all' ? 'active' : ''}`;
    allChip.textContent = 'All Providers';
    allChip.onclick = () => {
      state.providerFilter = 'all';
      renderProviderFilterChips();
      renderModelRegistry();
    };
    dom.providerFilterChips.appendChild(allChip);

    // List unique providers
    const provSet = new Set();
    state.models.forEach(m => {
      const p = m.owned_by || m.relay?.sources?.[0]?.provider;
      if (p) provSet.add(p);
    });

    provSet.forEach(p => {
      const chip = document.createElement('button');
      chip.className = `prov-filter-chip ${state.providerFilter === p ? 'active' : ''}`;
      chip.textContent = p;
      chip.onclick = () => {
        state.providerFilter = state.providerFilter === p ? 'all' : p;
        renderProviderFilterChips();
        renderModelRegistry();
      };
      dom.providerFilterChips.appendChild(chip);
    });
  }

  function togglePinModel(modelId, event) {
    if (event) event.stopPropagation();
    const idx = state.pinnedModels.indexOf(modelId);
    if (idx >= 0) {
      state.pinnedModels.splice(idx, 1);
      showToast(`Unpinned ${modelId}`);
    } else {
      state.pinnedModels.push(modelId);
      showToast(`Pinned ${modelId} to shelf`);
    }
    savePinned();
    renderQuickShelf();
    renderModelRegistry();
  }

  function renderModelRegistry() {
    dom.modelCountBadge.textContent = `${state.models.length} Models`;
    dom.modalModelsList.innerHTML = '';

    const q = state.searchQuery.toLowerCase().trim();
    const filter = state.activeFilter;
    const provFilter = state.providerFilter;

    if (dom.clearSearchBtn) {
      dom.clearSearchBtn.style.display = q ? 'block' : 'none';
    }

    const filtered = state.models.filter(m => {
      const id = (m.id || '').toLowerCase();
      const name = (m.name || '').toLowerCase();
      const prov = (m.owned_by || m.relay?.sources?.[0]?.provider || '').toLowerCase();
      const modality = (m.relay?.modality || '').toLowerCase();
      const isReasoning = m.reasoning || m.relay?.reasoning || id.includes('r1') || id.includes('think') || name.includes('reason');
      const isVision = m.relay?.vision || modality.includes('image') || modality.includes('video') || id.includes('vision');
      const isMedia = modality.includes('image') || modality.includes('video');
      const isPinned = state.pinnedModels.includes(m.id);

      if (provFilter !== 'all' && prov !== provFilter.toLowerCase()) {
        return false;
      }

      if (q && !id.includes(q) && !name.includes(q) && !prov.includes(q)) {
        return false;
      }

      if (filter === 'pinned' && !isPinned) return false;
      if (filter === 'reasoning' && !isReasoning) return false;
      if (filter === 'vision' && !isVision) return false;
      if (filter === 'media' && !isMedia) return false;
      if (filter === 'chat' && (isMedia && !isReasoning)) return false;

      return true;
    });

    if (!filtered.length) {
      dom.modalModelsList.innerHTML = `
        <div style="text-align: center; padding: 24px; color: var(--text-dim); font-size: 13px;">
          No models matched your query. Enter custom model id below.
        </div>
      `;
      return;
    }

    filtered.forEach(m => {
      const isSelected = m.id === state.currentModel;
      const prov = m.owned_by || m.relay?.sources?.[0]?.provider || 'relay';
      const isReasoning = m.reasoning || m.relay?.reasoning || m.id.includes('r1');
      const isVision = m.relay?.vision || (m.relay?.modality === 'image');
      const isPinned = state.pinnedModels.includes(m.id);
      const ctx = m.relay?.context ? `${Math.round(m.relay.context / 1000)}K ctx` : '';
      const probeData = state.modelProbes[m.id];

      const row = document.createElement('div');
      row.className = `model-row ${isSelected ? 'selected' : ''}`;
      row.onclick = () => selectModel(m.id);

      row.innerHTML = `
        <div class="model-row-left">
          <div class="model-row-name-line">
            <span class="model-dot"></span>
            <span class="model-row-name">${escapeHtml(m.name || m.id)}</span>
            <span class="provider-chip">${escapeHtml(prov)}</span>
          </div>
          <div class="model-row-id">
            <span>${escapeHtml(m.id)}</span>
            ${ctx ? `<span class="badge-tag" style="background: rgba(255,255,255,0.06);">${ctx}</span>` : ''}
          </div>
        </div>
        <div class="model-row-right">
          ${isReasoning ? '<span class="badge-tag reasoning">🧠 Thinks</span>' : ''}
          ${isVision ? '<span class="badge-tag vision">👁 Vision</span>' : ''}
          <button class="probe-model-btn" title="Probe latency" onclick="(window.LastLabApp || window.LastChatApp).probeModel('${escapeHtml(m.id)}', this); event.stopPropagation();">
            ${probeData ? `${probeData.latency}ms` : '⚡ Ping'}
          </button>
          <button class="pin-model-btn ${isPinned ? 'pinned' : ''}" title="${isPinned ? 'Unpin' : 'Pin to Shelf'}" onclick="(window.LastLabApp || window.LastChatApp).togglePinModel('${escapeHtml(m.id)}', event);">
            ${isPinned ? '★' : '☆'}
          </button>
        </div>
      `;
      dom.modalModelsList.appendChild(row);
    });
  }

  function selectModel(modelId) {
    state.currentModel = modelId;
    localStorage.setItem(STORAGE_KEYS.CURRENT_MODEL, modelId);
    updateCurrentModelDisplay();
    closeModelPicker();
    showToast(`Model set: ${modelId}`);
    logAdmin(`Active model switched to: ${modelId}`);
  }

  function openModelPicker() {
    dom.modelPickerModal.classList.add('active');
    dom.modelSearchInput.focus();
    renderQuickShelf();
    renderProviderFilterChips();
    renderModelRegistry();
    try {
      history.pushState({ modal: 'model-picker' }, '');
    } catch (e) {}
  }

  function closeModelPicker() {
    dom.modelPickerModal.classList.remove('active');
  }

  // ---------------------------------------------------------------------------
  // Admin Live Provider Matrix
  // ---------------------------------------------------------------------------
  function renderProvidersGrid() {
    dom.providersGrid.innerHTML = '';
    if (!state.providers.length) {
      dom.providersGrid.innerHTML = '<div class="param-desc">No providers detected.</div>';
      return;
    }
    state.providers.forEach(p => {
      const card = document.createElement('div');
      card.className = 'provider-card';
      card.innerHTML = `
        <div class="provider-name">
          <span>${escapeHtml(p.name || p.id)}</span>
          <span class="model-dot ${p.live ? '' : 'loading'}"></span>
        </div>
        <div class="provider-details">
          <span>Models: ${p.models ?? '--'}</span><br>
          <span>Key: ${p.has_key ? '✓ Configured' : 'No Key'}</span>
        </div>
      `;
      dom.providersGrid.appendChild(card);
    });
  }

  // ---------------------------------------------------------------------------
  // Chat Completion & Streaming Engine
  // ---------------------------------------------------------------------------
  async function handleSend() {
    if (state.isGenerating) {
      // Stop generation
      if (state.abortController) {
        state.abortController.abort();
        state.abortController = null;
      }
      setIsGenerating(false);
      showToast('Generation halted');
      return;
    }

    const prompt = dom.userInput.value.trim();
    if (!prompt) return;

    dom.userInput.value = '';
    autoResizeInput();

    const session = getCurrentSession();
    if (session.messages.length === 0) {
      session.title = prompt.length > 32 ? prompt.substring(0, 32) + '...' : prompt;
    }

    // Add user message to session
    session.messages.push({
      role: 'user',
      content: prompt,
      timestamp: Date.now(),
    });
    session.updatedAt = Date.now();
    saveSessions();
    renderChatMessages();

    // Prepare assistant placeholder turn
    const assistantMsg = {
      role: 'assistant',
      content: '',
      reasoning: '',
      model: state.currentModel,
      latencyMs: null,
      tokens: null,
      timestamp: Date.now(),
    };
    session.messages.push(assistantMsg);
    renderChatMessages();

    setIsGenerating(true);
    state.abortController = new AbortController();

    // Build OpenAI messages array
    const apiMessages = [];
    if (state.tuning.systemPrompt.trim()) {
      apiMessages.push({ role: 'system', content: state.tuning.systemPrompt.trim() });
    }
    // Append previous dialogue
    session.messages.slice(0, -1).forEach(m => {
      apiMessages.push({ role: m.role, content: m.content });
    });

    const requestBody = {
      model: state.currentModel,
      messages: apiMessages,
      temperature: state.tuning.temperature,
      top_p: state.tuning.topP,
      max_tokens: state.tuning.maxTokens,
      frequency_penalty: state.tuning.frequencyPenalty,
      presence_penalty: state.tuning.presencePenalty,
      stream: state.tuning.stream,
    };

    if (state.tuning.reasoningEffort && state.tuning.reasoningEffort !== 'none') {
      requestBody.reasoning_effort = state.tuning.reasoningEffort;
    }

    // Capture telemetry for raw inspector
    const curlHeaders = Object.entries(getAuthHeaders())
      .map(([k, v]) => `-H "${k}: ${v}"`)
      .join(' \\\n  ');
    state.lastRequest.curl = `curl -X POST "${state.admin.baseUrl}/v1/chat/completions" \\\n  ${curlHeaders} \\\n  -d '${JSON.stringify(requestBody, null, 2)}'`;
    state.lastRequest.requestJson = JSON.stringify(requestBody, null, 2);

    const startTime = performance.now();
    logAdmin(`Sending request to ${state.admin.baseUrl}/v1/chat/completions (Model: ${state.currentModel}, Stream: ${state.tuning.stream})...`);

    try {
      const response = await fetch(`${state.admin.baseUrl}/v1/chat/completions`, {
        method: 'POST',
        headers: getAuthHeaders(),
        body: JSON.stringify(requestBody),
        signal: state.abortController.signal,
      });

      const relayLatency = response.headers.get('x-relay-latency-ms');
      const roundTripMs = Math.round(performance.now() - startTime);
      assistantMsg.latencyMs = relayLatency ? parseInt(relayLatency, 10) : roundTripMs;
      dom.telemetryLatency.textContent = `${assistantMsg.latencyMs} ms`;

      if (!response.ok) {
        let errJson = {};
        try { errJson = await response.json(); } catch (e) {}
        state.lastRequest.responseJson = JSON.stringify(errJson, null, 2);
        const errMsg = errJson.error?.message || `HTTP ${response.status}: ${response.statusText}`;

        if (response.status === 401) {
          assistantMsg.content = `⚠️ **Admin Authentication Required**\n\nThe Relay Gateway rejected the request: *${errMsg}*.\n\nPlease navigate to the **⚡ Admin** tab and enter your \`ADMIN_TOKEN\` or Gateway \`sk-...\` API key.`;
        } else {
          assistantMsg.content = `**Error:** ${errMsg}`;
        }
        updateLastAssistantMessage(assistantMsg, false);
        saveSessions();
        showToast(errMsg);
        return;
      }

      if (state.tuning.stream) {
        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';

        while (true) {
          const { done, value } = await reader.read();
          if (done) break;

          buffer += decoder.decode(value, { stream: true });
          const lines = buffer.split('\n');
          buffer = lines.pop() || '';

          for (const line of lines) {
            const trimmed = line.trim();
            if (!trimmed || trimmed.startsWith(':')) continue;
            if (trimmed === 'data: [DONE]') continue;

            if (trimmed.startsWith('data: ')) {
              try {
                const chunk = JSON.parse(trimmed.substring(6));
                const delta = chunk.choices?.[0]?.delta || {};

                // Capture reasoning
                if (delta.reasoning_content) {
                  assistantMsg.reasoning += delta.reasoning_content;
                } else if (delta.reasoning) {
                  assistantMsg.reasoning += delta.reasoning;
                }

                // Capture text content
                if (delta.content) {
                  assistantMsg.content += delta.content;
                }

                parseThinkTags(assistantMsg);
                updateLastAssistantMessage(assistantMsg, true);
              } catch (e) {}
            }
          }
        }
      } else {
        const data = await response.json();
        state.lastRequest.responseJson = JSON.stringify(data, null, 2);
        const msg = data.choices?.[0]?.message || {};
        assistantMsg.content = msg.content || '';
        if (msg.reasoning_content) {
          assistantMsg.reasoning = msg.reasoning_content;
        } else if (msg.reasoning) {
          assistantMsg.reasoning = msg.reasoning;
        }
        parseThinkTags(assistantMsg);
        if (data.usage) {
          assistantMsg.tokens = data.usage.total_tokens;
          dom.telemetryTokens.textContent = `${data.usage.total_tokens} tkn`;
        }
        updateLastAssistantMessage(assistantMsg, false);
      }

      saveSessions();
      updateLastAssistantMessage(assistantMsg, false);
      logAdmin(`Generation completed in ${assistantMsg.latencyMs}ms.`);
    } catch (err) {
      if (err.name === 'AbortError') {
        assistantMsg.content += '\n\n*(Generation stopped by user)*';
      } else {
        assistantMsg.content += `\n\n**Error:** ${err.message}`;
        logAdmin(`Generation failed: ${err.message}`);
        showToast(`Request failed: ${err.message}`);
      }
      updateLastAssistantMessage(assistantMsg, false);
      saveSessions();
    } finally {
      setIsGenerating(false);
      state.abortController = null;
    }
  }

  function parseThinkTags(msg) {
    if (!msg.content) return;

    // Fully closed <think>...</think>
    if (msg.content.includes('<think>') && msg.content.includes('</think>')) {
      const match = msg.content.match(/<think>([\s\S]*?)<\/think>/);
      if (match) {
        msg.reasoning = (msg.reasoning ? msg.reasoning + '\n' : '') + match[1].trim();
        msg.content = msg.content.replace(/<think>[\s\S]*?<\/think>/, '').trim();
      }
    } else if (msg.content.startsWith('<think>')) {
      // In-flight thinking before closing tag
      const thoughtPart = msg.content.substring(7);
      msg.reasoning = thoughtPart;
      msg.isActivelyThinking = true;
    }
  }

  function setIsGenerating(generating) {
    state.isGenerating = generating;
    if (generating) {
      dom.sendBtn.classList.add('stop');
      dom.sendIcon.style.display = 'none';
      dom.stopIcon.style.display = 'block';
    } else {
      dom.sendBtn.classList.remove('stop');
      dom.sendIcon.style.display = 'block';
      dom.stopIcon.style.display = 'none';
    }
  }

  function reRunTurn(idx) {
    const session = getCurrentSession();
    if (!session || !session.messages[idx]) return;
    const msg = session.messages[idx];
    if (msg.role === 'user') {
      dom.userInput.value = msg.content;
      autoResizeInput();
      handleSend();
    } else {
      // Re-trigger assistant turn: remove this assistant message and resend previous user prompt
      session.messages.splice(idx, 1);
      const lastUser = [...session.messages].reverse().find(m => m.role === 'user');
      if (lastUser) {
        dom.userInput.value = lastUser.content;
        session.messages.pop(); // remove user msg too so handleSend re-adds cleanly
        handleSend();
      }
    }
  }

  // ---------------------------------------------------------------------------
  // UI Rendering: Chat Bubbles & Updates
  // ---------------------------------------------------------------------------
  function renderChatMessages() {
    const session = getCurrentSession();
    if (!session || !session.messages.length) {
      dom.emptyState.style.display = 'flex';
      dom.chatScroll.querySelectorAll('.chat-turn').forEach(el => el.remove());
      return;
    }

    dom.emptyState.style.display = 'none';
    dom.chatScroll.querySelectorAll('.chat-turn').forEach(el => el.remove());

    session.messages.forEach((msg, idx) => {
      const turn = createMessageTurnElement(msg, idx);
      dom.chatScroll.appendChild(turn);
    });

    scrollToBottom();
  }

  function createMessageTurnElement(msg, idx) {
    const turn = document.createElement('div');
    turn.className = `chat-turn ${msg.role}`;
    turn.dataset.idx = idx;

    const isUser = msg.role === 'user';
    const authorName = isUser ? 'You' : (msg.model || state.currentModel);

    let reasoningHtml = '';
    if (!isUser && msg.reasoning) {
      reasoningHtml = `
        <div class="reasoning-box">
          <div class="reasoning-header" onclick="this.parentElement.classList.toggle('collapsed')">
            <div class="reasoning-title">
              <span>🧠 Thinking Process</span>
            </div>
            <svg class="reasoning-toggle-icon" viewBox="0 0 24 24"><path d="M7.41 8.59L12 13.17l4.59-4.58L18 10l-6 6-6-6 1.41-1.41z"/></svg>
          </div>
          <div class="reasoning-content">${escapeHtml(msg.reasoning)}</div>
        </div>
      `;
    }

    const contentHtml = isUser
      ? escapeHtml(msg.content)
      : `<div class="markdown-body">${renderMarkdown(msg.content)}</div>`;

    const footerStats = !isUser && msg.latencyMs
      ? `<span>⚡ ${msg.latencyMs}ms</span>`
      : '';

    turn.innerHTML = `
      <div class="turn-header">
        <span class="turn-author ${msg.role}">${escapeHtml(authorName)}</span>
        <span>${new Date(msg.timestamp || Date.now()).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</span>
      </div>
      <div class="turn-content">
        ${reasoningHtml}
        <div class="message-text">${contentHtml}</div>
      </div>
      <div class="turn-footer">
        <div class="turn-stats">${footerStats}</div>
        <div class="turn-actions">
          ${isUser ? `<button class="turn-action-btn" onclick="dom.userInput.value='${escapeHtml(msg.content)}'; dom.userInput.focus();">Edit</button>` : ''}
          <button class="turn-action-btn" onclick="(window.LastLabApp || window.LastChatApp).reRunTurn(${idx})">Re-run</button>
          <button class="turn-action-btn" onclick="navigator.clipboard.writeText(this.closest('.chat-turn').querySelector('.message-text').innerText); (window.LastLabApp || window.LastChatApp).toast('Copied to clipboard');">
            Copy
          </button>
        </div>
      </div>
    `;

    return turn;
  }

  function updateLastAssistantMessage(msg, isStreaming) {
    const turns = dom.chatScroll.querySelectorAll('.chat-turn.assistant');
    if (!turns.length) return;
    const lastTurn = turns[turns.length - 1];

    let reasoningBox = lastTurn.querySelector('.reasoning-box');
    if (msg.reasoning) {
      if (!reasoningBox) {
        reasoningBox = document.createElement('div');
        reasoningBox.className = 'reasoning-box';
        reasoningBox.innerHTML = `
          <div class="reasoning-header" onclick="this.parentElement.classList.toggle('collapsed')">
            <div class="reasoning-title">
              <span>🧠 Thinking Process</span>
            </div>
            <svg class="reasoning-toggle-icon" viewBox="0 0 24 24"><path d="M7.41 8.59L12 13.17l4.59-4.58L18 10l-6 6-6-6 1.41-1.41z"/></svg>
          </div>
          <div class="reasoning-content"></div>
        `;
        lastTurn.querySelector('.turn-content').prepend(reasoningBox);
      }
      reasoningBox.querySelector('.reasoning-content').textContent = msg.reasoning;
    }

    const textEl = lastTurn.querySelector('.message-text');
    if (textEl) {
      const showContent = msg.isActivelyThinking ? '' : msg.content;
      textEl.innerHTML = `<div class="markdown-body">${renderMarkdown(showContent)}${isStreaming ? '<span class="streaming-cursor"></span>' : ''}</div>`;
    }

    const statsEl = lastTurn.querySelector('.turn-stats');
    if (statsEl && msg.latencyMs) {
      statsEl.innerHTML = `<span>⚡ ${msg.latencyMs}ms</span>`;
    }

    scrollToBottom();
  }

  function scrollToBottom() {
    dom.chatScroll.scrollTop = dom.chatScroll.scrollHeight;
  }

  function autoResizeInput() {
    dom.userInput.style.height = 'auto';
    dom.userInput.style.height = Math.min(dom.userInput.scrollHeight, 120) + 'px';
  }

  // ---------------------------------------------------------------------------
  // View & Tab Switching
  // ---------------------------------------------------------------------------
  function switchToView(viewId) {
    closeModelPicker();
    dom.inspectorModal.classList.remove('active');
    state.activeView = viewId;
    document.querySelectorAll('.view-pane').forEach(p => {
      p.classList.toggle('active', p.id === viewId);
    });
    document.querySelectorAll('.nav-item').forEach(b => {
      b.classList.toggle('active', b.dataset.view === viewId);
    });

    if (viewId === 'sessions-view') {
      renderSessionsList();
    } else if (viewId === 'admin-view') {
      fetchProviders();
    }

    try {
      history.pushState({ view: viewId }, '');
    } catch (e) {}
  }

  // ---------------------------------------------------------------------------
  // Android Back Button Handler (Native Bridge)
  // ---------------------------------------------------------------------------
  function handleBackPressed() {
    // 1. If model picker modal is active, close it
    if (dom.modelPickerModal && dom.modelPickerModal.classList.contains('active')) {
      closeModelPicker();
      return true;
    }

    // 2. If inspector modal is active, close it
    if (dom.inspectorModal && dom.inspectorModal.classList.contains('active')) {
      dom.inspectorModal.classList.remove('active');
      return true;
    }

    // 3. If currently in a non-chat tab (Tuning, Sessions, Admin), navigate back to Chat
    if (state.activeView !== 'chat-view') {
      switchToView('chat-view');
      return true;
    }

    // 4. On root chat view with no modals open -> return false to allow native app exit
    return false;
  }

  // ---------------------------------------------------------------------------
  // Event Bindings
  // ---------------------------------------------------------------------------
  function bindEvents() {
    // Navigation
    document.querySelectorAll('.nav-item').forEach(btn => {
      btn.addEventListener('click', () => switchToView(btn.dataset.view));
    });

    // Model Selector Modal
    dom.modelTriggerBtn.addEventListener('click', openModelPicker);
    dom.closeModelPickerBtn.addEventListener('click', closeModelPicker);
    dom.modelPickerModal.addEventListener('click', (e) => {
      if (e.target === dom.modelPickerModal) closeModelPicker();
    });

    dom.modelSearchInput.addEventListener('input', (e) => {
      state.searchQuery = e.target.value;
      renderModelRegistry();
    });

    if (dom.clearSearchBtn) {
      dom.clearSearchBtn.addEventListener('click', () => {
        dom.modelSearchInput.value = '';
        state.searchQuery = '';
        renderModelRegistry();
        dom.modelSearchInput.focus();
      });
    }

    document.querySelectorAll('.modal-tab-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        document.querySelectorAll('.modal-tab-btn').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        state.activeFilter = btn.dataset.filter;
        renderModelRegistry();
      });
    });

    dom.setCustomModelBtn.addEventListener('click', () => {
      const customId = dom.customModelInput.value.trim();
      if (customId) {
        selectModel(customId);
        dom.customModelInput.value = '';
      }
    });

    // Input & Send
    dom.userInput.addEventListener('input', autoResizeInput);
    dom.userInput.addEventListener('keydown', (e) => {
      if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        handleSend();
      }
    });
    dom.sendBtn.addEventListener('click', handleSend);

    // Starter Prompt Chips
    document.querySelectorAll('.prompt-chip').forEach(chip => {
      chip.addEventListener('click', () => {
        dom.userInput.value = chip.dataset.prompt;
        autoResizeInput();
        dom.userInput.focus();
      });
    });

    // Top Bar Actions
    dom.newChatTopBtn.addEventListener('click', () => createNewSession('New Playground'));
    dom.createSessionBtn.addEventListener('click', () => createNewSession('New Playground'));
    dom.clearChatBtn.addEventListener('click', () => {
      const sess = getCurrentSession();
      if (sess) {
        sess.messages = [];
        saveSessions();
        renderChatMessages();
        showToast('Chat cleared');
      }
    });
    if (dom.exportChatBtn) {
      dom.exportChatBtn.addEventListener('click', exportCurrentSession);
    }

    // Quick Toolbar buttons
    dom.quickParamsBtn.addEventListener('click', () => switchToView('tuning-view'));
    dom.quickReasoningBtn.addEventListener('click', () => {
      const order = ['none', 'low', 'medium', 'high'];
      const nextIdx = (order.indexOf(state.tuning.reasoningEffort) + 1) % order.length;
      state.tuning.reasoningEffort = order[nextIdx];
      dom.quickReasoningLabel.textContent = `Reason: ${state.tuning.reasoningEffort.toUpperCase()}`;
      updateReasoningButtons();
      saveTuning();
      showToast(`Reasoning Effort: ${state.tuning.reasoningEffort}`);
    });

    // Tuning Presets
    document.querySelectorAll('.preset-btn[data-preset]').forEach(btn => {
      btn.addEventListener('click', () => {
        document.querySelectorAll('.preset-btn[data-preset]').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        const text = SYSTEM_PRESETS[btn.dataset.preset];
        if (text) {
          state.tuning.systemPrompt = text;
          dom.systemPromptInput.value = text;
          saveTuning();
        }
      });
    });

    dom.systemPromptInput.addEventListener('input', (e) => {
      state.tuning.systemPrompt = e.target.value;
      saveTuning();
    });

    // Tuning Sliders
    function syncSlider(slider, valEl, key, formatFn = v => v) {
      slider.addEventListener('input', (e) => {
        const val = parseFloat(e.target.value);
        state.tuning[key] = val;
        valEl.textContent = formatFn(val);
        saveTuning();
      });
    }

    syncSlider(dom.paramTemp, dom.valTemp, 'temperature', v => v.toFixed(2));
    syncSlider(dom.paramTopP, dom.valTopP, 'topP', v => v.toFixed(2));
    syncSlider(dom.paramMaxTokens, dom.valMaxTokens, 'maxTokens', v => v);
    syncSlider(dom.paramFreq, dom.valFreq, 'frequencyPenalty', v => v.toFixed(2));
    syncSlider(dom.paramPres, dom.valPres, 'presencePenalty', v => v.toFixed(2));

    dom.paramStream.addEventListener('change', (e) => {
      state.tuning.stream = e.target.checked;
      saveTuning();
    });

    // Reasoning Effort Buttons
    document.querySelectorAll('#reasoning-effort-ctrl .segment-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        state.tuning.reasoningEffort = btn.dataset.val;
        updateReasoningButtons();
        dom.quickReasoningLabel.textContent = `Reason: ${state.tuning.reasoningEffort.toUpperCase()}`;
        saveTuning();
      });
    });

    dom.resetParamsBtn.addEventListener('click', () => {
      state.tuning = { ...DEFAULT_TUNING };
      populateTuningUI();
      saveTuning();
      showToast('Parameters reset to defaults');
    });

    // Admin Controls
    dom.adminBaseUrl.addEventListener('input', (e) => {
      state.admin.baseUrl = e.target.value.trim().replace(/\/+$/, '');
    });
    dom.adminTokenInput.addEventListener('input', (e) => {
      state.admin.adminToken = e.target.value;
    });
    dom.saveAdminBtn.addEventListener('click', () => {
      saveAdmin();
      showToast('Admin settings saved');
      logAdmin(`Saved Admin configuration. Base URL: ${state.admin.baseUrl}`);
      fetchModels();
      fetchProviders();
    });
    dom.pingGatewayBtn.addEventListener('click', pingGateway);

    // Raw Inspector
    dom.inspectRawBtn.addEventListener('click', () => {
      dom.inspectorModal.classList.add('active');
      renderInspector();
      try { history.pushState({ modal: 'inspector' }, ''); } catch (e) {}
    });
    dom.closeInspectorBtn.addEventListener('click', () => {
      dom.inspectorModal.classList.remove('active');
    });
    dom.inspectorModal.addEventListener('click', (e) => {
      if (e.target === dom.inspectorModal) dom.inspectorModal.classList.remove('active');
    });

    document.querySelectorAll('.inspector-tab').forEach(tab => {
      tab.addEventListener('click', () => {
        document.querySelectorAll('.inspector-tab').forEach(t => t.classList.remove('active'));
        tab.classList.add('active');
        state.activeInspectorTab = tab.dataset.tab;
        renderInspector();
      });
    });

    dom.copyInspectorBtn.addEventListener('click', () => {
      navigator.clipboard.writeText(dom.inspectorCode.textContent);
      showToast('Copied to clipboard');
    });

    // Global ESC key to close any modal
    document.addEventListener('keydown', (e) => {
      if (e.key === 'Escape') {
        handleBackPressed();
      }
    });

    // Browser Popstate (History Navigation)
    window.addEventListener('popstate', (e) => {
      handleBackPressed();
    });
  }

  function updateReasoningButtons() {
    document.querySelectorAll('#reasoning-effort-ctrl .segment-btn').forEach(btn => {
      btn.classList.toggle('active', btn.dataset.val === state.tuning.reasoningEffort);
    });
  }

  function populateTuningUI() {
    dom.systemPromptInput.value = state.tuning.systemPrompt;
    dom.paramTemp.value = state.tuning.temperature;
    dom.valTemp.textContent = state.tuning.temperature.toFixed(2);
    dom.quickTempLabel.textContent = `T: ${state.tuning.temperature.toFixed(2)}`;

    dom.paramTopP.value = state.tuning.topP;
    dom.valTopP.textContent = state.tuning.topP.toFixed(2);

    dom.paramMaxTokens.value = state.tuning.maxTokens;
    dom.valMaxTokens.textContent = state.tuning.maxTokens;

    dom.paramFreq.value = state.tuning.frequencyPenalty;
    dom.valFreq.textContent = state.tuning.frequencyPenalty.toFixed(2);

    dom.paramPres.value = state.tuning.presencePenalty;
    dom.valPres.textContent = state.tuning.presencePenalty.toFixed(2);

    dom.paramStream.checked = state.tuning.stream;
    updateReasoningButtons();
    dom.quickReasoningLabel.textContent = `Reason: ${state.tuning.reasoningEffort.toUpperCase()}`;
  }

  function populateAdminUI() {
    dom.adminBaseUrl.value = state.admin.baseUrl;
    dom.adminTokenInput.value = state.admin.adminToken;
    dom.gatewayStatusPill.textContent = `Gateway: ${state.admin.baseUrl}`;
  }

  function renderInspector() {
    if (state.activeInspectorTab === 'curl') {
      dom.inspectorCode.textContent = state.lastRequest.curl;
    } else if (state.activeInspectorTab === 'request') {
      dom.inspectorCode.textContent = state.lastRequest.requestJson;
    } else if (state.activeInspectorTab === 'response') {
      dom.inspectorCode.textContent = state.lastRequest.responseJson;
    }
  }

  // ---------------------------------------------------------------------------
  // Global Exposure
  // ---------------------------------------------------------------------------
  const appExports = {
    deleteSession,
    renameSession,
    exportCurrentSession,
    openModelPicker,
    switchToView,
    toast: showToast,
    selectModel,
    togglePinModel,
    probeModel,
    reRunTurn,
    onBackPressed: handleBackPressed,
    renderChatMessages,
    state,
  };
  window.LastLabApp = appExports;
  window.LastChatApp = appExports; // Backwards compatibility bridge

  // ---------------------------------------------------------------------------
  // Bootstrap Application
  // ---------------------------------------------------------------------------
  function init() {
    loadSavedData();
    populateTuningUI();
    populateAdminUI();
    renderQuickShelf();
    renderProviderFilterChips();
    renderModelRegistry();
    renderProvidersGrid();
    updateCurrentModelDisplay();
    bindEvents();
    renderChatMessages();

    // Fetch live models and providers asynchronously
    fetchModels();
    fetchProviders();

    logAdmin('LastLab initialized successfully.');
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }

})();
