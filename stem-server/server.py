"""Afterglow's loopback-only, authenticated, single-job stem API."""
import atexit, re, hashlib, hmac, json, os, secrets, shutil, subprocess, sys, threading, time, uuid, wave
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
ROOT=Path(__file__).resolve().parent
DATA=ROOT/'.data'; DATA.mkdir(exist_ok=True)
TOKEN_FILE=ROOT/'.token'
if not TOKEN_FILE.exists(): TOKEN_FILE.write_text(secrets.token_urlsafe(32),encoding='utf-8')
TOKEN=TOKEN_FILE.read_text(encoding='utf-8').strip()
LOCK=threading.RLock(); JOBS={}; ACTIVE=None
MAX_BYTES=200*1024*1024

def remove_job(job):
    folder=job['folder'].resolve()
    if folder.parent==DATA.resolve(): shutil.rmtree(folder,ignore_errors=True)

def execute(job):
    global ACTIVE
    try:
        with (job['folder']/'worker.log').open('w',encoding='utf-8') as log:
            with LOCK:
                if job['cancelled']: return
                job['state']='processing'
                job['process']=subprocess.Popen([sys.executable,str(ROOT/'worker.py'),str(job['folder'])],stdout=log,stderr=log,creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0),env={**os.environ,'TORCH_HOME':str(ROOT/'.models')})
            try: code=job['process'].wait(timeout=3600)
            except subprocess.TimeoutExpired:
                job['process'].kill();job['process'].wait();raise RuntimeError('Separation exceeded one hour. Try a shorter track.')
        with LOCK:
            if job['cancelled']: job['state']='cancelled'
            elif code==0 and (job['folder']/'stems.zip').is_file(): job['state']='complete'
            else: job['state']='failed';job['message']='AI separation failed. See the local server worker.log.'
    except Exception as exc:
        job['state']='failed';job['message']=str(exc)
    finally:
        with LOCK:
            if ACTIVE==job['id']: ACTIVE=None
            job['process']=None;job['finished']=time.time()
        for f in job['folder'].glob('*.wav'): f.unlink(missing_ok=True)
        if job['cancelled']: remove_job(job)

def shutdown():
    with LOCK:
        for job in JOBS.values():
            p=job.get('process')
            if p and p.poll() is None: p.terminate()
atexit.register(shutdown)

def cleanup():
    while True:
        time.sleep(60)
        with LOCK:
            for key,job in list(JOBS.items()):
                if job.get('finished',float('inf'))<time.time()-3600:
                    remove_job(job);JOBS.pop(key,None)

class Handler(BaseHTTPRequestHandler):
    def log_message(self,*args): pass  # Do not log credentials, filenames or requests.
    def setup(self):
        super().setup();self.connection.settimeout(60)
    def reply(self,code,value):
        data=json.dumps(value).encode();self.send_response(code);self.send_header('Content-Type','application/json');self.send_header('Content-Length',str(len(data)));self.end_headers();self.wfile.write(data)
    def authorized(self):
        if not hmac.compare_digest(self.headers.get('Authorization',''),'Bearer '+TOKEN):
            self.reply(401,{'error':'Check the server token in Studio.'});return False
        return True
    def job(self):
        parts=self.path.strip('/').split('/')
        if len(parts) not in (2,3) or parts[0]!='jobs': return None
        with LOCK: return JOBS.get(parts[1])
    def do_GET(self):
        if not self.authorized(): return
        if self.path=='/health': self.reply(200,{'service':'afterglow-stems','model':'htdemucs','stems':['vocals','drums','bass','other']});return
        job=self.job()
        if not job: self.reply(404,{'error':'Job no longer available.'});return
        if self.path.endswith('/result'):
            if job['state']!='complete': self.reply(409,{'error':'Stems are not ready.'});return
            try:
                with (job['folder']/'stems.zip').open('rb') as src:
                    size=os.fstat(src.fileno()).st_size;self.send_response(200);self.send_header('Content-Type','application/zip');self.send_header('Content-Length',str(size));self.end_headers();shutil.copyfileobj(src,self.wfile,65536)
            except (BrokenPipeError,ConnectionResetError): pass
            return
        status={'id':job['id'],'state':job['state'],'message':job.get('message','Preparing server job')}
        try:
            progress=json.loads((job['folder']/'progress.json').read_text());status['message']=progress['message'] if job['state']=='processing' else status['message']
        except (OSError,ValueError,KeyError): pass
        self.reply(200,status)
    def do_POST(self):
        global ACTIVE
        if not self.authorized(): return
        if self.path!='/jobs' and not re.fullmatch(r'/jobs/[a-f0-9]{32}',self.path): self.reply(404,{'error':'Unknown endpoint'});return
        try: size=int(self.headers.get('Content-Length','0'))
        except ValueError: size=0
        if not 44<=size<=MAX_BYTES: self.reply(413,{'error':'WAV upload must be under 200 MB.'});return
        with LOCK:
            if ACTIVE is not None: self.reply(409,{'error':'The server is busy with another track.'});return
            jid=self.path.split('/')[-1] if self.path!='/jobs' else uuid.uuid4().hex
            if jid in JOBS:self.reply(409,{'error':'Job already exists.'});return
            folder=DATA/jid;folder.mkdir();job={'id':jid,'folder':folder,'state':'uploading','cancelled':False,'process':None};JOBS[jid]=job;ACTIVE=jid
        try:
            remaining=size
            with (folder/'input.wav').open('wb') as out:
                while remaining:
                    if job['cancelled']:raise ValueError('Upload cancelled.')
                    block=self.rfile.read(min(65536,remaining))
                    if not block: raise ValueError('Upload interrupted.')
                    out.write(block);remaining-=len(block)
            with wave.open(str(folder/'input.wav'),'rb') as audio:
                if audio.getnchannels()!=2 or audio.getsampwidth()!=2 or audio.getframerate()!=44100 or not 0<audio.getnframes()/44100<=600: raise ValueError('Use stereo 44.1 kHz PCM WAV, up to 10 minutes.')
            job['state']='queued';threading.Thread(target=execute,args=(job,),daemon=True).start();self.reply(202,{'id':jid,'state':'queued'})
        except (OSError,ValueError,wave.Error) as exc:
            with LOCK:
                if ACTIVE==jid: ACTIVE=None
                JOBS.pop(jid,None)
            remove_job(job)
            try:self.reply(400,{'error':str(exc)})
            except OSError:pass
    def do_DELETE(self):
        if not self.authorized(): return
        job=self.job()
        if not job:self.reply(404,{'error':'Job no longer available.'});return
        with LOCK:
            job['cancelled']=True;job['state']='cancelled';p=job.get('process')
            if p and p.poll() is None:p.terminate()
            elif job.get('finished'):remove_job(job)
        self.reply(200,{'state':'cancelled'})

if __name__=='__main__':
    # Clean abandoned uploads after an interrupted previous server run.
    for folder in DATA.iterdir():
        if folder.is_dir():shutil.rmtree(folder)
    threading.Thread(target=cleanup,daemon=True).start()
    server=ThreadingHTTPServer(('127.0.0.1',8765),Handler)
    print('Afterglow stem server listening on 127.0.0.1:8765 (USB reverse).',flush=True)
    try:server.serve_forever()
    finally:shutdown();server.server_close()
