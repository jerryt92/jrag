import requests
import json
from config.config import *
from common.utills.restUtils import printStreamResponse

requestBody = {
    "model": "qwen3-14b-mlx",
    "messages": [
        {
            "content": "你名字叫“Jrag AI”\n/no_think",
            "role": "system"
        },
        {
            "content": "你是谁",
            "role": "user"
        }
    ],
    "max_tokens": 32768,
    "stream": True
}


def printOpenAiResponse(response):
    for line in response.iter_lines():
        if line:
            decoded_line = line.decode('utf-8')
            if decoded_line.startswith('data:'):
                data = decoded_line[6:]
                if data != '[DONE]':
                    json_data = json.loads(data)
                    print(
                        json_data
                        ['choices']
                        [0]
                        ['delta'].get('content', ''),
                        end=''
                    )


if __name__ == '__main__':
    response = requests.post(baseUrl + '/v1/chat/completions', headers=headers, data=json.dumps(requestBody),
                             stream=True)
    printStreamResponse(response)
