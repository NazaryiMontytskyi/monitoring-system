/* ── Live metric charts for the service detail page ── */

(function () {
    'use strict';

    var POLL_INTERVAL = 7000;
    var charts = {};

    var DARK_BG    = '#1f2937';
    var GRID_COLOR = 'rgba(255,255,255,0.05)';
    var GREEN  = '#22c55e';
    var YELLOW = '#f59e0b';
    var BLUE   = '#3b82f6';
    var PURPLE = '#a855f7';

    function commonOptions(titleText) {
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
            backgroundColor: color.replace(')', ',0.1)').replace('rgb', 'rgba'),
            borderWidth: 2,
            pointRadius: 2,
            tension: 0.4,
            fill: false
        };
    }

    function initCharts() {
        function ctx(id) {
            var canvas = document.getElementById(id);
            return canvas ? canvas.getContext('2d') : null;
        }

        charts.responseTime = new Chart(ctx('chart-response-time'), {
            type: 'line',
            data: { labels: [], datasets: [makeLineDataset('Response Time (ms)', GREEN, [])] },
            options: commonOptions('Response Time (ms)')
        });

        charts.cpu = new Chart(ctx('chart-cpu'), {
            type: 'line',
            data: {
                labels: [],
                datasets: [
                    makeLineDataset('System CPU (%)', YELLOW, []),
                    makeLineDataset('Process CPU (%)', BLUE, [])
                ]
            },
            options: commonOptions('CPU Usage (%)')
        });

        charts.heap = new Chart(ctx('chart-heap'), {
            type: 'line',
            data: {
                labels: [],
                datasets: [
                    makeLineDataset('Heap Used (MB)', GREEN, []),
                    makeLineDataset('Heap Max (MB)', YELLOW, [])
                ]
            },
            options: commonOptions('Heap Memory (MB)')
        });

        charts.nonHeap = new Chart(ctx('chart-non-heap'), {
            type: 'line',
            data: { labels: [], datasets: [makeLineDataset('Non-Heap Used (MB)', PURPLE, [])] },
            options: commonOptions('Non-Heap Memory (MB)')
        });

        charts.threads = new Chart(ctx('chart-threads'), {
            type: 'line',
            data: {
                labels: [],
                datasets: [
                    makeLineDataset('Live Threads', BLUE, []),
                    makeLineDataset('Daemon Threads', PURPLE, [])
                ]
            },
            options: commonOptions('JVM Threads')
        });

        charts.gc = new Chart(ctx('chart-gc'), {
            type: 'line',
            data: { labels: [], datasets: [makeLineDataset('GC Pause (ms)', YELLOW, [])] },
            options: commonOptions('GC Pause (ms)')
        });
    }

    function fmtTime(isoStr) {
        if (!isoStr) return '';
        var t = isoStr.split('T');
        return t.length > 1 ? t[1].substring(0, 8) : isoStr;
    }

    function orNull(v) {
        return v != null ? v : null;
    }

    function updateChart(chart, labels, datasetsData) {
        chart.data.labels = labels;
        datasetsData.forEach(function (data, i) {
            chart.data.datasets[i].data = data;
        });
        chart.update('none');
    }

    function fetchAndRender(serviceId) {
        fetch('/api/metrics/' + serviceId + '/history?minutes=30&limit=60')
            .then(function (res) { return res.ok ? res.json() : []; })
            .then(function (points) {
                if (!Array.isArray(points) || points.length === 0) return;

                var labels       = points.map(function (p) { return fmtTime(p.recordedAt); });
                var responseTimes = points.map(function (p) { return p.responseTimeMs; });
                var cpuSystem    = points.map(function (p) { return orNull(p.cpuUsage); });
                var cpuProcess   = points.map(function (p) { return orNull(p.processCpuUsage); });
                var heapUsed     = points.map(function (p) { return orNull(p.heapUsedMb); });
                var heapMax      = points.map(function (p) { return orNull(p.heapMaxMb); });
                var nonHeap      = points.map(function (p) { return orNull(p.nonHeapUsedMb); });
                var threadsLive  = points.map(function (p) { return orNull(p.threadsLive); });
                var threadsDaemon = points.map(function (p) { return orNull(p.threadsDaemon); });
                var gcPause      = points.map(function (p) { return orNull(p.gcPauseMs); });

                updateChart(charts.responseTime, labels, [responseTimes]);
                updateChart(charts.cpu,          labels, [cpuSystem, cpuProcess]);
                updateChart(charts.heap,         labels, [heapUsed, heapMax]);
                updateChart(charts.nonHeap,      labels, [nonHeap]);
                updateChart(charts.threads,      labels, [threadsLive, threadsDaemon]);
                updateChart(charts.gc,           labels, [gcPause]);
            })
            .catch(function () {});
    }

    document.addEventListener('DOMContentLoaded', function () {
        var section = document.getElementById('live-metrics-section');
        if (!section) return;

        var serviceId = section.getAttribute('data-service-id');
        if (!serviceId) return;

        initCharts();
        fetchAndRender(serviceId);
        setInterval(function () { fetchAndRender(serviceId); }, POLL_INTERVAL);
    });
}());
