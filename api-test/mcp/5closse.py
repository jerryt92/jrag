import requests
from config.config import *
from utills.restUtils import printResponse, printStreamResponse

# {
#   "mcpServers": {
#     "mcp-server-chart": {
#       "type": "sse",
#       "url": "https://mcp.api-inference.modelscope.net/07d63f2d31d343/sse"
#     }
#   }
# }

requestBody = {
    "jsonrpc": "2.0",
    "id": 'py-mcp-test',
    "method": "tools/list",
}

if __name__ == '__main__':
    response = requests.post(baseUrl + '/messages/?session_id=' + sessionId, headers=headers,
                             json=requestBody, stream=False)
    printStreamResponse(response, pretty=True)

