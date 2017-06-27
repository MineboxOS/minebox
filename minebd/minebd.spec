Name: MineBD
Version: 1
Release: 1%{?dist}
Summary: Our core module
License: Proprietary

%description
MineBD is the core module of a Minebox

%define _topdir %(echo \$PWD)/

%install
install -D %{_topdir}minebd/build/libs/minebd-1.0-SNAPSHOT-all.jar $RPM_BUILD_ROOT/usr/lib/minebox/minebd-1.0-SNAPSHOT-all.jar

%files
/usr/lib/minebox/minebd-1.0-SNAPSHOT-all.jar
