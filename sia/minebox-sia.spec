Name: minebox-sia

Version: %{getenv:SIA_VERSION}
Release: %{getenv:BUILD_ID}%{?dist}
Summary: Sia - decentralized cloud storage platform
License: MIT License
Requires: minebox-virtualenv minebox-uigateway systemd
Requires(pre): /usr/sbin/useradd, /usr/sbin/groupadd, /usr/bin/getent
Requires(postun): /usr/sbin/userdel,  /usr/sbin/groupdel

%description
Sia is a new decentralized cloud storage platform that radically alters the landscape of cloud storage. By leveraging smart contracts, client-side encryption, and sophisticated redundancy (via Reed-Solomon codes), Sia allows users to safely store their data with hosts that they do not know or trust.

%define _topdir %(echo \$PWD)/

# Packaging
%install
install -pD --mode 755 "%{_topdir}BUILD/siad" "$RPM_BUILD_ROOT/usr/bin/siad"
install -pD --mode 755 "%{_topdir}BUILD/siac" "$RPM_BUILD_ROOT/usr/bin/siac"
install -pD --mode 755 "%{_topdir}sia/trafficshaper.sh" "$RPM_BUILD_ROOT/usr/lib/minebox/trafficshaper.sh"
install -pD --mode 644 "%{_topdir}sia/systemd/minebox-trafficshaper.service" "$RPM_BUILD_ROOT/usr/lib/systemd/system/minebox-trafficshaper.service"
install -pD --mode 644 "%{_topdir}sia/systemd/sia.service" "$RPM_BUILD_ROOT/usr/lib/systemd/system/sia.service"

# Installation script
%pre
/usr/bin/getent group sia || /usr/sbin/groupadd -r sia
/usr/bin/getent passwd sia || /usr/sbin/useradd -r -d /mnt/lower1/sia -s /sbin/nologin -g sia sia
set +e
systemctl stop sia
systemctl stop minebox-trafficshaper
if [ "$1" = "2" ]; then
# On updates, disable first to reflect path changes correctly.
systemctl disable sia
systemctl disable minebox-trafficshaper
fi
set -e

%post
/usr/bin/chown -R sia:sia /mnt/lower*/sia/
systemctl daemon-reload
systemctl enable minebox-trafficshaper
systemctl start minebox-trafficshaper
systemctl enable sia
systemctl start sia

# Uninstallation script
%preun
if [ "$1" = 0 ] ; then
set +e
systemctl stop sia
systemctl disable sia
systemctl stop minebox-trafficshaper
systemctl disable minebox-trafficshaper
set -e
fi

%postun
if [ "$1" = 0 ] ; then
/usr/sbin/userdel sia
/usr/sbin/groupdel sia
fi
systemctl daemon-reload

%files

/usr/bin/siac
/usr/bin/siad
/usr/lib/minebox/trafficshaper.sh
/usr/lib/systemd/system/minebox-trafficshaper.service
/usr/lib/systemd/system/sia.service
