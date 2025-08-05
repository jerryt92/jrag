from config.config import *
from common.restUtils import *

requestBody = {
    "model": "nomic-embed-text:latest",
    "input": [
    "今天要不要提前出门",
    "打印结果",
    "今天人多吗"
  ]
}

if __name__ == '__main__':
    response = requests.post(baseUrl + '/api/embed', headers=headers, data=json.dumps(requestBody))
    printResponse(response)
