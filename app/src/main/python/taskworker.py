import logging

import initialization  # noqa: F401 keep this first, to ensure we're set up for other imports
from auth import os_user
from kolibri.plugins.app.utils import interface

# Note: initialize() is called by KolibriEnvironmentSetup.initializeEnv()
# We just need to register the interface handlers
interface.register(get_os_user=os_user)

logger = logging.getLogger(__name__)


def execute_job(request_id):
    """
    Execute a Kolibri job given a request ID (UUID)
    Called from Java TaskWorkerImpl via Chaquopy

    Args:
        request_id: The WorkManager request ID (UUID as string)

    Returns:
        bool: True if job executed successfully, False otherwise
    """
    logger.info("Starting Kolibri task worker for request {}".format(request_id))

    # Import this after we have initialized Kolibri
    from kolibri.core.tasks.worker import (
        execute_job as kolibri_execute_job,
    )  # noqa: E402

    try:
        # Execute the job
        # The job_id is the same as request_id for now
        # We use request_id for all identifiers since that's what WorkManager gives us
        kolibri_execute_job(
            str(request_id),
            worker_process="android_worker",
            worker_thread=str(request_id),
            worker_extra=str(request_id),
        )
        logger.info("Completed Kolibri task worker for request {}".format(request_id))
        return True

    except Exception as e:
        logger.exception("Error occurred executing job", exc_info=e)
        return False
