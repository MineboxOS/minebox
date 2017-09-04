Name: minebox-register-machine
#read Version from git tag
# we excpect a tag "sysadmin_vM.m.p"

# *NOTE* M is the Major number and has to be a _single digit_

Version: %(git describe --tags --match 'sysadmin*'|grep -oP "(?<=sysadmin_v).")
Release: %(git describe --tags --match 'sysadmin*'|grep -oP "(?<=sysadmin_v..).*" | tr '-' '_')%{?dist}
Summary: Minebox machine registration script
License: Proprietary
Requires: minebox-backupservice minebox-uigateway

%description
At first installation of the machine, this script is used to register it with the Minebox admin service.

%define _topdir %(echo \$PWD)/

# Packaging
%install
install -pD --mode 755 "%{_topdir}sysadmin/register-machine.sh" "$RPM_BUILD_ROOT/usr/lib/minebox/register-machine.sh"
install -pD --mode 755 "%{_topdir}sysadmin/print-machine-info.py" "$RPM_BUILD_ROOT/usr/lib/minebox/mbvenv/print-machine-info.py"
install -pD --mode 755 "%{_topdir}sysadmin/register-machine.py" "$RPM_BUILD_ROOT/usr/lib/minebox/mbvenv/register-machine.py"

# Installation script
%pre

%post
ln -s -f /usr/lib/minebox/register-machine.sh /usr/bin/register-machine.sh

# Uninstallation script
%preun

%postun
if [ "$1" = 0 ] ; then
  rm -f /usr/bin/register-machine.sh
fi

%files

/usr/lib/minebox/register-machine.sh
/usr/lib/minebox/mbvenv/print-machine-info.py
/usr/lib/minebox/mbvenv/print-machine-info.pyc
/usr/lib/minebox/mbvenv/print-machine-info.pyo
/usr/lib/minebox/mbvenv/register-machine.py
/usr/lib/minebox/mbvenv/register-machine.pyc
/usr/lib/minebox/mbvenv/register-machine.pyo
