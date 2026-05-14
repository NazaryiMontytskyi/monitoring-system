/* ── Live system-wide charts for the dashboard page ── */

(function () {
    'use strict';

    var POLL_INTERVAL    = 7000;
    var VISIBLE_POINTS   = 10;
    var charts           = {};
    var _fullData        = { labels: [], up: [], degraded: [], down: [], avgRt: [], avgCpu: [], anomalyCounts: [] };

    var GRID_COLOR = 'rgba(255,255,255,0.05)';
    var GREEN  = '#22c55e';
    var YELLOW = '#f59e0b';
    var BLUE   = '#3b82f6';
    var RED    = '#ef4444';
    var ORANGE = '#f97316';

    function commonOptions() {
        return {
            responsive: true,
            maintainAspectRatio: false,
            animation: false,
            plugins: {
                legend: {
                    display: true,
                    labels: { color: '#94a3b8', boxWidth: 12, font: { size: 11 } }
                },
                tooltip: { mode: 'index', intersect: false }
            },
            scales: {
                x: {
                    ticks: { color: '#94a3b8', font: { size: 10 }, maxTicksLimit: 8 },
                    grid:  { color: GRID_COLOR }
                },
                y: {
                    ticks: { color: '#94a3b8', font: { size: 10 } },
                    grid:  { color: GRID_COLOR },
                    beginAtZero: true
                }
            }
        };
    }

    function makeLineDataset(label, color, data) {
        return {
            label: label,
            data: data || [],
            borderColor: color,
            backgroundColor: color,
            borderWidth: 2,
            pointRadius: 2,
            tension: 0.4,
            fill: false
        };
    }

    function makeBarDataset(label, color, data) {
        return {
            label: label,
            data: data || [],
            backgroundColor: color,
            borderColor: color,
            borderWidth: 1
        };
    }

    /* Anomaly bar chart: bar color intensifies with count, red outline for any bar > 0. */
    function makeAnomalyDataset(data) {
        return {
            label: 'Anomalies',
            data: data || [],
            backgroundColor: (data || []).map(function (v) {
                return v > 0 ? 'rgba(239,68,68,0.7)' : 'rgba(239,68,68,0.15)';
            }),
            borderColor: (data || []).map(function (v) {
                return v > 0 ? RED : 'rgba(239,68,68,0.3)';
            }),
            borderWidth: 1
        };
    }

    function initCharts() {
        function ctx(id) {
            var canvas = document.getElementById(id);
            return canvas ? canvas.getContext('2d') : null;
        }

        charts.avgResponseTime = new Chart(ctx('sys-chart-avg-rt'), {
            type: 'line',
            data: { labels: [], datasets: [makeLineDataset('Avg Response Time (ms)', BLUE, [])] },
            options: commonOptions()
        });

        var statusOpts = commonOptions();
        statusOpts.scales.x.stacked = true;
        statusOpts.scales.y.stacked = true;
        charts.statusOverview = new Chart(ctx('sys-chart-status'), {
            type: 'bar',
            data: {
                labels: [],
                datasets: [
                    makeBarDataset('Up',       GREEN,  []),
                    makeBarDataset('Degraded', YELLOW, []),
                    makeBarDataset('Down',     RED,    [])
                ]
            },
            options: statusOpts
        });

        charts.avgCpu = new Chart(ctx('sys-chart-avg-cpu'), {
            type: 'line',
            data: { labels: [], datasets: [makeLineDataset('Avg CPU Usage (%)', YELLOW, [])] },
            options: commonOptions()
        });

        var anomalyOpts = commonOptions();
        anomalyOpts.scales.y.ticks.stepSize = 1;
        charts.anomalies = new Chart(ctx('sys-chart-anomalies'), {
            type: 'bar',
            data: { labels: [], datasets: [makeAnomalyDataset([])] },
            options: anomalyOpts
        });
    }

    function fmtTime(isoStr) {
        if (!isoStr) return '';
        var t = isoStr.split('T');
        return t.length > 1 ? t[1].substring(0, 8) : isoStr;
    }

    function updateChart(chart, labels, datasetsData) {
        chart.data.labels = labels;
        datasetsData.forEach(function (data, i) {
            chart.data.datasets[i].data = data;
        });
        chart.update('none');
    }

    /* Anomaly chart needs per-bar color recomputation on each update. */
    function updateAnomalyChart(labels, counts) {
        charts.anomalies.data.labels = labels;
        var ds = charts.anomalies.data.datasets[0];
        ds.data = counts;
        ds.backgroundColor = counts.map(function (v) {
            return v > 0 ? 'rgba(239,68,68,0.7)' : 'rgba(239,68,68,0.15)';
        });
        ds.borderColor = counts.map(function (v) {
            return v > 0 ? RED : 'rgba(239,68,68,0.3)';
        });
        charts.anomalies.update('none');

        /* Update the anomaly summary counter badge if it exists. */
        var total = counts.reduce(function (s, v) { return s + v; }, 0);
        var badge = document.getElementById('sys-anomaly-total');
        if (badge) {
            badge.textContent = total;
            badge.parentElement.style.display = total > 0 ? '' : 'none';
        }
    }

    function fetchAndRender() {
        fetch('/api/metrics/system/history?minutes=30&limit=60')
            .then(function (res) { return res.ok ? res.json() : []; })
            .then(function (points) {
                if (!Array.isArray(points) || points.length === 0) return;

                _fullData.labels       = points.map(function (p) { return fmtTime(p.recordedAt); });
                _fullData.avgRt        = points.map(function (p) { return p.avgResponseTimeMs || 0; });
                _fullData.avgCpu       = points.map(function (p) { return p.avgCpuUsage || 0; });
                _fullData.up           = points.map(function (p) { return p.servicesUp || 0; });
                _fullData.degraded     = points.map(function (p) { return p.servicesDegraded || 0; });
                _fullData.down         = points.map(function (p) { return p.servicesDown || 0; });
                _fullData.anomalyCounts = points.map(function (p) { return p.anomalyCount || 0; });

                var sl = function (arr) { return arr.slice(-VISIBLE_POINTS); };
                var lbl = sl(_fullData.labels);

                updateChart(charts.avgResponseTime, lbl, [sl(_fullData.avgRt)]);
                updateChart(charts.statusOverview,  lbl, [sl(_fullData.up), sl(_fullData.degraded), sl(_fullData.down)]);
                updateChart(charts.avgCpu,          lbl, [sl(_fullData.avgCpu)]);
                updateAnomalyChart(lbl, sl(_fullData.anomalyCounts));
            })
            .catch(function () {});
    }

    /* Build a Chart.js data object for the modal using the full (60-point) dataset.
       Styling is copied from the current source chart's datasets. */
    function buildFullChartData(chart, labels, datasetsData) {
        var datasets = chart.config.data.datasets.map(function (ds, i) {
            var copy = JSON.parse(JSON.stringify(ds));
            copy.data = datasetsData[i] || [];
            return copy;
        });
        return { labels: labels, datasets: datasets };
    }

    /* Anomaly chart uses per-bar color arrays — recompute them for all 60 points. */
    function buildFullAnomalyData(labels, counts) {
        return {
            labels: labels,
            datasets: [{
                label: 'Anomalies',
                data: counts,
                backgroundColor: counts.map(function (v) {
                    return v > 0 ? 'rgba(239,68,68,0.7)' : 'rgba(239,68,68,0.15)';
                }),
                borderColor: counts.map(function (v) {
                    return v > 0 ? RED : 'rgba(239,68,68,0.3)';
                }),
                borderWidth: 1
            }]
        };
    }

    function attachClickHandlers() {
        /* sys-chart-status is handled by initServicesModal — skip it here */
        var ids = [
            'sys-chart-avg-rt', 'sys-chart-avg-cpu',
            'sys-chart-anomalies', 'rtBarChart'
        ];
        ids.forEach(function (id) {
            var canvas = document.getElementById(id);
            if (!canvas) return;
            canvas.addEventListener('click', function () {
                var chart = Chart.getChart(canvas);
                if (!chart || typeof openChartModal !== 'function') return;
                var card = canvas.closest('.chart-card');
                var titleEl = card ? (card.querySelector('p') || card.querySelector('h2')) : null;
                var title = titleEl ? titleEl.textContent.trim() : id;

                var fullData;
                if (_fullData.labels.length > 0) {
                    if (id === 'sys-chart-avg-rt') {
                        fullData = buildFullChartData(chart, _fullData.labels, [_fullData.avgRt]);
                    } else if (id === 'sys-chart-avg-cpu') {
                        fullData = buildFullChartData(chart, _fullData.labels, [_fullData.avgCpu]);
                    } else if (id === 'sys-chart-anomalies') {
                        fullData = buildFullAnomalyData(_fullData.labels, _fullData.anomalyCounts);
                    }
                    /* rtBarChart is server-rendered per-service data — no full override needed */
                }

                openChartModal(chart, title, fullData);
            });
        });
    }

    function openServicesModal() {
        var modal = document.getElementById('services-status-modal');
        if (!modal) return;
        modal.style.display = 'flex';

        var BAR_WIDTH_PX = 28;
        var scroll  = document.getElementById('services-modal-scroll');
        var wrap    = document.getElementById('services-modal-canvas-wrap');
        var canvas  = document.getElementById('chart-services-modal');
        if (!scroll || !wrap || !canvas) return;

        var labels   = _fullData.labels;
        var minWidth = Math.max(scroll.clientWidth, labels.length * BAR_WIDTH_PX);

        wrap.style.width   = minWidth + 'px';
        canvas.width       = minWidth;
        canvas.height      = 320;
        canvas.style.width  = minWidth + 'px';
        canvas.style.height = '320px';

        var existing = Chart.getChart(canvas);
        if (existing) { existing.destroy(); }

        var GRID = 'rgba(255,255,255,0.05)';
        new Chart(canvas.getContext('2d'), {
            type: 'bar',
            data: {
                labels: labels,
                datasets: [
                    makeBarDataset('Up',       GREEN,  _fullData.up),
                    makeBarDataset('Degraded', YELLOW, _fullData.degraded),
                    makeBarDataset('Down',     RED,    _fullData.down)
                ]
            },
            options: {
                responsive: false,
                maintainAspectRatio: false,
                animation: false,
                plugins: {
                    legend:  { display: true, labels: { color: '#94a3b8', boxWidth: 12, font: { size: 12 } } },
                    tooltip: { mode: 'index', intersect: false }
                },
                scales: {
                    x: { stacked: true, ticks: { color: '#94a3b8', font: { size: 10 } }, grid: { color: GRID } },
                    y: { stacked: true, ticks: { color: '#94a3b8', font: { size: 10 } }, grid: { color: GRID }, beginAtZero: true }
                }
            }
        });

        scroll.scrollLeft = scroll.scrollWidth;
    }

    function initServicesModal() {
        var statusCanvas = document.getElementById('sys-chart-status');
        if (statusCanvas) {
            statusCanvas.style.cursor = 'pointer';
            statusCanvas.addEventListener('click', openServicesModal);
        }

        var modal    = document.getElementById('services-status-modal');
        var closeBtn = document.getElementById('btn-services-modal-close');
        if (!modal) return;

        function closeModal() { modal.style.display = 'none'; }

        if (closeBtn) closeBtn.addEventListener('click', closeModal);

        modal.addEventListener('click', function (e) {
            if (e.target === modal) closeModal();
        });

        document.addEventListener('keydown', function (e) {
            if (e.key === 'Escape' && modal.style.display !== 'none') closeModal();
        });
    }

    function statusColor(status) {
        if (status === 'UP')       return '#22c55e';
        if (status === 'DEGRADED') return '#f59e0b';
        if (status === 'DOWN')     return '#ef4444';
        return '#94a3b8';
    }

    function initAnomalyModal() {
        var modal   = document.getElementById('anomaly-modal');
        var btnOpen = document.getElementById('btn-anomaly-list');
        var btnClose = document.getElementById('btn-anomaly-close');
        if (!modal || !btnOpen) return;

        function openModal() {
            modal.style.display = 'flex';
            loadAnomalies();
        }

        function closeModal() {
            modal.style.display = 'none';
        }

        btnOpen.addEventListener('click', function (e) {
            e.stopPropagation();
            openModal();
        });

        btnClose.addEventListener('click', closeModal);

        modal.addEventListener('click', function (e) {
            if (e.target === modal) closeModal();
        });

        document.addEventListener('keydown', function (e) {
            if (e.key === 'Escape') closeModal();
        });
    }

    function loadAnomalies() {
        var tbody    = document.getElementById('anomaly-table-body');
        var emptyMsg = document.getElementById('anomaly-empty-msg');
        var table    = document.getElementById('anomaly-table');
        if (!tbody) return;

        tbody.innerHTML = '<tr><td colspan="5" style="padding:2rem;text-align:center;color:#94a3b8;">Loading…</td></tr>';
        if (emptyMsg) emptyMsg.style.display = 'none';
        if (table)    table.style.display = '';

        fetch('/api/metrics/anomalies?minutes=30&limit=100')
            .then(function (res) { return res.ok ? res.json() : []; })
            .then(function (rows) {
                tbody.innerHTML = '';

                if (!Array.isArray(rows) || rows.length === 0) {
                    if (table)    table.style.display = 'none';
                    if (emptyMsg) emptyMsg.style.display = '';
                    return;
                }

                rows.forEach(function (r, i) {
                    var tr = document.createElement('tr');
                    tr.style.backgroundColor = i % 2 === 0 ? '#1e293b' : '#162032';

                    var time = fmtTime(r.recordedAt);
                    var color = statusColor(r.status);

                    var z = Number(r.zScore);
                    var zText = Number.isFinite(z) ? z.toFixed(2) : '—';
                    tr.innerHTML =
                        '<td class="px-4 py-3" style="color:#94a3b8;">' + time + '</td>' +
                        '<td class="px-4 py-3 font-medium" style="color:#f1f5f9;">' + (r.serviceName || '—') + '</td>' +
                        '<td class="px-4 py-3"><span style="color:' + color + ';font-weight:600;">' + (r.status || '—') + '</span></td>' +
                        '<td class="px-4 py-3" style="color:#94a3b8;">' + r.responseTimeMs + ' ms</td>' +
                        '<td class="px-4 py-3" style="color:#fca5a5;">' + zText + '</td>';

                    tbody.appendChild(tr);
                });
            })
            .catch(function () {
                tbody.innerHTML = '<tr><td colspan="5" style="padding:2rem;text-align:center;color:#ef4444;">Failed to load anomalies.</td></tr>';
            });
    }

    document.addEventListener('DOMContentLoaded', function () {
        if (!document.getElementById('sys-chart-avg-rt')) return;
        initCharts();
        attachClickHandlers();
        initAnomalyModal();
        initServicesModal();
        fetchAndRender();
        setInterval(fetchAndRender, POLL_INTERVAL);
    });
}());
