# Hướng dẫn cài đặt toolchain — Genealogy Platform

Tài liệu này hướng dẫn cài đặt **mọi công cụ** cần thiết để chạy
validation gates (E1, E2.1, E2.2, …) trên một máy macOS mới, không
cần `sudo`, không cần Homebrew. Mỗi bước có số thứ tự — chạy đúng
trình tự, mỗi bước có thể verify trước khi đi tiếp.

## 0. Điều kiện tiên quyết

- macOS (Apple Silicon hoặc Intel), shell `zsh` (mặc định từ
  macOS Catalina).
- Cổng Internet ra ngoài (curl + git qua `raw.githubusercontent.com`).
- ~500 MB ổ đĩa trống (Node 22 + pnpm + Helm + Kong image).
- Docker Desktop / OrbStack / colima — chỉ cần cho smoke test
  E2.2 (bước 7).

Bạn **không cần** Homebrew, không cần `sudo`, không cần tài khoản
admin.

## 1. Cài nvm (Node Version Manager)

`nvm` cho phép cài nhiều phiên bản Node song song, install vào
`~/.nvm` (user-writable).

```sh
curl -fsSL https://raw.githubusercontent.com/nvm-sh/nvm/v0.40.1/install.sh | bash
```

Script sẽ tự append vào `~/.zshrc`:

```sh
export NVM_DIR="$HOME/.nvm"
[ -s "$NVM_DIR/nvm.sh" ] && \. "$NVM_DIR/nvm.sh"
```

Source lại shell hoặc mở terminal mới:

```sh
source ~/.zshrc
command -v nvm && nvm --version
# kỳ vọng: nvm 0.40.1
```

Nếu `command -v nvm` không ra (zsh không tự source), chạy thủ
công trong cùng shell:

```sh
export NVM_DIR="$HOME/.nvm"
[ -s "$NVM_DIR/nvm.sh" ] && \. "$NVM_DIR/nvm.sh"
```

## 2. Cài Node 22 LTS

```sh
nvm install 22
nvm use 22
nvm alias default 22
node --version    # v22.x.x
npm --version     # 10.x.x
```

NVM tự set `default` cho shell mới, không cần export PATH.

## 3. Bật pnpm qua corepack

Corepack đi kèm với Node 22 — quản lý package manager version
theo `package.json` `packageManager` field.

```sh
corepack enable
corepack prepare pnpm@9.12.0 --activate
pnpm --version    # 9.12.0
```

Repo này pin `pnpm@9.12.0` trong `package.json` (`packageManager`),
nên pnpm 9.12.0 là bắt buộc.

## 4. Cài Helm 3.16 (binary)

Tải tarball chính thức từ `get.helm.sh`, giải nén vào
`~/.local/bin` (user-writable, không cần sudo).

```sh
mkdir -p ~/.local/bin
curl -fsSL https://get.helm.sh/helm-v3.16.3-darwin-arm64.tar.gz \
  -o /tmp/helm.tar.gz
tar -xzf /tmp/helm.tar.gz -C /tmp/
mv /tmp/darwin-arm64/helm ~/.local/bin/helm
chmod +x ~/.local/bin/helm
```

Append `~/.local/bin` vào PATH trong `~/.zshrc`:

```sh
echo 'export PATH="$HOME/.local/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc
helm version    # version.BuildInfo{Version:"v3.16.3", ...}
# nếu vẫn lỗi 'accepts no arguments' thì Helm cũ hơn (≤ 3.10) chưa
# hỗ trợ --short positional; cài lại bản 3.16.3 như script trên.
```

> Nếu dùng Intel Mac, thay `darwin-arm64` bằng `darwin-amd64`.

## 5. Cài Docker (chỉ cho E2.2 smoke)

Cài một trong ba:

- **Docker Desktop** — `https://www.docker.com/products/docker-desktop/`
  (kéo .dmg, kéo vào Applications).
- **OrbStack** — `https://orbstack.dev/` (nhẹ hơn, native Apple Silicon).
- **colima** — `brew install colima docker` (nhưng đòi Homebrew).

Verify:

```sh
docker --version
docker info | head -5
docker run --rm hello-world
```

## 6. Verify toolchain

Mở terminal mới và chạy:

```sh
node --version       # v22.x.x
npm --version        # 10.x.x
pnpm --version       # 9.12.0
helm version         # version.BuildInfo{Version:"v3.16.3", ...}
docker --version     # 29.x.x
python3 --version    # 3.x.x
git --version        # 2.x.x
```

## 7. Cài dependencies + chạy validation gates

```sh
cd /path/to/genealogy-platform
pnpm install          # cài theo pnpm-lock.yaml (không --frozen-lockfile
                      # nếu vừa thêm devDep mới)
```

Chạy lần lượt các gate của E2.1 + E2.2:

```sh
# E2.1 — cluster baseline
pnpm check:platform:baseline
node --test scripts/__tests__/check-platform-baseline.test.mjs

# E2.2 — Kong runtime
pnpm lint:kong
node --test scripts/__tests__/lint-kong-config.test.mjs

# Helm
pnpm lint:helm
helm lint platform/helm/genealogy-platform --strict

# Ownership + lockfile
pnpm check:ownership
pnpm check:lockfile

# Toàn bộ script tests
pnpm test:scripts
```

Kết quả kỳ vọng (E2.1 + E2.2 PASS):

```
[baseline] clean — namespaces=8, envs=3, versions=13
[kong] clean — services=4, routes=4, plugins=14
[helm] clean — 1 chart(s)
1 chart(s) linted, 0 chart(s) failed
[ownership] clean — 32 declared paths, all per-directory OWNERS present
[lockfile] all required lockfiles present
# tests 24
# pass 24
# fail 0
```

## 8. Smoke test Kong (E2.2) end-to-end

Tạo self-signed cert, chạy Kong 3.8.0 trong Docker, verify edge
contract:

```sh
# 1. Generate self-signed cert (1-day). Production dùng cert-manager.
openssl req -new -x509 -days 1 -nodes \
  -out /tmp/cert.pem -keyout /tmp/key.pem \
  -subj "/CN=genealogy.local"

# 2. Run Kong 3.8.0 DB-less, mount kong.yml + cert
docker run -d --rm --name gp-kong-smoke \
  -p 8000:8000 -p 8443:8443 -p 8100:8100 \
  -v "$PWD/platform/helm/genealogy-platform/files/kong.yml:/etc/kong/kong.yml:ro" \
  -v /tmp/cert.pem:/tmp/cert.pem:ro \
  -v /tmp/key.pem:/tmp/key.pem:ro \
  -e KONG_DATABASE=off \
  -e KONG_DECLARATIVE_CONFIG=/etc/kong/kong.yml \
  -e KONG_PROXY_LISTEN="0.0.0.0:8000" \
  -e KONG_PROXY_LISTEN_SSL="0.0.0.0:8443 ssl" \
  -e KONG_ADMIN_LISTEN="127.0.0.1:8444 ssl" \
  -e KONG_STATUS_LISTEN="0.0.0.0:8100" \
  -e KONG_SSL_CERT=/tmp/cert.pem \
  -e KONG_SSL_CERT_KEY=/tmp/key.pem \
  -e KONG_PLUGINS="bundled,correlation-id,cors,request-size-limiting,rate-limiting,ip-restriction,jwt,prometheus" \
  kong:3.8.0

# 3. Wait for ready
for i in 1 2 3 4 5 6 7 8 9 10; do
  sleep 2
  curl -fsS http://127.0.0.1:8100/status > /dev/null 2>&1 && { echo "READY ${i}"; break; }
done

# 4. Run smoke test
KONG_PROXY=http://127.0.0.1:8000 \
KONG_STATUS=http://127.0.0.1:8100 \
  pnpm smoke:kong

# 5. Cleanup
docker kill gp-kong-smoke
```

Kết quả kỳ vọng (8/8 PASS):

```
[smoke] PASS — /status is DB-less (no `database` key)
[smoke] PASS — /status reports configuration_hash=…
[smoke] PASS — public route injected X-Request-Id=… (status=426)
[smoke] PASS — admin route matched from 127.0.0.1 (status=426; ip-restriction allow-loophole working)
[smoke] PASS — partner route request-size-limiting cap = 8 MB (config)
[smoke] PASS — authenticated route rate-limit = 300/min (config)
[smoke] PASS — plugins rendered = correlation-id, cors, ip-restriction, rate-limiting, request-size-limiting (matches allow-list)
[smoke] PASS — no domain-authorization plugins in config
[smoke] clean — Kong edge contract validated
```

## 9. Persistence (verify sau khi mở shell mới)

Tệp `~/.zshrc` sau khi cài đủ:

```sh
export NVM_DIR="$HOME/.nvm"
[ -s "$NVM_DIR/nvm.sh" ] && \. "$NVM_DIR/nvm.sh"
export PATH="$HOME/.local/bin:$PATH"
```

`nvm` tự source `nvm.sh` cho shell mới qua `~/.zshrc`. Helm ở
`~/.local/bin` được thêm vào PATH.

## 10. Troubleshooting

| Vấn đề | Cách sửa |
| ------ | -------- |
| `nvm: command not found` sau khi cài | `source ~/.zshrc` hoặc mở terminal mới |
| `corepack: command not found` | `nvm install 22 && nvm use 22` (corepack đi kèm Node) |
| `pnpm install` freeze | xoá `node_modules`, `pnpm install` lại |
| `helm: command not found` | `source ~/.zshrc`; PATH đã thêm `~/.local/bin` |
| `docker: Cannot connect to Docker daemon` | mở Docker Desktop / OrbStack |
| `Kong chạy nhưng smoke fail` | xoá `gp-kong-smoke`, restart container; cert path `/tmp/cert.pem` đã tồn tại |
| `port 8000 already in use` | `docker kill $(docker ps -q --filter "publish=8000")` |

## 11. Uninstall

```sh
# Xoa nvm + Node + pnpm
rm -rf ~/.nvm
# Xoá dòng NVM_DIR trong ~/.zshrc

# Xoá Helm
rm -f ~/.local/bin/helm
# Xoá dòng PATH trong ~/.zshrc

# Xoá Kong container + image
docker rm -f gp-kong-smoke
docker rmi kong:3.8.0
```

## 12. Lệnh trong repo đã verify đủ toolchain

Sau khi cài theo tài liệu này, các gate sau PASS (đã chạy trên
host dev xác nhận status DONE cho E2.1 + E2.2):

```sh
pnpm install                    # ~3s, 1 devDep (yaml 2.6.1)
pnpm lint:kong                  # clean
pnpm check:platform:baseline    # clean
pnpm lint:helm                  # clean
helm lint platform/helm/genealogy-platform --strict  # clean
node --test scripts/__tests__/**/*.test.mjs          # 24/24
pnpm check:ownership            # clean
pnpm check:lockfile             # clean
pnpm smoke:kong                 # 8/8 (live Kong 3.8)
```
