import os
import sys

import kolibri  # noqa: F401  Import Kolibri here so we can import modules from dist folder
import monkey_patch_zeroconf  # noqa: F401 Import this to patch zeroconf

script_dir = os.path.dirname(os.path.abspath(__file__))
sys.path.append(script_dir)

# Note: Environment variables are now set by KolibriEnvironmentSetup.java
# This file is kept for backwards compatibility and Python-specific initialization

# Verify required environment variables are set
required_vars = [
    "KOLIBRI_HOME",
    "KOLIBRI_APK_VERSION_NAME",
    "DJANGO_SETTINGS_MODULE",
    "KOLIBRI_AUTH_TOKEN",
]

for var in required_vars:
    if var not in os.environ:
        raise RuntimeError(
            f"Required environment variable {var} not set. "
            "KolibriEnvironmentSetup.initializeEnv() must be called from Java first."
        )
