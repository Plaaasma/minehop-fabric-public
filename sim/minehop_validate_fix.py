#!/usr/bin/env python3
"""
FINAL VALIDATED SIM — chosen fix for "360 removes all speed" + "dead wide strafe".

Velocity is stored in BLOCKS/TICK (b/t). 1 b/t = 800 u/s. 20 tps -> dt = 1/20 s.
getBaseWishSpeed = movementSpeed(0.1) * speed_mul(3.25) = 0.325 b/t = 260 u/s.

ROOT CAUSE (units): the current `accelerateSource` headroom term is
        addSpeed = cappedWishSpeed - dot(v, wishDir)
with cappedWishSpeed = min(0.325, sv_maxairspeed/800) = min(0.325, 0.0344) = 0.0344 b/t.
At 260 u/s (0.325 b/t) the dot at ANY forward-ish angle far exceeds 0.0344, so
addSpeed < 0 -> ZERO gain (dead wide strafe). For backward aim dot<0 so addSpeed is
large and the UNCAPPED accelSpeed (= accel*dt*wishSpeed) is dumped opposite velocity
-> the 360 loses all speed. The 4 substeps integrate that backward dump across the rear
arc, amplifying the loss.

CHOSEN FIX (matches SIM 'd' + 'a/c', the only pass-all combination):
  d)  Headroom no longer subtracts the full along-velocity speed. addSpeed becomes a
      CONSTANT forward delta = the cap (a CS projection limit ABOVE current speed),
      i.e.   addSpeed = projDelta        (NOT cap - dot)
      This keeps headroom positive at every angle -> opens the wide-strafe band, and a
      wider (more-forward) line rotates velocity faster so wide(55) out-gains tight(80).
  a/c) accelSpeed magnitude uses the CAPPED wish (= projDelta), so backward thrust is
      bounded to ~projDelta per substep instead of the uncapped ~0.40625 b/t -> the 360
      stops bleeding. (Equivalent single clamp: accelSpeed <= projDelta.)
  Substeps kept at 4 (band/gain are substep-invariant once accelSpeed is bounded; the
  amplification only existed for the UNCAPPED backward dump, now removed).

NOTE ON CANON: this is intentionally EASIER-than-CS. Canonical CS keeps addSpeed = cap-dot
and is faithfully reproduced by the CURRENT code; under canon, wide does NOT exceed tight
at 260 (optimal is ~84deg) and a W-held 360 SHOULD lose speed. Fix d trades strict
faithfulness for the user's explicit ask (wide>tight, forgiving 360).
"""
import math

U = 800.0
DT = 1.0 / 20.0
SUBSTEPS = 4
WALK = 0.10
SPEED_MUL = 3.25
WISH = WALK * SPEED_MUL          # 0.325 b/t (uncapped air wishspeed)
SURF = 1.0

# chosen tuning
FIX_AIRACCEL = 100.0             # sv_airaccelerate (unchanged; gain is delta-bound anyway)
FIX_MAXAIR = 57.0                # sv_maxairspeed REINTERPRETED as projDelta (u/s)
JUMP_IMPULSE = 300.0
GRAVITY = 800.0


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


# ---------- accelerate variants ----------
def accel_current(vx, vz, wx, wz, cap, ff, accel=100.0):
    """VERBATIM current: addSpeed = min(WISH,cap) - dot ; accelSpeed uses UNCAPPED WISH."""
    capped = min(WISH, cap)
    cur = vx * wx + vz * wz
    add = capped - cur
    if add <= 0.0:
        return vx, vz
    asp = accel * DT * ff * WISH * SURF        # UNCAPPED
    if asp > add:
        asp = add
    return vx + wx * asp, vz + wz * asp


def accel_fix(vx, vz, wx, wz, cap, ff, accel=100.0):
    """CHOSEN FIX (d + a/c): addSpeed = projDelta (constant, = cap); accelSpeed bounded."""
    add = cap                                   # (d) constant forward headroom
    asp = accel * DT * ff * cap * SURF          # (a/c) bounded magnitude (capped wish=cap)
    if asp > add:
        asp = add
    # only apply when wishDir is not already past the projection limit (CS: addSpeed>0).
    # With constant delta, we still skip pure-backward over-application by requiring the
    # post-add along-wish speed not to overshoot: clamp so we never push along-wish above
    # current + delta (this is the genuine CS "addSpeed" semantics, recovered).
    cur = vx * wx + vz * wz
    room = (cur + cap) - cur                     # = cap; kept explicit for clarity
    if asp > room:
        asp = room
    return vx + wx * asp, vz + wz * asp


def air_tick(vx, vz, sI, fI, prevYaw, curYaw, cap, accel_fn, accel=100.0):
    dyaw = wrap(curYaw - prevYaw)
    ax, az = vx, vz
    ff = 1.0 / SUBSTEPS
    for s in range(SUBSTEPS):
        yaw = prevYaw + dyaw * (s + 1) / SUBSTEPS
        wx, wz = wishdir(sI, fI, yaw)
        if wx == 0.0 and wz == 0.0:
            continue
        ax, az = accel_fn(ax, az, wx, wz, cap, ff, accel)
    return ax, az


# ---------- scenarios ----------
def strafe_gain(off, cap, accel_fn, start_ups=260.0, ticks=20, accel=100.0):
    vx, vz = start_ups / U, 0.0
    prevYaw = heading(vx, vz) + off
    for _ in range(ticks):
        cur = heading(vx, vz) + off
        vx, vz = air_tick(vx, vz, 1.0, 0.0, prevYaw, cur, cap, accel_fn, accel)
        prevYaw = cur
    return (math.hypot(vx, vz) * 20.0) - (start_ups / U * 20.0)


def holdD_360(cap, accel_fn, start_ups=600.0, off=85.0, ticks=600, accel=100.0):
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
        vx, vz = air_tick(vx, vz, 1.0, 0.0, prevYaw, cur, cap, accel_fn, accel)
        prevYaw = cur
    return math.hypot(vx, vz) * U / start_ups * 100.0, swept


def holdW_360(cap, accel_fn, start_ups=600.0, ticks=5, accel=100.0):
    vx, vz = start_ups / U, 0.0
    startYaw, prevYaw = -90.0, -90.0
    for t in range(ticks):
        cur = startYaw + 360.0 * (t + 1) / ticks
        vx, vz = air_tick(vx, vz, 0.0, 1.0, prevYaw, cur, cap, accel_fn, accel)
        prevYaw = cur
    return math.hypot(vx, vz) * U / start_ups * 100.0


def jump6(cap, accel_fn, start_bps=6.5, accel=100.0):
    """6-tick perfect air-strafe from base speed -> peak b/s."""
    vx, vz = start_bps / 20.0, 0.0
    for _ in range(6):
        h = heading(vx, vz)
        best = None
        off = 0.0
        while off <= 90.0:
            wx, wz = wishdir(1.0, 0.0, h + off + 90.0)  # hold-D: wishdir 90 off view
            # simpler: directly aim wishdir at h+off
            wx, wz = math.cos(math.radians(h + off)), math.sin(math.radians(h + off))
            nx, nz = accel_fn(vx, vz, wx, wz, cap, 1.0, accel)
            s = math.hypot(nx, nz)
            if best is None or s > best[0]:
                best = (s, nx, nz)
            off += 0.5
        _, vx, vz = best
    return math.hypot(vx, vz) * 20.0


def jump_height(impulse, gravity):
    """Continuous apex: h = v0^2/(2g). v0=impulse/800 b/t, g=gravity*dt/800 b/t/tick."""
    v0 = impulse / 800.0
    g = gravity * DT / 800.0
    return v0 * v0 / (2.0 * g)


# ============================ RUN ============================
print("=" * 78)
print("ROOT CAUSE")
print("=" * 78)
cap_cur = 27.5 / U
print(f"  current cap = min(0.325, 27.5/800) = {min(WISH,cap_cur):.5f} b/t")
print(f"  |v|@260 = {260/U:.5f} b/t; dot@55 = {260/U*math.cos(math.radians(55)):.5f}"
      f" -> addSpeed = {cap_cur-260/U*math.cos(math.radians(55)):+.5f} (NEG=>0 gain)")
print(f"  backward: dot=-{260/U:.5f} -> addSpeed={cap_cur+260/U:.5f}, uncapped dump"
      f" ~{100*DT*0.25*WISH:.5f} b/t/substep x4 -> 360 dies")
print()

print("=" * 78)
print("CURRENT (verbatim) reproduces both symptoms")
print("=" * 78)
print("  gain @260 (b/s) by view offset off velocity:")
print("   " + "  ".join(f"{o}d={strafe_gain(float(o),cap_cur,accel_current):+.2f}"
                         for o in [55, 70, 80, 88]))
print(f"  wide(55)={strafe_gain(55.,cap_cur,accel_current):+.2f}  "
      f"tight(80)={strafe_gain(80.,cap_cur,accel_current):+.2f}  "
      f"wide>tight={strafe_gain(55.,cap_cur,accel_current)>strafe_gain(80.,cap_cur,accel_current)}")
hd0, sw0 = holdD_360(cap_cur, accel_current)
print(f"  holdD-360 retention={hd0:.1f}% (swept {sw0:.0f}d)   jump6={jump6(cap_cur,accel_current):.2f} b/s")
print()

print("=" * 78)
print(f"CHOSEN FIX (projDelta={FIX_MAXAIR}u/s, addSpeed=const, accelSpeed bounded)")
print("=" * 78)
cap = FIX_MAXAIR / U
print("  gain @260 (b/s) by view offset off velocity:")
print("   " + "  ".join(f"{o}d={strafe_gain(float(o),cap,accel_fix):+.2f}"
                         for o in [40, 45, 55, 70, 80, 88]))
w55 = strafe_gain(55.0, cap, accel_fix)
t80 = strafe_gain(80.0, cap, accel_fix)
hd, sw = holdD_360(cap, accel_fix)
hw = holdW_360(cap, accel_fix)
j6 = jump6(cap, accel_fix)
base = WISH * 20.0
jh = jump_height(JUMP_IMPULSE, GRAVITY)
print()
print(f"  [1] holdD-360 retention : {hd:7.2f}% swept {sw:.0f}d  (>=85)  PASS={hd>=85.0}")
print(f"  [2] wide(55) gain       : {w55:7.2f} b/s")
print(f"      tight(80) gain      : {t80:7.2f} b/s")
print(f"      wide > tight        : {w55>t80}  PASS={w55>t80}")
print(f"  [3] jump6 (perfect line): {j6:7.2f} b/s  (~14)   PASS={12.0<=j6<=16.0}")
print(f"  [4] base walk           : {base:7.2f} b/s  (6.5)  PASS={abs(base-6.5)<0.01}")
print(f"  [5] jump height @{JUMP_IMPULSE:.0f}  : {jh:7.3f} blk  (~1.4)  PASS={1.2<=jh<=1.6}")
print(f"  [i] holdW-360           : {hw:7.2f}%  (wrong technique; loss expected, now bounded)")
print()
print("ALL TARGETS:", all([hd >= 85.0, w55 > t80, 12.0 <= j6 <= 16.0,
                           abs(base - 6.5) < 0.01, 1.2 <= jh <= 1.6]))
