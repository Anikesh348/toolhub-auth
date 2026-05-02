import type { CSSProperties } from "react";

export const styles: Record<string, CSSProperties> = {
  container: {
    minHeight: "100vh",
    background: "#070a12",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    color: "#f8fafc",
    overflowX: "hidden",
    overflowY: "auto",
    padding: "clamp(1.25rem, 4vw, 3rem)",
    position: "relative",
  },

  ambient: {
    position: "absolute",
    inset: 0,
    background:
      "radial-gradient(circle at 18% 20%, rgba(56, 189, 248, 0.18), transparent 32%), radial-gradient(circle at 88% 14%, rgba(168, 85, 247, 0.20), transparent 30%), linear-gradient(135deg, #080b12 0%, #0e1726 46%, #111827 100%)",
    pointerEvents: "none",
  },

  shell: {
    position: "relative",
    width: "min(100%, 980px)",
    minHeight: 560,
    display: "grid",
    gridTemplateColumns: "repeat(auto-fit, minmax(min(100%, 340px), 1fr))",
    border: "1px solid rgba(148, 163, 184, 0.22)",
    borderRadius: 28,
    overflow: "hidden",
    background: "rgba(15, 23, 42, 0.72)",
    boxShadow: "0 28px 80px rgba(0, 0, 0, 0.42)",
  },

  brandPanel: {
    display: "flex",
    flexDirection: "column",
    justifyContent: "flex-end",
    gap: 18,
    padding: "clamp(2rem, 5vw, 3.5rem)",
    background:
      "linear-gradient(145deg, rgba(14, 165, 233, 0.18), rgba(124, 58, 237, 0.22)), linear-gradient(180deg, rgba(15, 23, 42, 0.16), rgba(15, 23, 42, 0.82))",
  },

  brandTopline: {
    width: "fit-content",
    border: "1px solid rgba(226, 232, 240, 0.18)",
    borderRadius: 999,
    color: "#c4b5fd",
    fontSize: "0.78rem",
    fontWeight: 700,
    padding: "0.4rem 0.72rem",
    textTransform: "uppercase",
  },

  heroTitle: {
    maxWidth: 460,
    margin: 0,
    color: "#ffffff",
    fontSize: "2.75rem",
    lineHeight: 1.02,
    fontWeight: 800,
  },

  heroCopy: {
    maxWidth: 440,
    margin: 0,
    color: "#cbd5e1",
    fontSize: "1rem",
    lineHeight: 1.7,
  },

  card: {
    display: "flex",
    flexDirection: "column",
    justifyContent: "center",
    padding: "clamp(2rem, 5vw, 3.5rem)",
    background: "rgba(2, 6, 23, 0.72)",
    backdropFilter: "blur(18px)",
  },

  logo: {
    width: "min(100%, 232px)",
    height: "auto",
    marginBottom: 36,
    objectFit: "contain",
  },

  cardHeader: {
    marginBottom: 28,
  },

  kicker: {
    margin: "0 0 0.5rem",
    color: "#38bdf8",
    fontSize: "0.8rem",
    fontWeight: 700,
    letterSpacing: 0,
    textTransform: "uppercase",
  },

  title: {
    color: "#ffffff",
    fontSize: "2rem",
    lineHeight: 1.1,
    fontWeight: 800,
    margin: 0,
  },

  subtitle: {
    margin: "0.7rem 0 0",
    fontSize: "1rem",
    color: "#94a3b8",
  },

  googleButton: {
    width: "100%",
    minHeight: 52,
    padding: "0.85rem 1rem",
    borderRadius: 14,
    border: "1px solid rgba(226, 232, 240, 0.18)",
    background: "#ffffff",
    color: "#0f172a",
    fontSize: "1rem",
    fontWeight: 700,
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    gap: 12,
    cursor: "pointer",
    boxShadow: "0 14px 30px rgba(0, 0, 0, 0.24)",
  },

  googleIcon: {
    width: 20,
    height: 20,
    background: "#fff",
    borderRadius: "50%",
  },

  footer: {
    margin: "1.5rem 0 0",
    fontSize: "0.84rem",
    color: "#64748b",
    textAlign: "center",
  },

  statusCard: {
    position: "relative",
    width: "min(100%, 420px)",
    border: "1px solid rgba(148, 163, 184, 0.22)",
    borderRadius: 24,
    background: "rgba(2, 6, 23, 0.76)",
    boxShadow: "0 28px 80px rgba(0, 0, 0, 0.42)",
    padding: "2.5rem",
    textAlign: "center",
  },

  statusLogo: {
    width: "min(100%, 220px)",
    height: "auto",
    objectFit: "contain",
    marginBottom: 28,
  },

  statusTitle: {
    margin: 0,
    color: "#ffffff",
    fontSize: "1.75rem",
    lineHeight: 1.15,
    fontWeight: 800,
  },

  statusMessage: {
    margin: "0.9rem 0 0",
    color: "#94a3b8",
    fontSize: "1rem",
    lineHeight: 1.65,
  },
};
