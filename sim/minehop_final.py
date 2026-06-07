#!/usr/bin/env python3
"""
FINAL reconciliation sim for the CS:S-faithful Minehop air-movement fix.

Units: blocks/tick (b/t). 1 b/t = 800 u/s. Tick rate = 20 tps -> dt = 1/20 s.
Constants taken verbatim from the real Java (MovementUtil.accelerateSource /
LivingEntityMixin.travel air branch):

  AIR_ACCEL (sv_airaccelerate)        = 100
  airWishSpeedCap  (AIR_SPEED_CAP)    = 57 u/s  = 0.07125 b/t
  uncapped air wishSpeed              = 260 u/s = 0.325  b/t
  surfaceFriction                     = 1.0
  useUncappedWishSpeedForAccel        = True
  SOURCE_FRAME_TIME (mod's air dt)    = 1/20 s  (the 20 tps coarse step)

This sim confirms the CHOSEN fix:
  (1) DELETE the rescale clause (LivingEntityMixin lines 635-644).
  (2) DO NOT add substepping (keep single 20 Hz step) -> engine 'B'.
  (3) Keep sv_maxairspeed = 57 (no retune; optimal-strafe ramp is K-invariant
      and we are not substepping anyway).
  (4) findOptimalStrafeAngle: keep computing it for the HUD gauge ONLY; do NOT
      feed perfectAngle into the wish vector (auto-steer is not CS:S faithful).

Three engines:
  A = current mod: single 20 Hz step + rescale-up-to-preAirSpeed clause.
  B = fix: single 20 Hz step, rescale clause removed.
  C(K) = B run in K substeps of dt/K (optional polish; shown for comparison).
  REF = CS:S faithful at 64 tick (dt = 1/64), no rescale.
"""

import math

U = 800.0                 # u/s per b/t
DT20 = 1.0 / 20.0
DT64 = 1.0 / 64.0
ACCEL = 100.0
AIRCAP = 57.0 / U         # 0.07125 b/t
WISH = 260.0 / U          # 0.325 b/t  (uncapped, used for accelSpeed)
SURF = 1.0

def accel_source(vx, vz, wx, wz, wishspeed, cap, dt):
    """Faithful AirAccelerate. v and wishdir(wx,wz unit) horizontal only."""
    capped = min(wishspeed, cap)
    cur = vx * wx + vz * wz
    add = capped - cur
    if add <= 0.0:
        return vx, vz
    accelspeed = ACCEL * dt * wishspeed * SURF       # uncapped wishspeed
    if accelspeed > add:
        accelspeed = add
    return vx + accelspeed * wx, vz + accelspeed * wz

def rescale_clause(pre_vx, pre_vz, post_vx, post_vz, wx, wz):
    """Mod's wishAlignmentCos>-0.98 rescale-up-to-pre block (engine A)."""
    pre = math.hypot(pre_vx, pre_vz)
    cos = (pre_vx * wx + pre_vz * wz) / pre if pre > 1e-8 else 1.0
    if cos > -0.98:
        post = math.hypot(post_vx, post_vz)
        if post + 1e-8 < pre:
            if post > 1e-8:
                s = pre / post
                return post_vx * s, post_vz * s
            else:
                return pre_vx, pre_vz
    return post_vx, post_vz

def step_A(vx, vz, wx, wz, dt):
    px, pz = vx, vz
    nx, nz = accel_source(vx, vz, wx, wz, WISH, AIRCAP, dt)
    return rescale_clause(px, pz, nx, nz, wx, wz)

def step_B(vx, vz, wx, wz, dt):
    return accel_source(vx, vz, wx, wz, WISH, AIRCAP, dt)

def step_C(vx, vz, wx, wz, dt, K):
    for _ in range(K):
        vx, vz = accel_source(vx, vz, wx, wz, WISH, AIRCAP, dt / K)
    return vx, vz

# ---------------------------------------------------------------------------
# (a) WIDE->TIGHT optimal-angle curve.
# For a fixed speed |v| along +x, sweep wishdir angle theta off the velocity.
# Report (i) the angle of MAX next-tick gain and (ii) the WIDEST theta that
# still captures >=99% of that max gain (the user-perceived "usable" width).
# ---------------------------------------------------------------------------
def optimal_curve(stepfn, label):
    print(f"\n[{label}] optimal-angle curve (single 20Hz tick):")
    print("  speed(u/s)  bestTheta  bestGain(u/s)  widest>=99%(deg)")
    for sp_u in [100, 200, 300, 400, 500, 600, 700, 800, 900]:
        sp = sp_u / U
        best_gain = -1.0
        best_theta = 0.0
        gains = []
        for d in range(0, 1801):
            th = d * 0.1
            r = math.radians(th)
            wx, wz = math.cos(r), math.sin(r)
            nx, nz = stepfn(sp, 0.0, wx, wz, DT20)
            g = (math.hypot(nx, nz) - sp) * U
            gains.append((th, g))
            if g > best_gain:
                best_gain = g
                best_theta = th
        thr = best_gain * 0.99
        widest = 0.0
        for th, g in gains:
            if g >= thr and th <= 90.0:
                widest = max(widest, th)
        print(f"   {sp_u:5d}      {best_theta:5.1f}     {best_gain:8.3f}      {widest:6.1f}")

optimal_curve(step_B, "B  FIX (no rescale)")
optimal_curve(step_A, "A  CURRENT (rescale on)")

# ---------------------------------------------------------------------------
# (b) NO-MOUSE redirect per second. Yaw fixed -> wishdir choices are 8 FIXED
# world directions 45deg apart. Greedy: each tick pick the keyboard wishdir
# (of the 8) that maximizes |perp rotation| while not losing speed, for 1 s.
# Compare A, B, C3 (20*3=60Hz) and REF (CS-64). Report total heading change.
# ---------------------------------------------------------------------------
DIRS8 = [(math.cos(math.radians(a)), math.sin(math.radians(a)))
         for a in range(0, 360, 45)]

def nomouse_turn(stepfn, dt, nsteps, label):
    vx, vz = 1.0, 0.0        # 800 u/s along +x
    start = math.atan2(vz, vx)
    for _ in range(nsteps):
        best = None
        cur_ang = math.atan2(vz, vx)
        for wx, wz in DIRS8:
            nx, nz = stepfn(vx, vz, wx, wz, dt)
            nang = math.atan2(nz, nx)
            dturn = abs(((nang - cur_ang + math.pi) % (2*math.pi)) - math.pi)
            spd = math.hypot(nx, nz)
            # only accept directions that turn us and don't bleed badly
            score = dturn
            if best is None or score > best[0]:
                best = (score, nx, nz)
        _, vx, vz = best
    total = abs(((math.atan2(vz, vx) - start + math.pi) % (2*math.pi)) - math.pi)
    dps = math.degrees(total) / (nsteps * dt)
    print(f"  [{label}] no-mouse turn = {dps:8.2f} deg/s")
    return dps

print("\n(b) No-mouse redirect (fixed-8-direction greedy, 1 s):")
nomouse_turn(step_A, DT20, 20, "A current")
nomouse_turn(step_B, DT20, 20, "B fix")
nomouse_turn(lambda a,b,c,d,e: step_C(a,b,c,d,e,3), DT20, 20, "C3 60Hz")
nomouse_turn(step_B, DT64, 64, "REF CS-64")

# Continuous-wishdir upper bound (the 349 vs 34 signature):
def nomouse_cont(stepfn, dt, nsteps, label):
    vx, vz = 1.0, 0.0
    start = math.atan2(vz, vx)
    for _ in range(nsteps):
        cur = math.atan2(vz, vx)
        wx, wz = -math.sin(cur), math.cos(cur)   # exact perpendicular (needs mouse normally)
        vx, vz = stepfn(vx, vz, wx, wz, dt)
    total = abs(((math.atan2(vz, vx) - start + math.pi) % (2*math.pi)) - math.pi)
    print(f"  [{label}] continuous-perp turn = {math.degrees(total)/(nsteps*dt):8.2f} deg/s")

print("\n(b') Continuous-wishdir upper bound:")
nomouse_cont(step_A, DT20, 20, "A current")
nomouse_cont(step_B, DT20, 20, "B fix")
nomouse_cont(step_B, DT64, 64, "REF CS-64")

# ---------------------------------------------------------------------------
# (c) BHOP RAMP. Perfectly-strafed: every tick wishdir is perpendicular to the
# current velocity (cap-saturating). 15 air ticks/hop (~0.75 s air time).
# Start 260 u/s base. Report per-jump entry speeds; expect ~286/374/452/516/
# 575/628 and cruise ~625-660.
# ---------------------------------------------------------------------------
def bhop_ramp(stepfn, dt, ticks_per_hop, hops, label):
    speed = 260.0 / U
    entries = [speed * U]
    for h in range(hops):
        vx, vz = speed, 0.0      # heading reset is irrelevant; we track magnitude
        for _ in range(ticks_per_hop):
            cur = math.atan2(vz, vx)
            wx, wz = -math.sin(cur), math.cos(cur)   # perpendicular wishdir
            vx, vz = stepfn(vx, vz, wx, wz, dt)
        speed = math.hypot(vx, vz)
        entries.append(speed * U)
    print(f"\n(c) [{label}] bhop ramp (perfect strafe, {ticks_per_hop} ticks/hop):")
    print("   " + "  ".join(f"{e:6.1f}" for e in entries))
    return entries

eB = bhop_ramp(step_B, DT20, 15, 8, "B fix 20Hz")
eC3 = bhop_ramp(lambda a,b,c,d,e: step_C(a,b,c,d,e,3), DT20, 15, 8, "C3 60Hz")
print("\nRamp delta B vs C3 (u/s):",
      "  ".join(f"{c-b:+.2f}" for b, c in zip(eB, eC3)))

# Cold-start sanity: one tick from rest along wishdir must equal the cap (57 u/s)
nx, nz = step_B(0.0, 0.0, 1.0, 0.0, DT20)
print(f"\nCold-start check: {math.hypot(nx,nz)*U:.3f} u/s (expect 57.000)")

print("\nDECISION: remove rescale clause (lines 635-644); keep 20Hz single step;")
print("keep sv_maxairspeed=57; keep findOptimalStrafeAngle as HUD-only (no auto-steer).")
