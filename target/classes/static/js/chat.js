/* ================================================
   AI智能客服 - JavaScript
   ================================================ */

let chatSessionId = null;
let isChatOpen = false;
let isSending = false;
let hasHistory = false;

function initChat() {
    const savedSessionId = localStorage.getItem('lastChatSessionId');
    
    if (savedSessionId) {
        chatSessionId = savedSessionId;
        hasHistory = true;
    } else {
        chatSessionId = generateSessionId();
        hasHistory = false;
    }
    
    localStorage.setItem('lastChatSessionId', chatSessionId);
    
    createChatUI();
    loadQuickQuestions();
    
    if (hasHistory) {
        loadChatHistory();
    }
}

function generateSessionId() {
    return 'chat_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9);
}

function createChatUI() {
    const chatFloatBtn = document.createElement('button');
    chatFloatBtn.className = 'chat-float-btn';
    chatFloatBtn.id = 'chatFloatBtn';
    chatFloatBtn.innerHTML = '💬';
    chatFloatBtn.onclick = toggleChat;
    document.body.appendChild(chatFloatBtn);
    
    const chatWindow = document.createElement('div');
    chatWindow.className = 'chat-window';
    chatWindow.id = 'chatWindow';
    chatWindow.innerHTML = `
        <div class="chat-header">
            <div class="chat-header-left">
                <div class="chat-avatar">🤖</div>
                <div class="chat-title">
                    <h3>智能客服</h3>
                    <div class="chat-status">
                        <span class="status-dot"></span>
                        <span>在线</span>
                    </div>
                </div>
            </div>
            <div class="chat-header-actions">
                <button class="chat-header-btn" onclick="startNewChat()" title="新建对话">➕</button>
                <button class="chat-header-btn" onclick="clearChatHistory()" title="清空聊天">🗑️</button>
                <button class="chat-header-btn" onclick="toggleChat()" title="关闭">✕</button>
            </div>
        </div>
        <div class="chat-messages" id="chatMessages">
            <div class="chat-welcome" id="chatWelcome">
                <div class="chat-welcome-icon">👋</div>
                <h4>您好！我是智能客服</h4>
                <p>很高兴为您服务，请问有什么可以帮您的？</p>
                <div class="quick-questions" id="quickQuestions"></div>
                <div class="chat-options" id="chatOptions" style="display: none;">
                    <button class="btn btn-primary" onclick="continueLastChat()">继续上次对话</button>
                    <button class="btn btn-outline" onclick="startNewChat()">新建对话</button>
                </div>
            </div>
        </div>
        <div class="chat-input-area">
            <div class="chat-input-actions">
                <button class="chat-action-btn" onclick="transferToHuman()" title="转人工客服">👨‍💼 转人工</button>
            </div>
            <div class="chat-input-wrapper">
                <textarea 
                    class="chat-input" 
                    id="chatInput" 
                    placeholder="请输入您的问题..." 
                    rows="1"
                    onkeydown="handleChatKeydown(event)"
                    oninput="autoResizeTextarea(this)"
                ></textarea>
                <button class="chat-send-btn" id="chatSendBtn" onclick="sendChatMessage()">➤</button>
            </div>
            <div class="chat-input-tip">按 Enter 发送，Shift + Enter 换行</div>
        </div>
    `;
    document.body.appendChild(chatWindow);
}

function loadQuickQuestions() {
    const quickQuestions = [
        '如何购票？',
        '票能退吗？',
        '演唱会有哪些场次？',
        '忘记密码怎么办？'
    ];
    
    const container = document.getElementById('quickQuestions');
    if (!container) return;
    
    container.innerHTML = quickQuestions.map(q => 
        `<button class="quick-question-btn" onclick="sendQuickQuestion('${q}')">${q}</button>`
    ).join('');
}

async function loadChatHistory() {
    try {
        const response = await api.chat.getHistory(chatSessionId);
        if (response.code === 200 && response.data && response.data.length > 0) {
            showChatOptions();
        } else {
            hasHistory = false;
        }
    } catch (error) {
        console.error('加载聊天记录失败:', error);
        hasHistory = false;
    }
}

function showChatOptions() {
    const optionsDiv = document.getElementById('chatOptions');
    if (optionsDiv) {
        optionsDiv.style.display = 'block';
    }
}

async function continueLastChat() {
    const optionsDiv = document.getElementById('chatOptions');
    if (optionsDiv) {
        optionsDiv.style.display = 'none';
    }
    
    try {
        const response = await api.chat.getHistory(chatSessionId);
        if (response.code === 200 && response.data) {
            renderChatHistory(response.data);
        }
    } catch (error) {
        console.error('加载聊天记录失败:', error);
    }
}

function renderChatHistory(historyList) {
    const messagesContainer = document.getElementById('chatMessages');
    const welcomeDiv = document.getElementById('chatWelcome');
    if (welcomeDiv) {
        welcomeDiv.style.display = 'none';
    }
    
    historyList.forEach(history => {
        addUserMessage(history.question, false);
        addAiMessage({
            answer: history.answer,
            isRichText: false,
            needTransfer: history.isTransfer === 1
        }, false);
    });
    
    scrollToBottom();
}

function startNewChat() {
    chatSessionId = generateSessionId();
    localStorage.setItem('lastChatSessionId', chatSessionId);
    hasHistory = false;
    
    const messagesContainer = document.getElementById('chatMessages');
    const welcomeDiv = document.getElementById('chatWelcome');
    const optionsDiv = document.getElementById('chatOptions');
    
    messagesContainer.innerHTML = '';
    messagesContainer.appendChild(welcomeDiv);
    
    if (optionsDiv) {
        optionsDiv.style.display = 'none';
    }
    
    welcomeDiv.style.display = 'block';
    loadQuickQuestions();
}

function toggleChat() {
    const chatWindow = document.getElementById('chatWindow');
    const chatFloatBtn = document.getElementById('chatFloatBtn');
    
    isChatOpen = !isChatOpen;
    
    if (isChatOpen) {
        chatWindow.classList.add('active');
        chatFloatBtn.innerHTML = '✕';
        setTimeout(() => {
            document.getElementById('chatInput').focus();
        }, 100);
    } else {
        chatWindow.classList.remove('active');
        chatFloatBtn.innerHTML = '💬';
    }
}

function sendQuickQuestion(question) {
    document.getElementById('chatInput').value = question;
    sendChatMessage();
}

function handleChatKeydown(event) {
    if (event.key === 'Enter' && !event.shiftKey) {
        event.preventDefault();
        sendChatMessage();
    }
}

function autoResizeTextarea(textarea) {
    textarea.style.height = 'auto';
    textarea.style.height = Math.min(textarea.scrollHeight, 100) + 'px';
}

async function sendChatMessage() {
    if (isSending) return;
    
    const input = document.getElementById('chatInput');
    const question = input.value.trim();
    
    if (!question) return;
    
    isSending = true;
    const sendBtn = document.getElementById('chatSendBtn');
    sendBtn.disabled = true;
    
    const welcomeDiv = document.getElementById('chatWelcome');
    if (welcomeDiv) {
        welcomeDiv.style.display = 'none';
    }
    
    addUserMessage(question);
    input.value = '';
    autoResizeTextarea(input);
    
    showTypingIndicator();
    
    try {
        const userId = localStorage.getItem('userId') ? parseInt(localStorage.getItem('userId')) : null;
        const response = await api.chat.sendMessage(chatSessionId, userId, question);
        
        removeTypingIndicator();
        
        if (response.code === 200 && response.data) {
            addAiMessage(response.data);
        } else {
            addAiMessage({ answer: '抱歉，发生了一些问题，请稍后再试。' });
        }
    } catch (error) {
        console.error('发送消息失败:', error);
        removeTypingIndicator();
        addAiMessage({ answer: '抱歉，网络连接失败，请稍后再试。' });
    } finally {
        isSending = false;
        sendBtn.disabled = false;
    }
}

function addUserMessage(question, scroll = true) {
    const messagesContainer = document.getElementById('chatMessages');
    const messageDiv = document.createElement('div');
    messageDiv.className = 'chat-message user';
    
    const time = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
    
    messageDiv.innerHTML = `
        <div class="message-avatar">我</div>
        <div>
            <div class="message-content">${escapeHtml(question)}</div>
            <div class="message-time">${time}</div>
        </div>
    `;
    
    messagesContainer.appendChild(messageDiv);
    if (scroll) {
        scrollToBottom();
    }
}

function parseMarkdown(text) {
    let result = '';
    let i = 0;
    
    while (i < text.length) {
        if (text.substr(i, 2) === '**') {
            let end = text.indexOf('**', i + 2);
            if (end !== -1) {
                result += '<strong>' + escapeHtml(text.substring(i + 2, end)) + '</strong>';
                i = end + 2;
            } else {
                result += escapeHtml(text.charAt(i));
                i++;
            }
        } else if (text.charAt(i) === '*') {
            let end = text.indexOf('*', i + 1);
            if (end !== -1) {
                result += '<em>' + escapeHtml(text.substring(i + 1, end)) + '</em>';
                i = end + 1;
            } else {
                result += escapeHtml(text.charAt(i));
                i++;
            }
        } else if (text.charAt(i) === '`') {
            let end = text.indexOf('`', i + 1);
            if (end !== -1) {
                result += '<code>' + escapeHtml(text.substring(i + 1, end)) + '</code>';
                i = end + 1;
            } else {
                result += escapeHtml(text.charAt(i));
                i++;
            }
        } else if (text.charAt(i) === '\n') {
            result += '<br>';
            i++;
        } else {
            result += escapeHtml(text.charAt(i));
            i++;
        }
    }
    
    return result;
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function addAiMessage(data, scroll = true) {
    const messagesContainer = document.getElementById('chatMessages');
    const messageDiv = document.createElement('div');
    messageDiv.className = 'chat-message ai';
    
    const time = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
    
    let content;
    if (data.isRichText) {
        content = data.answer;
    } else {
        content = parseMarkdown(data.answer);
    }
    
    let transferHtml = '';
    if (data.needTransfer) {
        transferHtml = `
            <div class="transfer-hint">
                <p>这个问题我可能无法完全解答，需要转接人工客服吗？</p>
                <button class="transfer-btn" onclick="transferToHuman()">转人工客服</button>
            </div>
        `;
    }
    
    messageDiv.innerHTML = `
        <div class="message-avatar">🤖</div>
        <div>
            <div class="message-content">${content}</div>
            <div class="message-time">${time}</div>
            ${transferHtml}
        </div>
    `;
    
    messagesContainer.appendChild(messageDiv);
    if (scroll) {
        scrollToBottom();
    }
}

function showTypingIndicator() {
    const messagesContainer = document.getElementById('chatMessages');
    const indicatorDiv = document.createElement('div');
    indicatorDiv.className = 'chat-message ai';
    indicatorDiv.id = 'typingIndicator';
    
    indicatorDiv.innerHTML = `
        <div class="message-avatar">🤖</div>
        <div class="message-content">
            <div class="typing-indicator">
                <div class="typing-dot"></div>
                <div class="typing-dot"></div>
                <div class="typing-dot"></div>
            </div>
        </div>
    `;
    
    messagesContainer.appendChild(indicatorDiv);
    scrollToBottom();
}

function removeTypingIndicator() {
    const indicator = document.getElementById('typingIndicator');
    if (indicator) {
        indicator.remove();
    }
}

function scrollToBottom() {
    const messagesContainer = document.getElementById('chatMessages');
    messagesContainer.scrollTop = messagesContainer.scrollHeight;
}

function clearChatHistory() {
    const messagesContainer = document.getElementById('chatMessages');
    messagesContainer.innerHTML = `
        <div class="chat-welcome" id="chatWelcome">
            <div class="chat-welcome-icon">👋</div>
            <h4>您好！我是智能客服</h4>
            <p>很高兴为您服务，请问有什么可以帮您的？</p>
            <div class="quick-questions" id="quickQuestions"></div>
            <div class="chat-options" id="chatOptions" style="display: none;"></div>
        </div>
    `;
    loadQuickQuestions();
    
    chatSessionId = generateSessionId();
    localStorage.setItem('lastChatSessionId', chatSessionId);
    hasHistory = false;
}

async function transferToHuman() {
    const lastQuestion = getLastUserQuestion();
    const userId = localStorage.getItem('userId') ? parseInt(localStorage.getItem('userId')) : null;
    const userName = localStorage.getItem('nickname') || '';
    const userPhone = localStorage.getItem('phone') || '';

    if (!confirm('确定要转接人工客服吗？')) {
        return;
    }

    try {
        const response = await api.chat.requestTransfer(chatSessionId, userId, userName, userPhone, lastQuestion);
        if (response.code === 200) {
            alert('转接请求已提交！人工客服将尽快联系您。');
        } else {
            alert('转接失败，请稍后再试。');
        }
    } catch (error) {
        console.error('转接失败:', error);
        alert('转接失败，请稍后再试。');
    }
}

function getLastUserQuestion() {
    const messagesContainer = document.getElementById('chatMessages');
    const userMessages = messagesContainer.querySelectorAll('.chat-message.user .message-content');
    if (userMessages.length > 0) {
        return userMessages[userMessages.length - 1].textContent;
    }
    return '';
}

document.addEventListener('DOMContentLoaded', initChat);
