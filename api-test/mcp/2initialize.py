import requests
from config.config import *
from utills.restUtils import printResponse, printStreamResponse

# {
#   "mcpServers": {
#     "mcp-server-chart": {
#       "type": "sse",
#       "url": "https://mcp.api-inference.modelscope.net/3ad34471ffb244/sse"
#     }
#   }
# }

requestBody = {
    "jsonrpc": "2.0",
    "method": "initialize",
    "id": 'py-mcp-test',
    "params": {
        "protocolVersion": "2025-03-26",
        "capabilities": {
            "sampling": {},
            "roots": {"listChanged": True}
        },
        "clientInfo": {
            "name": "Spring AI MCP Client",
            "version": "0.3.1"
        }
    }
}

if __name__ == '__main__':
    response = requests.post(baseUrl + '/messages/?session_id=' + sessionId, headers=headers,
                             json=requestBody, stream=False)
    printStreamResponse(response, pretty=True)
