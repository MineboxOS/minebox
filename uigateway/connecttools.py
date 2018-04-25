#!/usr/bin/env python

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function
from flask import request, make_response, current_app
from functools import update_wrapper
from urlparse import urlparse
from os import environ
import os
import time
import re
import requests
import warnings


SIAD_URL="http://localhost:9980/"
MINEBD_URL="http://localhost:8080/v1/"
MINEBD_AUTH_KEY_FILE="/etc/minebox/local-auth.key"
BACKUPSERVICE_URL="http://localhost:5100/"
SETTINGS_URL="https://settings.api.minebox.io/v1/settings/settings/"
FAUCET_URL="https://faucet.api.minebox.io/v1/faucet/"
ADMIN_URL="https://faucet.api.minebox.io/v1/faucet/admin/"
METADATA_URL="https://metadata.api.minebox.io/v1/metadata/"
LOCALDEMO_URL="http://localhost:8050/v1/"
DEMOSIAD_URL="http://localhost:9900/"
ROCKSTOR_URL="https://localhost/"


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
            originhost = myurlparts.hostname
            if "Origin" in request.headers:
                req_originparts = urlparse(request.headers["Origin"])
                if req_originparts.hostname in current_app.config["allowed_cors_hosts"]:
                    originhost = req_originparts.hostname
                originport = req_originparts.port
            else:
                originport = None
            if originport is None:
                origin = "https://%s" % (originhost)
            else:
                origin = "https://%s:%s" % (originhost, originport)
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
                # For error-ish codes, tell that they are from backup service.
                bsdata["messagesource"] = "backupservice"
            return bsdata, response.status_code
        else:
            return {"message": response.text,
                    "messagesource": "backupservice"}, response.status_code
    except requests.ConnectionError as e:
        return {"message": str(e)}, 503
    except requests.RequestException as e:
        return {"message": str(e)}, 500


def post_to_backupservice(api, formData):
    url = BACKUPSERVICE_URL + api
    try:
        response = requests.post(url, data=formData, headers=headers)
        if ('Content-Type' in response.headers
            and re.match(r'^application/json',
                         response.headers['Content-Type'])):
            # create a dict generated from the JSON response.
            bsdata = response.json()
            if response.status_code >= 400:
                # For error-ish codes, tell that they are from backup service.
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
    # MineBD access requires the local key, fetch it.
    if not os.path.isfile(MINEBD_AUTH_KEY_FILE):
        return {"message": "No local key found, aborting."}, 503
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
        elif response.text[0] == "{" and response.text[-1] == "}":
            # HACK: We seem to have a JSON response without a JSON Content-Type.
            # Still try to get it as JSON.
            current_app.logger.warn('Warning: MineBD sent a JSON response without correct Content-Type for end point /%s', api)
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
    # MineBD access requires the local key, fetch it.
    if not os.path.isfile(MINEBD_AUTH_KEY_FILE):
        return {"message": "No local key found, aborting."}, 503
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
                # For error-ish codes, tell that they are from metadata.
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
                # For error-ish codes, tell that they are from metadata.
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
                # For error-ish codes, tell that they are from metadata.
                mdata["messagesource"] = "Metadata"
            return mdata, response.status_code
        else:
            return {"message": response.text,
                    "messagesource": "Metadata"}, response.status_code
    except requests.ConnectionError as e:
        return {"message": str(e)}, 503
    except requests.RequestException as e:
        return {"message": str(e)}, 500


def get_from_mineboxconfig(api):
    url = SETTINGS_URL + api
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
                # For error-ish codes, tell that they are from settings.
                mdata["messagesource"] = "Settings"
            return mdata, response.status_code
        else:
            return {"message": response.text,
                    "messagesource": "Settings"}, response.status_code
    except requests.ConnectionError as e:
        return {"message": str(e)}, 503
    except requests.RequestException as e:
        return {"message": str(e)}, 500


def post_to_faucetservice(api, queryData):
    url = FAUCET_URL + api
    token = _get_metadata_token()
    if token is None:
        return {"message": "Error requesting metadata token."}, 500

    try:
        headers = requests.utils.default_headers()
        headers.update({'X-Auth-Token': token})
        response = requests.post(url, params=queryData, headers=headers)
        if ('Content-Type' in response.headers
            and re.match(r'^application/json',
                         response.headers['Content-Type'])):
            # create a dict generated from the JSON response.
            mdata = response.json()
            if response.status_code >= 400:
                # For error-ish codes, tell that they are from faucet.
                mdata["messagesource"] = "Faucet"
            return mdata, response.status_code
        else:
            return {"message": response.text,
                    "messagesource": "Faucet"}, response.status_code
    except requests.ConnectionError as e:
        return {"message": str(e)}, 503
    except requests.RequestException as e:
        return {"message": str(e)}, 500


def post_to_adminservice(api, usetoken, jsonData):
    url = ADMIN_URL + api
    if usetoken:
        token = _get_metadata_token()
        if token is None:
            return {"message": "Error requesting metadata token."}, 500

    try:
        headers = requests.utils.default_headers()
        if usetoken:
            headers.update({'X-Auth-Token': token})
        headers.update({'Accept': 'application/json'})
        response = requests.post(url, json=jsonData, headers=headers)
        if ('Content-Type' in response.headers
            and re.match(r'^application/json',
                         response.headers['Content-Type'])):
            # create a dict generated from the JSON response.
            mdata = response.json()
            if response.status_code >= 400:
                # For error-ish codes, tell that they are from admin.
                mdata["messagesource"] = "Admin"
            return mdata, response.status_code
        else:
            return {"message": response.text,
                    "messagesource": "Admin"}, response.status_code
    except requests.ConnectionError as e:
        return {"message": str(e)}, 503
    except ValueError as e:
        return {"message": str(e)}, 503
    except requests.RequestException as e:
        return {"message": str(e)}, 500

def rockstor_user_setup():
    csrftoken = request.cookies.get('csrftoken')
    sessionid = request.cookies.get('sessionid')
    user_setup = ROCKSTOR_URL + "setup_user"
    referer = ROCKSTOR_URL

    headers = requests.utils.default_headers()
    headers.update({'X-CSRFToken': csrftoken, 'referer': referer})
    cookiejar = requests.cookies.RequestsCookieJar()
    cookiejar.set('csrftoken', csrftoken)
    cookiejar.set('sessionid', sessionid)
    try:
        # Given that we call localhost, the cert will be wrong, so
        # don't verify and suppress the warning on doing an insecure request.
        with warnings.catch_warnings():
            warnings.filterwarnings("ignore",
                category=requests.packages.urllib3.exceptions.InsecureRequestWarning)
            response = requests.get(user_setup, headers=headers,
                                    cookies=cookiejar, verify=False)
        if response.status_code == 200:
            return not response.json()["new_setup"]
        else:
            current_app.logger.warn('Checking users failed: %s' % response.text)
            return None
    except requests.exceptions.RequestException as e:
        current_app.logger.error('Error checking users: %s' % str(e))
        return None


def check_login():
    # If we got an auth token, see if it's the local auth key.
    authtoken = request.headers.get('X-Auth-Token')
    if authtoken:
        local_key = None
        if os.path.isfile(MINEBD_AUTH_KEY_FILE):
            with open(MINEBD_AUTH_KEY_FILE) as f:
                local_key = f.read().rstrip()
        if authtoken == local_key:
            # Fake a user name to fit with what users expect.
            return "[local_service]"
        else:
            current_app.logger.warn('Wrong auth token: %s' % authtoken)
            return False
    # We have no auth token, so check for django login.
    csrftoken = request.cookies.get('csrftoken')
    sessionid = request.cookies.get('sessionid')
    user_api = ROCKSTOR_URL + "api/commands/current-user"
    referer = ROCKSTOR_URL

    headers = requests.utils.default_headers()
    headers.update({'X-CSRFToken': csrftoken, 'referer': referer})
    cookiejar = requests.cookies.RequestsCookieJar()
    cookiejar.set('csrftoken', csrftoken)
    cookiejar.set('sessionid', sessionid)
    try:
        # Given that we call localhost, the cert will be wrong, so
        # don't verify and suppress the warning on doing an insecure request.
        with warnings.catch_warnings():
            warnings.filterwarnings("ignore",
                category=requests.packages.urllib3.exceptions.InsecureRequestWarning)
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
