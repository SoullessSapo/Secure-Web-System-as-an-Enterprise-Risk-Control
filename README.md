# Secure Web System as an Enterprise Risk Control

A small web system that demonstrates identity, authorization and transport-security controls:

- **React (Vite) SPA** that signs users in with **Auth0** and gets an access token for the API.
- **Spring Boot 3 / Java 21** API protected as an **OAuth 2.0 Resource Server** (JWT issuer + audience validation).
- **Apache HTTP Server** reverse proxy with a **Let's Encrypt** certificate on a **Duck DNS** hostname.

**Deployed URL:** https://YOUR-SUBDOMAIN.duckdns.org  <!-- replace once deployed -->

```
Browser
  │ login and access token
  ▼
Auth0 ───────────────► React frontend ── Bearer token ──► Spring Boot API
                                                        │
Internet ── Duck DNS ── Apache + TLS ──────────────────┘
```

## Repository layout

| Path | Contents |
| --- | --- |
| `backend/` | Spring Boot resource server: `SecurityConfig`, `RiskController` (`GET /api/risk-status`), tests |
| `frontend/` | Vite + React SPA using `@auth0/auth0-react` |
| `deploy/systemd/` | `enterprise-risk-api.service` (runs the JAR as `riskapi`, bound to 127.0.0.1) |
| `deploy/apache/` | `enterprise-risk.conf` virtual host (static SPA + `/api/` reverse proxy) |
| `deploy/duckdns/` | `duck.sh` IP updater (token read from a root-only file) |
| `deploy/verify.sh` | Collects evidence for the verification checklist from outside the server |
| `docs/evidence/` | Screenshots / command output for the deliverables |

## 1. Auth0 setup

1. **APIs → Create API**: name `Enterprise Risk Lab API`, identifier `https://enterprise-risk-api`, RS256.
2. In that API → **Permissions**: add `read:risk` – *Read the protected risk status*.
3. **Applications → Create Application → Single Page Application**: `Enterprise Risk Lab Frontend`.
   Set *Allowed Callback URLs*, *Allowed Logout URLs* and *Allowed Web Origins* to
   `http://localhost:5173,https://YOUR-SUBDOMAIN.duckdns.org` (no wildcards).
4. In the SPA → **APIs** → `Enterprise Risk Lab API` → **Edit** → **User-Delegated Access** → grant `read:risk`.
   (Client Access is for machine-to-machine apps and does not authorize a browser login.)
5. **Authentication → Database → Users**: create a test user.

The SPA needs only the domain, client ID and audience. **Never** put a client secret in the frontend.

## 2. Run locally

Backend (port 8080):

```bash
cd backend
AUTH0_ISSUER_URI=https://YOUR_TENANT.us.auth0.com/ ./mvnw spring-boot:run
```

On Windows PowerShell set `$env:AUTH0_ISSUER_URI="https://YOUR_TENANT.us.auth0.com/"` and run `.\mvnw.cmd spring-boot:run`.

Configuration is read from environment variables (defaults in `application.properties`):

| Variable | Purpose | Default |
| --- | --- | --- |
| `AUTH0_ISSUER_URI` | Token issuer; must include `https://` and the trailing `/` | `https://YOUR_TENANT.us.auth0.com/` |
| `AUTH0_AUDIENCE` | Required `aud` claim | `https://enterprise-risk-api` |
| `CORS_ALLOWED_ORIGINS` | Exact allowed browser origins | `http://localhost:5173,https://YOUR-SUBDOMAIN.duckdns.org` |
| `SERVER_ADDRESS` | Bind address (`127.0.0.1` in production) | `0.0.0.0` |

`curl -i http://localhost:8080/api/risk-status` → **401 Unauthorized** with `WWW-Authenticate: Bearer` (expected).

Frontend (port 5173):

```bash
cd frontend
cp .env.example .env.local   # then fill in your Auth0 domain and client ID
npm install
npm run dev
```

Open http://localhost:5173, log in, and click **Call protected API**.

Tests: `cd backend && ./mvnw test` covers 401 without a token, 200 with a valid JWT, public health,
deny-by-default for other paths and CORS origin restriction.

## 3. Deploy (Ubuntu server)

**Server hardening first:** change default credentials, use a non-root sudo user with SSH keys, enable a firewall
that allows only SSH, 80 and 443 (`sudo ufw allow OpenSSH && sudo ufw allow 80,443/tcp && sudo ufw enable`),
and open only TCP 80/443 at the cloud provider or router. **Do not expose 8080.**

1. **Duck DNS**: create `YOUR-SUBDOMAIN.duckdns.org`, install `deploy/duckdns/duck.sh` as described in its header,
   and confirm `nslookup YOUR-SUBDOMAIN.duckdns.org` returns the server's public IP.
2. **Packages**: `sudo apt update && sudo apt install -y apache2 certbot python3-certbot-apache openjdk-21-jre`
3. **Build on a trusted machine**:
   - `cd backend && ./mvnw clean package` → `target/enterprise-risk-api.jar`
   - `cd frontend`, create `.env.production.local` with `VITE_API_URL=https://YOUR-SUBDOMAIN.duckdns.org`
     (plus the Auth0 values), then `npm run build` → `dist/`
   - Copy both to the server with `scp`/`rsync` over SSH.
4. **Backend service**:
   ```bash
   sudo mkdir -p /opt/enterprise-risk-api
   sudo cp enterprise-risk-api.jar /opt/enterprise-risk-api/app.jar
   sudo useradd --system --no-create-home --shell /usr/sbin/nologin riskapi
   sudo chown -R riskapi:riskapi /opt/enterprise-risk-api
   sudo cp deploy/systemd/enterprise-risk-api.service /etc/systemd/system/   # edit tenant + hostname first
   sudo systemctl daemon-reload
   sudo systemctl enable --now enterprise-risk-api
   curl http://127.0.0.1:8080/actuator/health          # {"status":"UP"}
   sudo ss -ltnp | grep 8080                           # must show 127.0.0.1:8080, not 0.0.0.0
   ```
5. **Apache + HTTPS**:
   ```bash
   sudo mkdir -p /var/www/enterprise-risk-frontend
   sudo cp -r dist/* /var/www/enterprise-risk-frontend/
   sudo a2enmod ssl proxy proxy_http rewrite headers
   sudo cp deploy/apache/enterprise-risk.conf /etc/apache2/sites-available/   # edit ServerName first
   sudo a2ensite enterprise-risk.conf && sudo a2dissite 000-default.conf
   sudo apachectl configtest && sudo systemctl reload apache2
   sudo certbot --apache -d YOUR-SUBDOMAIN.duckdns.org --redirect
   sudo certbot renew --dry-run
   ```
6. In Auth0, make sure the HTTPS address is in Callback URLs, Logout URLs and Web Origins.

## 4. Verification checklist

Run `sh deploy/verify.sh YOUR-SUBDOMAIN.duckdns.org` from a machine outside the server and save the output to
`docs/evidence/`.

- [ ] Unauthenticated requests to `/api/risk-status` return 401.
- [ ] A logged-in user can call `/api/risk-status` from the frontend. → `docs/evidence/authenticated-call.png`
- [ ] The backend validates the JWT issuer and audience.
- [ ] The application is available at `https://YOUR-SUBDOMAIN.duckdns.org`.
- [ ] The browser shows a valid certificate and HTTPS lock icon. → `docs/evidence/certificate.png` or `verify.sh` output
- [ ] Port 8080 is not reachable from the public internet.
- [ ] `sudo certbot renew --dry-run` succeeds.

## 5. Risk-control reflection

**Authentication vs. authorization.** Authentication proves *who* the user is (Auth0 login). Authorization
decides *what* that identity may do (the API accepts only valid tokens issued for its audience; `read:risk`
models a specific allowed action).

| Control | Risk reduced | Evidence |
| --- | --- | --- |
| Auth0 authentication | Impersonation and weak credential handling | Auth0 login flow and user identity |
| JWT validation at the API | Unauthorized API access | 401 without a valid token |
| Audience validation | Token reuse against another API | API accepts only `aud=https://enterprise-risk-api` |
| HTTPS with a valid certificate | Credential or token exposure in transit | Browser certificate details / `openssl` output |
| Apache reverse proxy, app bound to 127.0.0.1 | Direct exposure of the application port | Only ports 80 and 443 are public |
| Restricted CORS | Other websites calling the API from a user's browser | Requests from unlisted origins are rejected (403) |
| Central service logs (`AUDIT` logger) | Delayed incident investigation | `journalctl -u enterprise-risk-api` shows subject of each access |
| systemd sandboxing, dedicated `riskapi` user | Impact of a compromised process | Unit file hardening directives |

## Security notes

- Never commit `.env.local`, `.env.production.local`, server credentials, Auth0 secrets or Duck DNS tokens (see `.gitignore`).
- The SPA uses the Auth0 client ID only, never a client secret.
- CORS is restricted to exact development and production origins.
- Access tokens are never displayed, logged or stored manually; the API logs only the token subject.
- Use least privilege for server accounts and apply OS updates regularly (`unattended-upgrades`).

## References

- [Auth0 React quickstart](https://auth0.com/docs/quickstart/spa/react)
- [Spring Security JWT resource server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)
- [Certbot documentation](https://eff-certbot.readthedocs.io/)
