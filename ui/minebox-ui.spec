Name: minebox-ui
#read Version from git tag
# we excpect a tag "ui_vM.m.p"

# *NOTE* M is the Major number and has to be a _single digit_

Version: %(git describe --tags --match 'ui*'|grep -oP "(?<=ui_v)[^-]+")
Release: %{BUILD_ID}%(git describe --tags --match 'ui*'|grep -oP "-.*$" | tr '-' '_')%{?dist}
Summary: The Minebox UI
License: Proprietary
Requires: minebox-rockstor minebox-uigateway

%description
The Minebox-specific User Interface pieces.

%define _topdir %(echo \$PWD)/

# Packaging
%install
mkdir -p "$RPM_BUILD_ROOT/opt/rockstor/static/minebox"
for item in %{_topdir}/ui/*; do
  ibase=`basename $item`
  if [ "$ibase" != "Jenkinsfile" -a "$ibase" != "minebox-ui.spec" ]; then
    cp -a "$item" "$RPM_BUILD_ROOT/opt/rockstor/static/minebox/"
  fi
done

# Installation script
%pre

%post

# Uninstallation script
%preun

%postun

%files

/opt/rockstor/static/minebox/*
