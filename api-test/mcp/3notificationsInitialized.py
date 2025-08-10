import requests

from config.config import *
from utills.restUtils import printStreamResponse

requestBody = {
    "method": "notifications/initialized",
    "jsonrpc": "2.0"
}

if __name__ == '__main__':
    response = requests.post(baseUrl + '/messages/?session_id=' + sessionId, headers=headers,
                             json=requestBody, stream=False)
    printStreamResponse(response, pretty=True)
