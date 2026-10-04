#!/usr/bin/env bash
# Print the visible text/desc + bounds of the current screen; `ui.sh tap "Text"` taps the first node whose text/desc contains it.
export PATH=$PATH:/Users/tim/Library/Android/sdk/platform-tools
export ANDROID_SERIAL=${ANDROID_SERIAL:-37211FDJG009GS}
adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
XML=$(adb shell cat /sdcard/ui.xml); adb shell rm /sdcard/ui.xml
python3 - "$1" "$2" <<PY
import sys,re,subprocess
x='''$XML'''
nodes=[]
for m in re.finditer(r'<node [^>]*>',x):
    n=m.group(0)
    t=re.search(r'\btext="([^"]*)"',n).group(1); d=re.search(r'content-desc="([^"]*)"',n).group(1)
    b=re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',n).groups()
    nodes.append((t or d,tuple(map(int,b))))
if sys.argv[1]=='tap':
    q=sys.argv[2].lower()
    for t,b in sorted(nodes,key=lambda n:n[0].lower()!=q):
        if q in t.lower():
            cx,cy=(b[0]+b[2])//2,(b[1]+b[3])//2
            subprocess.run(['adb','shell','input','tap',str(cx),str(cy)]); print('tapped',t,cx,cy); sys.exit(0)
    print('NOT FOUND',sys.argv[2]); sys.exit(1)
for t,b in nodes:
    if t: print(t,b)
PY
