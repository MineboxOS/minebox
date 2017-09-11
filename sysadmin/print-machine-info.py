#!/usr/bin/env python

# Script to register a new machine with the Minebox admin service.

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function
import logging
from systemtools import get_machine_info, get_local_ipaddress

# *** variables & constants ***


# Run the actual meat of the script.
def run(*args):
    # Set up logging, see https://docs.python.org/2/howto/logging.html
    logger = logging.getLogger("register-machine")
    logger.setLevel(logging.DEBUG)
    log_handler = logging.StreamHandler()
    log_handler.setLevel(logging.DEBUG)
    logger.addHandler(log_handler)

    machine_info = get_machine_info()
    logger.info("System UUID: %s", machine_info["system_uuid"])
    logger.info("Model (SKU): %s", machine_info["system_sku"])
    logger.info("Serial Number (Chassis): %s", machine_info["chassis_serial"])
    logger.info("IP Address: %s", get_local_ipaddress())


# Avoid running the script when e.g. simply importing the file.
if __name__ == '__main__':
    #logging.basicConfig(level=logging.DEBUG)
    import sys
    sys.exit(run(*sys.argv[1:]))
