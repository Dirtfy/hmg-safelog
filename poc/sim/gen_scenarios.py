"""Generates the fake signal traces in scenarios/*.csv (stdlib only).
Columns: t_ms,speed_kph,brake,gear,airbag,telltales  (telltales = '|' separated names that are ON)
Run: python3 -I poc/sim/gen_scenarios.py   (from repo root)"""
import os

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "scenarios")
HDR = "t_ms,speed_kph,brake,gear,airbag,telltales"


def row(t, v, brake, gear, airbag=0, tt=""):
    return f"{t},{v:.1f},{int(brake)},{gear},{airbag},{tt}"


def write(name, rows):
    os.makedirs(OUT, exist_ok=True)
    with open(os.path.join(OUT, name + ".csv"), "w", encoding="utf-8") as f:
        f.write(HDR + "\n" + "\n".join(rows) + "\n")


def steps(t0, t1, v0, v1, brake, gear, dt=100):
    """linear ramp, inclusive of both ends"""
    n = max(1, (t1 - t0) // dt)
    return [row(t0 + i * dt, v0 + (v1 - v0) * i / n, brake, gear) for i in range(n + 1)]


# 1. normal parking out + in: brake pressed at gear change, gentle creep
r = steps(0, 2000, 0, 0, True, "PARK")
r += steps(2100, 3000, 0, 0, True, "REVERSE")
r += steps(3100, 9000, 0.2, 6, False, "REVERSE")
r += steps(9100, 12000, 5, 0, True, "REVERSE")
r += steps(12100, 14000, 0, 0, True, "PARK")
write("normal_parking", r)

# 2. reverse misapplication: brake released, then sudden surge in R
r = steps(0, 2000, 0, 0, True, "PARK")
r += steps(2100, 3000, 0, 0, True, "REVERSE")
r += steps(3100, 5000, 0, 0, False, "REVERSE")
r += steps(5100, 6000, 0, 27, False, "REVERSE")      # +3 kph per 100 ms
r += steps(6100, 14000, 28, 35, False, "REVERSE")
write("reverse_misapplication", r)

# 3. forward low-speed misapplication in D: 5 -> 25 kph in 1 s, brake off
r = steps(0, 3000, 5, 5, False, "DRIVE")
r += steps(3100, 4100, 6, 25, False, "DRIVE")
r += steps(4200, 12000, 26, 30, False, "DRIVE")
write("forward_lowspeed_misapplication", r)

# 4. normal highway acceleration from 60 km/h
write("highway_accel", steps(0, 10000, 60, 100, False, "DRIVE"))

# 5. brake held while the car accelerates (e.g. hill / brake-held launch): no alarm
write("brake_held_accel", steps(0, 3000, 0, 30, True, "DRIVE"))

# 6. PARK -> REVERSE three times: t=2s (prompt), t=2 min (suppressed), t=12 min (prompt)
r = []
for base in (0, 120_000, 720_000):
    r += [row(base, 0, True, "PARK"), row(base + 1000, 0, True, "PARK"),
          row(base + 2000, 0, True, "REVERSE"), row(base + 4000, 0, True, "REVERSE"),
          row(base + 6000, 0, True, "PARK")]
write("park_to_reverse", r)

# 7. airbag deployed during a drive
r = steps(0, 5000, 50, 50, False, "DRIVE", 1000)
r += [row(6000, 20, True, "DRIVE", airbag=1), row(7000, 0, True, "DRIVE"), row(8000, 0, True, "PARK")]
write("airbag", r)

# 8. ABS telltale ON continuously for 4 days (sample every 12 h)
H12 = 12 * 3600 * 1000
write("telltale_3days", [row(i * H12, 0, True, "PARK", tt="ABS") for i in range(9)])

# 9. commute trip: one hard accel (contiguous), one hard brake
speeds = [0, 0, 5, 10, 15, 20, 32, 40, 40, 40, 28, 20, 10, 0]
r = [row(0, 0, True, "PARK"), row(1000, 0, True, "PARK")]
for i, v in enumerate(speeds):
    r.append(row(2000 + i * 1000, v, i >= 10, "DRIVE"))
r.append(row(16000, 0, True, "PARK"))
write("trip_commute", r)
print("ok")
