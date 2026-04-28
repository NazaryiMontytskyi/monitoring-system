/* ── Dashboard AJAX refresh — no full page reload ── */

(function () {
    'use strict';

    var REFRESH_INTERVAL = 7;

    function isDashboard() {
        return window.location.pathname === '/';
    }

    /* ── Helpers ── */

    function escapeHtml(str) {
        if (str == null) return '';
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;');
    }

    function fmtDateTime(iso) {
        if (!iso) return '';
        return String(iso).replace('T', ' ').substring(0, 19);
    }

    function statusColor(status, alpha) {
        switch (status) {
            case 'UP':       return 'rgba(34,197,94,'   + alpha + ')';
            case 'DEGRADED': return 'rgba(245,158,11,'  + alpha + ')';
            case 'DOWN':     return 'rgba(239,68,68,'   + alpha + ')';
            default:         return 'rgba(107,114,128,' + alpha + ')';
        }
    }

    function statusBadgeHtml(status) {
        var cfg = {
            UP:       { bg: 'rgba(34,197,94,0.15)',   text: '#22c55e', dot: '#22c55e' },
            DOWN:     { bg: 'rgba(239,68,68,0.15)',   text: '#ef4444', dot: '#ef4444' },
            DEGRADED: { bg: 'rgba(245,158,11,0.15)',  text: '#f59e0b', dot: null      }
        };
        var c   = cfg[status] || { bg: 'rgba(107,114,128,0.15)', text: '#6b7280', dot: null };
        var dot = c.dot
            ? '<span class="pulse-dot" style="display:inline-block;width:6px;height:6px;'
              + 'border-radius:50%;background-color:' + c.dot + ';"></span>'
            : '';
        return '<span class="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-semibold" '
             + 'style="background-color:' + c.bg + ';color:' + c.text + ';">'
             + dot + escapeHtml(status) + '</span>';
    }

    /* ── Fetch & render dashboard summary (stat cards, service table, bar chart) ── */

    function fetchDashboardSummary() {
        fetch('/api/dashboard')
            .then(function (res) { return res.ok ? res.json() : null; })
            .then(function (data) {
                if (!data) return;

                var $ = function (id) { return document.getElementById(id); };

                /* Stat cards */
                var total = $('stat-total');     if (total)    total.textContent    = data.totalServices;
                var up    = $('stat-up');        if (up)       up.textContent       = data.healthyCount;
                var deg   = $('stat-degraded');  if (deg)      deg.textContent      = data.degradedCount;
                var down  = $('stat-down');      if (down)     down.textContent     = data.downCount;

                /* Service status table */
                var services = data.services || [];
                var noMsg    = $('no-services-msg');
                var table    = $('service-table');
                var tbody    = $('service-table-body');

                if (services.length === 0) {
                    if (noMsg)  noMsg.classList.remove('hidden');
                    if (table)  table.classList.add('hidden');
                } else {
                    if (noMsg)  noMsg.classList.add('hidden');
                    if (table)  table.classList.remove('hidden');
                    if (tbody) {
                        tbody.innerHTML = services.map(function (svc, idx) {
                            var bg  = idx % 2 === 0 ? '#1e293b' : '#162032';
                            var avg = svc.avgResponseTimeMs != null
                                ? Math.round(svc.avgResponseTimeMs) + ' ms'
                                : '<span style="color:#475569;">—</span>';
                            return '<tr style="background-color:' + bg + ';" class="transition-colors hover:brightness-110">'
                                 + '<td class="px-4 py-3 font-medium" style="color:#f1f5f9;">' + escapeHtml(svc.name) + '</td>'
                                 + '<td class="px-4 py-3">' + statusBadgeHtml(svc.status) + '</td>'
                                 + '<td class="px-4 py-3" style="color:#94a3b8;">' + avg + '</td>'
                                 + '<td class="px-4 py-3">'
                                 + '<a href="/services/' + svc.id + '" '
                                 + 'class="px-3 py-1 rounded-lg text-xs font-medium transition-colors" '
                                 + 'style="background-color:#3b82f6;color:#fff;">Details</a>'
                                 + '</td></tr>';
                        }).join('');
                    }
                }

                /* Response Time bar chart */
                var canvas = document.getElementById('rtBarChart');
                if (canvas && services.length > 0) {
                    var chart = Chart.getChart(canvas);
                    if (chart) {
                        chart.data.labels                          = services.map(function (s) { return s.name; });
                        chart.data.datasets[0].data                = services.map(function (s) { return s.avgResponseTimeMs || 0; });
                        chart.data.datasets[0].backgroundColor     = services.map(function (s) { return statusColor(s.status, 0.7); });
                        chart.data.datasets[0].borderColor         = services.map(function (s) { return statusColor(s.status, 1);   });
                        chart.update();
                    }
                }
            })
            .catch(function () {});
    }

    /* ── Fetch & render recent alert events ── */

    function fetchRecentAlerts() {
        fetch('/api/dashboard/recent-events')
            .then(function (res) { return res.ok ? res.json() : null; })
            .then(function (events) {
                if (!events) return;

                var noMsg = document.getElementById('no-alerts-msg');
                var list  = document.getElementById('recent-alerts-list');

                if (events.length === 0) {
                    if (noMsg) noMsg.classList.remove('hidden');
                    if (list)  list.classList.add('hidden');
                } else {
                    if (noMsg) noMsg.classList.add('hidden');
                    if (list) {
                        list.classList.remove('hidden');
                        list.innerHTML = events.map(function (e) {
                            return '<li class="px-6 py-3 flex items-start gap-3">'
                                 + '<span class="w-2 h-2 rounded-full mt-1.5 flex-shrink-0" style="background-color:#ef4444;"></span>'
                                 + '<div>'
                                 + '<p class="text-sm" style="color:#f1f5f9;">' + escapeHtml(e.message) + '</p>'
                                 + '<p class="text-xs mt-0.5" style="color:#94a3b8;">' + fmtDateTime(e.firedAt) + '</p>'
                                 + '</div></li>';
                        }).join('');
                    }
                }
            })
            .catch(function () {});
    }

    /* ── Countdown + periodic refresh (no page reload) ── */

    function startRefreshCycle() {
        var el        = document.getElementById('refresh-countdown');
        var remaining = REFRESH_INTERVAL;

        if (el) el.textContent = 'Auto-refresh: ' + remaining + 's ↻';

        setInterval(function () {
            remaining -= 1;
            if (el) el.textContent = 'Auto-refresh: ' + remaining + 's ↻';

            if (remaining <= 0) {
                fetchDashboardSummary();
                fetchRecentAlerts();
                remaining = REFRESH_INTERVAL;
            }
        }, 1000);
    }

    document.addEventListener('DOMContentLoaded', function () {
        if (!isDashboard()) return;
        startRefreshCycle();
    });
}());
