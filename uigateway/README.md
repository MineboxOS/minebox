# Minebox UI Gateway (MUG)

A python service that allows the UI to access Minebox system functionality.

This is exposed to the same "public" that the UI itself is exposed to, so needs
to keep security high.
See [functionality outline](../doc/mb-ui-gateway-funktionen-skizze.md) in docs.

## Requirements and notes:

For REST API, it looks like [Falcon](http://falconframework.org), [Bottle](http://bottlepy.org/),
and [Flask](http://flask.pocoo.org/) are lightweight choices from reasearch, a
Python expert from Mozilla says that he considers django (which apparently can
be kept very light) or Flask the best choices.  
For now, we use Flask:
```
pip install flask
```
Or at yum level:
```
yum install python-flask
```
This results in the following packages being installed:
`python-flask`, `python-itsdangerous`, `python-werkzeug` from `extras`
and `python-babel`, `python-jinja2`, `python-markupsafe` from `base`.

As this installs Flask 0.10.1 (yes, an old version), here's the quickstart docs
for that version: http://flask.pocoo.org/docs/0.10/quickstart/

We also need the `requests` module to interact with other REST APIs.
```
pip install requests
```
Or at yum level:
```
yum install python-requests
```
This results in the following packages being installed:
`python-requests`, `python-backports`, `python-backports-ssl_match_hostname`,
`python-chardet`, `python-urllib3` from `base`.

For using SSL, we need the `OpenSSL` module in python (with `werkzeug` 0.10+
this may not be required, but CentOS 7 has 0.91)
```
pip install pyopenssl
```
Or at yum level:
```
yum install pyOpenSSL
```
This adds no dependencies to the above.


As the distro-installed packages create issues, we are installing our own stuff
in virtualenv:
```
yum install python-virtualenv python2-pip
```
This results in the following packages being installed:
`python-backports`, `python-backports-ssl_match_hostname`, `python-virtualenv`,
`python-setuptools` from `base` and `python2-pip` from `epel`.

Commands to set up / work with VirtualEnv:
```
cd /usr/lib/minebox
virtualenv mug
source mug/bin/activate
pip install flask flask-cors requests packaging appdirs
pip install pyopenssl
<do stuff>
deactivate
```
