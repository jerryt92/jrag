import json

import requests


def printStreamResponse(response, pretty=True):
    print('\n' + response.request.method + ' ' + response.request.url + '\n')
    print('Status Code: %s' % response.status_code)
    headersDict = dict(response.headers)
    print('Headers:', json.dumps(headersDict, indent=2 if pretty else None))
    print('Events:')
    for line in response.iter_lines():
        if line:
            decoded_line = line.decode('utf-8')
            print(decoded_line)


def printResponse(response: requests.Response, pretty=True):
    print('\n' + response.request.method + ' ' + response.request.url + '\n')
    print('Status Code: %s' % response.status_code)
    headersDict = dict(response.headers)
    print('Headers:', json.dumps(headersDict, indent=2 if pretty else None))
    if 'Content-Type' in headersDict and 'application/json' in headersDict['Content-Type']:
        print('Body:', json.dumps(response.json(), indent=2 if pretty else None))
    else:
        print('Body:', response.text)
