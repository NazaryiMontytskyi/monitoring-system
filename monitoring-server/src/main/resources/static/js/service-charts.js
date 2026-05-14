/* ── Live metric charts for the service detail page ── */

(function () {
    'use strict';

    var POLL_INTERVAL  = 7000;
    var VISIBLE_POINTS = 10;
    var charts         = {};

    /* Full (up to 60-point) dataset kept in memory for the modal. */
    var _svcFullData = {
        labels: [], responseTimes: [], anomalies: [], zScores: [],
        cpuSystem: [], cpuProcess: [], heapUsed: [], heapMax: [],
        nonHeap: [], threadsLive: [], threadsDaemon: [], gcPause: []
    };

    var DARK_BG    = '#1f2937';
    var GRID_COLOR = 'rgba(255,255,255,0.05)';
    var GREEN  = '#22c55e';
    var YELLOW = '#f59e0b';
    var BLUE   = '#3b82f6';
    var PURPLE = '#a855f7';
    var RED    = '#ef4444';

    /* Z-scores of the current window — kept in closure so the tooltip callback
       can read them without extra fetch calls. */
    var currentZScores = [];

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

    /* Response-time chart has a custom tooltip that appends the z-score for
       anomalous points. All other charts use the shared commonOptions. */
    function responseTimeOptions() {
        var opts = commonOptions('Response Time (ms)');
        opts.plugins.tooltip = {
            mode: 'index',
            intersect: false,
            callbacks: {
                afterLabel: function (context) {
                    if (context.datasetIndex !== 0) return null;
                    var z = currentZScores[context.dataIndex];
                    if (z != null && Math.abs(z) >= 2) {
                        return '\u26A0 Anomaly  z\u202F=\u202F' + z.toFixed(2);
                    }
                    return null;
                }
            }
        };
        return opts;
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
            data: {
                labels: [],
                datasets: [makeLineDataset('Response Time (ms)', GREEN, [])]
            },
            options: responseTimeOptions()
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
                    makeLineDataset('Heap Max (MB)',  YELLOW, [])
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
                    makeLineDataset('Live Threads',   BLUE,   []),
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

    /* Update the response-time chart with per-point anomaly styling. */
    function updateResponseTimeChart(labels, values, anomalies, zScores) {
        currentZScores = zScores;

        var ds = charts.responseTime.data.datasets[0];
        charts.responseTime.data.labels = labels;
        ds.data = values;

        ds.pointBackgroundColor = anomalies.map(function (a) {
            return a ? RED : GREEN;
        });
        ds.pointBorderColor = anomalies.map(function (a) {
            return a ? RED : GREEN;
        });
        ds.pointRadius = anomalies.map(function (a) {
            return a ? 7 : 2;
        });
        ds.pointHoverRadius = anomalies.map(function (a) {
            return a ? 9 : 4;
        });
        /* Keep the line itself green; anomalous segments turn red via point color. */
        ds.borderColor = GREEN;

        charts.responseTime.update('none');
    }

    /* Show / hide the anomaly banner above the charts. */
    function updateAnomalyBanner(count) {
        var banner = document.getElementById('anomaly-banner');
        if (!banner) return;
        var countEl = document.getElementById('anomaly-banner-count');
        if (count > 0) {
            if (countEl) countEl.textContent = count;
            banner.classList.remove('hidden');
        } else {
            banner.classList.add('hidden');
        }
    }

    function fetchAndRender(serviceId) {
        fetch('/api/metrics/' + serviceId + '/history?minutes=30&limit=60')
            .then(function (res) { return res.ok ? res.json() : []; })
            .then(function (points) {
                if (!Array.isArray(points) || points.length === 0) return;

                /* Store the full dataset for modal use. */
                _svcFullData.labels        = points.map(function (p) { return fmtTime(p.recordedAt); });
                _svcFullData.responseTimes = points.map(function (p) { return p.responseTimeMs; });
                _svcFullData.anomalies     = points.map(function (p) { return !!p.anomaly; });
                _svcFullData.zScores       = points.map(function (p) { return p.zScore || 0; });
                _svcFullData.cpuSystem     = points.map(function (p) { return orNull(p.cpuUsage); });
                _svcFullData.cpuProcess    = points.map(function (p) { return orNull(p.processCpuUsage); });
                _svcFullData.heapUsed      = points.map(function (p) { return orNull(p.heapUsedMb); });
                _svcFullData.heapMax       = points.map(function (p) { return orNull(p.heapMaxMb); });
                _svcFullData.nonHeap       = points.map(function (p) { return orNull(p.nonHeapUsedMb); });
                _svcFullData.threadsLive   = points.map(function (p) { return orNull(p.threadsLive); });
                _svcFullData.threadsDaemon = points.map(function (p) { return orNull(p.threadsDaemon); });
                _svcFullData.gcPause       = points.map(function (p) { return orNull(p.gcPauseMs); });

                /* Render only the last VISIBLE_POINTS on the small canvases. */
                var sl  = function (arr) { return arr.slice(-VISIBLE_POINTS); };
                var lbl = sl(_svcFullData.labels);

                updateResponseTimeChart(lbl, sl(_svcFullData.responseTimes),
                                        sl(_svcFullData.anomalies), sl(_svcFullData.zScores));
                updateChart(charts.cpu,     lbl, [sl(_svcFullData.cpuSystem), sl(_svcFullData.cpuProcess)]);
                updateChart(charts.heap,    lbl, [sl(_svcFullData.heapUsed),  sl(_svcFullData.heapMax)]);
                updateChart(charts.nonHeap, lbl, [sl(_svcFullData.nonHeap)]);
                updateChart(charts.threads, lbl, [sl(_svcFullData.threadsLive), sl(_svcFullData.threadsDaemon)]);
                updateChart(charts.gc,      lbl, [sl(_svcFullData.gcPause)]);

                /* Anomaly banner counts across the whole 30-min window. */
                var anomalyCount = _svcFullData.anomalies.filter(function (a) { return a; }).length;
                updateAnomalyBanner(anomalyCount);
            })
            .catch(function () {});
    }

    /* Build a full (60-point) Chart.js data object for simple line charts.
       Copies dataset styling from the source chart, replaces data arrays. */
    function buildSvcFullChartData(chart, labels, datasetsData) {
        var datasets = chart.config.data.datasets.map(function (ds, i) {
            return {
                label:           ds.label,
                data:            datasetsData[i] || [],
                borderColor:     ds.borderColor,
                backgroundColor: ds.backgroundColor,
                borderWidth:     ds.borderWidth || 2,
                tension:         ds.tension != null ? ds.tension : 0.4,
                fill:            ds.fill || false,
                pointRadius:     3,
                pointHoverRadius: 5
            };
        });
        return { labels: labels, datasets: datasets };
    }

    /* Response-time chart needs per-point anomaly markers rebuilt for all 60 points. */
    function buildFullResponseTimeData() {
        var labels    = _svcFullData.labels;
        var values    = _svcFullData.responseTimes;
        var anomalies = _svcFullData.anomalies;
        return {
            labels: labels,
            datasets: [{
                label:            'Response Time (ms)',
                data:             values,
                borderColor:      GREEN,
                backgroundColor:  GREEN,
                borderWidth:      2,
                tension:          0.4,
                fill:             false,
                pointBackgroundColor: anomalies.map(function (a) { return a ? RED : GREEN; }),
                pointBorderColor:     anomalies.map(function (a) { return a ? RED : GREEN; }),
                pointRadius:          anomalies.map(function (a) { return a ? 7 : 2; }),
                pointHoverRadius:     anomalies.map(function (a) { return a ? 9 : 4; })
            }]
        };
    }

    function attachClickHandlers() {
        var ids = [
            'chart-response-time', 'chart-cpu', 'chart-heap',
            'chart-non-heap', 'chart-threads', 'chart-gc'
        ];
        ids.forEach(function (id) {
            var canvas = document.getElementById(id);
            if (!canvas) return;
            canvas.addEventListener('click', function () {
                var chart = Chart.getChart(canvas);
                if (!chart || typeof openChartModal !== 'function') return;
                var card = canvas.closest('.chart-card');
                var titleEl = card ? card.querySelector('p') : null;
                var title = titleEl ? titleEl.textContent.trim() : id;

                var fullData;
                if (_svcFullData.labels.length > 0) {
                    var lbl = _svcFullData.labels;
                    if (id === 'chart-response-time') {
                        fullData = buildFullResponseTimeData();
                    } else if (id === 'chart-cpu') {
                        fullData = buildSvcFullChartData(chart, lbl, [_svcFullData.cpuSystem, _svcFullData.cpuProcess]);
                    } else if (id === 'chart-heap') {
                        fullData = buildSvcFullChartData(chart, lbl, [_svcFullData.heapUsed, _svcFullData.heapMax]);
                    } else if (id === 'chart-non-heap') {
                        fullData = buildSvcFullChartData(chart, lbl, [_svcFullData.nonHeap]);
                    } else if (id === 'chart-threads') {
                        fullData = buildSvcFullChartData(chart, lbl, [_svcFullData.threadsLive, _svcFullData.threadsDaemon]);
                    } else if (id === 'chart-gc') {
                        fullData = buildSvcFullChartData(chart, lbl, [_svcFullData.gcPause]);
                    }
                }

                openChartModal(chart, title, fullData);
            });
        });
    }

    document.addEventListener('DOMContentLoaded', function () {
        var section = document.getElementById('live-metrics-section');
        if (!section) return;

        var serviceId = section.getAttribute('data-service-id');
        if (!serviceId) return;

        initCharts();
        attachClickHandlers();
        fetchAndRender(serviceId);
        setInterval(function () { fetchAndRender(serviceId); }, POLL_INTERVAL);
    });
}());
