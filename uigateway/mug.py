#!/usr/bin/env python

from flask import Flask, jsonify
app = Flask(__name__)

@app.route("/")
def api_root():
    return jsonify(
      info="Hello World!"
    )

if __name__ == "__main__":
    app.run(host='0.0.0.0')
