curl --verbose --data-binary '{"jsonrpc": "1.0", "id":1, "method": "getblock", "params": ["00005bea8fea118804b4f5273f21a8bd5b43611a22b873b223c8b394f43a0176", true] }' -H 'Content-Type: application/json' http://127.0.0.1:$PORT/
