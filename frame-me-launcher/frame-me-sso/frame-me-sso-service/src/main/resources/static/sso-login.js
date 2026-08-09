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
            // 防 open redirect：只允许站内相对路径，拒绝 //evil.com 这类协议相对地址
            if (redirect && redirect.startsWith('/') && !redirect.startsWith('//')) {
                location.href = redirect;
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
