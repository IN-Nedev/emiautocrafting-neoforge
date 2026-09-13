# SPDX-License-Identifier: GPL-3.0-only
import asyncio
# Local-only fixture: fixed 300 ms latency in each direction, retaining normal throughput and order.
async def connection(reader, writer):
    remote_reader, remote_writer = await asyncio.open_connection('127.0.0.1',25565)
    async def forward(src,dst):
        queue=asyncio.Queue()
        async def read():
            try:
                while data:=await src.read(65536):
                    await queue.put((asyncio.get_running_loop().time()+0.3,data))
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
    server=await asyncio.start_server(connection,'127.0.0.1',25566)
    print('Latency proxy listening on 127.0.0.1:25566',flush=True)
    async with server: await server.serve_forever()
asyncio.run(main())
