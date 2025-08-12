import hashlib


def getSha256(data) -> str:
    return hashlib.sha256(data).hexdigest()
