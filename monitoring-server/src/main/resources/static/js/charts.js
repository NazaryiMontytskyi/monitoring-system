/* ── Chart.js initialization for monitoring dashboard ── */

(function () {
    'use strict';

    function statusColor(status, alpha) {
        alpha = alpha || 1;
        switch (status) {
            case 'UP':       return 'rgba(34,197,94,'  + alpha + ')';
            case 'DEGRADED': return 'rgba(245,158,11,' + alpha + ')';
            case 'DOWN':     return 'rgba(239,68,68,'  + alpha + ')';
            default:         return 'rgba(107,114,128,' + alpha + ')';
        }
    }

    function initDashboardBarChart() {
        var canvas = document.getElementById('rtBarChart');
        if (!canvas) return;

        var labels   = window._chartLabels   || [];
        var values   = window._chartValues   || [];
        var statuses = window._chartStatuses || [];

        if (labels.length === 0) return;

        var bgColors     = statuses.map(function (s) { return statusColor(s, 0.7); });
        var borderColors = statuses.map(function (s) { return statusColor(s, 1);   });

        new Chart(canvas, {
            type: 'bar',
            data: {
                labels: labels,
                datasets: [{
                    label: 'Avg Response Time (ms)',
                    data: values,
                    backgroundColor: bgColors,
                    borderColor: borderColors,
                    borderWidth: 1,
                    borderRadius: 6,
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                animation: { duration: 750 },
                plugins: {
                    legend: { display: false },
                    tooltip: {
                        callbacks: {
                            label: function (ctx) {
                                return ctx.parsed.y.toFixed(0) + ' ms';
                            }
                        }
                    }
                },
                scales: {
                    x: {
                        ticks: { color: '#94a3b8', font: { size: 11 } },
                        grid:  { color: 'rgba(51,65,85,0.5)' }
                    },
                    y: {
                        ticks: {
                            color: '#94a3b8',
                            font: { size: 11 },
                            callback: function (v) { return v + ' ms'; }
                        },
                        grid:  { color: 'rgba(51,65,85,0.5)' },
                        beginAtZero: true
                    }
                }
            }
        });
    }

    document.addEventListener('DOMContentLoaded', function () {
        initDashboardBarChart();
    });
}());
