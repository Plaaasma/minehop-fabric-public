#!/usr/bin/env python3
"""
Reproduce + validate SIM-block 'fix d' (the only pass-all candidate).

fix d (per SIM): the wishSpeedCap is reinterpreted as a CS-style FORWARD-SPEED
projection limit that sits ABOVE the current along-wish speed, so the headroom
addSpeed = (cap_abs) - dot stays positive across a wide angular band at low speed
(opens 'wide strafe'), while accelSpeed magnitude uses the CAPPED wish (bounds the
backward dump that kills the 360). sv_airaccelerate is LOWERED so a 6-tick jump
(jump6) tops out near 14 b/s rather than exploding.

Methodology mirrors sim_air.py (4-substep interpolated air tick, velocity in b/t).
Reports the user's exact metrics: wide(55) vs tight(80) gain @260, holdD-360 %,
holdW-360 %, jump6, base, jump height @impulse 300.
"""
import math

U = 800.0
DT = 1.0 / 20.0
SUBSTEPS = 4
WALK = 0.10
SPEED_MUL = 3.25
WISH = WALK * SPEED_MUL          # 0.325 b/t
SURF = 1.0


def wrap(a):
    a %= 360.0
    if a >= 180.0:
        a -= 360.0
    if a < -180.0:
        a += 360.0
    return a


def wishdir(sI, fI, yaw):
    d = math.hypot(sI, fI)
    if d < 1e-9:
        return 0.0, 0.0
    nx, nz = sI / d, fI / d
    r = math.radians(yaw)
    sn, cs = math.sin(r), math.cos(r)
    wx, wz = nx * cs - nz * sn, nz * cs + nx * sn
    m = math.hypot(wx, wz)
    return (wx / m, wz / m) if m > 1e-9 else (0.0, 0.0)


def heading(vx, vz):
    return math.degrees(math.atan2(vz, vx))


# ---- fix d accelerate ----
def accel_d(vx, vz, wx, wz, accel, cap_abs, ff):
    """fix d: cap_abs is an absolute forward-projection ceiling (b/t).
       addSpeed = cap_abs - dot ; accelSpeed uses CAPPED wish magnitude (bounded)."""
    cur = vx * wx + vz * wz
    add = cap_abs - cur
    if add <= 0.0:
        return vx, vz
    capped = min(WISH, cap_abs)
    asp = accel * DT * ff * capped * SURF      # bounded magnitude (fix a/c)
    if asp > add:
        asp = add
    return vx + wx * asp, vz + wz * asp


def air_tick(vx, vz, sI, fI, prevYaw, curYaw, accel, cap_abs):
    dyaw = wrap(curYaw - prevYaw)
    ax, az = vx, vz
    ff = 1.0 / SUBSTEPS
    for s in range(SUBSTEPS):
        yaw = prevYaw + dyaw * (s + 1) / SUBSTEPS
        wx, wz = wishdir(sI, fI, yaw)
        if wx == 0.0 and wz == 0.0:
            continue
        ax, az = accel_d(ax, az, wx, wz, accel, cap_abs, ff)
    return ax, az


def strafe_gain(off, accel, cap_abs, start_ups=260.0, ticks=20):
    vx, vz = start_ups / U, 0.0
    prevYaw = heading(vx, vz) + off
    for _ in range(ticks):
        cur = heading(vx, vz) + off
        vx, vz = air_tick(vx, vz, 1.0, 0.0, prevYaw, cur, accel, cap_abs)
        prevYaw = cur
    return (math.hypot(vx, vz) * 20.0) - (start_ups / U * 20.0)


def holdD_360(accel, cap_abs, start_ups=600.0, ticks=400, off=85.0):
    vx, vz = start_ups / U, 0.0
    prevYaw = heading(vx, vz) + off
    swept, last = 0.0, heading(vx, vz)
    for _ in range(ticks):
        h = heading(vx, vz)
        swept += wrap(h - last)
        last = h
        if abs(swept) >= 360.0:
            break
        cur = h + off
        vx, vz = air_tick(vx, vz, 1.0, 0.0, prevYaw, cur, accel, cap_abs)
        prevYaw = cur
    return math.hypot(vx, vz) * U / start_ups * 100.0, swept


def holdW_360(accel, cap_abs, start_ups=600.0, ticks=5):
    """Hold W, spin VIEW a full 360 over `ticks`. Expect heavy loss (wrong technique)."""
    vx, vz = start_ups / U, 0.0
    startYaw = -90.0
    prevYaw = startYaw
    for t in range(ticks):
        cur = startYaw + 360.0 * (t + 1) / ticks
        vx, vz = air_tick(vx, vz, 0.0, 1.0, prevYaw, cur, accel, cap_abs)
        prevYaw = cur
    return math.hypot(vx, vz) * U / start_ups * 100.0


def jump6(accel, cap_abs, start_bps=6.5):
    vx, vz = start_bps / 20.0, 0.0
    prevYaw = heading(vx, vz) + 90.0
    for _ in range(6):
        cur = heading(vx, vz) + 90.0
        vx, vz = air_tick(vx, vz, 1.0, 0.0, prevYaw, cur, accel, cap_abs)
        prevYaw = cur
    return math.hypot(vx, vz) * 20.0


def jump_height(impulse, gravity=800.0):
    v0 = impulse / 800.0
    g = gravity * DT / 800.0
    h, v = 0.0, v0
    while v > 0:
        h += v
        v -= g
    return h


print("Sweep fix-d params: accel x cap_abs -> wide55, tight80, wide>tight, holdD360, jump6")
print(f"{'accel':>6} {'cap(u/s)':>9} {'wide55':>8} {'tight80':>8} {'w>t':>5} "
      f"{'holdD%':>8} {'swept':>6} {'jump6':>7}")
best = None
for accel in [40, 50, 55, 60, 70, 100]:
    for cap_ups in [260, 280, 300, 320, 360]:
        cap_abs = cap_ups / U
        w = strafe_gain(55.0, accel, cap_abs)
        t = strafe_gain(80.0, accel, cap_abs)
        hd, sw = holdD_360(accel, cap_abs)
        j6 = jump6(accel, cap_abs)
        ok = (w > t) and (hd >= 85.0) and (12.0 <= j6 <= 16.0)
        if ok and best is None:
            best = (accel, cap_ups)
        print(f"{accel:6d} {cap_ups:9d} {w:8.2f} {t:8.2f} {str(w>t):>5} "
              f"{hd:8.2f} {sw:6.0f} {j6:7.2f}{'  <==' if ok and best==(accel,cap_ups) else ''}")

print()
if best is None:
    best = (55, 300)
accel, cap_ups = best
cap_abs = cap_ups / U
print("=" * 70)
print(f"CHOSEN fix-d params: sv_airaccelerate={accel}, sv_maxairspeed={cap_ups}")
print("=" * 70)
w55 = strafe_gain(55.0, accel, cap_abs)
t80 = strafe_gain(80.0, accel, cap_abs)
hd, sw = holdD_360(accel, cap_abs)
hw = holdW_360(accel, cap_abs)
j6 = jump6(accel, cap_abs)
base = WISH * 20.0
jh = jump_height(300.0)
print(f"  [1] holdD-360 retention : {hd:7.2f}%  swept {sw:.0f}d  (>=85)  PASS={hd>=85.0}")
print(f"  [2] wide(55) gain       : {w55:7.2f} b/s")
print(f"      tight(80) gain      : {t80:7.2f} b/s")
print(f"      wide > tight        : {w55>t80}  PASS={w55>t80}")
print(f"  [3] jump6               : {j6:7.2f} b/s  (~14)   PASS={12.0<=j6<=16.0}")
print(f"  [4] base walk           : {base:7.2f} b/s  (6.5)  PASS={abs(base-6.5)<0.01}")
print(f"  [5] jump height @300    : {jh:7.3f} blk  (~1.4)  PASS={1.2<=jh<=1.6}")
print(f"  [info] holdW-360        : {hw:7.2f}%  (expected heavy loss - wrong technique)")
print()
print("ALL TARGETS:", all([hd>=85.0, w55>t80, 12.0<=j6<=16.0,
                           abs(base-6.5)<0.01, 1.2<=jh<=1.6]))
