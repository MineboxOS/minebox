#!/usr/bin/env python

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function
from flask import request, make_response, current_app
from functools import update_wrapper
from urlparse import urlparse
from os import environ
import time
import re
import requests


SIAD_URL="http://localhost:9980/"
MINEBD_URL="http://localhost:8080/v1/"
MINEBD_AUTH_KEY_FILE="/etc/minebox/local-auth.key"
BACKUPSERVICE_URL="http://localhost:5100/"
METADATA_URL="https://metadata.minebox.io/v1/"
LOCALDEMO_URL="http://localhost:8050/v1/"
DEMOSIAD_URL="http://localhost:9900/"


def set_origin(*args, **kwargs):
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
            if request.method == 'OPTIONS':
                resp.headers["Access-Control-Allow-Methods"] = resp.headers['Allow']
            resp.headers["Access-Control-Allow-Credentials"] = "true"
            resp.headers["Vary"] = "Origin"
            return resp

        f.provide_automatic_options = False
        f.required_methods = getattr(f, 'required_methods', set())
        f.required_methods.add('OPTIONS')
        return update_wrapper(wrapped_function, f)
    return decorator


def get_demo_url():
    if 'DEMO' in environ and environ['DEMO'] == 'local':
        return LOCALDEMO_URL
    return METADATA_URL


def get_from_sia(api):
    if 'DEMO' in environ:
        url = DEMOSIAD_URL + api
    else:
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


def post_to_sia(api, formData):
    if 'DEMO' in environ:
        url = DEMOSIAD_URL + api
    else:
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


def get_from_backupservice(api):
    url = BACKUPSERVICE_URL + api
    try:
        response = requests.get(url)
        if ('Content-Type' in response.headers
            and re.match(r'^application/json',
                         response.headers['Content-Type'])):
            # create a dict generated from the JSON response.
            bsdata = response.json()
            if response.status_code >= 400:
                # For error-ish codes, tell that they are from Sia.
                bsdata["messagesource"] = "backupservice"
            return bsdata, response.status_code
        else:
            return {"message": response.text,
                    "messagesource": "backupservice"}, response.status_code
    except requests.ConnectionError as e:
        return {"message": str(e)}, 503
    except requests.RequestException as e:
        return {"message": str(e)}, 500


def get_from_minebd(api):
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


def put_to_minebd(api, formData, addHeaders = []):
    url = MINEBD_URL + api
    # siad requires a specific UA header, so add that to defaults.
    with open(MINEBD_AUTH_KEY_FILE) as f:
        local_key = f.read().rstrip()
    # Add headers to default request.
    headers = requests.utils.default_headers()
    for header in addHeaders:
        headers.update(header)
    try:
        response = requests.put(url, auth=("user", local_key), data=formData)
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


def _get_metadata_token():
    # If we do not have a token or timestamp is older than 5 minutes,
    # fetch a new token from MineBD (who uses metadata service for that).
    # Note that Metadata tokens are valid for 10 minutes right now.
    if (not hasattr(_get_metadata_token, "token") or _get_metadata_token.token is None
        or _get_metadata_token.timestamp < time.time() - 5 * 60):
        # Always set the timestamp so we do not have to test above if it's set,
        #  as it's only unset when token is also unset
        _get_metadata_token.timestamp = time.time()
        mbdata, mb_status_code = get_from_minebd('auth/getMetadataToken')
        if mb_status_code == 200:
            _get_metadata_token.token = mbdata["message"]
        else:
            current_app.logger.error('Error %s getting metadata token from MineBD: %s' % (mb_status_code,  mbdata["message"]))
            _get_metadata_token.token = None
    return _get_metadata_token.token


def get_from_metadata(api):
    url = get_demo_url() + api
    token = _get_metadata_token()
    if token is None:
        return {"message": "Error requesting metadata token."}, 500

    try:
        headers = requests.utils.default_headers()
        headers.update({'X-Auth-Token': token})
        response = requests.get(url, headers=headers)
        if ('Content-Type' in response.headers
            and re.match(r'^application/json',
                         response.headers['Content-Type'])):
            # create a dict generated from the JSON response.
            mdata = response.json()
            if response.status_code >= 400:
                # For error-ish codes, tell that they are from MineBD.
                mdata["messagesource"] = "Metadata"
            return mdata, response.status_code
        else:
            return {"message": response.text,
                    "messagesource": "Metadata"}, response.status_code
    except requests.ConnectionError as e:
        return {"message": str(e)}, 503
    except requests.RequestException as e:
        return {"message": str(e)}, 500


def put_to_metadata(api, formData):
    url = get_demo_url() + api
    token = _get_metadata_token()
    if token is None:
        return {"message": "Error requesting metadata token."}, 500

    try:
        headers = requests.utils.default_headers()
        headers.update({'X-Auth-Token': token})
        response = requests.put(url, data=formData, headers=headers)
        if ('Content-Type' in response.headers
            and re.match(r'^application/json',
                         response.headers['Content-Type'])):
            # create a dict generated from the JSON response.
            mdata = response.json()
            if response.status_code >= 400:
                # For error-ish codes, tell that they are from MineBD.
                mdata["messagesource"] = "Metadata"
            return mdata, response.status_code
        else:
            return {"message": response.text,
                    "messagesource": "Metadata"}, response.status_code
    except requests.ConnectionError as e:
        return {"message": str(e)}, 503
    except requests.RequestException as e:
        return {"message": str(e)}, 500


def delete_from_metadata(api):
    url = get_demo_url() + api
    token = _get_metadata_token()
    if token is None:
        return {"message": "Error requesting metadata token."}, 500

    try:
        headers = requests.utils.default_headers()
        headers.update({'X-Auth-Token': token})
        response = requests.delete(url, headers=headers)
        if ('Content-Type' in response.headers
            and re.match(r'^application/json',
                         response.headers['Content-Type'])):
            # create a dict generated from the JSON response.
            mdata = response.json()
            if response.status_code >= 400:
                # For error-ish codes, tell that they are from MineBD.
                mdata["messagesource"] = "Metadata"
            return mdata, response.status_code
        else:
            return {"message": response.text,
                    "messagesource": "Metadata"}, response.status_code
    except requests.ConnectionError as e:
        return {"message": str(e)}, 503
    except requests.RequestException as e:
        return {"message": str(e)}, 500


def check_login():
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
