"""
Task reconciliation system for Android
Syncs WorkManager state with Kolibri job database
"""
import logging
import threading

import initialization  # noqa: F401 keep this first
from java import jclass

logger = logging.getLogger(__name__)

# Thread lock for reconciliation - prevents concurrent execution
_reconcile_lock = threading.Lock()

# Java classes for WorkManager interaction
Task = jclass("org.learningequality.Kolibri.task.Task")


def reconcile_tasks():
    """
    Reconcile WorkManager state with Kolibri database
    Called from Java WorkController

    Returns:
        tuple: (added_count, cancelled_count) - reconciliation summary
    """
    # Non-blocking acquire - if already locked, skip reconciliation
    acquired = _reconcile_lock.acquire(blocking=False)
    if not acquired:
        logger.info("Reconciliation already in progress, skipping")
        return (0, 0)

    try:
        # Perform reconciliation
        result = _do_reconciliation()
        return (result["added"], result["cancelled"])
    except Exception as e:
        logger.error(f"Error during task reconciliation: {e}", exc_info=True)
        return (0, 0)
    finally:
        _reconcile_lock.release()


def _get_workmanager_job_ids():
    """
    Get all active job IDs from WorkManager (ENQUEUED and RUNNING states)

    Returns:
        set: Set of job ID strings
    """
    WorkManager = jclass("androidx.work.WorkManager")
    WorkInfo = jclass("androidx.work.WorkInfo")
    WorkQuery = jclass("androidx.work.WorkQuery")
    ContextUtil = jclass("org.learningequality.Kolibri.util.ContextUtil")
    Arrays = jclass("java.util.Arrays")

    context = ContextUtil.getApplicationContext()
    work_manager = WorkManager.getInstance(context)

    # Query for ENQUEUED and RUNNING work
    states = Arrays.asList(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING)
    work_query = WorkQuery.fromStates(states)
    work_info_list = work_manager.getWorkInfos(work_query).get()

    # Extract job IDs from tags
    job_ids = set()
    for work_info in work_info_list.toArray():
        tags = work_info.getTags()
        for tag in tags.toArray():
            # Filter out class names (worker class FQNs), keep job IDs
            # Job IDs can be UUIDs, numbers, or strings like "streamed_cache_cleanup"
            if not tag.startswith("org.") and not tag.startswith("androidx."):
                job_ids.add(tag)

    return job_ids


def _get_kolibri_jobs():
    """
    Get all active jobs from Kolibri database

    Returns:
        dict: Mapping of job_id string to job object
    """
    from kolibri.core.tasks.main import job_storage

    active_states = ["QUEUED", "RUNNING", "SCHEDULED"]
    kolibri_jobs = {}

    for state in active_states:
        for job in job_storage.filter_jobs(state=state):
            kolibri_jobs[str(job.job_id)] = job

    return kolibri_jobs


def _reenqueue_missing_task(job_id, job):
    """
    Re-enqueue a single missing task

    Returns:
        bool: True if successfully re-enqueued
    """
    try:
        request_id = Task.enqueueOnce(
            job_id,
            0,  # delay - immediate
            False,  # high_priority - use normal for reconciliation
            job.func,
            job.long_running,
        )
        if request_id:
            logger.info(f"Re-enqueued missing task: {job_id}")
            return True
        else:
            logger.error(f"Failed to re-enqueue task: {job_id}")
            return False
    except Exception as e:
        logger.error(f"Error re-enqueuing task {job_id}: {e}", exc_info=True)
        return False


def _cancel_orphaned_task(job_id):
    """
    Cancel a single orphaned task

    Returns:
        bool: True if successfully cancelled
    """
    try:
        Task.clear(job_id)
        logger.info(f"Cancelled orphaned task: {job_id}")
        return True
    except Exception as e:
        logger.error(f"Error cancelling task {job_id}: {e}", exc_info=True)
        return False


def _do_reconciliation():
    """
    Internal reconciliation logic
    Compares Kolibri database with WorkManager state and reconciles:
    - Re-enqueues missing tasks (in Kolibri but not in WorkManager)
    - Cancels orphaned tasks (in WorkManager but not in Kolibri)
    """
    logger.info("Starting task reconciliation")

    try:
        kolibri_jobs = _get_kolibri_jobs()
        kolibri_job_ids = set(kolibri_jobs.keys())
        logger.info(f"Found {len(kolibri_job_ids)} active jobs in Kolibri database")

        workmanager_job_ids = _get_workmanager_job_ids()
        logger.info(f"Found {len(workmanager_job_ids)} active tasks in WorkManager")

        # Re-enqueue missing tasks (in Kolibri but not in WorkManager)
        missing_job_ids = kolibri_job_ids - workmanager_job_ids
        added_count = 0
        if missing_job_ids:
            logger.info(f"Found {len(missing_job_ids)} missing tasks to re-enqueue")
            for job_id in missing_job_ids:
                job = kolibri_jobs.get(job_id)
                if job and _reenqueue_missing_task(job_id, job):
                    added_count += 1

        # Cancel orphaned tasks (in WorkManager but not in Kolibri)
        orphaned_job_ids = workmanager_job_ids - kolibri_job_ids
        cancelled_count = 0
        if orphaned_job_ids:
            logger.info(f"Found {len(orphaned_job_ids)} orphaned tasks to cancel")
            for job_id in orphaned_job_ids:
                if _cancel_orphaned_task(job_id):
                    cancelled_count += 1

        logger.info("Task reconciliation completed")
        logger.info(f"Added: {added_count}, Cancelled: {cancelled_count}")

        return {"added": added_count, "cancelled": cancelled_count}

    except Exception as e:
        logger.error(f"Error in reconciliation logic: {e}", exc_info=True)
        return {"added": 0, "cancelled": 0}


def get_all_active_jobs():
    """
    Get all active jobs from Kolibri database
    Helper function for testing and debugging

    Returns:
        list: List of active job objects
    """
    from kolibri.core.tasks.main import job_storage

    active_states = ["QUEUED", "RUNNING", "SCHEDULED"]
    jobs = []

    for state in active_states:
        jobs.extend(job_storage.filter_jobs(state=state))

    return jobs
