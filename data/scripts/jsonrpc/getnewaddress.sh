curl --verbose --data-binary '{"jsonrpc": "1.0", "id":1, "method": "getnewaddress", "params": ["_FOR_TEST_ONLY"]  }' -H 'Content-Type: application/json' http://127.0.0.1:$PORT/
