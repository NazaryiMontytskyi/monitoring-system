/* ── Dashboard auto-refresh every 7 seconds ── */

(function () {
    'use strict';

    var REFRESH_INTERVAL = 7;

    function isDashboard() {
        return window.location.pathname === '/';
    }

    function startCountdown() {
        var el = document.getElementById('refresh-countdown');
        if (!el) return;

        var remaining = REFRESH_INTERVAL;

        var timer = setInterval(function () {
            remaining -= 1;
            el.textContent = 'Auto-refresh: ' + remaining + 's \u21BB';

            if (remaining <= 0) {
                clearInterval(timer);
                window.location.reload();
            }
        }, 1000);
    }

    document.addEventListener('DOMContentLoaded', function () {
        if (isDashboard()) {
            startCountdown();
        }
    });
}());
