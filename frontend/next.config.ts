import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // 显式指定 Turbopack root，避免检测到 C:\Users\Lenovo\package-lock.json
  // 后把整个用户主目录当项目根扫描，导致首次编译卡死
  turbopack: {
    root: process.cwd(),
  },
  async rewrites() {
    return [
      {
        // 将前端的 /api 代理到后端的 /api
        source: '/api/:path*',
        destination: 'http://localhost:8080/api/:path*',
      },
    ];
  },
};

export default nextConfig;
