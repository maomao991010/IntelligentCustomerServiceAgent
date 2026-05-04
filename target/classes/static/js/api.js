const API_BASE_URL = '/api/v1';

function isFrontendPage() {
    const path = window.location.pathname;
    const frontendPages = ['index.html', 'my-orders.html', 'order-detail.html', 'user-center.html'];
    const isFrontend = path === '/' || frontendPages.some(page => path.endsWith('/' + page));
    return isFrontend;
}

const api = {
    async request(url, options) {
        options = options || {};
        const token = localStorage.getItem('token');
        const defaultHeaders = {
            'Content-Type': 'application/json'
        };

        if (token) {
            defaultHeaders['Authorization'] = 'Bearer ' + token;
        }

        try {
            const response = await fetch(API_BASE_URL + url, {
                method: options.method || 'GET',
                headers: Object.assign({}, defaultHeaders, options.headers || {}),
                body: options.body
            });

            if (response.status === 401) {
                localStorage.removeItem('token');
                localStorage.removeItem('user');
                localStorage.removeItem('permissions');
                localStorage.removeItem('roles');
                
                if (isFrontendPage()) {
                    if (typeof showLoginModal === 'function') {
                        showLoginModal();
                    }
                } else {
                    window.location.href = 'login.html';
                }
                
                throw {
                    status: 401,
                    message: '未登录，请先登录'
                };
            }

            if (response.status === 403) {
                alert('没有权限访问该资源');
                throw {
                    status: 403,
                    message: '没有权限访问该资源'
                };
            }

            const data = await response.json();

            if (!response.ok) {
                throw {
                    status: response.status,
                    message: data.message || '请求失败',
                    data: data
                };
            }

            return data;
        } catch (error) {
            console.error('API Request Error:', error);
            throw error;
        }
    },

    auth: {
        async login(phone, email, password, verificationCode, codeId, encryptedPassword) {
            const data = { verificationCode: verificationCode, codeId: codeId };
            if (phone) {
                data.phone = phone;
            } else if (email) {
                data.email = email;
            }
            if (encryptedPassword) {
                data.encryptedPassword = encryptedPassword;
            } else {
                data.password = password;
            }
            return api.request('/auth/login', {
                method: 'POST',
                body: JSON.stringify(data)
            });
        },

        async register(phone, password, nickname, verificationCode, codeId) {
            return api.request('/auth/register', {
                method: 'POST',
                body: JSON.stringify({ phone: phone, password: password, nickname: nickname, verificationCode: verificationCode, codeId: codeId })
            });
        },

        async getVerificationCode() {
            return api.request('/auth/verification-code', {
                method: 'GET'
            });
        },

        async sendEmailCode(email) {
            return api.request('/auth/send-email-code?email=' + encodeURIComponent(email), {
                method: 'POST'
            });
        },

        async logout(token) {
            return api.request('/auth/logout?token=' + encodeURIComponent(token), {
                method: 'POST'
            });
        },

        async registerWithEmail(phone, email, password, nickname, verificationCode) {
            return api.request('/auth/register', {
                method: 'POST',
                body: JSON.stringify({ phone: phone, email: email, password: password, nickname: nickname, verificationCode: verificationCode })
            });
        },

        async getSM2PublicKey() {
            return api.request('/auth/sm2-public-key', {
                method: 'GET'
            });
        }
    },

    sessions: {
        async getList(activityId) {
            const params = activityId ? '?activityId=' + activityId : '';
            return api.request('/sessions' + params, {
                method: 'GET'
            });
        },
        
        async getPage(pageNum, pageSize, keyword) {
            let url = '/sessions/page?pageNum=' + pageNum + '&pageSize=' + pageSize;
            if (keyword) {
                url += '&keyword=' + encodeURIComponent(keyword);
            }
            return api.request(url, {
                method: 'GET'
            });
        }
    },

    seats: {
        async getMap(sessionId) {
            return api.request('/seats/map/' + sessionId, {
                method: 'GET'
            });
        },

        async lock(sessionId, seatIds, userId, verificationCode) {
            return api.request('/seats/lock', {
                method: 'POST',
                body: JSON.stringify({
                    sessionId: sessionId,
                    seatIds: seatIds,
                    userId: userId,
                    verificationCode: verificationCode
                })
            });
        },

        async release(lockOrderId) {
            return api.request('/seats/release?lockOrderId=' + lockOrderId, {
                method: 'POST'
            });
        }
    },

    orders: {
        async create(lockOrderId, paymentMethod, totalPrice) {
            return api.request('/orders/create', {
                method: 'POST',
                body: JSON.stringify({
                    lockOrderId: lockOrderId,
                    paymentMethod: paymentMethod,
                    totalPrice: totalPrice
                })
            });
        },

        async getDetail(orderId) {
            return api.request('/orders/detail/' + orderId, {
                method: 'GET'
            });
        },

        async getList(userId) {
            return api.request('/orders/list?userId=' + userId, {
                method: 'GET'
            });
        },
        
        async getPage(userId, pageNum, pageSize, keyword) {
            let url = '/orders/page?userId=' + userId + '&pageNum=' + pageNum + '&pageSize=' + pageSize;
            if (keyword) {
                url += '&keyword=' + encodeURIComponent(keyword);
            }
            return api.request(url, {
                method: 'GET'
            });
        },

        async pay(orderId, paymentMethod) {
            return api.request('/orders/pay?orderId=' + orderId + '&paymentMethod=' + paymentMethod, {
                method: 'POST'
            });
        },

        async cancel(orderId, cancelReason) {
            let url = '/orders/cancel?orderId=' + orderId;
            if (cancelReason) url += '&cancelReason=' + encodeURIComponent(cancelReason);
            return api.request(url, { method: 'POST' });
        }
    },

    refunds: {
        async apply(orderId, refundReason, refundType) {
            return api.request('/refunds/apply', {
                method: 'POST',
                body: JSON.stringify({ orderId, refundReason, refundType: refundType || 'ORDER_CANCEL' })
            });
        },
        async audit(refundId, status, auditRemark) {
            return api.request('/refunds/audit/' + refundId, {
                method: 'POST',
                body: JSON.stringify({ status, auditRemark })
            });
        },
        async getByOrderId(orderId) {
            return api.request('/refunds/order/' + orderId, { method: 'GET' });
        },
        async getMyRefunds() {
            return api.request('/refunds/my', { method: 'GET' });
        },
        async getPage(pageNum, pageSize, status) {
            let url = '/refunds/page?pageNum=' + pageNum + '&pageSize=' + pageSize;
            if (status) url += '&status=' + status;
            return api.request(url, { method: 'GET' });
        }
    },

    tickets: {
        async generate(orderId) {
            return api.request('/tickets/generate/' + orderId, { method: 'POST' });
        },
        async getByOrderId(orderId) {
            return api.request('/tickets/order/' + orderId, { method: 'GET' });
        },
        async getByCode(ticketCode) {
            return api.request('/tickets/code/' + ticketCode, { method: 'GET' });
        },
        async use(ticketCode) {
            return api.request('/tickets/use/' + ticketCode, { method: 'POST' });
        },
        getQrCodeUrl(ticketCode) {
            return '/tickets/qrcode/' + ticketCode;
        },
        getPdfUrl(orderId) {
            return '/tickets/pdf/' + orderId;
        }
    },

    chat: {
        async sendMessage(sessionId, userId, question) {
            return api.request('/chat/send', {
                method: 'POST',
                body: JSON.stringify({
                    sessionId: sessionId,
                    userId: userId,
                    question: question
                })
            });
        },

        async getHistory(sessionId) {
            return api.request('/chat/history/' + sessionId, {
                method: 'GET'
            });
        },

        async getFaqList() {
            return api.request('/chat/faq/list', {
                method: 'GET'
            });
        },

        async getFaqPage(pageNum, pageSize, category) {
            let url = '/chat/faq/page?pageNum=' + pageNum + '&pageSize=' + pageSize;
            if (category) {
                url += '&category=' + encodeURIComponent(category);
            }
            return api.request(url, {
                method: 'GET'
            });
        },

        async getFaq(id) {
            return api.request('/chat/faq/' + id, {
                method: 'GET'
            });
        },

        async createFaq(faq) {
            return api.request('/chat/faq', {
                method: 'POST',
                body: JSON.stringify(faq)
            });
        },

        async updateFaq(id, faq) {
            return api.request('/chat/faq/' + id, {
                method: 'PUT',
                body: JSON.stringify(faq)
            });
        },

        async deleteFaq(id) {
            return api.request('/chat/faq/' + id, {
                method: 'DELETE'
            });
        },

        async requestTransfer(sessionId, userId, userName, userPhone, question) {
            return api.request('/chat/admin/transfer', {
                method: 'POST',
                body: JSON.stringify({
                    sessionId: sessionId,
                    userId: userId,
                    userName: userName,
                    userPhone: userPhone,
                    question: question
                })
            });
        },

        async getPendingTransfers() {
            return api.request('/chat/admin/transfer/pending', {
                method: 'GET'
            });
        },

        async getOverviewStatistics() {
            return api.request('/chat/admin/statistics/overview', {
                method: 'GET'
            });
        },

        async getDailyStatistics(date) {
            let url = '/chat/admin/statistics/daily';
            if (date) {
                url += '?date=' + encodeURIComponent(date);
            }
            return api.request(url, {
                method: 'GET'
            });
        },

        async getRecentChats(limit) {
            let url = '/chat/admin/history/recent';
            if (limit) {
                url += '?limit=' + limit;
            }
            return api.request(url, {
                method: 'GET'
            });
        }
    },

    userCenter: {
        async getProfile() {
            return api.request('/user/info', { method: 'GET' });
        },

        async updateProfile(nickname, email) {
            return api.request('/user-center/profile', {
                method: 'PUT',
                body: JSON.stringify({ nickname: nickname, email: email })
            });
        },

        async changePassword(oldPassword, newPassword) {
            return api.request('/user-center/password', {
                method: 'PUT',
                body: JSON.stringify({ oldPassword: oldPassword, newPassword: newPassword })
            });
        },

        async updateAvatar(avatarUrl) {
            return api.request('/user-center/avatar', {
                method: 'PUT',
                body: JSON.stringify({ avatarUrl: avatarUrl })
            });
        },

        async uploadAvatar(file) {
            const formData = new FormData();
            formData.append('file', file);
            const token = localStorage.getItem('token');
            const response = await fetch(API_BASE_URL + '/files/upload', {
                method: 'POST',
                headers: { 'Authorization': 'Bearer ' + token },
                body: formData
            });
            return response.json();
        },

        async bindPhone(phone, verificationCode) {
            return api.request('/user-center/bind-phone', {
                method: 'POST',
                body: JSON.stringify({ phone: phone, verificationCode: verificationCode })
            });
        },

        async bindEmail(email, verificationCode) {
            return api.request('/user-center/bind-email', {
                method: 'POST',
                body: JSON.stringify({ email: email, verificationCode: verificationCode })
            });
        },

        async unbindPhone() {
            return api.request('/user-center/unbind-phone', { method: 'POST' });
        },

        async unbindEmail() {
            return api.request('/user-center/unbind-email', { method: 'POST' });
        },

        async getAddressList() {
            return api.request('/user-center/addresses', { method: 'GET' });
        },

        async getAddress(id) {
            return api.request('/user-center/addresses/' + id, { method: 'GET' });
        },

        async addAddress(address) {
            return api.request('/user-center/addresses', {
                method: 'POST',
                body: JSON.stringify(address)
            });
        },

        async updateAddress(id, address) {
            return api.request('/user-center/addresses/' + id, {
                method: 'PUT',
                body: JSON.stringify(address)
            });
        },

        async deleteAddress(id) {
            return api.request('/user-center/addresses/' + id, { method: 'DELETE' });
        },

        async setDefaultAddress(id) {
            return api.request('/user-center/addresses/' + id + '/default', { method: 'PUT' });
        }
    },

    operationLogs: {
        async getPage(pageNum, pageSize, userId, module, type, startTime, endTime) {
            let url = '/operation-logs/page?pageNum=' + pageNum + '&pageSize=' + pageSize;
            if (userId) url += '&userId=' + userId;
            if (module) url += '&module=' + encodeURIComponent(module);
            if (type) url += '&type=' + encodeURIComponent(type);
            if (startTime) url += '&startTime=' + encodeURIComponent(startTime);
            if (endTime) url += '&endTime=' + encodeURIComponent(endTime);
            return api.request(url, { method: 'GET' });
        }
    }
};
