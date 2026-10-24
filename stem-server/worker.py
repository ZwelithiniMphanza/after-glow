"""One isolated Demucs job. No shell invocation and no user-controlled paths."""
import json,os,sys,zipfile
from pathlib import Path
import numpy as np
import soundfile as sf
import torch
from demucs.pretrained import get_model
from demucs.apply import apply_model
folder=Path(sys.argv[1]).resolve()
def progress(message):
    temp=folder/'progress.tmp';temp.write_text(json.dumps({'message':message}),encoding='utf-8');os.replace(temp,folder/'progress.json')
torch.set_num_threads(min(4,os.cpu_count() or 2))
progress('Loading four-stem AI model (first run downloads model weights)')
model=get_model('htdemucs');model.eval()
wave,rate=sf.read(folder/'input.wav',dtype='float32',always_2d=True)
if rate!=model.samplerate or wave.shape[1]!=model.audio_channels:raise ValueError('Invalid input audio format')
audio=torch.from_numpy(wave.T.copy());reference=audio.mean(0);mean=reference.mean();std=reference.std().clamp_min(1e-8);audio=(audio-mean)/std
progress('Separating vocals, drums, bass and other instruments on your computer')
with torch.inference_mode():
    stems=apply_model(model,audio[None],device='cpu',shifts=1,split=True,overlap=.25,progress=True,num_workers=0,segment=7)[0]
stems=stems*std+mean
progress('Writing four WAV stems')
with zipfile.ZipFile(folder/'stems.zip.part','w',zipfile.ZIP_DEFLATED,compresslevel=1) as output:
    for name,source in zip(model.sources,stems):
        path=folder/(name+'.wav');samples=source.T.cpu().numpy();peak=max(1.0,float(np.abs(samples).max()));sf.write(path,samples/peak,rate,subtype='PCM_16');output.write(path,path.name)
    output.writestr('README.txt','Afterglow server stems\nModel: Demucs htdemucs\nFour stems: vocals, drums, bass, other.\nAI isolation may contain bleed or artifacts. Peaks above full scale are normalized per stem.\n')
os.replace(folder/'stems.zip.part',folder/'stems.zip')
progress('Stems ready')
