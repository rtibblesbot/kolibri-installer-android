import zeroconf
from java import jclass

NetworkUtils = jclass("org.learningequality.Kolibri.util.NetworkUtils")


def get_all_addresses():
    # Get Java List and convert to Python list
    # Chaquopy Java collections need explicit conversion via toArray()
    java_list = NetworkUtils.getActiveIPv4Addresses()
    return [str(addr) for addr in java_list.toArray()]


zeroconf.get_all_addresses = get_all_addresses
