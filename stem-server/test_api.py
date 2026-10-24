"""Integration checks against the local server, using only generated demo audio."""
import json,time,urllib.request,urllib.error,uuid,io,wave,math,struct
from pathlib import Path
root=Path(__file__).resolve().parent
base='http://127.0.0.1:8765'
token=(root/'.token').read_text().strip()
def request(path,method='GET',data=None,auth=True):
    headers={'Authorization':'Bearer '+token} if auth else {}
    req=urllib.request.Request(base+path,data=data,headers=headers,method=method)
    try:
        with urllib.request.urlopen(req,timeout=15) as response:return response.status,json.load(response)
    except urllib.error.HTTPError as error:return error.code,json.load(error)
assert request('/health',auth=False)[0]==401
assert request('/health')[1]['stems']==['vocals','drums','bass','other']
assert request('/jobs','POST',b'invalid wave'*5)[0]==400
buffer=io.BytesIO()
with wave.open(buffer,'wb') as audio:
    audio.setnchannels(2);audio.setsampwidth(2);audio.setframerate(44100)
    audio.writeframes(b''.join(struct.pack('<hh',int(math.sin(i*2*math.pi*220/44100)*4000),int(math.sin(i*2*math.pi*330/44100)*4000)) for i in range(44100*3)))
fixture=buffer.getvalue()
job=uuid.uuid4().hex
assert request('/jobs/'+job,'POST',fixture)[0]==202
assert request('/jobs','POST',fixture)[0]==409
assert request('/jobs/'+job,'DELETE')[0]==200
for _ in range(100):
    if not (root/'.data'/job).exists():break
    time.sleep(.1)
assert not (root/'.data'/job).exists(),'Cancelled job files were not cleaned'
assert request('/jobs','POST',b'invalid wave'*5)[0]==400,'Cancelled job did not release server slot'
print('PASS: authentication, malformed WAV rejection, single-job limit, cancellation and temporary-file cleanup')
