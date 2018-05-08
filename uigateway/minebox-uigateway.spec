Name: minebox-uigateway
#read Version from git tag
# we excpect a tag "mug_vM.m.p"

Version: %(git describe --tags --match 'mug*'|grep -oP "(?<=mug_v)[^-]+")
Release: %{getenv:BUILD_ID}%(git describe --tags --match 'mug*'|grep -oP -- "-.*$" | tr '-' '_')%{?dist}
Summary: Minebox UI Gateway (MUG)
License: Proprietary
Requires: minebox-virtualenv MineBD sudo systemd
Requires(pre): /usr/sbin/useradd, /usr/sbin/groupadd, /usr/bin/getent, /usr/sbin/usermod
Requires(postun): /usr/sbin/userdel,  /usr/sbin/groupdel

%description
The Minebox UI Gateway (MUG) is a python service that allows the UI to access Minebox system functionality.

%define _topdir %(echo \$PWD)/

# Packaging
%install
install -pD --mode 755 "%{_topdir}uigateway/mug.sh" "$RPM_BUILD_ROOT/usr/lib/minebox/mug.sh"
install -pD --mode 644 "%{_topdir}uigateway/sudoers.d/mug" "$RPM_BUILD_ROOT/etc/sudoers.d/mug"
install -pD --mode 644 "%{_topdir}uigateway/systemd/mug.service" "$RPM_BUILD_ROOT/usr/lib/systemd/system/mug.service"
install -pD --mode 644 "%{_topdir}uigateway/minebox_settings.json" "$RPM_BUILD_ROOT/etc/minebox/minebox_settings.json"
install -pD --mode 755 "%{_topdir}uigateway/mug.py" "$RPM_BUILD_ROOT/usr/lib/minebox/mbvenv/mug.py"
install -pD --mode 644 "%{_topdir}uigateway/backupinfo.py" "$RPM_BUILD_ROOT/usr/lib/minebox/mbvenv/backupinfo.py"
install -pD --mode 644 "%{_topdir}uigateway/connecttools.py" "$RPM_BUILD_ROOT/usr/lib/minebox/mbvenv/connecttools.py"

# Installation script
%pre
/usr/bin/getent group mug || /usr/sbin/groupadd -r mug
/usr/bin/getent passwd mug || /usr/sbin/useradd -r -d /usr/lib/minebox/mbvenv -s /sbin/nologin -g mug mug
/usr/sbin/usermod mug -a -G minebd
/usr/sbin/usermod webconfig -a -G minebd
set +e
systemctl stop mug
set -e

%post
systemctl daemon-reload
systemctl enable mug
systemctl start mug
chown mug:mug /etc/minebox/minebox_settings.json

# Uninstallation script
%preun
if [ "$1" = 0 ] ; then
set +e
systemctl stop mug
systemctl disable mug
set -e
fi

%postun
if [ "$1" = 0 ] ; then
/usr/sbin/userdel mug
/usr/sbin/groupdel mug
fi
systemctl daemon-reload

%files

/usr/lib/minebox/mug.sh
/etc/sudoers.d/mug
/usr/lib/systemd/system/mug.service
%config(noreplace) /etc/minebox/minebox_settings.json
/usr/lib/minebox/mbvenv/mug.py
/usr/lib/minebox/mbvenv/mug.pyc
/usr/lib/minebox/mbvenv/mug.pyo
/usr/lib/minebox/mbvenv/backupinfo.py
/usr/lib/minebox/mbvenv/backupinfo.pyc
/usr/lib/minebox/mbvenv/backupinfo.pyo
/usr/lib/minebox/mbvenv/connecttools.py
/usr/lib/minebox/mbvenv/connecttools.pyc
/usr/lib/minebox/mbvenv/connecttools.pyo
