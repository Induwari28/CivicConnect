# CivicConnect

## Admin status updates

The backend protects the admin issue list and status updates with HTTP Basic authentication. Set both environment variables before starting Spring Boot:

```powershell
$env:ADMIN_USERNAME = "city-admin"
$env:ADMIN_PASSWORD = "<use-a-strong-password>"
```

The frontend keeps credentials in component memory only; the backend loads the admin account into memory with a BCrypt-encoded password. The public issue feed and create endpoints remain available without authentication. Admin-only endpoints are `GET /api/issues/admin` and `PATCH /api/issues/{id}/status`; the PATCH request body must be `{"status":"IN_PROGRESS"}` or `{"status":"RESOLVED"}` (and may also set `PENDING`).

Use HTTPS outside local development because HTTP Basic credentials are sent with each request.

## Issue photo uploads

Report photos are optional JPEG, PNG, GIF, or WebP images, limited to 5 MB. They are stored under `~/.civicconnect/uploads` by default; set `UPLOAD_DIR` to use another local directory. Mount a persistent directory and back it up when deploying. The existing JSON issue endpoint remains supported, and the web form submits multipart data when creating a report.
