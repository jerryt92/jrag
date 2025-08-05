from config.config import *
from common.restUtils import *

requestBody = {
    "model": "text-embedding-nomic-embed-text",
    "input": [
        "今天要不要提前出门",
        "打印结果",
        "今天人多吗"
    ]
}

if __name__ == '__main__':
    response = requests.post(baseUrl + '/v1/embeddings', headers=headers, data=json.dumps(requestBody))
    printResponse(response, pretty=False)
