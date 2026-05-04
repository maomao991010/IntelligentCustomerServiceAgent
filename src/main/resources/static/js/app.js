let currentUser = null;
let currentSession = null;
let selectedSeats = [];
let loadSeatMapData = null;
let currentLoginType = 'phone';
let currentRegisterType = 'phone';
let loginCodeId = '';
let registerCodeId = '';

// 缓存配置
const CACHE_KEY = 'sessions_cache';
const CACHE_EXPIRE_TIME = 3600000; // 缓存过期时间，1小时

function init() {
    checkLoginStatus();
    setupNavigation();
    loadHomeSessions();
    
    document.getElementById('loginEmail').setAttribute('disabled', 'disabled');
    document.getElementById('loginPhone').removeAttribute('disabled');
    
    document.getElementById('registerPhoneEmail').setAttribute('disabled', 'disabled');
    document.getElementById('registerEmail').setAttribute('disabled', 'disabled');
    document.getElementById('emailCode').setAttribute('disabled', 'disabled');
    document.getElementById('registerPhone').removeAttribute('disabled');
}

// 获取缓存数据
function getCachedData() {
    try {
        const cached = localStorage.getItem(CACHE_KEY);
        if (!cached) return null;
        
        const parsed = JSON.parse(cached);
        const now = Date.now();
        
        // 检查缓存是否过期
        if (now - parsed.timestamp > CACHE_EXPIRE_TIME) {
            localStorage.removeItem(CACHE_KEY);
            return null;
        }
        
        return parsed.data;
    } catch (error) {
        console.error('获取缓存失败:', error);
        return null;
    }
}

// 设置缓存数据
function setCachedData(data) {
    try {
        const cache = {
            data: data,
            timestamp: Date.now()
        };
        localStorage.setItem(CACHE_KEY, JSON.stringify(cache));
    } catch (error) {
        console.error('设置缓存失败:', error);
    }
}

// 缓存图片
async function cacheImages(sessions) {
    for (const session of sessions) {
        if (session.imagePath) {
            try {
                // 检查图片是否已缓存
                const imageCacheKey = `image_${session.id}`;
                const cachedImage = localStorage.getItem(imageCacheKey);
                
                if (cachedImage) {
                    // 使用缓存的图片
                    session.imageData = cachedImage;
                } else {
                    // 下载并缓存图片
                    const imageData = await fetchImageAsBase64(session.imagePath);
                    if (imageData) {
                        session.imageData = imageData;
                        localStorage.setItem(imageCacheKey, imageData);
                    }
                }
            } catch (error) {
                console.error('缓存图片失败:', error);
            }
        }
    }
    return sessions;
}

// 将图片转换为Base64
function fetchImageAsBase64(url) {
    return new Promise((resolve, reject) => {
        const img = new Image();
        img.crossOrigin = 'anonymous';
        img.onload = function() {
            const canvas = document.createElement('canvas');
            canvas.width = img.width;
            canvas.height = img.height;
            const ctx = canvas.getContext('2d');
            ctx.drawImage(img, 0, 0);
            try {
                const base64 = canvas.toDataURL('image/jpeg');
                resolve(base64);
            } catch (error) {
                reject(error);
            }
        };
        img.onerror = function() {
            reject(new Error('图片加载失败'));
        };
        img.src = url;
    });
}

// 加载首页演出数据
async function loadHomeSessions() {
    const homeSessionsGrid = document.getElementById('homeSessionsGrid');
    if (!homeSessionsGrid) return;

    homeSessionsGrid.innerHTML = '<div class="loading">加载中...</div>';

    try {
        // 检查本地缓存
        const cachedData = getCachedData();
        if (cachedData) {
            renderHomeSessions(cachedData);
            return;
        }

        // 从服务器获取数据
        const response = await api.sessions.getList();
        if (response.code === 200 && response.data) {
            // 处理图片缓存
            const sessionsWithCachedImages = await cacheImages(response.data);
            // 缓存数据
            setCachedData(sessionsWithCachedImages);
            renderHomeSessions(sessionsWithCachedImages);
        } else {
            homeSessionsGrid.innerHTML = '<div class="empty-state">暂无演出</div>';
        }
    } catch (error) {
        console.error('加载演出失败:', error);
        if (error && error.status === 401) {
            homeSessionsGrid.innerHTML = '<div class="empty-state"><div class="empty-state-icon">🔒</div><p>请先登录</p><button class="btn btn-primary" onclick="showLoginModal()">立即登录</button></div>';
            return;
        }
        homeSessionsGrid.innerHTML = '<div class="empty-state">加载失败</div>';
    }
}

// 渲染首页演出数据
function renderHomeSessions(sessions) {
    const homeSessionsGrid = document.getElementById('homeSessionsGrid');
    if (!homeSessionsGrid) return;

    const sessionsHtml = sessions.slice(0, 6).map(session => `
        <div class="session-card" onclick="selectSession(${session.id}, '${session.activityName}')">
            <div class="session-image">
                ${session.imageData ? `<img src="${session.imageData}" alt="${session.activityName}">` : session.imagePath ? `<img src="${session.imagePath}" alt="${session.activityName}">` : '🎤'}
            </div>
            <div class="session-info">
                <div class="session-title">${session.activityName}</div>
                <div class="session-venue">${session.venue}</div>
                <div class="session-time">${session.date} ${session.time}</div>
                <div class="session-price">${session.minPrice}<span>起</span></div>
            </div>
        </div>
    `).join('');
    homeSessionsGrid.innerHTML = sessionsHtml;
}

function checkLoginStatus() {
    const token = localStorage.getItem('token');
    const userStr = localStorage.getItem('user');
    if (token && userStr) {
        try {
            currentUser = JSON.parse(userStr);
            updateAuthUI(true);
        } catch (e) {
            localStorage.removeItem('token');
            localStorage.removeItem('user');
        }
    }
}

function updateAuthUI(isLoggedIn) {
    const navAuth = document.getElementById('navAuth');
    const navUser = document.getElementById('navUser');
    const userName = document.getElementById('userName');
    if (isLoggedIn && currentUser) {
        navAuth.classList.add('hidden');
        navUser.classList.remove('hidden');
        userName.textContent = currentUser.nickname || currentUser.phone;
    } else {
        navAuth.classList.remove('hidden');
        navUser.classList.add('hidden');
    }
}

function setupNavigation() {
    const navLinks = document.querySelectorAll('.nav-link');
    navLinks.forEach(function(link) {
        link.addEventListener('click', function(e) {
            var page = e.target.dataset.page;
            if (!page) return;
            e.preventDefault();
            navigateTo(page);
        });
    });
}

function navigateTo(page) {
    const pages = document.querySelectorAll('.page');
    pages.forEach(function(p) {
        p.classList.remove('active');
    });
    const targetPage = document.getElementById(page + 'Page');
    if (targetPage) {
        targetPage.classList.add('active');
    }
    const navLinks = document.querySelectorAll('.nav-link');
    navLinks.forEach(function(link) {
        link.classList.remove('active');
        if (link.dataset.page === page) {
            link.classList.add('active');
        }
    });
    if (page === 'sessions') {
        loadSessions();
    } else if (page === 'orders') {
        if (!currentUser) {
            showToast('请先登录', 'warning');
            showLoginModal();
            return;
        }
        loadOrders();
    }
    window.scrollTo(0, 0);
}

function showLoginModal() {
    document.getElementById('loginModal').classList.add('active');
    currentLoginType = 'phone';
    switchLoginTab('phone', null);
    refreshVerificationCode();
}

function closeLoginModal() {
    document.getElementById('loginModal').classList.remove('active');
}

function showRegisterModal() {
    document.getElementById('registerModal').classList.add('active');
    currentRegisterType = 'phone';
    switchRegisterTab('phone');
    refreshRegisterVerificationCode();
}

function closeRegisterModal() {
    document.getElementById('registerModal').classList.remove('active');
}

function switchToRegister() {
    closeLoginModal();
    showRegisterModal();
}

function switchToLogin() {
    closeRegisterModal();
    showLoginModal();
}

async function refreshVerificationCode() {
    try {
        const response = await api.auth.getVerificationCode();
        if (response.code === 200 && response.data) {
            document.getElementById('verificationCodeImg').src = response.data.imageUrl;
            loginCodeId = response.data.codeId;
        }
    } catch (error) {
        console.error('获取验证码失败:', error);
    }
}

async function refreshRegisterVerificationCode() {
    try {
        const response = await api.auth.getVerificationCode();
        if (response.code === 200 && response.data) {
            document.getElementById('registerVerificationCodeImg').src = response.data.imageUrl;
            registerCodeId = response.data.codeId;
        }
    } catch (error) {
        console.error('获取验证码失败:', error);
    }
}

function switchLoginTab(type, event) {
    currentLoginType = type;
    const tabs = document.querySelectorAll('.login-tab');
    tabs.forEach(tab => tab.classList.remove('active'));
    if (event && event.target) {
        event.target.classList.add('active');
    }
    
    if (type === 'phone') {
        document.getElementById('phoneLoginFields').style.display = 'block';
        document.getElementById('emailLoginFields').style.display = 'none';
        document.getElementById('loginPhone').setAttribute('required', 'required');
        document.getElementById('loginPhone').removeAttribute('disabled');
        document.getElementById('loginEmail').removeAttribute('required');
        document.getElementById('loginEmail').setAttribute('disabled', 'disabled');
    } else {
        document.getElementById('phoneLoginFields').style.display = 'none';
        document.getElementById('emailLoginFields').style.display = 'block';
        document.getElementById('loginPhone').removeAttribute('required');
        document.getElementById('loginPhone').setAttribute('disabled', 'disabled');
        document.getElementById('loginEmail').setAttribute('required', 'required');
        document.getElementById('loginEmail').removeAttribute('disabled');
    }
}

async function handleLogin(e) {
    e.preventDefault();
    
    // 手动验证表单，避免隐藏字段的验证错误
    let isValid = true;
    let errorMessage = '';
    
    if (currentLoginType === 'phone') {
        const phone = document.getElementById('loginPhone').value;
        if (!phone) {
            isValid = false;
            errorMessage = '请输入手机号';
        } else if (!/^1[3-9]\d{9}$/.test(phone)) {
            isValid = false;
            errorMessage = '请输入正确的手机号';
        }
    } else {
        const email = document.getElementById('loginEmail').value;
        if (!email) {
            isValid = false;
            errorMessage = '请输入邮箱';
        } else if (!/^[a-zA-Z0-9_-]+@[a-zA-Z0-9_-]+(\.[a-zA-Z0-9_-]+)+$/.test(email)) {
            isValid = false;
            errorMessage = '请输入正确的邮箱';
        }
    }
    
    const password = document.getElementById('loginPassword').value;
    if (!password) {
        isValid = false;
        errorMessage = '请输入密码';
    }
    
    const verificationCode = document.getElementById('loginCode').value;
    if (!verificationCode) {
        isValid = false;
        errorMessage = '请输入验证码';
    }
    
    if (!isValid) {
        showToast(errorMessage, 'error');
        return;
    }
    
    try {
        showToast('登录中...', 'success');
        
        // 获取SM2公钥并加密密码
        let encryptedPassword = null;
        try {
            const keyResponse = await api.auth.getSM2PublicKey();
            if (keyResponse.code === 200 && keyResponse.data) {
                // 使用本地SM2实现
                encryptedPassword = sm2Encrypt(password, keyResponse.data);
                console.log('SM2公钥获取成功，密码已加密');
            }
        } catch (error) {
            console.error('获取SM2公钥失败，使用明文密码:', error);
        }
        
        let response;
        
        if (currentLoginType === 'phone') {
            const phone = document.getElementById('loginPhone').value;
            response = await api.auth.login(phone, null, password, verificationCode, loginCodeId, encryptedPassword);
        } else {
            const email = document.getElementById('loginEmail').value;
            response = await api.auth.login(null, email, password, verificationCode, loginCodeId, encryptedPassword);
        }
        
        if (response.code === 200) {
            localStorage.setItem('token', response.data.token);
            localStorage.setItem('user', JSON.stringify(response.data.userInfo));
            currentUser = response.data.userInfo;
            updateAuthUI(true);
            closeLoginModal();
            showToast('登录成功', 'success');
            document.getElementById('loginForm').reset();
        } else {
            showToast(response.message || '登录失败', 'error');
            refreshVerificationCode();
        }
    } catch (error) {
        showToast(error.message || '登录失败', 'error');
        refreshVerificationCode();
    }
}

function switchRegisterTab(type, event) {
    currentRegisterType = type;
    const tabs = document.querySelectorAll('.register-tab');
    tabs.forEach(tab => tab.classList.remove('active'));
    if (event && event.target) {
        event.target.classList.add('active');
    }
    
    if (type === 'phone') {
        document.getElementById('phoneRegisterFields').style.display = 'block';
        document.getElementById('emailRegisterFields').style.display = 'none';
        document.getElementById('registerPhone').setAttribute('required', 'required');
        document.getElementById('registerPhone').removeAttribute('disabled');
        document.getElementById('registerPhoneEmail').removeAttribute('required');
        document.getElementById('registerPhoneEmail').setAttribute('disabled', 'disabled');
        document.getElementById('registerEmail').removeAttribute('required');
        document.getElementById('registerEmail').setAttribute('disabled', 'disabled');
    } else {
        document.getElementById('phoneRegisterFields').style.display = 'none';
        document.getElementById('emailRegisterFields').style.display = 'block';
        document.getElementById('registerPhone').removeAttribute('required');
        document.getElementById('registerPhone').setAttribute('disabled', 'disabled');
        document.getElementById('registerPhoneEmail').setAttribute('required', 'required');
        document.getElementById('registerPhoneEmail').removeAttribute('disabled');
        document.getElementById('registerEmail').setAttribute('required', 'required');
        document.getElementById('registerEmail').removeAttribute('disabled');
    }
}

async function sendEmailVerificationCode() {
    const email = document.getElementById('registerEmail').value;
    if (!email) {
        showToast('请输入邮箱地址', 'error');
        return;
    }
    
    const emailRegex = /^[a-zA-Z0-9_-]+@[a-zA-Z0-9_-]+(\.[a-zA-Z0-9_-]+)+$/;
    if (!emailRegex.test(email)) {
        showToast('邮箱格式不正确', 'error');
        return;
    }
    
    const btn = document.getElementById('sendEmailCodeBtn');
    btn.disabled = true;
    let countdown = 60;
    
    try {
        showToast('发送中...', 'info');
        const response = await api.auth.sendEmailCode(email);
        if (response.code === 200) {
            showToast('验证码已发送到您的邮箱', 'success');
            const timer = setInterval(() => {
                btn.textContent = countdown + '秒后重发';
                countdown--;
                if (countdown < 0) {
                    clearInterval(timer);
                    btn.textContent = '发送验证码';
                    btn.disabled = false;
                }
            }, 1000);
        } else {
            showToast(response.message || '发送失败', 'error');
            btn.disabled = false;
        }
    } catch (error) {
        showToast(error.message || '发送失败', 'error');
        btn.disabled = false;
    }
}

async function handleRegister(e) {
    e.preventDefault();
    const password = document.getElementById('registerPassword').value;
    const nickname = document.getElementById('registerNickname').value;
    
    try {
        showToast('注册中...', 'success');
        let response;
        
        if (currentRegisterType === 'phone') {
            const phone = document.getElementById('registerPhone').value;
            const verificationCode = document.getElementById('registerCode').value;
            response = await api.auth.register(phone, password, nickname, verificationCode, registerCodeId);
        } else {
            const phone = document.getElementById('registerPhoneEmail').value;
            const email = document.getElementById('registerEmail').value;
            const emailVerificationCode = document.getElementById('emailCode').value;
            response = await api.auth.registerWithEmail(phone, email, password, nickname, emailVerificationCode);
        }
        
        if (response.code === 200) {
            showToast('注册成功，请登录', 'success');
            closeRegisterModal();
            document.getElementById('registerForm').reset();
            setTimeout(showLoginModal, 500);
        } else {
            showToast(response.message || '注册失败', 'error');
            if (currentRegisterType === 'phone') {
                refreshRegisterVerificationCode();
            }
        }
    } catch (error) {
        showToast(error.message || '注册失败', 'error');
        if (currentRegisterType === 'phone') {
            refreshRegisterVerificationCode();
        }
    }
}

async function logout() {
    const token = localStorage.getItem('token');
    if (token) {
        try {
            await api.auth.logout(token);
        } catch (error) {
            console.error('登出失败:', error);
        }
    }
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    currentUser = null;
    updateAuthUI(false);
    navigateTo('home');
    showToast('已退出登录', 'success');
}

let currentSessionPage = 1;
let currentSessionKeyword = '';
const SESSION_PAGE_SIZE = 10;

async function loadSessions(pageNum = 1, keyword = '') {
    currentSessionPage = pageNum;
    currentSessionKeyword = keyword;
    
    const list = document.getElementById('sessionsList');
    const pagination = document.getElementById('sessionsPagination');
    list.innerHTML = '<div class="loading">加载中...</div>';
    pagination.style.display = 'none';
    
    try {
        const response = await api.sessions.getPage(pageNum, SESSION_PAGE_SIZE, keyword);
        if (response.code === 200 && response.data && response.data.records && response.data.records.length > 0) {
            const sessionsWithCachedImages = await cacheImages(response.data.records);
            renderSessions(sessionsWithCachedImages);
            renderSessionPagination(response.data);
        } else {
            list.innerHTML = '<div class="empty-state"><div class="empty-state-icon">🎭</div><p>暂无演出场次</p></div>';
            pagination.style.display = 'none';
        }
    } catch (error) {
        if (error && error.status === 401) {
            list.innerHTML = '<div class="empty-state"><div class="empty-state-icon">🔒</div><p>请先登录</p><button class="btn btn-primary" onclick="showLoginModal()">立即登录</button></div>';
            return;
        }
        list.innerHTML = '<div class="empty-state"><div class="empty-state-icon">❌</div><p>加载失败，请稍后重试</p><button class="btn btn-primary" onclick="loadSessions(' + pageNum + ', \'' + keyword.replace(/'/g, "\\'") + '\')">重新加载</button></div>';
    }
}

function renderSessionPagination(pageData) {
    const pagination = document.getElementById('sessionsPagination');
    pagination.style.display = 'flex';
    
    let html = '';
    
    html += '<button class="pagination-btn" onclick="loadSessions(1, \'' + currentSessionKeyword.replace(/'/g, "\\'") + '\')" ' + (pageData.pageNum <= 1 ? 'disabled' : '') + '>首页</button>';
    html += '<button class="pagination-btn" onclick="loadSessions(' + (pageData.pageNum - 1) + ', \'' + currentSessionKeyword.replace(/'/g, "\\'") + '\')" ' + (pageData.pageNum <= 1 ? 'disabled' : '') + '>上一页</button>';
    
    const startPage = Math.max(1, pageData.pageNum - 2);
    const endPage = Math.min(pageData.pages, pageData.pageNum + 2);
    
    for (let i = startPage; i <= endPage; i++) {
        html += '<button class="pagination-btn ' + (i === pageData.pageNum ? 'active' : '') + '" onclick="loadSessions(' + i + ', \'' + currentSessionKeyword.replace(/'/g, "\\'") + '\')">' + i + '</button>';
    }
    
    html += '<button class="pagination-btn" onclick="loadSessions(' + (pageData.pageNum + 1) + ', \'' + currentSessionKeyword.replace(/'/g, "\\'") + '\')" ' + (pageData.pageNum >= pageData.pages ? 'disabled' : '') + '>下一页</button>';
    html += '<button class="pagination-btn" onclick="loadSessions(' + pageData.pages + ', \'' + currentSessionKeyword.replace(/'/g, "\\'") + '\')" ' + (pageData.pageNum >= pageData.pages ? 'disabled' : '') + '>末页</button>';
    html += '<span class="pagination-info">共 ' + pageData.total + ' 条，第 ' + pageData.pageNum + '/' + pageData.pages + ' 页</span>';
    
    pagination.innerHTML = html;
}

function searchSessions() {
    const keyword = document.getElementById('sessionSearchKeyword').value.trim();
    loadSessions(1, keyword);
}

function resetSessionSearch() {
    document.getElementById('sessionSearchKeyword').value = '';
    loadSessions(1, '');
}

function handleSessionSearchKeyup(event) {
    if (event.key === 'Enter') {
        searchSessions();
    }
}

function renderSessions(sessions) {
    const list = document.getElementById('sessionsList');
    list.innerHTML = sessions.map(function(session) {
        return `
            <div class="session-item" onclick="selectSession(${session.id}, '${session.activityName}')">
                <div class="session-item-image">
                    ${session.imageData ? `<img src="${session.imageData}" alt="${session.activityName}">` : session.imagePath ? `<img src="${session.imagePath}" alt="${session.activityName}">` : '🎤'}
                </div>
                <div class="session-item-info">
                    <div class="session-item-title">
                        <span class="session-item-tag">演唱会</span>
                        ${session.activityName}
                    </div>
                    <div class="session-item-artists">
                        艺人：${session.artist || '未知'}
                    </div>
                    <div class="session-item-venue">
                        📍 ${session.venue}
                    </div>
                    <div class="session-item-time">
                        🎯 ${session.date} ${session.time}
                    </div>
                    <div class="session-item-price">
                        ${session.minPrice}${session.maxPrice > session.minPrice ? '-' + session.maxPrice : ''}<span>元</span>
                        <span class="session-item-status">售票中</span>
                    </div>
                </div>
            </div>
        `;
    }).join('');
}

function selectSession(sessionId, sessionTitle) {
    currentSession = { id: sessionId, title: sessionTitle };
    document.getElementById('sessionTitle').textContent = sessionTitle;
    navigateTo('seat');
    loadSeatMap(sessionId);
}

async function loadSeatMap(sessionId) {
    const grid = document.getElementById('seatGrid');
    grid.innerHTML = '<div class="loading">加载座位图...</div>';
    selectedSeats = [];
    updateSelectedSeatsUI();
    try {
        const response = await api.seats.getMap(sessionId);
        if (response.code === 200 && response.data) {
            loadSeatMapData = response.data;
            renderSeatMap(response.data);
        } else {
            grid.innerHTML = '<div class="empty-state"><div class="empty-state-icon">❌</div><p>加载失败，请稍后重试</p></div>';
        }
    } catch (error) {
        if (error && error.status === 401) {
            grid.innerHTML = '<div class="empty-state"><div class="empty-state-icon">🔒</div><p>请先登录后查看座位信息</p><button class="btn btn-primary" onclick="showLoginModal()">立即登录</button></div>';
            return;
        }
        grid.innerHTML = '<div class="empty-state"><div class="empty-state-icon">❌</div><p>加载失败，请稍后重试</p><button class="btn btn-primary" onclick="loadSeatMap(' + sessionId + ')">重新加载</button></div>';
    }
}

function renderSeatMap(seatData) {
    const grid = document.getElementById('seatGrid');
    if (!seatData || Object.keys(seatData).length === 0) {
        grid.innerHTML = '<div class="empty-state"><p>暂无座位数据</p></div>';
        return;
    }
    const rowNumbers = Object.keys(seatData).map(Number).sort(function(a, b) { return a - b; });
    let html = '';
    rowNumbers.forEach(function(rowNum) {
        const rowSeats = seatData[rowNum];
        if (rowSeats && rowSeats.length > 0) {
            html += '<div class="seat-row">';
            html += '<span class="seat-row-label">' + String.fromCharCode(64 + rowNum) + '</span>';
            rowSeats.forEach(function(seat) {
                const isSelected = selectedSeats.some(function(s) { return s.id === seat.id; });
                const normalizedStatus = seat.status ? seat.status.toLowerCase() : 'available';
                let seatStatus = isSelected ? 'selected' : normalizedStatus;
                const statusClass = getSeatStatusClass(seatStatus);
                const isClickable = normalizedStatus === 'available' || isSelected;
                const clickHandler = isClickable ? 'toggleSeat(' + seat.id + ', ' + seat.price + ', \'' + seat.seatType + '\', \'' + String.fromCharCode(64 + seat.rowNumber) + seat.seatNumber + '\')' : '';
                const titleText = seat.seatType + ' ¥' + seat.price;
                html += '<div class="seat ' + statusClass + '" ' + (clickHandler ? 'onclick="' + clickHandler + '"' : '') + ' title="' + titleText + '">' + seat.seatNumber + '</div>';
            });
            html += '</div>';
        }
    });
    grid.innerHTML = html || '<div class="empty-state"><p>暂无座位数据</p></div>';
}

function getSeatStatusClass(status) {
    switch (status) {
        case 'available': return 'available';
        case 'selected': return 'selected';
        case 'locked': return 'locked';
        case 'sold': return 'sold';
        default: return 'available';
    }
}

function toggleSeat(seatId, price, seatType, seatLabel) {
    const index = selectedSeats.findIndex(function(s) { return s.id === seatId; });
    if (index > -1) {
        selectedSeats.splice(index, 1);
    } else {
        if (selectedSeats.length >= 5) {
            showToast('最多只能选择5个座位', 'warning');
            return;
        }
        selectedSeats.push({ id: seatId, price: price, seatType: seatType, label: seatLabel });
    }
    if (loadSeatMapData) {
        renderSeatMap(loadSeatMapData);
    }
    updateSelectedSeatsUI();
}

function updateSelectedSeatsUI() {
    const list = document.getElementById('selectedSeatsList');
    const totalPrice = document.getElementById('totalPrice');
    const seatCount = document.getElementById('seatCount');
    const confirmBtn = document.getElementById('confirmSeatBtn');
    if (selectedSeats.length === 0) {
        list.innerHTML = '<p class="empty-tip">请选择座位</p>';
        confirmBtn.disabled = true;
    } else {
        list.innerHTML = selectedSeats.map(function(seat) {
            return '<div class="selected-seat-item"><span>' + seat.label + ' (' + seat.seatType + ')</span><span>¥' + seat.price + '</span><button onclick="removeSeat(' + seat.id + ')">×</button></div>';
        }).join('');
        confirmBtn.disabled = false;
    }
    const total = selectedSeats.reduce(function(sum, s) { return sum + s.price; }, 0);
    totalPrice.textContent = '¥' + total;
    seatCount.textContent = selectedSeats.length;
}

function removeSeat(seatId) {
    toggleSeat(seatId, 0, '', '');
}

async function confirmSeatSelection() {
    if (!currentUser) {
        showToast('请先登录', 'warning');
        showLoginModal();
        return;
    }
    if (selectedSeats.length === 0) {
        showToast('请选择座位', 'warning');
        return;
    }
    try {
        showToast('锁定座位中...', 'success');
        const seatIds = selectedSeats.map(function(s) { return s.id; });
        const response = await api.seats.lock(currentSession.id, seatIds, String(currentUser.id), '');
        if (response.code === 200) {
            const lockOrderId = response.data.orderId;
            const totalPrice = selectedSeats.reduce(function(sum, s) { return sum + s.price; }, 0);
            
            // 创建临时订单
            showToast('创建订单中...', 'success');
            const createResponse = await api.orders.create(lockOrderId, 'wechat', totalPrice);
            if (createResponse.code === 200) {
                const orderId = createResponse.data.orderId;
                
                // 显示支付选择模态框
                showPaymentChoiceModal(orderId, totalPrice, lockOrderId);
            } else {
                showToast('订单创建失败: ' + createResponse.message, 'error');
                loadSeatMap(currentSession.id);
            }
        } else {
            showToast(response.message || '锁定座位失败', 'error');
            loadSeatMap(currentSession.id);
        }
    } catch (error) {
        if (error && error.status === 401) {
            showToast('请先登录', 'warning');
            showLoginModal();
            return;
        }
        showToast(error.message || '锁定座位失败', 'error');
        loadSeatMap(currentSession.id);
    }
}

// 显示支付选择模态框
function showPaymentChoiceModal(orderId, totalPrice, lockOrderId) {
    const modal = document.getElementById('paymentChoiceModal');
    const amountElement = document.getElementById('paymentChoiceAmount');
    const orderIdElement = document.getElementById('paymentChoiceOrderId');
    
    amountElement.textContent = '¥' + totalPrice;
    orderIdElement.textContent = orderId;
    
    // 保存订单信息
    window.currentOrderId = orderId;
    window.currentLockOrderId = lockOrderId;
    window.currentTotalPrice = totalPrice;
    
    modal.classList.add('active');
}

// 关闭支付选择模态框
function closePaymentChoiceModal() {
    document.getElementById('paymentChoiceModal').classList.remove('active');
    
    // 重置选座状态
    selectedSeats = [];
    updateSelectedSeatsUI();
    
    // 重新加载座位图
    if (currentSession && currentSession.id) {
        loadSeatMap(currentSession.id);
    }
}

// 立即支付
async function payNow() {
    closePaymentChoiceModal();
    
    try {
        showToast('支付处理中...', 'success');
        
        const response = await api.orders.pay(window.currentOrderId, 'wechat');
        if (response.code === 200) {
            showToast('支付成功！订单已完成', 'success');
            
            // 重新加载座位图
            if (currentSession && currentSession.id) {
                await loadSeatMap(currentSession.id);
            }
            
            // 跳转到订单页面
            navigateTo('orders');
        } else {
            showToast('支付失败: ' + response.message, 'warning');
            
            // 支付失败时跳转到订单页面，用户可以重新支付
            navigateTo('orders');
        }
    } catch (error) {
        if (error && error.status === 401) {
            showToast('请先登录', 'warning');
            showLoginModal();
            return;
        }
        console.error('支付失败:', error);
        showToast('支付失败', 'warning');
        navigateTo('orders');
    }
}

// 稍后支付
function payLater() {
    closePaymentChoiceModal();
    showToast('订单已创建，请在30分钟内完成支付', 'success');
    
    // 跳转到订单页面
    navigateTo('orders');
}

function showPaymentModal() {
    document.getElementById('paymentModal').classList.add('active');
}

async function closePaymentModal() {
    if (window.lockOrderId) {
        try {
            await api.seats.release(window.lockOrderId);
        } catch (error) {
            console.error('释放座位失败:', error);
        }
    }
    document.getElementById('paymentModal').classList.remove('active');
}

async function confirmPayment() {
    try {
        showToast('支付处理中...', 'success');
        
        // 获取选中的座位和总价
        const seatIds = selectedSeats.map(function(s) { return s.id; });
        const totalPrice = selectedSeats.reduce(function(sum, s) { return sum + s.price; }, 0);
        
        // 使用之前保存的lockOrderId
        const lockOrderId = window.lockOrderId || 'LOCK_' + Date.now();
        
        // 1. 先创建订单
        const createResponse = await api.orders.create(lockOrderId, 'wechat', totalPrice);
        if (createResponse.code !== 200) {
            throw new Error('订单创建失败: ' + createResponse.message);
        }
        
        const orderId = createResponse.data.orderId;
        
        // 2. 模拟支付成功
        setTimeout(async function() {
            try {
                // 调用支付API
                const payResponse = await api.orders.pay(orderId, 'wechat');
                if (payResponse.code === 200) {
                    showToast('支付成功！订单已完成', 'success');
                    
                    // 支付成功后，重新加载座位图以更新状态
                    if (currentSession && currentSession.id) {
                        await loadSeatMap(currentSession.id);
                    }
                } else {
                    showToast('支付失败: ' + payResponse.message, 'warning');
                }
            } catch (error) {
                if (error && error.status === 401) {
                    showToast('请先登录', 'warning');
                    showLoginModal();
                    return;
                }
                console.error('支付失败:', error);
                showToast('支付失败', 'warning');
            }
            
            closePaymentModal();
            
            // 重置状态并跳转到订单页面
            selectedSeats = [];
            updateSelectedSeatsUI();
            navigateTo('orders');
        }, 1000);
        
    } catch (error) {
        if (error && error.status === 401) {
            showToast('请先登录', 'warning');
            showLoginModal();
            return;
        }
        showToast(error.message || '支付失败', 'error');
    }
}

let currentOrderPage = 1;
let currentOrderKeyword = '';
const ORDER_PAGE_SIZE = 10;

async function loadOrders(pageNum = 1, keyword = '') {
    currentOrderPage = pageNum;
    currentOrderKeyword = keyword;
    
    const list = document.getElementById('ordersList');
    const pagination = document.getElementById('ordersPagination');
    list.innerHTML = '<div class="loading">加载中...</div>';
    pagination.style.display = 'none';
    
    try {
        const response = await api.orders.getPage(currentUser.id, pageNum, ORDER_PAGE_SIZE, keyword);
        if (response.code === 200 && response.data && response.data.records && response.data.records.length > 0) {
            renderOrders(response.data.records);
            renderPagination(response.data);
        } else {
            list.innerHTML = '<div class="empty-state"><div class="empty-state-icon">📋</div><p>暂无订单</p><button class="btn btn-primary" onclick="navigateTo(\'sessions\')">去购票</button></div>';
            pagination.style.display = 'none';
        }
    } catch (error) {
        if (error && error.status === 401) {
            list.innerHTML = '<div class="empty-state"><div class="empty-state-icon">🔒</div><p>请先登录后查看订单</p><button class="btn btn-primary" onclick="showLoginModal()">立即登录</button></div>';
            return;
        }
        list.innerHTML = '<div class="empty-state"><div class="empty-state-icon">❌</div><p>加载失败，请稍后重试</p><button class="btn btn-primary" onclick="loadOrders(' + pageNum + ', \'' + keyword.replace(/'/g, "\\'") + '\')">重新加载</button></div>';
    }
}

function renderPagination(pageData) {
    const pagination = document.getElementById('ordersPagination');
    pagination.style.display = 'flex';
    
    let html = '';
    
    html += '<button class="pagination-btn" onclick="loadOrders(1, \'' + currentOrderKeyword.replace(/'/g, "\\'") + '\')" ' + (pageData.pageNum <= 1 ? 'disabled' : '') + '>首页</button>';
    html += '<button class="pagination-btn" onclick="loadOrders(' + (pageData.pageNum - 1) + ', \'' + currentOrderKeyword.replace(/'/g, "\\'") + '\')" ' + (pageData.pageNum <= 1 ? 'disabled' : '') + '>上一页</button>';
    
    const startPage = Math.max(1, pageData.pageNum - 2);
    const endPage = Math.min(pageData.pages, pageData.pageNum + 2);
    
    for (let i = startPage; i <= endPage; i++) {
        html += '<button class="pagination-btn ' + (i === pageData.pageNum ? 'active' : '') + '" onclick="loadOrders(' + i + ', \'' + currentOrderKeyword.replace(/'/g, "\\'") + '\')">' + i + '</button>';
    }
    
    html += '<button class="pagination-btn" onclick="loadOrders(' + (pageData.pageNum + 1) + ', \'' + currentOrderKeyword.replace(/'/g, "\\'") + '\')" ' + (pageData.pageNum >= pageData.pages ? 'disabled' : '') + '>下一页</button>';
    html += '<button class="pagination-btn" onclick="loadOrders(' + pageData.pages + ', \'' + currentOrderKeyword.replace(/'/g, "\\'") + '\')" ' + (pageData.pageNum >= pageData.pages ? 'disabled' : '') + '>末页</button>';
    html += '<span class="pagination-info">共 ' + pageData.total + ' 条，第 ' + pageData.pageNum + '/' + pageData.pages + ' 页</span>';
    
    pagination.innerHTML = html;
}

function searchOrders() {
    const keyword = document.getElementById('orderSearchKeyword').value.trim();
    loadOrders(1, keyword);
}

function resetOrderSearch() {
    document.getElementById('orderSearchKeyword').value = '';
    loadOrders(1, '');
}

function handleOrderSearchKeyup(event) {
    if (event.key === 'Enter') {
        searchOrders();
    }
}

function renderOrders(orders) {
    const list = document.getElementById('ordersList');
    list.innerHTML = orders.map(function(order) {
        let statusHtml = '<span class="order-status ' + order.orderStatus + '">' + getOrderStatusText(order.orderStatus) + '</span>';
        let actionHtml = '<button class="btn btn-outline" onclick="viewOrderDetail(\'' + order.orderId + '\')">查看详情</button>';
        
        // 如果是待支付订单，显示倒计时和支付按钮
        if (order.orderStatus === 'PENDING_PAYMENT' && order.expireTime) {
            const expireTime = new Date(order.expireTime).getTime();
            const now = new Date().getTime();
            const remainingTime = Math.max(0, expireTime - now);
            
            if (remainingTime > 0) {
                statusHtml += '<div class="order-countdown" id="countdown-' + order.orderId + '">剩余时间：<span class="countdown-timer">' + formatTime(remainingTime) + '</span></div>';
                actionHtml = '<button class="btn btn-primary" onclick="payOrder(\'' + order.orderId + '\')">继续支付</button> <button class="btn btn-outline" onclick="cancelOrder(\'' + order.orderId + '\')">取消订单</button>';
            } else {
                statusHtml += '<div class="order-expired">订单已过期</div>';
            }
        }
        
        return '<div class="order-card"><div class="order-header"><span class="order-id">订单号：' + order.orderId + '</span>' + statusHtml + '</div><div class="order-info"><div class="order-info-item"><span>演出名称：</span><span>' + order.activityName + '</span></div><div class="order-info-item"><span>演出时间：</span><span>' + order.sessionDate + ' ' + order.sessionTime + '</span></div><div class="order-info-item"><span>演出场馆：</span><span>' + order.venue + '</span></div><div class="order-info-item"><span>座位信息：</span><span>' + order.seatInfo + '</span></div></div><div class="order-footer"><span class="order-total">¥' + order.totalPrice + '</span>' + actionHtml + '</div></div>';
    }).join('');
    
    // 启动倒计时
    startCountdowns(orders);
}

function getOrderStatusText(status) {
    switch (status) {
        case 'PENDING_PAYMENT':
        case 'PENDING':
            return '待支付';
        case 'PAID':
        case 'SUCCESS':
            return '已支付';
        case 'CANCELLED':
            return '已取消';
        case 'REFUNDED':
            return '已退款';
        case 'FAILED':
            return '支付失败';
        default:
            return status;
    }
}

async function viewOrderDetail(orderId) {
    const detail = document.getElementById('orderDetail');
    detail.innerHTML = '<div class="loading">加载中...</div>';
    navigateTo('orderDetail');
    try {
        const response = await api.orders.getDetail(orderId);
        if (response.code === 200 && response.data) {
            renderOrderDetail(response.data);
        } else {
            detail.innerHTML = '<div class="empty-state"><p>加载失败</p></div>';
        }
    } catch (error) {
        if (error && error.status === 401) {
            detail.innerHTML = '<div class="empty-state"><div class="empty-state-icon">🔒</div><p>请先登录后查看订单详情</p><button class="btn btn-primary" onclick="showLoginModal()">立即登录</button></div>';
            return;
        }
        detail.innerHTML = '<div class="empty-state"><p>加载失败</p></div>';
    }
}

function renderOrderDetail(order) {
    const detail = document.getElementById('orderDetail');
    let actionButtons = '<div style="margin-top: 24px; text-align: right;">';
    
    // 如果是待支付状态，显示继续支付和取消订单按钮
    if (order.orderStatus === 'PENDING_PAYMENT') {
        actionButtons += '<button class="btn btn-primary" onclick="payOrder(\'' + order.orderId + '\')" style="margin-left: 12px;">继续支付</button>';
        actionButtons += '<button class="btn btn-outline" onclick="cancelOrder(\'' + order.orderId + '\')" style="margin-left: 12px;">取消订单</button>';
    }
    
    actionButtons += '<button class="btn btn-outline" onclick="navigateTo(\'orders\')" style="margin-left: 12px;">返回</button></div>';
    
    detail.innerHTML = '<div class="order-detail-header"><h3>订单号：' + order.orderId + '</h3><span class="order-status ' + order.orderStatus + '">' + getOrderStatusText(order.orderStatus) + '</span></div><h3>订单信息</h3><div class="detail-row"><span class="detail-label">演出名称</span><span class="detail-value">' + order.activityName + '</span></div><div class="detail-row"><span class="detail-label">演出日期</span><span class="detail-value">' + order.sessionDate + '</span></div><div class="detail-row"><span class="detail-label">演出时间</span><span class="detail-value">' + order.sessionTime + '</span></div><div class="detail-row"><span class="detail-label">演出场馆</span><span class="detail-value">' + order.venue + '</span></div><div class="detail-row"><span class="detail-label">座位信息</span><span class="detail-value">' + order.seatInfo + '</span></div><h3>支付信息</h3><div class="detail-row"><span class="detail-label">订单金额</span><span class="detail-value" style="color: #ff4d4f; font-weight: bold;">¥' + order.totalPrice + '</span></div><div class="detail-row"><span class="detail-label">支付方式</span><span class="detail-value">' + (order.paymentMethod === 'wechat' ? '微信支付' : '支付宝') + '</span></div><div class="detail-row"><span class="detail-label">支付状态</span><span class="detail-value">' + getOrderStatusText(order.paymentStatus) + '</span></div>' + actionButtons;
}

function showToast(message, type) {
    type = type || 'success';
    const toast = document.getElementById('toast');
    const toastMessage = document.getElementById('toastMessage');
    toast.className = 'toast ' + type + ' show';
    toastMessage.textContent = message;
    setTimeout(function() {
        toast.classList.remove('show');
    }, 3000);
}

// 格式化时间（毫秒转换为时分秒）
function formatTime(milliseconds) {
    const totalSeconds = Math.floor(milliseconds / 1000);
    const hours = Math.floor(totalSeconds / 3600);
    const minutes = Math.floor((totalSeconds % 3600) / 60);
    const seconds = totalSeconds % 60;
    
    return `${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
}

// 启动所有订单的倒计时
function startCountdowns(orders) {
    const pendingOrders = orders.filter(order => order.orderStatus === 'PENDING_PAYMENT' && order.expireTime);
    
    pendingOrders.forEach(order => {
        const expireTime = new Date(order.expireTime).getTime();
        const countdownElement = document.getElementById('countdown-' + order.orderId);
        
        if (countdownElement) {
            const timer = setInterval(() => {
                const now = new Date().getTime();
                const remainingTime = Math.max(0, expireTime - now);
                
                if (remainingTime > 0) {
                    const timerElement = countdownElement.querySelector('.countdown-timer');
                    if (timerElement) {
                        timerElement.textContent = formatTime(remainingTime);
                    }
                } else {
                    clearInterval(timer);
                    countdownElement.innerHTML = '<div class="order-expired">订单已过期</div>';
                    
                    // 重新加载订单列表
                    setTimeout(() => {
                        loadOrders();
                    }, 1000);
                }
            }, 1000);
        }
    });
}

// 继续支付订单
async function payOrder(orderId) {
    try {
        showToast('支付处理中...', 'success');
        
        const response = await api.orders.pay(orderId, 'wechat');
        if (response.code === 200) {
            showToast('支付成功！订单已完成', 'success');
            
            // 重新加载当前页订单列表
            setTimeout(() => {
                loadOrders(currentOrderPage, currentOrderKeyword);
            }, 1000);
        } else {
            showToast('支付失败: ' + response.message, 'warning');
        }
    } catch (error) {
        if (error && error.status === 401) {
            showToast('请先登录', 'warning');
            showLoginModal();
            return;
        }
        console.error('支付失败:', error);
        showToast('支付失败', 'warning');
    }
}

// 取消订单
async function cancelOrder(orderId) {
    if (confirm('确定要取消这个订单吗？取消后座位将被释放。')) {
        try {
            showToast('取消处理中...', 'success');
            
            const response = await api.orders.cancel(orderId);
            if (response.code === 200) {
                showToast('订单已取消，座位已释放', 'success');
                
                // 重新加载当前页订单列表
                setTimeout(() => {
                    loadOrders(currentOrderPage, currentOrderKeyword);
                }, 1000);
            } else {
                showToast('取消失败: ' + response.message, 'warning');
            }
        } catch (error) {
            if (error && error.status === 401) {
                showToast('请先登录', 'warning');
                showLoginModal();
                return;
            }
            console.error('取消失败:', error);
            showToast('取消失败', 'warning');
        }
    }
}

document.addEventListener('DOMContentLoaded', init);
