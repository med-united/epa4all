import asyncio
import websockets
import json
import base64

class TelematikWebSocketServer:

    def __init__(self, telematik_id="X110486750"):
        self.telematik_id = telematik_id
        self.fake_pdf_base64 = base64.b64encode(b"%PDF Content").decode()

    async def send_events(self, websocket):
        print("New WebSocket connection established.")

        try:
            print("time starts 10")
            await asyncio.sleep(10)
            medication_pdf_event = json.dumps({
                "kvnr": self.telematik_id,
                "medicationPdfBase64": self.fake_pdf_base64
            })
            await websocket.send(medication_pdf_event)
            print("Sent Medication PDF event")

            print("time starts 10")
            await asyncio.sleep(10)
            medication_pdf_event = json.dumps({
                "kvnr": "X110683202",
                "medicationPdfBase64": self.fake_pdf_base64
            })
            await websocket.send(medication_pdf_event)
            print("Sent Medication PDF event")

            print("time starts 10")
            await asyncio.sleep(10)
            medication_pdf_event = json.dumps({
                "kvnr": "X110486750",
                "medicationPdfBase64": self.fake_pdf_base64
            })
            await websocket.send(medication_pdf_event)
            print("Sent Medication PDF event")

            while True:
                await asyncio.sleep(10)

        except websockets.exceptions.ConnectionClosed:
            print("Connection closed")

    async def start_server(self):
        print("WebSocket Server Running on ws://localhost:8765")

        async with websockets.serve(self.send_events, "localhost", 8765):
            await asyncio.Future()

if __name__ == "__main__":
    server = TelematikWebSocketServer()
    asyncio.run(server.start_server())
