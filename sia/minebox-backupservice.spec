Name: minebox-backupservice
#read Version from git tag
# we excpect a tag "bkupsvc_vM.m.p"

Version: %(git describe --tags --match 'bkupsvc*'|grep -oP "(?<=bkupsvc_v)[^-]+")
Release: %{getenv:BUILD_ID}%(git describe --tags --match 'bkupsvc*'|grep -oP -- "-.*$" | tr '-' '_')%{?dist}
Summary: Minebox Backup Service
License: Proprietary
Requires: minebox-virtualenv minebox-uigateway systemd cronie btrfs-progs minebox-sia dmidecode hdparm

%description
The Minebox Backup Service drives the actual generation and upload of backups as well as the setup of the Sia service.

%define _topdir %(echo \$PWD)/

# Packaging
%install
install -pD --mode 755 "%{_topdir}sia/backup-service" "$RPM_BUILD_ROOT/usr/lib/minebox/backup-service"
install -pD --mode 644 "%{_topdir}sia/systemd/backup-service.service" "$RPM_BUILD_ROOT/usr/lib/systemd/system/backup-service.service"
install -pD --mode 644 "%{_topdir}sia/systemd/minebox-ip-notify.service" "$RPM_BUILD_ROOT/usr/lib/systemd/system/minebox-ip-notify.service"
install -pD --mode 644 "%{_topdir}sia/cron.d/backup-service" "$RPM_BUILD_ROOT/etc/cron.d/backup-service"
install -pD --mode 755 "%{_topdir}sia/backup-service.py" "$RPM_BUILD_ROOT/usr/lib/minebox/mbvenv/backup-service.py"
install -pD --mode 644 "%{_topdir}sia/backuptools.py" "$RPM_BUILD_ROOT/usr/lib/minebox/mbvenv/backuptools.py"
install -pD --mode 644 "%{_topdir}sia/siatools.py" "$RPM_BUILD_ROOT/usr/lib/minebox/mbvenv/siatools.py"
install -pD --mode 644 "%{_topdir}sia/systemtools.py" "$RPM_BUILD_ROOT/usr/lib/minebox/mbvenv/systemtools.py"

# Installation script
%pre
set +e
systemctl stop backup-service
set -e

%post
systemctl daemon-reload
systemctl enable backup-service
systemctl start backup-service
systemctl enable minebox-ip-notify
# On upgrades, also restart MUG as it depends on some libraries packaged here.
if [ $1 == 2 ] ; then
  systemctl restart mug
fi

# Uninstallation script
%preun
if [ "$1" = 0 ] ; then
set +e
systemctl stop backup-service
systemctl disable backup-service
set -e
fi

%postun
systemctl daemon-reload

%files

/usr/lib/minebox/backup-service
/usr/lib/systemd/system/backup-service.service
/usr/lib/systemd/system/minebox-ip-notify.service
/etc/cron.d/backup-service
/usr/lib/minebox/mbvenv/backup-service.py
/usr/lib/minebox/mbvenv/backup-service.pyc
/usr/lib/minebox/mbvenv/backup-service.pyo
/usr/lib/minebox/mbvenv/backuptools.py
/usr/lib/minebox/mbvenv/backuptools.pyc
/usr/lib/minebox/mbvenv/backuptools.pyo
/usr/lib/minebox/mbvenv/siatools.py
/usr/lib/minebox/mbvenv/siatools.pyc
/usr/lib/minebox/mbvenv/siatools.pyo
/usr/lib/minebox/mbvenv/systemtools.py
/usr/lib/minebox/mbvenv/systemtools.pyc
/usr/lib/minebox/mbvenv/systemtools.pyo
