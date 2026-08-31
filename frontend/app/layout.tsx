import type { Metadata } from "next";
import { Toaster } from "sonner";
import "./globals.css";
import Providers from "./providers";
import { AuthProvider } from "@/lib/auth-context";
import AppShell from "@/components/AppShell";

export const metadata: Metadata = {
  title: "农批履约中台",
  description: "Lot Management & Traceability System",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="zh-CN" className="font-sans">
      <body className="antialiased">
        <Providers>
          <AuthProvider>
            <AppShell>
              <div className="min-h-screen">
                {children}
              </div>
            </AppShell>
            <Toaster position="top-right" richColors closeButton />
          </AuthProvider>
        </Providers>
      </body>
    </html>
  );
}