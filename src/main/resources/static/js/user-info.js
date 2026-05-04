const USER_INFO_API_BASE_URL = '/api/v1';

function initUserInfo() {
    const navUserHtml = `
        <div class="nav-user">
            <button class="user-info-btn" onclick="showUserInfo()">👤 个人信息</button>
        </div>
    `;
    
    const navContainer = document.querySelector('.nav-container');
    if (navContainer && !navContainer.querySelector('.nav-user')) {
        const navLinks = navContainer.querySelector('.nav-links');
        if (navLinks) {
            navLinks.insertAdjacentHTML('afterend', navUserHtml);
        }
    }
    
    const modalHtml = `
        <div class="modal-overlay" id="userInfoModal">
            <div class="modal">
                <div class="modal-header">
                    <h2 class="modal-title">👤 个人信息与权限</h2>
                    <button class="modal-close" onclick="closeUserInfo()">×</button>
                </div>
                <div class="modal-body" id="userInfoContent">
                    <div style="text-align: center; padding: 40px; color: #94a3b8;">
                        加载中...
                    </div>
                </div>
            </div>
        </div>
    `;
    
    if (!document.getElementById('userInfoModal')) {
        document.body.insertAdjacentHTML('beforeend', modalHtml);
        
        document.getElementById('userInfoModal').addEventListener('click', function(e) {
            if (e.target === this) {
                closeUserInfo();
            }
        });
    }
    
    addUserInfoStyles();
}

function addUserInfoStyles() {
    if (document.getElementById('user-info-styles')) return;
    
    const styles = `
        <style id="user-info-styles">
        .nav-user {
            margin-left: auto;
            display: flex;
            align-items: center;
        }
        
        .user-info-btn {
            padding: 10px 20px;
            background: rgba(255, 255, 255, 0.15);
            color: white;
            border: 1px solid rgba(255, 255, 255, 0.3);
            border-radius: 10px;
            cursor: pointer;
            font-size: 14px;
            font-weight: 600;
            transition: all 0.3s ease;
        }
        
        .user-info-btn:hover {
            background: rgba(255, 255, 255, 0.25);
            transform: translateY(-2px);
        }
        
        .modal-overlay {
            position: fixed;
            top: 0;
            left: 0;
            width: 100%;
            height: 100%;
            background: rgba(0, 0, 0, 0.5);
            display: none;
            justify-content: center;
            align-items: center;
            z-index: 1000;
        }
        
        .modal-overlay.active {
            display: flex;
        }
        
        .modal {
            background: white;
            border-radius: 24px;
            width: 90%;
            max-width: 600px;
            max-height: 80vh;
            overflow-y: auto;
            box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
        }
        
        .modal-header {
            padding: 32px;
            border-bottom: 2px solid #e2e8f0;
            display: flex;
            justify-content: space-between;
            align-items: center;
        }
        
        .modal-title {
            font-size: 24px;
            font-weight: 700;
            color: #1e293b;
            margin: 0;
        }
        
        .modal-close {
            background: #f1f5f9;
            border: none;
            width: 40px;
            height: 40px;
            border-radius: 10px;
            cursor: pointer;
            font-size: 20px;
            transition: all 0.3s ease;
        }
        
        .modal-close:hover {
            background: #e2e8f0;
        }
        
        .modal-body {
            padding: 32px;
        }
        
        .info-section {
            margin-bottom: 32px;
        }
        
        .info-section-title {
            font-size: 18px;
            font-weight: 700;
            color: #1e293b;
            margin-bottom: 20px;
            display: flex;
            align-items: center;
            gap: 10px;
        }
        
        .info-item {
            display: flex;
            padding: 12px 0;
            border-bottom: 1px solid #f1f5f9;
        }
        
        .info-item:last-child {
            border-bottom: none;
        }
        
        .info-label {
            width: 100px;
            font-weight: 600;
            color: #64748b;
            flex-shrink: 0;
        }
        
        .info-value {
            color: #1e293b;
        }
        
        .permission-badge, .role-badge {
            display: inline-block;
            padding: 6px 12px;
            border-radius: 8px;
            font-size: 13px;
            font-weight: 500;
            margin: 4px;
        }
        
        .permission-badge {
            background: #dbeafe;
            color: #1d4ed8;
        }
        
        .role-badge {
            background: #dcfce7;
            color: #15803d;
        }
        
        .empty-state {
            color: #94a3b8;
            font-style: italic;
            padding: 10px 0;
        }
        </style>
    `;
    
    document.head.insertAdjacentHTML('beforeend', styles);
}

async function showUserInfo() {
    const modal = document.getElementById('userInfoModal');
    const content = document.getElementById('userInfoContent');
    modal.classList.add('active');
    
    const token = localStorage.getItem('token');
    console.log('Token:', token);
    
    if (!token) {
        content.innerHTML = `
            <div style="text-align: center; padding: 40px; color: #dc2626;">
                请先登录
            </div>
        `;
        return;
    }
    
    try {
        const apiBaseUrl = typeof API_BASE_URL !== 'undefined' ? API_BASE_URL : USER_INFO_API_BASE_URL;
        console.log('请求URL:', apiBaseUrl + '/user/info');
        
        const response = await fetch(apiBaseUrl + '/user/info', {
            method: 'GET',
            headers: {
                'Authorization': 'Bearer ' + token,
                'Content-Type': 'application/json'
            }
        });
        
        const data = await response.json();
        console.log('后端返回数据:', data);
        
        if (data.code === 200) {
            const userData = data.data;
            renderUserInfo(userData);
        } else {
            content.innerHTML = `
                <div style="text-align: center; padding: 40px; color: #dc2626;">
                    加载失败：${data.message}
                </div>
            `;
        }
    } catch (error) {
        console.error('加载个人信息失败:', error);
        content.innerHTML = `
            <div style="text-align: center; padding: 40px; color: #dc2626;">
                加载失败，请稍后重试
            </div>
        `;
    }
}

function renderUserInfo(data) {
    console.log('renderUserInfo 接收到的数据:', data);
    const content = document.getElementById('userInfoContent');
    
    if (!data) {
        content.innerHTML = `
            <div style="text-align: center; padding: 40px; color: #dc2626;">
                数据为空
            </div>
        `;
        return;
    }
    
    const user = data.user || {};
    const permissions = data.permissions || [];
    const roles = data.roles || [];
    
    console.log('解析后数据:', { user, permissions, roles });
    
    let permissionsHtml = '';
    if (permissions && permissions.length > 0) {
        permissionsHtml = permissions.map(p => 
            `<span class="permission-badge">${p}</span>`
        ).join('');
    } else {
        permissionsHtml = '<div class="empty-state">暂无权限</div>';
    }
    
    let rolesHtml = '';
    if (roles && roles.length > 0) {
        rolesHtml = roles.map(r => 
            `<span class="role-badge">${r}</span>`
        ).join('');
    } else {
        rolesHtml = '<div class="empty-state">暂无角色</div>';
    }
    
    const html = `
        <div class="info-section">
            <div class="info-section-title">👤 基本信息</div>
            <div class="info-item">
                <span class="info-label">用户ID</span>
                <span class="info-value">${user.id || '-'}</span>
            </div>
            <div class="info-item">
                <span class="info-label">昵称</span>
                <span class="info-value">${user.nickname || '-'}</span>
            </div>
            <div class="info-item">
                <span class="info-label">手机号</span>
                <span class="info-value">${user.phone || '-'}</span>
            </div>
            <div class="info-item">
                <span class="info-label">邮箱</span>
                <span class="info-value">${user.email || '-'}</span>
            </div>
        </div>
        
        <div class="info-section">
            <div class="info-section-title">🎭 角色</div>
            <div style="padding: 10px 0;">
                ${rolesHtml}
            </div>
        </div>
        
        <div class="info-section">
            <div class="info-section-title">🔐 权限</div>
            <div style="padding: 10px 0;">
                ${permissionsHtml}
            </div>
        </div>
    `;
    
    console.log('生成的HTML:', html);
    content.innerHTML = html;
}

function closeUserInfo() {
    document.getElementById('userInfoModal').classList.remove('active');
}

if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initUserInfo);
} else {
    initUserInfo();
}
