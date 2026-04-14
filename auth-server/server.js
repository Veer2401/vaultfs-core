require('dotenv').config();
const express = require('express');
const cors = require('cors');
const axios = require('axios');
const { v4: uuidv4 } = require('uuid');

const app = express();
const PORT = process.env.PORT || 4000;
const SERVER_URL = process.env.SERVER_URL || 'http://localhost:4000';
console.log(`[VaultFS] Auth server URL: ${SERVER_URL}`);

// ─── Middleware ───────────────────────────────────────────────
app.use(cors());
app.use(express.json());

// ─── Request logger ─────────────────────────────────────────
app.use((req, res, next) => {
  console.log(`[${new Date().toISOString()}] ${req.method} ${req.path}`);
  next();
});

// ─── Session store ───────────────────────────────────────────
const sessions = new Map();

// Clean expired sessions every 60 seconds
setInterval(() => {
  const now = Date.now();
  for (const [id, session] of sessions.entries()) {
    if (session.expiresAt && now > session.expiresAt) {
      sessions.delete(id);
    }
  }
}, 60 * 1000);

// ─── Root landing page ──────────────────────────────────────
app.get('/', (req, res) => {
  res.send(`
  <!DOCTYPE html>
  <html>
  <head>
      <title>VaultFS Auth Server</title>
      <style>
          * { margin: 0; padding: 0; box-sizing: border-box; }
          body { background: #0a0a0a; color: #fff; font-family: 'Segoe UI', sans-serif; display: flex; align-items: center; justify-content: center; height: 100vh; }
          .card { background: #111; border: 1px solid #333; border-radius: 16px; padding: 48px; text-align: center; width: 420px; }
          h1 { font-size: 2rem; margin-bottom: 8px; }
          .badge { display: inline-block; background: #238636; color: #fff; padding: 4px 12px; border-radius: 20px; font-size: 0.8rem; margin-bottom: 24px; }
          p { color: #888; line-height: 1.6; }
          code { background: #1a1a1a; padding: 2px 8px; border-radius: 4px; color: #58a6ff; }
      </style>
  </head>
  <body>
      <div class="card">
          <h1>\uD83D\uDD10 VaultFS</h1>
          <div class="badge">Auth Server v1.0.3</div>
          <p>This is the VaultFS authentication server.</p>
          <p style="margin-top: 12px;">Install the CLI: <code>npm install -g vaultfs</code></p>
      </div>
  </body>
  </html>
  `);
});

// ─── Health check ────────────────────────────────────────────
app.get('/health', (req, res) => {
  res.json({ status: 'ok', version: '1.0.3' });
});

// ─── Create new auth session ─────────────────────────────────
app.get('/auth/session/new', (req, res) => {
  const sessionId = uuidv4();
  sessions.set(sessionId, { status: 'pending', user: null, expiresAt: Date.now() + 5 * 60 * 1000 });
  // Also clean up via setTimeout as a fallback
  setTimeout(() => sessions.delete(sessionId), 5 * 60 * 1000);
  res.json({ sessionId });
});

// ─── Login page ─────────────────────────────────────────────
app.get('/auth/login', (req, res) => {
    const { sessionId } = req.query;
    res.send(`
    <!DOCTYPE html>
    <html lang="en">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>VaultFS — Sign In</title>
        <style>
            @import url('https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600&display=swap');

            * { margin: 0; padding: 0; box-sizing: border-box; }

            body {
                font-family: 'Inter', -apple-system, BlinkMacSystemFont, sans-serif;
                background: #000;
                color: #fff;
                height: 100vh;
                display: flex;
                align-items: center;
                justify-content: center;
                overflow: hidden;
            }

            /* Animated background */
            .bg {
                position: fixed;
                inset: 0;
                background:
                    radial-gradient(ellipse 80% 50% at 50% -20%, rgba(99,102,241,0.3), transparent),
                    radial-gradient(ellipse 60% 40% at 80% 80%, rgba(16,185,129,0.15), transparent),
                    radial-gradient(ellipse 40% 30% at 20% 60%, rgba(99,102,241,0.1), transparent);
                animation: bgPulse 8s ease-in-out infinite alternate;
            }

            @keyframes bgPulse {
                0% { opacity: 0.8; }
                100% { opacity: 1; }
            }

            /* Grid overlay */
            .grid {
                position: fixed;
                inset: 0;
                background-image:
                    linear-gradient(rgba(255,255,255,0.03) 1px, transparent 1px),
                    linear-gradient(90deg, rgba(255,255,255,0.03) 1px, transparent 1px);
                background-size: 50px 50px;
            }

            .card {
                position: relative;
                z-index: 10;
                background: rgba(255,255,255,0.04);
                backdrop-filter: blur(40px);
                -webkit-backdrop-filter: blur(40px);
                border: 1px solid rgba(255,255,255,0.1);
                border-radius: 24px;
                padding: 48px 40px;
                width: 100%;
                max-width: 400px;
                box-shadow:
                    0 0 0 1px rgba(255,255,255,0.05),
                    0 32px 64px rgba(0,0,0,0.4),
                    0 0 80px rgba(99,102,241,0.1);
                animation: cardIn 0.5s cubic-bezier(0.16,1,0.3,1) forwards;
            }

            @keyframes cardIn {
                from { opacity: 0; transform: translateY(24px) scale(0.97); }
                to { opacity: 1; transform: translateY(0) scale(1); }
            }

            .logo {
                display: flex;
                align-items: center;
                justify-content: center;
                gap: 12px;
                margin-bottom: 32px;
            }

            .logo-icon {
                width: 44px;
                height: 44px;
                background: linear-gradient(135deg, #6366f1, #10b981);
                border-radius: 12px;
                display: flex;
                align-items: center;
                justify-content: center;
                font-size: 20px;
                box-shadow: 0 8px 24px rgba(99,102,241,0.4);
            }

            .logo-text {
                font-size: 1.4rem;
                font-weight: 600;
                letter-spacing: -0.02em;
            }

            .logo-text span {
                color: rgba(255,255,255,0.4);
                font-weight: 300;
            }

            h1 {
                font-size: 1.6rem;
                font-weight: 600;
                letter-spacing: -0.03em;
                text-align: center;
                margin-bottom: 6px;
            }

            .subtitle {
                text-align: center;
                color: rgba(255,255,255,0.4);
                font-size: 0.9rem;
                font-weight: 400;
                margin-bottom: 36px;
            }

            .divider {
                display: flex;
                align-items: center;
                gap: 12px;
                margin: 20px 0;
            }

            .divider::before, .divider::after {
                content: '';
                flex: 1;
                height: 1px;
                background: rgba(255,255,255,0.08);
            }

            .divider span {
                font-size: 0.75rem;
                color: rgba(255,255,255,0.25);
                letter-spacing: 0.05em;
                text-transform: uppercase;
            }

            .btn {
                display: flex;
                align-items: center;
                justify-content: center;
                gap: 12px;
                width: 100%;
                padding: 13px 20px;
                border-radius: 12px;
                border: none;
                font-size: 0.95rem;
                font-weight: 500;
                cursor: pointer;
                text-decoration: none;
                transition: all 0.2s cubic-bezier(0.16,1,0.3,1);
                letter-spacing: -0.01em;
                font-family: inherit;
            }

            .btn:hover {
                transform: translateY(-1px);
                box-shadow: 0 8px 24px rgba(0,0,0,0.3);
            }

            .btn:active {
                transform: translateY(0px) scale(0.98);
            }

            .btn-google {
                background: #fff;
                color: #1a1a1a;
                margin-bottom: 12px;
            }

            .btn-google:hover {
                background: #f5f5f5;
            }

            .btn-github {
                background: rgba(255,255,255,0.06);
                color: #fff;
                border: 1px solid rgba(255,255,255,0.1);
            }

            .btn-github:hover {
                background: rgba(255,255,255,0.1);
                border-color: rgba(255,255,255,0.2);
            }

            .btn-guest {
                background: transparent;
                color: rgba(255,255,255,0.35);
                font-size: 0.85rem;
                padding: 10px;
                margin-top: 4px;
            }

            .btn-guest:hover {
                color: rgba(255,255,255,0.6);
                transform: none;
                box-shadow: none;
            }

            .google-icon {
                width: 18px;
                height: 18px;
            }

            .github-icon {
                width: 18px;
                height: 18px;
                fill: currentColor;
            }

            .footer {
                margin-top: 28px;
                text-align: center;
                font-size: 0.75rem;
                color: rgba(255,255,255,0.2);
                line-height: 1.6;
            }

            .footer a {
                color: rgba(255,255,255,0.35);
                text-decoration: none;
            }

            .footer a:hover {
                color: rgba(255,255,255,0.6);
            }

            .badge {
                display: inline-flex;
                align-items: center;
                gap: 5px;
                background: rgba(16,185,129,0.1);
                border: 1px solid rgba(16,185,129,0.2);
                color: #10b981;
                font-size: 0.7rem;
                font-weight: 500;
                padding: 3px 10px;
                border-radius: 20px;
                margin: 0 auto 24px;
                width: fit-content;
                letter-spacing: 0.03em;
            }

            .badge::before {
                content: '';
                width: 6px;
                height: 6px;
                background: #10b981;
                border-radius: 50%;
                animation: pulse 2s infinite;
            }

            @keyframes pulse {
                0%, 100% { opacity: 1; }
                50% { opacity: 0.3; }
            }
        </style>
    </head>
    <body>
        <div class="bg"></div>
        <div class="grid"></div>

        <div class="card">
            <div class="logo">
                <div class="logo-icon">\uD83D\uDD10</div>
                <div class="logo-text">Vault<span>FS</span></div>
            </div>

            <div class="badge">Secure File System</div>

            <h1>Welcome back</h1>
            <p class="subtitle">Sign in to access your vault</p>

            <a class="btn btn-google" href="/auth/google?sessionId=${sessionId}">
                <svg class="google-icon" viewBox="0 0 24 24">
                    <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"/>
                    <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/>
                    <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"/>
                    <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"/>
                </svg>
                Continue with Google
            </a>

            <a class="btn btn-github" href="/auth/github?sessionId=${sessionId}">
                <svg class="github-icon" viewBox="0 0 24 24">
                    <path d="M12 0C5.37 0 0 5.37 0 12c0 5.31 3.435 9.795 8.205 11.385.6.105.825-.255.825-.57 0-.285-.015-1.23-.015-2.235-3.015.555-3.795-.735-4.035-1.41-.135-.345-.72-1.41-1.23-1.695-.42-.225-1.02-.78-.015-.795.945-.015 1.62.87 1.845 1.23 1.08 1.815 2.805 1.305 3.495.99.105-.78.42-1.305.765-1.605-2.67-.3-5.46-1.335-5.46-5.925 0-1.305.465-2.385 1.23-3.225-.12-.3-.54-1.53.12-3.18 0 0 1.005-.315 3.3 1.23.96-.27 1.98-.405 3-.405s2.04.135 3 .405c2.295-1.56 3.3-1.23 3.3-1.23.66 1.65.24 2.88.12 3.18.765.84 1.23 1.905 1.23 3.225 0 4.605-2.805 5.625-5.475 5.925.435.375.81 1.095.81 2.22 0 1.605-.015 2.895-.015 3.3 0 .315.225.69.825.57A12.02 12.02 0 0 0 24 12c0-6.63-5.37-12-12-12z"/>
                </svg>
                Continue with GitHub
            </a>

            <div class="divider"><span>or</span></div>

            <a class="btn btn-guest" href="/auth/guest?sessionId=${sessionId}">
                Continue as Guest \u2192
            </a>

            <div class="footer">
                By signing in you agree to our
                <a href="https://github.com/ThreatGuardian/vaultfs-core">Terms</a>
                \u00B7
                <a href="https://github.com/ThreatGuardian/vaultfs-core">Privacy</a>
            </div>
        </div>
    </body>
    </html>
    `);
});

// ─── Guest login ─────────────────────────────────────────────
app.get('/auth/guest', (req, res) => {
  const { sessionId } = req.query;
  if (!sessionId || !sessions.has(sessionId)) {
    return res.status(400).json({ error: 'Invalid or missing sessionId' });
  }
  sessions.set(sessionId, {
    status: 'done',
    user: { name: 'Guest', email: 'guest@vaultfs.local', avatar: null, provider: 'guest' }
  });
  res.redirect(`${SERVER_URL}/auth/success?name=Guest&provider=guest`);
});

// ─── Google OAuth ────────────────────────────────────────────
app.get('/auth/google', (req, res) => {
  const { sessionId } = req.query;
  if (!sessionId || !sessions.has(sessionId)) {
    return res.status(400).json({ error: 'Invalid or missing sessionId' });
  }
  const params = new URLSearchParams({
    client_id: process.env.GOOGLE_CLIENT_ID,
    redirect_uri: `${SERVER_URL}/auth/google/callback`,
    response_type: 'code',
    scope: 'openid email profile',
    state: sessionId
  });
  res.redirect(`https://accounts.google.com/o/oauth2/v2/auth?${params}`);
});

app.get('/auth/google/callback', async (req, res) => {
  const { code, state } = req.query;
  if (!state || !sessions.has(state)) {
    return res.status(400).send('Invalid session. Please restart login.');
  }

  try {
    // Exchange code for tokens
    const tokenRes = await axios.post('https://oauth2.googleapis.com/token', {
      code,
      client_id: process.env.GOOGLE_CLIENT_ID,
      client_secret: process.env.GOOGLE_CLIENT_SECRET,
      redirect_uri: `${SERVER_URL}/auth/google/callback`,
      grant_type: 'authorization_code'
    });

    // Get user info
    const userRes = await axios.get('https://www.googleapis.com/oauth2/v3/userinfo', {
      headers: { Authorization: `Bearer ${tokenRes.data.access_token}` }
    });

    const { name, email, picture } = userRes.data;
    sessions.set(state, {
      status: 'done',
      user: { name, email, avatar: picture, provider: 'google' }
    });

    res.redirect(`${SERVER_URL}/auth/success?name=${encodeURIComponent(name)}&provider=google`);
  } catch (err) {
    console.error('[Google callback error]', err.response?.data || err.message);
    sessions.set(state, { status: 'error', user: null });
    res.status(500).send('Google authentication failed. Please try again.');
  }
});

// ─── GitHub OAuth ────────────────────────────────────────────
app.get('/auth/github', (req, res) => {
  const { sessionId } = req.query;
  if (!sessionId || !sessions.has(sessionId)) {
    return res.status(400).json({ error: 'Invalid or missing sessionId' });
  }
  const params = new URLSearchParams({
    client_id: process.env.GITHUB_CLIENT_ID,
    redirect_uri: `${SERVER_URL}/auth/github/callback`,
    scope: 'read:user user:email',
    state: sessionId
  });
  res.redirect(`https://github.com/login/oauth/authorize?${params}`);
});

app.get('/auth/github/callback', async (req, res) => {
  const { code, state } = req.query;
  if (!state || !sessions.has(state)) {
    return res.status(400).send('Invalid session. Please restart login.');
  }

  try {
    // Exchange code for access token
    const tokenRes = await axios.post('https://github.com/login/oauth/access_token', {
      client_id: process.env.GITHUB_CLIENT_ID,
      client_secret: process.env.GITHUB_CLIENT_SECRET,
      code,
      redirect_uri: `${SERVER_URL}/auth/github/callback`
    }, {
      headers: { Accept: 'application/json' }
    });

    const accessToken = tokenRes.data.access_token;

    // Get user info
    const userRes = await axios.get('https://api.github.com/user', {
      headers: { Authorization: `Bearer ${accessToken}` }
    });

    let email = userRes.data.email;

    // If email is private, fetch from /user/emails
    if (!email) {
      try {
        const emailRes = await axios.get('https://api.github.com/user/emails', {
          headers: { Authorization: `Bearer ${accessToken}` }
        });
        const primary = emailRes.data.find(e => e.primary && e.verified);
        if (primary) email = primary.email;
      } catch {
        // Email fetch failed — continue without it
      }
    }

    sessions.set(state, {
      status: 'done',
      user: {
        name: userRes.data.name || userRes.data.login,
        email: email || 'unknown',
        avatar: userRes.data.avatar_url,
        provider: 'github'
      }
    });

    const displayName = userRes.data.name || userRes.data.login;
    res.redirect(`${SERVER_URL}/auth/success?name=${encodeURIComponent(displayName)}&provider=github`);
  } catch (err) {
    console.error('[GitHub callback error]', err.response?.data || err.message);
    sessions.set(state, { status: 'error', user: null });
    res.status(500).send('GitHub authentication failed. Please try again.');
  }
});

// ─── Poll session status ─────────────────────────────────────
app.get('/auth/poll', (req, res) => {
  const { sessionId } = req.query;
  const session = sessions.get(sessionId);
  if (!session) return res.json({ status: 'expired' });
  if (session.status === 'done') {
    sessions.delete(sessionId); // one-time use
    return res.json({ status: 'done', user: session.user });
  }
  res.json({ status: session.status });
});

// ─── Success page ─────────────────────────────────────────
app.get('/auth/success', (req, res) => {
    const { name, provider } = req.query;
    const providerColor = provider === 'google' ? '#4285F4' : provider === 'github' ? '#10b981' : '#6366f1';
    res.send(`
    <!DOCTYPE html>
    <html lang="en">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>VaultFS — Welcome</title>
        <style>
            @import url('https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600&display=swap');
            * { margin: 0; padding: 0; box-sizing: border-box; }
            body {
                font-family: 'Inter', sans-serif;
                background: #000;
                color: #fff;
                height: 100vh;
                display: flex;
                align-items: center;
                justify-content: center;
            }
            .bg {
                position: fixed;
                inset: 0;
                background: radial-gradient(ellipse 80% 50% at 50% -20%, rgba(16,185,129,0.2), transparent);
            }
            .grid {
                position: fixed;
                inset: 0;
                background-image:
                    linear-gradient(rgba(255,255,255,0.03) 1px, transparent 1px),
                    linear-gradient(90deg, rgba(255,255,255,0.03) 1px, transparent 1px);
                background-size: 50px 50px;
            }
            .card {
                position: relative;
                z-index: 10;
                background: rgba(255,255,255,0.04);
                backdrop-filter: blur(40px);
                border: 1px solid rgba(255,255,255,0.1);
                border-radius: 24px;
                padding: 48px 40px;
                width: 100%;
                max-width: 400px;
                text-align: center;
                animation: cardIn 0.5s cubic-bezier(0.16,1,0.3,1) forwards;
            }
            @keyframes cardIn {
                from { opacity: 0; transform: translateY(24px) scale(0.97); }
                to { opacity: 1; transform: translateY(0) scale(1); }
            }
            .check {
                width: 64px;
                height: 64px;
                background: rgba(16,185,129,0.1);
                border: 1px solid rgba(16,185,129,0.3);
                border-radius: 50%;
                display: flex;
                align-items: center;
                justify-content: center;
                font-size: 28px;
                margin: 0 auto 24px;
            }
            h1 {
                font-size: 1.6rem;
                font-weight: 600;
                letter-spacing: -0.03em;
                margin-bottom: 8px;
            }
            .name {
                color: rgba(255,255,255,0.6);
                margin-bottom: 20px;
                font-size: 0.95rem;
            }
            .provider-badge {
                display: inline-block;
                background: rgba(255,255,255,0.05);
                border: 1px solid rgba(255,255,255,0.1);
                padding: 4px 14px;
                border-radius: 20px;
                font-size: 0.8rem;
                color: rgba(255,255,255,0.4);
                margin-bottom: 28px;
            }
            .instruction {
                background: rgba(255,255,255,0.03);
                border: 1px solid rgba(255,255,255,0.06);
                border-radius: 12px;
                padding: 16px;
                font-size: 0.85rem;
                color: rgba(255,255,255,0.4);
                line-height: 1.6;
            }
            .instruction strong {
                color: rgba(255,255,255,0.7);
                display: block;
                margin-bottom: 4px;
            }
        </style>
    </head>
    <body>
        <div class="bg"></div>
        <div class="grid"></div>
        <div class="card">
            <div class="check">\u2713</div>
            <h1>You're in!</h1>
            <p class="name">Welcome, ${name}</p>
            <div class="provider-badge">via ${provider}</div>
            <div class="instruction">
                <strong>Return to your terminal</strong>
                VaultFS is ready to use. You can close this tab.
            </div>
        </div>
    </body>
    </html>
    `);
});

// ─── Error handler ──────────────────────────────────────────
app.use((err, req, res, next) => {
  console.error(`[ERROR] ${err.message}`);
  res.status(500).json({ error: 'Internal server error' });
});

// ─── Start server ────────────────────────────────────────────
app.listen(PORT, () => {
  console.log(`\u2554\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2557`);
  console.log(`\u2551   VaultFS Auth Server v1.0.3         \u2551`);
  console.log(`\u2551   Running on port ${PORT}               \u2551`);
  console.log(`\u2551   Google OAuth: ${process.env.GOOGLE_CLIENT_ID ? '\u2705' : '\u274C'}                 \u2551`);
  console.log(`\u2551   GitHub OAuth: ${process.env.GITHUB_CLIENT_ID ? '\u2705' : '\u274C'}                 \u2551`);
  console.log(`\u255A\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u255D`);
});
