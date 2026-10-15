# Deployment

Two ways to make Room Booking reachable by other people:

| | Quick tunnel | Server (production) |
|---|---|---|
| Use for | A demo today | A permanent, always-on site |
| Cost | Free | ~$5–6/month VPS (+ ~$10/year domain, optional) |
| Address | Random `https://….trycloudflare.com`, new on every start | Your own domain with HTTPS |
| Runs on | Your PC; down when the PC sleeps or the command stops | The server, 24/7, restarts by itself |

Whichever you use, **always set `ADMIN_PASSWORD`**. The seed password `admin123` is printed in
the README, so anyone could otherwise log in as admin.

---

## A. Quick tunnel from your PC

1. Install cloudflared once (Windows): `winget install Cloudflare.cloudflared`
2. Start the stack with real secrets (Git Bash, from the project root):

   ```bash
   export ADMIN_PASSWORD='choose-a-long-password'      # at least 12 characters
   export JWT_SECRET=$(openssl rand -base64 32)
   DB_PORT=5433 docker compose up -d --build --wait    # DB_PORT only if 5432 is taken
   ```

3. Open the tunnel:

   ```bash
   cloudflared tunnel --url http://localhost:3000
   ```

   It prints `https://<random-words>.trycloudflare.com`. Share that link. Press Ctrl+C to close it.

Changing `JWT_SECRET` logs out everyone who is signed in. That's expected.

---

## B. Production server

The server only needs Docker and the files in this `deploy/` folder. CI builds and publishes the
images to GHCR, so the server just pulls them; it never builds anything.

```
Internet ──► Caddy :80/:443 (HTTPS, auto certificates)
               └──► nginx (website + /api proxy) ──► Spring Boot ──► PostgreSQL
                    (none of these publish ports to the internet)
```

### 1. Rent a server

Any VPS provider works (Hetzner, DigitalOcean, Vultr, ...). Choose **Ubuntu 24.04** with
**2 GB RAM or more**, because Java and PostgreSQL together need about 1 GB. Note the public IP.

### 2. Point a domain at it (optional, needed for HTTPS)

At your domain registrar, add a DNS **A record**: `rooms.example.com → <server IP>`.
Without a domain you can still run over plain HTTP on the IP (`DOMAIN=:80`) to try things out.

### 3. Prepare the server

```bash
ssh root@<server-ip>

# Docker
curl -fsSL https://get.docker.com | sh

# Firewall: SSH and web only. PostgreSQL and the API stay unreachable from outside.
ufw allow OpenSSH && ufw allow 80/tcp && ufw allow 443/tcp && ufw allow 443/udp && ufw --force enable
```

### 4. Copy the deploy files

From your PC (Git Bash, in the project root):

```bash
scp -r deploy root@<server-ip>:/opt/room-booking
```

### 5. Configure secrets

On the server:

```bash
cd /opt/room-booking
cp .env.example .env
openssl rand -base64 32    # run three times: DB_PASSWORD, JWT_SECRET and (or your own) ADMIN_PASSWORD
nano .env                  # fill in DOMAIN and the three secrets
chmod 600 .env
```

`docker compose` refuses to start while any required value is empty.

### 6. Allow the server to pull the images

The images on GHCR are private by default. Pick one:

- **Make them public** (simplest; the images contain no secrets): on GitHub open
  *your profile → Packages → `room_booking-api` → Package settings → Change visibility → Public*,
  and do the same for `room_booking-web`.
- **Or keep them private** and log in on the server with a GitHub
  [personal access token (classic)](https://github.com/settings/tokens) that has only the
  `read:packages` scope:

  ```bash
  echo <token> | docker login ghcr.io -u altynidev --password-stdin
  ```

### 7. Start

```bash
docker compose -f docker-compose.prod.yml up -d --wait
docker compose -f docker-compose.prod.yml ps          # all services should be healthy / running
```

Open `https://rooms.example.com`. The first visit can take a few seconds while Caddy obtains the
certificate. Log in as `admin` with your `ADMIN_PASSWORD`.

---

## Operating it

All commands run in `/opt/room-booking` on the server. `dc` is shorthand for
`docker compose -f docker-compose.prod.yml`. Add `alias dc='docker compose -f docker-compose.prod.yml'`
to `~/.bashrc` to use it.

| Task | Command |
|---|---|
| Update to the newest version from `main` | `dc pull && dc up -d` |
| Roll back / pin a version | set `IMAGE_TAG=sha-abc1234` in `.env`, then `dc up -d` (tags are listed on the GHCR package page) |
| Live logs (JSON) | `dc logs -f app` |
| Health | `dc exec app wget -qO- localhost:8081/actuator/health` |
| Change admin password | edit `ADMIN_PASSWORD` in `.env`, then `dc up -d app` |
| Restart everything | `dc restart` |

Containers restart automatically after crashes and after a server reboot (`restart: unless-stopped`).
Logs are rotated (5 × 10 MB per container).

### Backups

The database lives in the `pgdata` Docker volume. Take a daily dump with cron:

```bash
mkdir -p /opt/backups
crontab -e
# add:
0 3 * * * cd /opt/room-booking && docker compose -f docker-compose.prod.yml exec -T postgres pg_dump -U roombooking roombooking | gzip > /opt/backups/roombooking-$(date +\%F).sql.gz && find /opt/backups -name '*.sql.gz' -mtime +14 -delete
```

Restore a dump into an empty database:

```bash
gunzip -c /opt/backups/roombooking-2026-10-15.sql.gz | dc exec -T postgres psql -U roombooking roombooking
```

Copy the backups off the server from time to time; a backup on the same disk doesn't survive losing the server.

### Never do

- Commit `.env` (it is git-ignored) or paste its values into issues or chats.
- Run `dc down -v` on the server: `-v` deletes the database and the TLS certificates.
- Publish ports `5432`, `8080` or `8081`.
