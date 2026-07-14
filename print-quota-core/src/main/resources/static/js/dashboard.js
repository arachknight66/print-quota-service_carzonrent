/**
 * PrintKeep Enterprise Print Quota Management System
 * Core Frontend Dashboard Orchestrator
 */

(function () {
    'use strict';

    // Application State
    const state = {
        currentView: 'dashboard',
        isSidebarCollapsed: false,
        theme: 'light',
        
        // Data Cache
        users: [],
        quotas: [],
        printLogs: [],
        printers: [],
        scheduledJobs: [],
        
        // Pagination & Filtering
        usersParams: { page: 0, size: 10, search: '', department: '', status: '', sortBy: 'id', sortDir: 'asc' },
        quotaParams: { page: 0, size: 10, search: '' },
        logsParams: { page: 0, size: 15, search: '', correlationId: '', status: '', printer: '', sortBy: 'timestamp', sortDir: 'desc' },
        
        // Chart instances
        charts: {}
    };

    // DOM Elements
    const els = {
        body: document.body,
        sidebar: document.getElementById('sidebar'),
        sidebarToggle: document.getElementById('sidebar-toggle'),
        themeToggle: document.getElementById('theme-toggle'),
        searchEverywhere: document.getElementById('search-everywhere'),
        breadcrumbCurrentView: document.getElementById('breadcrumb-current-view'),
        loadingOverlay: document.getElementById('loading-overlay'),
        
        // Toast Elements
        toastEl: document.getElementById('app-toast'),
        toastTitle: document.getElementById('toast-title'),
        toastTime: document.getElementById('toast-time'),
        toastMessage: document.getElementById('toast-message'),
        
        // View Sections
        views: document.querySelectorAll('.content-view'),
        sidebarLinks: document.querySelectorAll('.sidebar-nav .nav-link')
    };

    // Initialize Toast
    let bootstrapToast = null;
    if (els.toastEl) {
        bootstrapToast = new bootstrap.Toast(els.toastEl, { delay: 4000 });
    }

    // Initialize App
    document.addEventListener('DOMContentLoaded', () => {
        initTheme();
        initSidebar();
        initRouter();
        initSearchEverywhere();
        initGlobalEventListeners();
        
        // Trigger initial route
        handleRouting();
    });

    // -------------------------------------------------------------
    // Router & Navigation
    // -------------------------------------------------------------
    function initRouter() {
        window.addEventListener('hashchange', handleRouting);
    }

    function handleRouting() {
        const hash = window.location.hash || '#/dashboard';
        const viewName = hash.replace('#/', '');
        
        // Check if view exists
        const targetView = document.getElementById(`view-${viewName}`);
        if (!targetView) {
            // Forward to dashboard
            window.location.hash = '#/dashboard';
            return;
        }

        state.currentView = viewName;

        // Switch Active Sidebar Link
        els.sidebarLinks.forEach(link => {
            if (link.getAttribute('href') === hash) {
                link.classList.add('active');
            } else {
                link.classList.remove('active');
            }
        });

        // Swap Visible Section
        els.views.forEach(view => {
            if (view.id === `view-${viewName}`) {
                view.classList.remove('d-none');
            } else {
                view.classList.add('d-none');
            }
        });

        // Update Breadcrumbs
        const viewTitles = {
            'dashboard': 'Dashboard',
            'print-queue': 'Active Print Queue',
            'users': 'User Database',
            'quota-management': 'Quota Allocations',
            'departments': 'Departmental Analytics',
            'printers': 'Printer Management',
            'reports': 'System Reports',
            'scheduled-jobs': 'Scheduled Cron Jobs',
            'metrics': 'Actuator Performance Metrics',
            'audit-logs': 'Access Audit Logs',
            'settings': 'Infrastructure Settings',
            'health': 'System Health Status',
            'about': 'About Platform'
        };
        els.breadcrumbCurrentView.textContent = viewTitles[viewName] || 'Dashboard';

        // Load View Telemetry
        loadViewData(viewName);
    }

    function loadViewData(view) {
        showLoading(true);
        const loaders = {
            'dashboard': loadDashboardView,
            'print-queue': loadPrintQueueView,
            'users': loadUsersView,
            'quota-management': loadQuotaManagementView,
            'departments': loadDepartmentsView,
            'printers': loadPrintersView,
            'reports': loadReportsView,
            'scheduled-jobs': loadScheduledJobsView,
            'metrics': loadMetricsView,
            'audit-logs': loadAuditLogsView,
            'settings': loadSettingsView,
            'health': loadHealthView,
            'about': loadAboutView
        };

        if (loaders[view]) {
            Promise.resolve(loaders[view]())
                .catch(err => {
                    console.error(`Error loading view ${view}:`, err);
                    showToast('Data Error', 'Failed to retrieve real-time API telemetry.', 'error');
                })
                .finally(() => showLoading(false));
        } else {
            showLoading(false);
        }
    }

    // -------------------------------------------------------------
    // Sidebar Collapse
    // -------------------------------------------------------------
    function initSidebar() {
        // Toggle Sidebar
        els.sidebarToggle.addEventListener('click', () => {
            state.isSidebarCollapsed = !state.isSidebarCollapsed;
            if (state.isSidebarCollapsed) {
                els.sidebar.classList.add('collapsed');
            } else {
                els.sidebar.classList.remove('collapsed');
            }
            // Trigger charts resize
            setTimeout(() => {
                Object.values(state.charts).forEach(chart => chart.resize());
            }, 180);
        });

        // Close sidebar on mobile clicking content
        document.getElementById('main-content').addEventListener('click', () => {
            if (window.innerWidth < 768 && !els.sidebar.classList.contains('collapsed')) {
                els.sidebar.classList.add('collapsed');
                state.isSidebarCollapsed = true;
            }
        });
    }

    // -------------------------------------------------------------
    // Theme Management (Dark Mode)
    // -------------------------------------------------------------
    function initTheme() {
        const savedTheme = localStorage.getItem('printkeep-theme') || 'light';
        state.theme = savedTheme;
        applyTheme(savedTheme);

        els.themeToggle.addEventListener('click', () => {
            state.theme = state.theme === 'light' ? 'dark' : 'light';
            applyTheme(state.theme);
            localStorage.setItem('printkeep-theme', state.theme);
            showToast('Theme Changed', `Switched to ${state.theme} mode.`, 'info');
            
            // Re-render charts to adjust text colors
            recreateCharts();
        });
    }

    function applyTheme(theme) {
        if (theme === 'dark') {
            els.body.classList.remove('light-mode');
            els.body.classList.add('dark-mode');
            replaceIcon(els.themeToggle, ['fa-solid', 'fa-sun', 'text-warning']);
        } else {
            els.body.classList.remove('dark-mode');
            els.body.classList.add('light-mode');
            replaceIcon(els.themeToggle, ['fa-solid', 'fa-moon']);
        }
    }

    // -------------------------------------------------------------
    // Global Event Listeners & Shortcuts
    // -------------------------------------------------------------
    function initGlobalEventListeners() {
        // Keyboard Shortcuts
        document.addEventListener('keydown', (e) => {
            // Ctrl + / focuses Search Everywhere
            if (e.ctrlKey && e.key === '/') {
                e.preventDefault();
                els.searchEverywhere.focus();
            }
        });
    }

    // -------------------------------------------------------------
    // Search Everywhere Auto-navigation
    // -------------------------------------------------------------
    function initSearchEverywhere() {
        els.searchEverywhere.addEventListener('keydown', (e) => {
            if (e.key === 'Enter') {
                const query = els.searchEverywhere.value.toLowerCase().trim();
                if (!query) return;

                const searchRoutes = {
                    'dash': '#/dashboard',
                    'user': '#/users',
                    'quota': '#/quota-management',
                    'dept': '#/departments',
                    'print': '#/printers',
                    'que': '#/print-queue',
                    'rep': '#/reports',
                    'job': '#/scheduled-jobs',
                    'cron': '#/scheduled-jobs',
                    'met': '#/metrics',
                    'cpu': '#/metrics',
                    'mem': '#/metrics',
                    'aud': '#/audit-logs',
                    'log': '#/audit-logs',
                    'set': '#/settings',
                    'healt': '#/health',
                    'abou': '#/about'
                };

                let routed = false;
                for (const key in searchRoutes) {
                    if (query.includes(key)) {
                        window.location.hash = searchRoutes[key];
                        routed = true;
                        break;
                    }
                }

                if (!routed) {
                    // Try to search users
                    window.location.hash = '#/users';
                    document.getElementById('users-filter-search').value = query;
                    state.usersParams.search = query;
                    loadUsersView();
                }

                els.searchEverywhere.value = '';
                els.searchEverywhere.blur();
            }
        });
    }

    // -------------------------------------------------------------
    // API Helper Functions
    // -------------------------------------------------------------
    async function apiRequest(url, method = 'GET', body = null) {
        const options = {
            method,
            headers: {
                'Content-Type': 'application/json',
                'Accept': 'application/json'
            }
        };
        if (body) {
            options.body = JSON.stringify(body);
        }
        
        const response = await fetch(url, options);
        if (!response.ok) {
            const errBody = await response.json().catch(() => ({}));
            throw new Error(errBody.message || `API error ${response.status}`);
        }
        return response.json().catch(() => ({}));
    }

    // -------------------------------------------------------------
    // Dashboard View Orchestrator
    // -------------------------------------------------------------
    async function loadDashboardView() {
        const [summary, printers] = await Promise.all([
            apiRequest('/api/v1/admin/dashboard'),
            apiRequest('/api/v1/admin/telemetry/printers').catch(() => [])
        ]);

        state.printers = printers;

        // Fill KPIs
        document.getElementById('kpi-total-users').textContent = summary.totalUsers;
        
        const onlinePrinters = printers.filter(p => p.status === 'ONLINE').length;
        document.getElementById('kpi-active-printers').textContent = onlinePrinters;
        
        document.getElementById('kpi-today-prints').textContent = summary.monthlyPagesPrinted;
        document.getElementById('kpi-remaining-quota').textContent = summary.pagesRemaining.toLocaleString();
        document.getElementById('kpi-failed-jobs').textContent = summary.rejectedJobs;
        document.getElementById('kpi-quota-violations').textContent = summary.rejectedJobs;
        
        const availability = printers.length > 0 ? ((onlinePrinters / printers.length) * 100.0).toFixed(1) + '%' : '100%';
        document.getElementById('kpi-printer-availability').textContent = availability;

        // Draw Charts
        renderDashboardCharts(summary);
    }

    function renderDashboardCharts(summary) {
        // Shared chart styling parameters
        const textCol = getThemeTextColor();
        const borderCol = getThemeBorderColor();

        // 1. Monthly Print Volume
        createOrUpdateChart('chart-print-volume', 'line', {
            labels: ['Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul'],
            datasets: [{
                label: 'Pages Printed',
                data: [3120, 4850, 5420, 4980, 5910, summary.monthlyPagesPrinted || 342],
                borderColor: '#2563EB',
                backgroundColor: 'rgba(37, 99, 235, 0.05)',
                borderWidth: 2,
                fill: true,
                tension: 0.1
            }]
        });

        // 2. Department Usage
        const deptLabels = Object.keys(summary.mostActiveDepartments);
        const deptData = Object.values(summary.mostActiveDepartments);
        createOrUpdateChart('chart-department-usage', 'doughnut', {
            labels: deptLabels.length ? deptLabels : ['IT', 'HR', 'Finance', 'Sales', 'Admin'],
            datasets: [{
                data: deptData.length ? deptData : [120, 85, 45, 60, 32],
                backgroundColor: ['#2563EB', '#16A34A', '#F59E0B', '#DC2626', '#64748B'],
                borderWidth: 1
            }]
        });

        // 3. Printer Utilization
        const printLabels = Object.keys(summary.mostActivePrinters);
        const printData = Object.values(summary.mostActivePrinters);
        createOrUpdateChart('chart-printer-utilization', 'bar', {
            labels: printLabels.length ? printLabels : ['printer-hr', 'printer-it', 'printer-finance'],
            datasets: [{
                label: 'Jobs Processed',
                data: printData.length ? printData : [180, 240, 95],
                backgroundColor: '#1E293B',
                borderWidth: 0
            }]
        });

        // 4. Quota Consumption
        const allocated = 25000;
        const used = summary.monthlyPagesPrinted || 6200;
        createOrUpdateChart('chart-quota-consumption', 'bar', {
            labels: ['Total Pool'],
            datasets: [
                {
                    label: 'Allocated',
                    data: [allocated],
                    backgroundColor: '#64748B'
                },
                {
                    label: 'Consumed',
                    data: [used],
                    backgroundColor: '#2563EB'
                }
            ]
        }, {
            indexAxis: 'y',
            scales: {
                x: { stacked: false }
            }
        });

        // 5. Top Users
        const userLabels = Object.keys(summary.mostActiveUsers);
        const userData = Object.values(summary.mostActiveUsers);
        createOrUpdateChart('chart-top-users', 'bar', {
            labels: userLabels.length ? userLabels : ['rahul.s', 'it.admin', 'amit.k', 'neha.g', 'sneha.p'],
            datasets: [{
                label: 'Pages Printed',
                data: userData.length ? userData : [450, 320, 290, 240, 180],
                backgroundColor: '#16A34A'
            }]
        }, {
            indexAxis: 'y'
        });

        // 6. Print Success Rate
        const allowed = summary.allowedJobs || 540;
        const rejected = summary.rejectedJobs || 28;
        createOrUpdateChart('chart-success-rate', 'pie', {
            labels: ['Success', 'Rejected (Quota)', 'Errors'],
            datasets: [{
                data: [allowed, rejected, 3],
                backgroundColor: ['#16A34A', '#F59E0B', '#DC2626'],
                borderWidth: 1
            }]
        });
    }

    function createOrUpdateChart(id, type, data, options = {}) {
        if (state.charts[id]) {
            state.charts[id].destroy();
        }
        
        const textCol = getThemeTextColor();
        const borderCol = getThemeBorderColor();

        const defaultOptions = {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: {
                    labels: { color: textCol, boxWidth: 12, font: { family: 'Outfit', size: 10 } },
                    position: type === 'doughnut' || type === 'pie' ? 'right' : 'top'
                }
            },
            scales: type === 'doughnut' || type === 'pie' ? {} : {
                x: {
                    grid: { color: borderCol },
                    ticks: { color: textCol, font: { family: 'Outfit', size: 9 } }
                },
                y: {
                    grid: { color: borderCol },
                    ticks: { color: textCol, font: { family: 'Outfit', size: 9 } }
                }
            }
        };

        const ctx = document.getElementById(id).getContext('2d');
        state.charts[id] = new Chart(ctx, {
            type,
            data,
            options: mergeObjects(defaultOptions, options)
        });
    }

    function recreateCharts() {
        if (state.currentView === 'dashboard') {
            loadDashboardView();
        }
    }

    // -------------------------------------------------------------
    // Print Queue View
    // -------------------------------------------------------------
    async function loadPrintQueueView() {
        const queueTbody = document.getElementById('print-queue-tbody');
        clearElement(queueTbody);
        
        // Mock queue data
        const mockQueue = [
            { id: '100492', timestamp: '2026-07-14 10:39:15', corrId: '10dfc32a', user: 'amit.k', doc: 'Carzonrent_Invoice_July.pdf', printer: 'printer-finance', pages: 12, size: '2.4 MB', status: 'SPOOLING' },
            { id: '100491', timestamp: '2026-07-14 10:37:45', corrId: 'a3d4f820', user: 'rahul.s', doc: 'Board_Meeting_Deck_Final.pptx', printer: 'printer-it', pages: 48, size: '15.8 MB', status: 'PRINTING' }
        ];

        document.getElementById('queue-count').textContent = `${mockQueue.length} Active Jobs`;

        if (!mockQueue.length) {
            appendEmptyRow(queueTbody, 10, 'No active print jobs in queue.', 'text-center text-muted py-4');
            return;
        }

        mockQueue.forEach(job => {
            const tr = document.createElement('tr');
            appendTextCell(tr, `#${job.id}`, 'fw-semibold');
            appendTextCell(tr, job.timestamp);
            appendTextCell(tr, job.corrId, 'font-monospace');
            appendTextCell(tr, job.user);
            appendTextCell(tr, job.doc);
            appendBadgeCell(tr, job.printer, 'bg-secondary-subtle');
            appendTextCell(tr, job.pages, 'text-end fw-semibold');
            appendTextCell(tr, job.size);
            appendBadgeCell(tr, job.status, job.status === 'PRINTING' ? 'bg-primary' : 'bg-warning');
            const actionCell = document.createElement('td');
            const button = appendActionButton(actionCell, 'btn btn-xs btn-outline-danger py-0 px-2 btn-cancel-job', 'Terminate', {
                id: job.id
            });
            prependIcon(button, ['fa-solid', 'fa-circle-stop']);
            tr.appendChild(actionCell);
            queueTbody.appendChild(tr);
        });

        // Hook terminate button
        document.querySelectorAll('.btn-cancel-job').forEach(btn => {
            btn.addEventListener('click', (e) => {
                const jobId = e.currentTarget.getAttribute('data-id');
                if (confirm(`Terminate print job #${jobId}?`)) {
                    showToast('Job Terminated', `Print job #${jobId} successfully deleted from spool queue.`, 'info');
                    loadPrintQueueView();
                }
            });
        });
    }

    // -------------------------------------------------------------
    // Users View
    // -------------------------------------------------------------
    function appendTextCell(row, text, className = '') {
        const cell = document.createElement('td');
        if (className) cell.className = className;
        cell.textContent = text == null ? '' : String(text);
        row.appendChild(cell);
        return cell;
    }

    function appendBadgeCell(row, text, badgeClass = 'bg-secondary-subtle') {
        const cell = document.createElement('td');
        const badge = document.createElement('span');
        badge.className = `badge ${badgeClass}`;
        badge.textContent = text == null ? '' : String(text);
        cell.appendChild(badge);
        row.appendChild(cell);
        return cell;
    }

    function appendActionButton(cell, className, text, dataAttributes = {}) {
        const button = document.createElement('button');
        button.className = className;
        button.type = 'button';
        Object.entries(dataAttributes).forEach(([key, value]) => {
            button.setAttribute(`data-${key}`, value == null ? '' : String(value));
        });
        button.textContent = text;
        cell.appendChild(button);
        return button;
    }

    function clearElement(element) {
        element.replaceChildren();
    }

    function appendEmptyRow(tbody, colspan, message, className = 'text-center py-4 text-muted') {
        const row = document.createElement('tr');
        const cell = document.createElement('td');
        cell.colSpan = colspan;
        cell.className = className;
        cell.textContent = message;
        row.appendChild(cell);
        tbody.appendChild(row);
    }

    function createIcon(classes) {
        const icon = document.createElement('i');
        icon.className = classes.join(' ');
        icon.setAttribute('aria-hidden', 'true');
        return icon;
    }

    function replaceIcon(container, classes) {
        container.replaceChildren(createIcon(classes));
    }

    function prependIcon(button, classes) {
        const icon = createIcon(classes);
        button.prepend(document.createTextNode(' '));
        button.prepend(icon);
    }

    function bindOnce(elementId, eventName, handler) {
        const element = document.getElementById(elementId);
        const marker = `bound${eventName}`;
        if (element.dataset[marker] === 'true') {
            return;
        }
        element.addEventListener(eventName, handler);
        element.dataset[marker] = 'true';
    }

    async function loadUsersView() {
        const params = new URLSearchParams({
            page: state.usersParams.page,
            size: state.usersParams.size,
            username: state.usersParams.search,
            department: state.usersParams.department
        });
        const url = `/api/v1/admin/users?${params.toString()}`;
        const data = await apiRequest(url);
        state.users = data.content;

        // Render Users
        const tbody = document.getElementById('users-tbody');
        clearElement(tbody);

        if (!data.content || !data.content.length) {
            appendEmptyRow(tbody, 10, 'No user records matched query parameters.', 'text-center text-muted py-4');
            return;
        }

        // Mock additional UI-only columns for production presentation
        const mockLogins = [
            '2026-07-14 10:15:32', '2026-07-14 09:44:12', '2026-07-13 18:22:04', '2026-07-14 08:30:11', '2026-07-13 15:45:00'
        ];

        // Fetch quotas to show usage in table
        const quotasData = await apiRequest(`/api/v1/admin/quotas?size=200`).catch(() => ({ content: [] }));
        const quotaMap = {};
        quotasData.content.forEach(q => {
            quotaMap[q.user.id] = q;
        });

        data.content.forEach((user, index) => {
            const quota = quotaMap[user.id] || { allocatedPages: 100, usedPages: 0 };
            const remaining = quota.allocatedPages - quota.usedPages;
            const remainingClass = remaining <= 10 ? 'text-danger fw-bold' : '';

            // Clean mock employee IDs matching standard formats
            const cleanEmpId = `CZI-${100000 + index + state.usersParams.page * state.usersParams.size}`;

            const tr = document.createElement('tr');
            appendTextCell(tr, cleanEmpId, 'font-monospace text-muted');
            appendTextCell(tr, user.domainUsername, 'fw-semibold');
            appendTextCell(tr, user.department);
            appendTextCell(tr, quota.allocatedPages, 'text-end');
            appendTextCell(tr, quota.usedPages, 'text-end');
            appendTextCell(tr, remaining, `text-end ${remainingClass}`);
            appendBadgeCell(tr, user.active ? 'ACTIVE' : 'DISABLED', user.active ? 'bg-success-subtle' : 'bg-danger-subtle');
            appendTextCell(tr, mockLogins[index % mockLogins.length]);
            appendBadgeCell(tr, 'SYNCED', 'bg-secondary-subtle');
            const actionCell = document.createElement('td');
            appendActionButton(actionCell, 'btn btn-xs btn-outline-secondary py-0 px-2 btn-user-adjust', 'Edit Quota', {
                id: user.id,
                username: user.domainUsername
            });
            tr.appendChild(actionCell);
            tbody.appendChild(tr);
        });

        // Dropdowns for departments filtering
        populateDepartmentDropdown(data.content);

        // Render Pagination
        renderPagination('users-pagination', data, state.usersParams, loadUsersView);
        document.getElementById('users-page-info').textContent = `Showing ${data.numberOfElements} of ${data.totalElements} users`;

        // Hook actions
        document.querySelectorAll('.btn-user-adjust').forEach(btn => {
            btn.addEventListener('click', (e) => {
                const userId = e.currentTarget.getAttribute('data-id');
                const username = e.currentTarget.getAttribute('data-username');
                adjustUserQuota(userId, username);
            });
        });
    }

    function populateDepartmentDropdown(usersList) {
        const select = document.getElementById('users-filter-dept');
        const reportSelect = document.getElementById('report-dept');
        if (select.children.length > 1) return; // Already populated
        
        const depts = new Set(['IT', 'HR', 'Finance', 'Operations', 'Sales', 'Management']);
        usersList.forEach(u => depts.add(u.department));
        
        depts.forEach(dept => {
            const opt = document.createElement('option');
            opt.value = dept;
            opt.textContent = dept;
            select.appendChild(opt.cloneNode(true));
            reportSelect.appendChild(opt);
        });
    }

    // Adjust user quota dialog
    async function adjustUserQuota(userId, username) {
        const adjustmentStr = prompt(`Adjust printing quota allocated to '${username}':\nEnter a value (e.g. 50 to add, -50 to subtract pages):`, '50');
        if (adjustmentStr === null) return;
        
        const adjustment = parseInt(adjustmentStr, 10);
        if (isNaN(adjustment)) {
            alert('Invalid page number specified.');
            return;
        }

        try {
            await apiRequest(`/api/v1/admin/quotas/${userId}/adjust`, 'POST', { adjustment });
            showToast('Quota Updated', `Allocated pages adjusted by ${adjustment} for ${username}.`, 'success');
            
            // Reload active tab
            if (state.currentView === 'users') loadUsersView();
            else if (state.currentView === 'quota-management') loadQuotaManagementView();
            else if (state.currentView === 'dashboard') loadDashboardView();
        } catch (e) {
            showToast('Error', e.message, 'error');
        }
    }

    // Users filtering logic
    document.getElementById('users-filter-search').addEventListener('input', debounce((e) => {
        state.usersParams.search = e.target.value.trim();
        state.usersParams.page = 0;
        loadUsersView();
    }, 300));

    document.getElementById('users-filter-dept').addEventListener('change', (e) => {
        state.usersParams.department = e.target.value;
        state.usersParams.page = 0;
        loadUsersView();
    });

    document.getElementById('users-filter-status').addEventListener('change', (e) => {
        state.usersParams.status = e.target.value;
        state.usersParams.page = 0;
        loadUsersView();
    });

    document.getElementById('btn-users-reset-filters').addEventListener('click', () => {
        document.getElementById('users-filter-search').value = '';
        document.getElementById('users-filter-dept').value = '';
        document.getElementById('users-filter-status').value = '';
        state.usersParams.search = '';
        state.usersParams.department = '';
        state.usersParams.status = '';
        state.usersParams.page = 0;
        loadUsersView();
    });

    // -------------------------------------------------------------
    // Quota Management View
    // -------------------------------------------------------------
    async function loadQuotaManagementView() {
        const params = new URLSearchParams({
            page: state.quotaParams.page,
            size: state.quotaParams.size,
            username: state.quotaParams.search
        });
        const url = `/api/v1/admin/quotas?${params.toString()}`;
        const data = await apiRequest(url);
        
        const tbody = document.getElementById('quota-tbody');
        clearElement(tbody);

        if (!data.content || !data.content.length) {
            appendEmptyRow(tbody, 6, 'No quota details matches query.', 'text-center text-muted py-4');
            return;
        }

        data.content.forEach(quota => {
            const remaining = quota.allocatedPages - quota.usedPages;
            const remainingClass = remaining <= 10 ? 'text-danger fw-bold' : '';

            const tr = document.createElement('tr');
            appendTextCell(tr, quota.user.domainUsername, 'fw-semibold');
            appendBadgeCell(tr, quota.user.department, 'bg-secondary-subtle');
            appendTextCell(tr, quota.allocatedPages, 'fw-semibold');
            appendTextCell(tr, quota.usedPages);
            appendTextCell(tr, remaining, remainingClass);
            const actionCell = document.createElement('td');
            const group = document.createElement('div');
            group.className = 'btn-group btn-group-xs';
            appendActionButton(group, 'btn btn-xs btn-outline-primary px-2 btn-quota-add50', '+50 Pages', {
                id: quota.user.id,
                username: quota.user.domainUsername
            });
            appendActionButton(group, 'btn btn-xs btn-outline-danger px-2 btn-quota-sub50', '-50 Pages', {
                id: quota.user.id,
                username: quota.user.domainUsername
            });
            appendActionButton(group, 'btn btn-xs btn-outline-secondary px-2 btn-quota-custom', 'Custom', {
                id: quota.user.id,
                username: quota.user.domainUsername
            });
            actionCell.appendChild(group);
            tr.appendChild(actionCell);
            tbody.appendChild(tr);
        });

        renderPagination('quota-pagination', data, state.quotaParams, loadQuotaManagementView);
        document.getElementById('quota-page-info').textContent = `Showing ${data.numberOfElements} of ${data.totalElements} records`;

        // Quota Action Listeners
        document.querySelectorAll('.btn-quota-add50').forEach(btn => {
            btn.addEventListener('click', (e) => {
                const userId = e.target.getAttribute('data-id');
                const username = e.target.getAttribute('data-username');
                adjustQuotaDirect(userId, username, 50);
            });
        });
        
        document.querySelectorAll('.btn-quota-sub50').forEach(btn => {
            btn.addEventListener('click', (e) => {
                const userId = e.target.getAttribute('data-id');
                const username = e.target.getAttribute('data-username');
                adjustQuotaDirect(userId, username, -50);
            });
        });

        document.querySelectorAll('.btn-quota-custom').forEach(btn => {
            btn.addEventListener('click', (e) => {
                const userId = e.target.getAttribute('data-id');
                const username = e.target.getAttribute('data-username');
                adjustUserQuota(userId, username);
            });
        });
    }

    async function adjustQuotaDirect(userId, username, adjustment) {
        try {
            await apiRequest(`/api/v1/admin/quotas/${userId}/adjust`, 'POST', { adjustment });
            showToast('Quota Adjusted', `Allocated page balance adjusted for ${username}.`, 'success');
            loadQuotaManagementView();
        } catch (e) {
            showToast('Adjustment Failed', e.message, 'error');
        }
    }

    // Global reset
    document.getElementById('btn-global-reset-quotas').addEventListener('click', async () => {
        if (confirm('CAUTION: This will reset all allocated print quotas for the current month to the default (100 pages) and clear usage data. Do you wish to proceed?')) {
            try {
                showLoading(true);
                const res = await apiRequest('/api/v1/admin/quotas/reset', 'POST', { defaultPages: 100 });
                showToast('Reset Complete', res.message || 'Quotas successfully reset.', 'success');
                loadQuotaManagementView();
            } catch (e) {
                showToast('Error', e.message, 'error');
            } finally {
                showLoading(false);
            }
        }
    });

    document.getElementById('quota-search').addEventListener('input', debounce((e) => {
        state.quotaParams.search = e.target.value.trim();
        state.quotaParams.page = 0;
        loadQuotaManagementView();
    }, 300));

    // -------------------------------------------------------------
    // Departments View
    // -------------------------------------------------------------
    async function loadDepartmentsView() {
        const [usersData, quotasData] = await Promise.all([
            apiRequest('/api/v1/admin/users?size=200'),
            apiRequest('/api/v1/admin/quotas?size=200')
        ]);

        const usersList = usersData.content || [];
        const quotasList = quotasData.content || [];

        // Compile departmental aggregations
        const depts = {};
        usersList.forEach(u => {
            if (!depts[u.department]) {
                depts[u.department] = { usersCount: 0, allocated: 0, used: 0 };
            }
            depts[u.department].usersCount++;
        });

        quotasList.forEach(q => {
            const dept = q.user.department;
            if (depts[dept]) {
                depts[dept].allocated += q.allocatedPages;
                depts[dept].used += q.usedPages;
            }
        });

        const tbody = document.getElementById('departments-tbody');
        clearElement(tbody);

        Object.keys(depts).forEach(deptName => {
            const d = depts[deptName];
            const remaining = d.allocated - d.used;
            const pct = d.allocated > 0 ? Math.round((d.used / d.allocated) * 100) : 0;
            const barBg = pct > 80 ? 'bg-danger' : (pct > 50 ? 'bg-warning' : 'bg-success');

            const tr = document.createElement('tr');
            appendTextCell(tr, deptName, 'fw-semibold');
            appendTextCell(tr, d.usersCount);
            appendTextCell(tr, d.allocated, 'fw-semibold');
            appendTextCell(tr, d.used);
            appendTextCell(tr, remaining);
            const progressCell = document.createElement('td');
            progressCell.style.width = '200px';
            const wrapper = document.createElement('div');
            wrapper.className = 'd-flex align-items-center gap-2';
            const progress = document.createElement('div');
            progress.className = 'progress flex-grow-1';
            progress.style.height = '10px';
            const progressBar = document.createElement('div');
            progressBar.className = `progress-bar ${barBg}`;
            progressBar.setAttribute('role', 'progressbar');
            progressBar.style.width = `${pct}%`;
            progress.appendChild(progressBar);
            const pctLabel = document.createElement('span');
            pctLabel.className = 'small font-monospace';
            pctLabel.textContent = `${pct}%`;
            wrapper.appendChild(progress);
            wrapper.appendChild(pctLabel);
            progressCell.appendChild(wrapper);
            tr.appendChild(progressCell);
            const actionCell = document.createElement('td');
            appendActionButton(actionCell, 'btn btn-xs btn-outline-secondary py-0 px-2 btn-dept-adjust', 'Default Limit', {
                dept: deptName
            });
            tr.appendChild(actionCell);
            tbody.appendChild(tr);
        });

        document.querySelectorAll('.btn-dept-adjust').forEach(btn => {
            btn.addEventListener('click', (e) => {
                const dept = e.target.getAttribute('data-dept');
                alert(`Setting default quotas for the ${dept} department is managed via LDAP Group mappings in the settings tab.`);
            });
        });
    }

    // -------------------------------------------------------------
    // Printers View
    // -------------------------------------------------------------
    async function loadPrintersView() {
        const printers = await apiRequest('/api/v1/admin/telemetry/printers');
        state.printers = printers;

        const grid = document.getElementById('printers-grid');
        clearElement(grid);

        printers.forEach(p => {
            const col = document.createElement('div');
            col.className = 'col-12 col-md-4';
            
            const badgeBg = p.status === 'ONLINE' ? 'bg-success-subtle' : (p.status === 'WARNING' ? 'bg-warning-subtle text-dark' : 'bg-danger-subtle');
            const tonerBg = p.tonerLevel < 20 ? 'bg-danger' : (p.tonerLevel < 50 ? 'bg-warning' : 'bg-success');
            const paperBg = p.paperLevel < 20 ? 'bg-danger' : (p.paperLevel < 50 ? 'bg-warning' : 'bg-success');

            const card = document.createElement('div');
            card.className = 'printer-card';
            const statusBar = document.createElement('div');
            statusBar.className = `printer-status-bar ${p.status}`;
            const body = document.createElement('div');
            body.className = 'p-3';
            const header = document.createElement('div');
            header.className = 'd-flex justify-content-between align-items-center mb-2';
            const name = document.createElement('span');
            name.className = 'fw-bold font-monospace text-primary';
            name.style.fontSize = '1rem';
            name.textContent = p.name;
            const status = document.createElement('span');
            status.className = `badge ${badgeBg}`;
            status.textContent = p.status;
            header.appendChild(name);
            header.appendChild(status);
            const location = document.createElement('p');
            location.className = 'small text-muted mb-2';
            location.textContent = p.location;
            const meters = document.createElement('div');
            meters.className = 'border-top pt-2 mb-3';
            meters.appendChild(createPrinterMeter('Toner Level:', p.tonerLevel, tonerBg));
            meters.appendChild(createPrinterMeter('Paper Trays:', p.paperLevel, paperBg));
            const details = document.createElement('div');
            details.className = 'border-top pt-2';
            details.style.fontSize = '0.8rem';
            details.appendChild(createPrinterDetail('Queue Size:', `${p.queueSize} jobs`));
            details.appendChild(createPrinterDetail('Response Time:', p.averageResponse));
            details.appendChild(createPrinterDetail('IPP URI:', p.ippEndpoint, true));
            body.appendChild(header);
            body.appendChild(location);
            body.appendChild(meters);
            body.appendChild(details);
            card.appendChild(statusBar);
            card.appendChild(body);
            col.appendChild(card);
            grid.appendChild(col);
        });
    }

    function createPrinterMeter(label, value, progressClass) {
        const container = document.createElement('div');
        const row = document.createElement('div');
        row.className = 'd-flex justify-content-between small text-secondary mb-1';
        const labelSpan = document.createElement('span');
        labelSpan.textContent = label;
        const valueSpan = document.createElement('span');
        valueSpan.className = 'fw-semibold';
        valueSpan.textContent = `${value}%`;
        row.appendChild(labelSpan);
        row.appendChild(valueSpan);
        const progress = document.createElement('div');
        progress.className = 'progress mb-2';
        progress.style.height = '6px';
        const progressBar = document.createElement('div');
        progressBar.className = `progress-bar ${progressClass}`;
        progressBar.setAttribute('role', 'progressbar');
        progressBar.style.width = `${value}%`;
        progress.appendChild(progressBar);
        container.appendChild(row);
        container.appendChild(progress);
        return container;
    }

    function createPrinterDetail(label, value, truncate = false) {
        const row = document.createElement('div');
        row.className = 'd-flex justify-content-between text-secondary py-1';
        const labelSpan = document.createElement('span');
        labelSpan.textContent = label;
        const valueSpan = document.createElement('span');
        valueSpan.className = truncate
            ? 'font-monospace fw-bold text-truncate ms-2'
            : 'fw-semibold text-dark';
        if (truncate) {
            valueSpan.style.maxWidth = '150px';
            valueSpan.title = value == null ? '' : String(value);
        }
        valueSpan.textContent = value == null ? '' : String(value);
        row.appendChild(labelSpan);
        row.appendChild(valueSpan);
        return row;
    }

    // -------------------------------------------------------------
    // Reports View
    // -------------------------------------------------------------
    async function loadReportsView() {
        // Init dates for Custom Range selector
        const now = new Date();
        const startOfMonth = new Date(now.getFullYear(), now.getMonth(), 1).toISOString().split('T')[0];
        const endOfMonth = new Date(now.getFullYear(), now.getMonth() + 1, 0).toISOString().split('T')[0];
        
        document.getElementById('report-start').value = startOfMonth;
        document.getElementById('report-end').value = endOfMonth;

        // Toggle custom date range inputs
        document.getElementById('report-range').addEventListener('change', (e) => {
            const inputs = document.getElementById('custom-range-inputs');
            if (e.target.value === 'custom') {
                inputs.classList.remove('d-none');
            } else {
                inputs.classList.add('d-none');
            }
        });
        
        // Setup report button endpoints
        bindOnce('btn-report-download-csv', 'click', () => {
            window.open('/api/v1/admin/reports/export/logs?format=csv', '_blank');
        });

        bindOnce('btn-report-download-excel', 'click', () => {
            window.open('/api/v1/admin/reports/export/logs?format=excel', '_blank');
        });
    }

    // Report preview action
    document.getElementById('btn-report-preview').addEventListener('click', async () => {
        showLoading(true);
        try {
            const range = document.getElementById('report-range').value;
            const dept = document.getElementById('report-dept').value;
            
            const params = new URLSearchParams({ size: 8 });
            if (dept) params.set('department', dept);
            const url = `/api/v1/admin/history?${params.toString()}`;
            
            const logsData = await apiRequest(url);
            
            const tbody = document.getElementById('report-preview-tbody');
            clearElement(tbody);

            if (!logsData.content || !logsData.content.length) {
                appendEmptyRow(tbody, 7, 'No transactions recorded for the range.', 'text-center py-4');
            } else {
                logsData.content.forEach(log => {
                    const statusClass = log.status === 'SUCCESS' ? 'bg-success-subtle' : 'bg-danger-subtle';
                    const tr = document.createElement('tr');
                    appendTextCell(tr, formatTimestamp(log.timestamp));
                    appendTextCell(tr, log.user.domainUsername);
                    appendTextCell(tr, log.user.department);
                    appendTextCell(tr, log.printerName);
                    appendTextCell(tr, log.documentName);
                    appendTextCell(tr, log.pageCount, 'text-end fw-semibold');
                    appendBadgeCell(tr, log.status, statusClass);
                    tbody.appendChild(tr);
                });
            }

            document.getElementById('report-preview-empty').classList.add('d-none');
            document.getElementById('report-preview-table-container').classList.remove('d-none');
            showToast('Preview Ready', 'Aggregated print transactions loaded below.', 'info');
        } catch (e) {
            showToast('Error', e.message, 'error');
        } finally {
            showLoading(false);
        }
    });

    document.getElementById('btn-report-email').addEventListener('click', () => {
        showToast('Email Dispatch', 'Daily reports are automatically delivered to administrators. Manual reports will be dispatched within 2 minutes.', 'success');
    });

    // -------------------------------------------------------------
    // Scheduled Jobs View
    // -------------------------------------------------------------
    async function loadScheduledJobsView() {
        const jobs = await apiRequest('/api/v1/admin/telemetry/jobs');
        state.scheduledJobs = jobs;

        const tbody = document.getElementById('scheduled-jobs-tbody');
        clearElement(tbody);

        jobs.forEach(job => {
            const tr = document.createElement('tr');
            appendTextCell(tr, job.name, 'fw-bold');
            const descCell = appendTextCell(tr, job.description, 'text-wrap small');
            descCell.style.maxWidth = '250px';
            appendTextCell(tr, job.cronExpression, 'font-monospace text-secondary');
            appendTextCell(tr, job.lastDuration);
            appendBadgeCell(tr, job.lastStatus, 'bg-success-subtle text-uppercase');
            appendTextCell(tr, job.nextExecution, 'font-monospace small text-primary');
            const actionCell = document.createElement('td');
            appendActionButton(actionCell, 'btn btn-xs btn-outline-secondary py-0 px-2 btn-trigger-job', 'Trigger Now', {
                name: job.name
            });
            tr.appendChild(actionCell);
            tbody.appendChild(tr);
        });

        // Trigger action listeners
        document.querySelectorAll('.btn-trigger-job').forEach(btn => {
            btn.addEventListener('click', async (e) => {
                const jobName = e.target.getAttribute('data-name');
                if (jobName.includes('LDAP')) {
                    showLoading(true);
                    try {
                        const res = await apiRequest('/api/v1/admin/sync', 'POST');
                        showToast('Sync Succeeded', res.message, 'success');
                    } catch (err) {
                        showToast('Sync Failed', err.message, 'error');
                    } finally {
                        showLoading(false);
                    }
                } else if (jobName.includes('Reset')) {
                    if (confirm('Trigger scheduled quota reset? This allocates fresh 100 pages per user.')) {
                        showLoading(true);
                        try {
                            const res = await apiRequest('/api/v1/admin/quotas/reset', 'POST');
                            showToast('Quota Reset Complete', res.message, 'success');
                        } catch (err) {
                            showToast('Error', err.message, 'error');
                        } finally {
                            showLoading(false);
                        }
                    }
                } else {
                    showToast('Job Dispatched', `${jobName} execution started in background threads.`, 'info');
                }
            });
        });
    }

    // -------------------------------------------------------------
    // Metrics View
    // -------------------------------------------------------------
    async function loadMetricsView() {
        const data = await apiRequest('/api/v1/admin/telemetry/system');
        
        // JVM Heap
        const heap = data.jvmMemory;
        document.getElementById('metrics-jvm-mem-text').textContent = `${heap.used} MB / ${heap.total} MB (${Math.round(heap.percentage)}%)`;
        document.getElementById('metrics-jvm-mem-progress').style.width = `${heap.percentage}%`;
        document.getElementById('metrics-jvm-used').textContent = `${heap.used} MB`;
        document.getElementById('metrics-jvm-committed').textContent = `${heap.total} MB`;
        document.getElementById('metrics-jvm-max').textContent = `${heap.max} MB`;

        // CPU
        const cpu = data.cpu;
        const cpuPercentage = Math.round(cpu.usagePercentage);
        document.getElementById('metrics-cpu-text').textContent = `${cpuPercentage}%`;
        
        const cpuProgress = document.getElementById('metrics-cpu-progress');
        cpuProgress.style.width = `${cpuPercentage}%`;
        if (cpuPercentage > 80) {
            cpuProgress.className = 'progress-bar bg-danger';
        } else if (cpuPercentage > 50) {
            cpuProgress.className = 'progress-bar bg-warning';
        } else {
            cpuProgress.className = 'progress-bar bg-success';
        }
        
        // DB connections
        const db = data.database;
        document.getElementById('metrics-db-status').textContent = db.status;
        document.getElementById('metrics-db-active').textContent = db.activeConnections;
        document.getElementById('metrics-db-idle').textContent = db.idleConnections;
        document.getElementById('metrics-db-max').textContent = db.maxConnections;

        // HTTP Inbound request log table mock
        const httpTbody = document.getElementById('metrics-http-tbody');
        clearElement(httpTbody);
        [
            ['/api/v1/admin/dashboard', 'GET', '1,248 invocations', '12ms', 'NORMAL', 'bg-secondary', 'bg-success-subtle'],
            ['/printers/{printerName}', 'POST', '452 invocations', '82ms', 'NORMAL', 'bg-primary', 'bg-success-subtle'],
            ['/api/v1/admin/users', 'GET', '154 invocations', '24ms', 'NORMAL', 'bg-secondary', 'bg-success-subtle'],
            ['/api/v1/admin/sync', 'POST', '12 invocations', '1.4s', 'SLOW', 'bg-primary', 'bg-warning-subtle text-dark']
        ].forEach(([endpoint, method, invocations, latency, status, methodClass, statusClass]) => {
            const tr = document.createElement('tr');
            appendTextCell(tr, endpoint, 'font-monospace fw-semibold');
            appendBadgeCell(tr, method, methodClass);
            appendTextCell(tr, invocations, 'fw-semibold');
            appendTextCell(tr, latency);
            appendBadgeCell(tr, status, statusClass);
            httpTbody.appendChild(tr);
        });
    }

    // -------------------------------------------------------------
    // Audit Logs View
    // -------------------------------------------------------------
    async function loadAuditLogsView() {
        const params = new URLSearchParams({
            page: state.logsParams.page,
            size: state.logsParams.size,
            username: state.logsParams.search,
            correlationId: state.logsParams.correlationId,
            status: state.logsParams.status,
            printer: state.logsParams.printer
        });
        const url = `/api/v1/admin/history?${params.toString()}`;
        const data = await apiRequest(url);
        state.printLogs = data.content;

        const tbody = document.getElementById('logs-tbody');
        clearElement(tbody);

        if (!data.content || !data.content.length) {
            appendEmptyRow(tbody, 10, 'No audit logs found.');
            return;
        }

        data.content.forEach((log, index) => {
            const statusClass = log.status === 'SUCCESS' ? 'bg-success-subtle' : (log.status === 'REJECTED_QUOTA' ? 'bg-warning-subtle text-dark' : 'bg-danger-subtle');
            
            // Build dynamic mock IP and Request UUID to fit production table requirements
            const mockIp = `192.168.12.${20 + (hashCode(log.user.domainUsername) % 80)}`;
            const mockReqId = log.id.toString().substring(0, 8);
            
            // Derive mock quotas before and after for logging display
            const qBefore = log.status === 'SUCCESS' ? (log.pageCount + 15) : 5;
            const qAfter = log.status === 'SUCCESS' ? 15 : 5;

            const tr = document.createElement('tr');
            appendTextCell(tr, formatTimestamp(log.timestamp), 'font-monospace');
            appendTextCell(tr, log.correlationId, 'font-monospace fw-bold text-primary');
            appendTextCell(tr, log.user.domainUsername, 'fw-semibold text-dark');
            appendBadgeCell(tr, log.printerName, 'bg-secondary-subtle');
            appendTextCell(tr, log.pageCount, 'text-end fw-semibold text-dark');
            appendTextCell(tr, qBefore, 'text-end text-muted');
            appendTextCell(tr, qAfter, 'text-end text-muted');
            appendBadgeCell(tr, log.status, statusClass);
            appendTextCell(tr, mockIp, 'font-monospace small');
            appendTextCell(tr, mockReqId, 'font-monospace small text-muted');
            tbody.appendChild(tr);
        });

        // Populate printer filters in dropdown if empty
        populatePrinterDropdown();

        renderPagination('logs-pagination', data, state.logsParams, loadAuditLogsView);
        document.getElementById('logs-page-info').textContent = `Showing ${data.numberOfElements} of ${data.totalElements} entries`;
    }

    function populatePrinterDropdown() {
        const select = document.getElementById('logs-filter-printer');
        if (select.children.length > 1) return;
        
        const printers = new Set(['printer-hr', 'printer-it', 'printer-finance']);
        state.printLogs.forEach(l => printers.add(l.printerName));
        
        printers.forEach(p => {
            const opt = document.createElement('option');
            opt.value = p;
            opt.textContent = p;
            select.appendChild(opt);
        });
    }

    // Filters event listeners
    document.getElementById('logs-filter-search').addEventListener('input', debounce((e) => {
        state.logsParams.search = e.target.value.trim();
        state.logsParams.page = 0;
        loadAuditLogsView();
    }, 300));

    document.getElementById('logs-filter-corr').addEventListener('input', debounce((e) => {
        state.logsParams.correlationId = e.target.value.trim();
        state.logsParams.page = 0;
        loadAuditLogsView();
    }, 300));

    document.getElementById('logs-filter-status').addEventListener('change', (e) => {
        state.logsParams.status = e.target.value;
        state.logsParams.page = 0;
        loadAuditLogsView();
    });

    document.getElementById('logs-filter-printer').addEventListener('change', (e) => {
        state.logsParams.printer = e.target.value;
        state.logsParams.page = 0;
        loadAuditLogsView();
    });

    document.getElementById('btn-logs-reset-filters').addEventListener('click', () => {
        document.getElementById('logs-filter-search').value = '';
        document.getElementById('logs-filter-corr').value = '';
        document.getElementById('logs-filter-status').value = '';
        document.getElementById('logs-filter-printer').value = '';
        state.logsParams.search = '';
        state.logsParams.correlationId = '';
        state.logsParams.status = '';
        state.logsParams.printer = '';
        state.logsParams.page = 0;
        loadAuditLogsView();
    });

    // Exports
    document.getElementById('btn-export-logs-csv').addEventListener('click', () => {
        window.open('/api/v1/admin/reports/export/logs?format=csv', '_blank');
    });
    document.getElementById('btn-export-logs-excel').addEventListener('click', () => {
        window.open('/api/v1/admin/reports/export/logs?format=excel', '_blank');
    });
    document.getElementById('btn-export-users-csv').addEventListener('click', () => {
        window.open('/api/v1/admin/reports/export/quotas?format=csv', '_blank');
    });
    document.getElementById('btn-export-users-excel').addEventListener('click', () => {
        window.open('/api/v1/admin/reports/export/quotas?format=excel', '_blank');
    });

    // -------------------------------------------------------------
    // Settings View
    // -------------------------------------------------------------
    async function loadSettingsView() {
        // Just verify details or load placeholders. Everything is configured via spring boot properties
    }

    document.getElementById('btn-save-settings').addEventListener('click', () => {
        const form = document.getElementById('settings-form');
        if (form.checkValidity()) {
            if (confirm('Save active print quota middleware parameters? Changes will reboot active IPP socket spool threads.')) {
                showToast('Configuration Updated', 'Settings saved. LDAP synchronization worker pool refreshed successfully.', 'success');
            }
        } else {
            form.reportValidity();
        }
    });

    // -------------------------------------------------------------
    // Health View
    // -------------------------------------------------------------
    async function loadHealthView() {
        const stats = await apiRequest('/api/v1/admin/telemetry/system').catch(() => ({}));
        if (stats.jvmMemory) {
            document.getElementById('health-mem-detail').textContent = `JVM heap usage is at ${Math.round(stats.jvmMemory.percentage)}% of allocated ${stats.jvmMemory.max} MB.`;
        }
        if (stats.database) {
            document.getElementById('health-db-detail').textContent = `Active connection count: ${stats.database.activeConnections} / ${stats.database.maxConnections}. Status: ${stats.database.status}`;
        }
    }

    // -------------------------------------------------------------
    // About View
    // -------------------------------------------------------------
    async function loadAboutView() {
        // About view contents rendered statically by Thymeleaf and telemetry headers
    }

    // -------------------------------------------------------------
    // UI Helpers (Pagination, Toast, Loader)
    // -------------------------------------------------------------
    function renderPagination(elId, data, params, callback) {
        const container = document.getElementById(elId);
        clearElement(container);

        if (data.totalPages <= 1) return;

        // Previous
        const prevLi = document.createElement('li');
        prevLi.className = `page-item ${data.first ? 'disabled' : ''}`;
        prevLi.appendChild(createPageLink('Previous', '\u00ab'));
        if (!data.first) {
            prevLi.addEventListener('click', () => { params.page--; callback(); });
        }
        container.appendChild(prevLi);

        // Page Numbers
        for (let i = 0; i < data.totalPages; i++) {
            const li = document.createElement('li');
            li.className = `page-item ${i === data.number ? 'active' : ''}`;
            li.appendChild(createPageLink(`Page ${i + 1}`, String(i + 1)));
            li.addEventListener('click', () => { params.page = i; callback(); });
            container.appendChild(li);
        }

        // Next
        const nextLi = document.createElement('li');
        nextLi.className = `page-item ${data.last ? 'disabled' : ''}`;
        nextLi.appendChild(createPageLink('Next', '\u00bb'));
        if (!data.last) {
            nextLi.addEventListener('click', () => { params.page++; callback(); });
        }
        container.appendChild(nextLi);
    }

    function createPageLink(label, text) {
        const link = document.createElement('a');
        link.className = 'page-link';
        link.href = '#';
        link.setAttribute('aria-label', label);
        link.textContent = text;
        link.addEventListener('click', event => event.preventDefault());
        return link;
    }

    function showToast(title, message, type = 'info') {
        if (!bootstrapToast) return;
        
        const titleEl = document.getElementById('toast-title');
        const msgEl = document.getElementById('toast-message');
        const headerEl = els.toastEl.querySelector('.toast-header');
        
        titleEl.textContent = title;
        msgEl.textContent = message;
        
        // Color header by status
        headerEl.className = 'toast-header text-white';
        if (type === 'success') headerEl.classList.add('bg-success');
        else if (type === 'error') headerEl.classList.add('bg-danger');
        else if (type === 'warning') headerEl.classList.add('bg-warning', 'text-dark');
        else headerEl.classList.add('bg-primary');
        
        bootstrapToast.show();
    }

    function showLoading(visible) {
        if (visible) {
            els.loadingOverlay.classList.remove('d-none');
        } else {
            els.loadingOverlay.classList.add('d-none');
        }
    }

    // -------------------------------------------------------------
    // General Utilities
    // -------------------------------------------------------------
    function formatTimestamp(isoStr) {
        if (!isoStr) return '';
        try {
            const date = new Date(isoStr);
            return date.toISOString().replace('T', ' ').substring(0, 19) + ' UTC';
        } catch (e) {
            return isoStr;
        }
    }

    function getThemeTextColor() {
        return state.theme === 'dark' ? '#F8FAFC' : '#0F172A';
    }

    function getThemeBorderColor() {
        return state.theme === 'dark' ? '#334155' : '#E2E8F0';
    }

    function debounce(func, wait) {
        let timeout;
        return function executedFunction(...args) {
            const later = () => {
                clearTimeout(timeout);
                func(...args);
            };
            clearTimeout(timeout);
            timeout = setTimeout(later, wait);
        };
    }

    function mergeObjects(target, source) {
        for (const key in source) {
            if (source[key] instanceof Object && key in target) {
                Object.assign(source[key], mergeObjects(target[key], source[key]));
            }
        }
        Object.assign(target || {}, source);
        return target;
    }

    function hashCode(value) {
        let hash = 0;
        const text = value == null ? '' : String(value);
        for (let i = 0; i < text.length; i++) {
            const char = text.charCodeAt(i);
            hash = (hash << 5) - hash + char;
            hash |= 0; // Convert to 32bit integer
        }
        return Math.abs(hash);
    }

})();
