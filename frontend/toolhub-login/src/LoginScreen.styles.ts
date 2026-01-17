import type { CSSProperties } from "react";

export const styles: Record<string, CSSProperties> = {
  container: {
    minHeight: "100vh",
    background:
      "radial-gradient(circle at top, #2b2b3a, #0f0f14)",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    color: "#fff",
  },

  card: {
    width: "100%",
    maxWidth: 420,
    padding: "3rem 2.5rem",
    borderRadius: 16,
    background: "rgba(20, 20, 30, 0.95)",
    boxShadow: "0 20px 50px rgba(0,0,0,0.6)",
    textAlign: "center",
  },

  logo: {
    width: 64,
    height: 64,
    marginBottom: 12,
  },

  title: {
    fontSize: "2rem",
    fontWeight: 700,
    marginBottom: 6,
  },

  subtitle: {
    fontSize: "0.95rem",
    color: "#b4b4c6",
    marginBottom: "2rem",
  },

  googleButton: {
    width: "100%",
    padding: "0.85rem 1rem",
    borderRadius: 10,
    border: "none",
    background: "#111",
    color: "#fff",
    fontSize: "1rem",
    fontWeight: 500,
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    gap: 12,
    cursor: "pointer",
    transition: "background 0.2s ease",
  },

  googleIcon: {
    width: 18,
    height: 18,
    background: "#fff",
    borderRadius: "50%",
    padding: 2,
  },

  footer: {
    marginTop: "2rem",
    fontSize: "0.8rem",
    color: "#777",
  },
};
