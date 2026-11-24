import random, datetime as dt
random.seed(2026)
fn_f=["María","Rosa","Ana","Carmen","Lucía","Patricia","Gabriela","Sofía","Valeria","Daniela","Milagros","Elena","Karina","Jessica","Fiorella","Andrea"]
fn_m=["José","Luis","Carlos","Jorge","Miguel","Juan Carlos","Pedro","Diego","Renzo","Alonso","Víctor","Raúl","César","Martín","Óscar","Fernando"]
ln=["Quispe","Flores","Huamán","Rojas","Mendoza","Torres","Vargas","Castillo","Ramírez","Chávez","Gutiérrez","Salazar","Paredes","Rivera","Espinoza","Sánchez","Díaz","Cárdenas","Ortiz","Medina","Vega","Ríos","Aguilar","Herrera","Cruz","Ccahuana","Ticona","Mamani","Palomino","Zevallos"]
dist=["Chaclacayo","Chaclacayo","Chaclacayo","Ate","Ate","Chosica","Chosica","Santa Eulalia","Huaycán","Cieneguilla","La Molina"]
streets=["Av. Nicolás Ayllón","Jr. Los Pinos","Calle Las Begonias","Av. Las Flores","Jr. San Martín","Calle Los Olivos","Av. José Carlos Mariátegui","Jr. Huáscar"]
N=32
pats=[]; used=set()
for i in range(N):
    sex=random.choice("FM"); first=random.choice(fn_f if sex=="F" else fn_m)
    last=f"{random.choice(ln)} {random.choice(ln)}"
    while True:
        dni=str(random.randint(40000000,79999999))
        if dni not in used and dni!="60870579": used.add(dni); break
    by=random.choice(list(range(1958,2014)))
    birth=dt.date(by,random.randint(1,12),random.randint(1,28))
    phone="9"+str(random.randint(10000000,99999999))
    d=random.choice(dist)
    addr=f"{random.choice(streets)} {random.randint(100,1899)}"
    created=dt.datetime(2026,9,random.randint(20,30),random.randint(9,18),random.choice([0,15,30,45]))
    pats.append(dict(dni=dni,first=first,last=last,birth=birth,sex=sex,phone=phone,d=d,addr=addr,created=created))
reasons=[("Evaluación inicial",30),("Control",30),("Profilaxis",45),("Restauración",45),("Endodoncia",60),("Exodoncia",45),("Control de ortodoncia",30),("Dolor dental",30),("Blanqueamiento",60)]
cancel_r=["El paciente pidió otra fecha","Viaje imprevisto","Problemas de salud","Cruce con su horario de trabajo"]
now=dt.datetime(2026,11,17,15,45)
busy_d={1:[],2:[]}; busy_p={i:[] for i in range(N)}
busy_d[2]+= [(dt.datetime(2026,11,10,15,30),dt.datetime(2026,11,10,16,30)),(dt.datetime(2026,11,10,17,30),dt.datetime(2026,11,10,18,15)),(dt.datetime(2026,11,11,11,0),dt.datetime(2026,11,11,12,0))]
def free(lst,s,e): return all(not(s<b and a<e) for a,b in lst)
appts=[]
day=dt.date(2026,10,1)
while day<=dt.date(2026,12,12):
    wd=day.isoweekday()
    if wd<=6:
        blocks=[(9,13)] + ([] if wd==6 else [(15,19)])
        for den in (1,2):
            n=random.randint(3,6) if day<=now.date() else random.randint(2,5)
            for _ in range(n*3):
                if n<=0: break
                b=random.choice(blocks); r,dur=random.choice(reasons)
                st=dt.datetime.combine(day,dt.time(b[0]))+dt.timedelta(minutes=15*random.randint(0,(b[1]-b[0])*4-dur//15))
                en=st+dt.timedelta(minutes=dur)
                if en>dt.datetime.combine(day,dt.time(b[1])): continue
                pi=random.randrange(N)
                if not free(busy_d[den],st,en) or not free(busy_p[pi],st,en): continue
                if pats[pi]['created']>st-dt.timedelta(days=1): continue
                busy_d[den].append((st,en)); busy_p[pi].append((st,en)); n-=1
                x=random.random(); reason_s=None; arrived=None
                if en<=now:
                    if x<0.76: status="ATENDIDA"; arrived=st-dt.timedelta(minutes=random.randint(3,15))
                    elif x<0.88: status="CANCELADA"; reason_s=random.choice(cancel_r)
                    else: status="NO_ASISTIO"
                elif st<=now: status="EN_ATENCION"; arrived=st-dt.timedelta(minutes=5)
                else:
                    if x<0.08: status="CANCELADA"; reason_s=random.choice(cancel_r)
                    elif (st-now).days<3 and x<0.6: status="CONFIRMADA"
                    else: status="PROGRAMADA"
                created=min(st-dt.timedelta(days=random.randint(1,10)), now-dt.timedelta(hours=1))
                appts.append((pi,den,st,en,dur,r,status,reason_s,arrived,created))
    day+=dt.timedelta(days=1)
q=lambda v: "NULL" if v is None else "'"+str(v).replace("'","''")+"'"
out=["SET NAMES utf8mb4;","START TRANSACTION;"]
for i,p in enumerate(pats):
    out.append(f"INSERT INTO patients (doc_type,doc_number,first_name,last_name,birth_date,sex,phone,email,address,district,active,created_at,created_by,notify_channel,demo,data_consent,data_consent_at) VALUES ('DNI',{q(p['dni'])},{q(p['first'])},{q(p['last'])},{q(p['birth'])},{q(p['sex'])},{q(p['phone'])},NULL,{q(p['addr'])},{q(p['d'])},TRUE,{q(p['created'])},'recepcion','WHATSAPP',TRUE,TRUE,{q(p['created'])}); SET @p{i}=LAST_INSERT_ID();")
rows=[]
for (pi,den,st,en,dur,r,s,rs,ar,cr) in appts:
    rows.append(f"(@p{pi},{den},{q(st)},{q(en)},{dur},{q(r)},{q(s)},{q(rs)},{q(ar)},{q(cr)},'recepcion')")
for k in range(0,len(rows),100):
    out.append("INSERT INTO appointments (patient_id,dentist_id,start_at,end_at,duration_min,reason,status,status_reason,arrived_at,created_at,created_by) VALUES "+",".join(rows[k:k+100])+";")
out.append("COMMIT;")
out.append("SELECT 'pacientes', COUNT(*) FROM patients UNION ALL SELECT 'citas', COUNT(*) FROM appointments UNION ALL SELECT CONCAT('citas ',status), COUNT(*) FROM appointments GROUP BY status;")
open('seed.sql','w').write("\n".join(out))
from collections import Counter
print(len(pats), len(appts), Counter(a[6] for a in appts))
# self-check overlaps
for den in (1,2):
    L=sorted(busy_d[den]); assert all(L[i][1]<=L[i+1][0] for i in range(len(L)-1))
for pi in busy_p:
    L=sorted(busy_p[pi]); assert all(L[i][1]<=L[i+1][0] for i in range(len(L)-1))
print("sin cruces OK")
