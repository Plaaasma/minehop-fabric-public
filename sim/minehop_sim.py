"""
Minehop movement simulation: quantify BUG1 (wide strafe never works) and
BUG2 (90-degree no-mouse redirect), and validate the fix.

Faithful to LivingEntityMixin.travel (air branch) + MovementUtil.accelerateSource.

Units: blocks/tick (b/t). 1 block = 40 source units; 20 tps.
  u/s -> b/t :  b/t = u/s / 800
  b/t -> u/s :  u/s = b/t * 800

Config (from context, matches Java):
  sv_airaccelerate     = 100
  sv_maxairspeed (cap) = 57 u/s  -> airCap = 57/800 = 0.07125 b/t
  air wishspeed        = 0.325 b/t (260 u/s, uncapped, used for accelSpeed magnitude)
  tick frametime       = 1/20 s
"""

import math

# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------
U2B = 1.0 / 800.0          # u/s -> b/t
B2U = 800.0                # b/t -> u/s
TICK_DT = 1.0 / 20.0       # Minehop server tick (s)

AIR_ACCEL   = 100.0        # sv_airaccelerate
AIR_WISH    = 0.325        # uncapped wishspeed b/t (used for accelSpeed magnitude)
AIR_CAP     = 57.0 * U2B   # 0.07125 b/t
SURF_FRIC   = 1.0

# Real Source reference tickrate for "feel" comparison
CS_TICK = 64.0
CS_DT   = 1.0 / CS_TICK

# ---------------------------------------------------------------------------
# Core Source accel (operates on a 2D horizontal velocity, b/t)
# Returns NEW velocity (vx, vz). frame_dt is the sub/step time in seconds.
# cap is a SPEED (b/t); accel magnitude uses uncapped wishspeed.
# ---------------------------------------------------------------------------
def accelerate_source(vx, vz, wdx, wdz, wishspeed, cap, accel, frame_dt,
                      uncapped_for_accel=True):
    capped_wish = min(wishspeed, cap)
    current = vx * wdx + vz * wdz          # dot(vel, wishdir)
    add = capped_wish - current
    if add <= 0:
        return vx, vz
    accel_wish = wishspeed if uncapped_for_accel else capped_wish
    accel_speed = accel * frame_dt * accel_wish * SURF_FRIC
    if accel_speed > add:
        accel_speed = add
    return vx + wdx * accel_speed, vz + wdz * accel_speed


def hspeed(vx, vz):
    return math.hypot(vx, vz)


# ---------------------------------------------------------------------------
# ENGINE A: CURRENT (single 20Hz step + rescale-up-to-preAirSpeed clause)
# ENGINE B: NO-RESCALE (delete the clause)
# ENGINE C: SUBSTEP (no rescale, K substeps each frame_dt = TICK_DT/K, cap per substep)
# Each takes velocity (b/t) and a wishdir unit vector, returns new velocity (b/t).
# ---------------------------------------------------------------------------
def step_A(vx, vz, wdx, wdz):
    pre = hspeed(vx, vz)
    cos_align = 1.0
    if pre > 1e-8:
        cos_align = (vx * wdx + vz * wdz) / pre
    nx, nz = accelerate_source(vx, vz, wdx, wdz, AIR_WISH, AIR_CAP, AIR_ACCEL, TICK_DT)
    if cos_align > -0.98:
        post = hspeed(nx, nz)
        if post + 1e-8 < pre:
            if post > 1e-8:
                r = pre / post
                nx, nz = nx * r, nz * r
            else:
                nx, nz = vx, vz
    return nx, nz


def step_B(vx, vz, wdx, wdz):
    return accelerate_source(vx, vz, wdx, wdz, AIR_WISH, AIR_CAP, AIR_ACCEL, TICK_DT)


def step_C(vx, vz, wdx, wdz, K):
    sub_dt = TICK_DT / K
    for _ in range(K):
        vx, vz = accelerate_source(vx, vz, wdx, wdz, AIR_WISH, AIR_CAP, AIR_ACCEL, sub_dt)
    return vx, vz


# Real CS:S single tick at 64Hz: same accel, dt = 1/64, NO rescale.
def step_CS(vx, vz, wdx, wdz):
    return accelerate_source(vx, vz, wdx, wdz, AIR_WISH, AIR_CAP, AIR_ACCEL, CS_DT)


# ---------------------------------------------------------------------------
# Helpers: wishdir from an absolute heading angle (deg), 2D.
# ---------------------------------------------------------------------------
def wishdir_from_angle(deg):
    r = math.radians(deg)
    return math.cos(r), math.sin(r)

def vel_from(speed_bt, deg):
    r = math.radians(deg)
    return speed_bt * math.cos(r), speed_bt * math.sin(r)

def heading_deg(vx, vz):
    return math.degrees(math.atan2(vz, vx))


# ===========================================================================
# (1) Optimal angle vs speed table for the FAITHFUL model (engine B physics:
#     plain Source accel, the real one). Angle = angle between velocity and
#     wishdir that maximizes next-step horizontal speed.
#     We sweep candidate wishdir angles relative to current velocity heading,
#     fine grid, pick the max post-speed. Use 64-tick faithful (step_CS) AND
#     20-tick faithful (step_B) -- the optimum angle is invariant to dt scale
#     except via the per-tick add cap; report 20-tick (the engine we ship).
# ===========================================================================
def optimal_angle_for_speed(speed_u, stepfn):
    speed_b = speed_u * U2B
    vx, vz = speed_b, 0.0   # velocity along +x
    best_ang, best_gain = 0.0, -1.0
    # search wishdir angle offset from velocity heading, 0..90 deg (sym)
    a = 0.0
    while a <= 90.0:
        wdx, wdz = wishdir_from_angle(a)
        nx, nz = stepfn(vx, vz, wdx, wdz)
        post = hspeed(nx, nz)
        if post > best_gain:
            best_gain, best_ang = post, a
        a += 0.1
    gain_u = (best_gain - speed_b) * B2U
    return best_ang, gain_u


def build_optimal_table():
    rows = []
    for su in range(100, 901, 100):
        ang_b, gain_b = optimal_angle_for_speed(su, step_B)     # 20-tick faithful
        ang_cs, gain_cs = optimal_angle_for_speed(su, step_CS)  # 64-tick faithful
        rows.append((su, ang_b, gain_b, ang_cs, gain_cs))
    return rows


# ===========================================================================
# (2) BUG1: does engine A collapse wide low-speed strafing?
#     At a low speed, sweep wishdir angle relative to velocity. In a faithful
#     model the post-step speed varies with angle (wide is best at low speed).
#     If engine A's rescale forces post == pre for ALL angles where it triggers,
#     then every angle yields the same speed -> wide strafing "never works".
# ===========================================================================
def bug1_angle_sweep(speed_u, stepfn):
    speed_b = speed_u * U2B
    vx, vz = speed_b, 0.0
    out = []
    for a in range(0, 91, 5):
        wdx, wdz = wishdir_from_angle(a)
        nx, nz = stepfn(vx, vz, wdx, wdz)
        out.append((a, hspeed(nx, nz) * B2U))
    return out


# ===========================================================================
# (3) BUG2: max heading change per SECOND with NO view-yaw change.
#     "Cycling the 8 key-relative wishdirs optimally."
#     With fixed yaw, the only wishdirs available are the 8 discrete
#     key combos (relative to a fixed yaw frame). We model yaw=0, so the 8
#     wishdirs are at absolute angles 0,45,90,135,180,225,270,315 deg.
#     Each tick we pick whichever of the 8 maximizes |heading change| while
#     not killing speed too much... but to be fair and match the user's claim
#     ("redirect direction"), we GREEDILY maximize turn rate: each tick choose
#     the wishdir (of 8) that yields the largest signed heading rotation of
#     the velocity vector (turning consistently one way). Integrate for 1 s
#     (20 ticks for A/B/C-driver; report deg/sec). Start at ~300 u/s heading 0.
#
#     Real CS-64 reference: same greedy over 8 dirs but 64 ticks/sec.
# ===========================================================================
EIGHT = [0, 45, 90, 135, 180, 225, 270, 315]

def max_turn_per_sec(stepfn, ticks_per_sec, start_speed_u=300.0, turn_sign=+1):
    vx, vz = vel_from(start_speed_u * U2B, 0.0)
    total_turn = 0.0
    for _ in range(int(ticks_per_sec)):
        h0 = heading_deg(vx, vz)
        best = None
        for wa in EIGHT:
            wdx, wdz = wishdir_from_angle(wa)
            nx, nz = stepfn(vx, vz, wdx, wdz)
            if hspeed(nx, nz) < 1e-9:
                continue
            h1 = heading_deg(nx, nz)
            d = (h1 - h0 + 180) % 360 - 180   # signed shortest delta
            score = turn_sign * d
            if best is None or score > best[0]:
                best = (score, nx, nz, d)
        if best is None:
            break
        _, vx, vz, d = best
        total_turn += d
    return turn_sign * total_turn   # total degrees turned over the second


def max_turn_per_sec_both(stepfn, ticks_per_sec, start_speed_u=300.0):
    return max(max_turn_per_sec(stepfn, ticks_per_sec, start_speed_u, +1),
               max_turn_per_sec(stepfn, ticks_per_sec, start_speed_u, -1))


# Engine C wrapper for given K
def make_step_C(K):
    return lambda vx, vz, wdx, wdz: step_C(vx, vz, wdx, wdz, K)


# ===========================================================================
# (4)/(5): bhop speed ramp. Simulate a glide: a fixed air time of T seconds,
#  player strafes optimally each tick (uses optimal wishdir relative to vel).
#  Compare top speed reached for: A, B, single-step maxair=57, vs C(K) and
#  also C with a rescaled maxair to see if cap must change.
#  We use a typical jump air-time. With sv_gravity=800 and jump_impulse=300:
#    jump vel up = 300 u/s = 0.375 b/t ; gravity accel = 800 u/s^2 = 0.001 b/t per tick?
#    air time to return = 2*v/g = 2*300/800 = 0.75 s -> 15 ticks (20Hz) per hop.
# ===========================================================================
def optimal_wishdir_step(vx, vz, stepfn, search_fn):
    """Pick wishdir (continuous, relative to velocity heading) maximizing post speed, then step."""
    h = heading_deg(vx, vz)
    best = None
    a = -90.0
    while a <= 90.0:
        wa = h + a
        wdx, wdz = wishdir_from_angle(wa)
        nx, nz = stepfn(vx, vz, wdx, wdz)
        post = hspeed(nx, nz)
        if best is None or post > best[0]:
            best = (post, nx, nz)
        a += 0.5
    return best[1], best[2]


def ramp_one_hop(stepfn, air_ticks, start_speed_u):
    vx, vz = vel_from(start_speed_u * U2B, 0.0)
    for _ in range(air_ticks):
        vx, vz = optimal_wishdir_step(vx, vz, stepfn, None)
    return hspeed(vx, vz) * B2U


def ramp_chain(stepfn, air_ticks, hops, start_speed_u):
    speed = start_speed_u
    seq = [speed]
    vx, vz = vel_from(speed * U2B, 0.0)
    for _ in range(hops):
        for _ in range(air_ticks):
            vx, vz = optimal_wishdir_step(vx, vz, stepfn, None)
        speed = hspeed(vx, vz) * B2U
        seq.append(speed)
    return seq


# For C we need air_ticks in substeps-equivalent? No: air_ticks is real 20Hz
# ticks; step_C already does K substeps inside one tick. Good.


# ===========================================================================
# MAIN
# ===========================================================================
def main():
    print("=" * 78)
    print("MINEHOP MOVEMENT SIM — bug quantification & fix validation")
    print("=" * 78)
    print(f"AIR_CAP = {AIR_CAP:.6f} b/t = {AIR_CAP*B2U:.2f} u/s   "
          f"AIR_WISH = {AIR_WISH} b/t = {AIR_WISH*B2U:.0f} u/s   AIR_ACCEL={AIR_ACCEL}")
    # sanity: accelSpeed magnitude per 20Hz tick
    acc20 = AIR_ACCEL * TICK_DT * AIR_WISH
    acc64 = AIR_ACCEL * CS_DT * AIR_WISH
    print(f"accelSpeed (uncapped, per 20Hz tick) = {acc20:.4f} b/t  "
          f"(per 64Hz tick = {acc64:.4f} b/t)")
    print(f"-> 20Hz accelSpeed >> airCap ({acc20:.3f} vs {AIR_CAP:.4f}), "
          f"so add is ALWAYS cap-bound on a cold start. addmax/tick = airCap = {AIR_CAP:.4f} b/t = {AIR_CAP*B2U:.1f} u/s")
    print()

    # ---- consistency check: 64-tick faithful gain at low speed should match
    # CS theory: per-tick gain when perpendicular ~ airCap (full cap captured)
    nx, nz = step_CS(*vel_from(0.0, 0.0), *wishdir_from_angle(0.0))
    print(f"[consistency] CS step from rest along wishdir: speed={hspeed(nx,nz)*B2U:.3f} u/s "
          f"(expect min(accel*dt*wish, cap)= {min(acc64,AIR_CAP)*B2U:.3f})")
    print()

    # -------------------- (1) OPTIMAL ANGLE TABLE --------------------
    print("-" * 78)
    print("(1) OPTIMAL STRAFE ANGLE vs SPEED  (angle between velocity & wishdir")
    print("    that MAXIMIZES next-tick speed; faithful Source accel)")
    print("-" * 78)
    print(f"{'speed u/s':>10} | {'20Hz optA(deg)':>15} {'gain u/s':>10} | "
          f"{'64Hz optA(deg)':>15} {'gain u/s':>10}")
    table = build_optimal_table()
    for su, ab, gb, acs, gcs in table:
        print(f"{su:>10} | {ab:>15.1f} {gb:>10.3f} | {acs:>15.1f} {gcs:>10.3f}")
    opt_table_str = "speed(u/s):optAngle(deg)[20Hz faithful] | " + ", ".join(
        f"{su}:{ab:.0f}" for su, ab, gb, acs, gcs in table)
    print()
    print("Trend (faithful): " + ", ".join(f"{su}u={ab:.0f}deg" for su,ab,gb,acs,gcs in table))
    print()

    # -------------------- (2) BUG1 --------------------
    print("-" * 78)
    print("(2) BUG1: wide low-speed strafing. post-tick speed vs wishdir angle")
    print("    (start speed 150 u/s). Faithful (B) should reward WIDE angles;")
    print("    Engine A (rescale) flattens them.")
    print("-" * 78)
    low = 150.0
    sweep_A = dict(bug1_angle_sweep(low, step_A))
    sweep_B = dict(bug1_angle_sweep(low, step_B))
    print(f"start={low} u/s")
    print(f"{'angle':>6} | {'A speed':>9} | {'B speed':>9}")
    for a in range(0, 91, 5):
        print(f"{a:>6} | {sweep_A[a]:>9.3f} | {sweep_B[a]:>9.3f}")
    A_vals = [sweep_A[a] for a in range(0, 91, 5)]
    B_vals = [sweep_B[a] for a in range(0, 91, 5)]
    A_spread = max(A_vals) - min(A_vals)
    B_spread = max(B_vals) - min(B_vals)
    A_best_angle = max(range(0, 91, 5), key=lambda a: sweep_A[a])
    B_best_angle = max(range(0, 91, 5), key=lambda a: sweep_B[a])
    print(f"\nEngine A: speed spread across angles = {A_spread:.3f} u/s, best angle = {A_best_angle} deg")
    print(f"Engine B: speed spread across angles = {B_spread:.3f} u/s, best angle = {B_best_angle} deg")
    # BUG1 present if A flattens (spread tiny) AND/OR A's best angle is NOT wide while faithful is wide
    bug1_collapse = A_spread < 0.5  # essentially all angles equal
    print(f"BUG1 (A collapses all angles to ~equal speed): {bug1_collapse}  "
          f"(A spread {A_spread:.3f} vs faithful B spread {B_spread:.3f})")
    # Also: does A ever let you GAIN at low speed by going wide?
    A_max_gain = max(A_vals) - low
    B_max_gain = max(B_vals) - low
    print(f"Max low-speed gain — A: {A_max_gain:.3f} u/s, B(faithful): {B_max_gain:.3f} u/s")
    print()

    # -------------------- (3) BUG2 --------------------
    print("-" * 78)
    print("(3) BUG2: max heading change per SECOND, NO view-yaw change,")
    print("    cycling 8 key-relative wishdirs optimally, start ~300 u/s.")
    print("-" * 78)
    K_for_64 = 3  # chosen below; 20*3=60 ~ 64
    stepC3 = make_step_C(3)
    stepC4 = make_step_C(4)
    turn_A  = max_turn_per_sec_both(step_A,  20, 300.0)
    turn_B  = max_turn_per_sec_both(step_B,  20, 300.0)
    turn_C3 = max_turn_per_sec_both(stepC3, 20, 300.0)
    turn_C4 = max_turn_per_sec_both(stepC4, 20, 300.0)
    turn_CS = max_turn_per_sec_both(step_CS, 64, 300.0)
    print(f"Engine A  (current, 20Hz, rescale)  : {turn_A:8.2f} deg/sec")
    print(f"Engine B  (no rescale, 20Hz)        : {turn_B:8.2f} deg/sec")
    print(f"Engine C3 (no rescale, K=3 substep) : {turn_C3:8.2f} deg/sec")
    print(f"Engine C4 (no rescale, K=4 substep) : {turn_C4:8.2f} deg/sec")
    print(f"Real CS:S (64-tick faithful)        : {turn_CS:8.2f} deg/sec  <-- target feel")
    print()
    # Convert "deg/sec heading change" to the user's "~1 unit" intuition:
    # CS lets you redirect ~tiny amount with no mouse. Report the per-second number.
    print("(User says real CS no-mouse redirect is small (~1 'unit'/tiny). "
          "A's huge number == BUG2.)")
    print()

    # -------------------- (4) recommended K --------------------
    print("-" * 78)
    print("(4) Recommended substep K (match ~64Hz feel for turn rate & accel cadence)")
    print("-" * 78)
    for K in (2, 3, 4):
        eff_hz = 20 * K
        sc = make_step_C(K)
        t = max_turn_per_sec_both(sc, 20, 300.0)
        print(f"  K={K}: effective {eff_hz} Hz, no-mouse turn = {t:7.2f} deg/sec "
              f"(CS64={turn_CS:.2f})")
    print(f"  -> K=3 gives 60Hz (closest below 64 without overshoot); "
          f"K=4 gives 80Hz (slightly over). Recommend K=3.")
    print()

    # -------------------- (5) ramp impact --------------------
    print("-" * 78)
    print("(5) Bhop speed RAMP: does substepping change tuned maxair=57 single-step?")
    print("-" * 78)
    air_ticks = 15   # ~0.75s hop at 20Hz (2*v/g)
    hops = 12
    start = 300.0
    chain_B  = ramp_chain(step_B,  air_ticks, hops, start)   # single-step, cap/tick
    chain_C3 = ramp_chain(make_step_C(3), air_ticks, hops, start)
    chain_C4 = ramp_chain(make_step_C(4), air_ticks, hops, start)
    print(f"air_ticks/hop={air_ticks} (~0.75s), hops={hops}, start={start} u/s")
    print(f"{'hop':>4} | {'B single':>10} | {'C K=3':>10} | {'C K=4':>10}")
    for i in range(hops + 1):
        print(f"{i:>4} | {chain_B[i]:>10.2f} | {chain_C3[i]:>10.2f} | {chain_C4[i]:>10.2f}")
    # per-hop gain at low speed (most cap-sensitive)
    gain_B_hop1  = chain_B[1]  - chain_B[0]
    gain_C3_hop1 = chain_C3[1] - chain_C3[0]
    gain_C4_hop1 = chain_C4[1] - chain_C4[0]
    print(f"\nFirst-hop gain: B={gain_B_hop1:.2f}  C3={gain_C3_hop1:.2f}  C4={gain_C4_hop1:.2f} u/s")
    # The cap is a SPEED not a rate, so per-substep cap == per-tick cap; the
    # only difference is how many times you 'top up to cap' per second.
    # Measure final speed difference:
    print(f"Final speed:  B={chain_B[-1]:.2f}  C3={chain_C3[-1]:.2f}  C4={chain_C4[-1]:.2f} u/s")
    ramp_diff_pct_C3 = 100.0 * (chain_C3[-1] - chain_B[-1]) / chain_B[-1]
    print(f"C3 vs B single-step final-speed delta = {ramp_diff_pct_C3:+.2f}%")

    # To find whether maxair must change: what cap makes C3 final speed match B?
    # The cap directly bounds dot(vel,wishdir) growth; substepping lets you
    # re-approach the cap K times. Find equivalent cap for C3 that reproduces B ramp.
    def ramp_final_with_cap(K, cap_u):
        cap_b = cap_u * U2B
        def sc(vx, vz, wdx, wdz):
            sub_dt = TICK_DT / K
            for _ in range(K):
                vx, vz = accelerate_source(vx, vz, wdx, wdz, AIR_WISH, cap_b, AIR_ACCEL, sub_dt)
            return vx, vz
        return ramp_chain(sc, air_ticks, hops, start)[-1]
    target = chain_B[-1]
    lo, hi = 30.0, 57.0
    for _ in range(40):
        mid = (lo + hi) / 2
        if ramp_final_with_cap(3, mid) > target:
            hi = mid
        else:
            lo = mid
    equiv_cap_C3 = (lo + hi) / 2
    print(f"Equivalent maxair for C3 to reproduce B(57) ramp = {equiv_cap_C3:.2f} u/s")
    print()

    # -------------------- SUMMARY NUMBERS --------------------
    print("=" * 78)
    print("SUMMARY (machine-readable)")
    print("=" * 78)
    print(f"BUG1_A_spread_u/s={A_spread:.3f}")
    print(f"BUG1_B_spread_u/s={B_spread:.3f}")
    print(f"BUG1_A_best_angle_deg={A_best_angle}")
    print(f"BUG1_B_best_angle_deg={B_best_angle}")
    print(f"BUG1_collapse={bug1_collapse}")
    print(f"turn_A_degpersec={turn_A:.2f}")
    print(f"turn_B_degpersec={turn_B:.2f}")
    print(f"turn_C3_degpersec={turn_C3:.2f}")
    print(f"turn_CS64_degpersec={turn_CS:.2f}")
    print(f"recommended_K=3")
    print(f"ramp_C3_vs_B_final_pct={ramp_diff_pct_C3:+.2f}")
    print(f"equiv_maxair_C3_u/s={equiv_cap_C3:.2f}")

    return dict(
        opt_table=table, opt_table_str=opt_table_str,
        A_spread=A_spread, B_spread=B_spread,
        A_best_angle=A_best_angle, B_best_angle=B_best_angle,
        bug1_collapse=bug1_collapse,
        turn_A=turn_A, turn_B=turn_B, turn_C3=turn_C3, turn_C4=turn_C4, turn_CS=turn_CS,
        chain_B=chain_B, chain_C3=chain_C3,
        ramp_diff_pct_C3=ramp_diff_pct_C3, equiv_cap_C3=equiv_cap_C3,
    )


if __name__ == "__main__":
    main()
