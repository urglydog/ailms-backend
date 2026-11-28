#!/usr/bin/env python3
"""Tạo (hoặc cập nhật) kênh Telegram và chính sách gửi cảnh báo của Grafana qua API — chạy lại bao nhiêu lần cũng được.

Lý do dùng API thay vì file provisioning: Grafana tự đổi Chat ID dạng số thành kiểu số khi thay biến môi trường, còn Telegram
cần chuỗi (lỗi "cannot unmarshal number into ... chatid of type string"), làm Grafana dừng khởi động.
Đọc be/.env lúc chạy (GRAFANA_ADMIN_*, TELEGRAM_*), KHÔNG in token/mật khẩu. Grafana chỉ nghe 127.0.0.1:3001.
Dùng:  python3 -I ops/setup_grafana_alerts.py
"""
import base64, json, sys, urllib.request, urllib.error

ENV = '/var/lms/be/.env'
BASE = 'http://127.0.0.1:3001'
NAME = 'telegram-lms'

env = {}
for line in open(ENV, encoding='utf-8'):
    if '=' in line and not line.lstrip().startswith('#'):
        k, v = line.rstrip('\n').split('=', 1)
        env[k] = v
user = env.get('GRAFANA_ADMIN_USER', 'admin')
pw = env.get('GRAFANA_ADMIN_PASSWORD', '')
tok, chat = env.get('TELEGRAM_BOT_TOKEN', ''), env.get('TELEGRAM_CHAT_ID', '')
if not (pw and tok and chat):
    sys.exit('Thiếu GRAFANA_ADMIN_PASSWORD / TELEGRAM_BOT_TOKEN / TELEGRAM_CHAT_ID trong .env')
auth = 'Basic ' + base64.b64encode(f'{user}:{pw}'.encode()).decode()


def api(method, path, body=None):
    req = urllib.request.Request(BASE + path, method=method, data=json.dumps(body).encode() if body is not None else None,
                                 headers={'Authorization': auth, 'Content-Type': 'application/json',
                                          'X-Disable-Provenance': 'true'})
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            raw = r.read()
            return r.status, (json.loads(raw) if raw else None)
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()[:200].replace(tok, '***')


cp = {'name': NAME, 'type': 'telegram',
      'settings': {'bottoken': tok, 'chatid': str(chat), 'disable_notification': False}}
st, lst = api('GET', '/api/v1/provisioning/contact-points')
exists = st == 200 and any(c.get('name') == NAME for c in lst)
uid = next((c['uid'] for c in lst if c.get('name') == NAME), None) if exists else None
if exists:
    cp['uid'] = uid
    st, res = api('PUT', f'/api/v1/provisioning/contact-points/{uid}', cp)
else:
    st, res = api('POST', '/api/v1/provisioning/contact-points', cp)
print('Kênh Telegram:', 'cập nhật' if exists else 'tạo mới', '-> HTTP', st, '' if st < 300 else res)

policy = {'receiver': NAME, 'group_by': ['grafana_folder', 'alertname'],
          'group_wait': '30s', 'group_interval': '5m', 'repeat_interval': '4h'}
st, res = api('PUT', '/api/v1/provisioning/policies', policy)
print('Chính sách gửi (mọi cảnh báo -> Telegram, nhắc lại mỗi 4 giờ):', 'HTTP', st, '' if st < 300 else res)
