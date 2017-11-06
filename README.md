# Minebox

This repository provides the source code of components running on a Minebox
that are not paret of other projects (such as Sia, Rockstor, or CentOS
components, whose open-source code is available elsewhere).

The main software components available in this repository are:

* **MineBD**:  
  Minebox block device, the virtual disk central to the Minebox storage
  (Source directory: minebd/)
* **MUG (Minebox UI Gateway)**:  
  Flask service to connect the UI with system functions
  (Source directory: uigateway/)
* **Backup Service**:  
  Internally-used Flask service that handles creation of backups, but also
  system maintenance tasks like setting up Sia correctly.
  (Source directory: sia/)
* **UI**:  
  The Minebox-specific web user interface
  (Source directory: ui/)
