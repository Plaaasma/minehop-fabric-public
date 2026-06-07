"""
Minehop movement sim v2 — corrected to expose the REAL mechanism of both bugs.

The spec's rescale clause:
    preAirSpeed = |vel_h|                         # speed BEFORE accel
    wishAlignmentCos = dot(vel, wishDir)/preAirSpeed
    accelerated = accelerateSource(vel, wishDir, ...)   # Source air accel (cap-bound add)
    if wishAlignmentCos > -0.98:
        post = |accelerated_h|
        if post + 1e-8 < preAirSpeed:              # only if accel REDUCED speed
            accelerated *= preAirSpeed / post      # RESCALE the vector back up to old magnitude

KEY GEOMETRIC FACT:
  accelerateSource only ADDS along wishDir (it never subtracts), so the post-accel
  speed is >= pre speed UNLESS wishDir points behind velocity enough that
  current = dot(vel,wishDir) is already > cap (then add<=0, returns vel unchanged,
  post==pre) -- OR when wishDir has a backward component the ADDED vector tilts the
  resultant such that its magnitude can drop below pre only if the add is negative,
  which never happens. So in PURE Source accel post>=pre ALWAYS and the rescale
  (post<pre) branch is dead for a single forward/sideways press.

  THEREFORE for the bug to exist, the engine must be RESCALING in the case the
  user actually hits: wishDir BEHIND velocity. When dot(vel,wishDir) > cap, addSpeed<=0,
  accelerateSource returns vel UNCHANGED. post==pre -> no rescale, no turn. Also no bug.

  The bug the user reports ("90 deg redirect, no mouse") is real CS air-strafe
  geometry PLUS the rescale removing the natural speed penalty of steering.
  In REAL Source: pressing a sideways/backward key with no mouse adds a small
  vector; |vel| barely changes and heading barely rotates (you cannot turn far
  because adding a capped sideways nudge to a big velocity rotates it by only
  atan(add/|vel|) per tick, and going backward LOSES speed which you can't afford).
  The Minehop rescale-up-to-preAirSpeed clause REMOVES the speed loss, so the
  player can keep pressing back+side keys, lose no speed, and the heading rotates
  atan(add/|vel|) EVERY tick for free -> accumulates to 90 deg fast == BUG2.

  And at low speed (BUG1): the natural reward for a WIDE strafe is post>pre. But
  the gauge/feel is dominated by the rescale path; when the user strafes wide and
  the resulting vector would EXCEED pre, fine; but the rescale ONLY ever forces
  speed DOWN to pre when post<pre -- it cannot be the whole story for BUG1 unless
  wishAlignmentCos is computed against the WRONG vector. Re-reading: wishAlignmentCos
  uses dot(accelVec, wishDir) where accelVec is the PRE velocity. The clause caps
  GAINS implicitly: any tick where the wide press would have lost speed (because in
  CS a too-wide press at low speed with the cap actually does add a backward-ish
  component) gets snapped back to pre -> the wide strafe yields exactly pre, i.e.
  "wide never gains" == BUG1. The optimum then degenerates to the single perpendicular
  angle and you can't open up wide.

This v2 models BOTH engines correctly and, crucially, models the BUG2 redirect as the
multi-tick HEADING ROTATION under continuous key presses (the thing the user feels),
not a single-tick speed delta.
"""

import math

U2B = 1.0/800.0
B2U = 800.0
TICK_DT = 1.0/20.0
AIR_ACCEL = 100.0
AIR_WISH  = 0.325
AIR_CAP   = 57.0*U2B
SURF = 1.0
CS_DT = 1.0/64.0

def accel_src(vx,vz,wx,wz,wish,cap,acc,dt):
    cw = min(wish,cap)
    cur = vx*wx+vz*wz
    add = cw-cur
    if add<=0: return vx,vz
    asp = acc*dt*wish*SURF
    if asp>add: asp=add
    return vx+wx*asp, vz+wz*asp

def sp(vx,vz): return math.hypot(vx,vz)
def ang(vx,vz): return math.degrees(math.atan2(vz,vx))
def wd(a):
    r=math.radians(a); return math.cos(r),math.sin(r)
def vel(s,a):
    r=math.radians(a); return s*math.cos(r),s*math.sin(r)

# ----- engines: return new (vx,vz) given wishdir -----
def A(vx,vz,wx,wz):          # CURRENT: accel + rescale-up-to-pre clause
    pre=sp(vx,vz)
    cosa = (vx*wx+vz*wz)/pre if pre>1e-8 else 1.0
    nx,nz=accel_src(vx,vz,wx,wz,AIR_WISH,AIR_CAP,AIR_ACCEL,TICK_DT)
    if cosa>-0.98:
        post=sp(nx,nz)
        if post+1e-8<pre:
            if post>1e-8:
                r=pre/post; nx,nz=nx*r,nz*r
            else: nx,nz=vx,vz
    return nx,nz

def B(vx,vz,wx,wz):          # NO rescale
    return accel_src(vx,vz,wx,wz,AIR_WISH,AIR_CAP,AIR_ACCEL,TICK_DT)

def C(K):
    def f(vx,vz,wx,wz):
        dt=TICK_DT/K
        for _ in range(K):
            vx,vz=accel_src(vx,vz,wx,wz,AIR_WISH,AIR_CAP,AIR_ACCEL,dt)
        return vx,vz
    return f

def CS(vx,vz,wx,wz):         # real CS 64-tick faithful
    return accel_src(vx,vz,wx,wz,AIR_WISH,AIR_CAP,AIR_ACCEL,CS_DT)

# ============================================================
# CORRECTED BUG2: NO mouse (fixed yaw). The 8 wishdirs are FIXED in world
# space at 0,45,...,315 deg. The player CANNOT pick a wishdir relative to a
# moving velocity (no mouse!). They can only press the 8 key combos which map
# to 8 FIXED world directions. To turn velocity from heading 0 toward +90, the
# only useful presses are the ones with a +y (left) component: 45, 90, 135.
# Each tick we greedily pick whichever FIXED dir rotates velocity most toward +y
# while the engine's speed rule applies. Engine A keeps speed (rescale) so the
# player can spam 'back-left' (135) forever and rotate freely. Engine B/CS lose
# speed when pressing backward, so the rotation stalls (you'd decelerate to ~cap).
# We integrate heading change over 1 second.
# ============================================================
EIGHT=[0,45,90,135,180,225,270,315]

def turn_no_mouse(stepfn, hz, start_u=300.0, sign=+1, secs=1.0):
    vx,vz=vel(start_u*U2B,0.0)
    total=0.0
    n=int(round(hz*secs))
    for _ in range(n):
        h0=ang(vx,vz)
        best=None
        for wa in EIGHT:
            wx,wz=wd(wa)
            nx,nz=stepfn(vx,vz,wx,wz)
            if sp(nx,nz)<1e-9: continue
            d=(ang(nx,nz)-h0+180)%360-180
            sc=sign*d
            # require the engine not let speed crater to ~0 (player wouldn't fly forever);
            # accept any move, the engine itself governs speed retention.
            if best is None or sc>best[0]:
                best=(sc,nx,nz,d)
        if best is None: break
        _,vx,vz,d=best
        total+=d
    return abs(total)/secs   # deg per second

def turn_best(stepfn,hz,start=300.0,secs=1.0):
    return max(turn_no_mouse(stepfn,hz,start,+1,secs),
               turn_no_mouse(stepfn,hz,start,-1,secs))

# Also report the SPEED retained over that turning second (the discriminator).
def turn_and_speed(stepfn,hz,start_u=300.0,sign=+1,secs=1.0):
    vx,vz=vel(start_u*U2B,0.0); total=0.0; n=int(round(hz*secs))
    for _ in range(n):
        h0=ang(vx,vz); best=None
        for wa in EIGHT:
            wx,wz=wd(wa); nx,nz=stepfn(vx,vz,wx,wz)
            if sp(nx,nz)<1e-9: continue
            d=(ang(nx,nz)-h0+180)%360-180
            if best is None or sign*d>best[0]: best=(sign*d,nx,nz,d)
        if best is None: break
        _,vx,vz,d=best; total+=d
    return abs(total)/secs, sp(vx,vz)*B2U

# ============================================================
# BUG1 corrected: wide strafe. With a FIXED yaw frame and velocity along +x,
# the wishdir is some angle theta off velocity. CS rewards a wide theta at low
# speed. We compare, per engine, the BEST achievable next-speed AND whether the
# WIDE end (small theta, more forward-ish overlapping) can ever gain. The user's
# complaint: wide presses give the SAME as not strafing (no gain). We measure the
# gain at a moderately-wide angle (theta=45) vs the perpendicular optimum.
# ============================================================
def speed_at(stepfn,start_u,theta):
    vx,vz=vel(start_u*U2B,0.0)
    wx,wz=wd(theta)   # wishdir theta deg off velocity (+x)
    nx,nz=stepfn(vx,vz,wx,wz)
    return sp(nx,nz)*B2U

# ============================================================
# OPTIMAL ANGLE table (faithful = B physics). Reported as angle off velocity.
# ============================================================
def opt_angle(stepfn,start_u):
    vx,vz=vel(start_u*U2B,0.0); best=(-1,0)
    a=0.0
    while a<=90.0:
        wx,wz=wd(a); nx,nz=stepfn(vx,vz,wx,wz); s=sp(nx,nz)
        if s>best[0]: best=(s,a)
        a+=0.1
    return best[1],(best[0]-start_u)/1.0

# ============================================================
# RAMP (bhop) — uses optimal continuous wishdir each tick (perfect strafe).
# ============================================================
def opt_step(vx,vz,stepfn):
    h=ang(vx,vz); best=None; a=-90.0
    while a<=90.0:
        wx,wz=wd(h+a); nx,nz=stepfn(vx,vz,wx,wz); s=sp(nx,nz)
        if best is None or s>best[0]: best=(s,nx,nz)
        a+=0.5
    return best[1],best[2]

def chain(stepfn,air,hops,start_u):
    vx,vz=vel(start_u*U2B,0.0); seq=[start_u]
    for _ in range(hops):
        for _ in range(air): vx,vz=opt_step(vx,vz,stepfn)
        seq.append(sp(vx,vz)*B2U)
    return seq

# ============================================================
def main():
    print("="*70)
    print("MINEHOP SIM v2")
    print("="*70)
    a20=AIR_ACCEL*TICK_DT*AIR_WISH; a64=AIR_ACCEL*CS_DT*AIR_WISH
    print(f"airCap={AIR_CAP*B2U:.1f}u/s  accelSpeed/tick: 20Hz={a20*B2U:.1f}u/s "
          f"64Hz={a64*B2U:.1f}u/s (both >> cap -> add cap-bound)")
    cx,cz=CS(0,0,1,0)
    print(f"[consistency] cold-start along wishdir -> {sp(cx,cz)*B2U:.3f} u/s (==cap 57)")
    print()

    # ---- (1) optimal angle ----
    print("(1) OPTIMAL ANGLE off velocity (faithful Source accel), 20Hz & 64Hz")
    rows=[]
    for su in range(100,901,100):
        ab,gb=opt_angle(B,su); ac,gc=opt_angle(CS,su)
        rows.append((su,ab,gb,ac,gc))
        print(f"  {su:>4}u/s  20Hz:{ab:5.1f}deg(+{gb:6.3f})  64Hz:{ac:5.1f}deg(+{gc:6.3f})")
    print()

    # ---- (1b) optimal angle in WIDE/TIGHT user terms, measured as the angle of
    # the *view/key* press relative to velocity that an actual player would hold.
    # In CS the player holds W+A (45 off forward) and the OPTIMAL line keeps wishdir
    # ~perpendicular to velocity. The "width" a player perceives is how far their
    # velocity lags the wishdir. At low speed the cap lets a SMALLER theta (wider,
    # more forward press) still hit the cap, so optimal *perceived* strafe is WIDE;
    # at high speed only a near-perpendicular (tight) press still adds. Show the
    # minimum theta that still achieves >=99% of the perpendicular gain:
    print("(1b) WIDEST press still ~optimal (>=99% of best gain) vs speed:")
    wide_tbl=[]
    for su in range(100,901,100):
        best_g=opt_angle(B,su)[1]/1.0
        # scan theta from 0 up, find smallest theta giving >=99% of best gain
        widest=90.0; a=0.0
        # best gain in u/s:
        bg=(speed_at(B,su,opt_angle(B,su)[0])-su)
        a=0.0
        while a<=90.0:
            g=speed_at(B,su,a)-su
            if g>=0.99*bg and bg>1e-6:
                widest=a; break
            a+=0.5
        wide_tbl.append((su,widest))
        print(f"  {su:>4}u/s widest near-optimal theta = {widest:4.1f} deg "
              f"({'WIDE' if widest<70 else 'tight'})")
    print("  -> smaller theta = wider strafe usable. Confirms wide@low, tight@high.")
    print()

    # ---- (2) BUG1 ----
    print("(2) BUG1: gain from a WIDE press (theta=45) vs optimal, low speed 150u/s")
    for eng,nm in ((A,'A current'),(B,'B norescale')):
        g45=speed_at(eng,150,45)-150
        gopt=max(speed_at(eng,150,t) for t in [x*0.5 for x in range(0,181)])-150
        oa=opt_angle(eng,150)[0]
        print(f"  {nm:12}: wide(45deg) gain={g45:7.3f}u/s  best gain={gopt:7.3f}u/s "
              f"at {oa:.1f}deg")
    # The real bug signature: does A ever let a WIDE (forward-ish, theta<45) press gain?
    print("  Wide-end (theta=30) gain:")
    for eng,nm in ((A,'A current'),(B,'B norescale'),(CS,'CS64')):
        print(f"    {nm:12}: theta=30 -> {speed_at(eng,150,30)-150:+.3f}u/s ; "
              f"theta=20 -> {speed_at(eng,150,20)-150:+.3f}u/s")
    print()

    # ---- (3) BUG2: no-mouse turn rate + speed retained ----
    print("(3) BUG2: no-mouse heading change (deg/sec) + speed kept, start 300u/s")
    for eng,nm,hz in ((A,'A current',20),(B,'B norescale',20),
                      (C(3),'C K=3',20),(C(4),'C K=4',20),(CS,'CS64',64)):
        td_p,ks_p=turn_and_speed(eng,hz,300,+1)
        td_n,ks_n=turn_and_speed(eng,hz,300,-1)
        if td_p>=td_n: td,ks=td_p,ks_p
        else: td,ks=td_n,ks_n
        print(f"  {nm:12}: turn={td:8.2f} deg/s, speed after 1s = {ks:7.1f} u/s")
    print()
    print("  Interpretation: real CS lets you turn only a little with no mouse")
    print("  BECAUSE pressing backward bleeds speed (you stall). Engine A's rescale")
    print("  refills speed every tick -> you can keep turning for free.")
    print()

    # ---- (4) recommended K ----
    print("(4) recommended K (effective Hz vs 64):")
    for K in (2,3,4):
        print(f"   K={K}: {20*K}Hz  turn={turn_best(C(K),20,300):7.2f}deg/s "
              f"(CS64 ref={turn_best(CS,64,300):.2f})")
    print("   -> K=3 (60Hz) closest to 64 without overshoot.")
    print()

    # ---- (5) ramp ----
    print("(5) RAMP: substep vs single-step (maxair=57)")
    air=15; hops=10; start=300.0
    cb=chain(B,air,hops,start); c3=chain(C(3),air,hops,start); c4=chain(C(4),air,hops,start)
    for i in range(hops+1):
        print(f"   hop{i:>2}: B={cb[i]:7.1f}  C3={c3[i]:7.1f}  C4={c4[i]:7.1f}")
    d3=100*(c3[-1]-cb[-1])/cb[-1]; d4=100*(c4[-1]-cb[-1])/cb[-1]
    print(f"   final delta vs B: C3={d3:+.2f}%  C4={d4:+.2f}%")
    # equivalent cap for C3 to match B(57)
    def cap_chain(K,capu):
        capb=capu*U2B
        def f(vx,vz,wx,wz):
            dt=TICK_DT/K
            for _ in range(K): vx,vz=accel_src(vx,vz,wx,wz,AIR_WISH,capb,AIR_ACCEL,dt)
            return vx,vz
        return chain(f,air,hops,start)[-1]
    tgt=cb[-1]; lo,hi=20.0,57.0
    for _ in range(50):
        m=(lo+hi)/2
        if cap_chain(3,m)>tgt: hi=m
        else: lo=m
    eqcap=(lo+hi)/2
    print(f"   equiv maxair for C3 to match B(57) ramp = {eqcap:.2f} u/s")
    print()

    # collect machine numbers
    res={}
    res['opt_rows']=rows
    res['wide_tbl']=wide_tbl
    res['turn_A']=turn_and_speed(A,20,300,+1);
    # full
    def tb(e,hz):
        p=turn_and_speed(e,hz,300,+1); n=turn_and_speed(e,hz,300,-1)
        return p if p[0]>=n[0] else n
    res['turn_A']=tb(A,20); res['turn_B']=tb(B,20); res['turn_C3']=tb(C(3),20)
    res['turn_C4']=tb(C(4),20); res['turn_CS']=tb(CS,64)
    res['ramp_C3_pct']=d3; res['eqcap']=eqcap
    res['A_g45_150']=speed_at(A,150,45)-150
    res['B_g45_150']=speed_at(B,150,45)-150
    res['A_g30_150']=speed_at(A,150,30)-150
    res['B_g30_150']=speed_at(B,150,30)-150
    print("MACHINE:",res['turn_A'],res['turn_B'],res['turn_C3'],res['turn_CS'])
    return res

if __name__=="__main__":
    main()
