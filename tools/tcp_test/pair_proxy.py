"""Loopback-only ordered TCP delay. Both directions delayed; no Minecraft packets are forged."""
import socket,threading,time,pathlib,json,sys
listen=int(sys.argv[1]);target=int(sys.argv[2]);delay=float(sys.argv[3])/1000;directory=pathlib.Path(sys.argv[4])
directory.mkdir(parents=True,exist_ok=True)
counts={'connections':0,'bytes_up':0,'bytes_down':0}
def pump(source,destination,key):
    try:
        while True:
            data=source.recv(65536)
            if not data:break
            time.sleep(delay);destination.sendall(data);counts[key]+=len(data)
    except OSError:pass
    finally:
        try:destination.shutdown(socket.SHUT_WR)
        except OSError:pass
def connection(client):
    upstream=socket.create_connection(('127.0.0.1',target));counts['connections']+=1
    a=threading.Thread(target=pump,args=(client,upstream,'bytes_up'),daemon=True);a.start()
    pump(upstream,client,'bytes_down');client.close();upstream.close()
with socket.socket() as server:
    server.bind(('127.0.0.1',listen));server.listen();server.settimeout(.5)
    print('Loopback proxy ready',listen,'->',target,'delay per read',delay,flush=True)
    while not (directory/'DONE.txt').exists() and not (directory/'FAIL.txt').exists():
        try:client,_=server.accept();threading.Thread(target=connection,args=(client,),daemon=True).start()
        except socket.timeout:pass
(directory/'TRANSPORT.json').write_text(json.dumps({'delay_per_read_ms':delay*1000,**counts},indent=2))
