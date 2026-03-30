"use strict";

const TOKEN_KEY = "vg_admin_token";

function getToken() {
    return sessionStorage.getItem(TOKEN_KEY);
}

function setToken(token) {
    sessionStorage.setItem(TOKEN_KEY, token);
}

function clearToken() {
    sessionStorage.removeItem(TOKEN_KEY);
}

function requireAuth() {
    if (!getToken()) {
        location.href = "/admin/index.html";
    }
}

function logout() {
    clearToken();
    location.href = "/admin/index.html";
}

async function adminFetch(url, options = {}) {
    const resp = await fetch(url, {
        ...options,
        headers: {
            "X-Admin-Token": getToken() || "",
            "Content-Type": "application/json",
            ...(options.headers || {}),
        },
    });
    if (resp.status === 401) {
        logout();
        return null;
    }
    return resp;
}

function showAlert(message, type = "danger") {
    const container = document.getElementById("alert-container");
    if (!container) return;
    const div = document.createElement("div");
    div.className = `alert alert-${type} alert-dismissible fade show`;
    div.innerHTML = `${message}<button type="button" class="btn-close" data-bs-dismiss="alert"></button>`;
    container.appendChild(div);
    setTimeout(() => div.remove(), 5000);
}

function buildNav(activePage) {
    const pages = [
        ["Settings", "/admin/settings.html"],
        ["Users", "/admin/users.html"],
        ["Organizations", "/admin/organizations.html"],
        ["Diagnostics", "/admin/diagnostics.html"],
    ];
    return pages.map(([label, href]) =>
        `<li class="nav-item">
          <a class="nav-link${activePage === label ? " active" : ""}" href="${href}">${label}</a>
        </li>`
    ).join("") +
    `<li class="nav-item">
      <a class="nav-link" href="/" target="_blank" rel="noreferrer">Vault</a>
    </li>`;
}

function renderNav(activePage) {
    const navEl = document.getElementById("nav-links");
    if (navEl) navEl.innerHTML = buildNav(activePage);
}
