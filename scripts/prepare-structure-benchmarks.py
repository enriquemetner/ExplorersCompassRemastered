#!/usr/bin/env python3
"""Prepare an isolated Fabric 1.20.1 test installation; never edits normal run/ data."""
import hashlib,json,shutil,urllib.request,urllib.parse,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
GAME=ROOT/'run-structure-benchmarks'
HEADERS={'User-Agent':'ExplorersCompassStructureBenchmark/1.0'}
def read(url):
    with urllib.request.urlopen(urllib.request.Request(url,headers=HEADERS),timeout=90) as response:return response.read()
def api(path):return json.loads(read('https://api.modrinth.com/v2/'+path))
def prepare():
    GAME.mkdir(exist_ok=True)
    for folder in ['mods','config']:
        source=ROOT/'run'/folder
        if source.exists():
            for entry in source.rglob('*'):
                target=GAME/folder/entry.relative_to(source)
                if entry.is_file() and not target.exists():
                    target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(entry,target)
    versions=[];seen=set()
    provided={'P7dR8mSH'} # Fabric API is supplied by the Gradle development runtime.
    def install(project,version=None):
        if project in seen or project in provided:return
        seen.add(project)
        if version:v=api('version/'+version)
        else:
            query=urllib.parse.urlencode({'loaders':'["fabric"]','game_versions':'["1.20.1"]'})
            candidates=api('project/'+project+'/version?'+query)
            v=next(x for x in candidates if x['version_type']=='release')
        file=next((f for f in v['files'] if f.get('primary')),v['files'][0])
        data=read(file['url'])
        assert hashlib.sha512(data).hexdigest()==file['hashes']['sha512'], 'Download checksum mismatch'
        # Remove older copies of this same mod ID in the isolated folder only.
        import io
        with zipfile.ZipFile(io.BytesIO(data)) as z:modid=json.loads(z.read('fabric.mod.json'))['id']
        for old in (GAME/'mods').glob('*.jar'):
            with zipfile.ZipFile(old) as z:
                if 'fabric.mod.json' in z.namelist() and json.loads(z.read('fabric.mod.json'))['id']==modid:old.unlink()
        (GAME/'mods'/file['filename']).write_bytes(data)
        versions.append({'project':project,'version':v['version_number'],'version_id':v['id'],'file':file['filename'],'sha512':file['hashes']['sha512']})
        print(modid,v['version_number'],flush=True)
        for dependency in v['dependencies']:
            if dependency['dependency_type']=='required':install(dependency['project_id'],dependency.get('version_id'))
    lock=ROOT/'scripts/structure-benchmark-mods.lock.json'
    if lock.exists():
        for entry in json.loads(lock.read_text()):install(entry['project'],entry['version_id'])
    else:
        for project in ['repurposed-structures-fabric','when-dungeons-arise','yungs-better-strongholds']:install(project)
        lock.write_text(json.dumps(versions,indent=2)+'\n')
    lock.write_text(json.dumps(versions,indent=2)+'\n')
    (GAME/'benchmark-mods.json').write_text(json.dumps(versions,indent=2)+'\n')
    print('Prepared',GAME)
if __name__=='__main__':prepare()
