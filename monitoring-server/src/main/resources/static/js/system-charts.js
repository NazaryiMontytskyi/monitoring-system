/* ── Live system-wide charts for the dashboard page ── */

(function () {
    'use strict';

    var POLL_INTERVAL = 7000;
    var charts = {};

    var GRID_COLOR = 'rgba(255,255,255,0.05)';
    var GREEN  = '#22c55e';
    var YELLOW = '#f59e0b';
    var BLUE   = '#3b82f6';
    var RED    = '#ef4444';

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

    function fetchAndRender() {
        fetch('/api/metrics/system/history?minutes=30&limit=60')
            .then(function (res) { return res.ok ? res.json() : []; })
            .then(function (points) {
                if (!Array.isArray(points) || points.length === 0) return;

                var labels   = points.map(function (p) { return fmtTime(p.recordedAt); });
                var avgRt    = points.map(function (p) { return p.avgResponseTimeMs || 0; });
                var avgCpu   = points.map(function (p) { return p.avgCpuUsage || 0; });
                var up       = points.map(function (p) { return p.servicesUp || 0; });
                var degraded = points.map(function (p) { return p.servicesDegraded || 0; });
                var down     = points.map(function (p) { return p.servicesDown || 0; });

                updateChart(charts.avgResponseTime, labels, [avgRt]);
                updateChart(charts.statusOverview,  labels, [up, degraded, down]);
                updateChart(charts.avgCpu,          labels, [avgCpu]);
            })
            .catch(function () {});
    }

    document.addEventListener('DOMContentLoaded', function () {
        if (!document.getElementById('sys-chart-avg-rt')) return;
        initCharts();
        fetchAndRender();
        setInterval(fetchAndRender, POLL_INTERVAL);
    });
}());
