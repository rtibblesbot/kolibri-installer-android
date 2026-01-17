"""
Kolibri Server Entry Point for Chaquopy

This module provides the AndroidKolibriProcessBus which can be started
from Java via KolibriServerService.
"""
import logging

import initialization  # noqa: F401 keep this first, to ensure we're set up for other imports
from auth import get_os_user_auth_token
from auth import os_user
from java import jclass
from kolibri.plugins.app.utils import interface
from kolibri.utils.server import BaseKolibriProcessBus
from kolibri.utils.server import KolibriServerPlugin
from kolibri.utils.server import ZeroConfPlugin
from kolibri.utils.server import ZipContentServerPlugin
from magicbus.plugins import SimplePlugin
from org.learningequality.Kolibri import KolibriServerViewModel

# Java utilities called directly
NetworkUtils = jclass("org.learningequality.Kolibri.util.NetworkUtils")
ShareUtils = jclass("org.learningequality.Kolibri.util.ShareUtils")

logger = logging.getLogger(__name__)

# Module-level reference to the running server bus (for shutdown)
_server_bus = None


def _get_auth_token():
    """Get auth token lazily to avoid import-time issues"""
    return get_os_user_auth_token()


class AppPlugin(SimplePlugin):
    """
    Plugin that handles server state monitoring

    Signals Java via ViewModel when HTTP server is ready.
    Provides initialization URL with auth token for first launch.
    """

    def __init__(self, bus):
        self.bus = bus
        self.bus.subscribe("SERVING", self.SERVING)

    def SERVING(self, port):
        """Called when server reaches SERVING state"""
        # Use Kolibri's interface to build proper initialization URL
        # Get auth token lazily to ensure environment is ready
        auth_token = _get_auth_token()
        init_url = "http://127.0.0.1:{port}".format(
            port=port
        ) + interface.get_initialize_url(auth_token=auth_token)

        logger.info(f"Kolibri server ready at: {init_url}")

        # Signal Java that server is ready
        KolibriServerViewModel.getInstance().setServerReady(True, port, init_url)


class AndroidKolibriProcessBus(BaseKolibriProcessBus):
    """
    Kolibri process bus for Android with Chaquopy

    This bus manages the Kolibri HTTP server lifecycle.
    Server handles both local WebView and remote peer connections.
    """

    def __init__(self):
        super().__init__()
        self._setup_plugins()

    def _setup_plugins(self):
        """Setup all required server plugins"""
        # Setup zeroconf plugin
        zeroconf_plugin = ZeroConfPlugin(self, self.port)
        zeroconf_plugin.subscribe()

        # Setup main Kolibri server
        kolibri_server = KolibriServerPlugin(self, self.port)
        kolibri_server.subscribe()

        # Setup zip content server (for alternate port)
        alt_port_server = ZipContentServerPlugin(self, self.zip_port)
        alt_port_server.subscribe()

        # Setup app plugin for state monitoring
        app_plugin = AppPlugin(self)
        app_plugin.subscribe()

    def stop(self):
        """Stop the server"""
        self.transition("EXITED")


def start_server():
    """
    Start the Kolibri HTTP server
    Called from Java KolibriServerService

    Runs HTTP server for both local WebView (via Service Worker)
    and remote peer connections. Blocks until server stops.

    Note: Kolibri initialization is done in KolibriEnvironmentSetup.java
    via kolibri.main.initialize() - do NOT call initialize() here again
    or it will fail with "Attempted to update plugins when registry is initialized"
    """
    global _server_bus

    logger.info("Starting Kolibri server")

    # Note: Plugins are enabled in KolibriEnvironmentSetup.java BEFORE initialize()
    # Do NOT call enable_plugin() here - it's too late after initialization

    # Register interface handlers
    # Call Java utilities directly for simple wrappers
    interface.register(
        share_file=lambda path=None, filename=None, message=None, app=None, mimetype=None: ShareUtils.shareByIntent(
            path or "", message or "", app or "", mimetype or ""
        )
    )
    interface.register(check_is_metered=NetworkUtils.isActiveNetworkMetered)
    interface.register(get_os_user=os_user)

    # Create and run server bus
    logger.info("Creating Kolibri server bus")
    bus = AndroidKolibriProcessBus()
    _server_bus = bus

    try:
        logger.info("Starting Kolibri server")
        # Note: This blocks until server stops
        bus.run()
    finally:
        _server_bus = None
        # Reset server state in ViewModel
        KolibriServerViewModel.getInstance().resetServerState()

    return bus


def stop_server():
    """
    Stop the Kolibri HTTP server
    Called from Java KolibriServerService.onDestroy()
    """
    global _server_bus

    if _server_bus is not None:
        logger.info("Stopping Kolibri server")
        try:
            _server_bus.stop()
        except Exception as e:
            logger.error(f"Error stopping server: {e}", exc_info=True)
    else:
        logger.warning("stop_server called but no server is running")
