"""
Task status management for Android workers
Provides functions to update Kolibri job status from Java
"""
import logging

import initialization  # noqa: F401 keep this first

logger = logging.getLogger(__name__)


def update_task_status(task_id, state):
    """
    Update task status in Kolibri database
    Called from Java when worker completes, fails, or is cancelled

    Args:
        task_id: The task/job ID (UUID as string)
        state: New state - "FAILED", "CANCELED", "COMPLETED"

    Returns:
        bool: True if update succeeded, False otherwise
    """
    try:
        # Import after initialization
        from kolibri.core.tasks.main import job_storage

        logger.info(f"Updating task {task_id} status to {state}")

        # Use the appropriate method based on state
        if state == "CANCELED":
            job_storage.cancel(task_id)
        elif state == "FAILED":
            # mark_job_as_failed requires exception and traceback
            job_storage.mark_job_as_failed(
                task_id, exception="Worker killed or failed", traceback=""
            )
        elif state == "COMPLETED":
            job_storage.complete_job(task_id)
        else:
            logger.warning(f"Unknown state: {state}")
            return False

        logger.info(f"Successfully updated task {task_id} to {state}")
        return True

    except Exception as e:
        logger.error(f"Failed to update task {task_id} status: {e}", exc_info=True)
        return False
