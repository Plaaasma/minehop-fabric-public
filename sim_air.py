import math

# ===== CONSTANTS (verbatim from spec) =====
TPS = 20
DT = 1.0 / TPS            # 0.05
WALK = 0.10
SPEED_MUL = 3.25
SV_MAXAIRSPEED = 27.5
CAP = SV_MAXAIRSPEED / 800.0   # 0.034375 b/t  (=27.5 u/s)
SV_AIRACCEL = 100
SUBSTEPS = 4
JUMP_IMPULSE = 300
GRAVITY = 800

# unit conversion: 1 block/tick = 20 b/s ; u/s == b/s in MC convention here (1 block = 1 m, u=block)
# velocity stored in blocks/tick.  b/s = b/t * 20
BPT_TO_BPS = 20.0
def ups_to_bpt(ups): return ups / BPT_TO_BPS    # u/s == b/s
def bpt_to_bps(bpt): return bpt * BPT_TO_BPS

# ===== movementInputToVelocity =====
# In MC, input (strafe sI = x, forward fI = z) rotated by yaw.
# yaw in degrees. movement: x' = sI*cos - fI*sin ... use MC formula.
def movement_input_to_velocity(sI, fI, yaw_deg):
    # normalize input length (MC normalizes if >1)
    dist = math.sqrt(sI*sI + fI*fI)
    if dist < 1e-9:
        return (0.0, 0.0)
    # MC: vec.normalize() then rotate by yaw.  forward is -z at yaw 0.
    # Standard MC: f = sin(-yaw)*z ... we use the conventional:
    yaw = math.radians(yaw_deg)
    sin = math.sin(yaw)
    cos = math.cos(yaw)
    # input vector (x=sI, z=fI) normalized
    nx = sI / dist
    nz = fI / dist
    # rotate: world_x = nx*cos - nz*sin ; world_z = nz*cos + nx*sin  (MC Vec3d.rotateY-ish)
    wx = nx * cos - nz * sin
    wz = nz * cos + nx * sin
    return (wx, wz)

def unit(vx, vz):
    m = math.sqrt(vx*vx + vz*vz)
    if m < 1e-12:
        return (0.0, 0.0)
    return (vx/m, vz/m)

# ===== accelerateSource (verbatim) =====
def accelerate_source(vx, vz, wdx, wdz, wishSpeed, wishSpeedCap, accel, surf, useUncapped, frameFraction,
                      acc_mode="current", backclamp=False):
    cappedWishSpeed = min(wishSpeed, wishSpeedCap)
    currentSpeed = vx*wdx + vz*wdz
    addSpeed = cappedWishSpeed - currentSpeed
    if addSpeed <= 0:
        return (vx, vz)
    # acceleration wish speed selection
    if acc_mode == "current":            # USES FULL UNCAPPED wishspeed  (the bug)
        accWish = wishSpeed
    elif acc_mode == "capped":           # FIX (a): use capped wishspeed
        accWish = cappedWishSpeed
    accelSpeed = accel * DT * frameFraction * accWish
    if accelSpeed > addSpeed:
        accelSpeed = addSpeed
    if backclamp:
        # FIX (c): clamp the per-substep add so it can't exceed the capped-gain magnitude
        maxAdd = accel * DT * frameFraction * cappedWishSpeed
        if accelSpeed > maxAdd:
            accelSpeed = maxAdd
    return (vx + wdx*accelSpeed, vz + wdz*accelSpeed)

# wishSpeed for full diagonal/any input |input|~1
def wish_speed(input_mag=1.0):
    return WALK * SPEED_MUL * input_mag   # 0.325

# ===== AIR TICK (interpolated 4-substep) =====
def air_tick(vx, vz, sI, fI, prevYaw, curYaw, cap=CAP, acc_mode="current", backclamp=False, input_mag=1.0):
    ws = wish_speed(input_mag)
    def wrap_degrees(a):
        a = a % 360.0
        if a >= 180.0: a -= 360.0
        if a < -180.0: a += 360.0
        return a
    dyaw = wrap_degrees(curYaw - prevYaw)
    ax, az = vx, vz
    for substep in range(SUBSTEPS):
        substepYaw = prevYaw + dyaw * (substep + 1) / 4.0
        wdx, wdz = movement_input_to_velocity(sI, fI, substepYaw)
        wdx, wdz = unit(wdx, wdz)
        ax, az = accelerate_source(ax, az, wdx, wdz, ws, cap, SV_AIRACCEL, 1.0, True, 1.0/4.0,
                                   acc_mode=acc_mode, backclamp=backclamp)
    return ax, az

def speed_bps(vx, vz):
    return bpt_to_bps(math.sqrt(vx*vx + vz*vz))

# ============================================================
# VALIDATION: analytic cap-bound law for the PERP single-application case
# When wishDir perpendicular to v (dot=0), and accelSpeed == cap (full),
# |vnew|^2 = |v|^2 + cap^2 - (|v|cosθ)^2 ; for perp cosθ=0 -> |vnew|^2=|v|^2+cap^2
# We need a config where accelSpeed is NOT clipped by addSpeed and equals cap.
# Use a single full-tick application (frameFraction=1, accWish s.t. accelSpeed>=addSpeed -> clipped to addSpeed=cap).
# ============================================================
def validate():
    print("=== VALIDATION vs analytic law ===")
    # velocity along +x at some speed, wishdir along +z (perp)
    v_bps = 13.0
    vx = ups_to_bpt(v_bps); vz = 0.0
    wdx, wdz = 0.0, 1.0
    # single application, perp: dot=0, addSpeed = cap - 0 = cap.
    # set frameFraction=1, accel huge so accelSpeed clipped to addSpeed=cap
    nx, nz = accelerate_source(vx, vz, wdx, wdz, wishSpeed=999.0, wishSpeedCap=CAP, accel=999, surf=1,
                               useUncapped=True, frameFraction=1.0, acc_mode="current")
    sim = math.sqrt(nx*nx + nz*nz)
    analytic = math.sqrt(vx*vx + CAP*CAP)   # perp case
    print(f"  perp: sim={sim:.8f}  analytic={analytic:.8f}  diff={abs(sim-analytic):.2e}")
    # general angle theta between v and wishdir, dot=|v|cosθ, addSpeed=cap-|v|cosθ
    for theta_deg in [30, 60, 90, 120]:
        th = math.radians(theta_deg)
        vx, vz = ups_to_bpt(v_bps), 0.0
        wdx, wdz = math.cos(th), math.sin(th)
        nx, nz = accelerate_source(vx, vz, wdx, wdz, 999.0, CAP, 999, 1, True, 1.0, acc_mode="current")
        sim = math.sqrt(nx*nx + nz*nz)
        # analytic when accelSpeed clipped to addSpeed=cap-|v|cosθ (cap-bound):
        # |vnew|^2 = |v|^2 + 2*|v|cosθ*addSpeed + addSpeed^2 ... but the cap law given:
        # |vnew|^2 = |v|^2 + cap^2 - (|v|cosθ)^2   -- this holds when final speed projection onto wishdir == cap
        vmag = math.sqrt(vx*vx)
        addS = CAP - vmag*math.cos(th)
        general = math.sqrt(vmag*vmag + 2*vmag*math.cos(th)*addS + addS*addS)
        caplaw = math.sqrt(vmag*vmag + CAP*CAP - (vmag*math.cos(th))**2)
        print(f"  theta={theta_deg:3d}: sim={sim:.8f} general={general:.8f} caplaw={caplaw:.8f}")
    print()

validate()

# ============================================================
# helper: build a yaw schedule so velocity heading sweeps 360 over N ticks
# ============================================================
def heading_deg(vx, vz):
    return math.degrees(math.atan2(vz, vx))

# ============================================================
# SCENARIO 1: hold W, spin VIEW 360 over ~5 ticks at 600 u/s
# fI=1, sI=0 (hold W). Yaw spins full 360 across 5 ticks.
# Measure horizontal speed retained.
# ============================================================
def scen_holdW_360(cap=CAP, acc_mode="current", backclamp=False, ticks=5, start_bps=600.0):
    # start velocity moving in +x (heading 0). With hold-W, wishdir = forward(yaw).
    # initial yaw aligned so forward == velocity heading.
    v_bps = start_bps
    vx, vz = ups_to_bpt(v_bps), 0.0
    # forward at yaw: movement_input_to_velocity(0,1,yaw). At yaw=0 -> (-sin0... ) compute heading.
    # find yaw where forward heading == 0 deg
    # forward(yaw): nx=0,nz=1 -> wx=-sin(yaw)? from formula: wx = 0*cos -1*sin = -sin ; wz=1*cos+0= cos
    # heading of forward = atan2(wz, wx)=atan2(cos,-sin). At yaw=0 -> atan2(1,0)=90deg.
    # we just need forward to start aligned to velocity heading(0). Solve: atan2(cos y,-sin y)=0 -> cos y=0 -> y=-90?
    # at y=-90: -sin(-90)=1 ->wx=1 ; cos(-90)=0 ->wz=0 -> heading atan2(0,1)=0. good. start yaw=-90.
    startYaw = -90.0
    prevYaw = startYaw
    for t in range(ticks):
        # spin view 360 total across ticks
        curYaw = startYaw + 360.0 * (t+1) / ticks
        vx, vz = air_tick(vx, vz, 0, 1, prevYaw, curYaw, cap=cap, acc_mode=acc_mode, backclamp=backclamp)
        prevYaw = curYaw
    final = speed_bps(vx, vz)
    return final / v_bps * 100.0

# ============================================================
# SCENARIO 2: hold D, mouse sweeps so VELOCITY does 360 (skilled airstrafe)
# sI=1, fI=0. The player turns view so wishdir stays ~90deg offset, dragging velocity around full circle.
# Measure retention (and ideally gain).
# ============================================================
def scen_holdD_360(cap=CAP, acc_mode="current", backclamp=False, ticks=60, start_bps=600.0,
                   offset_deg=90.0):
    v_bps = start_bps
    vx, vz = ups_to_bpt(v_bps), 0.0
    # hold D: wishdir = right vector = movement_input_to_velocity(1,0,yaw)
    # right(yaw): nx=1,nz=0 -> wx=cos ; wz=sin -> heading atan2(sin,cos)=yaw.
    # so wishdir heading == yaw for hold-D. To keep wishdir at offset from velocity heading:
    total_turn = 0.0
    prevHeading = heading_deg(vx, vz)
    swept = 0.0
    prev_v = v_bps
    # We rotate velocity ~360 deg. Each tick set view yaw = current velocity heading + offset.
    # prevYaw/curYaw: within a tick interpolation; we set both to maintain offset, with small spin.
    # Use many ticks; turn rate chosen so heading completes 360.
    prevYaw = heading_deg(vx, vz) + offset_deg
    done = False
    cum_heading = heading_deg(vx, vz)
    last_h = cum_heading
    for t in range(ticks):
        h = heading_deg(vx, vz)
        # unwrap
        dh = (h - last_h + 540) % 360 - 180
        swept += dh
        last_h = h
        if abs(swept) >= 360.0:
            done = True
            break
        curYaw = h + offset_deg   # keep wishdir offset ahead to pull heading around
        vx, vz = air_tick(vx, vz, 1, 0, prevYaw, curYaw, cap=cap, acc_mode=acc_mode, backclamp=backclamp)
        prevYaw = curYaw
    final = speed_bps(vx, vz)
    return final / v_bps * 100.0, swept

# ============================================================
# SCENARIO 3: low speed (260 u/s) wide vs tight strafe -> gain per second (b/s gained per second)
# Hold strafe at fixed offset from velocity for 1 second (20 ticks) WITHOUT letting heading run away much;
# measure speed gain.
# wide ~55deg from velocity ; tight ~80deg.
# Use hold-D, set view so wishdir is at given offset from velocity heading, re-aim each tick.
# ============================================================
def scen_strafe_gain(offset_deg, cap=CAP, acc_mode="current", backclamp=False, ticks=20, start_bps=260.0):
    v_bps = start_bps
    vx, vz = ups_to_bpt(v_bps), 0.0
    prevYaw = heading_deg(vx, vz) + offset_deg
    for t in range(ticks):
        h = heading_deg(vx, vz)
        curYaw = h + offset_deg
        vx, vz = air_tick(vx, vz, 1, 0, prevYaw, curYaw, cap=cap, acc_mode=acc_mode, backclamp=backclamp)
        prevYaw = curYaw
    final = speed_bps(vx, vz)
    # gain per second over the 1s window
    return (final - v_bps) / (ticks * DT)   # b/s per second

# ============================================================
# JUMP / BASE checks  (jump6~14 b/s, base 6.5)
#   base ground speed should be ~6.5 b/s ; a "jump6" trick reaching ~14 b/s.
#   We approximate: jump6 = chain of 6 strafe ticks from base after a jump?
#   Spec wants jump6~14 b/s and base 6.5 preserved. We model base as walk ground speed
#   and jump6 as accumulation reaching ~14. We check the air-accel doesn't destroy these.
# ============================================================
def base_speed_bps():
    # ground walk speed: movementSpeed*speed_mul as b/t? base 6.5 b/s
    return WALK * SPEED_MUL * BPT_TO_BPS  # 0.325*20 = 6.5

def jump6_bps(cap=CAP, acc_mode="current", backclamp=False):
    # start at base 6.5, do 6 well-aimed perp strafe ticks (optimal gain) and see speed.
    v_bps = base_speed_bps()
    vx, vz = ups_to_bpt(v_bps), 0.0
    prevYaw = heading_deg(vx, vz) + 90.0
    # optimal-ish: keep wishdir slightly ahead so gain near-max each tick; do 6 ticks
    for t in range(6):
        h = heading_deg(vx, vz)
        # choose offset that maximizes gain at this speed (near perp but allowing cap projection)
        curYaw = h + 90.0
        vx, vz = air_tick(vx, vz, 1, 0, prevYaw, curYaw, cap=cap, acc_mode=acc_mode, backclamp=backclamp)
        prevYaw = curYaw
    return speed_bps(vx, vz)

# ============================================================
# DOMINANT LOSS: worst single-tick backward add at 600 u/s
# When wishdir points fully backward (dot<0, max negative), addSpeed=cap-dot is large,
# accelSpeed=accel*DT*ff*wishSpeed(uncapped)=0.40625*ff each substep, 4 substeps.
# ============================================================
def worst_backward_add_bps(acc_mode="current", backclamp=False):
    v_bps = 600.0
    vx, vz = ups_to_bpt(v_bps), 0.0
    ws = wish_speed()
    total_add = 0.0
    ax, az = vx, vz
    # all 4 substeps fully backward (-x)
    for substep in range(SUBSTEPS):
        wdx, wdz = -1.0, 0.0
        before = math.sqrt(ax*ax+az*az)
        ax, az = accelerate_source(ax, az, wdx, wdz, ws, CAP, SV_AIRACCEL, 1.0, True, 1.0/4.0,
                                   acc_mode=acc_mode, backclamp=backclamp)
        after = math.sqrt(ax*ax+az*az)
    # net change in speed magnitude (loss) per tick
    net = (math.sqrt(ax*ax+az*az) - math.sqrt(vx*vx+vz*vz))
    # per-substep backward add magnitude (current bug):
    per_substep_add = SV_AIRACCEL * DT * (1.0/4.0) * (ws if acc_mode=="current" else min(ws,CAP))
    if backclamp:
        per_substep_add = min(per_substep_add, SV_AIRACCEL*DT*(1.0/4.0)*min(ws,CAP))
    worst_tick_bps = abs(per_substep_add * SUBSTEPS) * BPT_TO_BPS
    return worst_tick_bps, bpt_to_bps(net)

# ============================================================
# RUN ALL
# ============================================================
def run_config(name, cap=CAP, acc_mode="current", backclamp=False):
    holdW = scen_holdW_360(cap=cap, acc_mode=acc_mode, backclamp=backclamp)
    holdD, swept = scen_holdD_360(cap=cap, acc_mode=acc_mode, backclamp=backclamp)
    wide = scen_strafe_gain(55.0, cap=cap, acc_mode=acc_mode, backclamp=backclamp)
    tight = scen_strafe_gain(80.0, cap=cap, acc_mode=acc_mode, backclamp=backclamp)
    j6 = jump6_bps(cap=cap, acc_mode=acc_mode, backclamp=backclamp)
    base = base_speed_bps()
    worst, net = worst_backward_add_bps(acc_mode=acc_mode, backclamp=backclamp)
    print(f"--- {name} (cap={cap*800:.1f}u/s, acc_mode={acc_mode}, backclamp={backclamp}) ---")
    print(f"  holdW-360 retention : {holdW:6.2f}%")
    print(f"  holdD-360 retention : {holdD:6.2f}%  (swept {swept:.0f}deg)")
    print(f"  wide(55) gain       : {wide:7.3f} b/s per s")
    print(f"  tight(80) gain      : {tight:7.3f} b/s per s   wide>tight={wide>tight}")
    print(f"  jump6               : {j6:6.3f} b/s   base={base:.3f} b/s")
    print(f"  worst backward add  : {worst:7.2f} b/s/tick   net tick change(allback)={net:.2f} b/s")
    print()
    return dict(name=name, holdW=holdW, holdD=holdD, wide=wide, tight=tight, j6=j6, base=base, worst=worst)

print("=== CORRECTED STRAFE-GAIN MODEL (Quake-accurate sweep) ===")
# Proper airstrafe: each tick the player re-aims so wishdir leads velocity by 'offset'.
# Velocity heading is FREE to rotate (mouse drags it). Gain = perpendicular accel component
# integrated. With Quake cap law, per-tick speed gain ~ accelSpeed*sin(theta) where
# accelSpeed=min(addSpeed, accel*dt*wishspeed) and addSpeed=cap - |v|cos(theta).
# Gain exists only while |v|cos(theta) < cap. We sweep to find the gain BAND.
def strafe_gain_curve(cap, acc_mode, ticks=20, start_bps=260.0):
    res = {}
    for off in [30.0, 45.0, 55.0, 70.0, 80.0, 89.0]:
        v_bps = start_bps
        vx, vz = ups_to_bpt(v_bps), 0.0
        prevYaw = heading_deg(vx, vz) + off
        for t in range(ticks):
            h = heading_deg(vx, vz)
            curYaw = h + off
            vx, vz = air_tick(vx, vz, 1, 0, prevYaw, curYaw, cap=cap, acc_mode=acc_mode)
            prevYaw = curYaw
        res[off] = (speed_bps(vx,vz) - start_bps) / (ticks*DT)
    return res

for label, cap, am in [("current", CAP, "current"), ("cap=30", 30/800, "current"),
                        ("cap=150", 150/800, "current"), ("cap=320", 320/800, "current")]:
    c = strafe_gain_curve(cap, am)
    print(f"  {label:10s}: " + "  ".join(f"{o:.0f}d={g:+.2f}" for o,g in c.items()))
print()

print("=== FIX d: CS/Source-style (cap only the wishdir projection; fixed-mag add) ===")
# Source CS: addspeed = wishspd - currentspeed; if addspeed<=0 return.
#   BUT wishspd here is the CAPPED air wishspeed (30). currentspeed=dot(v,wishdir).
#   accelspeed = accel*dt*wishspeed_FULL*surf; clipped to addspeed.
# The reason CS works: gain comes from the SIN component of the fixed add while the cap
# only limits how much FORWARD speed you can build. Off-perp the add is still applied
# (addspeed>0 because cap-dot>0 whenever dot<cap). So wide angles (small dot) DO get add.
# Our problem: at 260b/s, dot(55d)=7.45 >> cap=0.034, so addspeed<0 -> NO add. SAME structure.
# => raising cap so cap > |v|*cos(off) is the ONLY thing that opens the band.
# CS uses cap=30 u/s but |v| is in u/s NOT u/tick, and accel uses frametime properly.
# THE UNIT BUG: cap & dot must be in the SAME unit system. If velocity were in u/s,
# dot(55d) at 260 = 149 u/s, cap=30 -> still negative. So CS limits this via a SMALL
# effective wishspeed cap = 30 u/s meaning you can only ADD up to where forward speed hits 30.
# This is WHY in CS you must hold near-90deg too -- but the BAND is wider because the
# per-tick add magnitude (accel*dt*wishspeed) is large, curving velocity efficiently.
def strafe_gain_csstyle(cap_ups, ticks=20, start_bps=260.0, accel=100):
    # operate in u/s to match CS semantics; convert.
    res={}
    for off in [30.0,45.0,55.0,70.0,80.0,89.0]:
        vx, vz = start_bps, 0.0  # u/s
        for t in range(ticks):
            h=math.degrees(math.atan2(vz,vx))
            wd=math.radians(h+off)
            wdx,wdz=math.cos(wd),math.sin(wd)
            cur=vx*wdx+vz*wdz
            add=cap_ups-cur
            if add>0:
                accelspd=accel*DT*(WALK*SPEED_MUL*BPT_TO_BPS)  # full wishspeed in u/s scale-ish
                accelspd=accel*DT*cap_ups  # CS: accel*dt*wishspeed(=cap) -> fixed mag
                if accelspd>add: accelspd=add
                vx+=wdx*accelspd; vz+=wdz*accelspd
        res[off]=(math.sqrt(vx*vx+vz*vz)-start_bps)/(ticks*DT)
    return res
for capv in [30, 150, 320]:
    c=strafe_gain_csstyle(capv)
    print(f"  CS cap={capv:3d}u/s: " + "  ".join(f"{o:.0f}d={g:+.2f}" for o,g in c.items()))
print()

print("=== MECHANISM PROBE ===")
# At realistic speed the dot(v,wishdir) in b/t vastly exceeds cap (0.034 b/t).
for v_bps in [260.0, 600.0]:
    for off in [55.0, 80.0, 90.0]:
        v_bpt = ups_to_bpt(v_bps)
        dot_bpt = v_bpt * math.cos(math.radians(off))
        print(f"  v={v_bps}b/s off={off}deg : dot={dot_bpt:.4f}b/t vs cap={CAP:.4f}b/t -> addSpeed={CAP-dot_bpt:+.4f} (gain {'YES' if CAP-dot_bpt>0 else 'NO/zero'})")
print()

print("=== SIMULATIONS ===\n")
cur = run_config("CURRENT (buggy)", cap=CAP, acc_mode="current", backclamp=False)
fa  = run_config("FIX a: capped wishspeed for accelspeed", cap=CAP, acc_mode="capped", backclamp=False)
fb  = run_config("FIX b: raise cap to 30", cap=30.0/800.0, acc_mode="current", backclamp=False)
fb2 = run_config("FIX b': raise cap to 30 + capped wishspeed", cap=30.0/800.0, acc_mode="capped", backclamp=False)
fc  = run_config("FIX c: clamp backward add", cap=CAP, acc_mode="current", backclamp=True)
fac = run_config("FIX a+c: capped + clamp", cap=CAP, acc_mode="capped", backclamp=True)
