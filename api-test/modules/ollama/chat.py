import requests
import json
from config.config import *
from common.utills.restUtils import printStreamResponse

requestBody = {
    "model": "qwen3:14b",
    "messages": [
        {
            "content": "你名字叫“Jrag AI”\n/no_think",
            "role": "system"
        },
        {
            "content": "你是谁？你能做什么？",
            "role": "user"
        }
    ],
    "options": {
        "temperature": 0.1,
        "num_ctx": 32768
    },
    "stream": True,
    "keepAlive": 3600
}


def printOllamaResponse(response):
    for line in response.iter_lines():
        if line:
            decoded_line = line.decode('utf-8')
            json_data = json.loads(decoded_line)
            print(
                json_data
                ['message']
                .get('content', ''),
                end=''
            )


if __name__ == '__main__':
    response = requests.post(baseUrl + '/api/chat', headers=headers, data=json.dumps(requestBody), stream=True)
    printStreamResponse(response)
