import json

import requests


def printStreamResponse(response, pretty=True):
    print('\n' + response.request.method + ' ' + response.request.url + '\n')
    print('Status Code: %s' % response.status_code)
    headersDict = dict(response.headers)
    print('Response Headers:', json.dumps(headersDict, indent=2 if pretty else None))
    print('Events:\n')
    for line in response.iter_lines():
        if line:
            decoded_line = line.decode('utf-8')
            print(decoded_line)


def printResponse(response: requests.Response, pretty=True):
    print('\n' + response.request.method + ' ' + response.request.url + '\n')
    print('Status Code: %s' % response.status_code)
    headersDict = dict(response.headers)
    print('Response Headers:', json.dumps(headersDict, indent=2 if pretty else None), '\n')
    if 'application/json' in headersDict.get('Content-Type', ''):
        if pretty:
            print('Response Body:')
            print(json.dumps(response.json(), indent=2, ensure_ascii=False))
        else:
            print('Response Body:', json.dumps(response.json(), separators=(',', ':')))
        return response.json()
    else:
        print('Response Body:', response.text)
        return response.text
