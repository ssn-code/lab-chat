(() => {
  'use strict';

  // State Management
  const state = {
    username: localStorage.getItem('labchat_username') || '',
    currentRoom: '#general',
    activeDmUser: null, // if chatting in DM
    rooms: [],
    users: [],
    messages: [],
    pinnedMessages: [],
    polls: [],
    bookmarks: JSON.parse(localStorage.getItem('labchat_bookmarks') || '[]'),
    unreadCounts: JSON.parse(localStorage.getItem('labchat_unreads') || '{}'),
    replyTo: null, // { id, user, text }
    attachedFile: null, // { id, name, size }
    codeMode: false,
    settings: {
      theme: localStorage.getItem('labchat_theme') || 'dark',
      density: localStorage.getItem('labchat_density') || 'comfortable',
      sound: localStorage.getItem('labchat_sound') !== 'false',
      enterSend: localStorage.getItem('labchat_enter_send') !== 'false'
    },
    adminToken: sessionStorage.getItem('labchat_admin_token') || '',
    pollingInterval: 1200,
    isReconnecting: false,
    pollTimer: null,
    lastTypingSent: 0
  };

  // DOM Elements
  const el = {
    // Layout & Sidebars
    sidebar: document.getElementById('sidebar'),
    usersSidebar: document.getElementById('users-sidebar'),
    btnSidebarToggle: document.getElementById('btn-sidebar-toggle'),
    btnUsersToggle: document.getElementById('btn-users-toggle'),
    roomList: document.getElementById('room-list'),
    dmList: document.getElementById('dm-list'),
    onlineUsersList: document.getElementById('online-users-list'),
    idleUsersList: document.getElementById('idle-users-list'),
    offlineUsersList: document.getElementById('offline-users-list'),
    onlineCounter: document.getElementById('online-counter'),
    usersBadge: document.getElementById('users-badge'),
    connBadge: document.getElementById('conn-badge'),

    // User Footer
    myAvatar: document.getElementById('my-avatar'),
    myName: document.getElementById('my-name'),
    myPresenceText: document.getElementById('my-presence-text'),
    myStatusDot: document.getElementById('my-status-dot'),

    // Chat Header & Banners
    currentChannelTitle: document.getElementById('current-channel-title'),
    channelDesc: document.getElementById('channel-desc'),
    announcementBanner: document.getElementById('announcement-banner'),
    announcementText: document.getElementById('announcement-text'),
    btnDismissAnnouncement: document.getElementById('btn-dismiss-announcement'),
    pinnedBar: document.getElementById('pinned-bar'),
    pinnedSummary: document.getElementById('pinned-summary'),
    btnViewPinned: document.getElementById('btn-view-pinned'),
    btnPinned: document.getElementById('btn-pinned'),
    pinnedCount: document.getElementById('pinned-count'),
    btnPolls: document.getElementById('btn-polls'),

    // Messages
    messagesContainer: document.getElementById('messages-container'),
    typingIndicator: document.getElementById('typing-indicator'),
    typingText: document.getElementById('typing-text'),

    // Bars above input
    replyBar: document.getElementById('reply-bar'),
    replyToUser: document.getElementById('reply-to-user'),
    replyToText: document.getElementById('reply-to-text'),
    btnCancelReply: document.getElementById('btn-cancel-reply'),
    attachmentBar: document.getElementById('attachment-bar'),
    attachmentName: document.getElementById('attachment-name'),
    attachmentSize: document.getElementById('attachment-size'),
    btnCancelAttachment: document.getElementById('btn-cancel-attachment'),
    codeSnippetBar: document.getElementById('code-snippet-bar'),
    codeLanguageSelect: document.getElementById('code-language-select'),
    btnCancelCode: document.getElementById('btn-cancel-code'),

    // Composer
    messageForm: document.getElementById('message-form'),
    messageInput: document.getElementById('message-input'),
    btnSend: document.getElementById('btn-send'),
    btnAttach: document.getElementById('btn-attach'),
    fileInput: document.getElementById('file-input'),
    btnCode: document.getElementById('btn-code'),
    btnEmoji: document.getElementById('btn-emoji'),
    emojiPopup: document.getElementById('emoji-popup'),
    emojiGrid: document.getElementById('emoji-grid'),

    // Modals
    joinDialog: document.getElementById('join-dialog'),
    joinForm: document.getElementById('join-form'),
    usernameInput: document.getElementById('username-input'),
    btnGuestJoin: document.getElementById('btn-guest-join'),
    joinError: document.getElementById('join-error'),

    createRoomDialog: document.getElementById('create-room-dialog'),
    createRoomForm: document.getElementById('create-room-form'),
    roomNameInput: document.getElementById('room-name-input'),
    btnCreateRoom: document.getElementById('btn-create-room'),
    roomError: document.getElementById('room-error'),

    searchDialog: document.getElementById('search-dialog'),
    searchInput: document.getElementById('search-input'),
    searchResults: document.getElementById('search-results'),
    btnSearch: document.getElementById('btn-search'),

    bookmarksDialog: document.getElementById('bookmarks-dialog'),
    bookmarksList: document.getElementById('bookmarks-list'),
    btnBookmarks: document.getElementById('btn-bookmarks'),

    pinnedDialog: document.getElementById('pinned-dialog'),
    pinnedList: document.getElementById('pinned-list'),

    pollsDialog: document.getElementById('polls-dialog'),
    pollsListContainer: document.getElementById('polls-list-container'),
    btnOpenCreatePoll: document.getElementById('btn-open-create-poll'),
    createPollDialog: document.getElementById('create-poll-dialog'),
    createPollForm: document.getElementById('create-poll-form'),
    pollQuestion: document.getElementById('poll-question'),
    pollError: document.getElementById('poll-error'),

    settingsDialog: document.getElementById('settings-dialog'),
    btnSettings: document.getElementById('btn-settings'),
    settingTheme: document.getElementById('setting-theme'),
    settingDensity: document.getElementById('setting-density'),
    settingSound: document.getElementById('setting-sound'),
    settingEnterSend: document.getElementById('setting-enter-send'),
    btnEnableNotifications: document.getElementById('btn-enable-notifications'),
    btnShowShortcuts: document.getElementById('btn-show-shortcuts'),
    shortcutsDialog: document.getElementById('shortcuts-dialog'),

    adminDialog: document.getElementById('admin-dialog'),
    btnAdmin: document.getElementById('btn-admin'),
    adminTokenInput: document.getElementById('admin-token-input'),
    btnAdminLogin: document.getElementById('btn-admin-login'),
    adminAuthError: document.getElementById('admin-auth-error'),
    adminToolsPanel: document.getElementById('admin-tools-panel'),
    statHost: document.getElementById('stat-host'),
    statPort: document.getElementById('stat-port'),
    statUptime: document.getElementById('stat-uptime'),
    statUsers: document.getElementById('stat-users'),
    statRooms: document.getElementById('stat-rooms'),
    statMessages: document.getElementById('stat-messages'),
    statFiles: document.getElementById('stat-files'),
    statMemory: document.getElementById('stat-memory'),
    adminAnnouncementInput: document.getElementById('admin-announcement-input'),
    btnPostAnnouncement: document.getElementById('btn-post-announcement'),
    btnClearAnnouncement: document.getElementById('btn-clear-announcement'),
    adminTargetUser: document.getElementById('admin-target-user'),
    btnAdminKick: document.getElementById('btn-admin-kick'),
    btnAdminMute: document.getElementById('btn-admin-mute'),
    btnAdminUnmute: document.getElementById('btn-admin-unmute'),
    btnAdminBan: document.getElementById('btn-admin-ban'),
    btnAdminClearRoom: document.getElementById('btn-admin-clear-room'),

    imageModal: document.getElementById('image-modal'),
    lightboxImg: document.getElementById('lightbox-img'),
    lightboxDownload: document.getElementById('lightbox-download')
  };

  // HTTP API Helpers
  async function api(path, options = {}) {
    const defaultHeaders = {
      'Cache-Control': 'no-store'
    };
    if (state.adminToken) {
      defaultHeaders['Authorization'] = 'Bearer ' + state.adminToken;
    }
    const res = await fetch(path, {
      ...options,
      headers: { ...defaultHeaders, ...(options.headers || {}) }
    });

    const isJson = res.headers.get('content-type')?.includes('application/json');
    const data = isJson ? await res.json().catch(() => ({})) : null;

    if (!res.ok) {
      const err = (data && data.error && data.error.message) || data.error || `HTTP ${res.status}`;
      throw new Error(err);
    }
    return data && data.data ? data.data : data;
  }

  const postJson = (path, body) => api(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json; charset=utf-8' },
    body: JSON.stringify(body)
  });

  // Synthesize Web Audio chime for notification (Zero external audio files)
  function playNotificationChime() {
    if (!state.settings.sound) return;
    try {
      const ctx = new (window.AudioContext || window.webkitAudioContext)();
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();

      osc.type = 'sine';
      osc.frequency.setValueAtTime(587.33, ctx.currentTime); // D5
      osc.frequency.exponentialRampToValueAtTime(880, ctx.currentTime + 0.15); // A5

      gain.gain.setValueAtTime(0.08, ctx.currentTime);
      gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.35);

      osc.connect(gain);
      gain.connect(ctx.destination);

      osc.start();
      osc.stop(ctx.currentTime + 0.35);
    } catch (e) {
      // AudioContext might be blocked until user interacts with document
    }
  }

  function sendBrowserNotification(title, body) {
    if ('Notification' in window && Notification.permission === 'granted') {
      new Notification(title, { body, icon: '/favicon.ico' });
    }
  }

  // Init Theme & Settings
  function applySettings() {
    document.body.setAttribute('data-theme', state.settings.theme);
    if (state.settings.density === 'compact') {
      document.body.classList.add('density-compact');
    } else {
      document.body.classList.remove('density-compact');
    }
    el.settingTheme.value = state.settings.theme;
    el.settingDensity.value = state.settings.density;
    el.settingSound.checked = state.settings.sound;
    el.settingEnterSend.checked = state.settings.enterSend;
  }

  // Connection Indicator
  function setConnectionStatus(status) {
    if (status === 'connected') {
      el.connBadge.innerHTML = '<span class="dot green"></span> Connected';
      state.isReconnecting = false;
    } else if (status === 'reconnecting') {
      el.connBadge.innerHTML = '<span class="dot yellow"></span> Reconnecting...';
      state.isReconnecting = true;
    } else {
      el.connBadge.innerHTML = '<span class="dot red"></span> Disconnected';
      state.isReconnecting = true;
    }
  }

  // User Initials Avatar
  function getInitials(name) {
    if (!name) return '?';
    const parts = name.trim().split(/[_\s-]+/);
    if (parts.length >= 2) {
      return (parts[0][0] + parts[1][0]).toUpperCase();
    }
    return name.slice(0, 2).toUpperCase();
  }

  function updateUserProfileUI() {
    if (!state.username) {
      el.myName.textContent = 'Not Joined';
      el.myAvatar.textContent = '--';
      el.myPresenceText.textContent = 'Offline';
      el.myStatusDot.className = 'status-dot offline';
      el.messageInput.disabled = true;
      el.btnSend.disabled = true;
      return;
    }
    el.myName.textContent = state.username;
    el.myAvatar.textContent = getInitials(state.username);
    el.myPresenceText.textContent = 'Online';
    el.myStatusDot.className = 'status-dot';
    el.messageInput.disabled = false;
    el.btnSend.disabled = false;
  }

  // Core Polling & Sync Loop
  async function refreshChat() {
    if (!state.username) return;

    try {
      // Send Heartbeat & get active state
      await postJson('/api/presence', {
        username: state.username,
        status: 'online',
        currentRoom: state.currentRoom
      });

      // Fetch messages for active context (Room or DM)
      let msgUrl = '';
      if (state.activeDmUser) {
        msgUrl = `/api/private/messages?username=${encodeURIComponent(state.username)}&target=${encodeURIComponent(state.activeDmUser)}`;
      } else {
        msgUrl = `/api/messages?room=${encodeURIComponent(state.currentRoom)}&username=${encodeURIComponent(state.username)}`;
      }

      const [msgData, usersData, roomsData, statusData] = await Promise.all([
        api(msgUrl),
        api('/api/users'),
        api('/api/rooms'),
        api('/api/server/status')
      ]);

      setConnectionStatus('connected');

      // Update Rooms
      state.rooms = roomsData.rooms || [];
      renderRooms();

      // Update Users
      state.users = usersData.users || [];
      renderUsers();

      // Update Announcement
      if (statusData && statusData.announcement) {
        el.announcementText.textContent = statusData.announcement;
        el.announcementBanner.classList.remove('hidden');
      } else {
        el.announcementBanner.classList.add('hidden');
      }

      // Update Server Status Modal Stats
      if (statusData) {
        el.statHost.textContent = statusData.host;
        el.statPort.textContent = statusData.port;
        el.statUptime.textContent = statusData.uptime;
        el.statUsers.textContent = statusData.activeUsers;
        el.statRooms.textContent = statusData.activeRooms;
        el.statMessages.textContent = statusData.totalMessages;
        el.statFiles.textContent = statusData.totalFiles;
        el.statMemory.textContent = statusData.memoryUsage;
      }

      // Update Typing indicator
      if (msgData.typing && msgData.typing.length > 0) {
        el.typingIndicator.classList.remove('hidden');
        if (msgData.typing.length === 1) {
          el.typingText.textContent = `${msgData.typing[0]} is typing...`;
        } else {
          el.typingText.textContent = `${msgData.typing.slice(0, 2).join(' and ')} are typing...`;
        }
      } else {
        el.typingIndicator.classList.add('hidden');
      }

      // Check for incoming notifications
      const prevMsgCount = state.messages.length;
      const newMessages = msgData.messages || [];

      if (prevMsgCount > 0 && newMessages.length > prevMsgCount) {
        const latest = newMessages[newMessages.length - 1];
        if (latest.username !== state.username && !latest.system) {
          playNotificationChime();
          // Mentions or DM notification
          if (latest.message && latest.message.includes('@' + state.username)) {
            sendBrowserNotification(`New mention from ${latest.username}`, latest.message);
          } else if (state.activeDmUser) {
            sendBrowserNotification(`New message from ${latest.username}`, latest.message);
          }
        }
      }

      state.messages = newMessages;
      renderMessages();

      // Check pinned messages in room
      if (!state.activeDmUser) {
        checkPinnedMessages();
      } else {
        el.pinnedBar.classList.add('hidden');
        el.pinnedCount.textContent = '0';
      }

    } catch (err) {
      console.warn('Sync poll error:', err);
      setConnectionStatus('reconnecting');
    }
  }

  // Render Rooms Navigation
  function renderRooms() {
    el.roomList.innerHTML = '';
    state.rooms.forEach(r => {
      const li = document.createElement('li');
      const isActive = !state.activeDmUser && state.currentRoom === r.name;
      li.className = `nav-item ${isActive ? 'active' : ''}`;

      const left = document.createElement('div');
      left.className = 'nav-item-left';
      left.innerHTML = `<span>#</span> <span>${escapeHtml(r.name.replace(/^#/, ''))}</span>`;

      const right = document.createElement('div');
      right.style.display = 'flex';
      right.style.alignItems = 'center';
      right.style.gap = '6px';

      const unread = state.unreadCounts[r.name] || 0;
      if (unread > 0 && !isActive) {
        const badge = document.createElement('span');
        badge.className = 'nav-badge';
        badge.textContent = unread;
        right.appendChild(badge);
      }

      const count = document.createElement('span');
      count.style.fontSize = '0.72rem';
      count.style.color = 'var(--text-dim)';
      count.textContent = r.memberCount > 0 ? r.memberCount : '';
      right.appendChild(count);

      li.append(left, right);
      li.onclick = () => switchRoom(r.name);
      el.roomList.appendChild(li);
    });
  }

  // Render Direct Messages Navigation
  function renderDms() {
    el.dmList.innerHTML = '';
    const otherUsers = state.users.filter(u => u.username !== state.username);

    if (otherUsers.length === 0) {
      const empty = document.createElement('li');
      empty.className = 'subtext';
      empty.style.padding = '8px 10px';
      empty.textContent = 'No other users online yet.';
      el.dmList.appendChild(empty);
      return;
    }

    otherUsers.forEach(u => {
      const li = document.createElement('li');
      const isActive = state.activeDmUser === u.username;
      li.className = `nav-item ${isActive ? 'active' : ''}`;

      const left = document.createElement('div');
      left.className = 'nav-item-left';
      left.innerHTML = `<span class="dot ${u.status === 'online' ? 'green' : (u.status === 'idle' ? 'yellow' : '')}"></span> <span>${escapeHtml(u.username)}</span>`;

      const unread = state.unreadCounts['@' + u.username] || 0;
      if (unread > 0 && !isActive) {
        const badge = document.createElement('span');
        badge.className = 'nav-badge';
        badge.textContent = unread;
        li.appendChild(badge);
      }

      li.append(left);
      li.onclick = () => switchDm(u.username);
      el.dmList.appendChild(li);
    });
  }

  // Render Active Users Sidebar
  function renderUsers() {
    el.onlineUsersList.innerHTML = '';
    el.idleUsersList.innerHTML = '';
    el.offlineUsersList.innerHTML = '';

    let onlineCount = 0;
    state.users.forEach(u => {
      if (u.status === 'online') onlineCount++;

      const li = document.createElement('li');
      li.className = 'user-item';
      li.title = `Click to direct message ${u.username}`;

      const av = document.createElement('div');
      av.className = 'avatar';
      av.style.width = '28px';
      av.style.height = '28px';
      av.style.fontSize = '0.75rem';
      av.textContent = getInitials(u.username);

      const meta = document.createElement('div');
      meta.className = 'user-item-meta';

      const name = document.createElement('span');
      name.className = 'user-item-name';
      name.textContent = u.username + (u.username === state.username ? ' (You)' : '');

      const sub = document.createElement('span');
      sub.className = 'user-item-sub';
      sub.textContent = u.currentRoom || '#general';

      meta.append(name, sub);
      li.append(av, meta);

      li.onclick = () => {
        if (u.username !== state.username) {
          switchDm(u.username);
        }
      };

      if (u.status === 'online') {
        el.onlineUsersList.appendChild(li);
      } else if (u.status === 'idle' || u.status === 'away') {
        el.idleUsersList.appendChild(li);
      } else {
        el.offlineUsersList.appendChild(li);
      }
    });

    el.onlineCounter.textContent = onlineCount;
    el.usersBadge.textContent = state.users.length;
    renderDms();
  }

  // Render Messages List
  function renderMessages() {
    const isAtBottom = el.messagesContainer.scrollHeight - el.messagesContainer.scrollTop - el.messagesContainer.clientHeight < 70;

    el.messagesContainer.innerHTML = '';

    if (state.messages.length === 0) {
      const p = document.createElement('p');
      p.className = 'system-message';
      p.textContent = state.activeDmUser
        ? `This is the start of your direct message history with @${state.activeDmUser}.`
        : `Welcome to ${state.currentRoom}! No messages here yet.`;
      el.messagesContainer.appendChild(p);
      return;
    }

    state.messages.forEach(msg => {
      if (msg.system) {
        const sys = document.createElement('div');
        sys.className = 'system-message';
        sys.textContent = msg.message;
        el.messagesContainer.appendChild(sys);
        return;
      }

      const card = document.createElement('article');
      card.id = `msg-${msg.id}`;
      card.className = `message-card ${msg.username === state.username ? 'mine' : ''}`;

      // User Avatar
      const av = document.createElement('div');
      av.className = 'message-avatar';
      av.textContent = getInitials(msg.username);

      // Content Box
      const content = document.createElement('div');
      content.className = 'message-content';

      // Meta: Author & Timestamp & Pinned status
      const meta = document.createElement('div');
      meta.className = 'message-meta';

      const author = document.createElement('span');
      author.className = 'author-name';
      author.textContent = msg.username;
      author.onclick = () => {
        if (msg.username !== state.username) switchDm(msg.username);
      };

      const time = document.createElement('span');
      time.className = 'message-time';
      time.textContent = new Date(msg.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

      meta.append(author, time);

      if (msg.edited) {
        const ed = document.createElement('span');
        ed.className = 'edited-tag';
        ed.textContent = '(edited)';
        meta.appendChild(ed);
      }

      if (msg.pinned) {
        const pinTag = document.createElement('span');
        pinTag.className = 'pinned-tag';
        pinTag.textContent = '📌 PINNED';
        meta.appendChild(pinTag);
      }

      content.appendChild(meta);

      // Reply Reference (if quoted)
      if (msg.replyTo) {
        const replyBox = document.createElement('div');
        replyBox.className = 'reply-quote-box';
        replyBox.innerHTML = `<strong>@${escapeHtml(msg.replyTo.user)}:</strong> ${escapeHtml(msg.replyTo.snippet)}`;
        replyBox.onclick = () => scrollToMessage(msg.replyTo.id);
        content.appendChild(replyBox);
      }

      // Message Body
      const body = document.createElement('div');
      body.className = `message-body ${msg.deleted ? 'deleted' : ''}`;

      if (msg.deleted) {
        body.textContent = 'This message was deleted.';
      } else {
        // Highlight mentions
        body.innerHTML = formatMessageText(msg.message);
      }
      content.appendChild(body);

      // Code Snippet (if present)
      if (msg.codeLanguage && !msg.deleted) {
        const codeBox = document.createElement('div');
        codeBox.className = 'code-container';

        const codeHead = document.createElement('div');
        codeHead.className = 'code-header';
        codeHead.innerHTML = `<span>${escapeHtml(msg.codeLanguage)}</span>`;

        const copyBtn = document.createElement('button');
        copyBtn.className = 'copy-btn';
        copyBtn.textContent = 'Copy Code';
        copyBtn.onclick = () => {
          navigator.clipboard.writeText(msg.message);
          copyBtn.textContent = 'Copied!';
          setTimeout(() => { copyBtn.textContent = 'Copy Code'; }, 2000);
        };
        codeHead.appendChild(copyBtn);

        const codeContent = document.createElement('pre');
        codeContent.className = 'code-content';
        codeContent.textContent = msg.message;

        codeBox.append(codeHead, codeContent);
        content.appendChild(codeBox);
      }

      // Attached File or Image
      if (msg.file && !msg.deleted) {
        if (msg.file.isImage) {
          const imgBox = document.createElement('div');
          imgBox.className = 'image-preview-container';
          const img = document.createElement('img');
          img.src = `/api/download?id=${encodeURIComponent(msg.file.id)}`;
          img.alt = msg.file.originalName;
          img.loading = 'lazy';
          img.onclick = () => openLightbox(img.src, msg.file.id, msg.file.originalName);
          imgBox.appendChild(img);
          content.appendChild(imgBox);
        } else {
          const fileCard = document.createElement('div');
          fileCard.className = 'attachment-card';
          fileCard.innerHTML = `
            <div class="attachment-info">
              <span class="file-icon">📄</span>
              <div>
                <strong>${escapeHtml(msg.file.originalName)}</strong>
                <div class="subtext">${formatBytes(msg.file.sizeBytes)}</div>
              </div>
            </div>
            <a href="/api/download?id=${encodeURIComponent(msg.file.id)}" class="primary-btn sm" download>Download</a>
          `;
          content.appendChild(fileCard);
        }
      }

      // Reactions Row
      if (msg.reactions && !msg.deleted) {
        const reactionsRow = document.createElement('div');
        reactionsRow.className = 'reactions-row';

        Object.keys(msg.reactions).forEach(emoji => {
          const rData = msg.reactions[emoji];
          if (rData.count > 0) {
            const chip = document.createElement('button');
            const hasReacted = rData.users && rData.users.includes(state.username);
            chip.className = `reaction-chip ${hasReacted ? 'reacted' : ''}`;
            chip.innerHTML = `<span>${emoji}</span> <span>${rData.count}</span>`;
            chip.onclick = () => toggleReaction(msg.id, emoji);
            reactionsRow.appendChild(chip);
          }
        });
        content.appendChild(reactionsRow);
      }

      // Hover Action Bar
      if (!msg.deleted) {
        const actions = document.createElement('div');
        actions.className = 'message-actions';

        // Quick reactions: 👍, ❤️, 😂, 🔥
        ['👍', '❤️', '🔥'].forEach(em => {
          const btn = document.createElement('button');
          btn.className = 'action-icon-btn';
          btn.textContent = em;
          btn.onclick = () => toggleReaction(msg.id, em);
          actions.appendChild(btn);
        });

        // Reply
        const replyBtn = document.createElement('button');
        replyBtn.className = 'action-icon-btn';
        replyBtn.title = 'Reply';
        replyBtn.textContent = '↩️';
        replyBtn.onclick = () => setReplyTarget(msg);
        actions.appendChild(replyBtn);

        // Bookmark
        const bmBtn = document.createElement('button');
        bmBtn.className = 'action-icon-btn';
        bmBtn.title = 'Bookmark message';
        bmBtn.textContent = isBookmarked(msg.id) ? '⭐' : '☆';
        bmBtn.onclick = () => toggleBookmark(msg);
        actions.appendChild(bmBtn);

        // Copy text
        const copyMsgBtn = document.createElement('button');
        copyMsgBtn.className = 'action-icon-btn';
        copyMsgBtn.title = 'Copy text';
        copyMsgBtn.textContent = '📋';
        copyMsgBtn.onclick = () => navigator.clipboard.writeText(msg.message);
        actions.appendChild(copyMsgBtn);

        // Edit (own messages only)
        if (msg.username === state.username) {
          const editBtn = document.createElement('button');
          editBtn.className = 'action-icon-btn';
          editBtn.title = 'Edit';
          editBtn.textContent = '✏️';
          editBtn.onclick = () => editMessagePrompt(msg);
          actions.appendChild(editBtn);
        }

        // Delete (own or admin)
        if (msg.username === state.username || state.adminToken) {
          const delBtn = document.createElement('button');
          delBtn.className = 'action-icon-btn';
          delBtn.title = 'Delete';
          delBtn.textContent = '🗑️';
          delBtn.onclick = () => deleteMessage(msg.id);
          actions.appendChild(delBtn);
        }

        card.appendChild(actions);
      }

      card.append(av, content);
      el.messagesContainer.appendChild(card);
    });

    if (isAtBottom) {
      el.messagesContainer.scrollTop = el.messagesContainer.scrollHeight;
    }
  }

  function formatMessageText(text) {
    if (!text) return '';
    let escaped = escapeHtml(text);
    // Highlight @Mentions
    escaped = escaped.replace(/@([a-zA-Z0-9_-]+)/g, '<span class="mention">@$1</span>');
    return escaped;
  }

  function scrollToMessage(id) {
    const target = document.getElementById(`msg-${id}`);
    if (target) {
      target.scrollIntoView({ behavior: 'smooth', block: 'center' });
      target.classList.add('highlight');
      setTimeout(() => target.classList.remove('highlight'), 2000);
    }
  }

  // Switch Rooms & DMs
  function switchRoom(roomName) {
    state.activeDmUser = null;
    state.currentRoom = roomName;
    state.unreadCounts[roomName] = 0;
    saveUnreads();

    el.currentChannelTitle.textContent = roomName;
    el.channelDesc.textContent = `Room ${roomName}`;
    el.btnSidebarToggle.classList.remove('active');
    el.sidebar.classList.remove('open');

    refreshChat();
  }

  function switchDm(targetUsername) {
    state.activeDmUser = targetUsername;
    state.unreadCounts['@' + targetUsername] = 0;
    saveUnreads();

    el.currentChannelTitle.textContent = `@${targetUsername}`;
    el.channelDesc.textContent = `Direct Message with ${targetUsername}`;
    el.sidebar.classList.remove('open');

    refreshChat();
  }

  // Reply Handling
  function setReplyTarget(msg) {
    state.replyTo = {
      id: msg.id,
      user: msg.username,
      text: msg.message.slice(0, 50) + (msg.message.length > 50 ? '...' : '')
    };
    el.replyToUser.textContent = state.replyTo.user;
    el.replyToText.textContent = state.replyTo.text;
    el.replyBar.classList.remove('hidden');
    el.messageInput.focus();
  }

  function cancelReply() {
    state.replyTo = null;
    el.replyBar.classList.add('hidden');
  }

  // Pinned Messages
  async function checkPinnedMessages() {
    try {
      const data = await api(`/api/pinned?room=${encodeURIComponent(state.currentRoom)}`);
      state.pinnedMessages = data.pinned || [];
      el.pinnedCount.textContent = state.pinnedMessages.length;

      if (state.pinnedMessages.length > 0) {
        const latest = state.pinnedMessages[state.pinnedMessages.length - 1];
        el.pinnedSummary.textContent = `${latest.username}: ${latest.message.slice(0, 70)}`;
        el.pinnedBar.classList.remove('hidden');
      } else {
        el.pinnedBar.classList.add('hidden');
      }
    } catch (e) {
      console.warn('Pinned fetch error:', e);
    }
  }

  // Reactions
  async function toggleReaction(messageId, emoji) {
    try {
      await postJson('/api/message/react', {
        messageId,
        emoji,
        username: state.username
      });
      refreshChat();
    } catch (e) {
      alert(e.message);
    }
  }

  // Edit & Delete
  async function editMessagePrompt(msg) {
    const newText = prompt('Edit your message:', msg.message);
    if (newText === null || newText.trim() === '' || newText === msg.message) return;
    try {
      await postJson('/api/message/edit', {
        messageId: msg.id,
        username: state.username,
        message: newText.trim()
      });
      refreshChat();
    } catch (e) {
      alert('Could not edit: ' + e.message);
    }
  }

  async function deleteMessage(messageId) {
    if (!confirm('Are you sure you want to delete this message?')) return;
    try {
      await postJson('/api/message/delete', {
        messageId,
        username: state.username,
        isAdmin: !!state.adminToken
      });
      refreshChat();
    } catch (e) {
      alert('Could not delete: ' + e.message);
    }
  }

  // Bookmarks
  function isBookmarked(id) {
    return state.bookmarks.some(b => b.id === id);
  }

  function toggleBookmark(msg) {
    if (isBookmarked(msg.id)) {
      state.bookmarks = state.bookmarks.filter(b => b.id !== msg.id);
    } else {
      state.bookmarks.unshift({
        id: msg.id,
        user: msg.username,
        message: msg.message,
        room: msg.room,
        time: msg.timestamp
      });
    }
    localStorage.setItem('labchat_bookmarks', JSON.stringify(state.bookmarks));
    renderMessages();
  }

  function renderBookmarks() {
    el.bookmarksList.innerHTML = '';
    if (state.bookmarks.length === 0) {
      el.bookmarksList.innerHTML = '<p class="empty-state">No saved messages yet. Hover any message and click ⭐ to save it.</p>';
      return;
    }
    state.bookmarks.forEach(b => {
      const card = document.createElement('div');
      card.className = 'search-match-card';
      card.innerHTML = `
        <div style="display:flex; justify-content:space-between; margin-bottom:4px;">
          <strong>@${escapeHtml(b.user)} (${escapeHtml(b.room)})</strong>
          <small class="subtext">${new Date(b.time).toLocaleTimeString()}</small>
        </div>
        <p>${escapeHtml(b.message)}</p>
      `;
      card.onclick = () => {
        el.bookmarksDialog.close();
        if (b.room && b.room.startsWith('#')) {
          switchRoom(b.room);
          setTimeout(() => scrollToMessage(b.id), 500);
        }
      };
      el.bookmarksList.appendChild(card);
    });
  }

  // Search (/search or Ctrl+K)
  function handleSearch(query) {
    if (!query || query.trim().length === 0) {
      el.searchResults.innerHTML = '<p class="search-empty">Type to search loaded messages in this room/chat.</p>';
      return;
    }
    const q = query.trim().toLowerCase();
    const matches = state.messages.filter(m => !m.deleted && (m.message.toLowerCase().includes(q) || m.username.toLowerCase().includes(q)));

    el.searchResults.innerHTML = '';
    if (matches.length === 0) {
      el.searchResults.innerHTML = `<p class="search-empty">No results matching "${escapeHtml(query)}".</p>`;
      return;
    }

    matches.forEach(m => {
      const card = document.createElement('div');
      card.className = 'search-match-card';

      const highLighted = escapeHtml(m.message).replace(new RegExp(`(${escapeRegex(query)})`, 'gi'), '<mark>$1</mark>');

      card.innerHTML = `
        <div style="display:flex; justify-content:space-between; margin-bottom:4px;">
          <strong>@${escapeHtml(m.username)}</strong>
          <small class="subtext">${new Date(m.timestamp).toLocaleTimeString()}</small>
        </div>
        <div>${highLighted}</div>
      `;
      card.onclick = () => {
        el.searchDialog.close();
        scrollToMessage(m.id);
      };
      el.searchResults.appendChild(card);
    });
  }

  // Polls
  async function loadPolls() {
    try {
      const data = await api('/api/polls');
      state.polls = data.polls || [];
      renderPolls();
    } catch (e) {
      console.warn('Polls load error:', e);
    }
  }

  function renderPolls() {
    el.pollsListContainer.innerHTML = '';
    if (state.polls.length === 0) {
      el.pollsListContainer.innerHTML = '<p class="empty-state">No polls created yet. Click "+ New Poll" to create one!</p>';
      return;
    }

    state.polls.forEach(poll => {
      const card = document.createElement('div');
      card.className = 'poll-card';

      const userVotedOpt = poll.userVotes && poll.userVotes[state.username];

      let optsHtml = '';
      poll.options.forEach((opt, idx) => {
        const pct = poll.totalVotes > 0 ? Math.round((opt.votes / poll.totalVotes) * 100) : 0;
        const isSelected = userVotedOpt === idx;
        optsHtml += `
          <div class="poll-option-row" data-idx="${idx}">
            <div class="poll-bar" style="width:${pct}%"></div>
            <span class="poll-opt-text">${isSelected ? '✅ ' : '○ '} ${escapeHtml(opt.text)}</span>
            <span class="poll-opt-votes">${opt.votes} (${pct}%)</span>
          </div>
        `;
      });

      card.innerHTML = `
        <div class="poll-question">${escapeHtml(poll.question)}</div>
        <div class="subtext" style="margin-bottom:8px;">Created by ${escapeHtml(poll.creator)} · ${poll.totalVotes} votes ${poll.closed ? '· [CLOSED]' : ''}</div>
        <div class="poll-options-list">${optsHtml}</div>
      `;

      if (!poll.closed) {
        card.querySelectorAll('.poll-option-row').forEach(row => {
          row.onclick = () => votePoll(poll.id, parseInt(row.getAttribute('data-idx')));
        });
      }

      el.pollsListContainer.appendChild(card);
    });
  }

  async function votePoll(pollId, optIdx) {
    try {
      await postJson('/api/poll/vote', {
        pollId,
        username: state.username,
        optionIndex: optIdx
      });
      loadPolls();
    } catch (e) {
      alert(e.message);
    }
  }

  // Lightbox
  function openLightbox(src, id, name) {
    el.lightboxImg.src = src;
    el.lightboxDownload.href = `/api/download?id=${encodeURIComponent(id)}`;
    el.lightboxDownload.download = name;
    el.imageModal.showModal();
  }

  // Throttle typing indicator
  function reportTyping() {
    const now = Date.now();
    if (now - state.lastTypingSent > 2500) {
      state.lastTypingSent = now;
      postJson('/api/typing', {
        username: state.username,
        context: state.currentRoom,
        targetUser: state.activeDmUser || ''
      }).catch(() => {});
    }
  }

  // File Upload
  async function handleFileUpload(file) {
    if (!file) return;
    if (file.size > 25 * 1024 * 1024) {
      alert('File exceeds 25 MB maximum upload limit.');
      return;
    }

    const formData = new FormData();
    formData.append('uploader', state.username);
    formData.append('file', file);

    el.btnAttach.textContent = '⏳';
    try {
      const res = await fetch('/api/upload', {
        method: 'POST',
        body: formData
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error?.message || 'Upload failed');

      state.attachedFile = {
        id: data.data.id,
        name: data.data.originalName,
        size: data.data.sizeBytes
      };

      el.attachmentName.textContent = state.attachedFile.name;
      el.attachmentSize.textContent = formatBytes(state.attachedFile.size);
      el.attachmentBar.classList.remove('hidden');
    } catch (e) {
      alert('Upload failed: ' + e.message);
    } finally {
      el.btnAttach.textContent = '📎';
      el.fileInput.value = '';
    }
  }

  // Form Submissions
  el.messageForm.onsubmit = async (e) => {
    e.preventDefault();
    const raw = el.messageInput.value;
    const text = raw.trim();

    if (!text && !state.attachedFile) return;

    // Check slash commands
    if (text.startsWith('/search')) {
      const query = text.replace('/search', '').trim();
      el.messageInput.value = '';
      el.searchDialog.showModal();
      el.searchInput.value = query;
      handleSearch(query);
      return;
    }

    if (text === '/code') {
      state.codeMode = !state.codeMode;
      el.codeSnippetBar.classList.toggle('hidden', !state.codeMode);
      el.messageInput.value = '';
      return;
    }

    el.btnSend.disabled = true;

    try {
      const payload = {
        username: state.username,
        message: text,
        codeLanguage: state.codeMode ? el.codeLanguageSelect.value : null,
        fileId: state.attachedFile ? state.attachedFile.id : '',
        replyToId: state.replyTo ? state.replyTo.id : ''
      };

      if (state.activeDmUser) {
        payload.recipient = state.activeDmUser;
        await postJson('/api/private/send', payload);
      } else {
        payload.room = state.currentRoom;
        await postJson('/api/send', payload);
      }

      el.messageInput.value = '';
      cancelReply();

      // Reset attachment & code mode
      state.attachedFile = null;
      el.attachmentBar.classList.add('hidden');
      if (state.codeMode) {
        state.codeMode = false;
        el.codeSnippetBar.classList.add('hidden');
      }

      await refreshChat();
    } catch (err) {
      alert('Failed to send: ' + err.message);
    } finally {
      el.btnSend.disabled = false;
      el.messageInput.focus();
    }
  };

  // Typing event & Enter-to-send
  el.messageInput.oninput = () => {
    reportTyping();
    // Auto-grow textarea height
    el.messageInput.style.height = 'auto';
    el.messageInput.style.height = Math.min(el.messageInput.scrollHeight, 140) + 'px';
  };

  el.messageInput.onkeydown = (e) => {
    if (e.key === 'Enter') {
      if (state.settings.enterSend && !e.shiftKey) {
        e.preventDefault();
        el.messageForm.requestSubmit();
      }
    }
  };

  // Join Flow
  el.joinForm.onsubmit = async (e) => {
    e.preventDefault();
    const candidate = el.usernameInput.value.trim();
    if (!candidate) return;

    try {
      await postJson('/api/join', { username: candidate });
      state.username = candidate;
      localStorage.setItem('labchat_username', candidate);
      el.joinDialog.close();
      updateUserProfileUI();
      refreshChat();
    } catch (err) {
      el.joinError.textContent = err.message;
      el.joinError.classList.remove('hidden');
    }
  };

  el.btnGuestJoin.onclick = async () => {
    try {
      const res = await postJson('/api/guest', {});
      state.username = res.username;
      localStorage.setItem('labchat_username', state.username);
      el.joinDialog.close();
      updateUserProfileUI();
      refreshChat();
    } catch (err) {
      el.joinError.textContent = err.message;
      el.joinError.classList.remove('hidden');
    }
  };

  // Create Room
  el.btnCreateRoom.onclick = () => el.createRoomDialog.showModal();
  el.createRoomForm.onsubmit = async (e) => {
    e.preventDefault();
    const raw = el.roomNameInput.value.trim();
    if (!raw) return;

    try {
      const res = await postJson('/api/rooms/create', {
        name: raw,
        creator: state.username
      });
      el.createRoomDialog.close();
      el.roomNameInput.value = '';
      switchRoom(res.room);
    } catch (err) {
      el.roomError.textContent = err.message;
      el.roomError.classList.remove('hidden');
    }
  };

  // Poll Creation
  el.btnOpenCreatePoll.onclick = () => el.createPollDialog.showModal();
  el.createPollForm.onsubmit = async (e) => {
    e.preventDefault();
    const q = el.pollQuestion.value.trim();
    const optInputs = document.querySelectorAll('.poll-option-input');
    const options = Array.from(optInputs).map(i => i.value.trim()).filter(v => v.length > 0);

    if (options.length < 2) {
      el.pollError.textContent = 'Please provide at least 2 options.';
      el.pollError.classList.remove('hidden');
      return;
    }

    try {
      await postJson('/api/poll/create', {
        question: q,
        creator: state.username,
        room: state.currentRoom,
        options
      });
      el.createPollDialog.close();
      el.pollQuestion.value = '';
      optInputs.forEach(i => i.value = '');
      loadPolls();
    } catch (err) {
      el.pollError.textContent = err.message;
      el.pollError.classList.remove('hidden');
    }
  };

  // Admin Actions
  el.btnAdminLogin.onclick = async () => {
    const token = el.adminTokenInput.value.trim();
    if (!token) return;
    try {
      await api('/api/admin/verify', {
        method: 'POST',
        headers: { 'Authorization': 'Bearer ' + token }
      });
      state.adminToken = token;
      sessionStorage.setItem('labchat_admin_token', token);
      document.getElementById('admin-auth-section').classList.add('hidden');
      el.adminToolsPanel.classList.remove('hidden');
    } catch (err) {
      el.adminAuthError.textContent = 'Invalid token.';
      el.adminAuthError.classList.remove('hidden');
    }
  };

  el.btnPostAnnouncement.onclick = async () => {
    const msg = el.adminAnnouncementInput.value.trim();
    if (!msg) return;
    try {
      await postJson('/api/admin/announcement', { message: msg });
      el.adminAnnouncementInput.value = '';
      refreshChat();
    } catch (err) {
      alert(err.message);
    }
  };

  el.btnClearAnnouncement.onclick = async () => {
    try {
      await postJson('/api/admin/announcement', { clear: true });
      refreshChat();
    } catch (err) {
      alert(err.message);
    }
  };

  el.btnAdminKick.onclick = async () => {
    const target = el.adminTargetUser.value.trim();
    if (!target) return;
    try {
      await postJson('/api/admin/kick', { targetUser: target });
      alert(`Kicked ${target}`);
      refreshChat();
    } catch (err) { alert(err.message); }
  };

  el.btnAdminMute.onclick = async () => {
    const target = el.adminTargetUser.value.trim();
    if (!target) return;
    try {
      await postJson('/api/admin/mute', { targetUser: target, muted: true });
      alert(`Muted ${target}`);
    } catch (err) { alert(err.message); }
  };

  el.btnAdminUnmute.onclick = async () => {
    const target = el.adminTargetUser.value.trim();
    if (!target) return;
    try {
      await postJson('/api/admin/mute', { targetUser: target, muted: false });
      alert(`Unmuted ${target}`);
    } catch (err) { alert(err.message); }
  };

  el.btnAdminBan.onclick = async () => {
    const target = el.adminTargetUser.value.trim();
    if (!target) return;
    try {
      await postJson('/api/admin/ban', { targetUser: target, minutes: 15 });
      alert(`Banned ${target} for 15 minutes.`);
    } catch (err) { alert(err.message); }
  };

  el.btnAdminClearRoom.onclick = async () => {
    if (!confirm(`Are you sure you want to clear all messages in ${state.currentRoom}?`)) return;
    try {
      await postJson('/api/admin/clear-room', { room: state.currentRoom });
      refreshChat();
    } catch (err) { alert(err.message); }
  };

  // Attachments & Code bar listeners
  el.btnAttach.onclick = () => el.fileInput.click();
  el.fileInput.onchange = (e) => {
    if (e.target.files && e.target.files[0]) {
      handleFileUpload(e.target.files[0]);
    }
  };
  el.btnCancelAttachment.onclick = () => {
    state.attachedFile = null;
    el.attachmentBar.classList.add('hidden');
  };

  el.btnCode.onclick = () => {
    state.codeMode = !state.codeMode;
    el.codeSnippetBar.classList.toggle('hidden', !state.codeMode);
  };
  el.btnCancelCode.onclick = () => {
    state.codeMode = false;
    el.codeSnippetBar.classList.add('hidden');
  };

  el.btnCancelReply.onclick = cancelReply;

  // Emojis Picker
  el.btnEmoji.onclick = (e) => {
    e.stopPropagation();
    el.emojiPopup.classList.toggle('hidden');
  };

  el.emojiGrid.onclick = (e) => {
    if (e.target.tagName === 'SPAN') {
      const emoji = e.target.textContent;
      el.messageInput.value += emoji;
      el.emojiPopup.classList.add('hidden');
      el.messageInput.focus();
    }
  };

  document.onclick = (e) => {
    if (!el.emojiPopup.contains(e.target) && e.target !== el.btnEmoji) {
      el.emojiPopup.classList.add('hidden');
    }
  };

  // Search Dialog
  el.btnSearch.onclick = () => {
    el.searchDialog.showModal();
    el.searchInput.focus();
  };
  el.searchInput.oninput = (e) => handleSearch(e.target.value);

  // Bookmarks Dialog
  el.btnBookmarks.onclick = () => {
    renderBookmarks();
    el.bookmarksDialog.showModal();
  };

  // Pinned Dialog
  el.btnPinned.onclick = () => el.pinnedDialog.showModal();
  el.btnViewPinned.onclick = () => el.pinnedDialog.showModal();

  // Polls Dialog
  el.btnPolls.onclick = () => {
    loadPolls();
    el.pollsDialog.showModal();
  };

  // Settings Dialog
  el.btnSettings.onclick = () => el.settingsDialog.showModal();
  el.settingTheme.onchange = (e) => {
    state.settings.theme = e.target.value;
    localStorage.setItem('labchat_theme', state.settings.theme);
    applySettings();
  };
  el.settingDensity.onchange = (e) => {
    state.settings.density = e.target.value;
    localStorage.setItem('labchat_density', state.settings.density);
    applySettings();
  };
  el.settingSound.onchange = (e) => {
    state.settings.sound = e.target.checked;
    localStorage.setItem('labchat_sound', state.settings.sound);
  };
  el.settingEnterSend.onchange = (e) => {
    state.settings.enterSend = e.target.checked;
    localStorage.setItem('labchat_enter_send', state.settings.enterSend);
  };

  el.btnEnableNotifications.onclick = async () => {
    if ('Notification' in window) {
      const res = await Notification.requestPermission();
      alert('Notification status: ' + res);
    } else {
      alert('Web Notifications not supported in this browser.');
    }
  };

  el.btnShowShortcuts.onclick = () => el.shortcutsDialog.showModal();

  // Admin Dialog
  el.btnAdmin.onclick = () => {
    el.adminDialog.showModal();
    if (state.adminToken) {
      document.getElementById('admin-auth-section').classList.add('hidden');
      el.adminToolsPanel.classList.remove('hidden');
    }
  };

  // Global Keyboard Shortcuts
  window.addEventListener('keydown', (e) => {
    if (e.ctrlKey && e.key.toLowerCase() === 'k') {
      e.preventDefault();
      el.searchDialog.showModal();
      el.searchInput.focus();
    } else if (e.key === 'Escape') {
      document.querySelectorAll('dialog[open]').forEach(d => d.close());
      cancelReply();
      el.emojiPopup.classList.add('hidden');
    }
  });

  // Close modals on clicking backdrop or .close-modal buttons
  document.querySelectorAll('.close-modal').forEach(btn => {
    btn.onclick = () => {
      const dialog = btn.closest('dialog');
      if (dialog) dialog.close();
    };
  });

  // Toggles for responsive UI
  el.btnSidebarToggle.onclick = () => el.sidebar.classList.toggle('open');
  el.btnUsersToggle.onclick = () => el.usersSidebar.classList.toggle('open');

  // Dismiss Announcement Banner
  el.btnDismissAnnouncement.onclick = () => el.announcementBanner.classList.add('hidden');

  // Helpers
  function saveUnreads() {
    localStorage.setItem('labchat_unreads', JSON.stringify(state.unreadCounts));
  }

  function escapeHtml(str) {
    if (!str) return '';
    return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }

  function escapeRegex(str) {
    return str.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  }

  function formatBytes(bytes) {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
  }

  // Initialization
  applySettings();
  updateUserProfileUI();

  if (!state.username) {
    el.joinDialog.showModal();
  } else {
    // Attempt re-join
    postJson('/api/join', { username: state.username })
      .then(() => refreshChat())
      .catch(() => {
        el.joinDialog.showModal();
      });
  }

  // Periodic polling
  setInterval(refreshChat, state.pollingInterval);
})();
