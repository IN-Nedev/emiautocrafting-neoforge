# SPDX-License-Identifier: GPL-3.0-only
import asyncio
import argparse

parser = argparse.ArgumentParser(description='Loopback-only Minecraft latency fixture')
parser.add_argument('--delay-ms', type=float, default=300, help='Added latency in EACH direction')
parser.add_argument('--listen-port', type=int, default=25566)
parser.add_argument('--server-port', type=int, default=25565)
args = parser.parse_args()
if args.delay_ms < 0 or not all(0 < port < 65536 for port in (args.listen_port, args.server_port)):
    parser.error('Delay must be nonnegative and ports must be between 1 and 65535')
# Queue each stream direction to retain normal throughput and ordering.
async def connection(reader, writer):
    remote_reader, remote_writer = await asyncio.open_connection('127.0.0.1',args.server_port)
    async def forward(src,dst):
        queue=asyncio.Queue()
        async def read():
            try:
                while data:=await src.read(65536):
                    await queue.put((asyncio.get_running_loop().time()+args.delay_ms/1000,data))
            finally: await queue.put((0,None))
        async def send():
            try:
                while True:
                    due,data=await queue.get()
                    if data is None:break
                    await asyncio.sleep(max(0,due-asyncio.get_running_loop().time()))
                    dst.write(data);await dst.drain()
            finally:dst.close()
        await asyncio.gather(read(),send())
    await asyncio.gather(forward(reader,remote_writer),forward(remote_reader,writer),return_exceptions=True)
async def main():
    server=await asyncio.start_server(connection,'127.0.0.1',args.listen_port)
    print(f'Latency proxy 127.0.0.1:{args.listen_port} -> 127.0.0.1:{args.server_port}; added RTT {2*args.delay_ms:g} ms',flush=True)
    async with server: await server.serve_forever()
asyncio.run(main())
