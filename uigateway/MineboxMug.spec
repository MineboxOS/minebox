Name: MineboxMUG
#read Version from git tag
# we excpect a tag "mug_vM.m.p"

# *NOTE* M is the Major number and has to be a _single digit_

Version: %(git describe --tags --match 'mug*'|grep -oP "(?<=mug_v).")
Release: %(git describe --tags --match 'mug*'|grep -oP "(?<=mug_v..).*" | tr '-' '_')%{?dist}
Summary: Our core module
License: Proprietary
Requires: python-flask python-requests pyOpenSSL

%description
MineboxMUG is a python service that allows the UI to access Minebox system functionality.

%define _topdir %(echo \$PWD)/

# Packaging
%install
install -D --mode 755 "%{_topdir}uigateway/mug.sh" "$RPM_BUILD_ROOT/usr/lib/minebox/mug.sh"
install -D "%{_topdir}uigateway/systemd/mug.service" "$RPM_BUILD_ROOT/etc/systemd/system/mug.service"
install -D --mode 755 "%{_topdir}uigateway/mug.py" "$RPM_BUILD_ROOT/usr/lib/minebox/mbvenv/mug.py"
install -D "%{_topdir}uigateway/backupinfo.py" "$RPM_BUILD_ROOT/usr/lib/minebox/mbvenv/backupinfo.py"
install -D "%{_topdir}uigateway/connecttools.py" "$RPM_BUILD_ROOT/usr/lib/minebox/mbvenv/connecttools.py"

# Installation script
%pre
set +e
systemctl stop mug
set -e

%post
systemctl daemon-reload
systemctl enable mug
systemctl start mug

# Uninstallation script
%preun
if [ "$1" = 0 ] ; then
set +e
systemctl stop mug
systemctl disable mug
set -e
fi

%postun
systemctl daemon-reload

%files

/usr/lib/minebox/mug.sh
/etc/systemd/system/mug.service
/usr/lib/minebox/mbvenv/mug.py
/usr/lib/minebox/mbvenv/mug.pyc
/usr/lib/minebox/mbvenv/mug.pyo
/usr/lib/minebox/mbvenv/backupinfo.py
/usr/lib/minebox/mbvenv/backupinfo.pyc
/usr/lib/minebox/mbvenv/backupinfo.pyo
/usr/lib/minebox/mbvenv/connecttools.py
/usr/lib/minebox/mbvenv/connecttools.pyc
/usr/lib/minebox/mbvenv/connecttools.pyo