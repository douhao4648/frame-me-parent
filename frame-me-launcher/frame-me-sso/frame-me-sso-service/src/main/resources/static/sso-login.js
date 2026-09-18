const form = document.getElementById('loginForm');
const btn = document.getElementById('submitBtn');
const msg = document.getElementById('msg');

form.addEventListener('submit', async (e) => {
    e.preventDefault();
    msg.textContent = '';
    btn.disabled = true;
    btn.textContent = '登录中…';
    try {
        const account = document.getElementById('account').value;
        const password = document.getElementById('password').value;
        const res = await fetch('/base/auth/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ account, password })
        });
        const data = await res.json();
        if (data.code === 200) {
            const params = new URLSearchParams(location.search);
            const redirect = params.get('redirect');
            // 防 open redirect：同源白名单——交给 URL 解析器判同源（/\evil.com、%5c 等
            // 归一化绕法整体消灭），拒绝 //evil.com 协议相对地址与外站
            let u = null;
            try {
                u = redirect ? new URL(redirect, location.origin) : null;
            } catch (ignored) {
                // 畸形地址按非法回跳处理
            }
            if (u && redirect.startsWith('/') && u.origin === location.origin) {
                location.href = u.pathname + u.search + u.hash;
            } else if (redirect) {
                msg.textContent = '非法的回跳地址';
            } else {
                location.href = '/';
            }
        } else {
            msg.textContent = data.msg || '登录失败';
        }
    } catch (err) {
        msg.textContent = '网络异常，请稍后重试';
    } finally {
        btn.disabled = false;
        btn.textContent = '登 录';
    }
});
