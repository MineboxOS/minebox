#!/usr/bin/env python

# Script to register a new machine with the Minebox admin service.

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function
import logging
from systemtools import register_machine

# *** variables & constants ***


# Run the actual meat of the script.
def run(*args):
    # Set up logging, see https://docs.python.org/2/howto/logging.html
    logger = logging.getLogger("register-machine")
    logger.setLevel(logging.DEBUG)
    log_handler = logging.StreamHandler()
    log_handler.setLevel(logging.DEBUG)
    logger.addHandler(log_handler)

    logger.info("Registering machine with Minebox admin service.")
    success, errmsg = register_machine()
    if success:
        logger.info("Registration successful!")
    else:
        logger.error(errmsg)


# Avoid running the script when e.g. simply importing the file.
if __name__ == '__main__':
    #logging.basicConfig(level=logging.DEBUG)
    import sys
    sys.exit(run(*sys.argv[1:]))
