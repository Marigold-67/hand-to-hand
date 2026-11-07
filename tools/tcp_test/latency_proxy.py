"""Ordered stream delay with independently scheduled delivery, not a sleep per read."""
import socket,threading,time,pathlib,json,sys,queue
listen,target=int(sys.argv[1]),int(sys.argv[2]);directory=pathlib.Path(sys.argv[3]);directory.mkdir(parents=True,exist_ok=True)
counts={'connections':0,'bytes_up':0,'bytes_down':0,'queue_peak':0};profiles={};halt=threading.Event()
def profile():
 try:return json.loads((directory/'NETWORK.json').read_text())
 except (OSError,ValueError):return {'name':'startup','one_way_ms':20,'jitter_ms':[0]}
def pump(source,destination,key):
 q=queue.Queue();last=0;index=0
 def sender():
  try:
   while True:
    item=q.get()
    if item is None:break
    at,data=item;remaining=at-time.monotonic()
    if remaining>0:halt.wait(remaining)
    if halt.is_set():break
    destination.sendall(data);counts[key]+=len(data)
  except OSError:pass
  finally:
   try:destination.shutdown(socket.SHUT_WR)
   except OSError:pass
 t=threading.Thread(target=sender,daemon=True);t.start()
 try:
  while not halt.is_set():
   data=source.recv(65536)
   if not data:break
   cfg=profile();name=cfg.get('name','unknown');profiles[name]=cfg
   jitter=cfg.get('jitter_ms',[0]);delay=max(0,cfg.get('one_way_ms',20)+jitter[index%len(jitter)]+cfg.get('extra_delay_ms',0))/1000
   at=max(last,time.monotonic()+delay);last=at;index+=1;q.put((at,data));counts['queue_peak']=max(counts['queue_peak'],q.qsize())
 except OSError:pass
 finally:q.put(None);t.join(timeout=3)
def connection(client):
 try:
  upstream=socket.create_connection(('127.0.0.1',target));counts['connections']+=1
  t=threading.Thread(target=pump,args=(client,upstream,'bytes_up'),daemon=True);t.start();pump(upstream,client,'bytes_down');client.close();upstream.close()
 except OSError:client.close()
with socket.socket() as server:
 server.bind(('127.0.0.1',listen));server.listen();server.settimeout(.5)
 print('Scheduled loopback delay proxy ready',listen,'->',target,flush=True)
 while not (directory/'DONE.txt').exists() and not (directory/'FAIL.txt').exists():
  try:client,_=server.accept();threading.Thread(target=connection,args=(client,),daemon=True).start()
  except socket.timeout:pass
halt.set();(directory/'TRANSPORT.json').write_text(json.dumps({'method':'scheduled ordered stream chunks, both directions','profiles':profiles,**counts},indent=2))
