import axios from "axios";
import { cerrarSesion } from "./auth.js";

export const API = import.meta.env.VITE_API_URL || "http://localhost:8080/api";
export const api = axios.create({ baseURL: API });

// Adjunta el token JWT a cada petición
api.interceptors.request.use((config) => {
  const token = localStorage.getItem("token");
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// 401 = token vencido o inválido: vuelve al login (un 403 solo significa "sin permiso")
api.interceptors.response.use(
  (r) => r,
  (err) => {
    const url = err.config?.url || "";
    if (err.response && err.response.status === 401 && !url.startsWith("/auth/")) {
      cerrarSesion();
      if (window.location.pathname !== "/login") window.location.href = "/login";
    }
    return Promise.reject(err);
  }
);
