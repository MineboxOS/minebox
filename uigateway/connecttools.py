#!/usr/bin/env python

from flask import request, make_response, current_app
from functools import update_wrapper
from urlparse import urlparse
from os import environ
import re
import requests


SIAD_URL="http://localhost:9980/"
MINEBD_URL="http://localhost:8080/v1/"
MINEBD_AUTH_KEY_FILE="/etc/minebox/local-auth.key"
METADATA_URL=""
DEMODATA_URL="http://localhost:8050/v1/"
DEMOSIA_URL="http://localhost:9900/"


def setOrigin(*args, **kwargs):
    def decorator(f):
        def wrapped_function(*args, **kwargs):
            if request.method == 'OPTIONS':
                resp = current_app.make_default_options_response()
            else:
                resp = make_response(f(*args, **kwargs))

            # Note that credentials only work if origin is not "*".
            # Use host we are running on but respect port of requsting origin,
            # so port forwarders work.
            myurlparts = urlparse(request.url_root)
            if "Origin" in request.headers:
                originport = urlparse(request.headers["Origin"]).port
            else:
                originport = None
            if originport is None:
                origin = "https://%s" % (myurlparts.hostname)
            else:
                origin = "https://%s:%s" % (myurlparts.hostname, originport)
            resp.headers["Access-Control-Allow-Origin"] = origin
            resp.headers["Access-Control-Allow-Credentials"] = "true"
            resp.headers["Vary"] = "Origin"
            return resp

        f.provide_automatic_options = False
        f.required_methods = getattr(f, 'required_methods', set())
        f.required_methods.add('OPTIONS')
        return update_wrapper(wrapped_function, f)
    return decorator

def getFromSia(api):
    url = SIAD_URL + api
    # siad requires a specific UA header, so add that to defaults.
    headers = requests.utils.default_headers()
    headers.update({'User-Agent': 'Sia-Agent'})
    try:
        response = requests.get(url, headers=headers)
        if ('Content-Type' in response.headers
            and re.match(r'^application/json',
                         response.headers['Content-Type'])):
            # create a dict generated from the JSON response.
            siadata = response.json()
            if response.status_code >= 400:
                # For error-ish codes, tell that they are from Sia.
                siadata["messagesource"] = "sia"
            return siadata, response.status_code
        else:
            return {"message": response.text,
                    "messagesource": "sia"}, response.status_code
    except requests.ConnectionError as e:
        return {"message": str(e)}, 503
    except requests.RequestException as e:
        return {"message": str(e)}, 500


def postToSia(api, formData):
    url = SIAD_URL + api
    # siad requires a specific UA header, so add that to defaults.
    headers = requests.utils.default_headers()
    headers.update({'User-Agent': 'Sia-Agent'})
    try:
        response = requests.post(url, data=formData, headers=headers)
        if ('Content-Type' in response.headers
            and re.match(r'^application/json',
                         response.headers['Content-Type'])):
            # create a dict generated from the JSON response.
            siadata = response.json()
            if response.status_code >= 400:
                # For error-ish codes, tell that they are from Sia.
                siadata["messagesource"] = "sia"
            return siadata, response.status_code
        else:
            return {"message": response.text,
                    "messagesource": "sia"}, response.status_code
    except requests.ConnectionError as e:
        return {"message": str(e)}, 503
    except requests.RequestException as e:
        return {"message": str(e)}, 500


def getFromMineBD(api):
    url = MINEBD_URL + api
    # siad requires a specific UA header, so add that to defaults.
    with open(MINEBD_AUTH_KEY_FILE) as f:
        local_key = f.read().rstrip()
    try:
        response = requests.get(url, auth=("user", local_key))
        if ('Content-Type' in response.headers
            and re.match(r'^application/json',
                         response.headers['Content-Type'])):
            # create a dict generated from the JSON response.
            mbdata = response.json()
            if response.status_code >= 400:
                # For error-ish codes, tell that they are from MineBD.
                mbdata["messagesource"] = "MineBD"
            return mbdata, response.status_code
        else:
            return {"message": response.text,
                    "messagesource": "MineBD"}, response.status_code
    except requests.ConnectionError as e:
        return {"message": str(e)}, 503
    except requests.RequestException as e:
        return {"message": str(e)}, 500


def checkLogin():
    csrftoken = request.cookies.get('csrftoken')
    sessionid = request.cookies.get('sessionid')
    user_api = "https://localhost/api/commands/current-user"
    referer = "https://localhost/"

    headers = requests.utils.default_headers()
    headers.update({'X-CSRFToken': csrftoken, 'referer': referer})
    cookiejar = requests.cookies.RequestsCookieJar()
    cookiejar.set('csrftoken', csrftoken)
    cookiejar.set('sessionid', sessionid)
    try:
        # Given that we call localhost, the cert will be wrong, so don't verify.
        response = requests.post(user_api, data=[], headers=headers,
                                 cookies=cookiejar, verify=False)
        if response.status_code == 200:
          return response.json()
        else:
          current_app.logger.warn('No valid login found: %s' % response.text)
          return False
    except requests.exceptions.RequestException as e:
        current_app.logger.error('Error checking login: %s' % str(e))
        return False
