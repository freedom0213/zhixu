# ----- 阶段 1：在容器内安装依赖并构建静态产物 -----
FROM node:20-alpine AS builder

WORKDIR /app

# 国内构建体验更好；如不需要可删除这两行
RUN npm config set registry https://registry.npmmirror.com

# 优先用 lockfile 复现安装；没有则 fallback 到 install
COPY package.json ./
COPY package-lock.json* pnpm-lock.yaml* yarn.lock* ./
RUN if [ -f package-lock.json ]; then \
      npm ci --no-audit --no-fund --prefer-offline; \
    elif [ -f pnpm-lock.yaml ]; then \
      corepack enable && corepack prepare pnpm@latest --activate && pnpm install --frozen-lockfile; \
    elif [ -f yarn.lock ]; then \
      corepack enable && yarn install --frozen-lockfile; \
    else \
      npm install --no-audit --no-fund --prefer-offline; \
    fi

COPY . .

# 复制一个兜底 vite 配置，避免 docker 构建上下文里某些本地文件缺失导致报错
ARG VITE_BUILD_MODE=development
ENV VITE_BUILD_MODE=${VITE_BUILD_MODE}

# 跑 package.json 里的 build 脚本
RUN npm run build

# ----- 阶段 2：纯 nginx serve 构建产物 -----
FROM nginx:1.27-alpine

COPY --from=builder /app/dist/ /usr/share/nginx/html/
RUN chmod -R 777 /usr/share/nginx/html/

CMD ["nginx", "-g", "daemon off;"]
