Name: MineBD
#read Version from git tag
# we excpect a tag "minebd_vM.m.p"

# *NOTE* M is the Major number and has to be a _single digit_

Version: %(git describe --tags --match 'minebd*'|grep -oP "(?<=minebd_v).")
Release: %(git describe --tags --match 'minebd*'|grep -oP "(?<=minebd_v..).*" | tr '-' '_')%{?dist}
Summary: Our core module
License: Proprietary

%description
MineBD is the core module of a Minebox

%define _topdir %(echo \$PWD)/

# Packaging
%install
install -D "%{_topdir}minebd/build/libs/minebd-1.0-SNAPSHOT-all.jar" "$RPM_BUILD_ROOT/usr/lib/minebox/minebd-1.0-SNAPSHOT-all.jar"
install -D --mode 755 "%{_topdir}minebd/systemd/mount-nbd0.sh" "$RPM_BUILD_ROOT/usr/lib/minebox/mount-nbd0.sh"
install -D --mode 755 "%{_topdir}minebd/mbkey.sh" "$RPM_BUILD_ROOT/usr/lib/minebox/mbkey.sh"
install -D "%{_topdir}minebd/config.yaml" "$RPM_BUILD_ROOT/etc/minebox/config.yaml"
install -D "%{_topdir}minebd/systemd/minebd.service" "$RPM_BUILD_ROOT/etc/systemd/system/minebd.service"
install -D "%{_topdir}minebd/systemd/nbd@nbd0.service.d/mount.conf" "$RPM_BUILD_ROOT/etc/systemd/system/nbd@nbd0.service.d/mount.conf"
install -D "%{_topdir}distro-tools/VM/nbd-client-config" "$RPM_BUILD_ROOT/etc/nbdtab"

# Installation script
%pre

%post
systemctl daemon-reload
# TBD do this different and don't append all the time
echo modprobe nbd >> "/etc/rc.modules"
modprobe nbd
systemctl enable minebd
systemctl start minebd &

# Uninstallation script
%preun
if [ "$1" = 0 ] ; then
set +e
systemctl stop nbd@ndb0
systemctl stop minebd
systemctl disable minebd
set -e
fi

%postun
systemctl daemon-reload

%files
/usr/lib/minebox/minebd-1.0-SNAPSHOT-all.jar
/usr/lib/minebox/mount-nbd0.sh
/usr/lib/minebox/mbkey.sh
/etc/minebox/config.yaml
/etc/systemd/system/minebd.service
/etc/systemd/system/nbd@nbd0.service.d/mount.conf
/etc/nbdtab

