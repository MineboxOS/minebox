Name: minebox-sia

Version: 1.2.2
Release: %(git describe --tags --match 'minebox*'|grep -oP "(?<=minebox_v).*" | tr '-' '_')%{?dist}
Summary: Sia - decentralized cloud storage platform
License: MIT License
Requires: minebox-virtualenv minebox-uigateway
Requires(pre): /usr/sbin/useradd, /usr/bin/getent
Requires(postun): /usr/sbin/userdel

%description
Sia is a new decentralized cloud storage platform that radically alters the landscape of cloud storage. By leveraging smart contracts, client-side encryption, and sophisticated redundancy (via Reed-Solomon codes), Sia allows users to safely store their data with hosts that they do not know or trust.

%define _topdir %(echo \$PWD)/

# Packaging
%install
install -D --mode 755 "%{_topdir}BUILD/siad" "$RPM_BUILD_ROOT/usr/local/siad"
install -D --mode 755 "%{_topdir}BUILD/siac" "$RPM_BUILD_ROOT/usr/local/siac"
install -D "%{_topdir}sia/systemd/sia.service" "$RPM_BUILD_ROOT/etc/systemd/system/sia.service"

# Installation script
%pre
/usr/bin/getent group sia || /usr/sbin/groupadd -r sia
/usr/bin/getent passwd sia || /usr/sbin/useradd -r -d /mnt/lower1/sia -s /sbin/nologin sia
set +e
systemctl stop sia
set -e

%post
/usr/bin/chown -R sia:sia /mnt/lower*/sia/
systemctl daemon-reload
systemctl enable sia
systemctl start sia

# Uninstallation script
%preun
if [ "$1" = 0 ] ; then
set +e
systemctl stop sia
systemctl disable sia
set -e
fi

%postun
/usr/sbin/userdel sia
systemctl daemon-reload

%files

/usr/local/siac
/usr/local/siad
/etc/systemd/system/sia.service
