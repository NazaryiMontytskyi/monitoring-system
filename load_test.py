#!/usr/bin/env python3
"""
load_test.py — Load generator for monitoring-system demo microservices.

Sends realistic HTTP traffic in six distinct phases so that load changes
(response time, error rate, anomalies) are clearly visible in the
monitoring dashboard in real time.

Requirements:
    pip install requests

Usage:
    python load_test.py                            # all phases, defaults
    python load_test.py --scenario spike           # single named scenario
    python load_test.py --workers 30 --rps 60      # custom concurrency
    python load_test.py --base-url http://localhost --list-scenarios
    python load_test.py --no-preflight             # skip service-check step
"""

import argparse
import json
import math
import random
import signal
import statistics
import sys
import threading
import time
from dataclasses import dataclass, field
from typing import Dict, List, Optional, Tuple

try:
    import requests
    from requests.adapters import HTTPAdapter
    from urllib3.util.retry import Retry
except ImportError:
    print("ERROR: 'requests' library is not installed.\n"
          "Run:  pip install requests")
    sys.exit(1)

# ─── ANSI colours ────────────────────────────────────────────────────────────
RESET  = "\033[0m"
BOLD   = "\033[1m"
DIM    = "\033[2m"
RED    = "\033[91m"
GREEN  = "\033[92m"
YELLOW = "\033[93m"
BLUE   = "\033[94m"
CYAN   = "\033[96m"
PURPLE = "\033[95m"

def c(color: str, text: str) -> str:
    return f"{color}{text}{RESET}"

# ─── Data structures ──────────────────────────────────────────────────────────

@dataclass
class Endpoint:
    method: str
    path: str                          # relative to base, e.g. /api/orders
    port: int
    label: str
    body: Optional[dict] = None
    weight: int = 10                   # higher → chosen more often

@dataclass
class PhaseStats:
    name: str
    times_ok:  List[float] = field(default_factory=list)
    times_err: List[float] = field(default_factory=list)
    total: int = 0
    errors: int = 0
    _lock: threading.Lock = field(default_factory=threading.Lock)
    _t0: float = field(default_factory=time.monotonic)

    def record(self, elapsed_ms: float, ok: bool) -> None:
        with self._lock:
            self.total += 1
            if ok:
                self.times_ok.append(elapsed_ms)
            else:
                self.errors += 1
                self.times_err.append(elapsed_ms)

    @property
    def error_rate(self) -> float:
        return self.errors / self.total * 100 if self.total else 0.0

    @property
    def avg_ms(self) -> float:
        return statistics.mean(self.times_ok) if self.times_ok else 0.0

    @property
    def p95_ms(self) -> float:
        if not self.times_ok:
            return 0.0
        s = sorted(self.times_ok)
        return s[max(0, int(len(s) * 0.95) - 1)]

    @property
    def p99_ms(self) -> float:
        if not self.times_ok:
            return 0.0
        s = sorted(self.times_ok)
        return s[max(0, int(len(s) * 0.99) - 1)]

    @property
    def rps(self) -> float:
        elapsed = time.monotonic() - self._t0
        return self.total / elapsed if elapsed > 0 else 0.0


@dataclass
class Phase:
    key: str
    title: str
    description: str
    duration_s: int
    workers: int
    target_rps: float                  # 0 = unlimited (workers drive pace)
    endpoints: List[Endpoint]
    color: str = BLUE


# ─── Endpoint catalogue ───────────────────────────────────────────────────────

def build_endpoints(base: str) -> Dict[str, List[Endpoint]]:
    """Return named endpoint groups used by different phases."""

    inv = [
        Endpoint("GET",  "/api/inventory",       8081, "INV list",        weight=15),
        Endpoint("GET",  "/api/inventory/1",      8081, "INV get-1",       weight=10),
        Endpoint("GET",  "/api/inventory/2",      8081, "INV get-2",       weight=10),
        Endpoint("POST", "/api/inventory",        8081, "INV create",
                 body={"name": "Widget", "quantity": random.randint(1, 50),
                       "price": round(random.uniform(1, 200), 2)},        weight=8),
    ]
    ord_ = [
        Endpoint("GET",  "/api/orders",           8082, "ORD list",        weight=15),
        Endpoint("GET",  "/api/orders/1",         8082, "ORD get-1",       weight=10),
        Endpoint("POST", "/api/orders",           8082, "ORD create",
                 body={"productId": 1, "quantity": 2, "customerId": "load-test"}, weight=8),
        Endpoint("PUT",  "/api/orders/1/status",  8082, "ORD update",
                 body={"status": "PROCESSING"},                            weight=5),
    ]
    pay = [
        Endpoint("GET",  "/api/payments",         8083, "PAY list",        weight=15),
        Endpoint("GET",  "/api/payments/1",       8083, "PAY get-1",       weight=10),
        Endpoint("POST", "/api/payments",         8083, "PAY create",
                 body={"orderId": 1, "amount": 99.99},                     weight=8),
        Endpoint("PUT",  "/api/payments/1/complete", 8083, "PAY complete",
                 body={"status": "SUCCESS"},                               weight=5),
    ]

    slow = [
        Endpoint("GET", "/api/inventory/slow", 8081, "INV slow",  weight=10),
        Endpoint("GET", "/api/orders/slow",    8082, "ORD slow",  weight=10),
        Endpoint("GET", "/api/payments/slow",  8083, "PAY slow",  weight=10),
    ]
    err = [
        Endpoint("GET", "/api/orders/error",   8082, "ORD error", weight=10),
        Endpoint("GET", "/api/payments/error", 8083, "PAY error", weight=10),
    ]

    normal = inv + ord_ + pay

    return {
        "normal":  normal,
        "slow":    slow + normal,       # 3 slow + all normal
        "errors":  err  + normal,       # error endpoints mixed in
        "spike":   normal,              # same endpoints, many more workers
        "cooldown": normal,
    }


# ─── Phase definitions ────────────────────────────────────────────────────────

def build_phases(endpoints: Dict[str, List[Endpoint]]) -> Dict[str, Phase]:
    phases = {
        "warmup": Phase(
            key="warmup",
            title="[1] Warmup",
            description="Gentle baseline load - establishes normal response time distribution",
            duration_s=25,
            workers=5,
            target_rps=8,
            endpoints=endpoints["normal"],
            color=GREEN,
        ),
        "normal": Phase(
            key="normal",
            title="[2] Normal Load",
            description="Steady moderate traffic - all three services receive balanced requests",
            duration_s=40,
            workers=15,
            target_rps=30,
            endpoints=endpoints["normal"],
            color=BLUE,
        ),
        "spike": Phase(
            key="spike",
            title="[3] Traffic Spike",
            description="Sudden burst - CPU, response time and thread count should spike on the dashboard",
            duration_s=25,
            workers=60,
            target_rps=0,             # unlimited - workers drive max throughput
            endpoints=endpoints["spike"],
            color=YELLOW,
        ),
        "slow": Phase(
            key="slow",
            title="[4] Slow Endpoints",
            description="80 % of traffic hits /slow endpoints - P95/P99 response time should climb sharply",
            duration_s=35,
            workers=12,
            target_rps=20,
            endpoints=endpoints["slow"],
            color=PURPLE,
        ),
        "errors": Phase(
            key="errors",
            title="[5] Error Injection",
            description="Error endpoints mixed in - error rate rises, anomaly chart should light up",
            duration_s=35,
            workers=15,
            target_rps=25,
            endpoints=endpoints["errors"],
            color=RED,
        ),
        "cooldown": Phase(
            key="cooldown",
            title="[6] Cooldown",
            description="Traffic tapers back - recovery visible in all charts",
            duration_s=20,
            workers=4,
            target_rps=5,
            endpoints=endpoints["cooldown"],
            color=CYAN,
        ),
    }
    return phases

PHASE_ORDER = ["warmup", "normal", "spike", "slow", "errors", "cooldown"]


# ─── HTTP helpers ─────────────────────────────────────────────────────────────

def make_session(base_url: str, port: int, timeout: float) -> requests.Session:
    s = requests.Session()
    adapter = HTTPAdapter(
        max_retries=Retry(total=0),   # no retries — we want to see errors
        pool_connections=30,
        pool_maxsize=60,
    )
    s.mount("http://", adapter)
    s.mount("https://", adapter)
    s.headers.update({"Content-Type": "application/json",
                       "Accept": "application/json",
                       "X-Load-Test": "true"})
    return s

_sessions: Dict[int, requests.Session] = {}
_session_lock = threading.Lock()

def get_session(port: int, base_url: str, timeout: float) -> requests.Session:
    with _session_lock:
        if port not in _sessions:
            _sessions[port] = make_session(base_url, port, timeout)
        return _sessions[port]


def send_request(ep: Endpoint, base_url: str,
                 timeout: float, stats: PhaseStats) -> None:
    url = f"{base_url}:{ep.port}{ep.path}"
    session = get_session(ep.port, base_url, timeout)
    t0 = time.monotonic()
    try:
        if ep.method == "GET":
            r = session.get(url, timeout=timeout)
        elif ep.method == "POST":
            r = session.post(url, json=ep.body or {}, timeout=timeout)
        elif ep.method == "PUT":
            r = session.put(url, json=ep.body or {}, timeout=timeout)
        else:
            r = session.request(ep.method, url, json=ep.body, timeout=timeout)
        elapsed = (time.monotonic() - t0) * 1000
        ok = r.status_code < 500
        stats.record(elapsed, ok)
    except Exception:
        elapsed = (time.monotonic() - t0) * 1000
        stats.record(elapsed, ok=False)


def weighted_choice(endpoints: List[Endpoint]) -> Endpoint:
    total = sum(ep.weight for ep in endpoints)
    r = random.uniform(0, total)
    cumulative = 0
    for ep in endpoints:
        cumulative += ep.weight
        if r <= cumulative:
            return ep
    return endpoints[-1]


# ─── Worker loop ─────────────────────────────────────────────────────────────

def worker_loop(endpoints: List[Endpoint], base_url: str,
                timeout: float, stats: PhaseStats,
                stop_event: threading.Event,
                rate_limiter: Optional[threading.Semaphore]) -> None:
    while not stop_event.is_set():
        if rate_limiter is not None:
            if not rate_limiter.acquire(timeout=1.0):
                continue
        ep = weighted_choice(endpoints)
        send_request(ep, base_url, timeout, stats)


# ─── Rate limiter (token-bucket style) ───────────────────────────────────────

class RateLimiter:
    """Releases at most `rps` tokens per second into a semaphore."""

    def __init__(self, sem: threading.Semaphore,
                 rps: float, stop_event: threading.Event):
        self._sem = sem
        self._rps = rps
        self._stop = stop_event
        self._thread = threading.Thread(target=self._run, daemon=True)

    def start(self) -> None:
        self._thread.start()

    def _run(self) -> None:
        interval = 1.0 / self._rps
        while not self._stop.is_set():
            self._sem.release()
            time.sleep(interval)


# ─── Progress display ─────────────────────────────────────────────────────────

def bar(filled: int, total: int, width: int = 30) -> str:
    f = int(width * filled / total) if total else 0
    return f"[{'█' * f}{'░' * (width - f)}]"

def fmt_ms(v: float) -> str:
    if v == 0:
        return c(DIM, "  --- ")
    color = GREEN if v < 300 else (YELLOW if v < 700 else RED)
    return c(color, f"{v:6.0f} ms")

def fmt_err(rate: float) -> str:
    if rate == 0:
        return c(GREEN, " 0.0%")
    color = YELLOW if rate < 5 else RED
    return c(color, f"{rate:5.1f}%")

def live_line(stats: PhaseStats, elapsed: float,
              duration: float, phase: Phase) -> str:
    progress = bar(int(elapsed), int(duration))
    remaining = max(0, duration - elapsed)
    return (
        f"\r  {phase.color}{progress}{RESET}"
        f"  {remaining:4.0f}s left"
        f"  {c(BOLD, str(stats.total)):>7} reqs"
        f"  {c(CYAN, f'{stats.rps:5.1f} rps')}"
        f"  avg {fmt_ms(stats.avg_ms)}"
        f"  p95 {fmt_ms(stats.p95_ms)}"
        f"  err {fmt_err(stats.error_rate)}"
        "    "
    )


# ─── Pre-flight checks ────────────────────────────────────────────────────────

def preflight(base_url: str, timeout: float) -> bool:
    print(c(BOLD, "\n  Pre-flight checks"))
    print(f"  {'─' * 52}")

    # monitoring server
    try:
        r = requests.get(f"{base_url}:8080/api/services",
                         timeout=timeout)
        services = r.json() if r.ok else []
        print(f"  {c(GREEN, '[OK]')} monitoring-server  "
              f"({len(services)} services registered)")
        names = {s.get("name") for s in services}
    except Exception as e:
        print(f"  {c(RED, '[ERR]')} monitoring-server unreachable: {e}")
        return False

    # demo services
    service_map = {
        "inventory-service": 8081,
        "order-service":     8082,
        "payment-service":   8083,
    }
    all_ok = True
    for name, port in service_map.items():
        try:
            r = requests.get(f"{base_url}:{port}/actuator/health",
                             timeout=timeout)
            status = r.json().get("status", "?") if r.ok else str(r.status_code)
            reg = c(GREEN, "registered") if name in names else c(YELLOW, "not registered")
            icon = c(GREEN, "[OK]") if status == "UP" else c(YELLOW, "[--]")
            print(f"  {icon} {name:<25} port {port}  "
                  f"health={c(GREEN if status=='UP' else YELLOW, status)}"
                  f"  {reg}")
        except Exception as e:
            print(f"  {c(RED, '[ERR]')} {name:<25} port {port}  "
                  f"unreachable: {e}")
            all_ok = False

    print(f"  {'─' * 52}")
    if not all_ok:
        print(c(YELLOW,
                "  [!] Some services are unreachable. "
                "Run with --no-preflight to proceed anyway.\n"))
    return all_ok


# ─── Phase runner ─────────────────────────────────────────────────────────────

def run_phase(phase: Phase, base_url: str, timeout: float) -> PhaseStats:
    stats = PhaseStats(name=phase.key)
    stop_event = threading.Event()

    # build semaphore + rate limiter
    sem: Optional[threading.Semaphore] = None
    rl: Optional[RateLimiter] = None
    if phase.target_rps > 0:
        sem = threading.Semaphore(0)
        rl = RateLimiter(sem, phase.target_rps, stop_event)
        rl.start()

    threads = [
        threading.Thread(
            target=worker_loop,
            args=(phase.endpoints, base_url, timeout, stats, stop_event, sem),
            daemon=True,
        )
        for _ in range(phase.workers)
    ]
    for t in threads:
        t.start()

    t0 = time.monotonic()
    try:
        while True:
            elapsed = time.monotonic() - t0
            if elapsed >= phase.duration_s:
                break
            sys.stdout.write(live_line(stats, elapsed, phase.duration_s, phase))
            sys.stdout.flush()
            time.sleep(0.4)
    finally:
        stop_event.set()
        for t in threads:
            t.join(timeout=3)

    # final line update
    sys.stdout.write(
        live_line(stats, phase.duration_s, phase.duration_s, phase))
    sys.stdout.flush()
    print()

    return stats


# ─── Reporting ───────────────────────────────────────────────────────────────

def print_phase_header(phase: Phase) -> None:
    print(f"\n  {phase.color}{BOLD}{phase.title}{RESET}"
          f"  {c(DIM, phase.description)}")
    meta = (f"{phase.workers} workers  "
            f"{phase.target_rps:.0f} rps target  "
            f"{phase.duration_s}s duration")
    print(f"  {c(DIM, meta)}")


def print_phase_summary(stats: PhaseStats) -> None:
    ok = stats.total - stats.errors
    print(f"  {'─' * 60}")
    print(f"  {'Total requests:':<20} {stats.total:>8}   "
          f"  {'OK:':<6} {c(GREEN, str(ok)):<20}"
          f"  {'Errors:':<8} {c(RED if stats.errors else GREEN, str(stats.errors))}")
    print(f"  {'Avg response time:':<20} {fmt_ms(stats.avg_ms)}"
          f"         {'P95:':<6} {fmt_ms(stats.p95_ms)}"
          f"  {'P99:':<6} {fmt_ms(stats.p99_ms)}")
    print(f"  {'Throughput:':<20} {stats.rps:>8.1f} req/s"
          f"         {'Error rate:':<12} {fmt_err(stats.error_rate)}")


def print_final_report(all_stats: Dict[str, PhaseStats]) -> None:
    total_req  = sum(s.total  for s in all_stats.values())
    total_err  = sum(s.errors for s in all_stats.values())
    all_times  = [t for s in all_stats.values() for t in s.times_ok]
    overall_avg = statistics.mean(all_times) if all_times else 0
    overall_p95 = sorted(all_times)[int(len(all_times)*0.95)-1] if all_times else 0
    overall_err = total_err / total_req * 100 if total_req else 0

    print(f"\n  {'═' * 62}")
    print(f"  {c(BOLD, 'FINAL REPORT')}")
    print(f"  {'═' * 62}")
    print(f"  {'Phase':<22} {'Requests':>8}  {'RPS':>6}  "
          f"{'Avg':>8}  {'P95':>8}  {'Errors':>7}")
    print(f"  {'─' * 62}")
    for key in PHASE_ORDER:
        if key not in all_stats:
            continue
        s = all_stats[key]
        err_str = (c(RED, f"{s.errors:>5}") if s.errors else
                   c(GREEN, f"{s.errors:>5}"))
        print(f"  {all_stats[key].name:<22} {s.total:>8}  "
              f"{s.rps:>6.1f}  "
              f"{s.avg_ms:>7.0f}ms  "
              f"{s.p95_ms:>7.0f}ms  "
              f"{err_str}")
    print(f"  {'─' * 62}")
    print(f"  {'TOTAL':<22} {total_req:>8}  "
          f"{'':>6}  "
          f"{overall_avg:>7.0f}ms  "
          f"{overall_p95:>7.0f}ms  "
          f"{c(RED if total_err else GREEN, str(total_err)):>5}")
    print(f"\n  Overall error rate:  {fmt_err(overall_err)}")
    print(f"  {'═' * 62}\n")


# ─── Entry point ─────────────────────────────────────────────────────────────

def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(
        description="Load generator for monitoring-system demo microservices",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Scenarios:
  warmup    - gentle baseline (8 rps, 5 workers, 25 s)
  normal    - steady moderate load (30 rps, 15 workers, 40 s)
  spike     - traffic burst, max throughput (60 workers, 25 s)
  slow      - hammers /slow endpoints => P95/P99 spike (20 rps, 35 s)
  errors    - injects error endpoints => error rate spike (25 rps, 35 s)
  cooldown  - taper back to baseline (5 rps, 4 workers, 20 s)
  all       - run all six phases in order (default)
        """,
    )
    p.add_argument("--base-url",  default="http://localhost",
                   help="Base URL (default: http://localhost)")
    p.add_argument("--timeout",   type=float, default=10.0,
                   help="Request timeout in seconds (default: 10)")
    p.add_argument("--scenario",  default="all",
                   choices=["all"] + PHASE_ORDER,
                   help="Which phase(s) to run (default: all)")
    p.add_argument("--workers",   type=int, default=0,
                   help="Override worker count for every phase")
    p.add_argument("--rps",       type=float, default=0,
                   help="Override target RPS for every phase")
    p.add_argument("--duration",  type=int, default=0,
                   help="Override duration (seconds) for every phase")
    p.add_argument("--no-preflight", action="store_true",
                   help="Skip the pre-flight connectivity check")
    p.add_argument("--list-scenarios", action="store_true",
                   help="Print scenario descriptions and exit")
    return p.parse_args()


def main() -> None:
    args = parse_args()

    endpoints = build_endpoints(args.base_url)
    phases    = build_phases(endpoints)

    if args.list_scenarios:
        print(c(BOLD, "\n  Available scenarios:\n"))
        for key in PHASE_ORDER:
            ph = phases[key]
            print(f"  {ph.color}{ph.title:<20}{RESET}  {ph.description}")
        print()
        return

    # ── Banner ────────────────────────────────────────────────────────────────
    print(c(BOLD, """
  +----------------------------------------------------------+
  |        monitoring-system  /  load_test.py               |
  |        Real-time load generator for demo services       |
  +----------------------------------------------------------+
    """))
    print(f"  Base URL  : {c(CYAN, args.base_url)}")
    print(f"  Timeout   : {args.timeout} s")
    print(f"  Scenario  : {c(YELLOW, args.scenario)}")
    if args.workers:
        print(f"  Workers   : overridden -> {args.workers}")
    if args.rps:
        print(f"  Target RPS: overridden -> {args.rps}")
    if args.duration:
        print(f"  Duration  : overridden -> {args.duration} s per phase")

    # ── Pre-flight ────────────────────────────────────────────────────────────
    if not args.no_preflight:
        ok = preflight(args.base_url, timeout=5.0)
        if not ok:
            sys.exit(1)

    # ── Apply overrides ───────────────────────────────────────────────────────
    if args.workers or args.rps or args.duration:
        for ph in phases.values():
            if args.workers:  ph.workers    = args.workers
            if args.rps:      ph.target_rps = args.rps
            if args.duration: ph.duration_s = args.duration

    # ── Select phases ─────────────────────────────────────────────────────────
    keys = PHASE_ORDER if args.scenario == "all" else [args.scenario]
    total_s = sum(phases[k].duration_s for k in keys)
    print(f"\n  {len(keys)} phase(s) selected - estimated duration: "
          f"{c(CYAN, f'{total_s}s (~{math.ceil(total_s/60)} min)')}")
    print(c(DIM, "  Open http://localhost:8080 to watch the dashboard.\n"
                 "  Press Ctrl+C to abort at any time.\n"))

    # ── Graceful interrupt ────────────────────────────────────────────────────
    interrupted = False
    def _sigint(*_):
        nonlocal interrupted
        interrupted = True
        print(c(YELLOW, "\n\n  ! Interrupted -- finishing current phase..."))
    signal.signal(signal.SIGINT, _sigint)

    # ── Run ───────────────────────────────────────────────────────────────────
    all_stats: Dict[str, PhaseStats] = {}
    global_t0 = time.monotonic()

    for key in keys:
        if interrupted:
            break
        phase = phases[key]
        print_phase_header(phase)
        stats = run_phase(phase, args.base_url, args.timeout)
        stats.name = key
        all_stats[key] = stats
        print_phase_summary(stats)

        if key != keys[-1] and not interrupted:
            print(c(DIM, "\n  Pausing 3 s before next phase..."))
            time.sleep(3)

    # ── Final report ──────────────────────────────────────────────────────────
    elapsed_total = time.monotonic() - global_t0
    print(f"\n  Total wall-clock time: {elapsed_total:.1f} s")
    print_final_report(all_stats)


if __name__ == "__main__":
    main()
