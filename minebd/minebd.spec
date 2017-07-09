Name: MineBD
Version: 1
Release: 1%{?dist}
Summary: Our core module
License: Proprietary

%description
MineBD is the core module of a Minebox

%define _topdir %(echo \$PWD)/

# Packaging
%install
install -D --target-directory "$RPM_BUILD_ROOT/usr/lib/minebox/" "%{_topdir}minebd/build/libs/minebd-1.0-SNAPSHOT-all.jar"
install -D --target-directory "$RPM_BUILD_ROOT/usr/lib/minebox/" --mode 755 "%{_topdir}minebd/systemd/mount-nbd0.sh"
install -D --target-directory "$RPM_BUILD_ROOT/etc/minebox/" "%{_topdir}minebd/config.yaml"
install -D --target-directory "$RPM_BUILD_ROOT/etc/systemd/system/" "%{_topdir}minebd/systemd/minebd.service"
install -D --target-directory "$RPM_BUILD_ROOT/etc/systemd/system/" "%{_topdir}minebd/systemd/nbd@nbd0.service.d"

# Installation script
%pre
set +e
systemctl stop nbd@ndb0
systemctl stop nbd-server
systemctl disable nbd-server
set -e

%post
systemctl daemon-reload
systemctl enable minebd
systemctl start minebd

# Uninstallation script
%preun
set +e
systemctl stop nbd@ndb0
systemctl stop nbd-server
systemctl disable nbd-server
systemctl stop minebd
systemctl disable minebd
set -e

%postun
systemctl daemon-reload

%files
/usr/lib/minebox/minebd-1.0-SNAPSHOT-all.jar
/usr/lib/minebox/mount-nbd0.sh
/etc/minebox/config.yaml
/etc/systemd/system/minebd.service
/etc/systemd/system/nbd@nbd0.service.d

